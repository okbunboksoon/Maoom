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

import maoomWeb.ire.user.dto.BerAsisTobeImportDetail;
import maoomWeb.ire.user.dto.BerAsisTobeImportResult;
import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

@Service
public class BerAsisTobeAdminService {

    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    private final BerAsisTobePairMapper pairMapper;

    public BerAsisTobeAdminService(BerAsisTobePairMapper pairMapper) {
        this.pairMapper = pairMapper;
    }

    public List<BerAsisTobePair> findAll() {
        return pairMapper.findAll();
    }

    @Transactional
    public BerAsisTobePair save(BerAsisTobePair pair) {
        normalizeAndValidate(pair);
        pairMapper.upsert(pair);
        return pairMapper.findByRegionAndHash(
                pair.getRegion(),
                pair.getHash());
    }

    @Transactional
    public void delete(String region, String hash) {
        String normalizedRegion = normalizeRegion(region);
        String normalizedHash = hash == null ? "" : hash.trim();

        if(normalizedHash.isBlank()){
            throw new IllegalArgumentException(
                    "삭제할 hash를 입력해 주세요.");
        }

        pairMapper.deleteByRegionAndHash(
                normalizedRegion,
                normalizedHash);
    }

    @Transactional
    public BerAsisTobeImportResult importExcel(InputStream excelInput)
            throws IOException {

        if(excelInput == null){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            Sheet sheet = findImportSheet(workbook);
            HeaderColumns headers = findHeaders(sheet);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();
            Map<String,ImportCandidate> imports = new LinkedHashMap<>();
            List<BerAsisTobeImportDetail> details = new ArrayList<>();
            int totalRows = 0;
            int skippedCount = 0;

            for(int rowIndex = headers.rowIndex() + 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++){
                Row row = sheet.getRow(rowIndex);

                if(row == null){
                    continue;
                }

                String region = formatCell(
                        row,
                        headers.regionColumn(),
                        formatter,
                        evaluator).trim();
                String hash = formatCell(
                        row,
                        headers.hashColumn(),
                        formatter,
                        evaluator).trim();
                String oldText = formatCell(
                        row,
                        headers.oldTextColumn(),
                        formatter,
                        evaluator);
                String newText = formatCell(
                        row,
                        headers.newTextColumn(),
                        formatter,
                        evaluator);
                if(region.isBlank()
                        && hash.isBlank()
                        && oldText.isBlank()
                        && newText.isBlank()){
                    continue;
                }

                totalRows++;

                try{
                    BerAsisTobePair pair = new BerAsisTobePair();
                    pair.setRegion(region);
                    pair.setHash(hash);
                    pair.setOldText(oldText);
                    pair.setNewText(newText);
                    normalizeAndValidate(pair);

                    String key = pair.getRegion()
                            + "\n"
                            + pair.getHash();

                    if(imports.containsKey(key)){
                        skippedCount++;
                        ImportCandidate previous = imports.get(key);
                        details.add(new BerAsisTobeImportDetail(
                                previous.excelRowNumber(),
                                previous.pair().getRegion(),
                                previous.pair().getHash(),
                                "제외",
                                "같은 region/hash가 이후 행에 다시 있어 마지막 값을 사용했습니다."));
                    }

                    imports.put(
                            key,
                            new ImportCandidate(rowIndex + 1, pair));
                }catch(IllegalArgumentException error){
                    skippedCount++;
                    details.add(new BerAsisTobeImportDetail(
                            rowIndex + 1,
                            region,
                            hash,
                            "제외",
                            error.getMessage()));
                }
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 BER asis-tobe 데이터가 없습니다.");
            }

            int insertedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for(ImportCandidate candidate : imports.values()){
                BerAsisTobePair pair = candidate.pair();
                BerAsisTobePair oldValue =
                        pairMapper.findByRegionAndHash(
                                pair.getRegion(),
                                pair.getHash());

                if(oldValue == null){
                    pairMapper.upsert(pair);
                    insertedCount++;
                    details.add(detail(candidate, "신규", ""));
                    continue;
                }

                if(sameValue(oldValue, pair)){
                    unchangedCount++;
                    details.add(detail(candidate, "변경 없음", ""));
                    continue;
                }

                pairMapper.upsert(pair);
                updatedCount++;
                details.add(detail(candidate, "수정", ""));
            }

            details.sort(
                    java.util.Comparator.comparingInt(
                            BerAsisTobeImportDetail::excelRowNumber));

            return new BerAsisTobeImportResult(
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
                "엑셀에서 region, hash, old_text, new_text 헤더를 찾지 못했습니다.");
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

            Integer regionColumn = null;
            Integer hashColumn = null;
            Integer oldTextColumn = null;
            Integer newTextColumn = null;
            for(int column = row.getFirstCellNum();
                    column >= 0
                    && column < row.getLastCellNum();
                    column++){
                String header = new DataFormatter(Locale.KOREA)
                        .formatCellValue(row.getCell(column))
                        .replaceAll("\\s+", "")
                        .replace("-", "_")
                        .toLowerCase(Locale.ROOT);

                if(header.equals("region") || header.equals("지역")){
                    regionColumn = column;
                }else if(header.equals("hash")){
                    hashColumn = column;
                }else if(header.equals("old_text")
                        || header.equals("old")
                        || header.equals("asis")
                        || header.equals("as_is")){
                    oldTextColumn = column;
                }else if(header.equals("new_text")
                        || header.equals("new")
                        || header.equals("tobe")
                        || header.equals("to_be")){
                    newTextColumn = column;
                }
            }

            if(regionColumn != null
                    && hashColumn != null
                    && oldTextColumn != null
                    && newTextColumn != null){
                return new HeaderColumns(
                        rowIndex,
                        regionColumn,
                        hashColumn,
                        oldTextColumn,
                        newTextColumn);
            }
        }

        throw new IllegalArgumentException("필수 헤더가 없습니다.");
    }

