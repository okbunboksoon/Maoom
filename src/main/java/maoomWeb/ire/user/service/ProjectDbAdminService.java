package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import maoomWeb.ire.user.dto.BerAsisTobeImportDetail;
import maoomWeb.ire.user.dto.BerAsisTobeImportResult;
import maoomWeb.ire.user.dto.ProjectDbItem;
import maoomWeb.ire.user.mapper.ProjectDbItemMapper;

@Service
public class ProjectDbAdminService {

    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    private final ProjectDbItemMapper itemMapper;

    public ProjectDbAdminService(ProjectDbItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    public List<ProjectDbItem> findAll(String dbType) {
        return itemMapper.findAll(tableName(dbType));
    }

    @Transactional
    public ProjectDbItem save(String dbType, ProjectDbItem item) {
        String tableName = tableName(dbType);
        normalizeAndValidate(item);
        itemMapper.upsert(tableName, item);
        return itemMapper.findByRegionAndHash(
                tableName,
                item.getRegion(),
                item.getHash());
    }

    @Transactional
    public void delete(String dbType, String region, String hash) {
        String tableName = tableName(dbType);
        String normalizedRegion = normalizeRegion(region);
        String normalizedHash = hash == null ? "" : hash.trim();

        if(normalizedHash.isBlank()){
            throw new IllegalArgumentException(
                    "삭제할 hash를 입력해 주세요.");
        }

        itemMapper.deleteByRegionAndHash(
                tableName,
                normalizedRegion,
                normalizedHash);
    }

    @Transactional
    public BerAsisTobeImportResult importPairsXml(
            String dbType,
            String region,
            InputStream xmlInput)
            throws IOException {

        String tableName = tableName(dbType);
        if(xmlInput == null){
            throw new IllegalArgumentException("XML 파일이 비어 있습니다.");
        }

        XmlImportCandidates candidates = readPairsXml(region, xmlInput);
        List<ProjectDbItem> items = candidates.items();
        List<BerAsisTobeImportDetail> details = new ArrayList<>();
        details.addAll(candidates.skippedDetails());
        int insertedCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for(int index = 0; index < items.size(); index++){
            ProjectDbItem item = items.get(index);
            ProjectDbItem oldValue = itemMapper.findByRegionAndHash(
                    tableName,
                    item.getRegion(),
                    item.getHash());

            if(oldValue == null){
                itemMapper.upsert(tableName, item);
                insertedCount++;
                details.add(new BerAsisTobeImportDetail(
                        index + 1,
                        item.getRegion(),
                        item.getHash(),
                        "신규",
                        ""));
                continue;
            }

            if(sameValue(oldValue, item)){
                unchangedCount++;
                details.add(new BerAsisTobeImportDetail(
                        index + 1,
                        item.getRegion(),
                        item.getHash(),
                        "변경 없음",
                        ""));
                continue;
            }

            itemMapper.upsert(tableName, item);
            updatedCount++;
            details.add(new BerAsisTobeImportDetail(
                    index + 1,
                    item.getRegion(),
                    item.getHash(),
                    "수정",
                    ""));
        }

        return new BerAsisTobeImportResult(
                items.size(),
                insertedCount,
                updatedCount,
                unchangedCount,
                candidates.skippedDetails().size(),
                details);
    }

    @Transactional
    public BerAsisTobeImportResult importExcel(
            String dbType,
            InputStream excelInput)
            throws IOException {
        return importExcel(dbType, null, excelInput);
    }

    @Transactional
    public BerAsisTobeImportResult importExcel(
            String dbType,
            String region,
            InputStream excelInput)
            throws IOException {

        String tableName = tableName(dbType);
        if(excelInput == null){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        boolean hasSelectedRegion = region != null && !region.trim().isBlank();
        String selectedRegion = hasSelectedRegion
                ? normalizeRegion(region)
                : "";
        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            Sheet sheet = findImportSheet(workbook, hasSelectedRegion);
            HeaderColumns headers = findHeaders(sheet, hasSelectedRegion);
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

                String rowRegion = hasSelectedRegion
                        ? selectedRegion
                        : formatCell(row, headers.regionColumn(),
                                formatter, evaluator).trim();
                String hash = formatCell(row, headers.hashColumn(),
                        formatter, evaluator).trim();
                String oldText = formatCell(row, headers.oldTextColumn(),
                        formatter, evaluator);
                String newText = formatCell(row, headers.newTextColumn(),
                        formatter, evaluator);
                if(rowRegion.isBlank()
                        && hash.isBlank()
                        && oldText.isBlank()
                        && newText.isBlank()){
                    continue;
                }

                totalRows++;

                try{
                    ProjectDbItem item = new ProjectDbItem();
                    item.setRegion(rowRegion);
                    item.setHash(hash);
                    item.setOldText(oldText);
                    item.setNewText(newText);
                    normalizeAndValidate(item);

                    String key = item.getRegion() + "\n" + item.getHash();
                    if(imports.containsKey(key)){
                        skippedCount++;
                        ImportCandidate previous = imports.get(key);
                        details.add(new BerAsisTobeImportDetail(
                                previous.excelRowNumber(),
                                previous.item().getRegion(),
                                previous.item().getHash(),
                                "제외",
                                "같은 region/hash가 이후 행에 다시 있어 마지막 값을 사용했습니다."));
                    }

                    imports.put(key, new ImportCandidate(rowIndex + 1, item));
                }catch(IllegalArgumentException error){
                    skippedCount++;
                    details.add(new BerAsisTobeImportDetail(
                            rowIndex + 1,
                            rowRegion,
                            hash,
                            "제외",
                            error.getMessage()));
                }
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 프로젝트 DB 데이터가 없습니다.");
            }

            int insertedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for(ImportCandidate candidate : imports.values()){
                ProjectDbItem item = candidate.item();
                ProjectDbItem oldValue = itemMapper.findByRegionAndHash(
                        tableName,
                        item.getRegion(),
                        item.getHash());

                if(oldValue == null){
                    itemMapper.upsert(tableName, item);
                    insertedCount++;
                    details.add(detail(candidate, "신규", ""));
                    continue;
                }

                if(sameValue(oldValue, item)){
                    unchangedCount++;
                    details.add(detail(candidate, "변경 없음", ""));
                    continue;
                }

                itemMapper.upsert(tableName, item);
                updatedCount++;
                details.add(detail(candidate, "수정", ""));
            }

            details.sort(java.util.Comparator.comparingInt(
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

    public String displayName(String dbType) {
        if("text".equals(dbType)){
            return "TEXT DB";
        }
        if("note".equals(dbType)){
            return "NOTE DB";
        }
        throw new IllegalArgumentException("지원하지 않는 프로젝트 DB입니다.");
    }

    private String tableName(String dbType) {
        if("text".equals(dbType)){
            return "tb_project_text_db";
        }
        if("note".equals(dbType)){
            return "tb_project_note_db";
        }
        throw new IllegalArgumentException("지원하지 않는 프로젝트 DB입니다.");
    }

    private XmlImportCandidates readPairsXml(
            String region,
            InputStream xmlInput)
            throws IOException {

        try{
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(xmlInput);
            Element root = document.getDocumentElement();
            if(root == null || !"pairs".equals(root.getTagName())){
                throw new IllegalArgumentException(
                        "asis-tobe XML은 <pairs> 루트여야 합니다.");
            }

            String normalizedRegion = normalizeRegion(region);
            NodeList pairs = root.getElementsByTagName("pair");
            Map<String,ProjectDbItem> deduped = new LinkedHashMap<>();
            List<BerAsisTobeImportDetail> skippedDetails = new ArrayList<>();
            for(int index = 0; index < pairs.getLength(); index++){
                Element pair = (Element) pairs.item(index);
                ProjectDbItem item = new ProjectDbItem();
                item.setRegion(normalizedRegion);
                item.setHash(pair.getAttribute("hash"));
                item.setOldText(textOf(pair, "old", 0));

                NodeList newNodes = pair.getElementsByTagName("new");
                item.setNewText(newNodes.getLength() == 0
                        ? ""
                        : newNodes.item(newNodes.getLength() - 1)
                                .getTextContent());
                try{
                    normalizeAndValidate(item);
                    deduped.put(
                            item.getRegion() + "\n" + item.getHash(),
                            item);
                }catch(IllegalArgumentException exception){
                    skippedDetails.add(new BerAsisTobeImportDetail(
                            index + 1,
                            item.getRegion(),
                            item.getHash(),
                            "제외",
                            exception.getMessage()));
                }
            }

            if(deduped.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 TEXT DB 데이터가 없습니다.");
            }
            return new XmlImportCandidates(
                    new ArrayList<>(deduped.values()),
                    skippedDetails);
        }catch(ParserConfigurationException | SAXException exception){
            throw new IllegalArgumentException(
                    "asis-tobe XML을 읽지 못했습니다: "
                    + exception.getMessage(),
                    exception);
        }
    }

    private String textOf(Element parent, String tagName, int index) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if(nodes.getLength() <= index){
            return "";
        }
        return nodes.item(index).getTextContent();
    }

    private Sheet findImportSheet(Workbook workbook) {
        return findImportSheet(workbook, false);
    }

    private Sheet findImportSheet(
            Workbook workbook,
            boolean regionFromRequest) {
        for(Sheet sheet : workbook){
            try{
                findHeaders(sheet, regionFromRequest);
                return sheet;
            }catch(IllegalArgumentException ignored){
            }
        }
        throw new IllegalArgumentException(
                "엑셀에서 region, hash, old_text, new_text 헤더를 찾지 못했습니다.");
    }

    private HeaderColumns findHeaders(Sheet sheet, boolean regionFromRequest) {
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
                    column >= 0 && column < row.getLastCellNum();
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

            if((regionFromRequest || regionColumn != null)
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

    private String formatCell(Row row, int column,
            DataFormatter formatter, FormulaEvaluator evaluator) {
        return formatter.formatCellValue(row.getCell(column), evaluator);
    }

    private void normalizeAndValidate(ProjectDbItem item) {
        if(item == null){
            throw new IllegalArgumentException("저장할 프로젝트 DB 항목이 없습니다.");
        }

        item.setRegion(normalizeRegion(item.getRegion()));
        item.setHash(item.getHash() == null ? "" : item.getHash().trim());
        item.setOldText(item.getOldText() == null ? "" : item.getOldText());
        item.setNewText(item.getNewText() == null ? "" : item.getNewText());

        if(item.getHash().isBlank()){
            throw new IllegalArgumentException("hash를 입력해 주세요.");
        }
        if(item.getNewText().isBlank()){
            throw new IllegalArgumentException("new_text를 입력해 주세요.");
        }
    }

    private String normalizeRegion(String region) {
        String normalized = region == null
                ? ""
                : region.trim().toUpperCase(Locale.ROOT);

        if(!normalized.equals("EG")
                && !normalized.equals("KO")
                && !normalized.equals("EU")
                && !normalized.equals("EU_RG")
                && !normalized.equals("US")){
            throw new IllegalArgumentException(
                    "region은 EG, KO, EU, EU_RG 또는 US만 사용할 수 있습니다.");
        }

        return normalized;
    }

    private boolean sameValue(ProjectDbItem oldValue, ProjectDbItem newValue) {
        return Objects.equals(oldValue.getOldText(), newValue.getOldText())
                && Objects.equals(oldValue.getNewText(), newValue.getNewText());
    }

    private BerAsisTobeImportDetail detail(
            ImportCandidate candidate,
            String status,
            String note) {
        return new BerAsisTobeImportDetail(
                candidate.excelRowNumber(),
                candidate.item().getRegion(),
                candidate.item().getHash(),
                status,
                note);
    }

    private record HeaderColumns(int rowIndex, Integer regionColumn,
            int hashColumn, int oldTextColumn, int newTextColumn) {
    }

    private record ImportCandidate(int excelRowNumber, ProjectDbItem item) {
    }

    private record XmlImportCandidates(
            List<ProjectDbItem> items,
            List<BerAsisTobeImportDetail> skippedDetails) {
    }
}
