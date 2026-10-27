package maoomWeb.ire.user.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import maoomWeb.ire.user.dto.DitaValidationDtos.ReportStateItem;
import maoomWeb.ire.user.dto.SchematronHistoryDto;

/** 기존 프로젝트 실행 로그와 분리된 Schematron 실행 이력 및 결과 보고서 경로를 관리한다. */
@Service
public class SchematronHistoryService {
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<Long, Set<String>> reportIssueIds = new ConcurrentHashMap<>();
    private static final Pattern REPORT_ISSUE_ID = Pattern.compile("data-issue-id=\\\"([0-9a-f]{32})\\\"");

    public SchematronHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initializeHistoryStorage() {
        // 기존 DB를 사용하는 서버도 재배포만으로 규칙 해시와 스냅샷 경로를 기록할 수 있게 한다.
        ensureColumn("schema_hash", "VARCHAR(64)");
        ensureColumn("schema_snapshot_path", "VARCHAR(1000)");
        ensureReportStateColumn("memo", "VARCHAR(1000)");
        interruptRunningHistories();
    }

    /**
     * 애플리케이션 시작 전에 남아 있던 RUNNING 이력은 복구할 실행 스레드가 없으므로 중단 상태로 정리한다.
     * 현재 배포 구조는 단일 서버를 전제로 하며, 다중 서버로 확장할 때는 서버 식별값과 heartbeat 기준이 필요하다.
     */
    int interruptRunningHistories() {
        return jdbcTemplate.update("""
                UPDATE tb_schematron_execution_history
                SET status='INTERRUPTED', finished_at=CURRENT_TIMESTAMP(6),
                    elapsed_ms=TIMESTAMPDIFF(MICROSECOND, started_at, CURRENT_TIMESTAMP(6)) DIV 1000,
                    error_message='서버 재시작으로 검사가 중단되었습니다.'
                WHERE status='RUNNING'
                """);
    }

    public Long start(String userId, String inputPath, String mapPath, String schemaName, String schemaHash,
                      String scope, String selectedNodes, String summary) {
        // 실제 검사를 시작하기 전에 RUNNING 이력을 만들어 장시간 검사도 관리자 화면에서 확인 가능하게 한다.
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tb_schematron_execution_history
                    (status, trigger_user_id, started_at, input_path, map_path, schema_name, schema_hash,
                     scope_type, selected_nodes, input_summary, created_at)
                    VALUES ('RUNNING', ?, CURRENT_TIMESTAMP(6), ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, userId);
            statement.setString(2, inputPath);
            statement.setString(3, mapPath);
            statement.setString(4, schemaName);
            statement.setString(5, schemaHash);
            statement.setString(6, scope);
            statement.setString(7, selectedNodes);
            statement.setString(8, summary);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Schematron 이력 번호를 생성하지 못했습니다.");
        return key.longValue();
    }

    public void success(Long id, int fileCount, int issueCount, Path report, Path schemaSnapshot) {
        // 성공 시 처리 수와 실제 HTML/.sch 저장 위치를 기록한다.
        jdbcTemplate.update("""
                UPDATE tb_schematron_execution_history
                SET status='SUCCESS', finished_at=CURRENT_TIMESTAMP(6),
                    elapsed_ms=TIMESTAMPDIFF(MICROSECOND, started_at, CURRENT_TIMESTAMP(6)) DIV 1000,
                    validated_file_count=?, issue_count=?, report_file_name=?, report_file_path=?,
                    schema_snapshot_path=?
                WHERE id=?
                """, fileCount, issueCount, report.getFileName().toString(), report.toString(),
                schemaSnapshot.toString(), id);
    }

    public void fail(Long id, Throwable error) {
        // 실패한 실행도 소요 시간과 원인을 남기되 결과 보고서 경로는 생성하지 않는다.
        if (id == null) return;
        String message = error == null ? "알 수 없는 오류" : error.getMessage();
        jdbcTemplate.update("""
                UPDATE tb_schematron_execution_history
                SET status='FAILED', finished_at=CURRENT_TIMESTAMP(6),
                    elapsed_ms=TIMESTAMPDIFF(MICROSECOND, started_at, CURRENT_TIMESTAMP(6)) DIV 1000,
                    error_message=? WHERE id=?
                """, message, id);
    }

    public void cancel(Long id, String message) {
        if (id == null) return;
        jdbcTemplate.update("""
                UPDATE tb_schematron_execution_history
                SET status='CANCELLED', finished_at=CURRENT_TIMESTAMP(6),
                    elapsed_ms=TIMESTAMPDIFF(MICROSECOND, started_at, CURRENT_TIMESTAMP(6)) DIV 1000,
                    error_message=? WHERE id=?
                """, message, id);
    }

