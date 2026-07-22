package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 제품사양서 비교 배치 실행 결과와 화면 표시용 로그.
 *
 * @param success 실행 성공 여부
 * @param resultPath 성공 시 {@code 입력경로\Result_Folder\Product_Equipment_List_Comparison.xlsx}
 * @param logs 화면 오류 메시지와 Result_Folder 로그 파일에 사용하는 실행 로그
 */
public record ProductSpecComparisonResult(
        boolean success,
        String resultPath,
        List<String> logs) {
}
