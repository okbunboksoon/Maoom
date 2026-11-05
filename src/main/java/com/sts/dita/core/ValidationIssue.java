package com.sts.dita.core;

import java.nio.file.Path;
import java.util.List;

/** Schematron 위반 한 건을 HTML 보고서에 필요한 문서 제목·규칙 ID·규칙 유형·심각도·분류와 함께 전달한다. */
public record ValidationIssue(
        Path file,
        String title,
        List<String> parentTitles,
        int line,
        String severity,
        String category,
        String ruleId,
        String ruleType,
        String location,
        String detectedContent,
        String message,
        int mapOrder,
        int level) {
    public ValidationIssue {
        parentTitles = parentTitles == null ? List.of() : List.copyOf(parentTitles);
    }
}
