package maoomWeb.ire.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.MultilingualRunRequest;
import maoomWeb.ire.user.dto.MultilingualRunResult;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.MultilingualConversionService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * 다국어 변환 팝업과 배치 실행 서비스를 연결한다.
 */
@RestController
@RequestMapping("/api/multilingual")
public class MultilingualController {

    private final MultilingualConversionService multilingualConversionService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public MultilingualController(
            MultilingualConversionService multilingualConversionService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.multilingualConversionService = multilingualConversionService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    @PostMapping("/run")
    public MultilingualRunResult run(
            @RequestBody MultilingualRunRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "MULTILINGUAL",
                "다국어 변환 실행",
                currentUserService.getUserId(authentication),
                request == null ? null : request.inputPath(),
                "다국어 변환 배치를 실행합니다.");
        MultilingualRunResult result =
                multilingualConversionService.run(request);

        if (result.success()) {
            projectExecutionLogService.success(
                    logId,
                    result.outputPath(),
                    "다국어 변환 완료");
        } else {
            projectExecutionLogService.fail(
                    logId,
                    new IllegalStateException(
                            result.logs().isEmpty()
                            ? "다국어 변환 실패"
                            : result.logs().get(result.logs().size() - 1)));
        }

        return result;
    }
}
