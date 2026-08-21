package maoomWeb.ire.user.dto;

public record PdfCheckScanViewerRunRequest(
        String targetDirectory,
        String outputDirectory) {
}
