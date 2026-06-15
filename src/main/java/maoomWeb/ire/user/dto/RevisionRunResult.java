package maoomWeb.ire.user.dto;

import java.util.List;

public record RevisionRunResult(
        boolean success,
        String outputPath,
        List<String> completedOptions,
        List<String> logs) {
}
