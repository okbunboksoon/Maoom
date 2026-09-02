package maoomWeb.ire.user.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.AutoCountingAnalyzeResult;

@Service
public class AutoCountingService {

    private static final String[] APPENDIX_ONLY_KEYS = {
            "appendix",
            "annex",
            "부록"
    };
    private static final String[] ABBREV_KEYS = {
            "abbreviation",
            "약어"
    };
    private static final String[] INDEX_KEYS = {
            "index",
            "색인"
    };
    private static final String FIRST_EDITION_TEMPLATE =
            "auto-counting/first-edition-template.xlsx";
    private static final int MAX_INPUT_ROWS = 1500;
    private static final String[] FIRST_EDITION_CHAPTERS = {
            "1장", "2장", "3장", "4장", "5장", "6장", "7장",
            "8장", "9장", "10장", "11장", "12장", "13장",
            "Appendix", "Abbreviation", "Index"
    };

    public AutoCountingAnalyzeResult analyzePdf(
            MultipartFile file,
            String mode,
            boolean pack32) throws IOException {

        validatePdf(file);
        byte[] pdfBytes = file.getBytes();
        boolean realPageMode = "page".equalsIgnoreCase(mode);

        try(PDDocument document = Loader.loadPDF(pdfBytes)){
            List<ChapterRange> ranges = countByBookmarks(document);
            Map<String, Integer> chapters = realPageMode
                    ? countRealPages(document, ranges)
                    : countFirstEditionPages(ranges);

            int totalPages = document.getNumberOfPages();
            int nonBlankPages = countNonBlankPages(document, 1, totalPages);
            int unit = pack32 ? 32 : 8;
            int fullPages = ((totalPages + unit - 1) / unit) * unit;

            return new AutoCountingAnalyzeResult(
                    "PDF 분석 완료",
                    file.getOriginalFilename(),
                    totalPages,
                    nonBlankPages,
                    fullPages,
                    chapters);
        }
    }

    public WorkbookFile createFirstEditionWorkbook(
            MultipartFile file,
            Map<String, String> fields) throws IOException {

        validatePdf(file);
        boolean fm = parseBoolean(fields.get("fm"));
        boolean pack32 = parseBoolean(fields.get("pack32"));
        boolean newTemplate = parseBoolean(fields.get("newTemplate"));

        ClassPathResource template = new ClassPathResource(FIRST_EDITION_TEMPLATE);
        if(!template.exists()){
            throw new IllegalStateException("초판 배열표 템플릿을 찾을 수 없습니다.");
        }

        try(InputStream templateInput = template.getInputStream();
                Workbook workbook = new XSSFWorkbook(templateInput);
                ByteArrayOutputStream output = new ByteArrayOutputStream()){

            Sheet sheet = workbook.getSheetAt(0);
            Sheet input = findInputSheet(workbook);
            if(input == null){
                throw new IllegalStateException("Input 시트를 찾을 수 없습니다.");
            }

            fillInputSheet(
                    input,
                    file.getBytes(),
                    fields,
                    fm,
                    pack32,
                    newTemplate);
            fillLabelValues(sheet, fields);
            if(workbook instanceof XSSFWorkbook xssfWorkbook){
                xssfWorkbook.setForceFormulaRecalculation(true);
            }

            workbook.write(output);
            return new WorkbookFile(
                    buildFirstEditionFileName(
                            fields.get("model"),
                            fields.get("language"),
                            fields.get("pubNumber")),
                    output.toByteArray());
        }
    }

