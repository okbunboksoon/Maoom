package maoomWeb.ire.admin.service;

/** 관리자 화면에서 신규 사용자를 등록할 때 받는 입력값. */
public record AdminUserCreateRequest(
        String userId,
        String password,
        String userName,
        String userEmail,
        String userRole,
        String slackUserId) {
}
