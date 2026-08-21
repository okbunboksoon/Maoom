package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class PdfCheckScanViewerControllerTest {

    @Test
    void handleLaunchExceptionReturnsPlainTextMessage() {
        PdfCheckScanViewerController controller =
                new PdfCheckScanViewerController(null, null, null);

        ResponseEntity<String> response = controller.handleLaunchException(
                new IllegalArgumentException("대상 폴더 경로를 입력해 주세요."));

        assertThat(response.getBody()).contains("대상 폴더");
    }
}
