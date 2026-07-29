/**
 * MaoomTool의 업무 로직 계층.
 *
 * <p>컨트롤러에서 넘어온 요청은 대부분 이 패키지에서 실제 작업으로 바뀐다.
 * 엑셀 생성, XSL/VBS 기반 파일 변환, PDF 댓글 처리, 실행 로그 기록, 사용자 계정
 * 처리가 여기에 있다. DB 접근은 mapper 또는 JdbcTemplate으로 내려가고, 화면에
 * 돌려줄 응답 DTO는 다시 컨트롤러로 올라간다.</p>
 */
package maoomWeb.ire.user.service;
