package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;

class ReplaceDarkSymbolServiceTest {

    @Test
    void readsExistingXmlAsDatabaseRows() {
        ReplaceDarkSymbolService service =
                new ReplaceDarkSymbolService(null, null);

        assertThat(service.readItems("xsl/replace_dark_symbol.xml"))
                .hasSizeGreaterThan(300)
                .allSatisfy(this::hasRequiredFields);
    }

    private void hasRequiredFields(ReplaceDarkSymbolItem item) {
        assertThat(item.getFromSymbol()).isNotBlank();
        assertThat(item.getToSymbol()).isNotBlank();
    }
}
