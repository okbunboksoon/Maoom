package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 정제 실행 후 화면에 돌려주는 결과다.
 *
 * @param success 전체 선택 단계가 오류 없이 완료됐는지 여부
 * @param outputPath 성공 결과가 저장된 실행별 폴더 경로
 * @param completedOptions 실제 완료된 단계 ID 목록
 * @param logs 화면과 revision.log에 표시할 실행 로그
 */
public record RevisionRunResult(
        boolean success,
        String outputPath,
        List<String> completedOptions,
        List<String> logs) {
}
