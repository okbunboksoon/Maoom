package maoomWeb.ire.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import maoomWeb.ire.user.oauth.GoogleOAuthSuccessHandler;

/**
 * PDF 리뷰 기능을 쓰기 위해 기존 로그인 사용자에게 Google Drive 권한을 추가 연결한다.
 *
 * <p>화면 위치: Google Drive 권한이 없을 때 유저 화면에서 {@code /google/connect}
 * 로 이동하면 OAuth 동의 화면을 거쳐 다시 사용자 화면으로 돌아온다.<br>
 * 연결 처리: OAuth 성공 후 {@link GoogleOAuthSuccessHandler}가 세션에 저장된
 * 사용자 ID/권한을 읽어 기존 계정에 Drive 인증 정보를 연결한다.</p>
 *
 * <p>수정 시 주의점: OAuth 이동 전 세션에 현재 사용자 ID와 권한을 보관해야
 * 콜백에서 새 계정을 만들지 않고 기존 계정으로 복귀할 수 있다.</p>
 */
@Controller
public class GoogleDriveConnectController {

	/**
	 * OAuth 이동 전에 현재 사용자 정보를 세션에 보관한다.
	 * 인증 완료 후 성공 핸들러가 이 정보를 이용해 기존 계정으로 복귀시킨다.
	 */
	@GetMapping("/google/connect")
	public String connect(
			Authentication authentication,
			HttpSession session) {

		session.setAttribute(
				GoogleOAuthSuccessHandler.CONNECT_USER_ID,
				authentication.getName());

		String role =
				authentication.getAuthorities()
					.stream()
					.findFirst()
					.map(authority ->
							authority.getAuthority())
					.orElse("ROLE_USER");

		session.setAttribute(
				GoogleOAuthSuccessHandler.CONNECT_USER_ROLE,
				role);

		return "redirect:/oauth2/authorization/google";
	}
}
