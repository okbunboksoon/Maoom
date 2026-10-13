package com.sts.dita.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** 검사 시간, 처리 파일 수 및 모든 위반 항목을 보고서 생성 단계로 전달한다. */
public record ValidationResult(
        Path mapFile,
        Instant startedAt,
        Instant finishedAt,
        int validatedFileCount,
        List<ValidationIssue> issues) {
}
