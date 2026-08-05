package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

class ArtworkRequestServiceTest {

    private final ArtworkRequestService service =
            new ArtworkRequestService();

    @TempDir
    Path tempDirectory;

    @Test
    void createRejectsBlankInputPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                "%PDF".getBytes());

        assertThatThrownBy(() -> service.create("", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("입력 경로");
    }

    @Test
    void createRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() -> service.create(tempDirectory.toString(), file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF 파일을 선택");
    }

    @Test
    void createRejectsNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.txt",
                "text/plain",
                "text".getBytes());

        assertThatThrownBy(() -> service.create(tempDirectory.toString(), file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF 파일만");
    }
}
