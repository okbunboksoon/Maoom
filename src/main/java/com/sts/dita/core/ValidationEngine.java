package com.sts.dita.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class ValidationEngine {
    private final MapParser mapParser = new MapParser();

    public MapModel loadMap(Path mapFile) throws Exception {
        return mapParser.parse(mapFile);
    }

    public ValidationResult validate(ValidationRequest request) throws Exception {
        return validate(request, ValidationProgressListener.NONE);
    }

    public ValidationResult validate(ValidationRequest request, ValidationProgressListener progress) throws Exception {
        // 선택 범위를 실제 파일 목록으로 확정한 뒤 동일 파일은 LinkedHashMap으로 한 번만 검사한다.
        Instant start = Instant.now();
        MapModel map = loadMap(request.mapFile());
        LinkedHashMap<Path, Integer> targets = collectTargets(map, request.scope(), request.selectedNodeIds());
        if (request.validateMap()) targets.putIfAbsent(map.mapFile(), -1);
        SchematronValidator validator = new SchematronValidator(request.schematronFile());
        List<ValidationIssue> issues = new ArrayList<>();
        int completed = 0;
        // 화면이 전체 건수를 먼저 표시할 수 있도록 파일 처리 전에 0/전체 상태를 보낸다.
        progress.onProgress(completed, targets.size(), null);
        for (var entry : targets.entrySet()) {
            Path target = entry.getKey();
            // 느린 네트워크 파일을 식별할 수 있도록 처리 직전 현재 파일명을 갱신한다.
            progress.onProgress(completed, targets.size(), target);
            if (!Files.isRegularFile(target)) {
                issues.add(new ValidationIssue(target, -1, "error", "missing-reference", "",
                        "참조 파일이 존재하지 않습니다.", entry.getValue()));
            } else {
                issues.addAll(validator.validate(target, entry.getValue()));
            }
            progress.onProgress(++completed, targets.size(), target);
        }
        return new ValidationResult(map.mapFile(), start, Instant.now(), targets.size(), List.copyOf(issues));
    }

    private static LinkedHashMap<Path, Integer> collectTargets(MapModel map, ValidationScope scope, Set<String> selected) {
        LinkedHashMap<Path, Integer> result = new LinkedHashMap<>();
        for (MapNode root : map.roots()) collect(root, scope, selected, false, result);
        return result;
    }

    private static void collect(MapNode node, ValidationScope scope, Set<String> selected,
                                boolean selectedAncestor, LinkedHashMap<Path, Integer> result) {
        // 자손 검사에서는 선택된 조상의 상태를 아래 단계까지 전달한다.
        boolean isSelected = selected.contains(node.id());
        boolean include = scope == ValidationScope.ALL
                || scope == ValidationScope.SELECTED_ONLY && isSelected
                || scope == ValidationScope.SELECTED_WITH_DESCENDANTS && (isSelected || selectedAncestor);
        Path target = node.resolvedTarget();
        if (include && target != null && isDita(target)) result.putIfAbsent(target, node.order());
        boolean descendantMode = selectedAncestor || isSelected;
        for (MapNode child : node.children()) collect(child, scope, selected, descendantMode, result);
    }

    private static boolean isDita(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dita") || name.endsWith(".ditamap") || name.endsWith(".xml");
    }
}
