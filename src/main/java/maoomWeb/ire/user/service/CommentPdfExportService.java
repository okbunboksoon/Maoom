package maoomWeb.ire.user.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationInk;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.drive.Drive;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;

/**
 * 화면에서 저장한 MAOOM 댓글을 원본 PDF의 표준 주석으로 변환해 새 PDF를 만든다.
 *
 * <p>pdfview.html은 위치를 페이지 너비/높이 기준 0~1 비율로 저장한다.
 * 이 서비스는 그 비율을 실제 PDF 좌표로 바꾸고 TEXT, RECT, CALLOUT, DRAW 유형에
 * 맞는 PDFBox 주석 객체를 만든다. 결과 주석은 Acrobat 등 일반 PDF 뷰어에서도
 * 선택하고 댓글 패널에서 확인할 수 있다.</p>
 *
 * <p>처리 순서: Drive 원본 다운로드 -> 댓글/답글/첨부 조회 -> 페이지별 좌표 변환
 * -> 표준 PDF 주석 및 첨부 삽입 -> 완성된 PDF 바이트 반환.</p>
 */
@Service
public class CommentPdfExportService {

    private static final Logger log =
            LoggerFactory.getLogger(CommentPdfExportService.class);
    private static final float ANNOTATION_SIZE = 22f;
    private static final float RECT_LINE_WIDTH = 2.2f;
    private static final float DRAW_LINE_WIDTH = 2.2f;
    private static final float CALLOUT_WIDTH = 150f;
    private static final float CALLOUT_HEIGHT = 64f;
    private static final ObjectMapper JSON_MAPPER =
            new ObjectMapper();
    private static final PDColor HIGHLIGHT_COLOR =
            rgb(1f, 0.64f, 0.20f);
    private static final PDColor RECT_COLOR =
            rgb(1f, 0.12f, 0.12f);
    private static final PDColor DRAW_COLOR =
            rgb(0.08f, 0.36f, 1f);

    private final CommentMapper commentMapper;
    private final Drive drive;
    private final Path uploadRoot;

