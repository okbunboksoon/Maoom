package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class QsgApplyResourceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void qsgDbApplyStylesheetReferencesExistingDictionary() throws Exception {
        ClassPathResource stylesheet =
                new ClassPathResource("xsl/0340-QSG-db-apply.xsl");
        String xsl = stylesheet.getContentAsString(StandardCharsets.UTF_8);

        assertThat(xsl)
                .contains("document('QSG_DB.xml')");
        assertThat(new ClassPathResource("xsl/QSG_DB.xml"))
                .isNotNull()
                .extracting(ClassPathResource::exists)
                .isEqualTo(true);
    }

    @Test
    void qsgBatchXslDirectoryUsesAdminQsgDbXmlAfterSharedXslCopy()
            throws Exception {

        Path xslDirectory = tempDirectory.resolve("xsl");
        QsgApplyService service = new QsgApplyService(
                new RecordingQsgDbAdminService());

        service.prepareXslDirectory(xslDirectory);

        assertThat(xslDirectory.resolve("0340-QSG-db-apply.xsl"))
                .isRegularFile();
        assertThat(Files.readString(
                xslDirectory.resolve("QSG_DB.xml"),
                StandardCharsets.UTF_8))
                .contains("admin-qsg-db-overwrite");
    }

    private static class RecordingQsgDbAdminService
            extends QsgDbAdminService {

        @Override
        public void writeQsgDbXml(Path xslDirectory) throws java.io.IOException {
            Files.createDirectories(xslDirectory);
            Files.writeString(
                    xslDirectory.resolve("QSG_DB.xml"),
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <qsg-db>
                        <entry hash="admin-qsg-db-overwrite">
                            <term lang="EN-GB">updated</term>
                        </entry>
                    </qsg-db>
                    """,
                    StandardCharsets.UTF_8);
        }
    }
}
