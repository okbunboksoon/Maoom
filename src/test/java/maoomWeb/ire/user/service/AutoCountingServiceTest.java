package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.AutoCountingAnalyzeResult;

class AutoCountingServiceTest {

    private final AutoCountingService service = new AutoCountingService();

    @Test
    void analyzePdfCountsFirstEditionChaptersFromBookmarks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                createBookmarkedPdf());

        AutoCountingAnalyzeResult result = service.analyzePdf(
                file,
                "first",
                false);

        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.fullPages()).isEqualTo(8);
        assertThat(result.chapters())
                .containsEntry("1장", 2)
                .containsEntry("2장", 2)
                .containsEntry("Index", 1);
    }

    @Test
    void analyzePdfCountsFirstEditionAbbreviationAndIndexLetterBookmarks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                createLetterSpecialBookmarkPdf());

        AutoCountingAnalyzeResult result = service.analyzePdf(
                file,
                "first",
                false);

        assertThat(result.chapters())
                .containsEntry("12장", 2)
                .containsEntry("Abbreviation", 2)
                .containsEntry("Index", 2);
    }

    @Test
    void analyzePdfRejectsNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.txt",
                "text/plain",
                "text".getBytes());

        assertThatThrownBy(() -> service.analyzePdf(file, "first", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF 파일만");
    }

    @Test
    void createFirstEditionWorkbookWritesInputSheetFromChapterCounts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                createBookmarkedPdf());

        AutoCountingService.WorkbookFile workbookFile =
                service.createFirstEditionWorkbook(
                        file,
                        Map.ofEntries(
                                Map.entry("model", "Model A"),
                                Map.entry("language", "KOR(KO)"),
                                Map.entry("pubNumber", "PUB-001"),
                                Map.entry("fullPage", "8"),
                                Map.entry("changePage", "5"),
                                Map.entry("pack32", "false"),
                                Map.entry("fm", "false"),
                                Map.entry("newTemplate", "false"),
                                Map.entry("chapter_서문", "0"),
                                Map.entry("chapter_1장", "2"),
                                Map.entry("chapter_2장", "1"),
                                Map.entry("chapter_Index", "1")));

        assertThat(workbookFile.fileName())
                .isEqualTo("Model_A_KOR(KO)_PUB-001-Table_Array.xlsx");
        assertThat(workbookFile.content()).isNotEmpty();

        try(Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(workbookFile.content()))){
            Sheet input = workbook.getSheet("Input");
            assertThat(input).isNotNull();
            assertThat(input.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("1");
            assertThat(input.getRow(6).getCell(0).getNumericCellValue())
                    .isEqualTo(7);
            assertThat(input.getRow(7).getCell(0).getNumericCellValue())
                    .isEqualTo(8);
            assertThat(input.getRow(8).getCell(0).getNumericCellValue())
                    .isEqualTo(9);
            assertThat(input.getRow(9).getCell(0).getStringCellValue())
                    .isEqualTo("BLANK");
            assertThat(input.getRow(11).getCell(0).getStringCellValue())
                    .isEqualTo("BLANK");
            assertThat(input.getRow(12).getCell(0).getStringCellValue())
                    .isEqualTo("MEMO");
        }
    }

    private byte[] createBookmarkedPdf() throws Exception {
        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()){

            PDPage page1 = addPage(document);
            addPage(document);
            PDPage page3 = addPage(document);
            addPage(document);
            PDPage page5 = addPage(document);

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            addBookmark(outline, "1 Introduction", page1);
            addBookmark(outline, "2 Safety", page3);
            addBookmark(outline, "Index", page5);
            outline.openNode();

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createLetterSpecialBookmarkPdf() throws Exception {
        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()){

            PDPage page1 = addPage(document);
            addPage(document);
            PDPage page3 = addPage(document);
            addPage(document);
            PDPage page5 = addPage(document);
            addPage(document);

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            addBookmark(outline, "12 Tarbijateave", page1);
            addBookmark(outline, "A Lühend", page3);
            addBookmark(outline, "I Aineloend", page5);
            outline.openNode();

            document.save(output);
            return output.toByteArray();
        }
    }

    private PDPage addPage(PDDocument document) {
        PDPage page = new PDPage();
        document.addPage(page);
        return page;
    }

    private void addBookmark(
            PDDocumentOutline outline,
            String title,
            PDPage page) {

        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(page);
        outline.addLast(item);
    }
}
