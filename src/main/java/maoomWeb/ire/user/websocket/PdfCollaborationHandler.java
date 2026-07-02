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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import maoomWeb.ire.user.service.PdfAccessService;

/**
 * 같은 PDF를 보고 있는 브라우저끼리 댓글 변경과 편집 상태를 실시간으로 전달한다.
 *
 * <p>연결 주소의 {@code pdfId}를 방 번호로 사용한다. DB 내용을 WebSocket에 직접
 * 저장하지는 않으며, 댓글이 바뀌면 {@code COMMENT_CHANGED} 신호만 보낸다.
 * 신호를 받은 화면이 REST API로 최신 댓글 목록을 다시 조회하는 구조다.</p>
 */
@Component
public class PdfCollaborationHandler
        extends TextWebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(PdfCollaborationHandler.class);

    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final PdfAccessService pdfAccessService;

    // PDF ID -> 해당 PDF를 보고 있는 WebSocket 세션 목록
    private final Map<Long,Set<WebSocketSession>> rooms =
            new ConcurrentHashMap<>();

    // 세션 ID -> PDF ID. 연결 종료 시 어느 방에서 제거할지 찾는 역방향 인덱스다.
    private final Map<String,Long> sessionPdfIds =
            new ConcurrentHashMap<>();

    // 세션 ID -> 화면에 표시할 사용자 정보
    private final Map<String,Map<String,String>> sessionUsers =
            new ConcurrentHashMap<>();

    public PdfCollaborationHandler(
            ObjectMapper objectMapper,
            CurrentUserService currentUserService,
            PdfAccessService pdfAccessService) {

        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.pdfAccessService = pdfAccessService;
    }

    @Override
    /**
     * 연결 주소의 pdfId와 로그인 사용자를 검사한 뒤 해당 PDF 방에 세션을 등록한다.
     * 등록이 끝나면 방 전체에 최신 접속자 목록(PRESENCE)을 보낸다.
     */
    public void afterConnectionEstablished(
            WebSocketSession session) throws Exception {

        Long pdfId = getPdfId(session.getUri());

        if(pdfId == null){
            session.close(
                    CloseStatus.BAD_DATA);
            return;
        }

        String userId = getUserId(session.getPrincipal());

        if(userId == null
                || !pdfAccessService.canAccessPdf(pdfId, userId)){
            session.close(
                    CloseStatus.POLICY_VIOLATION);
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
    /**
     * 화면에서 보낸 EDITING_START/EDITING_STOP을 검증한 뒤 같은 PDF 사용자에게 전달한다.
     * 클라이언트가 임의의 commentId를 보내더라도 현재 PDF 소속 댓글이 아니면 무시한다.
     */
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws Exception {

        Long pdfId =
                sessionPdfIds.get(
                        session.getId());

        if(pdfId == null){
            return;
        }

        String userId = getUserId(session.getPrincipal());

        if(userId == null
                || !pdfAccessService.canAccessPdf(pdfId, userId)){
            session.close(
                    CloseStatus.POLICY_VIOLATION);
            return;
        }

        Map<String,Object> payload;

        try{
            payload =
                    objectMapper.readValue(
                            message.getPayload(),
                            new TypeReference<Map<String,Object>>(){});
        }catch(IOException error){
            log.debug(
                    "Ignoring invalid collaboration message",
                    error);
            return;
        }

        String type =
                String.valueOf(
                        payload.get("type"));

        if(!"EDITING_START".equals(type)
                && !"EDITING_STOP".equals(type)){
            return;
        }

        Long commentId = toLong(payload.get("commentId"));

        if(commentId == null
                || !pdfAccessService.canAccessCommentInPdf(
                        commentId,
                        pdfId,
                        userId)){
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
                commentId);
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
    /** 연결이 끝난 세션과 사용자 정보를 제거하고 최신 접속자 목록을 다시 보낸다. */
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
                rooms.remove(pdfId, room);
            }
        }

        broadcastPresence(pdfId);
    }

    /**
     * CommentService가 DB 트랜잭션을 완료한 뒤 호출한다.
     * 수신 화면은 이 신호를 받으면 /api/comment/list를 다시 호출한다.
     */
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

            List<WebSocketSession> staleSessions =
                    new ArrayList<>();

            room.removeIf(session -> {

                if(!session.isOpen()){
                    staleSessions.add(session);
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
                    staleSessions.add(session);
                    return true;
                }
            });

            staleSessions.forEach(this::removeSessionMetadata);

            if(room.isEmpty()){
                rooms.remove(pdfId, room);
            }

        }catch(Exception e){
            log.warn(
                    "PDF collaboration broadcast failed",
                    e);
        }
    }

    private void removeSessionMetadata(WebSocketSession session) {

        sessionPdfIds.remove(session.getId());
        sessionUsers.remove(session.getId());
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

    private String getUserId(Principal principal) {
        if(principal instanceof Authentication authentication){
            return currentUserService.getUserId(authentication);
        }
        return principal == null ? null : principal.getName();
    }

    private Long toLong(Object value) {
        if(value instanceof Number number){
            return number.longValue();
        }
        if(value == null){
            return null;
        }
        try{
            return Long.valueOf(String.valueOf(value));
        }catch(NumberFormatException error){
            return null;
        }
    }
}
