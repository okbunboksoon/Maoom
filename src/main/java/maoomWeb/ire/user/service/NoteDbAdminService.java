package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import jakarta.annotation.PostConstruct;
import maoomWeb.ire.user.dto.NoteDbItem;
import maoomWeb.ire.user.mapper.NoteDbItemMapper;

@Service
public class NoteDbAdminService {

    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    private final NoteDbItemMapper itemMapper;
    private final JdbcTemplate jdbcTemplate;

    public NoteDbAdminService(
            NoteDbItemMapper itemMapper,
            JdbcTemplate jdbcTemplate) {
        this.itemMapper = itemMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureNoteDbSchema() {
        addColumnIfMissing("region", "VARCHAR(10) NOT NULL DEFAULT 'EG'");
        addColumnIfMissing("note_type", "VARCHAR(30) NOT NULL DEFAULT ''");
        addColumnIfMissing("note_text", "LONGTEXT");
        alterColumnIfExists("region", "VARCHAR(10) NOT NULL DEFAULT 'EG'");
        alterColumnIfExists("new_text", "LONGTEXT NULL");
        copyLegacyTextToNoteText();
        updateInvalidRegionsToEg();
        dropIndexIfExists("uk_project_note_hash");
        addUniqueIndexIfMissing();
    }

    public List<NoteDbItem> findAll() {
        return itemMapper.findAll();
    }

    @Transactional
    public NoteDbItem save(NoteDbItem item) {
        normalizeAndValidate(item);
        itemMapper.upsert(item);
        return itemMapper.findByRegionAndHash(
                item.getRegion(),
                item.getHash());
    }

    @Transactional
    public void delete(String region, String hash) {
        String normalizedRegion = normalizeRegion(region);
        String normalizedHash = hash == null ? "" : hash.trim();
        if(normalizedHash.isBlank()){
            throw new IllegalArgumentException("삭제할 hash를 입력해 주세요.");
        }
        itemMapper.deleteByRegionAndHash(normalizedRegion, normalizedHash);
    }

    @Transactional
    public Map<String,Integer> importExcel(InputStream excelInput)
            throws IOException {
        return importExcel(null, excelInput);
    }

    @Transactional
    public Map<String,Integer> importExcel(String region, InputStream excelInput)
            throws IOException {

        if(excelInput == null){
            throw new IllegalArgumentException("엑셀 파일이 비어 있습니다.");
        }

        boolean hasSelectedRegion = region != null && !region.trim().isBlank();
        String selectedRegion = normalizeRegion(region);
        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            Sheet sheet = findImportSheet(workbook);
            HeaderColumns headers = findHeaders(sheet);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();
            Map<String,NoteDbItem> imports = new LinkedHashMap<>();
            int totalRows = 0;
            int skippedCount = 0;

            for(int rowIndex = headers.rowIndex() + 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++){
                Row row = sheet.getRow(rowIndex);
                if(row == null){
                    continue;
                }

                String hash = formatCell(
                        row,
                        headers.hashColumn(),
                        formatter,
                        evaluator).trim();
                String rowRegion = !hasSelectedRegion
                        ? headers.regionColumn() == null
                        ? "EG"
                        : formatCell(
                                row,
                                headers.regionColumn(),
                                formatter,
                                evaluator).trim()
                        : selectedRegion;
                String noteType = formatCell(
                        row,
                        headers.typeColumn(),
                        formatter,
                        evaluator).trim();
                String noteText = formatCell(
                        row,
                        headers.textColumn(),
                        formatter,
                        evaluator);

                if(hash.isBlank()
                        && noteType.isBlank()
                        && noteText.isBlank()){
                    continue;
                }

                totalRows++;

                try{
                    NoteDbItem item = new NoteDbItem();
                    item.setRegion(rowRegion);
                    item.setHash(hash);
                    item.setNoteType(noteType);
                    item.setNoteText(noteText);
                    normalizeAndValidate(item);
                    imports.put(item.getRegion() + "|" + item.getHash(), item);
                }catch(IllegalArgumentException exception){
                    skippedCount++;
                }
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 NOTE DB 데이터가 없습니다.");
            }

            ImportCounts counts = saveImportedItems(
                    new ArrayList<>(imports.values()));
            return Map.of(
                    "totalRows", totalRows,
                    "insertedCount", counts.insertedCount(),
                    "updatedCount", counts.updatedCount(),
                    "unchangedCount", counts.unchangedCount(),
                    "skippedCount", skippedCount);
        }
    }

