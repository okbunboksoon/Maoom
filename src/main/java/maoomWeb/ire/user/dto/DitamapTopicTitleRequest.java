package maoomWeb.ire.user.dto;

/**
 * 법규 DITAMAP 편집 화면에서 파일명 변경 시 DITA title을 조회하는 요청.
 *
 * <p>화면에서는 사용자가 파일명만 바꾸지만, 실제 DITA 파일은 기준 ditamap의
 * 위치와 기존 href를 함께 봐야 정확히 찾을 수 있다. 그래서 새 파일명뿐 아니라
 * 기준 ditamap 경로와 기존 href도 같이 보낸다.</p>
 *
 * @param baseDitamapFile 현재 편집 중인 기준 ditamap 파일 경로
 * @param href 기존 topicref href. 폴더 경로와 #fragment를 보존하는 기준값
 * @param fileName 사용자가 새로 입력한 파일명
 */
public record DitamapTopicTitleRequest(
        String baseDitamapFile,
        String href,
        String fileName) {
}