    private String formatCell(
            Row row,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        return formatter.formatCellValue(row.getCell(column), evaluator);
    }

    private void normalizeAndValidate(BerAsisTobePair pair) {
        if(pair == null){
            throw new IllegalArgumentException(
                    "저장할 BER asis-tobe 항목이 없습니다.");
        }

        pair.setRegion(normalizeRegion(pair.getRegion()));
        pair.setHash(pair.getHash() == null ? "" : pair.getHash().trim());
        pair.setOldText(pair.getOldText() == null ? "" : pair.getOldText());
        pair.setNewText(pair.getNewText() == null ? "" : pair.getNewText());

        if(pair.getHash().isBlank()){
            throw new IllegalArgumentException("hash를 입력해 주세요.");
        }
        if(pair.getNewText().isBlank()){
            throw new IllegalArgumentException("new_text를 입력해 주세요.");
        }
    }

    private String normalizeRegion(String region) {
        String normalized = region == null
                ? ""
                : region.trim().toUpperCase(Locale.ROOT);

        if(!normalized.equals("EU") && !normalized.equals("US")){
            throw new IllegalArgumentException(
                    "region은 EU 또는 US만 사용할 수 있습니다.");
        }

        return normalized;
    }

    private boolean sameValue(
            BerAsisTobePair oldValue,
            BerAsisTobePair newValue) {

        return Objects.equals(oldValue.getOldText(), newValue.getOldText())
                && Objects.equals(oldValue.getNewText(), newValue.getNewText());
    }

    private BerAsisTobeImportDetail detail(
            ImportCandidate candidate,
            String status,
            String note) {

        return new BerAsisTobeImportDetail(
                candidate.excelRowNumber(),
                candidate.pair().getRegion(),
                candidate.pair().getHash(),
                status,
                note);
    }

    private record HeaderColumns(
            int rowIndex,
            int regionColumn,
            int hashColumn,
            int oldTextColumn,
            int newTextColumn) {
    }

    private record ImportCandidate(
            int excelRowNumber,
            BerAsisTobePair pair) {
    }
}