    @Transactional
    public Map<String,Integer> importXml(String region, InputStream xmlInput)
            throws IOException {

        if(xmlInput == null){
            throw new IllegalArgumentException("XML 파일이 비어 있습니다.");
        }

        ImportCounts counts = saveImportedItems(
                parseXml(normalizeRegion(region), xmlInput));

        return Map.of(
                "totalRows", counts.totalRows(),
                "insertedCount", counts.insertedCount(),
                "updatedCount", counts.updatedCount(),
                "unchangedCount", counts.unchangedCount(),
                "skippedCount", 0);
    }

    private ImportCounts saveImportedItems(List<NoteDbItem> items) {
        int insertedCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for(NoteDbItem item : items){
            NoteDbItem oldValue = itemMapper.findByRegionAndHash(
                    item.getRegion(),
                    item.getHash());
            if(oldValue == null){
                itemMapper.upsert(item);
                insertedCount++;
            }else if(sameValue(oldValue, item)){
                unchangedCount++;
            }else{
                itemMapper.upsert(item);
                updatedCount++;
            }
        }

        return new ImportCounts(
                items.size(),
                insertedCount,
                updatedCount,
                unchangedCount);
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
                "엑셀에서 hash, type, text 헤더를 찾지 못했습니다.");
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
            Integer typeColumn = null;
            Integer textColumn = null;
            for(int column = row.getFirstCellNum();
                    column >= 0 && column < row.getLastCellNum();
                    column++){
                String header = new DataFormatter(Locale.KOREA)
                        .formatCellValue(row.getCell(column))
                        .replaceAll("\\s+", "")
                        .replace("-", "_")
                        .toLowerCase(Locale.ROOT);

                if(header.equals("region")){
                    regionColumn = column;
                }else if(header.equals("hash")){
                    hashColumn = column;
                }else if(header.equals("type")
                        || header.equals("note_type")){
                    typeColumn = column;
                }else if(header.equals("text")
                        || header.equals("note_text")){
                    textColumn = column;
                }
            }

