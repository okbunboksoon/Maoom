package maoomWeb.ire.admin.controller;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;
import maoomWeb.ire.user.service.ReplaceDarkSymbolService;

/**
 * 관리자 > Replace Symbol DB 화면의 조회/수정/삭제/내보내기 API.
 *
 * <p>화면 위치는 {@code templates/admin/section/replaceDarkSymbol.html}이고,
 * 프론트 동작은 {@code static/admin/js/adminMain.js}의
 * replaceDarkSymbol 관련 함수들이 담당한다.</p>
 */
@Controller
public class AdminReplaceDarkSymbolController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReplaceDarkSymbolService service;

    public AdminReplaceDarkSymbolController(
            ReplaceDarkSymbolService service) {
        this.service = service;
    }

    @GetMapping("/admin/replace-dark-symbol/items")
    @ResponseBody
    public List<ReplaceDarkSymbolItem> getItems() {
        // DataTables가 그대로 표시할 From/To 행 목록을 내려준다.
        return service.findAll();
    }

    @PutMapping("/admin/replace-dark-symbol/items")
    @ResponseBody
    public ReplaceDarkSymbolItem saveItem(
            @RequestBody ReplaceDarkSymbolItem item) {
        // From 값을 키로 저장한다. 같은 From이면 To 값만 최신 값으로 갱신된다.
        return service.save(item);
    }

    @DeleteMapping("/admin/replace-dark-symbol/items/{fromSymbol}")
    @ResponseBody
    public void deleteItem(@PathVariable String fromSymbol) {
        // 삭제 기준도 XSL의 @from 값과 동일한 from_symbol이다.
        service.delete(fromSymbol);
    }

    @GetMapping("/admin/replace-dark-symbol/export")
    public ResponseEntity<byte[]> exportExcel()
            throws IOException {
        // 관리자 검토용 다운로드이며, 업로드 기능은 아직 연결하지 않는다.
        byte[] excel = createWorkbook(service.findAll());
        String fileName = "replace_dark_symbol_DB_"
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

    private byte[] createWorkbook(List<ReplaceDarkSymbolItem> items)
            throws IOException {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("replace_dark_symbol DB");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("replace_dark_symbol DB");
            title.getCell(0).setCellStyle(titleStyle);

            Row createdAt = sheet.createRow(1);
            createdAt.createCell(0).setCellValue("내보낸 시각");
            createdAt.createCell(1).setCellValue(
                    LocalDateTime.now().format(DATE_TIME_FORMAT));

            Row total = sheet.createRow(2);
            total.createCell(0).setCellValue("총 건수");
            total.createCell(1).setCellValue(items.size());

            Row header = sheet.createRow(4);
            String[] headers = {
                    "No",
                    "from",
                    "to",
                    "created_at",
                    "updated_at"
            };

            for(int column = 0; column < headers.length; column++){
                header.createCell(column).setCellValue(headers[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }

            int rowIndex = 5;
            for(int index = 0; index < items.size(); index++){
                ReplaceDarkSymbolItem item = items.get(index);
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(index + 1);
                row.createCell(1).setCellValue(safe(item.getFromSymbol()));
                row.createCell(2).setCellValue(safe(item.getToSymbol()));
                row.createCell(3).setCellValue(
                        item.getCreatedAt() == null
                                ? ""
                                : item.getCreatedAt().format(DATE_TIME_FORMAT));
                row.createCell(4).setCellValue(
                        item.getUpdatedAt() == null
                                ? ""
                                : item.getUpdatedAt().format(DATE_TIME_FORMAT));
            }

            sheet.createFreezePane(0, 5);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            4,
                            Math.max(4, rowIndex - 1),
                            0,
                            headers.length - 1));
            sheet.setColumnWidth(0, 10 * 256);
            sheet.setColumnWidth(1, 36 * 256);
            sheet.setColumnWidth(2, 36 * 256);
            sheet.setColumnWidth(3, 22 * 256);
            sheet.setColumnWidth(4, 22 * 256);

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
