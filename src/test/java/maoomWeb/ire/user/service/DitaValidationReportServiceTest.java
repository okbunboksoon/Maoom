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
    void returnsOnlyNestedReportFoldersInConfiguredOrder() throws Exception {
        Path language = Files.createDirectories(tempDir.resolve("2026/project/Korean"));
        Path report = Files.createDirectory(language.resolve("report"));
        Files.writeString(report.resolve("261101_0900_report.html"), "old");
        Files.writeString(report.resolve("261102_0900_report.html"), "new");
        Files.writeString(Files.createDirectories(language.resolve("dita/old/report"))
                .resolve("ignored.html"), "ignored");
        Files.createDirectories(tempDir.resolve("legacy_flat/report"));
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        when(history.findSuccessfulReportSummaries()).thenReturn(Map.of());
        DitaValidationReportService service = new DitaValidationReportService(history, tempDir);

        var years = service.findGomsReports();

        assertEquals(1, years.size());
        assertEquals("2026", years.get(0).year());
        assertEquals("project", years.get(0).projects().get(0).projectName());
        var korean = years.get(0).projects().get(0).languages().get(0);
        assertEquals("Korean", korean.languageName());
        assertEquals("2026/project/Korean", korean.gomsPath());
        assertEquals("261102_0900_report.html", korean.reports().get(0).fileName());
        assertEquals("261101_0900_report.html", korean.reports().get(1).fileName());
    }

    @Test
    void blocksPathTraversalWhenResolvingAReport() {
        DitaValidationReportService service = new DitaValidationReportService(
                mock(SchematronHistoryService.class), tempDir);

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveGomsReport("2026", "..", "Korean", "report.html"));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveGomsReport("2026", "project", "Korean", "../report.html"));
    }
}
