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
 * 유저 메인 "다국어 변환" 카드에서 열리는 변환 팝업의 REST 입구다.
 *
 * <p>화면 위치: {@code templates/user/multilingual/multilingualPopup.html}<br>
 * 호출 JS: 같은 HTML 하단 스크립트에서 {@code fetch('/api/multilingual/run')}
 * 으로 실행 요청을 보낸다.<br>
 * 연결 라우트: {@code UserController#multilingualList()}가
 * {@code /multilingual/list} 팝업 화면을 반환하고, {@code userMain.html}의
 * {@code openMultilingualPopup()}이 그 팝업을 연다.<br>
 * 연결 서비스: {@link MultilingualConversionService}가 입력 경로 검증,
 * 작업 폴더 생성, classpath 리소스 복사, 배치 실행, {@code Result_Folder}
 * 복사와 {@code multilingual.log} 저장을 담당한다.</p>
 *
 * <p>수정 시 주의점: XML 입력일 때만 화면의 ditamap 이름이 bookmap 생성에
 * 쓰인다. 입력값 추가나 배치 옵션 변경 시 DTO, 화면 JS, 서비스 검증을 같이
 * 확인해야 한다.</p>
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

    /** 다국어 변환 실행 버튼 클릭 시 호출된다. 성공하면 입력 경로의 Result_Folder를 반환한다. */
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
