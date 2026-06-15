package maoomWeb.ire.user.websocket;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import maoomWeb.ire.user.service.CurrentUserService;

@Component
/**
 * PDF별 WebSocket 방을 관리하며 접속자와 댓글 편집 상태를 실시간 공유한다.
 */
public class PdfCollaborationHandler
        extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    private final Map<Long,Set<WebSocketSession>> rooms =
            new ConcurrentHashMap<>();

    private final Map<String,Long> sessionPdfIds =
            new ConcurrentHashMap<>();

    private final Map<String,Map<String,String>> sessionUsers =
            new ConcurrentHashMap<>();

    public PdfCollaborationHandler(
            ObjectMapper objectMapper,
            CurrentUserService currentUserService) {

        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    /** 쿼리의 pdfId를 기준으로 세션을 방에 등록하고 접속자 목록을 방송한다. */
    public void afterConnectionEstablished(
            WebSocketSession session) throws Exception {

        Long pdfId = getPdfId(session.getUri());

        if(pdfId == null){
            session.close(
                    CloseStatus.BAD_DATA);
            return;
        }

        Map<String,String> user =
                getUser(session.getPrincipal());

        rooms.computeIfAbsent(
                pdfId,
                key -> ConcurrentHashMap.newKeySet())
                .add(session);

        sessionPdfIds.put(
                session.getId(),
                pdfId);
        sessionUsers.put(
                session.getId(),
                user);

        broadcastPresence(pdfId);
    }

    @Override
    /** 클라이언트의 댓글 편집 시작/종료 이벤트를 같은 PDF 사용자에게 전달한다. */
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws Exception {

        Long pdfId =
                sessionPdfIds.get(
                        session.getId());

        if(pdfId == null){
            return;
        }

        Map<String,Object> payload =
                objectMapper.readValue(
                        message.getPayload(),
                        new TypeReference<Map<String,Object>>(){});

        String type =
                String.valueOf(
                        payload.get("type"));

        if(!"EDITING_START".equals(type)
                && !"EDITING_STOP".equals(type)){
            return;
        }

        Map<String,Object> event =
                new LinkedHashMap<>();

        event.put("type", "EDITING");
        event.put(
                "editing",
                "EDITING_START".equals(type));
        event.put(
                "commentId",
                payload.get("commentId"));
        event.put(
                "user",
                sessionUsers.get(
                        session.getId()));

        broadcast(
                pdfId,
                event,
                session.getId());
    }

    @Override
    /** 종료된 세션을 방에서 제거하고 최신 접속자 목록을 방송한다. */
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) throws Exception {

        Long pdfId =
                sessionPdfIds.remove(
                        session.getId());

        sessionUsers.remove(
                session.getId());

        if(pdfId == null){
            return;
        }

        Set<WebSocketSession> room =
                rooms.get(pdfId);

        if(room != null){

            room.remove(session);

            if(room.isEmpty()){
                rooms.remove(pdfId);
            }
        }

        broadcastPresence(pdfId);
    }

    /** 댓글 데이터가 바뀌었음을 PDF 방 전체에 알린다. */
    public void broadcastCommentChanged(Long pdfId) {

        if(pdfId == null){
            return;
        }

        broadcast(
                pdfId,
                Map.of("type", "COMMENT_CHANGED"),
                null);
    }

    /** 현재 PDF 방에 접속한 중복 없는 사용자 목록을 방송한다. */
    private void broadcastPresence(Long pdfId) {

        Set<WebSocketSession> room =
                rooms.get(pdfId);

        List<Map<String,String>> users =
                new ArrayList<>();

        if(room != null){

            room.stream()
                .map(WebSocketSession::getId)
                .map(sessionUsers::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(users::add);
        }

        Map<String,Object> event =
                new LinkedHashMap<>();

        event.put("type", "PRESENCE");
        event.put("users", users);

        broadcast(
                pdfId,
                event,
                null);
    }

    /** 이벤트를 JSON으로 직렬화해 열린 세션에 전송하고 실패한 세션을 정리한다. */
    private void broadcast(
            Long pdfId,
            Map<String,?> event,
            String excludedSessionId) {

        Set<WebSocketSession> room =
                rooms.get(pdfId);

        if(room == null){
            return;
        }

        try{

            TextMessage message =
                    new TextMessage(
                            objectMapper.writeValueAsString(event));

            room.removeIf(session -> {

                if(!session.isOpen()){
                    return true;
                }

                if(session.getId().equals(
                        excludedSessionId)){
                    return false;
                }

                try{

                    synchronized(session){
                        session.sendMessage(message);
                    }

                    return false;

                }catch(IOException e){
                    return true;
                }
            });

        }catch(Exception e){
            System.err.println(
                    "PDF collaboration broadcast failed: "
                    + e.getMessage());
        }
    }

    /** WebSocket 접속 URI에서 숫자형 pdfId 쿼리 값을 읽는다. */
    private Long getPdfId(URI uri) {

        if(uri == null){
            return null;
        }

        String value =
                UriComponentsBuilder
                .fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("pdfId");

        if(value == null){
            return null;
        }

        try{
            return Long.valueOf(value);
        }catch(NumberFormatException e){
            return null;
        }
    }

    /** Principal을 화면에 전달할 사용자 ID/이름 맵으로 변환한다. */
    private Map<String,String> getUser(
            Principal principal) {

        Map<String,String> user =
                new LinkedHashMap<>();

        if(principal instanceof Authentication authentication){

            user.put(
                    "userId",
                    currentUserService.getUserId(authentication));
            user.put(
                    "userName",
                    currentUserService.getUserName(authentication));

        }else{

            String name =
                    principal == null
                            ? "unknown"
                            : principal.getName();

            user.put("userId", name);
            user.put("userName", name);
        }

        return user;
    }
}
