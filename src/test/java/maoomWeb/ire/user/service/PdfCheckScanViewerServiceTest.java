package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

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
    void createsDatedKoreanOutputFileName() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService("", "");

        String fileName = service.createOutputFileName(
                LocalDateTime.of(2026, 8, 20, 15, 30, 0));

        org.assertj.core.api.Assertions.assertThat(fileName)
                .isEqualTo("20260820_153000_인쇄데이터_검증.xlsx");
    }
}
