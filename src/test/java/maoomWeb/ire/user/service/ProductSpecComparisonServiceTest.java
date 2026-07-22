package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductSpecComparisonServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesWorkbookFileNamesFromInputDirectoryOnly() throws Exception {
        Path inputDirectory = tempDirectory.resolve("input");
        Files.createDirectories(inputDirectory);
        Files.writeString(inputDirectory.resolve("before.xlsx"), "");
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        Path resolved = service.resolveInputFile(inputDirectory, "before.xlsx");

        assertThat(resolved).isEqualTo(
                inputDirectory.resolve("before.xlsx")
                        .toAbsolutePath()
                        .normalize());
    }

    @Test
    void rejectsPathsAndUnsupportedExtensions() {
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThatThrownBy(() -> service.resolveInputFile(
                tempDirectory,
                "..\\before.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xlsx 또는 xls 파일명만");
        assertThatThrownBy(() -> service.resolveInputFile(
                tempDirectory,
                "before.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xlsx 또는 xls 파일명만");
    }

    @Test
    void resolvesInputDirectoryFromPath() throws Exception {
        Path inputDirectory = tempDirectory.resolve("v-server-work");
        Files.createDirectories(inputDirectory);
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThat(service.resolveInputDirectory(inputDirectory.toString()))
                .isEqualTo(inputDirectory.toAbsolutePath().normalize());
    }
}
