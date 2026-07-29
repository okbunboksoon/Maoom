package maoomWeb.ire.admin.service;

/** 관리자 사용자 테이블의 인라인 수정 요청. */
public record AdminUserUpdateRequest(
        String field,
        String value) {
}
