package maoomWeb.ire.user.service;

import java.sql.PreparedStatement;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * 화면에서 사용자가 실행한 큰 작업의 생명주기를 DB에 기록한다.
 *
 * <p>로그 저장은 운영 관찰용 부가기능이다. DB 권한, 스키마 초기화, 네트워크 문제로
 * 로그 INSERT/UPDATE가 실패해도 원래 파일 변환/저장 작업은 막지 않는다. 그래서 모든
 * public 메서드는 내부에서 예외를 잡고 경고 로그만 남긴다.</p>
 */
@Service
public class ProjectExecutionLogService {

    private static final Logger log =
            LoggerFactory.getLogger(ProjectExecutionLogService.class);
    private static final int SHORT_TEXT_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;

    public ProjectExecutionLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 작업 시작 로그를 남긴다.
     *
     * @return 생성된 로그 ID. 저장에 실패하면 null을 반환해 호출자가 원래 작업을 계속할 수 있게 한다.
     */
    public Long start(
            String jobType,
            String jobName,
            String triggerUserId,
            String inputPath,
            String message) {

        try{
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO tb_project_execution_log (
                            job_type,
                            job_name,
                            status,
                            trigger_user_id,
                            input_path,
                            message,
                            started_at,
                            created_at
                        ) VALUES (
                            ?,
                            ?,
                            'STARTED',
                            ?,
                            ?,
                            ?,
                            CURRENT_TIMESTAMP(6),
                            CURRENT_TIMESTAMP(6)
                        )
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, trim(jobType));
                statement.setString(2, trim(jobName));
                statement.setString(3, trim(triggerUserId));
                statement.setString(4, trim(inputPath));
                statement.setString(5, trim(message));
                return statement;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        }catch(Exception exception){
            log.warn("프로젝트 실행 시작 로그 저장 실패: {}", exception.getMessage());
            return null;
        }
    }

    /** 작업 성공 상태와 결과 위치를 기록한다. */
    public void success(
            Long logId,
            String outputPath,
            String message) {

        finish(
                logId,
                "SUCCESS",
                outputPath,
                message,
                null);
    }

    /** 작업 실패 상태와 최하위 원인 메시지를 기록한다. */
    public void fail(
            Long logId,
            Throwable exception) {

        finish(
                logId,
                "FAILED",
                null,
                exception == null ? null : rootMessage(exception),
                exception == null ? null : stackMessage(exception));
    }

    private void finish(
            Long logId,
            String status,
            String outputPath,
            String message,
            String errorMessage) {

        if(logId == null){
            return;
        }

        try{
            jdbcTemplate.update("""
                    UPDATE tb_project_execution_log
                    SET status = ?,
                        output_path = ?,
                        message = ?,
                        error_message = ?,
                        finished_at = CURRENT_TIMESTAMP(6),
                        elapsed_ms = TIMESTAMPDIFF(
                                MICROSECOND,
                                started_at,
                                CURRENT_TIMESTAMP(6)) DIV 1000
                    WHERE id = ?
                    """,
                    status,
                    trim(outputPath),
                    trim(message),
                    errorMessage,
                    logId);
        }catch(Exception exception){
            log.warn("프로젝트 실행 종료 로그 저장 실패: {}", exception.getMessage());
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;

        while(current.getCause() != null){
            current = current.getCause();
        }

        return current.getMessage() == null || current.getMessage().isBlank()
                ? exception.getMessage()
                : current.getMessage();
    }

    private String stackMessage(Throwable exception) {
        StringBuilder builder = new StringBuilder();
        Throwable current = exception;

        while(current != null){
            if(!builder.isEmpty()){
                builder.append(System.lineSeparator())
                        .append("caused by: ");
            }
            builder.append(current.getClass().getName());

            if(current.getMessage() != null
                    && !current.getMessage().isBlank()){
                builder.append(": ")
                        .append(current.getMessage());
            }

            current = current.getCause();
        }

        return builder.toString();
    }

    private String trim(String value) {
        if(value == null){
            return null;
        }

        String trimmed = value.trim();

        if(trimmed.length() <= SHORT_TEXT_LIMIT){
            return trimmed;
        }

        return trimmed.substring(0, SHORT_TEXT_LIMIT);
    }
}
