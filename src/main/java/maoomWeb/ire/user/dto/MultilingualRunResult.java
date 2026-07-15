package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 다국어 변환 배치 실행 결과와 로그.
 */
public record MultilingualRunResult(
        boolean success,
        String outputPath,
        List<String> logs) {
}
