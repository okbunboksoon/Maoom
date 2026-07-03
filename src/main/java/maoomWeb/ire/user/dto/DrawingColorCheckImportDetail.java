package maoomWeb.ire.user.dto;

/**
 * 검토 엑셀의 각 행이 DB 반영 과정에서 어떻게 처리됐는지 기록한다.
 *
 * @param excelRowNumber 사용자가 보는 Excel 행 번호(1부터 시작)
 * @param drawingName 엑셀에서 읽은 도안명
 * @param inputValue 엑셀 컬러도안 셀의 원래 값
 * @param appliedValue 업무 규칙을 적용한 최종 V/X 값
 * @param previousValue 반영 전 DB에 저장되어 있던 값
 * @param status 신규, 수정, 변경 없음, 제외 중 하나
 * @param note 공백 변환이나 제외 사유 등의 추가 설명
 */
public record DrawingColorCheckImportDetail(
        int excelRowNumber,
        String drawingName,
        String inputValue,
        String appliedValue,
        String previousValue,
        String status,
        String note) {
}
