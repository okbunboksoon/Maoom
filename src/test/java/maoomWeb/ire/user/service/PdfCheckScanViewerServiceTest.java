package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfCheckScanViewerServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void runRejectsBlankConfiguredPath() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService("", "");

        assertThatThrownBy(() -> service.run(
                "W:\\2026\\_Printing_KHQ",
                "V:\\Tools\\test\\result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("경로가 설정되지 않았습니다");
    }

    @Test
    void runRejectsMissingFile() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        tempDirectory.resolve("missing.exe").toString(),
                        tempDirectory.resolve("folder_lang_match.xlsx").toString());

        assertThatThrownBy(() -> service.run(
                "W:\\2026\\_Printing_KHQ",
                "V:\\Tools\\test\\result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    void runRejectsNonExeFile() throws IOException {
        Path textFile = tempDirectory.resolve("viewer.txt");
        Files.writeString(textFile, "not an exe");
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        textFile.toString(),
                        tempDirectory.resolve("folder_lang_match.xlsx").toString());

        assertThatThrownBy(() -> service.run(
                "W:\\2026\\_Printing_KHQ",
                "V:\\Tools\\test\\result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".exe");
    }

    @Test
    void runCommandUsesQuietOption() throws Exception {
        Path exeFile = tempDirectory.resolve("PdfPrintCheckCliNew-0.2.1.exe");
        Path matchTableFile = tempDirectory.resolve("folder_lang_match.xlsx");
        Path targetDirectory = tempDirectory.resolve("_Printing_KHQ");
        Path outputDirectory = tempDirectory.resolve("result");
        Files.writeString(exeFile, "fake exe");
        Files.writeString(matchTableFile, "fake xlsx");
        Files.createDirectory(targetDirectory);

        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        exeFile.toString(),
                        matchTableFile.toString());

        Method prepareRun = PdfCheckScanViewerService.class.getDeclaredMethod(
                "prepareRun",
                String.class,
                String.class);
        prepareRun.setAccessible(true);
        Object context = prepareRun.invoke(
                service,
                targetDirectory.toString(),
                outputDirectory.toString());
        Method commandMethod = context.getClass().getDeclaredMethod("command");
        commandMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) commandMethod.invoke(context);

        org.assertj.core.api.Assertions.assertThat(command)
                .containsExactly(
                        exeFile.toAbsolutePath().normalize().toString(),
                        targetDirectory.toAbsolutePath().normalize().toString(),
                        "--xlsx",
                        matchTableFile.toAbsolutePath().normalize().toString(),
                        "--out",
                        command.get(5),
                        "--quiet");
    }

    @Test
    void createsDatedKoreanOutputFileName() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService("", "");

        String fileName = service.createOutputFileName(
                LocalDateTime.of(2026, 8, 20, 15, 30, 0));

        org.assertj.core.api.Assertions.assertThat(fileName)
                .isEqualTo("20260820_153000_인쇄데이터_검증.xlsx");
    }
}
