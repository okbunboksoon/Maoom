package maoomWeb.ire.user.service;

import java.nio.file.*;
import java.util.*;
import java.time.Instant;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import com.sts.dita.core.ValidationIssue;
import com.sts.dita.core.ValidationResult;
import maoomWeb.ire.user.config.DitaValidationProperties;

/** GOMS 프로젝트의 로컬 복사, 원본 경로 복원과 임시 폴더 정리를 담당한다. */
@Service
public class DitaValidationStagingService {
    private static final Logger log = LoggerFactory.getLogger(DitaValidationStagingService.class);
    private final DitaValidationProgressService progressService;
    private final Path stagingRoot;
    private final Duration retention;

    @Autowired
    public DitaValidationStagingService(DitaValidationProgressService progressService,
            DitaValidationProperties properties) {
        this(progressService, Path.of(properties.getStagingRoot()),
                Duration.ofHours(properties.getStagingRetentionHours()));
    }

    DitaValidationStagingService(DitaValidationProgressService progressService, Path stagingRoot) {
        this(progressService, stagingRoot, Duration.ofHours(24));
    }

    DitaValidationStagingService(DitaValidationProgressService progressService,
            Path stagingRoot, Duration retention) {
        this.progressService = progressService;
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        this.retention = retention;
    }

    Path folder(long historyId) {
        Path folder = stagingRoot.resolve("history-" + historyId).normalize();
        if (!folder.startsWith(stagingRoot)) throw new IllegalStateException("올바른 로컬 검사 임시 경로가 아닙니다.");
        return folder;
    }

    void stage(Path sourceRoot, Path targetRoot, String progressToken, Runnable cancellationCheck) throws Exception {
        if (targetRoot.startsWith(sourceRoot) || sourceRoot.startsWith(targetRoot)) {
            throw new IllegalStateException("로컬 검사 임시 폴더는 V 서버 프로젝트 경로와 분리되어야 합니다.");
        }
        if (Files.exists(targetRoot)) cleanup(targetRoot);
        List<Path> entries = DitaValidationTargetService.walkProjectEntries(sourceRoot);
        long requiredBytes = 0L;
        for (Path entry : entries) {
            if (Files.isSymbolicLink(entry)) {
                throw new IllegalStateException("심볼릭 링크가 포함된 GOMS 프로젝트는 로컬 복사할 수 없습니다: "
                        + sourceRoot.relativize(entry));
            }
            if (Files.isRegularFile(entry)) requiredBytes += Files.size(entry);
        }
        Files.createDirectories(targetRoot);
        if (Files.getFileStore(targetRoot).getUsableSpace() < requiredBytes) {
            throw new IllegalStateException("로컬 검사 임시 폴더의 디스크 공간이 부족합니다.");
        }
        int completed = 0;
        int total = Math.max(1, entries.size() - 1);
        for (Path entry : entries) {
            if (entry.equals(sourceRoot)) continue;
            cancellationCheck.run();
            Path relative = sourceRoot.relativize(entry);
            Path target = targetRoot.resolve(relative).normalize();
            if (!target.startsWith(targetRoot)) throw new IllegalStateException("올바르지 않은 복사 경로입니다.");
            if (Files.isDirectory(entry)) {
                Files.createDirectories(target);
            } else if (Files.isRegularFile(entry)) {
                Files.createDirectories(target.getParent());
                Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
            }
            progressService.updateStaging(progressToken, ++completed, total, relative);
        }
    }

    ValidationResult restoreOriginalPaths(ValidationResult result, Path localRoot,
            Path sourceRoot, Path sourceMap) {
        List<ValidationIssue> issues = result.issues().stream().map(issue -> new ValidationIssue(
                restoreOriginalPath(issue.file(), localRoot, sourceRoot), issue.title(), issue.parentTitles(),
                issue.line(), issue.severity(), issue.category(), issue.ruleId(), issue.ruleType(),
                issue.location(), issue.detectedContent(), issue.message(), issue.mapOrder(), issue.level())).toList();
        return new ValidationResult(sourceMap, result.startedAt(), result.finishedAt(),
                result.validatedFileCount(), issues);
    }

    void cleanup(Path folder) {
        if (folder == null || !folder.startsWith(stagingRoot) || !Files.exists(folder)) return;
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("GOMS 로컬 검사 임시 폴더를 정리하지 못했습니다: {}", folder, ex);
        }
    }

    /** 비정상 종료로 남은 오래된 작업 폴더만 서버 시작 후 정리한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupExpiredFolders() {
        if (!Files.isDirectory(stagingRoot)) return;
        Instant cutoff = Instant.now().minus(retention);
        try (var paths = Files.list(stagingRoot)) {
            for (Path folder : paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("history-\\d+"))
                    .toList()) {
                try {
                    if (Files.getLastModifiedTime(folder).toInstant().isBefore(cutoff)) cleanup(folder);
                } catch (Exception ex) {
                    log.warn("오래된 DITA 임시 폴더 상태를 확인하지 못했습니다: {}", folder, ex);
                }
            }
        } catch (Exception ex) {
            log.warn("DITA 임시 폴더 자동 정리를 수행하지 못했습니다: {}", stagingRoot, ex);
        }
    }

    private Path restoreOriginalPath(Path file, Path localRoot, Path sourceRoot) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(localRoot) ? sourceRoot.resolve(localRoot.relativize(normalized)).normalize() : file;
    }
}
