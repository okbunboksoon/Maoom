package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BerAsisTobeSentenceImportServiceTest {

    @Test
    void rejectsNonExcelXmlFileBeforeBatchExecution() {
        BerAsisTobeSentenceImportService service =
                new BerAsisTobeSentenceImportService(
                        null,
                        null,
                        null,
                        null,
                        null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.importSentenceExcel(
                "EU",
                file,
                "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excel XML");
    }

    @Test
    void rejectsUnsupportedRegionBeforeBatchExecution() {
        BerAsisTobeSentenceImportService service =
                new BerAsisTobeSentenceImportService(
                        null,
                        null,
                        null,
                        null,
                        null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "excel.xml",
                "application/xml",
                "<Workbook/>".getBytes());

        assertThatThrownBy(() -> service.importSentenceExcel(
                "KR",
                file,
                "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EU, EU_RG 또는 US");
    }
}
