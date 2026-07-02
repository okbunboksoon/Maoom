package maoomWeb.ire.user.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;

/**
 * PDF 댓글, 답글, 첨부파일을 검토용 Excel 문서로 만든다.
 *
 * <p>CommentController의 {@code /api/comment/export}가 이 서비스를 호출한다.
 * 댓글과 답글을 시간 순으로 행에 배치하고, 지원되는 이미지 첨부는 셀 안에 넣으며
 * 일반 파일은 파일명 목록으로 표시한다.</p>
 */
@Service
public class CommentExportService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int IMAGE_COLUMN = 6;
    private static final int IMAGE_CELL_WIDTH_PX = 420;
    private static final int IMAGE_HEIGHT_PX = 72;
    private static final int IMAGE_GAP_PX = 6;

    private final CommentMapper commentMapper;
    private final Path uploadRoot;

    /** 댓글과 답글 조회에 사용할 매퍼를 주입한다. */
    public CommentExportService(
            CommentMapper commentMapper,
            @Value("${app.comment.upload-dir}") String uploadDir) {
        this.commentMapper = commentMapper;
        this.uploadRoot = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 원댓글과 답글을 시간 순서대로 배치한 XLSX 바이트 배열을 만든다.
     * 반환된 바이트는 컨트롤러가 Content-Disposition 헤더와 함께 브라우저로 전송한다.
     */
    public byte[] createWorkbook(Long pdfId) throws IOException {

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
        List<Long> replyIds = repliesByComment.values()
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

        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){

            Sheet sheet = workbook.createSheet("댓글 목록");
            Drawing<?> drawing =
                    sheet.createDrawingPatriarch();
            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            String[] headers = {
                    "상태",
                    "구분",
                    "페이지",
                    "작성자",
                    "코멘트내용",
                    "작성시간",
                    "첨부 이미지"
            };

            for(int i = 0; i < headers.length; i++){
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            int rowIndex = 1;

            for(CommentDto comment : comments){

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(
                        formatStatus(comment.getStatus()));
                row.createCell(1).setCellValue("원댓글");
                row.createCell(2).setCellValue(
                        formatPageNumber(comment.getPageNum()));
                row.createCell(3).setCellValue(
                        formatAuthor(
                                comment.getUserName(),
                                comment.getUserId()));
                row.createCell(4).setCellValue(
                        comment.getCommentText());
                row.createCell(5).setCellValue(
                        comment.getCreateDt() == null
                        ? ""
                        : DATE_FORMAT.format(
                                comment.getCreateDt()));
                addAttachments(
                        workbook,
                        drawing,
                        row,
                        attachmentsByComment.getOrDefault(
                                comment.getCommentId(),
                                List.of()));

                List<CommentReplyDto> replies =
                        repliesByComment.getOrDefault(
                                comment.getCommentId(),
                                List.of());

                for(CommentReplyDto reply : replies){

                    Row replyRow = sheet.createRow(rowIndex++);

                    replyRow.createCell(0).setCellValue(
                            formatStatus(comment.getStatus()));
                    replyRow.createCell(1).setCellValue("답글");
                    replyRow.createCell(2).setCellValue(
                            formatPageNumber(comment.getPageNum()));
                    replyRow.createCell(3).setCellValue(
                            formatAuthor(
                                    reply.getUserName(),
                                    reply.getUserId()));
                    replyRow.createCell(4).setCellValue(
                            "↳ " + reply.getReplyText());
                    replyRow.createCell(5).setCellValue(
                            reply.getCreateDt() == null
                            ? ""
                            : reply.getCreateDt());
                    addAttachments(
                            workbook,
                            drawing,
                            replyRow,
                            attachmentsByReply.getOrDefault(
                                    reply.getReplyId(),
                                    List.of()));
                }
            }

            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 12 * 256);
            sheet.setColumnWidth(2, 10 * 256);
            sheet.setColumnWidth(3, 18 * 256);
            sheet.setColumnWidth(4, 55 * 256);
            sheet.setColumnWidth(5, 22 * 256);
            sheet.setColumnWidth(
                    IMAGE_COLUMN,
                    58 * 256);
            sheet.createFreezePane(0, 1);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String formatPageNumber(Integer pageNum) {
        return pageNum == null ? "" : pageNum + "페이지";
    }

    private String formatStatus(String status) {
        return "RESOLVED".equals(status)
                ? "해결"
                : "미해결";
    }

    private String formatAuthor(
            String userName,
            String userId) {
        return userName == null || userName.isBlank()
                ? userId == null ? "" : userId
                : userName;
    }

    private void addAttachments(
            Workbook workbook,
            Drawing<?> drawing,
            Row row,
            List<CommentAttachmentDto> attachments)
            throws IOException {

        List<CommentAttachmentDto> images =
                attachments.stream()
                .filter(this::isExcelImage)
                .toList();
        List<String> otherFiles =
                attachments.stream()
                .filter(attachment ->
                        !isExcelImage(attachment))
                .map(CommentAttachmentDto::getOriginalName)
                .toList();

        if(!otherFiles.isEmpty()){
            row.createCell(IMAGE_COLUMN)
                    .setCellValue(
                            String.join(", ", otherFiles));
        }

        if(images.isEmpty()){
            return;
        }

        List<ImageData> imageData = new ArrayList<>();

        for(CommentAttachmentDto attachment : images){
            Path imagePath =
                    resolveStoredFile(
                            attachment.getStoredName());

            if(Files.isRegularFile(imagePath)){
                imageData.add(
                        new ImageData(
                                Files.readAllBytes(imagePath),
                                getPictureType(
                                        attachment.getContentType())));
            }
        }

        if(imageData.isEmpty()){
            return;
        }

        int thumbnailWidth =
                Math.min(
                        96,
                        Math.max(
                                42,
                                (IMAGE_CELL_WIDTH_PX
                                - IMAGE_GAP_PX
                                * (imageData.size() + 1))
                                / imageData.size()));
        int rowIndex = row.getRowNum();

        row.setHeightInPoints(
                (float) (IMAGE_HEIGHT_PX * 0.75 + 8));

        for(int index = 0;
                index < imageData.size();
                index++){

            ImageData image = imageData.get(index);
            int pictureIndex =
                    workbook.addPicture(
                            image.bytes(),
                            image.pictureType());
            int left =
                    IMAGE_GAP_PX
                    + index
                    * (thumbnailWidth + IMAGE_GAP_PX);
            XSSFClientAnchor anchor =
                    new XSSFClientAnchor();

            anchor.setCol1(IMAGE_COLUMN);
            anchor.setRow1(rowIndex);
            anchor.setCol2(IMAGE_COLUMN);
            anchor.setRow2(rowIndex);
            anchor.setDx1(Units.pixelToEMU(left));
            anchor.setDy1(Units.pixelToEMU(4));
            anchor.setDx2(
                    Units.pixelToEMU(
                            left + thumbnailWidth));
            anchor.setDy2(
                    Units.pixelToEMU(
                            4 + IMAGE_HEIGHT_PX));
            anchor.setAnchorType(
                    ClientAnchor.AnchorType.MOVE_AND_RESIZE);

            Picture picture =
                    drawing.createPicture(
                            anchor,
                            pictureIndex);
            picture.getClientAnchor().setAnchorType(
                    ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        }
    }

    private boolean isExcelImage(
            CommentAttachmentDto attachment) {
        return getPictureType(attachment.getContentType()) != -1;
    }

    private int getPictureType(String contentType) {

        if(contentType == null){
            return -1;
        }

        return switch(contentType.toLowerCase()){
            case "image/png" -> Workbook.PICTURE_TYPE_PNG;
            case "image/jpeg", "image/jpg" ->
                    Workbook.PICTURE_TYPE_JPEG;
            default -> -1;
        };
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

    private record ImageData(
            byte[] bytes,
            int pictureType) {
    }

    /** Excel 제목 행에 사용할 굵은 글꼴 스타일을 생성한다. */
    private CellStyle createHeaderStyle(Workbook workbook) {

        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

}
