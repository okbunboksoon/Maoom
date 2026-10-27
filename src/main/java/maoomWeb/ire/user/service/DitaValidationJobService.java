package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/** HTTP 연결과 분리된 Schematron 백그라운드 작업의 수명 주기를 관리한다. */
@Service
public class DitaValidationJobService {
    private final DitaValidationWorkflowService workflow;
    private final DitaValidationProgressService progress;
    private final CurrentUserService currentUserService;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final long retentionMinutes;

    public DitaValidationJobService(DitaValidationWorkflowService workflow,
            DitaValidationProgressService progress,
            CurrentUserService currentUserService,
            @Value("${dita.validation.async-threads:2}") int threads,
            @Value("${dita.validation.async-queue-capacity:20}") int queueCapacity,
            @Value("${dita.validation.progress-retention-minutes:60}") long retentionMinutes) {
        this.workflow = workflow;
        this.progress = progress;
        this.currentUserService = currentUserService;
        this.retentionMinutes = Math.max(1, retentionMinutes);
        this.executor = new ThreadPoolExecutor(Math.max(1, threads), Math.max(1, threads), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Math.max(1, queueCapacity)), runnable -> {
                    Thread thread = new Thread(runnable, "dita-validation-job");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized JobAcceptedResponse submit(RunRequest request, Authentication authentication) {
        if (request == null) throw new IllegalArgumentException("검사 요청이 없습니다.");
        String ownerUserId = currentUserService.getUserId(authentication);
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }
        boolean alreadyRunning = jobs.values().stream().anyMatch(job -> ownerUserId.equals(job.ownerUserId)
                && ("WAITING".equals(job.status) || "RUNNING".equals(job.status)));
        if (alreadyRunning) throw new IllegalStateException("이미 실행 중인 Schematron 검사가 있습니다.");
        String jobId = validToken(request.progressToken()) ? request.progressToken() : UUID.randomUUID().toString();
        Job newJob = new Job(jobId, ownerUserId, currentUserService.getUserName(authentication));
        if (jobs.putIfAbsent(jobId, newJob) != null) {
            throw new IllegalArgumentException("이미 사용 중인 검사 작업 번호입니다.");
        }
        Job job = jobs.get(jobId);
        progress.waiting(jobId);
        try {
            job.future = executor.submit(() -> execute(job, request, authentication));
        } catch (RuntimeException ex) {
            jobs.remove(jobId);
            progress.fail(jobId, new IllegalStateException("검사 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요."));
            throw new IllegalStateException("검사 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
        }
        return new JobAcceptedResponse(jobId, "WAITING");
    }

    public JobStatusResponse get(String jobId, Authentication authentication) {
        Job job = jobs.get(jobId);
        if (job == null) return new JobStatusResponse(jobId, "UNKNOWN", null, 0, 0, null,
                "검사 작업을 찾을 수 없습니다.");
        requireOwnerOrAdministrator(job, authentication);
        return response(job);
    }

    public ProgressResponse getProgress(String jobId, Authentication authentication) {
        Job job = jobs.get(jobId);
        if (job == null) return new ProgressResponse("UNKNOWN", 0, 0, null,
                LocalDateTime.now(), "검사 작업을 찾을 수 없습니다.");
        requireOwnerOrAdministrator(job, authentication);
        return progress.get(jobId);
    }

    private JobStatusResponse response(Job job) {
        RunResponse result = job.result;
        return new JobStatusResponse(job.id, job.status,
                result == null ? null : result.historyId(),
                result == null ? 0 : result.validatedFileCount(),
                result == null ? 0 : result.issueCount(),
                result == null ? null : result.reportUrl(), job.message);
    }

    public JobStatusResponse cancel(String jobId, Authentication authentication) {
        Job job = jobs.get(jobId);
        if (job == null) return new JobStatusResponse(jobId, "UNKNOWN", null, 0, 0, null,
                "검사 작업을 찾을 수 없습니다.");
        requireOwnerOrAdministrator(job, authentication);
        cancelInternal(job, "사용자가 Schematron 검사를 취소했습니다.");
        return response(job);
    }

    public java.util.List<JobAdminItem> findAllForAdministrator(Authentication authentication) {
        requireAdministrator(authentication);
        return jobs.values().stream().sorted(java.util.Comparator.comparing((Job job) -> job.createdAt).reversed())
                .map(job -> new JobAdminItem(job.id, job.status, job.ownerUserId, job.ownerName,
                        job.createdAt, job.updatedAt, job.result == null ? null : job.result.historyId(),
                        job.result == null ? 0 : job.result.validatedFileCount(),
                        job.result == null ? 0 : job.result.issueCount(), job.message)).toList();
    }

    public JobStatusResponse cancelAsAdministrator(String jobId, Authentication authentication) {
        requireAdministrator(authentication);
        Job job = jobs.get(jobId);
        if (job == null) return new JobStatusResponse(jobId, "UNKNOWN", null, 0, 0, null,
                "검사 작업을 찾을 수 없습니다.");
        cancelInternal(job, "관리자가 Schematron 검사를 취소했습니다.");
        return response(job);
    }

    private void cancelInternal(Job job, String message) {
        if (!"WAITING".equals(job.status) && !"RUNNING".equals(job.status)) return;
        progress.cancel(job.id);
        Future<?> future = job.future;
        if (future != null) future.cancel(true);
        job.status = "CANCELLED";
        job.message = message;
        job.updatedAt = LocalDateTime.now();
        progress.cancelled(job.id, job.message);
    }

    private void requireOwnerOrAdministrator(Job job, Authentication authentication) {
        String userId = currentUserService.getUserId(authentication);
        if (!job.ownerUserId.equals(userId) && !currentUserService.isAdministrator(authentication)) {
            throw new AccessDeniedException("다른 사용자의 검사 작업에는 접근할 수 없습니다.");
        }
    }

    private void requireAdministrator(Authentication authentication) {
        if (!currentUserService.isAdministrator(authentication)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
    }

    private void execute(Job job, RunRequest request, Authentication authentication) {
        if ("CANCELLED".equals(job.status)) return;
        job.status = "RUNNING";
        job.updatedAt = LocalDateTime.now();
        try {
            RunResponse result = workflow.run(new RunRequest(request.inputPath(), request.mapPath(),
                    request.schematronType(), request.scope(), request.selectedNodeIds(),
                    request.validateMap(), job.id), authentication);
            job.result = result;
            job.status = "SUCCESS";
        } catch (java.util.concurrent.CancellationException ex) {
            job.status = "CANCELLED";
            job.message = ex.getMessage();
        } catch (Exception ex) {
            job.status = "FAILED";
            job.message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        } finally {
            job.updatedAt = LocalDateTime.now();
        }
    }

    @Scheduled(fixedDelayString = "${dita.validation.progress-cleanup-ms:60000}")
    public void cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(retentionMinutes);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            boolean expired = !"RUNNING".equals(job.status) && job.updatedAt.isBefore(cutoff);
            if (expired && job.future != null && !job.future.isDone()) job.future.cancel(true);
            return expired;
        });
    }

    @PreDestroy
    public void shutdown() {
        jobs.values().forEach(job -> cancelInternal(job, "서버 종료로 Schematron 검사가 중단되었습니다."));
        executor.shutdownNow();
    }

    private boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9-]{1,64}");
    }

    private static final class Job {
        private final String id;
        private final String ownerUserId;
        private final String ownerName;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private volatile String status = "WAITING";
        private volatile String message = "";
        private volatile LocalDateTime updatedAt = LocalDateTime.now();
        private volatile RunResponse result;
        private volatile Future<?> future;
        private Job(String id, String ownerUserId, String ownerName) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.ownerName = ownerName;
        }
    }
}
