package maoomWeb.ire.user.controller;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.service.DitaValidationWorkflowService;
import maoomWeb.ire.user.service.DitaValidationProgressService;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.SchematronFileService;
import maoomWeb.ire.user.service.SchematronHistoryService;

/** 검사 화면의 ditamap 조회, 실행, 진행 조회 및 HTML 보고서 열기를 제공한다. */
@RestController
public class DitaValidationController {
    private final DitaValidationWorkflowService workflow;
    private final SchematronHistoryService history;
    private final SchematronFileService schematronFiles;
    private final DitaValidationProgressService progress;
    private final CurrentUserService currentUserService;

    public DitaValidationController(DitaValidationWorkflowService workflow, SchematronHistoryService history,
                                    SchematronFileService schematronFiles, DitaValidationProgressService progress,
                                    CurrentUserService currentUserService) {
        this.workflow = workflow;
        this.history = history;
        this.schematronFiles = schematronFiles;
        this.progress = progress;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/dita-validation/schematrons")
    public List<SchematronChoice> schematrons() throws Exception {
        return schematronFiles.latestChoices();
    }

    @PostMapping("/api/dita-validation/maps")
    public List<MapItem> maps(@RequestBody PathRequest request) throws Exception {
        return workflow.findMaps(request == null ? null : request.inputPath());
    }

    @PostMapping("/api/dita-validation/tree")
    public MapTree tree(@RequestBody MapRequest request) throws Exception {
        return workflow.loadTree(request == null ? null : request.mapPath());
    }

    @PostMapping("/api/dita-validation/run")
    public RunResponse run(@RequestBody RunRequest request, Authentication authentication) throws Exception {
        return workflow.run(request, authentication);
    }

    @GetMapping("/api/dita-validation/progress/{token}")
    public ProgressResponse progress(@PathVariable String token) {
        // 긴 동기 검사 요청과 별도로 호출되어 현재 처리 파일 수를 화면에 갱신한다.
        return progress.get(token);
    }

    @GetMapping(value="/api/dita-validation/reports/{id}", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> report(@PathVariable Long id) throws Exception {
        // 브라우저가 서버 파일 경로에 직접 접근하지 않도록 이력 번호를 통해 HTML을 전달한다.
        Path path = history.reportPath(id);
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(Files.readAllBytes(path));
    }

    @GetMapping("/api/dita-validation/reports/{id}/state")
    public List<ReportStateItem> reportState(@PathVariable Long id) {
        return history.findReportStates(id);
    }

    @PostMapping("/api/dita-validation/reports/{id}/state")
    public ReportStateItem saveReportState(@PathVariable Long id, @RequestBody ReportStateRequest request,
                                           Authentication authentication) {
        if (request == null) throw new IllegalArgumentException("저장할 확인 상태가 없습니다.");
        return history.saveReportState(id, request.issueId(), request.checked(),
                currentUserService.getUserId(authentication), currentUserService.getUserName(authentication));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> badRequest(Exception exception) {
        return ResponseEntity.badRequest().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
