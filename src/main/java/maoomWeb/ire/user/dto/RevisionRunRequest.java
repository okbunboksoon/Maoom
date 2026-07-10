package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 정제 팝업에서 서버로 보내는 실행 요청이다.
 *
 * @param inputPath DITA/DITAMAP 원본이 있는 서버 PC의 절대경로
 * @param outputPath 실행 결과 폴더를 만들 기준 절대경로
 * @param inputType 입력 형태(xml 또는 dita)
 * @param outputType 출력 형태(xml 또는 dita)
 * @param optionIds 사용자가 선택한 정제 단계 ID 목록
 * @param bookmapMapName XML 입력에서 bookmap.xml을 새로 만들 때 사용할 mapname
 */
public record RevisionRunRequest(
        String inputPath,
        String outputPath,
        String inputType,
        String outputType,
        List<String> optionIds,
        String bookmapMapName) {
}
