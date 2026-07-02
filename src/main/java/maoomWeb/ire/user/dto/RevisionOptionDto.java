package maoomWeb.ire.user.dto;

/**
 * 정제 팝업의 체크박스 한 항목을 구성하는 읽기 전용 데이터다.
 *
 * @param id 실행 요청과 서비스 내부 단계를 연결하는 고유 ID
 * @param label 화면에 표시할 단계 이름
 * @param description 사용자가 단계 기능을 이해할 수 있는 설명
 */
public record RevisionOptionDto(
        String id,
        String label,
        String description) {
}
