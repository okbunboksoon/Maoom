package maoomWeb.ire.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검사 이력과 별개로 사용자가 첫 화면에서 실행한 최근 입력 경로를 관리한다. */
@Service
public class DitaValidationRecentPathService {
    private final JdbcTemplate jdbcTemplate;

    public DitaValidationRecentPathService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void save(String userId, String executionMode, String inputPath) {
        String path = normalizePath(inputPath);
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("로그인 사용자 정보가 없습니다.");
        String mode = normalizeMode(executionMode);
        jdbcTemplate.update("""
                INSERT INTO tb_dita_validation_recent_path
                    (user_id, execution_mode, path_hash, input_path, last_used_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE input_path=VALUES(input_path), last_used_at=CURRENT_TIMESTAMP(6)
                """, userId, mode, hash(path), path);
        jdbcTemplate.update("""
                DELETE FROM tb_dita_validation_recent_path
                WHERE user_id=? AND execution_mode=? AND path_hash NOT IN (
                    SELECT path_hash FROM (
                        SELECT path_hash FROM tb_dita_validation_recent_path
                        WHERE user_id=? AND execution_mode=?
                        ORDER BY last_used_at DESC
                        LIMIT 10
                    ) recent_paths
                )
                """, userId, mode, userId, mode);
    }

    public List<String> findRecent(String userId, String executionMode, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return jdbcTemplate.queryForList("""
                SELECT input_path FROM tb_dita_validation_recent_path
                WHERE user_id=? AND execution_mode=?
                ORDER BY last_used_at DESC
                LIMIT ?
                """, String.class, userId, normalizeMode(executionMode), safeLimit);
    }

    @Transactional
    public void delete(String userId, String executionMode, String inputPath) {
        String path = normalizePath(inputPath);
        jdbcTemplate.update("""
                DELETE FROM tb_dita_validation_recent_path
                WHERE user_id=? AND execution_mode=? AND path_hash=?
                """, userId, normalizeMode(executionMode), hash(path));
    }

    private String normalizePath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) throw new IllegalArgumentException("입력 경로가 비어 있습니다.");
        String path = inputPath.trim();
        if (path.length() > 1000) throw new IllegalArgumentException("입력 경로가 너무 깁니다.");
        return path;
    }

    private String normalizeMode(String executionMode) {
        String mode = executionMode == null ? "GOMS" : executionMode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("GOMS", "LOCAL_PATH").contains(mode)) {
            throw new IllegalArgumentException("지원하지 않는 실행 방식입니다.");
        }
        return mode;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
