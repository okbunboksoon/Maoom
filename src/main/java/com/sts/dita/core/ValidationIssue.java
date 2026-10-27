package com.sts.dita.core;

import java.nio.file.Path;
import java.util.List;

/** Schematron 위반 한 건을 HTML 보고서에 필요한 문서 제목·위치·규칙·심각도·유형과 함께 전달한다. */
public record ValidationIssue(
        Path file,
        String title,
        List<String> parentTitles,
        int line,
        String severity,
        String category,
        String ruleId,
        String location,
        String message,
        int mapOrder,
        int level) {
    public ValidationIssue {
        parentTitles = parentTitles == null ? List.of() : List.copyOf(parentTitles);
    }
}