    private void fillInputSheet(
            Sheet input,
            byte[] pdfBytes,
            Map<String, String> fields,
            boolean fm,
            boolean pack32,
            boolean newTemplate) throws IOException {

        int rowA;
        int pageNo;

        if(newTemplate){
            Set<Integer> blankPages;
            int totalPages;
            try(PDDocument document = Loader.loadPDF(pdfBytes)){
                blankPages = findBlankPageIndexes(document);
                totalPages = document.getNumberOfPages();
            }

            rowA = 0;
            pageNo = 1;
            for(int pageIndex = 0; pageIndex < totalPages && rowA < MAX_INPUT_ROWS; pageIndex++){
                if(blankPages.contains(pageIndex)){
                    getOrCreateCell(input, rowA++, 0).setCellValue("BLANK");
                }else{
                    Cell cell = getOrCreateCell(input, rowA++, 0);
                    if(fm){
                        cell.setCellValue("F" + pageNo);
                    }else{
                        cell.setCellValue(pageNo);
                    }
                    pageNo++;
                }
            }
        }else{
            int prefacePages = parseInt(fields.get("chapter_서문"));
            String[] fixed = fixedPrefaceValues(prefacePages, fm);
            for(int i = 0; i < fixed.length; i++){
                getOrCreateCell(input, i, 0).setCellValue(fixed[i]);
            }
            rowA = 6;
            pageNo = 7;

            if(fm){
                for(int ch = 0; ch < FIRST_EDITION_CHAPTERS.length; ch++){
                    String key = FIRST_EDITION_CHAPTERS[ch];
                    int pages = parseInt(fields.get("chapter_" + key));
                    String prefix = firstEditionPrefix(key, ch);
                    for(int page = 1; page <= pages && rowA < MAX_INPUT_ROWS; page++){
                        getOrCreateCell(input, rowA++, 0).setCellValue(prefix + "-" + page);
                    }
                    if(pages % 2 == 1 && rowA < MAX_INPUT_ROWS){
                        getOrCreateCell(input, rowA++, 0).setCellValue("BLANK");
                    }
                    if(rowA >= MAX_INPUT_ROWS){
                        break;
                    }
                }
            }else{
                for(String key : FIRST_EDITION_CHAPTERS){
                    int pages = parseInt(fields.get("chapter_" + key));
                    for(int i = 0; i < pages && rowA < MAX_INPUT_ROWS; i++){
                        getOrCreateCell(input, rowA++, 0).setCellValue(pageNo++);
                    }
                    if(pages % 2 == 1 && rowA < MAX_INPUT_ROWS){
                        getOrCreateCell(input, rowA++, 0).setCellValue("BLANK");
                        pageNo++;
                    }
                    if(rowA >= MAX_INPUT_ROWS){
                        break;
                    }
                }
            }
        }

        int unit = pack32 ? 32 : 8;
        int mod = rowA % unit;
        if(mod != 0){
            int pad = unit - mod;
            for(int i = 0; i < pad && rowA < MAX_INPUT_ROWS; i++){
                getOrCreateCell(input, rowA++, 0).setCellValue("MEMO");
            }
        }
        while(rowA < MAX_INPUT_ROWS){
            getOrCreateCell(input, rowA++, 0).setBlank();
        }
    }

    private Sheet findInputSheet(
            Workbook workbook) {

        Sheet input = workbook.getSheet("Input");
        if(input == null
                && workbook.getNumberOfSheets() > 1
                && "Input".equalsIgnoreCase(workbook.getSheetName(1))){
            input = workbook.getSheetAt(1);
        }
        return input;
    }

    private void fillLabelValues(
            Sheet sheet,
            Map<String, String> fields) {

        Map<String, String> labelToValue = new LinkedHashMap<>();
        labelToValue.put("Vehicle", fields.get("vehicle"));
        labelToValue.put("Model", fields.get("model"));
        labelToValue.put("Language", fields.get("language"));
        labelToValue.put("Pub. Number", fields.get("pubNumber"));
        labelToValue.put("Date", fields.get("date"));
        labelToValue.put("Quantity", fields.get("quantity"));
        labelToValue.put("Revised version", fields.get("revised"));
        labelToValue.put("Full page", fields.get("fullPage"));
        labelToValue.put("Change page", fields.get("changePage"));
        labelToValue.put("barcode", fields.get("barcode"));
        labelToValue.put("Translation line", fields.get("translation"));

        for(Map.Entry<String, String> entry : labelToValue.entrySet()){
            int[] cellPosition = findCellByLabel(sheet, entry.getKey());
            if(cellPosition != null){
                setTextOrNumber(
                        sheet,
                        cellPosition[0],
                        cellPosition[1] + 1,
                        entry.getValue());
            }
        }
    }

    private Set<Integer> findBlankPageIndexes(
            PDDocument document) throws IOException {

        Set<Integer> blankPages = new java.util.LinkedHashSet<>();
        for(int page = 1; page <= document.getNumberOfPages(); page++){
            if(isPageBlank(document, page)){
                blankPages.add(page - 1);
            }
        }
        return blankPages;
    }

