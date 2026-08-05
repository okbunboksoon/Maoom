package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * Index 추출 완료 후 화면과 실행 로그에 남길 결과 정보.
 *
 * @param inputPath 사용자가 입력한 DITA 경로
 * @param indexLevel 실제 배치에 전달한 Index 레벨
 * @param reportPath 생성된 index-review.xlsx 경로
 * @param logs 서비스와 배치가 남긴 주요 진행 로그
 */
public record IndexExtractResult(
        String inputPath,
        int indexLevel,
        String reportPath,
        List<String> logs) {
}
