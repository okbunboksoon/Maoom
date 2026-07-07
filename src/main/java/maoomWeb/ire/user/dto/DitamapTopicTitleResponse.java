package maoomWeb.ire.user.dto;

/**
 * 파일명으로 찾은 DITA title과 저장에 사용할 href 정보.
 *
 * <p>서버가 실제 파일을 찾아보고 응답하므로, 화면은 사용자가 입력한 값 대신
 * 서버가 확인한 파일명과 href를 저장 데이터에 반영한다. title은 DITA 원본의
 * direct child title을 읽은 값이며, 원본 DITA 파일은 수정하지 않는다.</p>
 *
 * @param title DITA 파일에서 읽은 title. 비어 있으면 서버에서 파일명으로 대체
 * @param fileName 실제로 찾은 DITA 파일명
 * @param href 기존 href의 경로 구조를 유지하고 파일명만 바꾼 저장용 href
 */
public record DitamapTopicTitleResponse(
        String title,
        String fileName,
        String href) {
}
