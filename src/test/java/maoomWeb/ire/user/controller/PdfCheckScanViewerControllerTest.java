package maoomWeb.ire.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PdfCheckScanViewerControllerTest {

    @Test
    void launchApiDoesNotStartServerSideProcess() {
        PdfCheckScanViewerController controller =
                new PdfCheckScanViewerController();

        ResponseEntity<String> response = controller.launch();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).contains("사용자 PC");
    }
}
