package maoomWeb.ire.user.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.CommentExportResponse;

/**
 * 댓글 Excel/PDF 내보내기의 파일 생성과 실행 로그 기록을 담당한다.
 */
@Service
public class CommentExportWorkflowService {

    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CommentExportService exportService;
    private final CommentPdfExportService pdfExportService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public CommentExportWorkflowService(
            CommentExportService exportService,
            CommentPdfExportService pdfExportService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.exportService = exportService;
        this.pdfExportService = pdfExportService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    public CommentExportResponse createExcel(
            Long pdfId,
            String fileName,
            String userId)
            throws IOException {

        Long logId = projectExecutionLogService.start(
                "COMMENT_EXPORT",
                "댓글 Excel 내보내기",
                userId,
                "pdfId=" + pdfId,
                "PDF 댓글과 답글을 XLSX로 내보냅니다.");

        try{
            byte[] workbook = exportService.createWorkbook(pdfId);
            String exportFileName = buildCommentExportFileName(
                    fileName,
                    "xlsx");
            projectExecutionLogService.success(
                    logId,
                    exportFileName,
                    "댓글 Excel 생성 완료, "
                    + workbook.length
                    + " bytes");
            return new CommentExportResponse(
                    exportFileName,
                    workbook,
                    XLSX_MEDIA_TYPE);
        }catch(IOException | RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    public CommentExportResponse createPdf(
            Long pdfId,
            String fileId,
            String fileName,
            String userId)
            throws IOException {

        Long logId = projectExecutionLogService.start(
                "COMMENT_EXPORT",
                "댓글 PDF 내보내기",
                userId,
                "pdfId=" + pdfId + ", fileId=" + fileId,
                "화면 댓글을 PDF 주석으로 변환해 PDF를 내보냅니다.");

        try{
            byte[] pdf = pdfExportService.createAnnotatedPdf(
                    pdfId,
                    fileId);
            String exportFileName = buildCommentExportFileName(
                    fileName,
                    "pdf");
            projectExecutionLogService.success(
                    logId,
                    exportFileName,
                    "댓글 PDF 생성 완료, "
                    + pdf.length
                    + " bytes");
            return new CommentExportResponse(
                    exportFileName,
                    pdf,
                    MediaType.APPLICATION_PDF);
        }catch(IOException | RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    /** Excel과 PDF에 동일한 댓글 내보내기 파일명 규칙을 적용한다. */
    private String buildCommentExportFileName(
            String fileName,
            String extension) {

        String baseName =
                fileName == null || fileName.isBlank()
                ? "PDF"
                : fileName.trim();

        baseName = baseName.replaceFirst(
                "(?i)\\.pdf$",
                "");
        baseName = baseName.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_");

        String timestamp =
                LocalDateTime.now().format(EXPORT_TIME_FORMAT);

        return timestamp
                + "_"
                + baseName
                + "_Comment."
                + extension;
    }
}
