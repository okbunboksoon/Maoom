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
 * 유저 메인 "정제" 카드에서 열리는 DITA 정제 팝업의 REST 입구다.
 *
 * <p>화면 위치: {@code templates/user/revision/revisionPopup.html}<br>
 * 호출 JS: 같은 HTML 하단 스크립트에서 {@code fetch('/api/revision/options')},
 * {@code fetch('/api/revision/run')}를 호출한다.<br>
 * 연결 라우트: {@code UserController#revisionList()}가 {@code /revision/list}
 * 팝업 화면을 반환하고, {@code userMain.html}의 {@code openRevisionPopup()}이
 * 그 팝업을 연다.<br>
 * 연결 서비스: {@link RevisionPipelineService}가 입력 경로 검증, revision-tool
 * 리소스 준비, Saxon/XSL 실행, 결과 폴더와 {@code revision.log} 생성을 담당한다.</p>
 *
 * <p>수정 시 주의점: 컨트롤러는 요청/응답과 실행 이력 저장만 담당한다.
 * 단계 목록, 실행 순서, 파일 복사 규칙을 바꿀 때는 서비스와 화면의 옵션 표시가
 * 함께 맞는지 확인해야 한다.</p>
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