    private String[] fixedPrefaceValues(
            int prefacePages,
            boolean fm) {

        if(fm){
            return prefacePages == 4
                    ? new String[] {"F1", "F2", "F3", "BLANK", "F5", "BLANK"}
                    : new String[] {"F1", "BLANK", "F3", "BLANK", "F5", "BLANK"};
        }
        return prefacePages == 4
                ? new String[] {"1", "2", "3", "BLANK", "5", "BLANK"}
                : new String[] {"1", "BLANK", "3", "BLANK", "5", "BLANK"};
    }

    private String firstEditionPrefix(
            String key,
            int chapterIndex) {

        if("Index".equals(key)){
            return "I";
        }
        if("Appendix".equals(key) || "Abbreviation".equals(key)){
            return "A";
        }
        return String.valueOf(chapterIndex + 1);
    }

    private Cell getOrCreateCell(
            Sheet sheet,
            int rowIndex,
            int colIndex) {

        Row row = sheet.getRow(rowIndex);
        if(row == null){
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(colIndex);
        if(cell == null){
            cell = row.createCell(colIndex);
        }
        return cell;
    }

    private void setTextOrNumber(
            Sheet sheet,
            int rowIndex,
            int colIndex,
            String text) {

        Cell cell = getOrCreateCell(sheet, rowIndex, colIndex);
        if(cell.getCellType() == CellType.FORMULA){
            return;
        }
        if(text == null || text.trim().isEmpty()){
            cell.setBlank();
            return;
        }
        try{
            cell.setCellValue(Double.parseDouble(text.replace(",", "")));
        }catch(NumberFormatException exception){
            cell.setCellValue(text);
        }
    }

    private int[] findCellByLabel(
            Sheet sheet,
            String label) {

        if(sheet == null || label == null){
            return null;
        }
        String target = label.trim().toLowerCase(Locale.ROOT);
        int lastRow = sheet.getLastRowNum();
        for(int rowIndex = 0; rowIndex <= lastRow; rowIndex++){
            Row row = sheet.getRow(rowIndex);
            if(row == null){
                continue;
            }
            int lastCell = Math.max(0, row.getLastCellNum());
            for(int colIndex = 0; colIndex < lastCell; colIndex++){
                String value = getCellString(row.getCell(colIndex));
                if(value != null
                        && value.trim().toLowerCase(Locale.ROOT).equals(target)){
                    return new int[] {rowIndex, colIndex};
                }
            }
        }
        return null;
    }

    private String getCellString(
            Cell cell) {

        if(cell == null){
            return null;
        }
        if(cell.getCellType() == CellType.STRING){
            return cell.getStringCellValue();
        }
        if(cell.getCellType() == CellType.NUMERIC){
            return String.valueOf(cell.getNumericCellValue());
        }
        if(cell.getCellType() == CellType.BOOLEAN){
            return String.valueOf(cell.getBooleanCellValue());
        }
        return null;
    }

    private String buildFirstEditionFileName(
            String model,
            String language,
            String pubNumber) {

        String baseName = String.join(
                "_",
                sanitizeFileToken(model),
                sanitizeFileToken(language),
                sanitizeFileToken(pubNumber))
                .replaceAll("^_+|_+$", "");
        if(baseName.isBlank()){
            baseName = "AutoCountingSwing";
        }
        return baseName + "-Table_Array.xlsx";
    }

    private String sanitizeFileToken(
            String value) {

        if(value == null){
            return "";
        }
        return value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
    }

    private int parseInt(
            String value) {

        if(value == null || value.isBlank()){
            return 0;
        }
        try{
            return Integer.parseInt(value.trim());
        }catch(NumberFormatException exception){
            return 0;
        }
    }

    private boolean parseBoolean(
            String value) {

        return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }

    private void validatePdf(MultipartFile file) {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("PDF 파일을 선택해 주세요.");
        }

        String originalName = file.getOriginalFilename();
        if(originalName == null
                || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")){
            throw new IllegalArgumentException("PDF 파일만 선택할 수 있습니다.");
        }
    }

    private Map<String, Integer> countFirstEditionPages(
            List<ChapterRange> ranges) {

        Map<String, Integer> result = createChapterMap();
        for(ChapterRange range : ranges){
            String key = convertFirstEditionKey(range.title());
            if(key != null && result.containsKey(key)){
                result.put(key, range.pageCount());
            }
        }
        return result;
    }

