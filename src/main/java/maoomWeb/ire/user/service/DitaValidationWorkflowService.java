package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sts.dita.core.MapModel;
import com.sts.dita.core.MapNode;
import com.sts.dita.core.ValidationRequest;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.ValidationScope;
import com.sts.dita.core.ValidationIssue;
import maoomWeb.ire.user.config.DitaValidationProperties;

/**
 * 검사 화면 요청을 실제 Schematron 실행으로 연결하는 업무 흐름이다.
 * 입력 검증, 전용 이력, 규칙 스냅샷, 진행 상태, HTML 결과 생성을 한곳에서 조정한다.
 */
@Service
public class DitaValidationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(DitaValidationWorkflowService.class);
    private final DitaSchematronService validationService;
    private final SchematronFileService schematronFileService;
    private final DitaValidationProgressService progressService;
    private final CurrentUserService currentUserService;
    private final Path gomsOutputRoot;
    private final long timeoutNanos;
    private final DitaValidationTargetService targetService;
    private final DitaValidationStagingService stagingService;
    private final DitaValidationReportService reportService;
    private final DitaValidationHistoryCoordinator historyCoordinator;
    private final DitaValidationReportWriterService reportWriterService;

    @Autowired
    public DitaValidationWorkflowService(
            DitaSchematronService validationService,
            SchematronHistoryService historyService,
            SchematronFileService schematronFileService,
            CurrentUserService currentUserService,
            DitaValidationProgressService progressService,
            DitaValidationTargetService targetService,
            DitaValidationStagingService stagingService,
            DitaValidationReportService reportService,
            DitaValidationHistoryCoordinator historyCoordinator,
            DitaValidationReportWriterService reportWriterService,
            DitaValidationProperties properties) {
        this.validationService = validationService;
        this.schematronFileService = schematronFileService;
        this.currentUserService = currentUserService;
        this.progressService = progressService;
        this.gomsOutputRoot = Path.of(properties.getGomsOutputRoot()).toAbsolutePath().normalize();
        this.timeoutNanos = TimeUnit.MINUTES.toNanos(Math.max(1, properties.getTimeoutMinutes()));
        this.targetService = targetService;
        this.stagingService = stagingService;
        this.reportService = reportService;
        this.historyCoordinator = historyCoordinator;
        this.reportWriterService = reportWriterService;
    }

    DitaValidationWorkflowService(
            DitaSchematronService validationService,
            SchematronHistoryService historyService,
            SchematronFileService schematronFileService,
            CurrentUserService currentUserService,
            DitaValidationProgressService progressService,
            String reportDir,
            long timeoutMinutes,
            String gomsOutputRoot,
            String stagingRoot) {
        this.validationService = validationService;
        this.schematronFileService = schematronFileService;
        this.currentUserService = currentUserService;
        this.progressService = progressService;
        this.gomsOutputRoot = Path.of(gomsOutputRoot).toAbsolutePath().normalize();
        this.timeoutNanos = TimeUnit.MINUTES.toNanos(Math.max(1, timeoutMinutes));
        this.targetService = new DitaValidationTargetService(validationService, this.gomsOutputRoot);
        this.stagingService = new DitaValidationStagingService(progressService, Path.of(stagingRoot));
        this.reportService = new DitaValidationReportService(historyService, this.gomsOutputRoot);
        this.historyCoordinator = new DitaValidationHistoryCoordinator(historyService);
        this.reportWriterService = new DitaValidationReportWriterService(
                schematronFileService, Path.of(reportDir));
    }

    DitaValidationWorkflowService(DitaSchematronService validationService,
            SchematronHistoryService historyService, SchematronFileService schematronFileService,
            CurrentUserService currentUserService, DitaValidationProgressService progressService,
            String reportDir) {
        this(validationService, historyService, schematronFileService, currentUserService,
                progressService, reportDir, 30, "V:/ValidationCheck",
                Path.of(System.getProperty("java.io.tmpdir"), "maoomtool-dita-validation").toString());
    }

    DitaValidationWorkflowService(DitaSchematronService validationService,
            SchematronHistoryService historyService, SchematronFileService schematronFileService,
            CurrentUserService currentUserService, DitaValidationProgressService progressService,
            String reportDir, String gomsOutputRoot) {
        this(validationService, historyService, schematronFileService, currentUserService,
                progressService, reportDir, 30, gomsOutputRoot,
                Path.of(System.getProperty("java.io.tmpdir"), "maoomtool-dita-validation").toString());
    }

    DitaValidationWorkflowService(DitaSchematronService validationService,
            SchematronHistoryService historyService, SchematronFileService schematronFileService,
            CurrentUserService currentUserService, DitaValidationProgressService progressService,
            String reportDir, String gomsOutputRoot, String stagingRoot) {
        this(validationService, historyService, schematronFileService, currentUserService,
                progressService, reportDir, 30, gomsOutputRoot, stagingRoot);
    }

    DitaValidationWorkflowService(DitaSchematronService validationService,
            SchematronHistoryService historyService, SchematronFileService schematronFileService,
            CurrentUserService currentUserService, String reportDir) {
        this(validationService, historyService, schematronFileService, currentUserService,
                new DitaValidationProgressService(), reportDir, 30, "V:/ValidationCheck",
                Path.of(System.getProperty("java.io.tmpdir"), "maoomtool-dita-validation").toString());
    }

    public List<MapItem> findMaps(String inputPath) throws Exception {
        return targetService.findMaps(inputPath);
    }

    /** GOMS 결과 루트의 프로젝트별 report HTML을 최신 파일명 내림차순으로 반환한다. */
    public List<GomsReportProject> findGomsReports() throws Exception {
        return reportService.findGomsReports();
    }

    public Path resolveGomsReport(String projectName, String fileName) {
        return reportService.resolveGomsReport(projectName, fileName);
    }

    public MapTree loadTree(String mapPath) throws Exception {
        return targetService.loadTree(mapPath);
    }

    public RunResponse run(RunRequest request, org.springframework.security.core.Authentication authentication)
            throws Exception {
        // 웹 요청 검증부터 이력, 규칙 스냅샷, 검사, HTML 생성까지 한 실행 단위로 처리한다.
        if (request == null) throw new IllegalArgumentException("검사 요청이 없습니다.");
        Path root = targetService.validDirectory(request.inputPath());
        boolean gomsExecution = "GOMS".equalsIgnoreCase(request.executionMode());
        if (request.executionMode() != null && !request.executionMode().isBlank()
                && !gomsExecution && !"LOCAL_PATH".equalsIgnoreCase(request.executionMode())) {
            throw new IllegalArgumentException("올바른 실행 방식이 아닙니다.");
        }
        if (gomsExecution && (!root.startsWith(gomsOutputRoot) || root.equals(gomsOutputRoot))) {
            throw new IllegalArgumentException("GOMS 결과 경로가 아닙니다: " + root);
        }
        Path map = targetService.validMap(request.mapPath(), root);
        ValidationScope scope;
        try { scope = ValidationScope.valueOf(request.scope()); }
        catch (Exception ex) { throw new IllegalArgumentException("올바른 검사 범위를 선택해 주세요."); }
        Set<String> selected = request.selectedNodeIds() == null ? Set.of() : Set.copyOf(request.selectedNodeIds());
        if (scope != ValidationScope.ALL && selected.isEmpty()) {
            throw new IllegalArgumentException("검사할 트리 항목을 선택해 주세요.");
        }
        Path schema = schematronFileService.resolveLatest();
        String schemaHash = schematronFileService.sha256(schema);
        String summary = map.getFileName() + " / " + schema.getFileName() + " / " + scopeLabel(scope)
                + (selected.isEmpty() ? "" : " / 선택 " + selected.size() + "개");
        String progressToken = request.progressToken();
        DitaValidationExecutionContext context = new DitaValidationExecutionContext(
                root, map, gomsExecution, progressToken);
        String userId = currentUserService.getUserId(authentication);
        String userName = currentUserService.getUserName(authentication);
        progressService.start(progressToken);
        long deadline = System.nanoTime() + timeoutNanos;
        long stageStarted = System.nanoTime();
        try {
            context.historyId(historyCoordinator.start(userId, root, map, schema, schemaHash,
                    scope.name(), String.join(",", selected), summary,
                    request.requestedInputPath(), request.executionMode()));
            logStage(context, "HISTORY", stageStarted);
            stageStarted = System.nanoTime();
            Path historyFolder = reportWriterService.historyFolder(context.historyId());
            context.snapshot(reportWriterService.snapshot(schema, schemaHash, historyFolder));
            logStage(context, "SNAPSHOT", stageStarted);
            stageStarted = System.nanoTime();
            Path validationRoot = root;
            Path validationMap = map;
            if (gomsExecution) {
                context.stagingFolder(stagingService.folder(context.historyId()));
                stagingService.stage(root, context.stagingFolder(), progressToken,
                        () -> checkCancellation(progressToken, deadline));
                validationRoot = context.stagingFolder();
                validationMap = context.stagingFolder().resolve(root.relativize(map)).normalize();
                if (!Files.isRegularFile(validationMap)) {
                    throw new IllegalStateException("로컬 복사본에서 DITAMAP을 찾을 수 없습니다: " + map.getFileName());
                }
                logStage(context, "COPY", stageStarted);
                stageStarted = System.nanoTime();
            }
            ValidationResult result = validationService.validate(new ValidationRequest(
                    validationMap, context.snapshot(), scope, selected, request.validateMap(), validationRoot),
                    (completed, total, currentFile) -> progressService.update(progressToken, completed, total, currentFile),
                    () -> checkCancellation(progressToken, deadline));
            logStage(context, "VALIDATE", stageStarted);
            stageStarted = System.nanoTime();
            if (gomsExecution) {
                result = stagingService.restoreOriginalPaths(
                        result, context.stagingFolder(), root, map);
            }
            Path report = reportWriterService.write(result, root, gomsExecution,
                    historyFolder, context.historyId(), userName, userId);
            logStage(context, "REPORT", stageStarted);
            historyCoordinator.success(context.historyId(), result.validatedFileCount(),
                    result.issues().size(), report, context.snapshot());
            progressService.complete(progressToken, result.validatedFileCount());
            return new RunResponse(context.historyId(), "SUCCESS", result.validatedFileCount(),
                    result.issues().size(), "/api/dita-validation/reports/" + context.historyId());
        } catch (CancellationException ex) {
            historyCoordinator.cancel(context.historyId(), ex.getMessage());
            progressService.cancelled(progressToken, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            historyCoordinator.fail(context.historyId(), ex);
            progressService.fail(progressToken, ex);
            throw ex;
        } finally {
            stagingService.cleanup(context.stagingFolder());
            progressService.finish(progressToken);
        }
    }

    private void logStage(DitaValidationExecutionContext context, String stage, long startedNanos) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        log.info("DITA validation jobId={} historyId={} stage={} elapsedMs={}",
                context.progressToken(), context.historyId(), stage, elapsedMs);
    }

    private void checkCancellation(String progressToken, long deadline) {
        if (progressService.isCancellationRequested(progressToken)) {
            throw new CancellationException("사용자가 Schematron 검사를 취소했습니다.");
        }
        if (System.nanoTime() - deadline >= 0) {
            throw new CancellationException("Schematron 검사 제한 시간을 초과했습니다.");
        }
    }

    private String scopeLabel(ValidationScope scope) {
        return switch (scope) {
            case ALL -> "ditamap 전체 검사";
            case SELECTED_ONLY -> "선택 항목만 검사";
            case SELECTED_WITH_DESCENDANTS -> "선택 항목과 모든 자손 검사";
        };
    }
}
