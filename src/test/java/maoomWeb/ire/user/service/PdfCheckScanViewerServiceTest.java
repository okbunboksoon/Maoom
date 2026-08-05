package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfCheckScanViewerServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void launchRejectsBlankConfiguredPath() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService("");

        assertThatThrownBy(service::launch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("경로가 설정되지 않았습니다");
    }

    @Test
    void launchRejectsMissingFile() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        tempDirectory.resolve("missing.exe").toString());

        assertThatThrownBy(service::launch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    void launchRejectsNonExeFile() throws IOException {
        Path textFile = tempDirectory.resolve("viewer.txt");
        Files.writeString(textFile, "not an exe");
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        textFile.toString());

        assertThatThrownBy(service::launch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".exe");
    }
}
