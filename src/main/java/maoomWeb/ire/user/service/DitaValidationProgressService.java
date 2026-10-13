package maoomWeb.ire.user.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.DitaValidationDtos.ProgressResponse;

/** 긴 동기 검사 중에도 별도 HTTP 요청으로 파일 단위 진행 상태를 조회할 수 있게 보관한다. */
@Service
public class DitaValidationProgressService {
    // 검사 POST 요청과 별도의 진행 조회 GET 요청이 동시에 접근하므로 thread-safe Map을 사용한다.
    private final Map<String, ProgressResponse> progress = new ConcurrentHashMap<>();

    public void start(String token) {
        if (valid(token)) progress.put(token, new ProgressResponse("RUNNING", 0, 0, "", LocalDateTime.now(), ""));
    }

    public void update(String token, int completed, int total, Path currentFile) {
        // updatedAt은 특정 파일에서 장시간 멈췄는지 화면에서 판단하기 위한 마지막 처리 시각이다.
        if (valid(token)) progress.put(token, new ProgressResponse("RUNNING", completed, total,
                currentFile == null ? "" : currentFile.getFileName().toString(), LocalDateTime.now(), ""));
    }

    public void complete(String token, int total) {
        if (valid(token)) progress.put(token, new ProgressResponse("SUCCESS", total, total, "", LocalDateTime.now(), ""));
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

    private boolean valid(String token) {
        // URL 경로에 사용되는 토큰은 짧은 영문·숫자·하이픈만 허용한다.
        return token != null && token.matches("[A-Za-z0-9-]{1,64}");
    }
}
