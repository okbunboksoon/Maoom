package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;

class CommentExportServiceTest {

    private static final byte[] PNG_1X1 = {
            (byte) 0x89, 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d,
            0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00,
            0x00, 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0x00, 0x00, 0x00,
            0x0d, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xd7, 0x63,
            (byte) 0xf8, (byte) 0xcf, (byte) 0xc0,
            (byte) 0xf0, 0x1f, 0x00, 0x05,
            0x00, 0x01, (byte) 0xff, (byte) 0x89,
            (byte) 0x99, 0x3d, 0x1d, 0x00,
            0x00, 0x00, 0x00, 0x49,
            0x45, 0x4e, 0x44, (byte) 0xae,
            0x42, 0x60, (byte) 0x82
    };

    @Test
    void embedsCommentImageAsThumbnail(
            @TempDir Path uploadRoot) throws Exception {

        CommentMapper mapper = mock(CommentMapper.class);
        CommentDto comment = new CommentDto();
        comment.setCommentId(10L);
        comment.setPdfId(1L);
        comment.setPageNum(3);
        comment.setUserId("admin");
        comment.setUserName("관리자");
        comment.setCommentText("이미지 확인");
        comment.setStatus("RESOLVED");
        comment.setCreateDt(LocalDateTime.of(
                2026, 6, 15, 8, 30));

        CommentAttachmentDto attachment =
                new CommentAttachmentDto();
        attachment.setAttachmentId(20L);
        attachment.setCommentId(10L);
        attachment.setOriginalName("capture.png");
        attachment.setStoredName("stored-image");
        attachment.setContentType("image/png");

        CommentReplyDto reply = new CommentReplyDto();
        reply.setReplyId(30L);
        reply.setCommentId(10L);
        reply.setUserId("reply-user");
        reply.setUserName("답글 작성자");
        reply.setReplyText("답글");
        reply.setCreateDt("2026-06-15 08:31:00");

        Files.write(
                uploadRoot.resolve("stored-image"),
                PNG_1X1);

        when(mapper.getCommentList(1L, "ALL"))
                .thenReturn(List.of(comment));
        when(mapper.getReplyListByCommentIds(List.of(10L)))
                .thenReturn(List.of(reply));
        when(mapper.getAttachmentListByCommentIds(List.of(10L)))
                .thenReturn(List.of(attachment));
        when(mapper.getAttachmentListByReplyIds(List.of(30L)))
                .thenReturn(List.of());

        CommentExportService service =
                new CommentExportService(
                        mapper,
                        uploadRoot.toString());
        byte[] output = service.createWorkbook(1L);

        try(XSSFWorkbook workbook =
                    new XSSFWorkbook(
                            new ByteArrayInputStream(output))){
            assertThat(workbook.getAllPictures()).hasSize(1);
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(0)
                    .getCell(0)
                    .getStringCellValue())
                    .isEqualTo("상태");
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(1)
                    .getCell(0)
                    .getStringCellValue())
                    .isEqualTo("해결");
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(0)
                    .getCell(6)
                    .getStringCellValue())
                    .isEqualTo("첨부 이미지");
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(1)
                    .getCell(3)
                    .getStringCellValue())
                    .isEqualTo("관리자");
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(2)
                    .getCell(3)
                    .getStringCellValue())
                    .isEqualTo("답글 작성자");
            assertThat(
                    workbook.getSheet("댓글 목록")
                    .getRow(1)
                    .getHeightInPoints())
                    .isGreaterThan(50);
        }
    }
}
