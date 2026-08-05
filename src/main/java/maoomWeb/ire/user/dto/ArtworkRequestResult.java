package maoomWeb.ire.user.dto;

public record ArtworkRequestResult(
        boolean success,
        String fileName,
        long fileSize,
        String resultPath,
        String message) {
}
