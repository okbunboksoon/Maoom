package maoomWeb.ire.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
/**
 * 일반 로그인과 인증 예외 경로를 위한 Spring Security 설정.
 */
public class SecurityConfig {

	/** 애플리케이션에서 비밀번호 저장과 검증에 사용할 BCrypt 인코더. */
	@Bean
	public PasswordEncoder passwordEncoder() {
	    return new BCryptPasswordEncoder();
	}

	/** 공개 리소스와 인증 필요 경로를 설정한다. */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http)
			throws Exception {

	    http
	        .csrf(csrf -> csrf.disable())
	        .authorizeHttpRequests(auth -> auth
	            .requestMatchers(
	                "/",
	                "/login",
	                "/css/**",
	                "/user/css/**",
	                "/user/font/**",
	                "/js/**",
	                "/images/**",
	                "/pdfjs/**"
	            ).permitAll()
	            .requestMatchers("/admin/**").hasRole("ADMIN")
	            .anyRequest().authenticated()
	        )
	        .logout(logout -> logout
	            .logoutSuccessUrl("/")
	            .invalidateHttpSession(true)
	            .clearAuthentication(true)
	            .deleteCookies("JSESSIONID")
	        )
	        .exceptionHandling(exception -> exception
	            .authenticationEntryPoint(
	                new LoginUrlAuthenticationEntryPoint("/")
	            )
	        )
	        .headers(headers ->
	            headers.frameOptions(frame ->
	                frame.sameOrigin()
	            )
	        );

	    return http.build();
	}
}
