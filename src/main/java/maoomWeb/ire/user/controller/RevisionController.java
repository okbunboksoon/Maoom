package maoomWeb.ire.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import maoomWeb.ire.user.dto.RevisionOptionDto;
import maoomWeb.ire.user.dto.RevisionRunRequest;
import maoomWeb.ire.user.dto.RevisionRunResult;
import maoomWeb.ire.user.service.RevisionPipelineService;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * DITA 정제 팝업과 실제 XSL 파이프라인 서비스를 연결하는 REST 컨트롤러.
 *
 * <p>{@code revisionPopup.html}은 이 컨트롤러에서 단계 목록을 조회하고,
 * 사용자가 선택한 경로와 단계 ID를 실행 요청으로 보낸다. 컨트롤러는 HTTP 요청
 * 변환만 담당하고 파일 검증과 Saxon 실행은 {@link RevisionPipelineService}에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/revision")
public class RevisionController {

    private final RevisionPipelineService revisionPipelineService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public RevisionController(
            RevisionPipelineService revisionPipelineService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.revisionPipelineService = revisionPipelineService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    /** 화면에 표시할 정제 단계 ID, 이름과 설명을 실행 순서대로 반환한다. */
    @GetMapping("/options")
    public List<RevisionOptionDto> getOptions() {
        return revisionPipelineService.getOptions();
    }

    /** 선택한 단계만 순서대로 실행하고 결과 경로와 로그를 반환한다. */
    @PostMapping("/run")
    public RevisionRunResult run(
            @RequestBody RevisionRunRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "REVISION",
                "DITA 정제 실행",
                currentUserService.getUserId(authentication),
                request == null ? null : request.inputPath(),
                "선택한 정제 파이프라인을 실행합니다.");
        RevisionRunResult result = revisionPipelineService.run(request);

        if(result.success()){
            projectExecutionLogService.success(
                    logId,
                    result.outputPath(),
                    "완료 단계 "
                    + result.completedOptions().size()
                    + "건");
        }else{
            projectExecutionLogService.fail(
                    logId,
                    new IllegalStateException(
                            result.logs().isEmpty()
                            ? "정제 실행 실패"
                            : result.logs().get(result.logs().size() - 1)));
        }

        return result;
    }
}
