package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.DrawingColorCheckImportDetail;
import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;

/**
 * DB 반영 요약과 도안별 처리 결과를 사용자가 확인할 Excel 리포트로 만든다.
 * 실제 운영 파일은 컬러체크 생성 파일과 같은 바탕화면의 temp 폴더에 저장한다.
 */
@Service
public class ColorCheckImportReportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 운영 기본 경로에 리포트를 저장하고 생성된 전체 경로를 반환한다. */
    public Path createReport(
            DrawingColorCheckImportResult result,
            String sourceFileName) throws IOException {
        Path outputDirectory = Path.of(
                System.getProperty("user.home"),
                "Desktop",
                "temp");
        return createReport(
                result,
                sourceFileName,
                outputDirectory);
    }

    /** 테스트에서도 임시 폴더를 지정할 수 있도록 저장 경로를 받는 실제 생성 메서드다. */
    public Path createReport(
            DrawingColorCheckImportResult result,
            String sourceFileName,
            Path outputDirectory) throws IOException {

        Files.createDirectories(outputDirectory);
        Path reportFile = findAvailableFile(
                outputDirectory,
                createReportFileName(sourceFileName));

        try(Workbook workbook = new XSSFWorkbook();
                OutputStream output =
                        Files.newOutputStream(reportFile)){
            Sheet sheet = workbook.createSheet("DB 반영 결과");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(
                    "컬러체크 DB 반영 리포트");
            title.getCell(0).setCellStyle(titleStyle);

            addLabelValue(
                    sheet,
                    2,
                    "생성 시각",
                    LocalDateTime.now().format(DATE_TIME_FORMAT));
            addLabelValue(
                    sheet,
                    3,
                    "검토 엑셀",
                    safe(sourceFileName));
            addLabelValue(
                    sheet,
                    5,
                    "처리 대상",
                    result.totalRows() + "건");
            addLabelValue(
                    sheet,
                    6,
                    "신규",
                    result.insertedCount() + "건");
            addLabelValue(
                    sheet,
                    7,
                    "수정",
                    result.updatedCount() + "건");
            addLabelValue(
                    sheet,
                    8,
                    "변경 없음",
                    result.unchangedCount() + "건");
            addLabelValue(
                    sheet,
                    9,
                    "제외",
                    result.skippedCount() + "건");

            int headerRowIndex = 11;
            Row header = sheet.createRow(headerRowIndex);
            String[] headers = {
                    "Excel 행",
                    "도안명",
                    "입력값",
                    "실제 반영값",
                    "기존 DB 값",
                    "처리 결과",
                    "비고"
            };

            for(int column = 0;
                    column < headers.length;
                    column++){
                header.createCell(column)
                        .setCellValue(headers[column]);
                header.getCell(column)
                        .setCellStyle(headerStyle);
            }

            int rowIndex = headerRowIndex + 1;

            for(DrawingColorCheckImportDetail detail
                    : result.details()){
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(
                        detail.excelRowNumber());
                row.createCell(1).setCellValue(
                        safe(detail.drawingName()));
                row.createCell(2).setCellValue(
                        safe(detail.inputValue()));
                row.createCell(3).setCellValue(
                        safe(detail.appliedValue()));
                row.createCell(4).setCellValue(
                        safe(detail.previousValue()));
                row.createCell(5).setCellValue(
                        safe(detail.status()));
                row.createCell(6).setCellValue(
                        safe(detail.note()));
            }

            sheet.createFreezePane(
                    0,
                    headerRowIndex + 1);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            headerRowIndex,
                            Math.max(headerRowIndex, rowIndex - 1),
                            0,
                            headers.length - 1));
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 34 * 256);
            sheet.setColumnWidth(2, 12 * 256);
            sheet.setColumnWidth(3, 14 * 256);
            sheet.setColumnWidth(4, 14 * 256);
            sheet.setColumnWidth(5, 14 * 256);
            sheet.setColumnWidth(6, 38 * 256);

            workbook.write(output);
        }

        return reportFile;
    }

    private void addLabelValue(
            Sheet sheet,
            int rowIndex,
            String label,
            String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(
                IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private String createReportFileName(String sourceFileName) {
        String baseName = sourceFileName == null
                || sourceFileName.isBlank()
                ? "컬러체크"
                : sourceFileName.trim()
                        .replaceFirst("(?i)\\.xlsx?$", "");
        baseName = baseName.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_");
        return baseName + "_DB반영리포트.xlsx";
    }

    private Path findAvailableFile(
            Path outputDirectory,
            String fileName) {
        Path firstChoice = outputDirectory.resolve(fileName);

        if(!Files.exists(firstChoice)){
            return firstChoice;
        }

        String baseName = fileName.replaceFirst(
                "(?i)\\.xlsx$",
                "");

        for(int sequence = 1; ; sequence++){
            Path candidate = outputDirectory.resolve(
                    baseName
                    + " ("
                    + sequence
                    + ").xlsx");

            if(!Files.exists(candidate)){
                return candidate;
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
