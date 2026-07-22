package maoomWeb.ire.user.dto;

/**
 * 제품사양서 비교 화면에서 전달하는 입력값.
 *
 * @param inputPath 서버 PC에서 접근 가능한 V서버/H서버 입력 경로
 * @param beforeFileName 입력 경로 안의 이전 제품사양서 파일명
 * @param afterFileName 입력 경로 안의 신규 제품사양서 파일명
 * @param keyMode {@code ALL} 또는 {@code SPECIFIC}
 * @param keys 특정 Key 추출 시 {@code K1 K2 K3} 형식으로 전달되는 값
 */
public record ProductSpecComparisonRequest(
        String inputPath,
        String beforeFileName,
        String afterFileName,
        String keyMode,
        String keys) {
}
