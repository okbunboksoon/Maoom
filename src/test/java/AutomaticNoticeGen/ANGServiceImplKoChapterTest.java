package AutomaticNoticeGen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ANGServiceImplKoChapterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesSafetyAndFigureContentsAsSeparateWholeChapterRows()
            throws Exception {
        Path rulesPath = tempDirectory.resolve("ANG_ko_rules.xlsx");
        try(InputStream input = getClass().getResourceAsStream(
                "/notice-rules/ANG_ko_rules.xlsx")) {
            assertThat(input).isNotNull();
            Files.copy(input, rulesPath);
        }

        Path pdfPath = tempDirectory.resolve(
                "KIA-CV-PE-EV_KO-2027-OM_test.pdf");
        try(PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfPath.toFile());
        }

        ChapterRange safety = new ChapterRange(
                1, "1 안전 주의 사항", 11, 42);
        ChapterRange figureContents = new ChapterRange(
                2, "2 그림 목차", 43, 54);
        ChapterRange vehicleInformation = new ChapterRange(
                3, "3 차량 정보", 55, 66);
        ChapterRange evFeatures = new ChapterRange(
                7, "7 전기차 전용 기능", 241, 296);
        ChapterRange index = new ChapterRange(
                8, "I 색인", 297, 300);

        ANGServiceImpl service = new ANGServiceImpl(tempDirectory) {
            @Override
            public List<ChapterRange> getChapterRanges(String ignored) {
                return List.of(
                        safety,
                        figureContents,
                        vehicleInformation,
                        evFeatures,
                        index);
            }

            @Override
            public List<SectionRange> getSections(
                    String ignored,
                    ChapterRange chapter,
                    int lookaheadPages) {
                if (chapter == safety) {
                    return List.of(new SectionRange(
                            "안전 주의 사항 본문", 12, 42));
                }
                if (chapter == figureContents) {
                    return List.of(new SectionRange(
                            "외관도 I", 44, 54));
                }
                if (chapter == vehicleInformation) {
                    return List.of(new SectionRange(
                            "차량 제원", 56, 66));
                }
                if (chapter == evFeatures) {
                    return List.of(
                            new SectionRange("전기 자동차 개요", 243, 244),
                            new SectionRange(
                                    "전기 자동차 안전 주의 사항",
                                    293,
                                    296));
                }
                return List.of();
            }
        };

        Path outputPath = tempDirectory.resolve("result.xlsx");
        service.exportExcel(pdfPath.toString(), outputPath.toString());

        try(Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(outputPath))) {
            Sheet sheet = workbook.getSheet("검토표");

            assertThat(sheet.getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("0장");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue())
                    .isEqualTo("1~10");
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue())
                    .isEqualTo("서문");

            assertThat(sheet.getRow(5).getCell(0).getStringCellValue())
                    .isEqualTo("1장");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue())
                    .isEqualTo("12~42");
            assertThat(sheet.getRow(5).getCell(2).getStringCellValue())
                    .isEqualTo("안전 주의 사항");

            assertThat(sheet.getRow(6).getCell(0).getStringCellValue())
                    .isEqualTo("2장");
            assertThat(sheet.getRow(6).getCell(1).getStringCellValue())
                    .isEqualTo("44~54");
            assertThat(sheet.getRow(6).getCell(2).getStringCellValue())
                    .isEqualTo("그림 목차");

            assertThat(sheet.getRow(7).getCell(0).getStringCellValue())
                    .isEqualTo("3장");
            assertThat(sheet.getRow(7).getCell(1).getStringCellValue())
                    .isEqualTo("56~66");
            assertThat(sheet.getRow(7).getCell(2).getStringCellValue())
                    .isEqualTo("차량 제원");

            assertThat(sheet.getRow(8).getCell(0).getStringCellValue())
                    .isEqualTo("4장");
            assertThat(sheet.getRow(8).getCell(1).getStringCellValue())
                    .isEqualTo("243~296");
            assertThat(sheet.getRow(8).getCell(2).getStringCellValue())
                    .isEqualTo("EV가이드");
            assertThat(sheet.getLastRowNum()).isEqualTo(8);
        }
    }
}
