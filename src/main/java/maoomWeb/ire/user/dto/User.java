package maoomWeb.ire.user.dto;

/**
 * 로그인 사용자와 권한, Slack 연동 정보를 전달하는 DTO.
 */
public class User {


	 private String userId;
	 private String userPw;
	 private String userName;
	 private String userNickName;
	 private String userEmail;
	 private String userRole;
	 private String slackUserId;
	 private String profileImageStoredName;
	 private String profileImageContentType;


	 @Override
	 public String toString() {
	 	return "User{" +
	 			"userId='" + userId + '\'' +
	 			", userPw='" + userPw + '\'' +
	 			", userName='" + userName + '\'' +
	 			", userNickName='" + userNickName + '\'' +
	 			", userEmail='" + userEmail + '\'' +
	 			", userRole='" + userRole + '\'' +
	 			'}';
	 }
	
	  public String getUserPw() {
	        return userPw;
	    }

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setUserPw(String userPw) {
		this.userPw = userPw;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserNickName() {
		return userNickName;
	}

	public void setUserNickName(String userNickName) {
		this.userNickName = userNickName;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail;
	}

	public String getUserRole() {
		return userRole;
	}

	public void setUserRole(String userRole) {
		this.userRole = userRole;
	}

	public String getSlackUserId() {
		return slackUserId;
	}

	public void setSlackUserId(String slackUserId) {
		this.slackUserId = slackUserId;
	}

	public String getProfileImageStoredName() {
		return profileImageStoredName;
	}

	public void setProfileImageStoredName(String profileImageStoredName) {
		this.profileImageStoredName = profileImageStoredName;
	}

	public String getProfileImageContentType() {
		return profileImageContentType;
	}

	public void setProfileImageContentType(String profileImageContentType) {
		this.profileImageContentType = profileImageContentType;
	}
}
