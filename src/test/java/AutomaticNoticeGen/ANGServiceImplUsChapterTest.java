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

class ANGServiceImplUsChapterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void mergesSpecialUsChaptersWithoutTheirTocPages() throws Exception {
        try(InputStream input = getClass().getResourceAsStream(
                "/notice-rules/ANG_us_rules.xlsx")) {
            assertThat(input).isNotNull();
            Files.copy(input, tempDirectory.resolve("ANG_us_rules.xlsx"));
        }

        Path pdfPath = tempDirectory.resolve(
                "KIA-CV1-EV-en-US-2027-OM_test.pdf");
        try(PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfPath.toFile());
        }

        ChapterRange introduction = new ChapterRange(
                1, "1 Introduction", 11, 14);
        ChapterRange overview = new ChapterRange(
                2, "2 Overview", 15, 22);
        ChapterRange specifications = new ChapterRange(
                3, "3 Specifications", 23, 34);
        ChapterRange hybridOverview = new ChapterRange(
                4, "4 Hybrid system overview", 35, 48);
        ChapterRange evGuide = new ChapterRange(
                7, "7 Electric vehicle guide", 209, 272);

        ANGServiceImpl service = new ANGServiceImpl(tempDirectory) {
            @Override
            public List<ChapterRange> getChapterRanges(String ignored) {
                return List.of(
                        introduction,
                        overview,
                        specifications,
                        hybridOverview,
                        evGuide);
            }

            @Override
            public List<SectionRange> getSections(
                    String ignored,
                    ChapterRange chapter,
                    int lookaheadPages) {
                if (chapter == introduction) {
                    return List.of(
                            new SectionRange("Vehicle modifications", 12, 12),
                            new SectionRange(
                                    "Vehicle data collection",
                                    14,
                                    14));
                }
                if (chapter == overview) {
                    return List.of(
                            new SectionRange("Exterior overview", 16, 17),
                            new SectionRange(
                                    "Motor room compartment",
                                    22,
                                    22));
                }
                if (chapter == specifications) {
                    return List.of(new SectionRange("Dimensions", 24, 34));
                }
                if (chapter == hybridOverview) {
                    return List.of(
                            new SectionRange("Hybrid system", 36, 39),
                            new SectionRange("Energy flow", 45, 48));
                }
                if (chapter == evGuide) {
                    return List.of(
                            new SectionRange(
                                    "Electric vehicle overview",
                                    211,
                                    213),
                            new SectionRange(
                                    "Countermeasures for accidents or fire",
                                    267,
                                    272));
                }
                return List.of();
            }
        };

        Path outputPath = tempDirectory.resolve("result.xlsx");
        service.exportExcel(pdfPath.toString(), outputPath.toString());

        try(Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(outputPath))) {
            Sheet sheet = workbook.getSheet("검토표");

            assertRow(sheet, 5, "1 Introduction", "12~14", "Introduction");
            assertRow(sheet, 6, "2 Overview", "16~22", "Overview");
            assertRow(
                    sheet,
                    8,
                    "4 Hybrid system overview",
                    "36~48",
                    "Hybrid system overview");
            assertRow(
                    sheet,
                    9,
                    "7 Electric vehicle guide",
                    "211~272",
                    "Electric vehicle guide");
        }
    }

    private static void assertRow(
            Sheet sheet,
            int rowIndex,
            String group,
            String pages,
            String reviewItem) {
        assertThat(sheet.getRow(rowIndex).getCell(0).getStringCellValue())
                .isEqualTo(group);
        assertThat(sheet.getRow(rowIndex).getCell(1).getStringCellValue())
                .isEqualTo(pages);
        assertThat(sheet.getRow(rowIndex).getCell(2).getStringCellValue())
                .isEqualTo(reviewItem);
    }
}
