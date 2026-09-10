package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.AutomaticNoticeRule;
import maoomWeb.ire.user.dto.AutomaticNoticeRuleImportResult;
import maoomWeb.ire.user.mapper.AutomaticNoticeRuleMapper;

class AutomaticNoticeRuleAdminServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void importsLegacyRulesWorkbookWithDefaultRegion()
            throws Exception {

        InMemoryMapper mapper = new InMemoryMapper();
        AutomaticNoticeRuleAdminService service =
                new AutomaticNoticeRuleAdminService(mapper);

        AutomaticNoticeRuleImportResult result =
                service.importExcel(
                        "KO",
                        new ByteArrayInputStream(legacyWorkbookBytes()));

        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(mapper.findByRegionTypeAndKey(
                "KO",
                "EXACT",
                "스마트키").getTeams()).isEqualTo("전자설계2팀");
    }

    @Test
    void writesRuntimeRulesWorkbookInLegacyShape()
            throws Exception {

        InMemoryMapper mapper = new InMemoryMapper();
        mapper.upsert(rule(
                "KO",
                "EXACT",
                "스마트키",
                "상세",
                "전자설계2팀",
                10,
                "Y"));
        mapper.upsert(rule(
                "KO",
                "EXACT",
                "미사용",
                "상세",
                "팀",
                20,
                "N"));

        AutomaticNoticeRuleWorkbookService workbookService =
                new AutomaticNoticeRuleWorkbookService(
                        new AutomaticNoticeRuleAdminService(mapper));
        Path output = tempDirectory.resolve("ANG_ko_rules.xlsx");

        assertThat(workbookService.writeRuntimeRulesWorkbook("KO", output))
                .isTrue();

        try(Workbook workbook = WorkbookFactory.create(output.toFile())){
            Sheet sheet = workbook.getSheet("Rules");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("A(MatchType)");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("스마트키");
        }
    }

    private byte[] legacyWorkbookBytes() throws Exception {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("Rules");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("A(MatchType)");
            header.createCell(1).setCellValue("B(Key)");
            header.createCell(2).setCellValue("C(Detail=D열)");
            header.createCell(3).setCellValue("D(Teams=E열, 줄바꿈 \\n 가능)");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("EXACT");
            row1.createCell(1).setCellValue("스마트키");
            row1.createCell(2).setCellValue("상세");
            row1.createCell(3).setCellValue("전자설계2팀");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("CONTAINS");
            row2.createCell(1).setCellValue("디지털키");
            row2.createCell(2).setCellValue("상세2");
            row2.createCell(3).setCellValue("팀A\\n팀B");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static AutomaticNoticeRule rule(
            String region,
            String matchType,
            String matchKey,
            String detail,
            String teams,
            int priority,
            String enabled) {

        AutomaticNoticeRule rule = new AutomaticNoticeRule();
        rule.setRegion(region);
        rule.setMatchType(matchType);
        rule.setMatchKey(matchKey);
        rule.setDetail(detail);
        rule.setTeams(teams);
        rule.setPriority(priority);
        rule.setEnabled(enabled);
        return rule;
    }

    private static class InMemoryMapper
            implements AutomaticNoticeRuleMapper {

        private final Map<String,AutomaticNoticeRule> rows =
                new LinkedHashMap<>();

        @Override
        public List<AutomaticNoticeRule> findByRegion(String region) {
            return rows.values()
                    .stream()
                    .filter(item -> region.equals(item.getRegion()))
                    .toList();
        }

        @Override
        public List<AutomaticNoticeRule> findEnabledByRegion(String region) {
            return rows.values()
                    .stream()
                    .filter(item -> region.equals(item.getRegion()))
                    .filter(item -> "Y".equals(item.getEnabled()))
                    .toList();
        }

        @Override
        public int countByRegion(String region) {
            return (int) findByRegion(region).size();
        }

        @Override
        public List<AutomaticNoticeRule> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public AutomaticNoticeRule findByRegionTypeAndKey(
                String region,
                String matchType,
                String matchKey) {
            return rows.get(key(region, matchType, matchKey));
        }

        @Override
        public int upsert(AutomaticNoticeRule rule) {
            rows.put(
                    key(rule.getRegion(),
                            rule.getMatchType(),
                            rule.getMatchKey()),
                    copy(rule));
            return 1;
        }

        @Override
        public int deleteByRegionTypeAndKey(
                String region,
                String matchType,
                String matchKey) {
            rows.remove(key(region, matchType, matchKey));
            return 1;
        }

        private String key(
                String region,
                String matchType,
                String matchKey) {
            return region + "\n" + matchType + "\n" + matchKey;
        }

        private AutomaticNoticeRule copy(AutomaticNoticeRule source) {
            AutomaticNoticeRule rule = new AutomaticNoticeRule();
            rule.setRegion(source.getRegion());
            rule.setMatchType(source.getMatchType());
            rule.setMatchKey(source.getMatchKey());
            rule.setDetail(source.getDetail());
            rule.setTeams(source.getTeams());
            rule.setPriority(source.getPriority());
            rule.setAliasText(source.getAliasText());
            rule.setEnabled(source.getEnabled());
            rule.setMemo(source.getMemo());
            return rule;
        }
    }
}
