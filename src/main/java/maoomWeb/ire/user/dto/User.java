package maoomWeb.ire.user.dto;

/**
 * 로그인, 계정 화면, 댓글 멘션과 프로필 이미지 기능이 공유하는 사용자 DTO다.
 *
 * <p>UserMapper.xml의 UserResultMap이 tb_user 컬럼을 이 필드에 연결한다.
 * API 응답에 사용할 때는 비밀번호를 포함하지 않도록 서비스에서 null로 지우거나
 * 필요한 필드만 별도 Map으로 반환한다.</p>
 */
public class User {

	/** 로그인 ID이며 tb_user의 기본키다. */
	 private String userId;
	/** 평문 또는 BCrypt 형식의 저장 비밀번호. 화면에 반환하면 안 된다. */
	 private String userPw;
	/** 화면, 댓글 작성자와 멘션 목록에 표시되는 사용자 이름. */
	 private String userName;
	/** 현재 DB 조회 SQL에는 포함되지 않은 확장용 별칭 필드. */
	 private String userNickName;
	/** 사용자 연락처 및 계정 정보에 사용하는 이메일. */
	 private String userEmail;
	/** USER/ADMIN 또는 ROLE_USER/ROLE_ADMIN 형식의 접근 권한. */
	 private String userRole;
	/** 댓글 @멘션을 Slack 사용자에게 연결하는 Slack 멤버 ID. */
	 private String slackUserId;
	/** 서버 프로필 이미지 폴더에 저장된 UUID 기반 파일명. */
	 private String profileImageStoredName;
	/** 브라우저 응답 Content-Type에 사용할 이미지 MIME 타입. */
	 private String profileImageContentType;


	 @Override
	 public String toString() {
	 	return "User{" +
	 			"userId='" + userId + '\'' +
	 			", userPw='[PROTECTED]'" +
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
