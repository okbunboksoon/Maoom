package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
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
import com.sts.dita.report.HtmlReportWriter;

/**
 * 검사 화면 요청을 실제 Schematron 실행으로 연결하는 업무 흐름이다.
 * 입력 검증, 전용 이력, 규칙 스냅샷, 진행 상태, HTML 결과 생성을 한곳에서 조정한다.
 */
@Service
public class DitaValidationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(DitaValidationWorkflowService.class);
    private final DitaSchematronService validationService;
    private final SchematronHistoryService historyService;
    private final SchematronFileService schematronFileService;
    private final DitaValidationProgressService progressService;
    private final CurrentUserService currentUserService;
    private final Path reportRoot;
    private final Path gomsOutputRoot;
    private final Path stagingRoot;
    private final long timeoutNanos;

    @Autowired
    public DitaValidationWorkflowService(
            DitaSchematronService validationService,
            SchematronHistoryService historyService,
            SchematronFileService schematronFileService,
            CurrentUserService currentUserService,
            DitaValidationProgressService progressService,
            @Value("${dita.validation.report-dir}") String reportDir,
            @Value("${dita.validation.timeout-minutes:30}") long timeoutMinutes,
            @Value("${dita.validation.goms-output-root:V:/ValidationCheck}") String gomsOutputRoot,
            @Value("${dita.validation.staging-root:${java.io.tmpdir}/maoomtool-dita-validation}") String stagingRoot) {
        this.validationService = validationService;
        this.historyService = historyService;
        this.schematronFileService = schematronFileService;
        this.currentUserService = currentUserService;
        this.progressService = progressService;
        this.reportRoot = Path.of(reportDir).toAbsolutePath().normalize();
        this.gomsOutputRoot = Path.of(gomsOutputRoot).toAbsolutePath().normalize();
        this.stagingRoot = Path.of(stagingRoot).toAbsolutePath().normalize();
        this.timeoutNanos = TimeUnit.MINUTES.toNanos(Math.max(1, timeoutMinutes));
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
        // 입력 폴더 아래의 모든 ditamap을 찾아 화면 드롭다운에 파일명과 실제 경로를 전달한다.
        Path root = validDirectory(inputPath);
        // GOMS 출력은 <프로젝트>/dita 바로 아래에 DITAMAP을 생성하므로 깊은 V 서버 순회를 피한다.
        int maxDepth = root.startsWith(gomsOutputRoot) && !root.equals(gomsOutputRoot) ? 2 : Integer.MAX_VALUE;
        return walkProjectEntries(root, maxDepth).stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ditamap"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> new MapItem(path.getFileName().toString(), path.toAbsolutePath().normalize().toString()))
                .toList();
    }

    /** old와 report는 결과 필터링만 하지 않고 디렉터리 진입 자체를 생략한다. */
    private List<Path> walkProjectEntries(Path root) throws java.io.IOException {
        return walkProjectEntries(root, Integer.MAX_VALUE);
    }

    private List<Path> walkProjectEntries(Path root, int maxDepth) throws java.io.IOException {
        List<Path> entries = new ArrayList<>();
        Files.walkFileTree(root, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root)) {
                    String name = directory.getFileName().toString();
                    if ("old".equalsIgnoreCase(name) || "report".equalsIgnoreCase(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                entries.add(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                entries.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return entries;
    }

    /** GOMS 결과 루트의 프로젝트별 report HTML을 최신 파일명 내림차순으로 반환한다. */
    public List<GomsReportProject> findGomsReports() throws Exception {
        if (!Files.isDirectory(gomsOutputRoot)) {
            throw new IllegalStateException("GOMS 결과 폴더를 찾을 수 없습니다: " + gomsOutputRoot);
        }
        Map<String, GomsReportSummary> reportSummaries = historyService.findSuccessfulReportSummaries();
        List<Path> projects;
        try (var paths = Files.list(gomsOutputRoot)) {
            projects = paths.filter(Files::isDirectory)
                    .filter(path -> !"old".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        List<GomsReportProject> result = new ArrayList<>();
        for (Path project : projects) {
            Path reportFolder = project.resolve("report");
            List<GomsReportFile> reports = new ArrayList<>();
            if (Files.isDirectory(reportFolder)) {
                try (var paths = Files.list(reportFolder)) {
                    for (Path report : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                            .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER).reversed()).toList()) {
                        GomsReportSummary summary = reportSummaries.get(
                                SchematronHistoryService.normalizedPathKey(report));
                        Long historyId = summary == null ? null : summary.historyId();
                        String fileName = report.getFileName().toString();
                        String url = historyId == null
                                ? "/api/dita-validation/goms/reports/file?project="
                                        + encodeUrl(project.getFileName().toString()) + "&file=" + encodeUrl(fileName)
                                : "/api/dita-validation/reports/" + historyId;
                        reports.add(new GomsReportFile(fileName, historyId, historyId != null, url,
                                Files.getLastModifiedTime(report).toMillis(),
                                summary == null ? null : summary.validatedFileCount(),
                                summary == null ? null : summary.issueCount(),
                                summary == null ? null : summary.finishedAt(),
                                summary == null ? null : summary.testerName(),
                                summary == null ? null : summary.appliedCount(),
                                summary == null ? null : summary.scopeType()));
                    }
                }
            }
            result.add(new GomsReportProject(project.getFileName().toString(), reports));
        }
        return result;
    }

    public Path resolveGomsReport(String projectName, String fileName) {
        requireSinglePathName(projectName, "프로젝트 폴더명");
        requireSinglePathName(fileName, "리포트 파일명");
        if (!fileName.toLowerCase().endsWith(".html")) {
            throw new IllegalArgumentException("HTML 리포트만 열 수 있습니다.");
        }
        Path project = gomsOutputRoot.resolve(projectName).normalize();
        Path reportFolder = project.resolve("report").normalize();
        Path report = reportFolder.resolve(fileName).normalize();
        if (!project.startsWith(gomsOutputRoot) || "old".equalsIgnoreCase(project.getFileName().toString())
                || !report.getParent().equals(reportFolder) || !Files.isRegularFile(report)) {
            throw new IllegalArgumentException("리포트 파일을 찾을 수 없습니다.");
        }
        return report;
    }

    private void requireSinglePathName(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "이 없습니다.");
        Path path = Path.of(value);
        if (path.isAbsolute() || path.getNameCount() != 1 || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("올바르지 않은 " + label + "입니다.");
        }
    }

    private String encodeUrl(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public MapTree loadTree(String mapPath) throws Exception {
        Path map = validMap(mapPath, null);
        MapModel model = validationService.loadMap(map);
        return new MapTree(map.toString(), model.roots().stream().map(this::treeNode).toList());
    }

    public RunResponse run(RunRequest request, org.springframework.security.core.Authentication authentication)
            throws Exception {
        // 웹 요청 검증부터 이력, 규칙 스냅샷, 검사, HTML 생성까지 한 실행 단위로 처리한다.
        if (request == null) throw new IllegalArgumentException("검사 요청이 없습니다.");
        Path root = validDirectory(request.inputPath());
        boolean gomsExecution = "GOMS".equalsIgnoreCase(request.executionMode());
        if (request.executionMode() != null && !request.executionMode().isBlank()
                && !gomsExecution && !"LOCAL_PATH".equalsIgnoreCase(request.executionMode())) {
            throw new IllegalArgumentException("올바른 실행 방식이 아닙니다.");
        }
        if (gomsExecution && (!root.startsWith(gomsOutputRoot) || root.equals(gomsOutputRoot))) {
            throw new IllegalArgumentException("GOMS 결과 경로가 아닙니다: " + root);
        }
        Path map = validMap(request.mapPath(), root);
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
        Long historyId = null;
        Path stagingFolder = null;
        String progressToken = request.progressToken();
        String userId = currentUserService.getUserId(authentication);
        String userName = currentUserService.getUserName(authentication);
        progressService.start(progressToken);
        long deadline = System.nanoTime() + timeoutNanos;
        try {
            historyId = historyService.start(
                    userId, root.toString(), map.toString(),
                    schema.getFileName().toString(), schemaHash, scope.name(), String.join(",", selected), summary);
            LocalDate today = LocalDate.now();
            Path historyFolder = reportRoot.resolve(Integer.toString(today.getYear()))
                    .resolve(String.format("%02d", today.getMonthValue())).resolve(Long.toString(historyId));
            Path reportFolder = gomsExecution ? root.resolve("report") : historyFolder;
            // 규칙 스냅샷은 기존 중앙 이력 폴더에 유지하고 GOMS report에는 결과 HTML만 저장한다.
            Path snapshot = schematronFileService.copySnapshot(schema, historyFolder);
            // 선택 후 복사되는 사이 규칙이 변경된 경우 다른 규칙으로 실행되는 것을 차단한다.
            if (!schemaHash.equals(schematronFileService.sha256(snapshot))) {
                throw new IllegalStateException("Schematron 파일이 실행 준비 중 변경되었습니다. 다시 실행해 주세요.");
            }
            Path validationRoot = root;
            Path validationMap = map;
            if (gomsExecution) {
                stagingFolder = stagingRoot.resolve("history-" + historyId).normalize();
                if (!stagingFolder.startsWith(stagingRoot)) {
                    throw new IllegalStateException("올바른 로컬 검사 임시 경로가 아닙니다.");
                }
                stageGomsProject(root, stagingFolder, progressToken, deadline);
                validationRoot = stagingFolder;
                validationMap = stagingFolder.resolve(root.relativize(map)).normalize();
                if (!Files.isRegularFile(validationMap)) {
                    throw new IllegalStateException("로컬 복사본에서 DITAMAP을 찾을 수 없습니다: " + map.getFileName());
                }
            }
            ValidationResult result = validationService.validate(new ValidationRequest(
                    validationMap, snapshot, scope, selected, request.validateMap(), validationRoot),
                    (completed, total, currentFile) -> progressService.update(progressToken, completed, total, currentFile),
                    () -> checkCancellation(progressToken, deadline));
            if (gomsExecution) result = restoreOriginalPaths(result, stagingFolder, root, map);
            Files.createDirectories(reportFolder);
            Path report = reportFolder.resolve(gomsExecution
                    ? HtmlReportWriter.gomsReportFileName(result) : HtmlReportWriter.reportFileName(result));
            new HtmlReportWriter().write(result, report, historyId, userName, userId);
            historyService.success(historyId, result.validatedFileCount(), result.issues().size(), report, snapshot);
            progressService.complete(progressToken, result.validatedFileCount());
            return new RunResponse(historyId, "SUCCESS", result.validatedFileCount(),
                    result.issues().size(), "/api/dita-validation/reports/" + historyId);
        } catch (CancellationException ex) {
            historyService.cancel(historyId, ex.getMessage());
            progressService.cancelled(progressToken, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            historyService.fail(historyId, ex);
            progressService.fail(progressToken, ex);
            throw ex;
        } finally {
            deleteStagingFolder(stagingFolder);
            progressService.finish(progressToken);
        }
    }

    /** V 서버 프로젝트를 작업별 로컬 폴더로 복사해 검사 중 네트워크 파일 접근을 최소화한다. */
    private void stageGomsProject(Path sourceRoot, Path targetRoot, String progressToken, long deadline)
            throws Exception {
        if (targetRoot.startsWith(sourceRoot) || sourceRoot.startsWith(targetRoot)) {
            throw new IllegalStateException("로컬 검사 임시 폴더는 V 서버 프로젝트 경로와 분리되어야 합니다.");
        }
        if (Files.exists(targetRoot)) deleteStagingFolder(targetRoot);
        List<Path> entries = walkProjectEntries(sourceRoot);
        long requiredBytes = 0L;
        for (Path entry : entries) {
            if (Files.isSymbolicLink(entry)) {
                throw new IllegalStateException("심볼릭 링크가 포함된 GOMS 프로젝트는 로컬 복사할 수 없습니다: "
                        + sourceRoot.relativize(entry));
            }
            if (Files.isRegularFile(entry)) requiredBytes += Files.size(entry);
        }
        Files.createDirectories(targetRoot);
        if (Files.getFileStore(targetRoot).getUsableSpace() < requiredBytes) {
            throw new IllegalStateException("로컬 검사 임시 폴더의 디스크 공간이 부족합니다.");
        }
        int completed = 0;
        int total = Math.max(1, entries.size() - 1);
        for (Path entry : entries) {
            if (entry.equals(sourceRoot)) continue;
            checkCancellation(progressToken, deadline);
            Path relative = sourceRoot.relativize(entry);
            Path target = targetRoot.resolve(relative).normalize();
            if (!target.startsWith(targetRoot)) throw new IllegalStateException("올바르지 않은 복사 경로입니다.");
            if (Files.isDirectory(entry)) {
                Files.createDirectories(target);
            } else if (Files.isRegularFile(entry)) {
                Files.createDirectories(target.getParent());
                Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
            }
            progressService.updateStaging(progressToken, ++completed, total, relative);
        }
    }

    /** HTML과 이력에는 임시 폴더가 아닌 원래 V 서버 경로가 기록되게 되돌린다. */
    private ValidationResult restoreOriginalPaths(ValidationResult result, Path localRoot,
            Path sourceRoot, Path sourceMap) {
        List<ValidationIssue> issues = result.issues().stream().map(issue -> new ValidationIssue(
                restoreOriginalPath(issue.file(), localRoot, sourceRoot), issue.title(), issue.parentTitles(),
                issue.line(), issue.severity(), issue.category(), issue.ruleId(), issue.ruleType(),
                issue.location(), issue.message(), issue.mapOrder(), issue.level())).toList();
        return new ValidationResult(sourceMap, result.startedAt(), result.finishedAt(),
                result.validatedFileCount(), issues);
    }

    private Path restoreOriginalPath(Path file, Path localRoot, Path sourceRoot) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(localRoot) ? sourceRoot.resolve(localRoot.relativize(normalized)).normalize() : file;
    }

    private void deleteStagingFolder(Path folder) {
        if (folder == null || !folder.startsWith(stagingRoot) || !Files.exists(folder)) return;
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("GOMS 로컬 검사 임시 폴더를 정리하지 못했습니다: {}", folder, ex);
        }
    }

    private void checkCancellation(String progressToken, long deadline) {
        if (progressService.isCancellationRequested(progressToken)) {
            throw new CancellationException("사용자가 Schematron 검사를 취소했습니다.");
        }
        if (System.nanoTime() - deadline >= 0) {
            throw new CancellationException("Schematron 검사 제한 시간을 초과했습니다.");
        }
    }

    private TreeNode treeNode(MapNode node) {
        return new TreeNode(node.id(), node.title(), node.href(),
                node.children().stream().map(this::treeNode).toList());
    }

    private Path validDirectory(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("입력 경로를 입력해 주세요.");
        Path path = Path.of(value.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("접근할 수 있는 입력 폴더가 아닙니다.");
        return path;
    }

    private Path validMap(String value, Path root) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ditamap을 선택해 주세요.");
        Path path = Path.of(value.trim()).toAbsolutePath().normalize();
        if (root != null && !path.startsWith(root)) throw new IllegalArgumentException("입력 경로 밖의 ditamap은 검사할 수 없습니다.");
        if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase().endsWith(".ditamap")) {
            throw new IllegalArgumentException("올바른 ditamap 파일이 아닙니다.");
        }
        return path;
    }

    private String scopeLabel(ValidationScope scope) {
        return switch (scope) {
            case ALL -> "ditamap 전체 검사";
            case SELECTED_ONLY -> "선택 항목만 검사";
            case SELECTED_WITH_DESCENDANTS -> "선택 항목과 모든 자손 검사";
        };
    }
}
