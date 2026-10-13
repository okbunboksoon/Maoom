package com.sts.dita.core;

import java.nio.file.Path;

/** Schematron 위반 한 건을 HTML 보고서에 필요한 위치·규칙·심각도와 함께 전달한다. */
public record ValidationIssue(
        Path file,
        int line,
        String severity,
        String ruleId,
        String location,
        String message,
        int mapOrder) {
}
