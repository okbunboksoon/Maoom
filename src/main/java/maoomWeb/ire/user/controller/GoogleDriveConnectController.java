package maoomWeb.ire.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import maoomWeb.ire.user.oauth.GoogleOAuthSuccessHandler;

@Controller
/**
 * 기존 로그인 사용자에게 Google Drive 권한을 추가 연결하는 컨트롤러.
 */
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
