package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DitaValidationStagingServiceTest {
    @TempDir Path tempDir;

    @Test
    void copiesProjectWithoutOldAndReportThenCleansIt() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("root.ditamap"), "<map/>");
        Files.createDirectories(source.resolve("topics"));
        Files.writeString(source.resolve("topics/topic.dita"), "<topic/>");
        Files.createDirectories(source.resolve("old"));
        Files.writeString(source.resolve("old/old.dita"), "<topic/>");
        Files.createDirectories(source.resolve("report"));
        Files.writeString(source.resolve("report/old.html"), "old");
        Path stagingRoot = Files.createDirectory(tempDir.resolve("staging"));
        DitaValidationProgressService progress = new DitaValidationProgressService();
        DitaValidationStagingService service = new DitaValidationStagingService(progress, stagingRoot);
        Path target = service.folder(10);

        service.stage(source, target, "copy-token", () -> { });

        assertTrue(Files.isRegularFile(target.resolve("root.ditamap")));
        assertTrue(Files.isRegularFile(target.resolve("topics/topic.dita")));
        assertFalse(Files.exists(target.resolve("old")));
        assertFalse(Files.exists(target.resolve("report")));
        assertEquals("백그라운드 작업을 준비하고 있습니다.", progress.get("copy-token").message());

        service.cleanup(target);
        assertFalse(Files.exists(target));
    }

    @Test
    void stopsCopyWhenCancellationCheckFails() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("root.ditamap"), "<map/>");
        Path stagingRoot = Files.createDirectory(tempDir.resolve("staging"));
        DitaValidationStagingService service = new DitaValidationStagingService(
                new DitaValidationProgressService(), stagingRoot);

        assertThrows(IllegalStateException.class,
                () -> service.stage(source, service.folder(11), "token",
                        () -> { throw new IllegalStateException("cancel"); }));
    }

    @Test
    void removesOnlyExpiredHistoryFolders() throws Exception {
        Path stagingRoot = Files.createDirectory(tempDir.resolve("staging"));
        Path expired = Files.createDirectory(stagingRoot.resolve("history-1"));
        Path current = Files.createDirectory(stagingRoot.resolve("history-2"));
        Path unrelated = Files.createDirectory(stagingRoot.resolve("manual"));
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(Duration.ofHours(3))));
        DitaValidationStagingService service = new DitaValidationStagingService(
                new DitaValidationProgressService(), stagingRoot, Duration.ofHours(1));

        service.cleanupExpiredFolders();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(current));
        assertTrue(Files.exists(unrelated));
    }
}
