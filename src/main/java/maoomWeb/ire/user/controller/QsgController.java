package maoomWeb.ire.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import maoomWeb.ire.user.dto.QsgRunRequest;
import maoomWeb.ire.user.dto.QsgRunResult;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;
import maoomWeb.ire.user.service.QsgApplyService;

/**
 * QSG 팝업의 실행 API.
 *
 * <p>화면은 입력 경로와 언어 코드 목록만 보낸다. 실제 경로 검증, 작업 폴더
 * 생성, classpath 리소스 복사, 언어별 배치 실행, Result_Folder 복사는
 * {@link QsgApplyService}가 담당한다.</p>
 */
@RestController
@RequestMapping("/api/qsg")
public class QsgController {

    private final QsgApplyService qsgApplyService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public QsgController(
            QsgApplyService qsgApplyService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.qsgApplyService = qsgApplyService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    @PostMapping("/run")
    /** QSG 실행 버튼 클릭 시 호출된다. 성공하면 입력 경로의 Result_Folder를 반환한다. */
    public QsgRunResult run(
            @RequestBody QsgRunRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "QSG",
                "QSG 실행",
                currentUserService.getUserId(authentication),
                request == null ? null : request.inputPath(),
                "선택한 언어 QSG 배치를 실행합니다.");
        QsgRunResult result = qsgApplyService.run(request);

        if(result.success()){
            projectExecutionLogService.success(
                    logId,
                    result.outputPath(),
                    "QSG 변환 완료, "
                    + result.languageCodes().size()
                    + "개 언어");
        }else{
            projectExecutionLogService.fail(
                    logId,
                    new IllegalStateException(
                            result.logs().isEmpty()
                            ? "QSG 실행 실패"
                            : result.logs().get(result.logs().size() - 1)));
        }

        return result;
    }
}
