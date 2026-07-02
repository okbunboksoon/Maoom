package maoomWeb.ire.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.RevisionOptionDto;
import maoomWeb.ire.user.dto.RevisionRunRequest;
import maoomWeb.ire.user.dto.RevisionRunResult;
import maoomWeb.ire.user.service.RevisionPipelineService;

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

    public RevisionController(
            RevisionPipelineService revisionPipelineService) {
        this.revisionPipelineService = revisionPipelineService;
    }

    /** 화면에 표시할 정제 단계 ID, 이름과 설명을 실행 순서대로 반환한다. */
    @GetMapping("/options")
    public List<RevisionOptionDto> getOptions() {
        return revisionPipelineService.getOptions();
    }

    /** 선택한 단계만 순서대로 실행하고 결과 경로와 로그를 반환한다. */
    @PostMapping("/run")
    public RevisionRunResult run(
            @RequestBody RevisionRunRequest request) {
        return revisionPipelineService.run(request);
    }
}
