package maoomWeb.ire.user.dto;

/** 로그인 사용자가 변경할 수 있는 이름과 새 비밀번호를 전달한다. */
public class UserAccountUpdateDto {

    private String userName;
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
