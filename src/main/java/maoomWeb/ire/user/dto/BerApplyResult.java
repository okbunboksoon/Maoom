package maoomWeb.ire.user.dto;

import java.util.List;

/** BER 반영 배치 완료 후 화면에 보여줄 결과 정보. */
public record BerApplyResult(
        String inputPath,
        String reportPath,
        String topicsPath,
        List<String> logs) {
}
