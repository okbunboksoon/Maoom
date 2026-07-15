package maoomWeb.ire.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.ProjectExecutionLogDto;

/**
 * 관리자 화면에서 실행 로그 테이블을 조회한다.
 *
 * <p>로그는 운영자가 "누가, 어떤 파일로, 어떤 큰 작업을 실행했고 실패했는지"를
 * 나중에 확인하기 위한 데이터다. 현재는 최근 내역 조회만 제공하고, 삭제/수정은
 * 하지 않는다.</p>
 */
@Service
public class AdminProjectExecutionLogService {

    private final JdbcTemplate jdbcTemplate;

    public AdminProjectExecutionLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 관리자 메인 화면에 표시할 최근 실행 로그를 최신순으로 가져온다. */
    public List<ProjectExecutionLogDto> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));

        return jdbcTemplate.query("""
                SELECT
                    id,
                    job_type,
                    job_name,
                    status,
                    trigger_user_id,
                    input_path,
                    output_path,
                    message,
                    error_message,
                    started_at,
                    finished_at,
                    elapsed_ms
                FROM tb_project_execution_log
                ORDER BY started_at DESC, id DESC
                LIMIT ?
                """,
                this::mapLog,
                safeLimit);
    }

    private ProjectExecutionLogDto mapLog(
            ResultSet resultSet,
            int rowNumber)
            throws SQLException {

        return new ProjectExecutionLogDto(
                resultSet.getLong("id"),
                resultSet.getString("job_type"),
                resultSet.getString("job_name"),
                resultSet.getString("status"),
                resultSet.getString("trigger_user_id"),
                resultSet.getString("input_path"),
                resultSet.getString("output_path"),
                resultSet.getString("message"),
                resultSet.getString("error_message"),
                resultSet.getTimestamp("started_at")
                        .toLocalDateTime(),
                resultSet.getTimestamp("finished_at") == null
                        ? null
                        : resultSet.getTimestamp("finished_at")
                                .toLocalDateTime(),
                resultSet.getObject("elapsed_ms", Long.class));
    }
}
