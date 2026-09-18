package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.AutomaticNoticeResult;

class AutomaticNoticeSampleRunTest {

    private static final Path SAMPLE_PDF = Path.of(
            "C:/Users/adobe/Desktop/KIA-CV1a-EV-en-US-2027-OM_Full-PDF-260730-0.1_web_low.pdf");
    private static final Path KO_SAMPLE_PDF = Path.of(
            "C:/Users/adobe/Desktop/협조문 자동 완성/"
                    + "KIA-CV-PE-EV_KO-2027-OM_Full-PDF-260722-1.1_print.pdf");
    private static final Path US_SAMPLE_PDF = Path.of(
            "C:/Users/adobe/Desktop/협조문 자동 완성/"
                    + "KIA-CV1-EV-en-US-2027-OM_Full-PDF-260703-0.1_web.pdf");

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

        assertThat(result.market()).isEqualTo("EG");
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

    @Test
    void createWritesKoSafetyAndFigureContentsAsSeparateChapters()
            throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(KO_SAMPLE_PDF));

        AutomaticNoticeService service = new AutomaticNoticeService();
        MockMultipartFile file;
        try(InputStream input = Files.newInputStream(KO_SAMPLE_PDF)){
            file = new MockMultipartFile(
                    "file",
                    KO_SAMPLE_PDF.getFileName().toString(),
                    "application/pdf",
                    input);
        }

        AutomaticNoticeResult result =
                service.create(outputDirectory.toString(), file);

        assertThat(result.market()).isEqualTo("KO");
        try(Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(Path.of(result.resultPath())))){
            var sheet = workbook.getSheet("검토표");
            DataFormatter formatter = new DataFormatter();

            assertThat(findRow(sheet, formatter,
                    "1장", "12~42", "안전 주의 사항"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findRow(sheet, formatter,
                    "2장", "44~54", "그림 목차"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findRowStartingWith(
                    sheet, formatter, "3장", "차량 제원"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findRow(
                    sheet, formatter, "7장", "243~296", "EV가이드"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findReviewItem(
                    sheet, formatter, "전기 자동차 개요"))
                    .isEqualTo(-1);
            assertThat(findRowStartingWith(
                    sheet, formatter, "12장", "모터룸의 명칭"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findReviewItem(sheet, formatter, "그림 목차"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findReviewItem(sheet, formatter, "안전 주의 사항"))
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void createMergesSpecialUsChaptersForSamplePdf() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(US_SAMPLE_PDF));

        AutomaticNoticeService service = new AutomaticNoticeService();
        MockMultipartFile file;
        try(InputStream input = Files.newInputStream(US_SAMPLE_PDF)){
            file = new MockMultipartFile(
                    "file",
                    US_SAMPLE_PDF.getFileName().toString(),
                    "application/pdf",
                    input);
        }

        AutomaticNoticeResult result =
                service.create(outputDirectory.toString(), file);

        assertThat(result.market()).isEqualTo("EG");
        try(Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(Path.of(result.resultPath())))){
            var sheet = workbook.getSheet("검토표");
            DataFormatter formatter = new DataFormatter();

            assertThat(findRow(sheet, formatter,
                    "1 Introduction", "12~14", "Introduction"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findRow(sheet, formatter,
                    "2 Overview", "16~22", "Overview"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findRow(
                    sheet,
                    formatter,
                    "7 Electric vehicle guide",
                    "211~272",
                    "Electric vehicle guide"))
                    .isGreaterThanOrEqualTo(0);
            assertThat(findReviewItem(
                    sheet, formatter, "Vehicle modifications"))
                    .isEqualTo(-1);
            assertThat(findReviewItem(
                    sheet, formatter, "Exterior overview"))
                    .isEqualTo(-1);
            assertThat(findReviewItem(
                    sheet, formatter, "Electric vehicle overview"))
                    .isEqualTo(-1);
        }
    }

    private static int findRow(
            org.apache.poi.ss.usermodel.Sheet sheet,
            DataFormatter formatter,
            String group,
            String pages,
            String reviewItem) {
        for(int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++){
            var row = sheet.getRow(rowIndex);
            if(row == null) continue;
            if(group.equals(formatter.formatCellValue(row.getCell(0)))
                    && pages.equals(formatter.formatCellValue(row.getCell(1)))
                    && reviewItem.equals(
                            formatter.formatCellValue(row.getCell(2)))){
                return rowIndex;
            }
        }
        return -1;
    }

    private static int findRowStartingWith(
            org.apache.poi.ss.usermodel.Sheet sheet,
            DataFormatter formatter,
            String group,
            String reviewItem) {
        for(int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++){
            var row = sheet.getRow(rowIndex);
            if(row == null) continue;
            if(group.equals(formatter.formatCellValue(row.getCell(0)))
                    && reviewItem.equals(
                            formatter.formatCellValue(row.getCell(2)))){
                return rowIndex;
            }
        }
        return -1;
    }

    private static int findReviewItem(
            org.apache.poi.ss.usermodel.Sheet sheet,
            DataFormatter formatter,
            String reviewItem) {
        for(int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++){
            var row = sheet.getRow(rowIndex);
            if(row != null && reviewItem.equals(
                    formatter.formatCellValue(row.getCell(2)))){
                return rowIndex;
            }
        }
        return -1;
    }
}
