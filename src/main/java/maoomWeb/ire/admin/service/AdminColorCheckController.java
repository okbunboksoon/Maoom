package maoomWeb.ire.admin.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.service.DrawingColorCheckService;

/** 관리자 화면에서 컬러체크 DB를 내려받는 기능을 담당한다. */
@Controller
public class AdminColorCheckController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DrawingColorCheckService drawingColorCheckService;

    public AdminColorCheckController(
            DrawingColorCheckService drawingColorCheckService) {
        this.drawingColorCheckService = drawingColorCheckService;
    }

    /** 관리자 화면 테이블에 표시할 컬러체크 DB 전체 목록을 반환한다. */
    @GetMapping("/admin/color-check/items")
    @ResponseBody
    public List<DrawingColorCheckDto> getColorCheckItems() {
        return drawingColorCheckService.findAll();
    }

    /** 관리자 화면에서 컬러체크 항목을 추가하거나 수정한다. */
    @PutMapping("/admin/color-check/items")
    @ResponseBody
    public DrawingColorCheckDto saveColorCheckItem(
            @RequestBody DrawingColorCheckDto item) {
        return drawingColorCheckService.save(item);
    }

    /** 관리자 화면에서 컬러체크 항목을 삭제한다. */
    @DeleteMapping("/admin/color-check/items/{drawingName}")
    @ResponseBody
    public void deleteColorCheckItem(
            @PathVariable String drawingName) {
        drawingColorCheckService.delete(drawingName);
    }

    /** 현재 컬러체크 DB 전체 내용을 XLSX 파일로 내려준다. */
    @GetMapping("/admin/color-check/export")
    public ResponseEntity<byte[]> exportColorCheckDb()
            throws IOException {
        byte[] excel = createWorkbook(
                drawingColorCheckService.findAll());
        String fileName = "컬러체크_DB_"
                + LocalDateTime.now().format(FILE_TIME_FORMAT)
                + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(excel.length);

        return new ResponseEntity<>(
                excel,
                headers,
                HttpStatus.OK);
    }

    private byte[] createWorkbook(
            List<DrawingColorCheckDto> items)
            throws IOException {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("컬러체크 DB");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row title = sheet.createRow(0);
            title.createCell(0)
                    .setCellValue("컬러체크 DB");
            title.getCell(0)
                    .setCellStyle(titleStyle);

            Row createdAt = sheet.createRow(1);
            createdAt.createCell(0)
                    .setCellValue("내보낸 시각");
            createdAt.createCell(1)
                    .setCellValue(LocalDateTime.now()
                            .format(DATE_TIME_FORMAT));

            Row total = sheet.createRow(2);
            total.createCell(0)
                    .setCellValue("총 건수");
            total.createCell(1)
                    .setCellValue(items.size());

            Row header = sheet.createRow(4);
            String[] headers = {
                    "No",
                    "도안명",
                    "컬러도안",
                    "등록일"
            };

            for(int column = 0;
                    column < headers.length;
                    column++){
                header.createCell(column)
                        .setCellValue(headers[column]);
                header.getCell(column)
                        .setCellStyle(headerStyle);
            }

            int rowIndex = 5;

            for(int index = 0;
                    index < items.size();
                    index++){
                DrawingColorCheckDto item = items.get(index);
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0)
                        .setCellValue(index + 1);
                row.createCell(1)
                        .setCellValue(safe(item.getDrawingName()));
                row.createCell(2)
                        .setCellValue(safe(item.getCheckValue()));
                row.createCell(3)
                        .setCellValue(item.getRegDt() == null
                                ? ""
                                : item.getRegDt().format(DATE_TIME_FORMAT));
            }

            sheet.createFreezePane(0, 5);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            4,
                            Math.max(4, rowIndex - 1),
                            0,
                            headers.length - 1));
            sheet.setColumnWidth(0, 10 * 256);
            sheet.setColumnWidth(1, 42 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            sheet.setColumnWidth(3, 22 * 256);

            workbook.write(output);
            return output.toByteArray();
        }
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
