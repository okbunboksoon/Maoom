package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.AutomaticNoticeRule;

/** DB 룰을 기존 ANGServiceImpl이 읽을 수 있는 Rules 엑셀로 만든다. */
@Service
public class AutomaticNoticeRuleWorkbookService {

    private final AutomaticNoticeRuleAdminService ruleAdminService;

    public AutomaticNoticeRuleWorkbookService(
            AutomaticNoticeRuleAdminService ruleAdminService) {
        this.ruleAdminService = ruleAdminService;
    }

    public boolean writeRuntimeRulesWorkbook(
            String region,
            Path destination) throws IOException {

        List<AutomaticNoticeRule> rules =
                ruleAdminService.findEnabledByRegion(region);
        if(rules.isEmpty()){
            return false;
        }

        writeWorkbook(rules, destination);
        return true;
    }

    public byte[] createExportWorkbook(List<AutomaticNoticeRule> rules)
            throws IOException {
        try(Workbook workbook = new XSSFWorkbook();
                java.io.ByteArrayOutputStream output =
                        new java.io.ByteArrayOutputStream()){
            fillSheet(workbook, rules, true);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeWorkbook(
            List<AutomaticNoticeRule> rules,
            Path destination) throws IOException {

        Files.createDirectories(destination.getParent());
        try(Workbook workbook = new XSSFWorkbook();
                OutputStream output = Files.newOutputStream(destination)){
            fillSheet(workbook, rules, false);
            workbook.write(output);
        }
    }

    private void fillSheet(
            Workbook workbook,
            List<AutomaticNoticeRule> rules,
            boolean includeAdminColumns) {

        Sheet sheet = workbook.createSheet("Rules");
        Row header = sheet.createRow(0);
        String[] headers = includeAdminColumns
                ? new String[]{
                        "region",
                        "match_type",
                        "match_key",
                        "detail",
                        "teams",
                        "priority",
                        "alias_text",
                        "enabled",
                        "memo"
                }
                : new String[]{
                        "A(MatchType)",
                        "B(Key)",
                        "C(Detail=D열)",
                        "D(Teams=E열, 줄바꿈 \\n 가능)"
                };

        for(int i = 0; i < headers.length; i++){
            header.createCell(i).setCellValue(headers[i]);
        }

        int rowIndex = 1;
        for(AutomaticNoticeRule rule : rules){
            Row row = sheet.createRow(rowIndex++);
            if(includeAdminColumns){
                row.createCell(0).setCellValue(safe(rule.getRegion()));
                row.createCell(1).setCellValue(safe(rule.getMatchType()));
                row.createCell(2).setCellValue(safe(rule.getMatchKey()));
                row.createCell(3).setCellValue(safe(rule.getDetail()));
                row.createCell(4).setCellValue(safe(rule.getTeams()));
                row.createCell(5).setCellValue(rule.getPriority() == null
                        ? 100
                        : rule.getPriority());
                row.createCell(6).setCellValue(safe(rule.getAliasText()));
                row.createCell(7).setCellValue(safe(rule.getEnabled()));
                row.createCell(8).setCellValue(safe(rule.getMemo()));
            }else{
                row.createCell(0).setCellValue(safe(rule.getMatchType()));
                row.createCell(1).setCellValue(safe(rule.getMatchKey()));
                row.createCell(2).setCellValue(safe(rule.getDetail()));
                row.createCell(3).setCellValue(
                        safe(rule.getTeams()).replace("\n", "\\n"));
            }
        }

        int width = includeAdminColumns ? 9 : 4;
        for(int i = 0; i < width; i++){
            sheet.setColumnWidth(i, 24 * 256);
        }
        sheet.setColumnWidth(includeAdminColumns ? 2 : 1, 50 * 256);
        sheet.setColumnWidth(includeAdminColumns ? 3 : 2, 80 * 256);
        sheet.setColumnWidth(includeAdminColumns ? 4 : 3, 60 * 256);
        sheet.createFreezePane(0, 1);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
