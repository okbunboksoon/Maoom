package maoomWeb.ire.user.dto;

import java.util.List;

public record RevisionRunRequest(
        String inputPath,
        String outputPath,
        List<String> optionIds) {
}
