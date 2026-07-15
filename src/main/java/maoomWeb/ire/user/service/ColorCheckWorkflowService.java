package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.ColorCheckWorkbookResponse;
import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;

/**
 * 컬러체크 화면의 큰 실행 흐름을 조율한다.
 *
 * <p>컨트롤러는 HTTP 요청/응답만 담당하고, 이 서비스가 PDF 분석 엑셀 생성,
 * 검토 엑셀의 최종 발주 양식 변환, DB 반영, 실행 로그 기록을 한 흐름으로 묶는다.</p>
 */
@Service
public class ColorCheckWorkflowService {

    private final ColorCheckExportService colorCheckExportService;
    private final ColorCheckFinalWorkbookService finalWorkbookService;
    private final DrawingColorCheckService drawingColorCheckService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public ColorCheckWorkflowService(
            ColorCheckExportService colorCheckExportService,
            ColorCheckFinalWorkbookService finalWorkbookService,
            DrawingColorCheckService drawingColorCheckService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.colorCheckExportService = colorCheckExportService;
        this.finalWorkbookService = finalWorkbookService;
        this.drawingColorCheckService = drawingColorCheckService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    /** 업로드된 PDF에서 도안 분류용 검토 엑셀을 만든다. */
    public ColorCheckWorkbookResponse createReviewWorkbook(
            MultipartFile file,
            String userId)
            throws IOException {

        Long logId = projectExecutionLogService.start(
                "COLOR_CHECK",
                "컬러체크 검토 엑셀 생성",
                userId,
                file.getOriginalFilename(),
                "PDF에서 도안명을 추출해 검토용 XLSX를 생성합니다.");

        try{
            byte[] excel = colorCheckExportService.createWorkbook(
                    file.getBytes());
            String fileName = createOutputFileName(file.getOriginalFilename());
            projectExecutionLogService.success(
                    logId,
                    fileName,
                    "검토 엑셀 생성 완료, "
                    + excel.length
                    + " bytes");
            return new ColorCheckWorkbookResponse(
                    fileName,
                    excel,
                    false,
                    emptyImportResult());
        }catch(IOException | RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    /** 검토 엑셀을 최종 발주 양식으로 변환하고, 선택에 따라 V/X 값을 DB에도 반영한다. */
    public ColorCheckWorkbookResponse createFinalWorkbook(
            MultipartFile file,
            boolean updateDatabase,
            String userId)
            throws IOException {

        Long logId = projectExecutionLogService.start(
                "COLOR_CHECK",
                "컬러체크 최종 발주 엑셀 생성",
                userId,
                file.getOriginalFilename(),
                updateDatabase
                ? "검토 엑셀을 최종 양식으로 변환하고 V/X 값을 DB에 반영합니다."
                : "검토 엑셀을 최종 양식으로만 변환합니다.");
        String extension = file.getOriginalFilename()
                .toLowerCase()
                .endsWith(".xls")
                ? ".xls"
                : ".xlsx";
        Path uploadedFile = Files.createTempFile(
                "color-check-import-",
                extension);
        Path outputDirectory = Files.createTempDirectory(
                "color-check-output-");
        Path finalWorkbook = null;

        try{
            file.transferTo(uploadedFile);
            finalWorkbook =
                    finalWorkbookService.createFinalWorkbook(
                            uploadedFile,
                            file.getOriginalFilename(),
                            outputDirectory);

            DrawingColorCheckImportResult result = updateDatabase
                    ? importColorCheckValues(uploadedFile)
                    : emptyImportResult();
            byte[] excel = Files.readAllBytes(finalWorkbook);
            String fileName = finalWorkbook.getFileName()
                    .toString();
            projectExecutionLogService.success(
                    logId,
                    fileName,
                    "최종 엑셀 생성 완료, DB 반영 "
                    + result.insertedCount()
                    + "건 추가/"
                    + result.updatedCount()
                    + "건 수정");
            return new ColorCheckWorkbookResponse(
                    fileName,
                    excel,
                    updateDatabase,
                    result);
        }catch(IOException | RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }finally{
            deleteIfExists(uploadedFile);
            deleteIfExists(finalWorkbook);
            deleteIfExists(outputDirectory);
        }
    }

    private DrawingColorCheckImportResult importColorCheckValues(
            Path uploadedFile)
            throws IOException {
        try(InputStream input = Files.newInputStream(uploadedFile)){
            return drawingColorCheckService.importExcel(input);
        }
    }

    private DrawingColorCheckImportResult emptyImportResult() {
        return new DrawingColorCheckImportResult(
                0,
                0,
                0,
                0,
                0,
                List.of());
    }

    /** 원본 PDF 이름 뒤에 "_컬러체크.xlsx"를 붙여 기본 파일명을 만든다. */
    private String createOutputFileName(String originalName) {
        String baseName = originalName == null
                ? "color-check"
                : originalName.replaceFirst("(?i)\\.pdf$", "");
        return baseName + "_도안분류용.xlsx";
    }

    private void deleteIfExists(Path path) throws IOException {
        if(path != null){
            Files.deleteIfExists(path);
        }
    }
}
