package com.sts.dita.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ValidationEngine {
    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);
    private final MapParser mapParser = new MapParser();
    private final long fileTimeoutSeconds;

    public ValidationEngine() { this(120); }
    public ValidationEngine(long fileTimeoutSeconds) { this.fileTimeoutSeconds = Math.max(1, fileTimeoutSeconds); }

    public MapModel loadMap(Path mapFile) throws Exception {
        return mapParser.parse(mapFile);
    }

    public ValidationResult validate(ValidationRequest request) throws Exception {
        return validate(request, ValidationProgressListener.NONE);
    }

    public ValidationResult validate(ValidationRequest request, ValidationProgressListener progress) throws Exception {
        return validate(request, progress, ValidationCancellation.NONE);
    }

    public ValidationResult validate(ValidationRequest request, ValidationProgressListener progress,
                                     ValidationCancellation cancellation) throws Exception {
        // 선택 범위를 실제 파일 목록으로 확정한 뒤 동일 파일은 LinkedHashMap으로 한 번만 검사한다.
        Instant start = Instant.now();
        cancellation.check();
        MapModel map = loadMap(request.mapFile());
        LinkedHashMap<Path, TargetInfo> targets = collectTargets(map, request.scope(), request.selectedNodeIds());
        if (request.validateMap()) targets.putIfAbsent(map.mapFile(), new TargetInfo(-1, map.title(), List.of(), 0));
        SchematronValidator validator = new SchematronValidator(request.schematronFile(), request.projectRoot());
        List<ValidationIssue> issues = new ArrayList<>();
        Map<Path, Long> fileNanos = new LinkedHashMap<>();
        int completed = 0;
        // 화면이 전체 건수를 먼저 표시할 수 있도록 파일 처리 전에 0/전체 상태를 보낸다.
        progress.onProgress(completed, targets.size(), null);
        for (var entry : targets.entrySet()) {
            cancellation.check();
            Path target = entry.getKey();
            // 느린 네트워크 파일을 식별할 수 있도록 처리 직전 현재 파일명을 갱신한다.
            progress.onProgress(completed, targets.size(), target);
            if (!Files.isRegularFile(target)) {
                issues.add(new ValidationIssue(target, entry.getValue().title(), entry.getValue().reportParentTitles(), -1, "error", "system",
                        "missing-reference", "", "참조 파일이 존재하지 않습니다.", entry.getValue().order(),
                        entry.getValue().level()));
            } else {
                long fileStarted = System.nanoTime();
                ExecutorService fileExecutor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "dita-file-validation");
                    thread.setDaemon(true);
                    return thread;
                });
                Future<List<ValidationIssue>> future = fileExecutor.submit(() -> validator.validate(target,
                        entry.getValue().title(), entry.getValue().reportParentTitles(), entry.getValue().order(),
                        entry.getValue().level(), cancellation));
                try {
                    issues.addAll(waitForFile(future, cancellation));
                } catch (TimeoutException ex) {
                    future.cancel(true);
                    issues.add(new ValidationIssue(target, entry.getValue().title(), entry.getValue().reportParentTitles(),
                            -1, "error", "system", "file-timeout", "",
                            "파일 처리 제한 시간 " + fileTimeoutSeconds + "초를 초과했습니다."
                                    + (validator.currentRuleId().isBlank() ? "" : " 처리 중 규칙: " + validator.currentRuleId()),
                            entry.getValue().order(), entry.getValue().level()));
                } finally {
                    fileExecutor.shutdownNow();
                    // Windows에서는 검증 스레드가 XML 입력 스트림을 닫기 전에 다음 단계로
                    // 넘어가면 임시 파일이 잠긴 채 남을 수 있으므로 종료를 짧게 기다린다.
                    if (!fileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("DITA 파일 검증 스레드가 종료 대기 시간을 초과했습니다: {}", target);
                    }
                    fileNanos.put(target, System.nanoTime() - fileStarted);
                }
            }
            progress.onProgress(++completed, targets.size(), target);
        }
        cancellation.check();
        logDiagnostics(fileNanos, validator);
        return new ValidationResult(map.mapFile(), start, Instant.now(), targets.size(), List.copyOf(issues));
    }

    private List<ValidationIssue> waitForFile(Future<List<ValidationIssue>> future,
            ValidationCancellation cancellation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(fileTimeoutSeconds);
        while (true) {
            cancellation.check();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException();
            try {
                return future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250)), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ex) {
                if (System.nanoTime() >= deadline) throw ex;
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw ex;
            }
        }
    }

    private void logDiagnostics(Map<Path, Long> fileNanos, SchematronValidator validator) {
        var slowFiles = fileNanos.entrySet().stream()
                .sorted(Map.Entry.<Path, Long>comparingByValue().reversed()).limit(10)
                .map(entry -> entry.getKey().getFileName() + "=" + TimeUnit.NANOSECONDS.toMillis(entry.getValue()) + "ms")
                .toList();
        var slowRules = validator.ruleNanos().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .map(entry -> entry.getKey() + "=" + TimeUnit.NANOSECONDS.toMillis(entry.getValue()) + "ms")
                .toList();
        log.info("Schematron 진단: slowFiles={}, slowRules={}, externalDocuments={}, parseFailures={}",
                slowFiles, slowRules, validator.externalDocumentCount(), validator.parseFailureCount());
    }

    private static LinkedHashMap<Path, TargetInfo> collectTargets(MapModel map, ValidationScope scope, Set<String> selected) {
        LinkedHashMap<Path, TargetInfo> result = new LinkedHashMap<>();
        for (MapNode root : map.roots()) collect(root, scope, selected, false, List.of(), 1, result);
        return result;
    }

    private static void collect(MapNode node, ValidationScope scope, Set<String> selected,
                                boolean selectedAncestor, List<String> parentTitles,
                                int level,
                                LinkedHashMap<Path, TargetInfo> result) {
        // 자손 검사에서는 선택된 조상의 상태를 아래 단계까지 전달한다.
        boolean isSelected = selected.contains(node.id());
        boolean include = scope == ValidationScope.ALL
                || scope == ValidationScope.SELECTED_ONLY && isSelected
                || scope == ValidationScope.SELECTED_WITH_DESCENDANTS && (isSelected || selectedAncestor);
        Path target = node.resolvedTarget();
        if (include && target != null && isDita(target)) {
            TargetInfo existing = result.get(target);
            if (existing == null) result.put(target, new TargetInfo(node.order(), node.title(), parentTitles, level));
            else existing.addReference();
        }
        boolean descendantMode = selectedAncestor || isSelected;
        List<String> childParents = new ArrayList<>(parentTitles);
        if (node.title() != null && !node.title().isBlank()) childParents.add(node.title());
        List<String> immutableChildParents = List.copyOf(childParents);
        for (MapNode child : node.children()) {
            collect(child, scope, selected, descendantMode, immutableChildParents, level + 1, result);
        }
    }

    private static boolean isDita(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dita") || name.endsWith(".ditamap") || name.endsWith(".xml");
    }

    private static final class TargetInfo {
        private final int order;
        private final String title;
        private final List<String> parentTitles;
        private final int level;
        private int additionalReferences;

        private TargetInfo(int order, String title, List<String> parentTitles, int level) {
            this.order = order;
            this.title = title;
            this.parentTitles = parentTitles;
            this.level = level;
        }
        private int order() { return order; }
        private String title() { return title; }
        private int level() { return level; }
        private void addReference() { additionalReferences++; }
        private List<String> reportParentTitles() {
            if (additionalReferences == 0) return parentTitles;
            List<String> display = new ArrayList<>(parentTitles);
            display.add("외 " + additionalReferences + "개 경로");
            return List.copyOf(display);
        }
    }
}
