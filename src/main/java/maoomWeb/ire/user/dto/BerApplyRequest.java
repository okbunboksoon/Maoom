package maoomWeb.ire.user.dto;

/** BER 반영 배치 실행에 필요한 입력/출력 경로. */
public record BerApplyRequest(
        String ditaPath,
        String outputPath) {
}