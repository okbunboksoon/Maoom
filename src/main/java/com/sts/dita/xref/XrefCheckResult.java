package com.sts.dita.xref;

import java.nio.file.Path;

public record XrefCheckResult(
        Path sourceFile, int line, String href,
        Path targetFile, String targetId,
        String formatStatus, String fileStatus, String mapStatus, String idStatus,
        String message) {
    public boolean hasError() {
        return !"정상".equals(formatStatus) || "파일 없음".equals(fileStatus)
                || "미연결".equals(mapStatus) || "ID 없음".equals(idStatus);
    }
}
