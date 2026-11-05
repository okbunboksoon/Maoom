package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DitaValidationTargetServiceTest {
    @TempDir Path tempDir;

    @Test
    void excludesOldAndReportFoldersWhenFindingMaps() throws Exception {
        Files.writeString(tempDir.resolve("root.ditamap"), "<map/>");
        Files.createDirectories(tempDir.resolve("topics"));
        Files.writeString(tempDir.resolve("topics/child.ditamap"), "<map/>");
        Files.createDirectories(tempDir.resolve("old"));
        Files.writeString(tempDir.resolve("old/old.ditamap"), "<map/>");
        Files.createDirectories(tempDir.resolve("report"));
        Files.writeString(tempDir.resolve("report/report.ditamap"), "<map/>");

        DitaValidationTargetService service = new DitaValidationTargetService(null, tempDir.resolve("goms"));

        var names = service.findMaps(tempDir.toString()).stream().map(item -> item.name()).toList();

        assertEquals(2, names.size());
        assertTrue(names.contains("root.ditamap"));
        assertTrue(names.contains("child.ditamap"));
        assertFalse(names.contains("old.ditamap"));
        assertFalse(names.contains("report.ditamap"));
    }

    @Test
    void rejectsMapOutsideTheSelectedRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.writeString(tempDir.resolve("outside.ditamap"), "<map/>");
        DitaValidationTargetService service = new DitaValidationTargetService(null, tempDir.resolve("goms"));

        assertThrows(IllegalArgumentException.class, () -> service.validMap(outside.toString(), root));
    }
}
