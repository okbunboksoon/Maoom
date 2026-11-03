package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GomsExportServiceTest {
    @TempDir Path tempDir;

    @Test
    void updatesOnlyExportTargetsRunsBatchAndReturnsGeneratedFolder() throws Exception {
        Path exportRoot = Files.createDirectories(tempDir.resolve("EXPORT_260626"));
        Path classes = Files.createDirectories(exportRoot.resolve("classes"));
        Path properties = classes.resolve("common.properties");
        Files.writeString(properties, "# 설정 유지\r\nEXPORT.TARGET=/Project/2025/OLD\r\n"
                + "EXPORT.TARGET_SUB=Korean\r\nDOWNLOAD.FOLDER=V:/ValidationCheck\r\n", StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\r\nexit /b 0\r\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("ValidationCheck"));
        AtomicBoolean executed = new AtomicBoolean();
        GomsExportService service = new GomsExportService(exportRoot, outputRoot, Duration.ofMinutes(5),
                (batch, directory, timeout) -> {
                    executed.set(true);
                    Path dita = Files.createDirectories(outputRoot.resolve("NQ5_HEV_PE_27MY_English_LHD/dita"));
                    Files.writeString(dita.resolve("root.ditamap"), "<map/>", StandardCharsets.UTF_8);
                    return 0;
                });

        var result = service.export("2026/NQ5_HEV_PE_27MY/English_LHD");

        String updated = Files.readString(properties, StandardCharsets.UTF_8);
        assertTrue(executed.get());
        assertEquals(outputRoot.resolve("NQ5_HEV_PE_27MY_English_LHD").toString(), result.inputPath());
        assertTrue(updated.contains("# 설정 유지\r\n"));
        assertTrue(updated.contains("EXPORT.TARGET=/Project/2026/NQ5_HEV_PE_27MY\r\n"));
        assertTrue(updated.contains("EXPORT.TARGET_SUB=English_LHD\r\n"));
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
                "EXPORT.TARGET=/Project/2025/OLD\nEXPORT.TARGET_SUB=Korean\n", StandardCharsets.UTF_8);
        Files.writeString(exportRoot.resolve("run.bat"), "@echo off\n", StandardCharsets.UTF_8);
        Path outputRoot = Files.createDirectories(tempDir.resolve("failure-output"));
        GomsExportService service = new GomsExportService(exportRoot, outputRoot,
                Duration.ofMinutes(5), (batch, directory, timeout) -> 21);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.export("2026/NQ5_HEV_PE_27MY/English_LHD"));

        assertTrue(error.getMessage().contains("종료 코드: 21"));
    }
}