    private Map<String, Integer> countRealPages(
            PDDocument document,
            List<ChapterRange> ranges) throws IOException {

        Map<String, Integer> result = createChapterMap();
        for(ChapterRange range : ranges){
            String key = convertRealPageKey(range.title());
            if(key == null || !result.containsKey(key)){
                continue;
            }

            int pageCount = range.pageCount();
            if(pageCount > 0 && isPageBlank(document, range.endPage())){
                pageCount--;
            }
            result.put(key, Math.max(0, pageCount));
        }
        return result;
    }

    private List<ChapterRange> countByBookmarks(
            PDDocument document) throws IOException {

        PDDocumentOutline outline = document.getDocumentCatalog()
                .getDocumentOutline();
        if(outline == null){
            return Collections.emptyList();
        }

        List<PDOutlineItem> level0 = siblings(outline.getFirstChild());
        List<PDOutlineItem> level1 = childrenOf(level0);
        List<PDOutlineItem> level2 = childrenOf(level1);

        List<PDOutlineItem> chosen = level0;
        int chosenScore = levelScore(document, level0);
        int score1 = levelScore(document, level1);
        int score2 = levelScore(document, level2);
        if(score1 > chosenScore){
            chosen = level1;
            chosenScore = score1;
        }
        if(score2 > chosenScore){
            chosen = level2;
        }

        List<BookmarkStart> starts = new ArrayList<>();
        for(PDOutlineItem item : chosen){
            int pageIndex = pageIndexOf(document, item);
            if(pageIndex >= 0){
                starts.add(new BookmarkStart(item.getTitle(), pageIndex));
            }
        }
        if(starts.isEmpty()){
            return Collections.emptyList();
        }

        starts.sort(Comparator.comparingInt(BookmarkStart::pageIndex));
        int total = document.getNumberOfPages();
        List<ChapterRange> raw = new ArrayList<>();

        int firstStart = starts.get(0).pageIndex();
        if(firstStart > 0){
            raw.add(new ChapterRange("서문/목차", 1, firstStart));
        }

        for(int i = 0; i < starts.size(); i++){
            BookmarkStart current = starts.get(i);
            int endExclusive0 = i == starts.size() - 1
                    ? total
                    : starts.get(i + 1).pageIndex();
            raw.add(new ChapterRange(
                    current.title(),
                    current.pageIndex() + 1,
                    endExclusive0));
        }

        return mergeSpecialRanges(raw);
    }

    private List<ChapterRange> mergeSpecialRanges(
            List<ChapterRange> raw) {

        List<ChapterRange> result = new ArrayList<>();
        for(int i = 0; i < raw.size(); ){
            ChapterRange range = raw.get(i);

            if(i == 0 && "서문/목차".equals(range.title())){
                result.add(range);
                i++;
                continue;
            }

            if(isAppendixOnly(range.title())){
                int start = range.startPage();
                int end = range.endPage();
                int j = i + 1;
                while(j < raw.size() && isAppendixOnly(raw.get(j).title())){
                    end = raw.get(j).endPage();
                    j++;
                }
                result.add(new ChapterRange("Appendix", start, end));
                i = j;
                continue;
            }

            if(isAbbreviation(range.title())){
                int start = range.startPage();
                int end = range.endPage();
                int j = i + 1;
                while(j < raw.size() && isAbbreviation(raw.get(j).title())){
                    end = raw.get(j).endPage();
                    j++;
                }
                result.add(new ChapterRange("Abbreviation", start, end));
                i = j;
                continue;
            }

            result.add(range);
            i++;
        }
        return result;
    }

    private List<PDOutlineItem> siblings(PDOutlineItem first) {
        List<PDOutlineItem> items = new ArrayList<>();
        PDOutlineItem current = first;
        while(current != null){
            items.add(current);
            current = current.getNextSibling();
        }
        return items;
    }

    private List<PDOutlineItem> childrenOf(List<PDOutlineItem> parents) {
        List<PDOutlineItem> items = new ArrayList<>();
        for(PDOutlineItem parent : parents){
            PDOutlineItem child = parent.getFirstChild();
            while(child != null){
                items.add(child);
                child = child.getNextSibling();
            }
        }
        return items;
    }

    private int levelScore(
            PDDocument document,
            List<PDOutlineItem> items) throws IOException {

        int numeric = 0;
        int special = 0;
        int total = 0;
        for(PDOutlineItem item : items){
            if(pageIndexOf(document, item) < 0){
                continue;
            }
            total++;
            String title = safeLower(item.getTitle());
            if(title.matches("^\\s*\\d{1,2}\\b.*")
                    || title.matches("^\\s*chapter\\s*\\d{1,2}\\b.*")){
                numeric++;
            }
            if(containsAny(title, APPENDIX_ONLY_KEYS)
                    || containsAny(title, ABBREV_KEYS)
                    || containsAny(title, INDEX_KEYS)){
                special++;
            }
        }
        return numeric * 100 + special * 10 + total;
    }

