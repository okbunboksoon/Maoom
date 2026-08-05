package maoomWeb.ire.user.dto;

/** Index 추출 배치 실행에 필요한 입력 경로와 출력 레벨. */
public record IndexExtractRequest(
        String ditaPath,
        Integer indexLevel) {
}