            if(hashColumn != null
                    && typeColumn != null
                    && textColumn != null){
                return new HeaderColumns(
                        rowIndex,
                        regionColumn,
                        hashColumn,
                        typeColumn,
                        textColumn);
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

    private List<NoteDbItem> parseXml(String region, InputStream xmlInput)
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
            if(root == null || !"notes".equals(root.getTagName())){
                throw new IllegalArgumentException(
                        "note_db.xml은 <notes> 루트여야 합니다.");
            }

            NodeList notes = root.getElementsByTagName("note");
            Map<String,NoteDbItem> deduped = new LinkedHashMap<>();
            for(int index = 0; index < notes.getLength(); index++){
                Element note = (Element) notes.item(index);
                NoteDbItem item = new NoteDbItem();
                item.setRegion(region);
                item.setHash(note.getAttribute("hash"));
                item.setNoteType(note.getAttribute("type"));
                NodeList texts = note.getElementsByTagName("text");
                item.setNoteText(texts.getLength() == 0
                        ? ""
                        : texts.item(0).getTextContent());
                normalizeAndValidate(item);
                deduped.put(item.getRegion() + "|" + item.getHash(), item);
            }

            if(deduped.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 NOTE DB 데이터가 없습니다.");
            }
            return new ArrayList<>(deduped.values());
        }catch(ParserConfigurationException | SAXException exception){
            throw new IllegalArgumentException(
                    "note_db.xml을 읽지 못했습니다: "
                    + exception.getMessage(),
                    exception);
        }
    }

    private void normalizeAndValidate(NoteDbItem item) {
        if(item == null){
            throw new IllegalArgumentException("저장할 NOTE DB 항목이 없습니다.");
        }

        item.setRegion(normalizeRegion(item.getRegion()));
        item.setHash(item.getHash() == null ? "" : item.getHash().trim());
        item.setNoteType(item.getNoteType() == null
                ? ""
                : item.getNoteType().trim());
        item.setNoteText(item.getNoteText() == null ? "" : item.getNoteText());

        if(item.getHash().isBlank()){
            throw new IllegalArgumentException("hash를 입력해 주세요.");
        }
        if(item.getNoteType().isBlank()){
            throw new IllegalArgumentException("type을 입력해 주세요.");
        }
        if(item.getNoteText().isBlank()){
            throw new IllegalArgumentException("text를 입력해 주세요.");
        }
    }

    private String normalizeRegion(String region) {
        String normalized = region == null
                ? "EG"
                : region.trim().toUpperCase(Locale.ROOT);
        if(normalized.isBlank()){
            return "EG";
        }
        if(!normalized.equals("EG") && !normalized.equals("KO")){
            throw new IllegalArgumentException(
                    "NOTE DB region은 EG 또는 KO만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private boolean sameValue(NoteDbItem oldValue, NoteDbItem newValue) {
        return java.util.Objects.equals(
                oldValue.getNoteType(),
                newValue.getNoteType())
                && java.util.Objects.equals(
                        oldValue.getNoteText(),
                        newValue.getNoteText());
    }

    private void addColumnIfMissing(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_project_note_db'
                  AND column_name = ?
                """,
                Integer.class,
                columnName);
        if(count != null && count == 0){
            jdbcTemplate.execute(
                    "ALTER TABLE tb_project_note_db ADD COLUMN "
                    + columnName
                    + " "
                    + definition);
        }
    }

    private void alterColumnIfExists(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_project_note_db'
                  AND column_name = ?
                """,
                Integer.class,
                columnName);
        if(count != null && count > 0){
            jdbcTemplate.execute(
                    "ALTER TABLE tb_project_note_db MODIFY COLUMN "
                    + columnName
                    + " "
                    + definition);
        }
    }

    private void updateInvalidRegionsToEg() {
        jdbcTemplate.update(
                """
                UPDATE tb_project_note_db
                SET region = 'EG'
                WHERE region IS NULL
                   OR region = ''
                   OR region NOT IN ('EG', 'KO')
                """);
    }

    private void copyLegacyTextToNoteText() {
        if(!columnExists("new_text")){
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE tb_project_note_db
                SET note_text = new_text
                WHERE (note_text IS NULL OR note_text = '')
                  AND new_text IS NOT NULL
                  AND new_text <> ''
                """);
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_project_note_db'
                  AND column_name = ?
                """,
                Integer.class,
                columnName);
        return count != null && count > 0;
    }

    private void addUniqueIndexIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_project_note_db'
                  AND index_name = 'uk_project_note_region_hash'
                """,
                Integer.class);
        if(count != null && count == 0){
            jdbcTemplate.execute(
                    "ALTER TABLE tb_project_note_db "
                    + "ADD UNIQUE KEY uk_project_note_region_hash "
                    + "(region, hash)");
        }
    }

    private void dropIndexIfExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'tb_project_note_db'
                  AND index_name = ?
                """,
                Integer.class,
                indexName);
        if(count != null && count > 0){
            jdbcTemplate.execute(
                    "ALTER TABLE tb_project_note_db DROP INDEX "
                    + indexName);
        }
    }

    private record HeaderColumns(
            int rowIndex,
            Integer regionColumn,
            int hashColumn,
            int typeColumn,
            int textColumn) {
    }

    private record ImportCounts(
            int totalRows,
            int insertedCount,
            int updatedCount,
            int unchangedCount) {
    }
}
