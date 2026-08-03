package maoomWeb.ire.admin.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.BerAsisTobeImportResult;
import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.dto.BerAsisTobeSentenceImportResult;
import maoomWeb.ire.user.service.BerAsisTobeAdminService;
import maoomWeb.ire.user.service.BerAsisTobeSentenceImportService;

@Controller
public class AdminBerAsisTobeController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BerAsisTobeAdminService adminService;
    private final BerAsisTobeSentenceImportService sentenceImportService;

    public AdminBerAsisTobeController(
            BerAsisTobeAdminService adminService,
            BerAsisTobeSentenceImportService sentenceImportService) {
        this.adminService = adminService;
        this.sentenceImportService = sentenceImportService;
    }

    @GetMapping("/admin/ber-asis-tobe/items")
    @ResponseBody
    public List<BerAsisTobePair> getItems() {
        return adminService.findAll();
    }

    @PutMapping("/admin/ber-asis-tobe/items")
    @ResponseBody
    public BerAsisTobePair saveItem(
            @RequestBody BerAsisTobePair item) {
        return adminService.save(item);
    }

    @PostMapping("/admin/ber-asis-tobe/import")
    @ResponseBody
    public BerAsisTobeImportResult importExcel(
            @RequestParam("file") MultipartFile file)
            throws IOException {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "업로드할 엑셀 파일을 선택해 주세요.");
        }

        return adminService.importExcel(file.getInputStream());
    }

    @GetMapping("/admin/ber-asis-tobe/sentence-import-popup")
    public String sentenceImportPopup() {
        return "admin/berSentenceImportPopup";
    }

    @PostMapping("/admin/ber-asis-tobe/sentence-import")
    @ResponseBody
    public ResponseEntity<?> importSentenceExcel(
            @RequestParam("region") String region,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String userId = authentication == null
                ? null
                : authentication.getName();

        try{
            BerAsisTobeSentenceImportResult result =
                    sentenceImportService.importSentenceExcel(
                            region,
                            file,
                            userId);
            return ResponseEntity.ok(result);
        }catch(IllegalArgumentException exception){
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @DeleteMapping("/admin/ber-asis-tobe/items/{region}/{hash}")
    @ResponseBody
    public void deleteItem(
            @PathVariable String region,
            @PathVariable String hash) {
        adminService.delete(region, hash);
    }

    @GetMapping("/admin/ber-asis-tobe/export")
    public ResponseEntity<byte[]> exportExcel()
            throws IOException {
        byte[] excel = createWorkbook(adminService.findAll());
        String fileName = "BER_asis-tobe_DB_"
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

    private byte[] createWorkbook(List<BerAsisTobePair> items)
            throws IOException {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("BER asis-tobe DB");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("BER asis-tobe DB");
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
                    "region",
                    "hash",
                    "old_text",
                    "new_text",
                    "created_at",
                    "updated_at"
            };

            for(int column = 0; column < headers.length; column++){
                header.createCell(column).setCellValue(headers[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }

            int rowIndex = 5;
            for(int index = 0; index < items.size(); index++){
                BerAsisTobePair item = items.get(index);
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(index + 1);
                row.createCell(1).setCellValue(safe(item.getRegion()));
                row.createCell(2).setCellValue(safe(item.getHash()));
                row.createCell(3).setCellValue(safe(item.getOldText()));
                row.createCell(4).setCellValue(safe(item.getNewText()));
                row.createCell(5).setCellValue(
                        item.getCreatedAt() == null
                                ? ""
                                : item.getCreatedAt().format(DATE_TIME_FORMAT));
                row.createCell(6).setCellValue(
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
            sheet.setColumnWidth(1, 12 * 256);
            sheet.setColumnWidth(2, 72 * 256);
            sheet.setColumnWidth(3, 80 * 256);
            sheet.setColumnWidth(4, 90 * 256);
            sheet.setColumnWidth(5, 22 * 256);
            sheet.setColumnWidth(6, 22 * 256);

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
