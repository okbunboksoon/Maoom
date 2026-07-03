package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationInk;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.api.services.drive.Drive;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;

class CommentPdfExportServiceTest {

    @Test
    void exportsCommentsAsLinkedNativePdfAnnotations()
            throws Exception {

        byte[] sourcePdf;

        try(PDDocument source = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            source.addPage(
                    new PDPage(
                            new PDRectangle(600, 800)));
            source.save(output);
            sourcePdf = output.toByteArray();
        }

        CommentMapper mapper = mock(CommentMapper.class);
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.Get get = mock(Drive.Files.Get.class);
        CommentDto rectangle = comment(10L, "RECT");
        CommentDto highlight = comment(11L, "TEXT");
        CommentDto callout = comment(12L, "CALLOUT");
        CommentDto drawing = comment(13L, "DRAW");
        CommentReplyDto reply = new CommentReplyDto();
        CommentAttachmentDto commentImage =
                attachment(
                        100L,
                        10L,
                        null,
                        "comment.png",
                        "comment-image");
        CommentAttachmentDto replyImage =
                attachment(
                        101L,
                        10L,
                        20L,
                        "reply.png",
                        "reply-image");
        CommentAttachmentDto replyXml =
                attachment(
                        102L,
                        10L,
                        20L,
                        "12asis-tobe_ko.xml",
                        "reply-xml");
        replyXml.setContentType("application/xml");

        writeImage(
                uploadRoot.resolve("comment-image"),
                Color.RED);
        writeImage(
                uploadRoot.resolve("reply-image"),
                Color.BLUE);
        Files.writeString(
                uploadRoot.resolve("reply-xml"),
                "<root><value>첨부파일</value></root>");

        rectangle.setCommentCode("C-0010");
        rectangle.setRectX(new BigDecimal("0.25"));
        rectangle.setRectY(new BigDecimal("0.50"));
        rectangle.setRectW(new BigDecimal("0.20"));
        rectangle.setRectH(new BigDecimal("0.10"));
        rectangle.setUserName("작성자");
        rectangle.setStatus("RESOLVED");
        rectangle.setCommentText("본문 코멘트");
        rectangle.setCreateDt(
                LocalDateTime.of(
                        2026, 6, 19, 9, 28));

        highlight.setRectX(new BigDecimal("0.10"));
        highlight.setRectY(new BigDecimal("0.10"));
        highlight.setRectW(new BigDecimal("0.20"));
        highlight.setRectH(new BigDecimal("0.05"));
        highlight.setDrawingPath(
                "{\"type\":\"text-highlight-rects\","
                + "\"rects\":[{\"x\":0.10,\"y\":0.10,"
                + "\"w\":0.20,\"h\":0.05}]}");

        callout.setRectX(new BigDecimal("0.55"));
        callout.setRectY(new BigDecimal("0.25"));
        callout.setRectW(BigDecimal.ZERO);
        callout.setRectH(BigDecimal.ZERO);
        callout.setCommentText("말풍선 코멘트");

        drawing.setRectX(new BigDecimal("0.55"));
        drawing.setRectY(new BigDecimal("0.55"));
        drawing.setRectW(new BigDecimal("0.20"));
        drawing.setRectH(new BigDecimal("0.10"));
        drawing.setDrawingPath(
                "[{\"x\":0.55,\"y\":0.55},"
                + "{\"x\":0.65,\"y\":0.60},"
                + "{\"x\":0.75,\"y\":0.55}]");

        reply.setReplyId(20L);
        reply.setCommentId(10L);
        reply.setUserName("답글 작성자");
        reply.setReplyText("답글 내용");
        reply.setCreateDt("2026-06-19 09:29:00");

        when(mapper.getCommentList(1L, "ALL"))
                .thenReturn(
                        List.of(
                                rectangle,
                                highlight,
                                callout,
                                drawing));
        when(mapper.getReplyListByCommentIds(
                List.of(10L, 11L, 12L, 13L)))
                .thenReturn(List.of(reply));
        when(mapper.getAttachmentListByCommentIds(
                List.of(10L, 11L, 12L, 13L)))
                .thenReturn(List.of(commentImage));
        when(mapper.getAttachmentListByReplyIds(
                List.of(20L)))
                .thenReturn(
                        List.of(
                                replyImage,
                                replyXml));
        when(drive.files()).thenReturn(files);
        when(files.get("drive-file")).thenReturn(get);
        when(get.setSupportsAllDrives(true)).thenReturn(get);
        doAnswer(invocation -> {
            OutputStream stream = invocation.getArgument(0);
            stream.write(sourcePdf);
            return null;
        }).when(get).executeMediaAndDownloadTo(
                any(OutputStream.class));

        CommentPdfExportService service =
                new CommentPdfExportService(
                        mapper,
                        drive,
                        uploadRoot.toString());
        byte[] result =
                service.createAnnotatedPdf(
                        1L,
                        "drive-file");
        writePreviewWhenRequested(result);

        try(PDDocument exported =
                Loader.loadPDF(result)){
            List<PDAnnotation> annotations =
                    exported.getPage(0)
                    .getAnnotations();

            assertThat(annotations)
                    .hasSize(8);
            assertThat(annotations)
                    .satisfiesExactly(
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationSquare.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationText.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationFileAttachment.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationFileAttachment.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationFileAttachment.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationHighlight.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationFreeText.class),
                            annotation -> assertThat(annotation)
                                    .isExactlyInstanceOf(
                                            PDAnnotationInk.class));

            PDAnnotationSquare exportedRectangle =
                    (PDAnnotationSquare) annotations.get(0);
            PDAnnotationText exportedReply =
                    (PDAnnotationText) annotations.get(1);
            PDAnnotationFileAttachment exportedCommentImage =
                    (PDAnnotationFileAttachment)
                    annotations.get(2);
            PDAnnotationFileAttachment exportedReplyImage =
                    (PDAnnotationFileAttachment)
                    annotations.get(3);
            PDAnnotationFileAttachment exportedReplyXml =
                    (PDAnnotationFileAttachment)
                    annotations.get(4);
            PDAnnotationHighlight exportedHighlight =
                    (PDAnnotationHighlight) annotations.get(5);
            PDAnnotationFreeText exportedCallout =
                    (PDAnnotationFreeText) annotations.get(6);
            PDAnnotationInk exportedInk =
                    (PDAnnotationInk) annotations.get(7);

            assertThat(exportedRectangle.getSubject())
                    .isEqualTo("C-0010 · 해결");
            assertThat(exportedRectangle.getContents())
                    .contains(
                            "[해결] 작성자",
                            "본문 코멘트")
                    .doesNotContain(
                            "답글 작성자",
                            "답글 내용");
            assertThat(exportedRectangle.getAnnotationName())
                    .isEqualTo("maoom-comment-10");
            assertThat(exportedRectangle.isPrinted())
                    .isTrue();
            assertThat(exportedRectangle.getAppearance())
                    .isNotNull();
            assertThat(exportedRectangle.getCreationDate())
                    .isNotNull();

            assertThat(exportedReply.getInReplyTo())
                    .isEqualTo(exportedRectangle);
            assertThat(exportedReply.getReplyType())
                    .isEqualTo(
                            PDAnnotationMarkup.RT_REPLY);
            assertThat(exportedReply.getTitlePopup())
                    .isEqualTo("답글 작성자");
            assertThat(exportedReply.getContents())
                    .isEqualTo("답글 내용");
            assertThat(exportedReply.getAnnotationName())
                    .isEqualTo("maoom-reply-20");
            assertThat(exportedReply.getCreationDate())
                    .isNotNull();
            assertThat(exportedReply.isNoView())
                    .isTrue();

            assertThat(exportedCommentImage.getInReplyTo())
                    .isEqualTo(exportedRectangle);
            assertThat(exportedCommentImage.getContents())
                    .isEqualTo("comment.png");
            assertThat(exportedCommentImage.isNoView())
                    .isTrue();
            assertThat(exportedCommentImage.getAnnotationName())
                    .isEqualTo("maoom-attachment-100");

            PDComplexFileSpecification commentFile =
                    (PDComplexFileSpecification)
                    exportedCommentImage.getFile();
            assertThat(commentFile.getFilename())
                    .isEqualTo("comment.png");
            assertThat(commentFile.getEmbeddedFile())
                    .isNotNull();
            assertThat(commentFile.getEmbeddedFile().getSubtype())
                    .isEqualTo("image/png");
            assertThat(commentFile.getEmbeddedFile().getSize())
                    .isGreaterThan(0);

            assertThat(exportedReplyImage.getInReplyTo())
                    .isEqualTo(exportedReply);
            assertThat(exportedReplyImage.getContents())
                    .isEqualTo("reply.png");
            assertThat(exportedReplyImage.isNoView())
                    .isTrue();
            assertThat(exportedReplyImage.getAnnotationName())
                    .isEqualTo("maoom-attachment-101");

            assertThat(exportedReplyXml.getInReplyTo())
                    .isEqualTo(exportedReply);
            assertThat(exportedReplyXml.getContents())
                    .isEqualTo("12asis-tobe_ko.xml");
            assertThat(exportedReplyXml.getAnnotationName())
                    .isEqualTo("maoom-attachment-102");

            PDComplexFileSpecification xmlFile =
                    (PDComplexFileSpecification)
                    exportedReplyXml.getFile();
            assertThat(xmlFile.getFilename())
                    .isEqualTo("12asis-tobe_ko.xml");
            assertThat(xmlFile.getEmbeddedFile())
                    .isNotNull();
            assertThat(xmlFile.getEmbeddedFile().getSubtype())
                    .isEqualTo("application/xml");
            assertThat(xmlFile.getEmbeddedFile().getSize())
                    .isGreaterThan(0);

            assertThat(exportedHighlight.getQuadPoints())
                    .hasSize(8);
            assertThat(exportedHighlight.getAppearance())
                    .isNotNull();

            assertThat(exportedCallout.getIntent())
                    .isEqualTo(
                            PDAnnotationFreeText
                            .IT_FREE_TEXT_CALLOUT);
            assertThat(exportedCallout.getCallout())
                    .hasSize(6);
            assertThat(exportedCallout.getContents())
                    .contains("말풍선 코멘트");

            assertThat(exportedInk.getInkList().length)
                    .isEqualTo(1);
            assertThat(exportedInk.getInkList()[0])
                    .hasSize(6);
            assertThat(exportedInk.getAppearance())
                    .isNotNull();
            assertThat(exported.getPage(0).hasContents())
                    .isFalse();
        }
    }

    @TempDir
    Path uploadRoot;

    private void writeImage(
            Path path,
            Color color) throws Exception {

        BufferedImage image =
                new BufferedImage(
                        80,
                        50,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        try{
            graphics.setColor(color);
            graphics.fillRect(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight());
            graphics.setColor(Color.WHITE);
            graphics.fillRect(12, 12, 56, 26);
        }finally{
            graphics.dispose();
        }

        ImageIO.write(
                image,
                "png",
                path.toFile());
    }

    private void writePreviewWhenRequested(
            byte[] pdf) throws Exception {

        String previewPath =
                System.getProperty(
                        "comment.pdf.preview");

        if(previewPath == null || previewPath.isBlank()){
            return;
        }

        Path target = Path.of(previewPath);
        Files.createDirectories(target.getParent());
        Files.write(target, pdf);
    }

    private CommentAttachmentDto attachment(
            long attachmentId,
            Long commentId,
            Long replyId,
            String originalName,
            String storedName) {

        CommentAttachmentDto attachment =
                new CommentAttachmentDto();
        attachment.setAttachmentId(attachmentId);
        attachment.setCommentId(commentId);
        attachment.setReplyId(replyId);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setContentType("image/png");
        return attachment;
    }

    private CommentDto comment(
            long id,
            String type) {
        CommentDto comment = new CommentDto();
        comment.setCommentId(id);
        comment.setPageNum(1);
        comment.setCommentType(type);
        comment.setUserId(type.toLowerCase() + "-user");
        comment.setStatus("OPEN");
        return comment;
    }
}
