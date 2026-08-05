package maoomWeb.ire.user.dto;

/**
 * Index 추출 팝업에서 서버로 보내는 요청값.
 *
 * @param ditaPath 서버 PC가 접근할 수 있는 DITA 또는 topics 폴더 경로
 * @param indexLevel 추출할 Index 깊이. 비어 있으면 서비스에서 기본 10으로 처리한다.
 */
public record IndexExtractRequest(
        String ditaPath,
        Integer indexLevel) {
}
