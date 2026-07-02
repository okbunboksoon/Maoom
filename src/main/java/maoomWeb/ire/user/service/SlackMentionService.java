package maoomWeb.ire.user.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import maoomWeb.ire.user.dto.PdfDto;
import maoomWeb.ire.user.mapper.PdfMapper;
import maoomWeb.ire.user.mapper.UserMapper;

@Service
/**
 * 댓글 문자열의 @사용자 멘션을 Slack 사용자 ID로 변환하여 알림을 보낸다.
 * Incoming Webhook 채널과 Bot API 개인 DM을 서로 독립적으로 호출한다.
 */
public class SlackMentionService {

    private static final Logger log =
            LoggerFactory.getLogger(SlackMentionService.class);

    private static final Pattern MENTION_PATTERN =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}._%+-])@"
                    + "([\\p{L}\\p{N}._-]+"
                    + "(?:@[A-Za-z0-9.-]+)?)");

    private final String webhookUrl;
    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final UserMapper userMapper;
    private final PdfMapper pdfMapper;

    public SlackMentionService(
            @Value("${app.slack.webhook-url:}") String webhookUrl,
            @Value("${app.slack.bot-token:}") String botToken,
            ObjectMapper objectMapper,
            UserMapper userMapper,
            PdfMapper pdfMapper) {

        this.webhookUrl = webhookUrl;
        this.botToken = botToken;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.pdfMapper = pdfMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostConstruct
    void logConfiguration() {
        log.info(
                "Slack notification configuration: channel={}, dm={}",
                !isBlank(webhookUrl),
                !isBlank(botToken));
    }

    /** 댓글/답글의 멘션 대상에게 채널 알림과 개인 DM을 비동기로 전송한다. */
    public void sendMentionNotification(
            String content,
            Long pdfId,
            String commentCode) {

        if(isBlank(webhookUrl) && isBlank(botToken)){
            return;
        }

        Set<String> mentions = extractMentions(content);

        if(mentions.isEmpty()){
            return;
        }

        Set<String> slackUserIds =
                mentions.stream()
                .map(userMapper::getSlackUserIdByMention)
                .filter(id ->
                        id != null
                        && !id.isBlank())
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new));

        if(slackUserIds.isEmpty()){
            return;
        }

        try{

            String message = buildMessage(
                    pdfId,
                    removeMentions(content),
                    commentCode);
            String channelText =
                    slackUserIds.stream()
                    .map(id -> "<@" + id + ">")
                    .collect(Collectors.joining(" "))
                    + "\n"
                    + message;

            sendChannelNotification(channelText);

            slackUserIds.forEach(
                    slackUserId ->
                            sendDirectNotification(
                                    slackUserId,
                                    message));

        }catch(Exception e){
            log.warn(
                    "Slack mention notification failed",
                    e);
        }
    }

    /** Incoming Webhook에 멘션이 포함된 채널 메시지를 전송한다. */
    private void sendChannelNotification(String text)
            throws Exception {

        if(isBlank(webhookUrl)){
            return;
        }

        String body =
                objectMapper.writeValueAsString(
                        Map.of("text", text));
        HttpRequest request =
                HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header(
                        "Content-Type",
                        "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(body))
                .build();

        httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if(response.statusCode() < 200
                            || response.statusCode() >= 300){
                        log.warn(
                                "Slack channel notification failed: HTTP {}",
                                response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    logSlackFailure(
                            "channel notification",
                            error);
                    return null;
                });
    }

    /** Bot API로 1:1 DM 채널을 열고 멘션 대상에게 메시지를 전송한다. */
    private void sendDirectNotification(
            String slackUserId,
            String message) {

        if(isBlank(botToken)){
            return;
        }

        try{
            callSlackApi(
                    "https://slack.com/api/conversations.open",
                    Map.of("users", slackUserId))
                    .thenCompose(response -> {
                        String channelId =
                                response.path("channel")
                                .path("id")
                                .asText();

                        if(channelId.isBlank()){
                            return CompletableFuture.failedFuture(
                                    new IllegalStateException(
                                            "DM channel ID is missing."));
                        }

                        return callSlackApi(
                                "https://slack.com/api/chat.postMessage",
                                Map.of(
                                        "channel",
                                        channelId,
                                        "text",
                                        message));
                    })
                    .join();

            log.debug(
                    "Slack direct notification sent to {}",
                    slackUserId);
        }catch(Exception e){
            logSlackFailure(
                    "direct notification to " + slackUserId,
                    e);
        }
    }

    /** Slack Web API를 호출하고 ok=false 응답을 예외로 변환한다. */
    private CompletableFuture<JsonNode> callSlackApi(
            String url,
            Map<String,String> payload) {

        HttpRequest request;

        try{
            String body =
                    objectMapper.writeValueAsString(payload);

            request =
                    HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(
                            "Authorization",
                            "Bearer " + botToken)
                    .header(
                            "Content-Type",
                            "application/json; charset=utf-8")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(body))
                    .build();
        }catch(Exception e){
            return CompletableFuture.failedFuture(e);
        }

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try{
                        JsonNode json =
                                objectMapper.readTree(
                                        response.body());

                        if(response.statusCode() < 200
                                || response.statusCode() >= 300
                                || !json.path("ok").asBoolean()){
                            throw new IllegalStateException(
                                    json.path("error")
                                    .asText(
                                            "HTTP "
                                            + response.statusCode()));
                        }

                        return json;
                    }catch(Exception e){
                        throw new IllegalStateException(
                                "Invalid Slack API response.",
                                e);
                    }
                });
    }

    /** 중복 순서를 유지하면서 @ 뒤의 사용자 ID를 추출한다. */
    private Set<String> extractMentions(String content) {

        Set<String> mentions = new LinkedHashSet<>();

        if(content == null){
            return mentions;
        }

        Matcher matcher =
                MENTION_PATTERN.matcher(content);

        while(matcher.find()){
            mentions.add(matcher.group(1));
        }

        return mentions;
    }

    /** Slack 메시지 본문에서는 애플리케이션의 @사용자 토큰을 제거한다. */
    private String removeMentions(String content) {

        if(content == null){
            return "";
        }

        return MENTION_PATTERN
                .matcher(content)
                .replaceAll("")
                .trim()
                .replaceAll("\\s{2,}", " ");
    }

    private String buildMessage(
            Long pdfId,
            String content,
            String commentCode) {

        PdfDto pdf =
                pdfId == null
                ? null
                : pdfMapper.getPdfById(pdfId);
        String filePath =
                pdf == null || isBlank(pdf.getFilePath())
                ? "-"
                : pdf.getFilePath();
        String fileName =
                pdf == null || isBlank(pdf.getFileName())
                ? "PDF"
                : pdf.getFileName();

        return "경    로: " + filePath
                + "\n파 일 명: " + fileName
                + "\n코 멘 트: " + content
                + "\n코멘트ID: "
                + formatCommentNumber(commentCode);
    }

    private String formatCommentNumber(String commentCode) {
        return isBlank(commentCode)
                ? "-"
                : commentCode.replaceFirst("^id", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void logSlackFailure(
            String operation,
            Throwable error) {

        Throwable cause =
                error.getCause() == null
                ? error
                : error.getCause();

        log.warn(
                "Slack {} failed: {}",
                operation,
                cause.getMessage());
    }
}
