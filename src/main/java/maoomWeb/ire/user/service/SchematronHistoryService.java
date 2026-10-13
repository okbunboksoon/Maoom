package maoomWeb.ire.user.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.SchematronHistoryDto;

/** 기존 프로젝트 실행 로그와 분리된 Schematron 실행 이력 및 결과 보고서 경로를 관리한다. */
@Service
public class SchematronHistoryService {
    private final JdbcTemplate jdbcTemplate;

    public SchematronHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureSchematronMetadataColumns() {
        // 기존 DB를 사용하는 서버도 재배포만으로 규칙 해시와 스냅샷 경로를 기록할 수 있게 한다.
        ensureColumn("schema_hash", "VARCHAR(64)");
        ensureColumn("schema_snapshot_path", "VARCHAR(1000)");
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
}
