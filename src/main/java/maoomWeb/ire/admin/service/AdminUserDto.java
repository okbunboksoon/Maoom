package maoomWeb.ire.admin.service;

/** 관리자 사용자 목록 화면에 반환하는 사용자 행 데이터. */
public record AdminUserDto(
        String userId,
        String passwordStatus,
        String userName,
        String userEmail,
        String userRole,
        String slackUserId) {
}
