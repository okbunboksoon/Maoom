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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.service.DitaValidationWorkflowService;
import maoomWeb.ire.user.service.DitaValidationProgressService;
import maoomWeb.ire.user.service.DitaValidationJobService;
import maoomWeb.ire.user.service.GomsExportService;
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
    private final DitaValidationJobService jobs;
    private final GomsExportService gomsExport;

    public DitaValidationController(DitaValidationWorkflowService workflow, SchematronHistoryService history,
                                    SchematronFileService schematronFiles, DitaValidationProgressService progress,
                                    CurrentUserService currentUserService, DitaValidationJobService jobs,
                                    GomsExportService gomsExport) {
        this.workflow = workflow;
        this.history = history;
        this.schematronFiles = schematronFiles;
        this.progress = progress;
        this.currentUserService = currentUserService;
        this.jobs = jobs;
        this.gomsExport = gomsExport;
    }

    @GetMapping("/api/dita-validation/schematrons")
    public List<SchematronChoice> schematrons() throws Exception {
        return schematronFiles.latestChoices();
    }

    @PostMapping("/api/dita-validation/maps")
    public List<MapItem> maps(@RequestBody PathRequest request) throws Exception {
        return workflow.findMaps(request == null ? null : request.inputPath());
    }

    @PostMapping("/api/dita-validation/goms/export")
    public GomsExportResponse exportGoms(@RequestBody GomsExportRequest request) throws Exception {
        return gomsExport.export(request == null ? null : request.inputPath());
    }

    @GetMapping("/api/dita-validation/recent-input-paths")
    public List<String> recentInputPaths(@RequestParam String executionMode,
                                         Authentication authentication) {
        return history.findRecentInputPaths(currentUserService.getUserId(authentication), executionMode, 10);
    }

    @GetMapping("/api/dita-validation/goms/reports")
    public List<GomsReportProject> gomsReports() throws Exception {
        return workflow.findGomsReports();
    }

    @GetMapping(value="/api/dita-validation/goms/reports/file", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> gomsReportFile(@RequestParam String project,
            @RequestParam String file) throws Exception {
        Path path = workflow.resolveGomsReport(project, file);
        String html = Files.readString(path, StandardCharsets.UTF_8);
        html = html.replaceAll("data-history-id=\"[^\"]*\"", "data-history-id=\"\"")
                .replace("isLocalFile=location.protocol==='file:'", "isLocalFile=true");
        String readOnlyScript = """
                <script>addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('.done').forEach(x=>x.disabled=true);['checkAll','uncheckAll'].forEach(id=>{let b=document.getElementById(id);if(b)b.disabled=true});let n=document.getElementById('stateNotice');if(n)n.textContent='DB 이력과 연결되지 않은 리포트이므로 읽기 전용입니다.'});</script>
                """;
        html = html.replace("</body>", readOnlyScript + "</body>");
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/api/dita-validation/tree")
    public MapTree tree(@RequestBody MapRequest request) throws Exception {
        return workflow.loadTree(request == null ? null : request.mapPath());
    }

    @PostMapping("/api/dita-validation/run")
    public JobAcceptedResponse run(@RequestBody RunRequest request, Authentication authentication) {
        return jobs.submit(request, authentication);
    }

    @GetMapping("/api/dita-validation/jobs/{jobId}")
    public JobStatusResponse job(@PathVariable String jobId, Authentication authentication) {
        return jobs.get(jobId, authentication);
    }

    @PostMapping("/api/dita-validation/jobs/{jobId}/cancel")
    public JobStatusResponse cancelJob(@PathVariable String jobId, Authentication authentication) {
        return jobs.cancel(jobId, authentication);
    }

    @GetMapping("/api/dita-validation/progress/{token}")
    public ProgressResponse progress(@PathVariable String token, Authentication authentication) {
        // 긴 동기 검사 요청과 별도로 호출되어 현재 처리 파일 수를 화면에 갱신한다.
        return jobs.getProgress(token, authentication);
    }

    @PostMapping("/api/dita-validation/progress/{token}/cancel")
    public JobStatusResponse cancel(@PathVariable String token, Authentication authentication) {
        return jobs.cancel(token, authentication);
    }

    @GetMapping(value="/api/dita-validation/reports/{id}", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> report(@PathVariable Long id) throws Exception {
        // 브라우저가 서버 파일 경로에 직접 접근하지 않도록 이력 번호를 통해 HTML을 전달한다.
        // 기존 보고서도 서버에서 열 때 DB 확인 상태를 원본 HTML에 먼저 반영한다.
        history.synchronizeReportFile(id);
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

    @PostMapping("/api/dita-validation/reports/{id}/state/batch")
    public List<ReportStateItem> saveReportStates(@PathVariable Long id,
            @RequestBody ReportStateBatchRequest request, Authentication authentication) {
        if (request == null) throw new IllegalArgumentException("저장할 확인 상태가 없습니다.");
        return history.saveReportStates(id, request.issueIds(), request.checked(),
                currentUserService.getUserId(authentication), currentUserService.getUserName(authentication));
    }

    @PostMapping("/api/dita-validation/reports/{id}/memo")
    public ReportStateItem saveReportMemo(@PathVariable Long id, @RequestBody ReportMemoRequest request) {
        if (request == null) throw new IllegalArgumentException("저장할 메모가 없습니다.");
        return history.saveReportMemo(id, request.issueId(), request.memo());
    }

    @PostMapping("/api/dita-validation/reports/{id}/false-positive")
    public ReportStateItem saveFalsePositive(@PathVariable Long id, @RequestBody ReportFalseRequest request,
                                             Authentication authentication) {
        if (request == null) throw new IllegalArgumentException("저장할 False 상태가 없습니다.");
        return history.saveFalsePositive(id, request.issueId(), request.falsePositive(),
                currentUserService.getUserId(authentication), currentUserService.getUserName(authentication));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> badRequest(Exception exception) {
        return ResponseEntity.badRequest().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