    public List<SchematronHistoryDto> findAll() {
        return jdbcTemplate.query("""
                SELECT id,status,trigger_user_id,started_at,finished_at,elapsed_ms,
                       input_summary,report_file_name,error_message
                FROM tb_schematron_execution_history ORDER BY started_at DESC,id DESC
                """, (rs, row) -> new SchematronHistoryDto(
                rs.getLong("id"), rs.getString("status"), rs.getString("trigger_user_id"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
                rs.getObject("elapsed_ms", Long.class), rs.getString("input_summary"),
                rs.getString("report_file_name"), rs.getString("error_message")));
    }

    public Path reportPath(Long id) {
        // DB 경로만 믿지 않고 실제 파일 존재 여부까지 확인한 뒤 사용자에게 반환한다.
        List<String> paths = jdbcTemplate.query(
                "SELECT report_file_path FROM tb_schematron_execution_history WHERE id=? AND status='SUCCESS'",
                (rs, row) -> rs.getString(1), id);
        if (paths.isEmpty() || paths.get(0) == null) throw new IllegalArgumentException("결과 보고서를 찾을 수 없습니다.");
        Path path = Path.of(paths.get(0)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("저장된 결과 보고서가 없습니다.");
        return path;
    }

    /** 보고서 항목의 공유 확인 상태를 모두 조회한다. */
    public List<ReportStateItem> findReportStates(Long historyId) {
        requireSuccessfulHistory(historyId);
        return jdbcTemplate.query("""
                SELECT issue_id, checked, updated_at, confirmer_name, memo
                FROM tb_schematron_report_issue_state
                WHERE history_id=? ORDER BY issue_id
                """, (rs, row) -> new ReportStateItem(
                rs.getString("issue_id"), rs.getBoolean("checked"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("confirmer_name"), rs.getString("memo")), historyId);
    }

    /** 체크 시각과 확인자는 서버에서 결정하고, 체크 해제 시 두 값을 함께 제거한다. */
    @Transactional
    public ReportStateItem saveReportState(Long historyId, String issueId, boolean checked,
                                           String userId, String userName) {
        requireSuccessfulHistory(historyId);
        if (issueId == null || !issueId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("올바른 보고서 항목이 아닙니다.");
        }
        requireIssuesInReport(historyId, List.of(issueId));
        String confirmer = userName == null || userName.isBlank() ? userId : userName;
        if (checked && (confirmer == null || confirmer.isBlank())) {
            throw new IllegalArgumentException("로그인 사용자 정보를 확인할 수 없습니다.");
        }
        jdbcTemplate.update("""
                INSERT INTO tb_schematron_report_issue_state
                    (history_id, issue_id, checked, updated_at, confirmer_user_id, confirmer_name)
                VALUES (?, ?, ?, IF(?, CURRENT_TIMESTAMP(6), NULL), IF(?, ?, NULL), IF(?, ?, NULL))
                ON DUPLICATE KEY UPDATE
                    checked=VALUES(checked), updated_at=VALUES(updated_at),
                    confirmer_user_id=VALUES(confirmer_user_id), confirmer_name=VALUES(confirmer_name)
                """, historyId, issueId, checked, checked, checked, userId, checked, confirmer);
        return jdbcTemplate.queryForObject("""
                SELECT issue_id, checked, updated_at, confirmer_name, memo
                FROM tb_schematron_report_issue_state WHERE history_id=? AND issue_id=?
                """, (rs, row) -> new ReportStateItem(
                rs.getString("issue_id"), rs.getBoolean("checked"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("confirmer_name"), rs.getString("memo")), historyId, issueId);
    }

    /** 필터된 다수 항목을 한 트랜잭션과 JDBC batch로 저장한다. 중간 오류는 전체 롤백된다. */
    @Transactional
    public List<ReportStateItem> saveReportStates(Long historyId, List<String> issueIds, boolean checked,
                                                  String userId, String userName) {
        requireSuccessfulHistory(historyId);
        if (issueIds == null || issueIds.isEmpty()) return List.of();
        List<String> ids = new LinkedHashSet<>(issueIds).stream().toList();
        if (ids.size() > 10000 || ids.stream().anyMatch(id -> id == null || !id.matches("[0-9a-f]{32}"))) {
            throw new IllegalArgumentException("올바르지 않은 보고서 항목이 포함되어 있습니다.");
        }
        requireIssuesInReport(historyId, ids);
        String confirmer = userName == null || userName.isBlank() ? userId : userName;
        if (checked && (confirmer == null || confirmer.isBlank())) {
            throw new IllegalArgumentException("로그인 사용자 정보를 확인할 수 없습니다.");
        }
        String sql = """
                INSERT INTO tb_schematron_report_issue_state
                    (history_id, issue_id, checked, updated_at, confirmer_user_id, confirmer_name)
                VALUES (?, ?, ?, IF(?, CURRENT_TIMESTAMP(6), NULL), IF(?, ?, NULL), IF(?, ?, NULL))
                ON DUPLICATE KEY UPDATE checked=VALUES(checked), updated_at=VALUES(updated_at),
                    confirmer_user_id=VALUES(confirmer_user_id), confirmer_name=VALUES(confirmer_name)
                """;
        jdbcTemplate.batchUpdate(sql, ids, Math.min(500, ids.size()), (statement, id) -> {
            statement.setLong(1, historyId);
            statement.setString(2, id);
            statement.setBoolean(3, checked);
            statement.setBoolean(4, checked);
            statement.setBoolean(5, checked);
            statement.setString(6, userId);
            statement.setBoolean(7, checked);
            statement.setString(8, confirmer);
        });
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] parameters = new Object[ids.size() + 1];
        parameters[0] = historyId;
        for (int i = 0; i < ids.size(); i++) parameters[i + 1] = ids.get(i);
        return jdbcTemplate.query("""
                SELECT issue_id, checked, updated_at, confirmer_name, memo
                FROM tb_schematron_report_issue_state
                WHERE history_id=? AND issue_id IN (%s) ORDER BY issue_id
                """.formatted(placeholders), (rs, row) -> new ReportStateItem(
                rs.getString("issue_id"), rs.getBoolean("checked"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("confirmer_name"), rs.getString("memo")), parameters);
    }

    /** 작업 메모는 확인 여부와 독립적으로 저장하며 빈 문자열은 NULL로 정리한다. */
    @Transactional
    public ReportStateItem saveReportMemo(Long historyId, String issueId, String memo) {
        requireSuccessfulHistory(historyId);
        if (issueId == null || !issueId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("올바른 보고서 항목이 아닙니다.");
        }
        requireIssuesInReport(historyId, List.of(issueId));
        String normalized = memo == null ? "" : memo.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException("메모는 500자 이하로 입력해 주세요.");
        jdbcTemplate.update("""
                INSERT INTO tb_schematron_report_issue_state (history_id, issue_id, checked, memo)
                VALUES (?, ?, FALSE, NULLIF(?, ''))
                ON DUPLICATE KEY UPDATE memo=VALUES(memo)
                """, historyId, issueId, normalized);
        return jdbcTemplate.queryForObject("""
                SELECT issue_id, checked, updated_at, confirmer_name, memo
                FROM tb_schematron_report_issue_state WHERE history_id=? AND issue_id=?
                """, (rs, row) -> new ReportStateItem(rs.getString("issue_id"), rs.getBoolean("checked"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("confirmer_name"), rs.getString("memo")), historyId, issueId);
    }

    private void requireSuccessfulHistory(Long historyId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tb_schematron_execution_history
                WHERE id=? AND status='SUCCESS'
                """, Integer.class, historyId);
        if (count == null || count == 0) throw new IllegalArgumentException("결과 보고서를 찾을 수 없습니다.");
    }

    /** 요청 ID가 형식만 맞춘 임의 값이 아니라 해당 이력의 실제 HTML 항목인지 확인한다. */
    private void requireIssuesInReport(Long historyId, List<String> issueIds) {
        Set<String> allowed = reportIssueIds.computeIfAbsent(historyId, id -> readIssueIds(reportPath(id)));
        if (!allowed.containsAll(issueIds)) {
            throw new IllegalArgumentException("해당 결과 보고서에 없는 항목이 포함되어 있습니다.");
        }
    }

    Set<String> readIssueIds(Path report) {
        try {
            String html = Files.readString(report, StandardCharsets.UTF_8);
            Set<String> ids = new LinkedHashSet<>();
            REPORT_ISSUE_ID.matcher(html).results().forEach(match -> ids.add(match.group(1)));
            return Set.copyOf(ids);
        } catch (Exception exception) {
            throw new IllegalStateException("결과 보고서 항목을 확인할 수 없습니다.", exception);
        }
    }

    private void ensureColumn(String column, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_schematron_execution_history'
                  AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE tb_schematron_execution_history ADD COLUMN "
                    + column + " " + definition);
        }
    }

    private void ensureReportStateColumn(String column, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tb_schematron_report_issue_state'
                  AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count != null && count == 0) jdbcTemplate.execute(
                "ALTER TABLE tb_schematron_report_issue_state ADD COLUMN " + column + " " + definition);
    }
}