    private int pageIndexOf(
            PDDocument document,
            PDOutlineItem item) throws IOException {

        PDPage page = null;
        PDDestination destination = item.getDestination();
        if(destination instanceof PDPageDestination pageDestination){
            page = pageDestination.getPage();
            if(page == null && pageDestination.getPageNumber() >= 0){
                return pageDestination.getPageNumber();
            }
        }

        if(page == null && item.getAction() instanceof PDActionGoTo goTo){
            PDDestination actionDestination = goTo.getDestination();
            if(actionDestination instanceof PDPageDestination pageDestination){
                page = pageDestination.getPage();
                if(page == null && pageDestination.getPageNumber() >= 0){
                    return pageDestination.getPageNumber();
                }
            }
        }

        if(page == null){
            page = item.findDestinationPage(document);
        }
        return page == null ? -1 : document.getPages().indexOf(page);
    }

    private int countNonBlankPages(
            PDDocument document,
            int startPage,
            int endPage) throws IOException {

        int count = 0;
        for(int page = startPage; page <= endPage; page++){
            if(!isPageBlank(document, page)){
                count++;
            }
        }
        return count;
    }

    private boolean isPageBlank(
            PDDocument document,
            int page) throws IOException {

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(document);
        if(text == null){
            return true;
        }

        String cleaned = text
                .replaceAll("[\\u0000-\\u001F]", "")
                .replaceAll("[\\u2000-\\u200F]", "")
                .replaceAll("[\\u00A0]", "")
                .replaceAll("[\\u00AD]", "")
                .replaceAll("\\s+", "")
                .trim();
        return cleaned.isEmpty();
    }

    private Map<String, Integer> createChapterMap() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("서문", 0);
        for(int i = 1; i <= 13; i++){
            result.put(i + "장", 0);
        }
        result.put("Appendix", 0);
        result.put("Abbreviation", 0);
        result.put("Index", 0);
        return result;
    }

    private String convertFirstEditionKey(String title) {
        String lower = safeLower(title).trim();
        if(lower.equals("서문/목차")){
            return "서문";
        }
        if(lower.matches("^\\d+.*")){
            return lower.replaceAll("^(\\d+).*", "$1") + "장";
        }
        if(lower.contains("appendix")){
            return "Appendix";
        }
        if(lower.contains("abbreviation")){
            return "Abbreviation";
        }
        if(lower.contains("index") || lower.contains("색인")){
            return "Index";
        }
        return null;
    }

    private String convertRealPageKey(String title) {
        String lower = safeLower(title).trim();
        if(lower.equals("서문/목차")){
            return "서문";
        }
        if(lower.matches("^a\\s+.+")
                || lower.equals("abbreviation")
                || lower.contains("приложение")){
            return "Abbreviation";
        }
        if(lower.matches("^\\d+.*")){
            return lower.replaceAll("^(\\d+).*", "$1") + "장";
        }
        if(lower.matches("^i\\s+.+")){
            return "Index";
        }
        if(lower.contains("appendix")){
            return "Appendix";
        }
        return null;
    }

    private boolean isAppendixOnly(String title) {
        return containsAny(safeLower(title), APPENDIX_ONLY_KEYS);
    }

    private boolean isAbbreviation(String title) {
        return containsAny(safeLower(title), ABBREV_KEYS);
    }

    private boolean containsAny(String haystack, String[] needles) {
        String source = safeLower(haystack);
        String sourceNoAccents = stripDiacritics(source);
        for(String needle : needles){
            String target = safeLower(needle);
            String targetNoAccents = stripDiacritics(target);
            if(source.contains(target)
                    || sourceNoAccents.contains(targetNoAccents)){
                return true;
            }
        }
        return false;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String stripDiacritics(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private record BookmarkStart(
            String title,
            int pageIndex) {
    }

    public record WorkbookFile(
            String fileName,
            byte[] content) {
    }

    private record ChapterRange(
            String title,
            int startPage,
            int endPage) {

        int pageCount() {
            return endPage - startPage + 1;
        }
    }
}
