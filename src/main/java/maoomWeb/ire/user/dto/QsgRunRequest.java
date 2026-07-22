package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * QSG 실행 화면에서 전달하는 입력값.
 *
 * @param inputPath 서버 PC에서 접근 가능한 V서버/H서버 입력 경로
 * @param languageCodes 실행할 QSG 언어 코드 목록
 */
public record QsgRunRequest(
        String inputPath,
        List<String> languageCodes) {
}
