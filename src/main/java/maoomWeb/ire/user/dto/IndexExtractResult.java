package maoomWeb.ire.user.dto;

import java.util.List;

/** Index 추출 완료 후 화면에 보여줄 결과 정보. */
public record IndexExtractResult(
        String inputPath,
        int indexLevel,
        String reportPath,
        List<String> logs) {
}
