package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.IndexExtractRequest;
import maoomWeb.ire.user.dto.IndexExtractResult;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.IndexExtractService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/** Index 추출 화면의 AJAX 요청을 배치 실행 서비스로 넘기는 REST 컨트롤러. */
@RestController
public class IndexExtractController {

    private final IndexExtractService indexExtractService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public IndexExtractController(
            IndexExtractService indexExtractService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.indexExtractService = indexExtractService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    @PostMapping("/api/index-extract/run")
    public IndexExtractResult run(
            @RequestBody IndexExtractRequest request,
            Authentication authentication) {

        String inputPath = request == null ? null : request.ditaPath();
        Long logId = projectExecutionLogService.start(
                "INDEX_EXTRACT",
                "Index 추출 실행",
                currentUserService.getUserId(authentication),
                inputPath,
                "DITA Index 추출 배치를 실행합니다.");

        try{
            IndexExtractResult result = indexExtractService.run(request);
            projectExecutionLogService.success(
                    logId,
                    result.reportPath(),
                    "Index 추출 완료");
            return result;
        }catch(IllegalArgumentException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
