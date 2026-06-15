package maoomWeb.ire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import maoomWeb.ire.user.websocket.PdfCollaborationHandler;

@Configuration
@EnableWebSocket
/**
 * PDF 공동 작업용 WebSocket 엔드포인트를 등록한다.
 */
public class WebSocketConfig implements WebSocketConfigurer {

    private final PdfCollaborationHandler collaborationHandler;

    public WebSocketConfig(
            PdfCollaborationHandler collaborationHandler) {
        this.collaborationHandler = collaborationHandler;
    }

    @Override
    /** 클라이언트가 /ws/pdf 주소로 협업 채널에 접속하도록 핸들러를 연결한다. */
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {

        registry.addHandler(
                collaborationHandler,
                "/ws/pdf");
    }
}
