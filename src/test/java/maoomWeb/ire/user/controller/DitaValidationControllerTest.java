package maoomWeb.ire.user.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import maoomWeb.ire.user.service.DitaValidationWorkflowService;

class DitaValidationControllerTest {
    @TempDir Path tempDir;

    @Test
    void servesUnlinkedGomsReportAsReadOnly() throws Exception {
        Path report = tempDir.resolve("report.html");
        Files.writeString(report, """
                <html><body data-history-id="77"><div id="stateNotice"></div>
                <script>const isLocalFile=location.protocol==='file:';</script></body></html>
                """, StandardCharsets.UTF_8);
        DitaValidationWorkflowService workflow = mock(DitaValidationWorkflowService.class);
        when(workflow.resolveGomsReport("project", "report.html")).thenReturn(report);
        DitaValidationController controller = new DitaValidationController(
                workflow, null, null, null, null, null, null);

        ResponseEntity<byte[]> response = controller.gomsReportFile("project", "report.html");
        String html = new String(response.getBody(), StandardCharsets.UTF_8);

        assertTrue(html.contains("data-history-id=\"\""));
        assertTrue(html.contains("isLocalFile=true"));
        assertTrue(html.contains("읽기 전용입니다."));
        assertFalse(html.contains("data-history-id=\"77\""));
    }
}
