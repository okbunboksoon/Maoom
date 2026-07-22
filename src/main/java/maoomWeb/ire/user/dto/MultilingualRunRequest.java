package maoomWeb.ire.user.dto;

/**
 * 다국어 변환 실행 화면에서 전달하는 입력값.
 *
 * @param inputPath 서버 PC에서 접근 가능한 V서버/H서버 입력 경로
 * @param bookmapMapName XML 입력일 때 bookmap.xml 생성에 사용할 ditamap 파일명
 */
public record MultilingualRunRequest(
        String inputPath,
        String bookmapMapName) {
}
