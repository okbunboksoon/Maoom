package maoomWeb.ire.user.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PreDestroy;

import maoomWeb.ire.user.dto.DitaValidationDtos.ProgressResponse;

/** 긴 동기 검사 중에도 별도 HTTP 요청으로 파일 단위 진행 상태를 조회할 수 있게 보관한다. */
@Service
public class DitaValidationProgressService {
    // 검사 POST 요청과 별도의 진행 조회 GET 요청이 동시에 접근하므로 thread-safe Map을 사용한다.
    private final Map<String, ProgressResponse> progress = new ConcurrentHashMap<>();
    private final Map<String, Thread> runningThreads = new ConcurrentHashMap<>();
    private final Set<String> cancellationRequests = ConcurrentHashMap.newKeySet();
    private final long retentionMinutes;

    public DitaValidationProgressService() {
        this(60);
    }

    @Autowired
    public DitaValidationProgressService(
            @Value("${dita.validation.progress-retention-minutes:60}") long retentionMinutes) {
        this.retentionMinutes = Math.max(1, retentionMinutes);
    }

    public void start(String token) {
        if (!valid(token)) return;
        cancellationRequests.remove(token);
        runningThreads.put(token, Thread.currentThread());
        progress.put(token, new ProgressResponse("RUNNING", 0, 0, "", LocalDateTime.now(), ""));
    }

    public void waiting(String token) {
        if (valid(token)) progress.put(token,
                new ProgressResponse("WAITING", 0, 0, "", LocalDateTime.now(), "검사 대기 중입니다."));
    }

    public void update(String token, int completed, int total, Path currentFile) {
        // updatedAt은 특정 파일에서 장시간 멈췄는지 화면에서 판단하기 위한 마지막 처리 시각이다.
        if (valid(token)) progress.put(token, new ProgressResponse("RUNNING", completed, total,
                currentFile == null ? "" : currentFile.getFileName().toString(), LocalDateTime.now(), ""));
    }

    public void complete(String token, int total) {
        if (valid(token)) progress.put(token, new ProgressResponse("SUCCESS", total, total, "", LocalDateTime.now(), ""));
    }

    public ProgressResponse cancel(String token) {
        if (!valid(token)) return get(token);
        cancellationRequests.add(token);
        ProgressResponse previous = get(token);
        ProgressResponse cancelling = new ProgressResponse("CANCELLING", previous.completed(), previous.total(),
                previous.currentFile(), LocalDateTime.now(), "검사 취소를 요청했습니다.");
        progress.put(token, cancelling);
        Thread thread = runningThreads.get(token);
        if (thread != null) thread.interrupt();
        return cancelling;
    }

    public boolean isCancellationRequested(String token) {
        return valid(token) && (cancellationRequests.contains(token) || Thread.currentThread().isInterrupted());
    }

    public void cancelled(String token, String message) {
        if (!valid(token)) return;
        ProgressResponse previous = get(token);
        progress.put(token, new ProgressResponse("CANCELLED", previous.completed(), previous.total(),
                previous.currentFile(), LocalDateTime.now(), message));
    }

    public void finish(String token) {
        if (!valid(token)) return;
        runningThreads.remove(token, Thread.currentThread());
        cancellationRequests.remove(token);
        // 웹 요청 스레드가 풀에 반환되기 전에 취소용 interrupt 상태를 제거한다.
        Thread.interrupted();
    }

    public void fail(String token, Throwable error) {
        if (!valid(token)) return;
        ProgressResponse previous = get(token);
        String message = error == null || error.getMessage() == null ? "검사 실패" : error.getMessage();
        progress.put(token, new ProgressResponse("FAILED", previous.completed(), previous.total(),
                previous.currentFile(), LocalDateTime.now(), message));
    }

    public ProgressResponse get(String token) {
        if (!valid(token)) return new ProgressResponse("UNKNOWN", 0, 0, "", null, "올바르지 않은 진행 번호입니다.");
        return progress.getOrDefault(token, new ProgressResponse("WAITING", 0, 0, "", null, ""));
    }

    /** 완료·실패·취소 상태는 잠시 조회할 수 있게 둔 뒤 메모리에서 제거한다. */
    @Scheduled(fixedDelayString = "${dita.validation.progress-cleanup-ms:60000}")
    public void cleanupExpired() {
        cleanupExpired(LocalDateTime.now());
    }

    void cleanupExpired(LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(retentionMinutes);
        progress.entrySet().removeIf(entry -> terminal(entry.getValue())
                && entry.getValue().updatedAt() != null
                && entry.getValue().updatedAt().isBefore(cutoff));
        // 비정상 흐름에서 남은 취소 토큰도 실행 스레드와 진행 정보가 모두 없으면 함께 정리한다.
        cancellationRequests.removeIf(token -> !runningThreads.containsKey(token) && !progress.containsKey(token));
    }

    @PreDestroy
    public void shutdown() {
        // 서버 종료 중인 검사는 취소 표식을 남기고 interrupt하여 가능한 한 빨리 중단시킨다.
        runningThreads.forEach((token, thread) -> {
            cancellationRequests.add(token);
            thread.interrupt();
        });
    }

    private boolean terminal(ProgressResponse value) {
        return "SUCCESS".equals(value.status()) || "FAILED".equals(value.status())
                || "CANCELLED".equals(value.status()) || "UNKNOWN".equals(value.status())
                || "WAITING".equals(value.status());
    }

    private boolean valid(String token) {
        // URL 경로에 사용되는 토큰은 짧은 영문·숫자·하이픈만 허용한다.
        return token != null && token.matches("[A-Za-z0-9-]{1,64}");
    }
}
