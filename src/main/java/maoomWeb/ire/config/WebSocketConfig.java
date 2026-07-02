package maoomWeb.ire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import maoomWeb.ire.user.websocket.PdfCollaborationHandler;

/**
 * PDF 리뷰 화면과 실시간 협업 핸들러를 연결하는 WebSocket 설정이다.
 *
 * <p>브라우저의 {@code pdfview.html}은 {@code /ws/pdf?pdfId=...}로 접속하고,
 * 실제 접속자/편집 상태 관리는 {@link PdfCollaborationHandler}가 담당한다.</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PdfCollaborationHandler collaborationHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            PdfCollaborationHandler collaborationHandler,
            @Value("${app.websocket.allowed-origins:}")
            String allowedOrigins) {
        this.collaborationHandler = collaborationHandler;
        this.allowedOrigins =
                java.util.Arrays.stream(
                        allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    /**
     * {@code /ws/pdf} 요청을 협업 핸들러에 연결한다.
     * allowed-origins 설정이 비어 있으면 현재 서버와 같은 출처의 기본 정책을 사용한다.
     */
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {

        var registration = registry.addHandler(
                collaborationHandler,
                "/ws/pdf");

        if(allowedOrigins.length > 0){
            registration.setAllowedOrigins(allowedOrigins);
        }
    }
}
