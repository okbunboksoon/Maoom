package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DitaValidationReportServiceTest {
    @TempDir Path tempDir;

    @Test
    void returnsReportsInLatestFileNameOrderAndExcludesOldProject() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path report = Files.createDirectory(project.resolve("report"));
        Files.writeString(report.resolve("261101_0900_report.html"), "old");
        Files.writeString(report.resolve("261102_0900_report.html"), "new");
        Path old = Files.createDirectory(tempDir.resolve("old"));
        Files.createDirectories(old.resolve("report"));
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        when(history.findSuccessfulReportSummaries()).thenReturn(Map.of());
        DitaValidationReportService service = new DitaValidationReportService(history, tempDir);

        var projects = service.findGomsReports();

        assertEquals(1, projects.size());
        assertEquals("261102_0900_report.html", projects.get(0).reports().get(0).fileName());
        assertEquals("261101_0900_report.html", projects.get(0).reports().get(1).fileName());
    }

    @Test
    void blocksPathTraversalWhenResolvingAReport() {
        DitaValidationReportService service = new DitaValidationReportService(
                mock(SchematronHistoryService.class), tempDir);

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveGomsReport("..", "report.html"));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveGomsReport("project", "../report.html"));
    }
}
