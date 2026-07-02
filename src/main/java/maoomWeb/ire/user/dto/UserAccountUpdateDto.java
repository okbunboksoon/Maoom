package maoomWeb.ire.user.dto;

/**
 * 계정 설정 화면에서 이름과 선택적 새 비밀번호를 UserService로 전달한다.
 * 새 비밀번호가 공백이면 이름만 변경하고, 값이 있으면 검증 후 BCrypt로 저장한다.
 */
public class UserAccountUpdateDto {

    /** 필수 표시 이름. UserService에서 공백 제거와 길이를 검증한다. */
    private String userName;
    /** 선택 입력값. 서버에서 원문을 저장하지 않고 BCrypt 해시로 변환한다. */
    private String newPassword;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
