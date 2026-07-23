package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.mapper.DrawingColorCheckMapper;

class ColorCheckExportServiceTest {

    @Test
    void keepsMixedCaseDrawingNameAndMatchesDbIgnoringCase()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        DrawingColorCheckDto check =
                new DrawingColorCheckDto();
        check.setDrawingName("  N_SP3I26_B04_038_IR  ");
        check.setCheckValue(" x ");
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of(check));
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdf("N_SP3i26_B04_038_IR"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var row = workbook.getSheet("컬러체크").getRow(3);
            assertThat(row.getCell(0).getStringCellValue())
                    .isEqualTo("N_SP3i26_B04_038_IR");
            assertThat(row.getCell(2).getStringCellValue())
                    .isEqualTo("X");
        }
    }

    @Test
    void createsExpectedColumnsAndEmbeddedDrawingImage()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        DrawingColorCheckDto check =
                new DrawingColorCheckDto();
        check.setDrawingName("N_SP327_002_001_3_E");
        check.setCheckValue("V");
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of(check));
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);
        byte[] excel = service.createWorkbook(
                createPdf("N_SP327_002_001_3_E"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var sheet = workbook.getSheet("컬러체크");
            assertThat(sheet.getRow(0).getCell(0)
                    .getStringCellValue()).isEqualTo("체크");
            assertThat(sheet.getNumMergedRegions()).isEqualTo(1);
            assertThat(sheet.getRow(2).getCell(0)
                    .getStringCellValue()).isEqualTo("도안명");
            assertThat(sheet.getRow(2).getCell(1)
                    .getStringCellValue()).isEqualTo("레드도안");
            assertThat(sheet.getRow(2).getCell(2)
                    .getStringCellValue()).isEqualTo("컬러도안");
            assertThat(sheet.getRow(2).getCell(3)
                    .getStringCellValue()).isEqualTo("챕터 구별");
            assertThat(sheet.getRow(2).getCell(4)
                    .getStringCellValue()).isEqualTo("챕터 넘버");
            assertThat(sheet.getRow(2).getCell(5)
                    .getStringCellValue()).isEqualTo("이미지");
            assertThat(sheet.getRow(3).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("N_SP327_002_001_3_E");
            assertThat(sheet.getRow(3).getCell(1)
                    .getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(3).getCell(2)
                    .getStringCellValue()).isEqualTo("V");
            assertThat(sheet.getRow(3).getCell(3)
                    .getStringCellValue()).isEqualTo("Door controls");
            assertThat(sheet.getRow(3).getCell(4)
                    .getStringCellValue()).isEqualTo("4");
            assertThat(workbook.getAllPictures()).hasSize(1);

            BufferedImage image = javax.imageio.ImageIO.read(
                    new ByteArrayInputStream(
                            workbook.getAllPictures()
                            .get(0).getData()));
            assertThat(image.getWidth()).isGreaterThan(100);
            assertThat(image.getHeight()).isGreaterThan(100);
        }
    }

    @Test
    void acceptsLegacyDrawingNamePrefixes()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdf("OCL4M035125N"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            assertThat(workbook.getSheet("컬러체크")
                    .getRow(3).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("OCL4M035125N");
        }
    }

    @Test
    void acceptsAnyDrawingCodeIncludingKiaPrefix()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdf("ODEEV123456"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            assertThat(workbook.getSheet("컬러체크")
                    .getRow(3).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("ODEEV123456");
        }

        byte[] kiaExcel = service.createWorkbook(
                createPdf("KIA_C00_048"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(kiaExcel))){
            assertThat(workbook.getSheet("컬러체크")
                    .getRow(3).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("KIA_C00_048");
        }
    }

    @Test
    void excludesLargerFuseTableCellValues()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.createWorkbook(
                        createPdf("MEMORY1", 7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("도안명을 찾지 못했습니다");
    }

    @Test
    void keepsOnlyFirstOccurrenceOfDuplicateDrawingName()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdfWithDuplicateDrawingNames(
                        "N_SP327_B06_060",
                        "n_sp327_b06_060"));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var sheet = workbook.getSheet("컬러체크");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(3).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("N_SP327_B06_060");
            assertThat(workbook.getAllPictures()).hasSize(1);
        }
    }

    @Test
    void numbersChapterWithoutDrawingsBeforeNextChapter()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdfWithChapterPages(
                        new ChapterDrawing(
                                "Introduction",
                                null),
                        new ChapterDrawing(
                                "Overview",
                                "ONQ5052179L")));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var row = workbook.getSheet("컬러체크")
                    .getRow(3);
            assertThat(row.getCell(0).getStringCellValue())
                    .isEqualTo("ONQ5052179L");
            assertThat(row.getCell(3).getStringCellValue())
                    .isEqualTo("Overview");
            assertThat(row.getCell(4).getStringCellValue())
                    .isEqualTo("2");
        }
    }

    @Test
    void usesPdfChapterNumbersAndKeepsOrderWithinChapter()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdfWithChapterPages(
                        new ChapterDrawing(
                                "Introduction",
                                "ONQ5000001L"),
                        new ChapterDrawing(
                                "Overview",
                                "ONQ5000002L"),
                        new ChapterDrawing(
                                "Overview",
                                "ONQ5000009L"),
                        new ChapterDrawing(
                                "Specifications",
                                "ONQ5000003L")));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var sheet = workbook.getSheet("컬러체크");
            assertThat(java.util.List.of(
                    sheet.getRow(3).getCell(0)
                            .getStringCellValue(),
                    sheet.getRow(4).getCell(0)
                            .getStringCellValue(),
                    sheet.getRow(5).getCell(0)
                            .getStringCellValue(),
                    sheet.getRow(6).getCell(0)
                            .getStringCellValue()))
                    .containsExactly(
                            "ONQ5000001L",
                            "ONQ5000002L",
                            "ONQ5000009L",
                            "ONQ5000003L");
            assertThat(java.util.List.of(
                    sheet.getRow(3).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(4).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(5).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(6).getCell(4)
                            .getStringCellValue()))
                    .containsExactly("1", "2", "2", "3");
        }
    }

    @Test
    void recognizesPluralControlsAndFeaturesChapter()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdfWithChapterPages(
                        new ChapterDrawing(
                                "Driver Adjustments",
                                "OKA4034049L_2"),
                        new ChapterDrawing(
                                "Controls and Features",
                                "OKA4040001L"),
                        new ChapterDrawing(
                                "Hybrid vehicle guide",
                                "OKA4070001L"),
                        new ChapterDrawing(
                                "Driver assistance guide",
                                "OKA4056199L")));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var sheet = workbook.getSheet("컬러체크");
            assertThat(sheet.getRow(3).getCell(3)
                    .getStringCellValue())
                    .isEqualTo("Driver Adjustments");
            assertThat(sheet.getRow(3).getCell(4)
                    .getStringCellValue())
                    .isEqualTo("5");
            assertThat(sheet.getRow(4).getCell(3)
                    .getStringCellValue())
                    .isEqualTo("Hybrid vehicle guide");
            assertThat(sheet.getRow(4).getCell(4)
                    .getStringCellValue())
                    .isEqualTo("7");
            assertThat(sheet.getRow(5).getCell(3)
                    .getStringCellValue())
                    .isEqualTo("Controls and Features");
            assertThat(sheet.getRow(5).getCell(4)
                    .getStringCellValue())
                    .isEqualTo("8");
            assertThat(sheet.getRow(6).getCell(3)
                    .getStringCellValue())
                    .isEqualTo("Driver assistance guide");
            assertThat(sheet.getRow(6).getCell(4)
                    .getStringCellValue())
                    .isEqualTo("10");
        }
    }

    @Test
    void usesPdfBookmarkChapterNumbersWhenChapterOrderChanges()
            throws Exception {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        Mockito.when(mapper.findByDrawingNames(
                Mockito.anyList()))
                .thenReturn(java.util.List.of());
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        byte[] excel = service.createWorkbook(
                createPdfWithOutlineChapterPages(
                        new ChapterDrawing(
                                "6 Seating and safety restraints",
                                "ONQ5060001L"),
                        new ChapterDrawing(
                                "7 Controls and Features",
                                "ONQ5070001L"),
                        new ChapterDrawing(
                                "8 Driving your vehicle",
                                "ONQ5080001L"),
                        new ChapterDrawing(
                                "10 Driver assistance guide",
                                "ONQ5100001L")));

        try(XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excel))){
            var sheet = workbook.getSheet("컬러체크");
            assertThat(java.util.List.of(
                    sheet.getRow(3).getCell(3)
                            .getStringCellValue(),
                    sheet.getRow(4).getCell(3)
                            .getStringCellValue(),
                    sheet.getRow(5).getCell(3)
                            .getStringCellValue(),
                    sheet.getRow(6).getCell(3)
                            .getStringCellValue()))
                    .containsExactly(
                            "Seating and safety restraints",
                            "Controls and Features",
                            "Driving your vehicle",
                            "Driver assistance guide");
            assertThat(java.util.List.of(
                    sheet.getRow(3).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(4).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(5).getCell(4)
                            .getStringCellValue(),
                    sheet.getRow(6).getCell(4)
                            .getStringCellValue()))
                    .containsExactly("6", "7", "8", "10");
        }
    }

    @Test
    void recognizesKoreanChapterTitlesAndNumberedHeadings() {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);

        assertThat(service.recognizeChapterTitle(
                "운전자 보조 시스템"))
                .isEqualTo("운전자 보조 시스템");
        assertThat(service.recognizeChapterTitle(
                "제 7장 시동 및 주행"))
                .isEqualTo("시동 및 주행");
        assertThat(service.recognizeChapterTitle(
                "비상시 응급조치"))
                .isEqualTo("비상시 응급 조치");
        assertThat(service.recognizeChapterTitle(
                "Hybrid vehicle guide"))
                .isEqualTo("Hybrid vehicle guide");
        assertThat(service.recognizeChapterTitle(
                "Electric vehicle guide"))
                .isEqualTo("Electric vehicle guide");
        assertThat(service.recognizeChapterTitle(
                "7 Hybrid vehicle guide"))
                .isEqualTo("Hybrid vehicle guide");
        assertThat(service.recognizeChapterTitle(
                "7 Electric vehicle guide"))
                .isEqualTo("Electric vehicle guide");
    }

    @Test
    void recognizesGl3KoreanManualChapterTitles() {

        DrawingColorCheckMapper mapper =
                Mockito.mock(DrawingColorCheckMapper.class);
        ColorCheckExportService service =
                new ColorCheckExportService(mapper);
        java.util.List<String> titles = java.util.List.of(
                "안전 주의 사항",
                "그림 목차",
                "차량 정보",
                "차량 열림과 닫힘 장치",
                "주행 준비",
                "좌석 및 안전 장치",
                "하이브리드 전용 기능",
                "차량 편의 장치",
                "시동 및 주행",
                "운전자 보조 시스템",
                "비상시 응급조치",
                "정기 점검");

        assertThat(titles.stream()
                .map(service::recognizeChapterTitle)
                .toList())
                .containsExactly(
                        "안전 주의 사항",
                        "그림 목차",
                        "차량 정보",
                        "차량 열림과 닫힘 장치",
                        "주행 준비",
                        "좌석 및 안전 장치",
                        "하이브리드 전용 기능",
                        "차량 편의 장치",
                        "시동 및 주행",
                        "운전자 보조 시스템",
                        "비상시 응급 조치",
                        "정기 점검");
    }

    private byte[] createPdf(String drawingName)
            throws Exception {
        return createPdf(drawingName, 5);
    }

    private byte[] createPdf(
            String drawingName,
            float drawingNameFontSize)
            throws Exception {

        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            try(PDPageContentStream content =
                    new PDPageContentStream(document, page)){
                content.setNonStrokingColor(Color.LIGHT_GRAY);
                content.addRect(40, 180, 515, 560);
                content.fill();
                writeText(content, font, 14, 40, 790,
                        "Chapter 4 Door controls");
                writeText(content, font,
                        drawingNameFontSize, 210, 150,
                        drawingName);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithDuplicateDrawingNames(
            String firstName,
            String secondName) throws Exception {

        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            for(String drawingName
                    : java.util.List.of(
                            firstName,
                            secondName)){
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try(PDPageContentStream content =
                        new PDPageContentStream(
                                document,
                                page)){
                    content.setNonStrokingColor(
                            Color.LIGHT_GRAY);
                    content.addRect(40, 180, 515, 560);
                    content.fill();
                    writeText(content, font, 14, 40, 790,
                            "Chapter 4 Door controls");
                    writeText(content, font, 5, 210, 150,
                            drawingName);
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithChapterPages(
            ChapterDrawing... pages) throws Exception {

        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            for(ChapterDrawing pageData : pages){
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try(PDPageContentStream content =
                        new PDPageContentStream(
                                document,
                                page)){
                    writeText(
                            content,
                            font,
                            14,
                            40,
                            790,
                            pageData.chapter());

                    if(pageData.drawingName() != null){
                        content.setNonStrokingColor(
                                Color.LIGHT_GRAY);
                        content.addRect(
                                40,
                                180,
                                515,
                                560);
                        content.fill();
                        writeText(
                                content,
                                font,
                                5,
                                210,
                                150,
                                pageData.drawingName());
                    }
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithOutlineChapterPages(
            ChapterDrawing... pages) throws Exception {

        try(PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);
            PDDocumentOutline outline =
                    new PDDocumentOutline();
            document.getDocumentCatalog()
                    .setDocumentOutline(outline);

            for(ChapterDrawing pageData : pages){
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDOutlineItem item =
                        new PDOutlineItem();
                item.setTitle(pageData.chapter());
                item.setDestination(page);
                outline.addLast(item);

                try(PDPageContentStream content =
                        new PDPageContentStream(
                                document,
                                page)){
                    writeText(
                            content,
                            font,
                            10,
                            40,
                            790,
                            "Body text without chapter heading");
                    content.setNonStrokingColor(
                            Color.LIGHT_GRAY);
                    content.addRect(
                            40,
                            180,
                            515,
                            560);
                    content.fill();
                    writeText(
                            content,
                            font,
                            5,
                            210,
                            150,
                            pageData.drawingName());
                }
            }

            outline.openNode();
            document.save(output);
            return output.toByteArray();
        }
    }

    private record ChapterDrawing(
            String chapter,
            String drawingName) {
    }

    private void writeText(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            float x,
            float y,
            String text) throws Exception {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}
