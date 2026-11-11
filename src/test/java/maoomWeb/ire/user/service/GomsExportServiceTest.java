package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GomsExportServiceTest {
    @TempDir Path tempDir;

    @Test
    void storesGeneratedDitaInYearProjectLanguageAndArchivesCurrentFiles() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("EXPORT_260626"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Path properties = classes.resolve("common.properties");
        Files.writeString(properties, "# 설정 유지\r\nEXPORT.TARGET=/Project/2025/OLD\r\n"
                + "EXPORT.TARGET_SUB=Korean\r\nDOWNLOAD.FOLDER=V:/ValidationCheck\r\n", StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\r\nexit /b 0\r\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("ValidationCheck"));
        Path current = Files.createDirectories(outputRoot.resolve(
                "2026/NQ5_HEV_PE_27MY/English_LHD/dita"));
        Files.writeString(current.resolve("current.dita"), "old", StandardCharsets.UTF_8);
        Files.createDirectories(current.resolve("old/previous"));
        Files.writeString(current.resolve("old/previous/kept.dita"), "kept", StandardCharsets.UTF_8);
        AtomicBoolean executed = new AtomicBoolean();
        GomsExportService service = new GomsExportService(exportRoot, outputRoot, Duration.ofMinutes(5),
                (batch, directory, timeout) -> {
                    executed.set(true);
                    String runningProperties = Files.readString(properties, StandardCharsets.UTF_8);
                    assertTrue(runningProperties.contains("EXPORT.TARGET=/Project/2026/NQ5_HEV_PE_27MY"));
                    assertTrue(runningProperties.contains("EXPORT.TARGET_SUB=English_LHD"));
                    String download = runningProperties.lines()
                            .filter(line -> line.startsWith("DOWNLOAD.FOLDER="))
                            .findFirst().orElseThrow().substring("DOWNLOAD.FOLDER=".length());
                    Path dita = Files.createDirectories(Path.of(download)
                            .resolve("NQ5_HEV_PE_27MY_English_LHD/dita"));
                    Files.writeString(dita.resolve("root.ditamap"), "<map/>", StandardCharsets.UTF_8);
                    Files.writeString(dita.resolve("new.dita"), "new", StandardCharsets.UTF_8);
                    return 0;
                });

        var result = service.export("2026/NQ5_HEV_PE_27MY/English_LHD");

        String updated = Files.readString(properties, StandardCharsets.UTF_8);
        assertTrue(executed.get());
        Path expectedDita = outputRoot.resolve("2026/NQ5_HEV_PE_27MY/English_LHD/dita");
        assertEquals(expectedDita.toString(), result.inputPath());
        assertTrue(Files.isRegularFile(expectedDita.resolve("root.ditamap")));
        assertTrue(Files.isRegularFile(expectedDita.resolve("new.dita")));
        assertTrue(Files.isRegularFile(expectedDita.resolve("old/previous/kept.dita")));
        try (var paths = Files.walk(expectedDita.resolve("old"))) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().equals("current.dita")));
        }
        assertTrue(Files.isDirectory(expectedDita.getParent().resolve("report")));
        assertTrue(updated.contains("# 설정 유지\r\n"));
        assertTrue(updated.contains("EXPORT.TARGET=/Project/2025/OLD\r\n"));
        assertTrue(updated.contains("EXPORT.TARGET_SUB=Korean\r\n"));
        assertTrue(updated.contains("DOWNLOAD.FOLDER=V:/ValidationCheck\r\n"));
    }

    @Test
    void rejectsUnsafeInputBeforeChangingPropertiesOrRunningBatch() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("export"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Path properties = classes.resolve("common.properties");
        String original = "EXPORT.TARGET=/Project/2025/OLD\nEXPORT.TARGET_SUB=Korean\n";
        Files.writeString(properties, original, StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\n", StandardCharsets.UTF_8);
        AtomicBoolean executed = new AtomicBoolean();
        GomsExportService service = new GomsExportService(exportRoot, tempDir.resolve("out"),
                Duration.ofMinutes(5), (batch, directory, timeout) -> {
                    executed.set(true);
                    return 0;
                });

        assertThrows(IllegalArgumentException.class,
                () -> service.export("2026/../NQ5_HEV_PE_27MY/English_LHD"));
        assertEquals(original, Files.readString(properties, StandardCharsets.UTF_8));
        assertTrue(!executed.get());
    }

    @Test
    void reportsBatchFailureWithoutAcceptingAnOldOutputFolder() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("export-failure"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Files.writeString(classes.resolve("common.properties"),
                "EXPORT.TARGET=/Project/2025/OLD\nEXPORT.TARGET_SUB=Korean\nDOWNLOAD.FOLDER=V:/ValidationCheck\n",
                StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("failure-output"));
        GomsExportService service = new GomsExportService(exportRoot, outputRoot,
                Duration.ofMinutes(5), (batch, directory, timeout) -> 21);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.export("2026/NQ5_HEV_PE_27MY/English_LHD"));

        assertTrue(error.getMessage().contains("종료 코드: 21"));
    }

    @Test
    void keepsCurrentDitaWhenGeneratedOutputHasNoDitamap() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("missing-map-export"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Path properties = classes.resolve("common.properties");
        Files.writeString(properties, "EXPORT.TARGET=/Project/2025/OLD\n"
                + "EXPORT.TARGET_SUB=Korean\nDOWNLOAD.FOLDER=V:/ValidationCheck\n",
                StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\r\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("missing-map-output"));
        Path current = Files.createDirectories(outputRoot.resolve("2026/PROJECT/Korean/dita"));
        Files.writeString(current.resolve("current.ditamap"), "<map/>");
        GomsExportService service = new GomsExportService(exportRoot, outputRoot,
                Duration.ofMinutes(1), (batch, directory, timeout) -> {
                    String text = Files.readString(properties, StandardCharsets.UTF_8);
                    String download = text.lines().filter(line -> line.startsWith("DOWNLOAD.FOLDER="))
                            .findFirst().orElseThrow().substring("DOWNLOAD.FOLDER=".length());
                    Path dita = Files.createDirectories(Path.of(download).resolve("PROJECT_Korean/dita"));
                    Files.writeString(dita.resolve("topic.dita"), "<concept/>");
                    return 0;
                });

        assertThrows(IllegalStateException.class,
                () -> service.export("2026/PROJECT/Korean"));
        assertTrue(Files.isRegularFile(current.resolve("current.ditamap")));
        assertTrue(!Files.exists(current.resolve("old")));
    }

    @Test
    void queuesConcurrentExportsInsteadOfRunningThemTogether() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("queued-export"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Path properties = classes.resolve("common.properties");
        Files.writeString(properties, "EXPORT.TARGET=/Project/2025/OLD\n"
                + "EXPORT.TARGET_SUB=Korean\nDOWNLOAD.FOLDER=V:/ValidationCheck\n",
                StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\r\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("queued-output"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean overlap = new AtomicBoolean();
        AtomicBoolean running = new AtomicBoolean();
        GomsExportService service = new GomsExportService(exportRoot, outputRoot,
                Duration.ofMinutes(1), (batch, directory, timeout) -> {
                    if (!running.compareAndSet(false, true)) overlap.set(true);
                    firstEntered.countDown();
                    releaseFirst.await(5, TimeUnit.SECONDS);
                    String text = Files.readString(properties, StandardCharsets.UTF_8);
                    String download = text.lines().filter(line -> line.startsWith("DOWNLOAD.FOLDER="))
                            .findFirst().orElseThrow().substring("DOWNLOAD.FOLDER=".length());
                    String project = text.contains("/FIRST") ? "FIRST" : "SECOND";
                    Path dita = Files.createDirectories(Path.of(download).resolve(project + "_Korean/dita"));
                    Files.writeString(dita.resolve("root.ditamap"), "<map/>");
                    running.set(false);
                    return 0;
                });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.export("2026/FIRST/Korean"));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.export("2026/SECOND/Korean"));
            Thread.sleep(150);
            assertTrue(!second.isDone());
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertTrue(!overlap.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
