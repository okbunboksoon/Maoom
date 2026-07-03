package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColorCheckFinalWorkbookServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsOrderWorkbookFromReviewedColorCheckExcel()
            throws Exception {

        Path source = tempDirectory.resolve(
                "KIA-SP3-ICE-HEV-en-US-2027-OM_Full-PDF-"
                + "260526-1.1_ALL_low_light_컬러체크.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(output.getFileName().toString())
                .matches(
                        "\\d{6}_도안발주내역서_"
                        + "SP3_ICE-HEV_27MY_US_HTML\\.xlsx");

        try(var input = Files.newInputStream(output);
                var workbook = WorkbookFactory.create(input)){
            assertThat(workbook.getNumberOfSheets())
                    .isEqualTo(2);
            assertThat(workbook.getSheet("도안 발주서"))
                    .isNotNull();
            assertThat(workbook.getSheet("작업의뢰 내역"))
                    .isNotNull();
            assertThat(workbook.getSheet("챕터"))
                    .isNull();

            var details = workbook.getSheet("작업의뢰 내역");
            assertThat(details.getRow(4).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("OMVQ014018");
            assertThat(details.getRow(4).getCell(3)
                    .getStringCellValue())
                    .isEqualTo("요약본");
            assertThat(details.getRow(4).getCell(8)
                    .getStringCellValue())
                    .isEqualTo("SP3_ICE-HEV_27MY_US");
            assertThat(details.getRow(5).getCell(9)
                    .getStringCellValue())
                    .isEqualTo("2");
            assertThat(details.getRow(6).getCell(2)
                    .getStringCellValue())
                    .isBlank();
            for(int rowIndex = 3;
                    rowIndex <= details.getLastRowNum();
                    rowIndex++){
                for(int column = 1;
                        column <= 10;
                        column++){
                    var style = details.getRow(rowIndex)
                            .getCell(
                                    column,
                                    Row.MissingCellPolicy
                                    .CREATE_NULL_AS_BLANK)
                            .getCellStyle();
                    assertThat(style.getBorderTop())
                            .isEqualTo(
                                    org.apache.poi.ss.usermodel
                                    .BorderStyle.THIN);
                    assertThat(style.getBorderRight())
                            .isEqualTo(
                                    org.apache.poi.ss.usermodel
                                    .BorderStyle.THIN);
                    assertThat(style.getBorderBottom())
                            .isEqualTo(
                                    org.apache.poi.ss.usermodel
                                    .BorderStyle.THIN);
                    assertThat(style.getBorderLeft())
                            .isEqualTo(
                                    org.apache.poi.ss.usermodel
                                    .BorderStyle.THIN);
                }
            }

            var summary = workbook.getSheet("도안 발주서");
            assertThat(summary.getRow(2).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("SP3_ICE-HEV_27MY_US");
            assertThat(summary.getRow(6).getCell(1)
                    .getStringCellValue())
                    .isEqualTo("SP3_ICE-HEV_27MY_US");
            assertThat(summary.getRow(6).getCell(5)
                    .getNumericCellValue())
                    .isEqualTo(1);
            assertThat(summary.getRow(10).getCell(5)
                    .getNumericCellValue())
                    .isEqualTo(1);
            assertThat(summary.getRow(30).getCell(5)
                    .getNumericCellValue())
                    .isEqualTo(2);
        }

        try(ZipFile zip = new ZipFile(output.toFile())){
            assertThat(zip.getEntry("xl/calcChain.xml"))
                    .isNull();
        }
    }

    @Test
    void keepsChapterTwelveAsSeparateSummaryGroup()
            throws Exception {

        Path source = tempDirectory.resolve(
                "KIA-SG2-HEV-PE-en_GB-2027-OM_Full-PDF-"
                + "260604-2.3_digital_low_도안분류용.xlsx");

        try(XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output =
                        Files.newOutputStream(source)){
            var sheet = workbook.createSheet("컬러체크");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("도안명");
            header.createCell(2).setCellValue("컬러도안");
            header.createCell(3).setCellValue("챕터 구별");
            header.createCell(4).setCellValue("챕터 넘버");
            addRow(
                    sheet,
                    3,
                    "ONQ5120001",
                    "V",
                    "Maintenance",
                    "12");
            workbook.write(output);
        }

        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();
        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        try(var input = Files.newInputStream(output);
                var workbook = WorkbookFactory.create(input)){
            var summary = workbook.getSheet("도안 발주서");
            assertThat(summary.getRow(30).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("12");
            assertThat(summary.getRow(30).getCell(5)
                    .getNumericCellValue())
                    .isEqualTo(1);
            assertThat(summary.getRow(32).getCell(5)
                    .getNumericCellValue())
                    .isEqualTo(1);
        }
    }

    @Test
    void doesNotOverwriteExistingFinalWorkbook()
            throws Exception {

        Path source = tempDirectory.resolve(
                "sample_컬러체크.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path first = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);
        Path second = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(first).isNotEqualTo(second);
        assertThat(second.getFileName().toString())
                .endsWith(" (1).xlsx");
    }

    @Test
    void usesCountryForEnglishUnderscoreLocale()
            throws Exception {

        Path source = tempDirectory.resolve(
                "KIA-KA4_PE_ICE-en_EG-2027-OM_Full-PDF-"
                + "260619-1.0_print_도안분류용.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(output.getFileName().toString())
                .matches(
                        "\\d{6}_도안발주내역서_"
                        + "KA4_ICE_27MY_EG_HTML\\.xlsx");

        try(var input = Files.newInputStream(output);
                var workbook = WorkbookFactory.create(input)){
            assertThat(workbook.getSheet("도안 발주서")
                    .getRow(2).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("KA4_ICE_27MY_EG");
            assertThat(workbook.getSheet("작업의뢰 내역")
                    .getRow(4).getCell(8)
                    .getStringCellValue())
                    .isEqualTo("KA4_ICE_27MY_EG");
        }
    }

    @Test
    void usesLanguageForKoreanUnderscoreLocale()
            throws Exception {

        Path source = tempDirectory.resolve(
                "KIA-KA4_PE_ICE-ko_KR-2027-OM_Full-PDF-"
                + "260619-1.0_print_도안분류용.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(output.getFileName().toString())
                .matches(
                        "\\d{6}_도안발주내역서_"
                        + "KA4_ICE_27MY_KO_HTML\\.xlsx");

        try(var input = Files.newInputStream(output);
                var workbook = WorkbookFactory.create(input)){
            assertThat(workbook.getSheet("도안 발주서")
                    .getRow(2).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("KA4_ICE_27MY_KO");
        }
    }

    @Test
    void usesUsCountryCodeForEnglishUsLocale()
            throws Exception {

        Path source = tempDirectory.resolve(
                "KIA-KA4_PE_ICE-en_US-2027-OM_Full-PDF-"
                + "260619-1.0_print_도안분류용.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(output.getFileName().toString())
                .matches(
                        "\\d{6}_도안발주내역서_"
                        + "KA4_ICE_27MY_US_HTML\\.xlsx");
    }

    @Test
    void ignoresDatePrefixAndKeepsCompoundPowertrain()
            throws Exception {

        Path source = tempDirectory.resolve(
                "260317_KIA-SP3-ICE-HEV-en_GB-2027-OM_Full-"
                + "PDF-260317-4.0_ALL_도안분류용.xlsx");
        createReviewWorkbook(source);
        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();

        Path output = service.createFinalWorkbook(
                source,
                source.getFileName().toString(),
                tempDirectory);

        assertThat(output.getFileName().toString())
                .matches(
                        "\\d{6}_도안발주내역서_"
                        + "SP3_ICE-HEV_27MY_EG_HTML\\.xlsx");

        try(var input = Files.newInputStream(output);
                var workbook = WorkbookFactory.create(input)){
            assertThat(workbook.getSheet("도안 발주서")
                    .getRow(2).getCell(2)
                    .getStringCellValue())
                    .isEqualTo("SP3_ICE-HEV_27MY_EG");
        }
    }

    @Test
    void keepsSingleHevAndPhevPowertrainNames()
            throws Exception {

        ColorCheckFinalWorkbookService service =
                new ColorCheckFinalWorkbookService();
        Path hevSource = tempDirectory.resolve(
                "KIA-SP3-HEV-en_US-2027-OM_도안분류용.xlsx");
        Path phevSource = tempDirectory.resolve(
                "KIA-SP3-PHEV-en_US-2027-OM_도안분류용.xlsx");
        createReviewWorkbook(hevSource);
        createReviewWorkbook(phevSource);

        Path hevOutput = service.createFinalWorkbook(
                hevSource,
                hevSource.getFileName().toString(),
                tempDirectory);
        Path phevOutput = service.createFinalWorkbook(
                phevSource,
                phevSource.getFileName().toString(),
                tempDirectory);

        assertThat(hevOutput.getFileName().toString())
                .contains("SP3_HEV_27MY_US_HTML");
        assertThat(phevOutput.getFileName().toString())
                .contains("SP3_PHEV_27MY_US_HTML");
    }

    private void createReviewWorkbook(Path target)
            throws Exception {

        try(XSSFWorkbook workbook = new XSSFWorkbook();
                OutputStream output =
                        Files.newOutputStream(target)){
            var sheet = workbook.createSheet("컬러체크");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("도안명");
            header.createCell(1).setCellValue("레드도안");
            header.createCell(2).setCellValue("컬러도안");
            header.createCell(3).setCellValue("챕터 구별");
            header.createCell(4).setCellValue("챕터 넘버");
            addRow(
                    sheet,
                    3,
                    "OMVQ014018",
                    "V",
                    "요약본",
                    "Q");
            addRow(
                    sheet,
                    4,
                    "OSVQ014116",
                    "V",
                    "Introduction",
                    "2");
            addRow(
                    sheet,
                    5,
                    "OSVQ014999",
                    "X",
                    "Introduction",
                    "2");
            workbook.write(output);
        }
    }

    private void addRow(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIndex,
            String drawingName,
            String colorCheck,
            String chapter,
            String chapterNumber) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(drawingName);
        row.createCell(2).setCellValue(colorCheck);
        row.createCell(3).setCellValue(chapter);
        row.createCell(4).setCellValue(chapterNumber);
    }
}
