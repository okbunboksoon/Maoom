package maoomWeb.ire.user.dto;

import java.util.Map;

public record AutoCountingAnalyzeResult(
        String message,
        String fileName,
        int totalPages,
        int nonBlankPages,
        int fullPages,
        Map<String, Integer> chapters) {
}
