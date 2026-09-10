package maoomWeb.ire.admin.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.AutomaticNoticeRule;
import maoomWeb.ire.user.dto.AutomaticNoticeRuleImportResult;
import maoomWeb.ire.user.service.AutomaticNoticeRuleAdminService;
import maoomWeb.ire.user.service.AutomaticNoticeRuleWorkbookService;

/** 관리자 페이지의 협조문 자동 완성 룰 DB API. */
@Controller
public class AdminAutomaticNoticeRuleController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AutomaticNoticeRuleAdminService adminService;
    private final AutomaticNoticeRuleWorkbookService workbookService;

    public AdminAutomaticNoticeRuleController(
            AutomaticNoticeRuleAdminService adminService,
            AutomaticNoticeRuleWorkbookService workbookService) {
        this.adminService = adminService;
        this.workbookService = workbookService;
    }

    @GetMapping("/admin/automatic-notice-rules/items")
    @ResponseBody
    public List<AutomaticNoticeRule> getItems() {
        return adminService.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DB_EDITOR')")
    @PutMapping("/admin/automatic-notice-rules/items")
    @ResponseBody
    public AutomaticNoticeRule saveItem(
            @RequestBody AutomaticNoticeRule rule) {
        return adminService.save(rule);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DB_EDITOR')")
    @PostMapping("/admin/automatic-notice-rules/import")
    @ResponseBody
    public AutomaticNoticeRuleImportResult importExcel(
            @RequestParam(value = "region", required = false)
            String region,
            @RequestParam("file") MultipartFile file)
            throws IOException {

        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "업로드할 엑셀 파일을 선택해 주세요.");
        }

        return adminService.importExcel(region, file.getInputStream());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DB_EDITOR')")
    @DeleteMapping("/admin/automatic-notice-rules/items/{region}/{matchType}/{matchKey}")
    @ResponseBody
    public void deleteItem(
            @PathVariable String region,
            @PathVariable String matchType,
            @PathVariable String matchKey) {
        adminService.delete(region, matchType, matchKey);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DB_EDITOR')")
    @DeleteMapping("/admin/automatic-notice-rules/items")
    @ResponseBody
    public void deleteItemByQuery(
            @RequestParam("region") String region,
            @RequestParam("matchType") String matchType,
            @RequestParam("matchKey") String matchKey) {
        adminService.delete(region, matchType, matchKey);
    }

    @GetMapping("/admin/automatic-notice-rules/export")
    public ResponseEntity<byte[]> exportExcel()
            throws IOException {
        byte[] excel =
                workbookService.createExportWorkbook(adminService.findAll());
        String fileName = "Automatic_Notice_Rules_"
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
}
