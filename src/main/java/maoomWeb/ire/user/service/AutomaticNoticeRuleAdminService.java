package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import maoomWeb.ire.user.dto.AutomaticNoticeRule;
import maoomWeb.ire.user.dto.AutomaticNoticeRuleImportDetail;
import maoomWeb.ire.user.dto.AutomaticNoticeRuleImportResult;
import maoomWeb.ire.user.mapper.AutomaticNoticeRuleMapper;

/** 자동 고지문 생성에 쓰는 지역·유형별 규칙을 검증하고 엑셀로 일괄 등록하는 서비스다. */
@Service
public class AutomaticNoticeRuleAdminService {

    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    private final AutomaticNoticeRuleMapper ruleMapper;

    public AutomaticNoticeRuleAdminService(
            AutomaticNoticeRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    public List<AutomaticNoticeRule> findAll() {
        return ruleMapper.findAll();
    }

    public List<AutomaticNoticeRule> findEnabledByRegion(String region) {
        return ruleMapper.findEnabledByRegion(normalizeRegion(region));
    }

    @Transactional
    public AutomaticNoticeRule save(AutomaticNoticeRule rule) {
        normalizeAndValidate(rule);
        ruleMapper.upsert(rule);
        return ruleMapper.findByRegionTypeAndKey(
                rule.getRegion(),
                rule.getMatchType(),
                rule.getMatchKey());
    }

    @Transactional
    public void delete(
            String region,
            String matchType,
            String matchKey) {

        String normalizedRegion = normalizeRegion(region);
        String normalizedType = normalizeMatchType(matchType);
        String normalizedKey = matchKey == null ? "" : matchKey.trim();

        if(normalizedKey.isBlank()){
            throw new IllegalArgumentException(
                    "삭제할 match_key를 입력해 주세요.");
        }

        ruleMapper.deleteByRegionTypeAndKey(
                normalizedRegion,
                normalizedType,
                normalizedKey);
    }

    @Transactional
    public AutomaticNoticeRuleImportResult importExcel(
            String defaultRegion,
            InputStream excelInput) throws IOException {

        if(excelInput == null){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        String fallbackRegion = defaultRegion == null
                || defaultRegion.isBlank()
                ? null
                : normalizeRegion(defaultRegion);

        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            Sheet sheet = findImportSheet(workbook);
            HeaderColumns headers = findHeaders(sheet);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();
            Map<String,ImportCandidate> imports = new LinkedHashMap<>();
            List<AutomaticNoticeRuleImportDetail> details =
                    new ArrayList<>();
            int totalRows = 0;
            int skippedCount = 0;

            for(int rowIndex = headers.rowIndex() + 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++){
                Row row = sheet.getRow(rowIndex);
                if(row == null){
                    continue;
                }

                String region = headers.regionColumn() == null
                        ? fallbackRegion
                        : formatCell(
                                row,
                                headers.regionColumn(),
                                formatter,
                                evaluator).trim();
                String type = formatCell(
                        row,
                        headers.matchTypeColumn(),
                        formatter,
                        evaluator).trim();
                String key = formatCell(
                        row,
                        headers.matchKeyColumn(),
                        formatter,
                        evaluator).trim();
                String detail = formatCell(
                        row,
                        headers.detailColumn(),
                        formatter,
                        evaluator);
                String teams = formatCell(
                        row,
                        headers.teamsColumn(),
                        formatter,
                        evaluator).replace("\\n", "\n");
                String priority = headers.priorityColumn() == null
                        ? ""
                        : formatCell(
                                row,
                                headers.priorityColumn(),
                                formatter,
                                evaluator).trim();
                String alias = headers.aliasColumn() == null
                        ? ""
                        : formatCell(
                                row,
                                headers.aliasColumn(),
                                formatter,
                                evaluator);
                String enabled = headers.enabledColumn() == null
                        ? "Y"
                        : formatCell(
                                row,
                                headers.enabledColumn(),
                                formatter,
                                evaluator).trim();
                String memo = headers.memoColumn() == null
                        ? ""
                        : formatCell(
                                row,
                                headers.memoColumn(),
                                formatter,
                                evaluator);

                if(isBlank(region)
                        && type.isBlank()
                        && key.isBlank()
                        && detail.isBlank()
                        && teams.isBlank()){
                    continue;
                }

                totalRows++;

                try{
                    AutomaticNoticeRule rule = new AutomaticNoticeRule();
                    rule.setRegion(region);
                    rule.setMatchType(type);
                    rule.setMatchKey(key);
                    rule.setDetail(detail);
                    rule.setTeams(teams);
                    rule.setPriority(parsePriority(priority));
                    rule.setAliasText(alias);
                    rule.setEnabled(enabled);
                    rule.setMemo(memo);
                    normalizeAndValidate(rule);

                    String mapKey = rule.getRegion()
                            + "\n"
                            + rule.getMatchType()
                            + "\n"
                            + rule.getMatchKey();
                    if(imports.containsKey(mapKey)){
                        skippedCount++;
                        ImportCandidate previous = imports.get(mapKey);
                        details.add(new AutomaticNoticeRuleImportDetail(
                                previous.excelRowNumber(),
                                previous.rule().getRegion(),
                                previous.rule().getMatchType(),
                                previous.rule().getMatchKey(),
                                "제외",
                                "같은 region/match_type/match_key가 이후 행에 다시 있어 마지막 값을 사용했습니다."));
                    }

                    imports.put(
                            mapKey,
                            new ImportCandidate(rowIndex + 1, rule));
                }catch(IllegalArgumentException error){
                    skippedCount++;
                    details.add(new AutomaticNoticeRuleImportDetail(
                            rowIndex + 1,
                            region,
                            type,
                            key,
                            "제외",
                            error.getMessage()));
                }
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 협조문 룰 데이터가 없습니다.");
            }

            int insertedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for(ImportCandidate candidate : imports.values()){
                AutomaticNoticeRule rule = candidate.rule();
                AutomaticNoticeRule oldValue =
                        ruleMapper.findByRegionTypeAndKey(
                                rule.getRegion(),
                                rule.getMatchType(),
                                rule.getMatchKey());

                if(oldValue == null){
                    ruleMapper.upsert(rule);
                    insertedCount++;
                    details.add(detail(candidate, "신규", ""));
                    continue;
                }

                if(sameValue(oldValue, rule)){
                    unchangedCount++;
                    details.add(detail(candidate, "변경 없음", ""));
                    continue;
                }

                ruleMapper.upsert(rule);
                updatedCount++;
                details.add(detail(candidate, "수정", ""));
            }

            details.sort(
                    java.util.Comparator.comparingInt(
                            AutomaticNoticeRuleImportDetail::excelRowNumber));

            return new AutomaticNoticeRuleImportResult(
                    totalRows,
                    insertedCount,
                    updatedCount,
                    unchangedCount,
                    skippedCount,
                    details);
        }
    }

    private Sheet findImportSheet(Workbook workbook) {
        for(Sheet sheet : workbook){
            try{
                findHeaders(sheet);
                return sheet;
            }catch(IllegalArgumentException ignored){
            }
        }

        throw new IllegalArgumentException(
                "엑셀에서 match_type, match_key, detail, teams 헤더를 찾지 못했습니다.");
    }

    private HeaderColumns findHeaders(Sheet sheet) {
        int lastSearchRow = Math.min(
                sheet.getLastRowNum(),
                HEADER_SEARCH_ROW_LIMIT - 1);

        for(int rowIndex = sheet.getFirstRowNum();
                rowIndex <= lastSearchRow;
                rowIndex++){
            Row row = sheet.getRow(rowIndex);
            if(row == null){
                continue;
            }

            Integer region = null;
            Integer matchType = null;
            Integer matchKey = null;
            Integer detail = null;
            Integer teams = null;
            Integer priority = null;
            Integer alias = null;
            Integer enabled = null;
            Integer memo = null;

            for(int column = row.getFirstCellNum();
                    column >= 0 && column < row.getLastCellNum();
                    column++){
                String header = new DataFormatter(Locale.KOREA)
                        .formatCellValue(row.getCell(column))
                        .replaceAll("\\s+", "")
                        .replace("-", "_")
                        .toLowerCase(Locale.ROOT);

                if(header.equals("region") || header.equals("market")
                        || header.equals("지역") || header.equals("시장")){
                    region = column;
                }else if(header.equals("match_type")
                        || header.equals("matchtype")
                        || header.equals("a(matchtype)")
                        || header.equals("type")){
                    matchType = column;
                }else if(header.equals("match_key")
                        || header.equals("matchkey")
                        || header.equals("b(key)")
                        || header.equals("key")){
                    matchKey = column;
                }else if(header.equals("detail")
                        || header.equals("c(detail=d열)")
                        || header.equals("세부검토사항")){
                    detail = column;
                }else if(header.equals("teams")
                        || header.equals("team")
                        || header.equals("d(teams=e열,줄바꿈\\n가능)")
                        || header.equals("설계팀")){
                    teams = column;
                }else if(header.equals("priority") || header.equals("우선순위")){
                    priority = column;
                }else if(header.equals("alias") || header.equals("alias_text")
                        || header.equals("별칭")){
                    alias = column;
                }else if(header.equals("enabled") || header.equals("사용여부")){
                    enabled = column;
                }else if(header.equals("memo") || header.equals("메모")){
                    memo = column;
                }
            }

            if(matchType != null
                    && matchKey != null
                    && detail != null
                    && teams != null){
                return new HeaderColumns(
                        rowIndex,
                        region,
                        matchType,
                        matchKey,
                        detail,
                        teams,
                        priority,
                        alias,
                        enabled,
                        memo);
            }
        }

        throw new IllegalArgumentException("필수 헤더가 없습니다.");
    }

    private void normalizeAndValidate(AutomaticNoticeRule rule) {
        if(rule == null){
            throw new IllegalArgumentException(
                    "저장할 협조문 룰이 없습니다.");
        }

        rule.setRegion(normalizeRegion(rule.getRegion()));
        rule.setMatchType(normalizeMatchType(rule.getMatchType()));
        rule.setMatchKey(rule.getMatchKey() == null
                ? ""
                : rule.getMatchKey().trim());
        rule.setDetail(rule.getDetail() == null ? "" : rule.getDetail());
        rule.setTeams(rule.getTeams() == null ? "" : rule.getTeams());
        rule.setPriority(rule.getPriority() == null
                ? 100
                : rule.getPriority());
        rule.setAliasText(rule.getAliasText() == null
                ? ""
                : rule.getAliasText());
        rule.setEnabled(normalizeEnabled(rule.getEnabled()));
        rule.setMemo(rule.getMemo() == null ? "" : rule.getMemo());

        if(rule.getMatchKey().isBlank()){
            throw new IllegalArgumentException(
                    "match_key를 입력해 주세요.");
        }
    }

    private String normalizeRegion(String region) {
        String normalized = region == null
                ? ""
                : region.trim().toUpperCase(Locale.ROOT);

        if(!normalized.equals("KO")
                && !normalized.equals("US")
                && !normalized.equals("EG")){
            throw new IllegalArgumentException(
                    "region은 KO, US 또는 EG만 사용할 수 있습니다.");
        }

        return normalized;
    }

    private String normalizeMatchType(String matchType) {
        String normalized = matchType == null
                ? ""
                : matchType.trim().toUpperCase(Locale.ROOT);

        if(!normalized.equals("EXACT")
                && !normalized.equals("CONTAINS")
                && !normalized.equals("REGEX")){
            throw new IllegalArgumentException(
                    "match_type은 EXACT, CONTAINS 또는 REGEX만 사용할 수 있습니다.");
        }

        return normalized;
    }

    private String normalizeEnabled(String enabled) {
        String normalized = enabled == null
                ? "Y"
                : enabled.trim().toUpperCase(Locale.ROOT);

        if(normalized.equals("TRUE") || normalized.equals("1")
                || normalized.equals("사용") || normalized.equals("Y")){
            return "Y";
        }
        if(normalized.equals("FALSE") || normalized.equals("0")
                || normalized.equals("미사용") || normalized.equals("N")){
            return "N";
        }

        throw new IllegalArgumentException(
                "enabled는 Y 또는 N만 사용할 수 있습니다.");
    }

    private Integer parsePriority(String priority) {
        if(priority == null || priority.isBlank()){
            return 100;
        }
        try{
            return Integer.parseInt(priority.replaceAll("\\.0$", ""));
        }catch(NumberFormatException exception){
            throw new IllegalArgumentException(
                    "priority는 숫자만 사용할 수 있습니다.");
        }
    }

    private String formatCell(
            Row row,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        return formatter.formatCellValue(row.getCell(column), evaluator);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean sameValue(
            AutomaticNoticeRule oldValue,
            AutomaticNoticeRule newValue) {

        return Objects.equals(oldValue.getDetail(), newValue.getDetail())
                && Objects.equals(oldValue.getTeams(), newValue.getTeams())
                && Objects.equals(oldValue.getPriority(), newValue.getPriority())
                && Objects.equals(oldValue.getAliasText(), newValue.getAliasText())
                && Objects.equals(oldValue.getEnabled(), newValue.getEnabled())
                && Objects.equals(oldValue.getMemo(), newValue.getMemo());
    }

    private AutomaticNoticeRuleImportDetail detail(
            ImportCandidate candidate,
            String status,
            String note) {

        return new AutomaticNoticeRuleImportDetail(
                candidate.excelRowNumber(),
                candidate.rule().getRegion(),
                candidate.rule().getMatchType(),
                candidate.rule().getMatchKey(),
                status,
                note);
    }

    private record HeaderColumns(
            int rowIndex,
            Integer regionColumn,
            int matchTypeColumn,
            int matchKeyColumn,
            int detailColumn,
            int teamsColumn,
            Integer priorityColumn,
            Integer aliasColumn,
            Integer enabledColumn,
            Integer memoColumn) {
    }

    private record ImportCandidate(
            int excelRowNumber,
            AutomaticNoticeRule rule) {
    }
}
