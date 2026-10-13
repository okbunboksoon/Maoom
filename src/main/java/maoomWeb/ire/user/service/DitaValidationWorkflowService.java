package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sts.dita.core.MapModel;
import com.sts.dita.core.MapNode;
import com.sts.dita.core.ValidationRequest;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.ValidationScope;
import com.sts.dita.report.HtmlReportWriter;

/**
 * 검사 화면 요청을 실제 Schematron 실행으로 연결하는 업무 흐름이다.
 * 입력 검증, 전용 이력, 규칙 스냅샷, 진행 상태, HTML 결과 생성을 한곳에서 조정한다.
 */
@Service
public class DitaValidationWorkflowService {
    private final DitaSchematronService validationService;
    private final SchematronHistoryService historyService;
    private final SchematronFileService schematronFileService;
    private final DitaValidationProgressService progressService;
    private final CurrentUserService currentUserService;
    private final Path reportRoot;

    @Autowired
    public DitaValidationWorkflowService(
            DitaSchematronService validationService,
            SchematronHistoryService historyService,
            SchematronFileService schematronFileService,
            CurrentUserService currentUserService,
            DitaValidationProgressService progressService,
            @Value("${dita.validation.report-dir}") String reportDir) {
        this.validationService = validationService;
        this.historyService = historyService;
        this.schematronFileService = schematronFileService;
        this.currentUserService = currentUserService;
        this.progressService = progressService;
        this.reportRoot = Path.of(reportDir).toAbsolutePath().normalize();
    }

    DitaValidationWorkflowService(DitaSchematronService validationService,
            SchematronHistoryService historyService, SchematronFileService schematronFileService,
            CurrentUserService currentUserService, String reportDir) {
        this(validationService, historyService, schematronFileService, currentUserService,
                new DitaValidationProgressService(), reportDir);
    }

    public List<MapItem> findMaps(String inputPath) throws Exception {
        // 입력 폴더 아래의 모든 ditamap을 찾아 화면 드롭다운에 파일명과 실제 경로를 전달한다.
        Path root = validDirectory(inputPath);
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ditamap"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> new MapItem(path.getFileName().toString(), path.toAbsolutePath().normalize().toString()))
                    .toList();
        }
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
        Path map = validMap(request.mapPath(), root);
        ValidationScope scope;
        try { scope = ValidationScope.valueOf(request.scope()); }
        catch (Exception ex) { throw new IllegalArgumentException("올바른 검사 범위를 선택해 주세요."); }
        Set<String> selected = request.selectedNodeIds() == null ? Set.of() : Set.copyOf(request.selectedNodeIds());
        if (scope != ValidationScope.ALL && selected.isEmpty()) {
            throw new IllegalArgumentException("검사할 트리 항목을 선택해 주세요.");
        }
        Path schema = schematronFileService.resolveLatest(request.schematronType());
        String schemaHash = schematronFileService.sha256(schema);
        String summary = map.getFileName() + " / " + schema.getFileName() + " / " + scopeLabel(scope)
                + (selected.isEmpty() ? "" : " / 선택 " + selected.size() + "개");
        Long historyId = null;
        String progressToken = request.progressToken();
        String userId = currentUserService.getUserId(authentication);
        String userName = currentUserService.getUserName(authentication);
        progressService.start(progressToken);
        try {
            historyId = historyService.start(
                    userId, root.toString(), map.toString(),
                    schema.getFileName().toString(), schemaHash, scope.name(), String.join(",", selected), summary);
            LocalDate today = LocalDate.now();
            // 결과 충돌을 막고 월별 정리가 가능하도록 연/월/이력번호 폴더를 사용한다.
            Path folder = reportRoot.resolve(Integer.toString(today.getYear()))
                    .resolve(String.format("%02d", today.getMonthValue())).resolve(Long.toString(historyId));
            Path snapshot = schematronFileService.copySnapshot(schema, folder);
            // 선택 후 복사되는 사이 규칙이 변경된 경우 다른 규칙으로 실행되는 것을 차단한다.
            if (!schemaHash.equals(schematronFileService.sha256(snapshot))) {
                throw new IllegalStateException("Schematron 파일이 실행 준비 중 변경되었습니다. 다시 실행해 주세요.");
            }
            ValidationResult result = validationService.validate(new ValidationRequest(
                    map, snapshot, scope, selected, request.validateMap()),
                    (completed, total, currentFile) -> progressService.update(progressToken, completed, total, currentFile));
            Path report = folder.resolve("schematron-report.html");
            new HtmlReportWriter().write(result, report, userName, userId);
            historyService.success(historyId, result.validatedFileCount(), result.issues().size(), report, snapshot);
            progressService.complete(progressToken, result.validatedFileCount());
            return new RunResponse(historyId, "SUCCESS", result.validatedFileCount(),
                    result.issues().size(), "/api/dita-validation/reports/" + historyId);
        } catch (Exception ex) {
            historyService.fail(historyId, ex);
            progressService.fail(progressToken, ex);
            throw ex;
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
