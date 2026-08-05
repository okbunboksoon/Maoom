package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.ProductSpecComparisonRequest;

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

    @Test
    void mapsGDriveInputPathToGServerUncPath() {
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThat(service.toServerInputPath("G:\\project\\spec"))
                .isEqualTo("\\\\192.168.10.221\\kia_om25\\project\\spec");
        assertThat(service.toServerInputPath("g:/project/spec"))
                .isEqualTo("\\\\192.168.10.221\\kia_om25/project/spec");
        assertThat(service.toServerInputPath("G:"))
                .isEqualTo("\\\\192.168.10.221\\kia_om25");
    }

    @Test
    void formatsExtractKeysForLog() {
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThat(service.formatExtractKeys("A1"))
                .isEqualTo("A1");
        assertThat(service.formatExtractKeys(""))
                .isEqualTo("ALL");
        assertThat(service.formatExtractKeys(null))
                .isEqualTo("ALL");
    }

    @Test
    void normalizesSpecificKeysWithDifferentSpecGroups() throws Exception {
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThat(normalizeKeys(service, "a4   i3 c1 c2"))
                .isEqualTo("A4 I3 C1 C2");
    }

    @Test
    void rejectsSpecificKeysWithoutLetterNumberTokens() throws Exception {
        ProductSpecComparisonService service =
                new ProductSpecComparisonService();

        assertThatThrownBy(() -> normalizeKeys(service, "K1,K2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A4 I3 C1 C2");
        assertThatThrownBy(() -> normalizeKeys(service, "A 4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A4 I3 C1 C2");
    }

    private String normalizeKeys(
            ProductSpecComparisonService service,
            String keys) throws Exception {
        Method method = ProductSpecComparisonService.class
                .getDeclaredMethod(
                        "normalizeKeys",
                        ProductSpecComparisonRequest.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(
                    service,
                    new ProductSpecComparisonRequest(
                            null,
                            null,
                            null,
                            "SPECIFIC",
                            keys));
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
