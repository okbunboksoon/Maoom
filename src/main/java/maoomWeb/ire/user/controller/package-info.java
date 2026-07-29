/**
 * 사용자 화면과 사용자용 REST API의 진입점 모음.
 *
 * <p>이 패키지의 클래스는 브라우저 요청을 처음 받는다. 컨트롤러는 화면 이름을
 * 반환하거나 요청 DTO를 검증한 뒤, 실제 작업을 {@code user.service} 계층에
 * 위임한다. DB나 파일 처리 규칙을 찾을 때는 컨트롤러에서 호출하는 서비스 클래스를
 * 다음으로 보면 된다.</p>
 */
package maoomWeb.ire.user.controller;
