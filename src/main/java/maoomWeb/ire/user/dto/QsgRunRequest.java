package maoomWeb.ire.user.dto;

import java.util.List;

/** QSG 실행 화면에서 전달하는 입력 경로와 선택 언어 코드 목록. */
public record QsgRunRequest(
        String inputPath,
        List<String> languageCodes) {
}
