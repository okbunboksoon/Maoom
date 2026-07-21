package maoomWeb.ire.user.dto;

import java.util.List;

/** QSG 배치 실행 결과와 로그. */
public record QsgRunResult(
        boolean success,
        String outputPath,
        List<String> languageCodes,
        List<String> logs) {
}
