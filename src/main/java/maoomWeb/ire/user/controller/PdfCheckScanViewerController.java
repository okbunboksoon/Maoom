package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.service.PdfCheckScanViewerService;

@RestController
public class PdfCheckScanViewerController {

    private final PdfCheckScanViewerService pdfCheckScanViewerService;

    public PdfCheckScanViewerController(
            PdfCheckScanViewerService pdfCheckScanViewerService) {
        this.pdfCheckScanViewerService = pdfCheckScanViewerService;
    }

    @PostMapping("/api/pdf-check-scan-viewer/launch")
    public Map<String, String> launch() {
        pdfCheckScanViewerService.launch();
        return Map.of("message", "PDF 검수 스캔 뷰어를 실행했습니다.");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleLaunchException(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