    public CommentPdfExportService(
            CommentMapper commentMapper,
            Drive drive,
            @Value("${app.comment.upload-dir}") String uploadDir) {
        this.commentMapper = commentMapper;
        this.drive = drive;
        this.uploadRoot = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /** 지정한 PDF의 모든 댓글과 첨부를 원본에 합쳐 다운로드용 PDF를 생성한다. */
    public byte[] createAnnotatedPdf(
            Long pdfId,
            String driveFileId) throws IOException {

        List<CommentDto> comments =
                commentMapper.getCommentList(pdfId, "ALL")
                .stream()
                .sorted(
                        Comparator
                        .comparing(
                                CommentDto::getPageNum,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()))
                        .thenComparing(
                                CommentDto::getCreateDt,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder())))
                .toList();
        Map<Long,List<CommentReplyDto>> repliesByComment =
                comments.isEmpty()
                ? Map.of()
                : commentMapper.getReplyListByCommentIds(
                        comments.stream()
                        .map(CommentDto::getCommentId)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        CommentReplyDto::getCommentId));
        Map<Long,List<CommentAttachmentDto>> attachmentsByComment =
                comments.isEmpty()
                ? Map.of()
                : commentMapper.getAttachmentListByCommentIds(
                        comments.stream()
                        .map(CommentDto::getCommentId)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        CommentAttachmentDto::getCommentId));
        List<Long> replyIds =
                repliesByComment.values()
                .stream()
                .flatMap(List::stream)
                .map(CommentReplyDto::getReplyId)
                .toList();
        Map<Long,List<CommentAttachmentDto>> attachmentsByReply =
                replyIds.isEmpty()
                ? Map.of()
                : commentMapper.getAttachmentListByReplyIds(replyIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CommentAttachmentDto::getReplyId));

        try(ByteArrayOutputStream original =
                    new ByteArrayOutputStream();
                ByteArrayOutputStream output =
                    new ByteArrayOutputStream()){

            drive.files()
                    .get(driveFileId)
                    .setSupportsAllDrives(true)
                    .executeMediaAndDownloadTo(original);

            try(PDDocument document =
                    Loader.loadPDF(original.toByteArray())){

                for(CommentDto comment : comments){
                    List<CommentReplyDto> replies =
                            repliesByComment.getOrDefault(
                                    comment.getCommentId(),
                                    List.of());
                    addNativeAnnotation(
                            document,
                            comment,
                            replies,
                            attachmentsByComment.getOrDefault(
                                    comment.getCommentId(),
                                    List.of()),
                            attachmentsByReply);
                }

                document.save(output);
            }

            return output.toByteArray();
        }
    }

    private void addNativeAnnotation(
            PDDocument document,
            CommentDto comment,
            List<CommentReplyDto> replies,
            List<CommentAttachmentDto> commentAttachments,
            Map<Long,List<CommentAttachmentDto>> attachmentsByReply)
            throws IOException {

        Integer pageNumber = comment.getPageNum();

        if(pageNumber == null
                || pageNumber < 1
                || pageNumber > document.getNumberOfPages()){
            return;
        }

        PDPage page = document.getPage(pageNumber - 1);
        PDAnnotation annotation =
                createNativeAnnotation(
                        document,
                        page,
                        comment);

        if(annotation == null){
            return;
        }

        applyCommentMetadata(
                annotation,
                page,
                comment);
        page.getAnnotations().add(annotation);
        Map<Long,PDAnnotation> replyAnnotations =
                addReplyAnnotations(
                page,
                annotation,
                replies);
        addAttachmentAnnotations(
                document,
                page,
                annotation,
                commentAttachments,
                replies,
                attachmentsByReply,
                replyAnnotations);
    }

    /**
     * Embeds images as file-attachment annotations linked to the matching
     * comment or reply. No image is painted into the PDF page content.
     */
    private void addAttachmentAnnotations(
            PDDocument document,
            PDPage page,
            PDAnnotation commentAnnotation,
            List<CommentAttachmentDto> commentAttachments,
            List<CommentReplyDto> replies,
            Map<Long,List<CommentAttachmentDto>> attachmentsByReply,
            Map<Long,PDAnnotation> replyAnnotations) {

        addLinkedAttachments(
                document,
                page,
                commentAnnotation,
                commentAttachments,
                null);

        for(CommentReplyDto reply : replies){
            PDAnnotation replyAnnotation =
                    replyAnnotations.get(
                            reply.getReplyId());

            if(replyAnnotation == null){
                continue;
            }

            addLinkedAttachments(
                    document,
                    page,
                    replyAnnotation,
                    attachmentsByReply.getOrDefault(
                            reply.getReplyId(),
                            List.of()),
                    reply);
        }
    }

    private void addLinkedAttachments(
            PDDocument document,
            PDPage page,
            PDAnnotation parent,
            List<CommentAttachmentDto> attachments,
            CommentReplyDto reply) {

        for(CommentAttachmentDto attachment : attachments){
            try{
                Path attachmentPath =
                        resolveStoredFile(
                                attachment.getStoredName());

                if(!Files.isRegularFile(attachmentPath)){
                    log.warn(
                            "Skipping missing PDF comment attachment: {}",
                            attachmentPath);
                    continue;
                }

                byte[] bytes =
                        Files.readAllBytes(attachmentPath);
                PDEmbeddedFile embeddedFile;

                try(ByteArrayInputStream input =
                        new ByteArrayInputStream(bytes)){
                    embeddedFile =
                            new PDEmbeddedFile(
                                document,
                                input);
                }

                embeddedFile.setSubtype(
                        normalizedContentType(
                                attachment,
                                attachmentPath));
                embeddedFile.setSize(bytes.length);
                embeddedFile.setCreationDate(
                        toCalendar(
                                attachment.getCreateDt()));

                String fileName =
                        attachment.getOriginalName() == null
                        || attachment.getOriginalName().isBlank()
                        ? "attachment"
                        : attachment.getOriginalName();
                PDComplexFileSpecification file =
                        new PDComplexFileSpecification();
                file.setFile(fileName);
                file.setFileUnicode(fileName);
                file.setFileDescription("댓글 첨부파일");
                file.setEmbeddedFile(embeddedFile);
                file.setEmbeddedFileUnicode(embeddedFile);

                PDAnnotationFileAttachment annotation =
                        new PDAnnotationFileAttachment();
                annotation.setRectangle(
                        parent.getRectangle());
                annotation.setPage(page);
                annotation.setNoView(true);
                annotation.setPrinted(false);
                annotation.setInReplyTo(parent);
                annotation.setReplyType(
                        PDAnnotationMarkup.RT_REPLY);
                annotation.setAttachmentName(
                        PDAnnotationFileAttachment
                        .ATTACHMENT_NAME_PAPERCLIP);
                annotation.setFile(file);
                annotation.setSubject("첨부파일");
                annotation.setContents(fileName);
                annotation.setTitlePopup(
                        reply == null
                        ? attachment.getUploaderId()
                        : getReplyAuthor(reply));
                annotation.setCreationDate(
                        toCalendar(
                                attachment.getCreateDt()));

                if(attachment.getAttachmentId() != null){
                    annotation.setAnnotationName(
                            "maoom-attachment-"
                            + attachment.getAttachmentId());
                }

                page.getAnnotations().add(annotation);
            }catch(IOException | RuntimeException error){
                log.warn(
                        "Skipping unreadable PDF comment attachment: {}",
                        attachment.getOriginalName(),
                        error);
            }
        }
    }

    private String normalizedContentType(
            CommentAttachmentDto attachment,
            Path attachmentPath) throws IOException {

        String contentType =
                attachment.getContentType();

        if(contentType != null
                && !contentType.isBlank()){
            return contentType;
        }

        String detectedType =
                Files.probeContentType(attachmentPath);

        return detectedType == null
                || detectedType.isBlank()
                ? "application/octet-stream"
                : detectedType;
    }

    private Path resolveStoredFile(String storedName) {

        Path target =
                uploadRoot.resolve(storedName).normalize();

        if(!target.startsWith(uploadRoot)){
            throw new IllegalArgumentException(
                    "잘못된 첨부파일 경로입니다.");
        }

        return target;
    }

    private PDAnnotation createNativeAnnotation(
            PDDocument document,
            PDPage page,
            CommentDto comment)
            throws IOException {

        String type = comment.getCommentType();

        if("TEXT".equals(type)){
            return createHighlight(
                    document,
                    page,
                    comment);
        }
        if("RECT".equals(type)){
            return createRectangle(
                    document,
                    page,
                    comment);
        }
        if("CALLOUT".equals(type)){
            return createCallout(
                    page,
                    comment);
        }
        if("DRAW".equals(type)){
            return createInk(
                    document,
                    page,
                    comment);
        }

        return createTextNote(page, comment);
    }

    private PDAnnotation createHighlight(
            PDDocument document,
            PDPage page,
            CommentDto comment)
            throws IOException {

        List<NormalizedRect> rectangles =
                parseTextRectangles(
                        comment.getDrawingPath());

        if(rectangles.isEmpty()){
            NormalizedRect fallback =
                    getCommentRectangle(comment);

            if(fallback != null){
                rectangles = List.of(fallback);
            }
        }

        if(rectangles.isEmpty()){
            return createTextNote(page, comment);
        }

        List<Float> quadPoints = new ArrayList<>();
        List<PdfPoint> allPoints = new ArrayList<>();

        for(NormalizedRect rectangle : rectangles){
            PdfQuad quad = toPdfQuad(page, rectangle);
            quadPoints.add(quad.topLeft().x());
            quadPoints.add(quad.topLeft().y());
            quadPoints.add(quad.topRight().x());
            quadPoints.add(quad.topRight().y());
            quadPoints.add(quad.bottomLeft().x());
            quadPoints.add(quad.bottomLeft().y());
            quadPoints.add(quad.bottomRight().x());
            quadPoints.add(quad.bottomRight().y());
            allPoints.addAll(quad.points());
        }

        PDAnnotationHighlight annotation =
                new PDAnnotationHighlight();
        annotation.setRectangle(
                boundingRectangle(allPoints, 1f));
        annotation.setQuadPoints(
                toFloatArray(quadPoints));
        annotation.setColor(HIGHLIGHT_COLOR);
        annotation.setConstantOpacity(0.55f);
        annotation.constructAppearances(document);
        return annotation;
    }

    private PDAnnotation createRectangle(
            PDDocument document,
            PDPage page,
            CommentDto comment)
            throws IOException {

        NormalizedRect rectangle =
                getCommentRectangle(comment);

        if(rectangle == null){
            return createTextNote(page, comment);
        }

        PDAnnotationSquare annotation =
                new PDAnnotationSquare();
        annotation.setRectangle(
                boundingRectangle(
                        toPdfQuad(page, rectangle).points(),
                        RECT_LINE_WIDTH));
        annotation.setColor(RECT_COLOR);
        annotation.setBorderStyle(
                solidBorder(RECT_LINE_WIDTH));
        annotation.constructAppearances(document);
        return annotation;
    }

    private PDAnnotation createCallout(
            PDPage page,
            CommentDto comment) {

        PdfPoint anchor =
                toPdfPoint(
                        page,
                        getAnnotationPoint(comment).x(),
                        getAnnotationPoint(comment).y());
        PDRectangle crop = page.getCropBox();
        float gap = 24f;
        float left =
                anchor.x() + gap + CALLOUT_WIDTH
                        <= crop.getUpperRightX()
                ? anchor.x() + gap
                : anchor.x() - gap - CALLOUT_WIDTH;
        float bottom =
                Math.max(
                        crop.getLowerLeftY() + 4f,
                        Math.min(
                                anchor.y() - CALLOUT_HEIGHT / 2f,
                                crop.getUpperRightY()
                                - CALLOUT_HEIGHT - 4f));
        PDRectangle box =
                new PDRectangle(
                        left,
                        bottom,
                        CALLOUT_WIDTH,
                        CALLOUT_HEIGHT);
        float boxEdgeX =
                left > anchor.x()
                ? left
                : left + CALLOUT_WIDTH;
        float boxEdgeY =
                Math.max(
                        bottom + 8f,
                        Math.min(
                                anchor.y(),
                                bottom + CALLOUT_HEIGHT - 8f));
        float kneeX =
                anchor.x()
                + (boxEdgeX - anchor.x()) * 0.55f;

        PDAnnotationFreeText annotation =
                new PDAnnotationFreeText();
        annotation.setRectangle(box);
        annotation.setIntent(
                PDAnnotationFreeText.IT_FREE_TEXT_CALLOUT);
        annotation.setCallout(
                new float[]{
                    anchor.x(),
                    anchor.y(),
                    kneeX,
                    anchor.y(),
                    boxEdgeX,
                    boxEdgeY
                });
        annotation.setLineEndingStyle("OpenArrow");
        annotation.setColor(RECT_COLOR);
        annotation.setBorderStyle(solidBorder(1.5f));
        annotation.setDefaultAppearance(
                "/Helv 10 Tf 0.12 0.12 0.12 rg");
        annotation.setDefaultStyleString(
                "font:10pt sans-serif;color:#202020");
        return annotation;
    }

    private PDAnnotation createInk(
            PDDocument document,
            PDPage page,
            CommentDto comment)
            throws IOException {

        List<NormalizedPoint> points =
                parseDrawingPoints(
                        comment.getDrawingPath());

        if(points.size() < 2){
            return createTextNote(page, comment);
        }

        float[] inkPath = new float[points.size() * 2];
        List<PdfPoint> pdfPoints = new ArrayList<>();

        for(int index = 0; index < points.size(); index++){
            NormalizedPoint point = points.get(index);
            PdfPoint pdfPoint =
                    toPdfPoint(
                            page,
                            point.x(),
                            point.y());
            inkPath[index * 2] = pdfPoint.x();
            inkPath[index * 2 + 1] = pdfPoint.y();
            pdfPoints.add(pdfPoint);
        }

        PDAnnotationInk annotation =
                new PDAnnotationInk();
        annotation.setRectangle(
                boundingRectangle(
                        pdfPoints,
                        DRAW_LINE_WIDTH));
        annotation.setInkList(
                new float[][]{inkPath});
        annotation.setColor(DRAW_COLOR);
        annotation.setBorderStyle(
                solidBorder(DRAW_LINE_WIDTH));
        annotation.constructAppearances(document);
        return annotation;
    }

    private PDAnnotationText createTextNote(
            PDPage page,
            CommentDto comment) {

        PdfPoint point =
                toPdfPoint(
                        page,
                        getAnnotationPoint(comment).x(),
                        getAnnotationPoint(comment).y());
        PDAnnotationText annotation =
                new PDAnnotationText();
        annotation.setRectangle(
                createAnnotationRectangle(
                        page,
                        point));
        annotation.setOpen(false);
        annotation.setName(
                PDAnnotationText.NAME_COMMENT);
        return annotation;
    }

    private void applyCommentMetadata(
            PDAnnotation annotation,
            PDPage page,
            CommentDto comment) {

        annotation.setPage(page);
        annotation.setPrinted(true);
        annotation.setContents(
                buildContents(comment));

        if(comment.getCommentId() != null){
            annotation.setAnnotationName(
                    "maoom-comment-"
                    + comment.getCommentId());
        }

        if(annotation instanceof PDAnnotationMarkup markup){
            markup.setTitlePopup(getAuthor(comment));
            markup.setSubject(buildSubject(comment));
            markup.setCreationDate(
                    toCalendar(comment.getCreateDt()));
        }
    }

    private Map<Long,PDAnnotation> addReplyAnnotations(
            PDPage page,
            PDAnnotation parent,
            List<CommentReplyDto> replies)
            throws IOException {

        Map<Long,PDAnnotation> result =
                new HashMap<>();

        if(!(parent instanceof PDAnnotationMarkup)
                || replies.isEmpty()){
            return result;
        }

        for(CommentReplyDto reply : replies){
            PDAnnotationText annotation =
                    new PDAnnotationText();
            annotation.setRectangle(parent.getRectangle());
            annotation.setPage(page);
            annotation.setOpen(false);
            annotation.setName(
                    PDAnnotationText.NAME_COMMENT);
            annotation.setNoView(true);
            annotation.setPrinted(false);
            annotation.setInReplyTo(parent);
            annotation.setReplyType(
                    PDAnnotationMarkup.RT_REPLY);
            annotation.setTitlePopup(
                    getReplyAuthor(reply));
            annotation.setSubject("답글");
            annotation.setContents(
                    reply.getReplyText() == null
                    ? ""
                    : reply.getReplyText().trim());
            annotation.setCreationDate(
                    parseReplyDate(reply.getCreateDt()));

            if(reply.getReplyId() != null){
                annotation.setAnnotationName(
                        "maoom-reply-"
                        + reply.getReplyId());
                result.put(
                        reply.getReplyId(),
                        annotation);
            }

            page.getAnnotations().add(annotation);
        }

        return result;
    }

    private PDBorderStyleDictionary solidBorder(float width) {
        PDBorderStyleDictionary border =
                new PDBorderStyleDictionary();
        border.setStyle(
                PDBorderStyleDictionary.STYLE_SOLID);
        border.setWidth(width);
        return border;
    }

    private static PDColor rgb(
            float red,
            float green,
            float blue) {
        return new PDColor(
                new float[]{red, green, blue},
                PDDeviceRGB.INSTANCE);
    }

    private PdfQuad toPdfQuad(
            PDPage page,
            NormalizedRect rectangle) {

        return new PdfQuad(
                toPdfPoint(
                        page,
                        rectangle.x(),
                        rectangle.y()),
                toPdfPoint(
                        page,
                        rectangle.x() + rectangle.w(),
                        rectangle.y()),
                toPdfPoint(
                        page,
                        rectangle.x(),
                        rectangle.y() + rectangle.h()),
                toPdfPoint(
                        page,
                        rectangle.x() + rectangle.w(),
                        rectangle.y() + rectangle.h()));
    }

    private PDRectangle boundingRectangle(
            List<PdfPoint> points,
            float padding) {

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for(PdfPoint point : points){
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }

        return new PDRectangle(
                minX - padding,
                minY - padding,
                Math.max(1f, maxX - minX + padding * 2f),
                Math.max(1f, maxY - minY + padding * 2f));
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];

        for(int index = 0; index < values.size(); index++){
            result[index] = values.get(index);
        }

        return result;
    }

    private NormalizedRect getCommentRectangle(
            CommentDto comment) {

        if(comment.getRectX() == null
                || comment.getRectY() == null
                || comment.getRectW() == null
                || comment.getRectH() == null){
            return null;
        }

        double x =
                clampRatio(
                        comment.getRectX().doubleValue());
        double y =
                clampRatio(
                        comment.getRectY().doubleValue());
        double width =
                Math.min(
                        Math.max(
                                comment.getRectW().doubleValue(),
                                0),
                        1 - x);
        double height =
                Math.min(
                        Math.max(
                                comment.getRectH().doubleValue(),
                                0),
                        1 - y);

        if(width <= 0 || height <= 0){
            return null;
        }

        return new NormalizedRect(
                x,
                y,
                width,
                height);
    }

    private List<NormalizedRect> parseTextRectangles(
            String drawingPath) {

        if(drawingPath == null
                || drawingPath.isBlank()){
            return List.of();
        }

        try{
            JsonNode root =
                    JSON_MAPPER.readTree(drawingPath);
            JsonNode rectangles =
                    root.isArray()
                    ? root
                    : root.path("rects");

            if(!rectangles.isArray()){
                return List.of();
            }

            List<NormalizedRect> result =
                    new ArrayList<>();

            for(JsonNode rectangle : rectangles){
                double x =
                        clampRatio(
                                rectangle.path("x")
                                .asDouble());
                double y =
                        clampRatio(
                                rectangle.path("y")
                                .asDouble());
                double width =
                        Math.min(
                                Math.max(
                                        rectangle.path("w")
                                        .asDouble(),
                                        0),
                                1 - x);
                double height =
                        Math.min(
                                Math.max(
                                        rectangle.path("h")
                                        .asDouble(),
                                        0),
                                1 - y);

                if(width > 0 && height > 0){
                    result.add(
                            new NormalizedRect(
                                    x,
                                    y,
                                    width,
                                    height));
                }
            }

            return result;
        }catch(IOException error){
            return List.of();
        }
    }

    private List<NormalizedPoint> parseDrawingPoints(
            String drawingPath) {

        if(drawingPath == null
                || drawingPath.isBlank()){
            return List.of();
        }

        try{
            JsonNode root =
                    JSON_MAPPER.readTree(drawingPath);

            if(!root.isArray()){
                return List.of();
            }

            List<NormalizedPoint> result =
                    new ArrayList<>();

            for(JsonNode point : root){
                if(point.has("x") && point.has("y")){
                    result.add(
                            new NormalizedPoint(
                                    clampRatio(
                                            point.path("x")
                                            .asDouble()),
                                    clampRatio(
                                            point.path("y")
                                            .asDouble())));
                }
            }

            return result;
        }catch(IOException error){
            return List.of();
        }
    }

    private NormalizedPoint getAnnotationPoint(
            CommentDto comment) {

        BigDecimal x =
                firstNonNull(
                        comment.getRectX(),
                        comment.getPosX());
        BigDecimal y =
                firstNonNull(
                        comment.getRectY(),
                        comment.getPosY());
        double normalizedX =
                clampRatio(
                        x == null
                        ? 0.02
                        : x.doubleValue());
        double normalizedY =
                clampRatio(
                        y == null
                        ? 0.02
                        : y.doubleValue());

        return new NormalizedPoint(
                normalizedX,
                normalizedY);
    }

    private BigDecimal firstNonNull(
            BigDecimal primary,
            BigDecimal fallback) {
        return primary == null ? fallback : primary;
    }

    private PdfPoint toPdfPoint(
            PDPage page,
            double normalizedX,
            double normalizedY) {

        PDRectangle cropBox = page.getCropBox();
        float left = cropBox.getLowerLeftX();
        float bottom = cropBox.getLowerLeftY();
        float right = cropBox.getUpperRightX();
        float top = cropBox.getUpperRightY();
        float width = cropBox.getWidth();
        float height = cropBox.getHeight();
        int rotation =
                Math.floorMod(page.getRotation(), 360);

        return switch(rotation){
            case 90 -> new PdfPoint(
                    left + (float) normalizedY * width,
                    bottom + (float) normalizedX * height);
            case 180 -> new PdfPoint(
                    right - (float) normalizedX * width,
                    bottom + (float) normalizedY * height);
            case 270 -> new PdfPoint(
                    right - (float) normalizedY * width,
                    top - (float) normalizedX * height);
            default -> new PdfPoint(
                    left + (float) normalizedX * width,
                    top - (float) normalizedY * height);
        };
    }

    private PDRectangle createAnnotationRectangle(
            PDPage page,
            PdfPoint point) {

        PDRectangle cropBox = page.getCropBox();
        float x =
                Math.max(
                        cropBox.getLowerLeftX(),
                        Math.min(
                                point.x(),
                                cropBox.getUpperRightX()
                                - ANNOTATION_SIZE));
        float y =
                Math.max(
                        cropBox.getLowerLeftY(),
                        Math.min(
                                point.y() - ANNOTATION_SIZE,
                                cropBox.getUpperRightY()
                                - ANNOTATION_SIZE));

        return new PDRectangle(
                x,
                y,
                ANNOTATION_SIZE,
                ANNOTATION_SIZE);
    }

    private String buildSubject(CommentDto comment) {
        String code =
                comment.getCommentCode() == null
                ? "Comment"
                : comment.getCommentCode();

        return code
                + " · "
                + formatStatus(comment.getStatus());
    }

    private String buildContents(CommentDto comment) {

        StringBuilder contents =
                new StringBuilder();

        contents.append("[")
                .append(formatStatus(comment.getStatus()))
                .append("] ")
                .append(getAuthor(comment));

        if(comment.getCommentText() != null
                && !comment.getCommentText().isBlank()){
            contents.append("\n")
                    .append(comment.getCommentText().trim());
        }

        return contents.toString();
    }

    private Calendar toCalendar(LocalDateTime dateTime) {
        if(dateTime == null){
            return null;
        }

        return GregorianCalendar.from(
                dateTime.atZone(
                        ZoneId.systemDefault()));
    }

    private Calendar parseReplyDate(String value) {
        if(value == null || value.isBlank()){
            return null;
        }

        List<DateTimeFormatter> formats =
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern(
                                "yyyy-MM-dd HH:mm:ss"),
                        DateTimeFormatter.ofPattern(
                                "yyyy-MM-dd HH:mm"));

        for(DateTimeFormatter format : formats){
            try{
                return toCalendar(
                        LocalDateTime.parse(
                                value.trim(),
                                format));
            }catch(DateTimeParseException ignored){
                // Try the next format used by existing database mappings.
            }
        }

        return null;
    }

    private String getAuthor(CommentDto comment) {
        String author =
                comment.getUserName() == null
                || comment.getUserName().isBlank()
                ? comment.getUserId()
                : comment.getUserName();
        return author == null || author.isBlank()
                ? "Unknown"
                : author;
    }

    private String getReplyAuthor(CommentReplyDto reply) {
        String author =
                reply.getUserName() == null
                || reply.getUserName().isBlank()
                ? reply.getUserId()
                : reply.getUserName();
        return author == null || author.isBlank()
                ? "Unknown"
                : author;
    }

    private String formatStatus(String status) {
        return "RESOLVED".equals(status)
                ? "해결"
                : "미해결";
    }

    private double clampRatio(double value) {
        return Math.max(0, Math.min(value, 1));
    }

    private record NormalizedPoint(
            double x,
            double y) {
    }

    private record PdfPoint(
            float x,
            float y) {
    }

    private record NormalizedRect(
            double x,
            double y,
            double w,
            double h) {
    }

    private record PdfQuad(
            PdfPoint topLeft,
            PdfPoint topRight,
            PdfPoint bottomLeft,
            PdfPoint bottomRight) {

        private List<PdfPoint> points() {
            return List.of(
                    topLeft,
                    topRight,
                    bottomLeft,
                    bottomRight);
        }
    }

}
