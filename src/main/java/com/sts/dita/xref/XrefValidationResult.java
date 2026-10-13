package com.sts.dita.xref;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record XrefValidationResult(Path mapFile, Instant finishedAt, int linkedFileCount,
                                   List<XrefCheckResult> xrefs) {
    public long errorCount() { return xrefs.stream().filter(XrefCheckResult::hasError).count(); }
}
