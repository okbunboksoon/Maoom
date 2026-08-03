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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.QsgDbImportResult;
import maoomWeb.ire.user.dto.QsgDbTerm;
import maoomWeb.ire.user.service.QsgDbAdminService;

/**
 * 관리자 페이지의 QSG DB 조회 API.
 *
 * <p>화면 위치는 {@code templates/admin/section/qsgDb.html}이고,
 * 프론트 호출은 {@code static/admin/js/adminMain.js}의
 * {@code loadQsgDbItems()}에서 한다.</p>
 *
 * <p>현재 QSG DB는 실제 DB 테이블이 아니라 classpath의
 * {@code src/main/resources/xsl/QSG_DB.xml}을 읽기 전용으로 펼쳐 보여준다.
 * 나중에 MySQL 테이블로 옮길 때는 이 컨트롤러 URL은 유지하고,
 * {@link QsgDbAdminService} 내부 조회 방식만 Mapper 기반으로 바꾸면
 * 관리자 화면 수정 범위를 줄일 수 있다.</p>
 */
@Controller
public class AdminQsgDbController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QsgDbAdminService qsgDbAdminService;

    public AdminQsgDbController(QsgDbAdminService qsgDbAdminService) {
        this.qsgDbAdminService = qsgDbAdminService;
    }

    @GetMapping("/admin/qsg-db/items")
    @ResponseBody
    public List<QsgDbTerm> getItems() {
        return qsgDbAdminService.findAll();
    }

    @PostMapping("/admin/qsg-db/import")
    @ResponseBody
    public QsgDbImportResult importExcel(
            @RequestParam("file") MultipartFile file)
            throws IOException {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "업로드할 엑셀 파일을 선택해 주세요.");
        }

        return qsgDbAdminService.importExcel(file.getInputStream());
    }

    @GetMapping("/admin/qsg-db/export")
    public ResponseEntity<byte[]> exportExcel()
            throws IOException {
        byte[] excel = createWorkbook(qsgDbAdminService.findAll());
        String fileName = "QSG_DB_"
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

    private byte[] createWorkbook(List<QsgDbTerm> items)
            throws IOException {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("QSG DB");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("QSG DB");
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
                    "hash",
                    "lang",
                    "term"
            };

            for(int column = 0; column < headers.length; column++){
                header.createCell(column).setCellValue(headers[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }

            int rowIndex = 5;
            for(int index = 0; index < items.size(); index++){
                QsgDbTerm item = items.get(index);
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(index + 1);
                row.createCell(1).setCellValue(safe(item.getHash()));
                row.createCell(2).setCellValue(safe(item.getLang()));
                row.createCell(3).setCellValue(safe(item.getTerm()));
            }

            sheet.createFreezePane(0, 5);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            4,
                            Math.max(4, rowIndex - 1),
                            0,
                            headers.length - 1));
            sheet.setColumnWidth(0, 10 * 256);
            sheet.setColumnWidth(1, 72 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            sheet.setColumnWidth(3, 90 * 256);

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
