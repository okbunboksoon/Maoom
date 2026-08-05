package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;

class PdfCheckScanViewerServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void launchRejectsMissingBundledExeWhenNoPathConfigured() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService("", new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                });

        assertThatThrownBy(service::launch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로젝트에 포함된");
    }

    @Test
    void launchRejectsMissingFile() {
        PdfCheckScanViewerService service =
                new PdfCheckScanViewerService(
                        tempDirectory.resolve("missing.exe").toString(),
                        new ByteArrayResource(new byte[0]));

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
                        textFile.toString(),
                        new ByteArrayResource(new byte[0]));

        assertThatThrownBy(service::launch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".exe");
    }
}
