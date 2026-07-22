package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class QsgApplyResourceTest {

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
}
