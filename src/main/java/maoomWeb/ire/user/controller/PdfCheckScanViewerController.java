package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.PdfCheckScanViewerResult;
import maoomWeb.ire.user.dto.PdfCheckScanViewerRunRequest;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.PdfCheckScanViewerService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * 사용자 메인 화면의 '인쇄데이터 검증' 카드에서 호출하는 REST 컨트롤러.
 *
 * <p>화면에서 받은 대상 폴더, 매치테이블 경로/파일명, 결과 저장 경로를
 * CLI 실행 서비스로 넘긴다.</p>
 */
@RestController
public class PdfCheckScanViewerController {

    private final PdfCheckScanViewerService pdfCheckScanViewerService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public PdfCheckScanViewerController(
            PdfCheckScanViewerService pdfCheckScanViewerService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.pdfCheckScanViewerService = pdfCheckScanViewerService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    /** 인쇄데이터 검증 CLI를 실행해 결과 xlsx를 생성한다. */
    @PostMapping(
            value = "/api/pdf-check-scan-viewer/run",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public PdfCheckScanViewerResult run(
            @RequestBody PdfCheckScanViewerRunRequest request,
            Authentication authentication) {
        String targetDirectory = request == null ? null : request.targetDirectory();
        String outputDirectory = request == null ? null : request.outputDirectory();
        Long logId = projectExecutionLogService.start(
                "PRINT_CHECK",
                "인쇄데이터 검증",
                currentUserService.getUserId(authentication),
                targetDirectory,
                "인쇄데이터 검증 CLI를 실행합니다.");

        try{
            PdfCheckScanViewerResult result = pdfCheckScanViewerService.run(
                    targetDirectory,
                    outputDirectory);
            projectExecutionLogService.success(
                    logId,
                    result.resultPath(),
                    buildSuccessLogMessage(result));
            return result;
        }catch(RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    private String buildSuccessLogMessage(PdfCheckScanViewerResult result) {
        if(result == null){
            return "인쇄데이터 검증 완료";
        }

        StringBuilder message = new StringBuilder("인쇄데이터 검증 완료");
        if(result.log() != null && !result.log().isBlank()){
            message.append(System.lineSeparator())
                    .append(result.log());
        }
        return message.toString();
    }

    /** EXE 경로 누락, 파일 없음, 실행 실패를 팝업에서 읽을 수 있는 한글 문장으로 반환한다. */
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleLaunchException(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
