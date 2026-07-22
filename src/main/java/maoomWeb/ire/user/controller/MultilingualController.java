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
 * 다국어 변환 팝업의 실행 API.
 *
 * <p>화면은 입력 경로와 XML 입력용 ditamap 이름만 보낸다. 실제 경로 검증,
 * 작업 폴더 생성, classpath 리소스 복사, 배치 실행, Result_Folder 복사는
 * {@link MultilingualConversionService}가 담당한다.</p>
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
    /** 다국어 변환 실행 버튼 클릭 시 호출된다. 성공하면 입력 경로의 Result_Folder를 반환한다. */
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
