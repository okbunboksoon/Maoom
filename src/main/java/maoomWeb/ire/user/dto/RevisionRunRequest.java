package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 정제 팝업에서 서버로 보내는 실행 요청이다.
 *
 * @param inputPath DITA/DITAMAP 원본이 있는 서버 PC의 절대경로
 * @param outputPath 실행 결과 폴더를 만들 기준 절대경로
 * @param optionIds 사용자가 선택한 정제 단계 ID 목록
 */
public record RevisionRunRequest(
        String inputPath,
        String outputPath,
        List<String> optionIds) {
}
