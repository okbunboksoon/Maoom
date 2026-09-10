package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.AutomaticNoticeResult;

class AutomaticNoticeSampleRunTest {

    private static final Path SAMPLE_PDF = Path.of(
            "C:/Users/adobe/Desktop/KIA-CV1a-EV-en-US-2027-OM_Full-PDF-260730-0.1_web_low.pdf");

    @TempDir
    Path outputDirectory;

    @Test
    void createWritesWorkbookAndUsedRulesForSamplePdf() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE_PDF));

        AutomaticNoticeService service = new AutomaticNoticeService();
        MockMultipartFile file;
        try(InputStream input = Files.newInputStream(SAMPLE_PDF)){
            file = new MockMultipartFile(
                    "file",
                    SAMPLE_PDF.getFileName().toString(),
                    "application/pdf",
                    input);
        }

        AutomaticNoticeResult result =
                service.create(outputDirectory.toString(), file);

        Path resultDirectory = Path.of(result.resultDirectory());
        Path resultWorkbook = Path.of(result.resultPath());
        Path rulesWorkbook = Path.of(result.rulesPath());

        assertThat(result.market()).isEqualTo("US");
        assertThat(resultDirectory.getFileName().toString())
                .isEqualTo(
                        "KIA-CV1a-EV-en-US-2027-OM_Full-PDF-260730-0.1_web_low_협조문자동완성");
        assertThat(resultWorkbook).isRegularFile();
        assertThat(rulesWorkbook).isRegularFile();
        assertThat(resultDirectory.resolve("run_info.json")).isRegularFile();

        try(Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(resultWorkbook))){
            var sheet = workbook.getSheet("검토표");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("그룹");
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue())
                    .isEqualTo("Foreword");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue())
                    .contains("법규관련 확인");
            assertThat(sheet.getRow(4).getCell(4).getStringCellValue())
                    .contains("법규인증1팀");
        }
    }
}
