package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;

class ReplaceDarkSymbolServiceTest {

    @Test
    void readsExistingXmlAsDatabaseRows() {
        /*
         * 최초 실행 시 DB를 채우는 기준 파일이 깨지면 관리자 화면과 배치 XML 생성이 같이 흔들린다.
         * 그래서 기존 replace_dark_symbol.xml이 DB 행으로 정상 파싱되는지 확인한다.
         */
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
