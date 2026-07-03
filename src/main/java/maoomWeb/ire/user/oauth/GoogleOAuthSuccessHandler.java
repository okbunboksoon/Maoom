package maoomWeb.ire.user.oauth;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
@ConditionalOnBean(OAuth2AuthorizedClientService.class)
/**
 * Google OAuth 인증 성공 후 토큰을 기존 MAOOM 계정에 연결한다.
 * Drive 연결 전 저장한 세션 정보가 없으면 일반 OAuth 로그인으로 처리한다.
 */
public class GoogleOAuthSuccessHandler
		implements AuthenticationSuccessHandler {

	public static final String CONNECT_USER_ID =
			"GOOGLE_CONNECT_USER_ID";

	public static final String CONNECT_USER_ROLE =
			"GOOGLE_CONNECT_USER_ROLE";

	private final OAuth2AuthorizedClientService clientService;

	public GoogleOAuthSuccessHandler(
			OAuth2AuthorizedClientService clientService) {
		this.clientService = clientService;
	}

	@Override
	/** Google 토큰의 principal을 로컬 사용자 인증으로 교체하고 PDF 목록으로 이동한다. */
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication)
			throws IOException, ServletException {

		HttpSession session =
				request.getSession(false);

		String userId =
				session == null
					? null
					: (String) session.getAttribute(
							CONNECT_USER_ID);

		String userRole =
				session == null
					? null
					: (String) session.getAttribute(
							CONNECT_USER_ROLE);

		if(!(authentication
				instanceof OAuth2AuthenticationToken oauthToken)
				|| userId == null
				|| userId.isBlank()) {

			response.sendRedirect("/main");
			return;
		}

		OAuth2AuthorizedClient client =
				clientService.loadAuthorizedClient(
						oauthToken.getAuthorizedClientRegistrationId(),
						oauthToken.getName());

		if(client == null) {
			response.sendError(
					HttpServletResponse.SC_UNAUTHORIZED,
					"Google OAuth 토큰을 저장하지 못했습니다.");
			return;
		}

		if(userRole == null || userRole.isBlank()) {
			userRole = "ROLE_USER";
		}

		Authentication localAuthentication =
				new UsernamePasswordAuthenticationToken(
						userId,
						null,
						List.of(
								new SimpleGrantedAuthority(
										userRole)));

		clientService.saveAuthorizedClient(
				client,
				localAuthentication);

		SecurityContext context =
				SecurityContextHolder.createEmptyContext();

		context.setAuthentication(
				localAuthentication);
		SecurityContextHolder.setContext(
				context);

		session.setAttribute(
				HttpSessionSecurityContextRepository
					.SPRING_SECURITY_CONTEXT_KEY,
				context);

		session.removeAttribute(
				CONNECT_USER_ID);
		session.removeAttribute(
				CONNECT_USER_ROLE);

		response.sendRedirect("/pdf/list");
	}
}
