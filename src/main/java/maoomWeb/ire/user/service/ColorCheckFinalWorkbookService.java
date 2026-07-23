package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.model.CalculationChain;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 검토용 컬러체크 엑셀을 도안 발주 내역서 양식으로 변환한다.
 *
 * <p>양식은 사용자가 제공한 실제 발주 내역서를 템플릿으로 사용하므로
 * 기존 인쇄 설정, 셀 병합, 서식은 그대로 유지하고 데이터만 교체한다.</p>
 */
@Service
public class ColorCheckFinalWorkbookService {

    private static final String TEMPLATE_PATH =
            "excel/color-check-order-template.xlsx";
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd");
    private static final int DETAIL_FIRST_ROW = 4;
    private static final int UNIT_PRICE = 4500;
    private static final Pattern FOUR_DIGIT_YEAR =
            Pattern.compile("\\d{4}");

    /**
     * 업로드 엑셀을 읽어 최종 발주 내역서를 바탕화면/temp에 저장한다.
     */
    public Path createFinalWorkbook(
            Path sourceExcel,
            String sourceFileName) throws IOException {
        return createFinalWorkbook(
                sourceExcel,
                sourceFileName,
                Path.of(
                        System.getProperty("user.home"),
                        "Desktop",
                        "temp"));
    }

    /**
     * 테스트에서 저장 폴더를 바꿀 수 있도록 출력 경로를 받는 생성 메서드다.
     */
    public Path createFinalWorkbook(
            Path sourceExcel,
            String sourceFileName,
            Path outputDirectory) throws IOException {

        List<OrderEntry> entries = readEntries(sourceExcel);

        if(entries.isEmpty()){
            throw new IllegalArgumentException(
                    "최종 엑셀을 만들 도안 데이터가 없습니다.");
        }

        String outputVehicleName =
                extractVehicleName(sourceFileName);
        String workbookVehicleName =
                removeHtmlSuffix(outputVehicleName);
        Files.createDirectories(outputDirectory);
        Path outputFile = findAvailableFile(
                outputDirectory,
                createOutputFileName(outputVehicleName));

        ClassPathResource template =
                new ClassPathResource(TEMPLATE_PATH);

        try(InputStream templateInput = template.getInputStream();
                CleanXSSFWorkbook workbook =
                        new CleanXSSFWorkbook(templateInput);
                OutputStream output =
                        Files.newOutputStream(outputFile)){
            writeOrderDetails(
                    workbook.getSheet("작업의뢰 내역"),
                    entries,
                    workbookVehicleName);
            writeSummary(
                    workbook.getSheet("도안 발주서"),
                    entries,
                    workbookVehicleName);
            removeChapterSheet(workbook);
            removeStaleCalculationChain(workbook);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
        }

        return outputFile;
    }

    /** 최종 발주 결과에는 참조용 템플릿 챕터 시트를 포함하지 않는다. */
    private void removeChapterSheet(
            XSSFWorkbook workbook) {
        int chapterSheetIndex =
                workbook.getSheetIndex("챕터");

        if(chapterSheetIndex >= 0){
            workbook.removeSheetAt(
                    chapterSheetIndex);
        }
    }

    /**
     * 템플릿에 저장된 예전 수식 계산 순서를 제거한다.
     *
     * <p>행과 수식을 다시 작성한 뒤 calcChain.xml이 남아 있으면 Excel이
     * 유효하지 않은 계산 참조로 판단해 파일 복구 경고를 표시한다. 계산 체인은
     * 삭제하고 전체 재계산을 요청하면 Excel이 새 순서를 안전하게 만든다.</p>
     */
    private void removeStaleCalculationChain(
            CleanXSSFWorkbook workbook) {
        CalculationChain calculationChain =
                workbook.getCalculationChain();

        if(calculationChain == null){
            return;
        }

        workbook.removeCalculationChain(
                calculationChain);
    }

    private List<OrderEntry> readEntries(
            Path sourceExcel) throws IOException {

        try(InputStream input = Files.newInputStream(sourceExcel);
                Workbook workbook = WorkbookFactory.create(input)){
            ImportSheet importSheet = findImportSheet(workbook);
            DataFormatter formatter =
                    new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper()
                    .createFormulaEvaluator();
            List<OrderEntry> entries = new ArrayList<>();

            for(int rowIndex = importSheet.headerRow() + 1;
                    rowIndex <= importSheet.sheet().getLastRowNum();
                    rowIndex++){
                Row row = importSheet.sheet().getRow(rowIndex);

                if(row == null){
                    continue;
                }

                String drawingName = formatCell(
                        row,
                        importSheet.drawingNameColumn(),
                        formatter,
                        evaluator).trim();
                String colorCheck = formatCell(
                        row,
                        importSheet.colorCheckColumn(),
                        formatter,
                        evaluator).trim()
                        .toUpperCase(Locale.ROOT);

                if(drawingName.isBlank()
                        || !colorCheck.equals("V")){
                    continue;
                }

                entries.add(new OrderEntry(
                        drawingName,
                        formatCell(
                                row,
                                importSheet.chapterColumn(),
                                formatter,
                                evaluator).trim(),
                        formatCell(
                                row,
                                importSheet.chapterNumberColumn(),
                                formatter,
                                evaluator).trim()));
            }

            return entries;
        }
    }

    private ImportSheet findImportSheet(Workbook workbook) {

        for(Sheet sheet : workbook){
            for(int rowIndex = sheet.getFirstRowNum();
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++){
                Row row = sheet.getRow(rowIndex);

                if(row == null){
                    continue;
                }

                Map<String,Integer> columns =
                        findHeaderColumns(row);

                if(columns.containsKey("도안명")
                        && columns.containsKey("컬러도안")
                        && columns.containsKey("챕터")
                        && columns.containsKey("챕터번호")){
                    return new ImportSheet(
                            sheet,
                            rowIndex,
                            columns.get("도안명"),
                            columns.get("컬러도안"),
                            columns.get("챕터"),
                            columns.get("챕터번호"));
                }
            }
        }

        throw new IllegalArgumentException(
                "도안명, 컬러도안, 챕터, 챕터 번호 열이 있는 시트를 찾지 못했습니다.");
    }

    private Map<String,Integer> findHeaderColumns(Row row) {
        Map<String,Integer> columns = new LinkedHashMap<>();

        for(Cell cell : row){
            String header = cell.toString()
                    .replaceAll("\\s+", "")
                    .trim();

            if(header.equals("도안명")){
                columns.put("도안명", cell.getColumnIndex());
            }else if(header.equals("컬러도안")){
                columns.put(
                        "컬러도안",
                        cell.getColumnIndex());
            }else if(header.equals("챕터")
                    || header.equals("챕터구별")){
                columns.put("챕터", cell.getColumnIndex());
            }else if(header.equals("챕터번호")
                    || header.equals("챕터넘버")
                    || header.equals("챕터NO.")
                    || header.equals("챕터NO")){
                columns.put("챕터번호", cell.getColumnIndex());
            }
        }

        return columns;
    }

    private void writeOrderDetails(
            Sheet sheet,
            List<OrderEntry> entries,
            String vehicleName) {

        if(sheet == null){
            throw new IllegalArgumentException(
                    "템플릿에 '작업의뢰 내역' 시트가 없습니다.");
        }

        int totalRowIndex = sheet.getLastRowNum();
        int templateCapacity =
                totalRowIndex - DETAIL_FIRST_ROW;

        if(entries.size() > templateCapacity){
            int addedRows = entries.size() - templateCapacity;
            sheet.shiftRows(
                    totalRowIndex,
                    totalRowIndex,
                    addedRows,
                    true,
                    false);
            totalRowIndex += addedRows;
        }

        Row styleSource = sheet.getRow(DETAIL_FIRST_ROW);

        for(int rowIndex = DETAIL_FIRST_ROW;
                rowIndex < totalRowIndex;
                rowIndex++){
            Row row = sheet.getRow(rowIndex);

            if(row == null){
                row = sheet.createRow(rowIndex);
                copyRowStyle(styleSource, row);
            }

            clearRowValues(row, 1, 10);
        }

        for(int index = 0; index < entries.size(); index++){
            int rowIndex = DETAIL_FIRST_ROW + index;
            Row row = sheet.getRow(rowIndex);

            if(row == null){
                row = sheet.createRow(rowIndex);
                copyRowStyle(styleSource, row);
            }

            OrderEntry entry = entries.get(index);
            setCellValue(row, 1, index + 1);
            setCellValue(row, 2, entry.drawingName());
            setCellValue(
                    row,
                    3,
                    entry.chapter().isBlank()
                            ? "도안"
                            : entry.chapter());
            setCellValue(row, 4, "설명도");
            setCellValue(row, 5, UNIT_PRICE);
            setCellValue(row, 6, 1);
            setCellValue(row, 7, UNIT_PRICE);
            setCellValue(row, 8, vehicleName);
            setCellValue(row, 9, entry.chapterNumber());
            setCellValue(row, 10, "");
        }

        Row totalRow = sheet.getRow(totalRowIndex);

        if(totalRow == null){
            totalRow = sheet.createRow(totalRowIndex);
            copyRowStyle(styleSource, totalRow);
        }

        int firstExcelRow = DETAIL_FIRST_ROW + 1;
        int lastExcelRow = DETAIL_FIRST_ROW + entries.size();
        setCellValue(totalRow, 6, entries.size());
        setCellValue(totalRow, 7, entries.size() * UNIT_PRICE);

        if(entries.isEmpty()){
            setCellValue(totalRow, 6, 0);
            setCellValue(totalRow, 7, 0);
        }else{
            totalRow.getCell(
                    6,
                    Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula(
                            "SUM(G"
                            + firstExcelRow
                            + ":G"
                            + lastExcelRow
                            + ")");
            totalRow.getCell(
                    7,
                    Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula(
                            "SUM(H"
                            + firstExcelRow
                            + ":H"
                            + lastExcelRow
                            + ")");
        }

        sheet.setAutoFilter(
                new CellRangeAddress(
                        3,
                        DETAIL_FIRST_ROW + entries.size() - 1,
                        1,
                        10));
        applyDetailBorders(
                sheet,
                3,
                totalRowIndex);
    }

    /**
     * 작업의뢰 내역의 헤더부터 합계행까지 B~K 모든 셀에 동일한 테두리를 준다.
     * 기존 정렬·숫자 형식은 복제하고 테두리만 얇은 실선으로 통일한다.
     */
    private void applyDetailBorders(
            Sheet sheet,
            int firstRow,
            int lastRow) {
        Map<Short,CellStyle> borderedStyles =
                new LinkedHashMap<>();

        for(int rowIndex = firstRow;
                rowIndex <= lastRow;
                rowIndex++){
            Row row = sheet.getRow(rowIndex);

            if(row == null){
                row = sheet.createRow(rowIndex);
            }

            for(int column = 1;
                    column <= 10;
                    column++){
                Cell cell = row.getCell(
                        column,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                CellStyle originalStyle =
                        cell.getCellStyle();
                short styleIndex =
                        originalStyle.getIndex();
                CellStyle borderedStyle =
                        borderedStyles.get(styleIndex);

                if(borderedStyle == null){
                    borderedStyle =
                            sheet.getWorkbook()
                            .createCellStyle();
                    borderedStyle.cloneStyleFrom(
                            originalStyle);
                    borderedStyle.setBorderTop(
                            BorderStyle.THIN);
                    borderedStyle.setBorderRight(
                            BorderStyle.THIN);
                    borderedStyle.setBorderBottom(
                            BorderStyle.THIN);
                    borderedStyle.setBorderLeft(
                            BorderStyle.THIN);
                    borderedStyles.put(
                            styleIndex,
                            borderedStyle);
                }

                cell.setCellStyle(
                        borderedStyle);
            }
        }
    }

    private void writeSummary(
            Sheet sheet,
            List<OrderEntry> entries,
            String vehicleName) {

        if(sheet == null){
            throw new IllegalArgumentException(
                    "템플릿에 '도안 발주서' 시트가 없습니다.");
        }

        setCellValue(sheet.getRow(2), 2, vehicleName);
        Map<String,Integer> counts = new LinkedHashMap<>();

        for(OrderEntry entry : entries){
            String chapterNumber =
                    normalizeChapterNumber(
                            entry.chapterNumber());
            counts.merge(chapterNumber, 1, Integer::sum);
        }

        int maxNumericChapter = counts.keySet()
                .stream()
                .filter(value -> value.matches("[1-9][0-9]*"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(11);
        maxNumericChapter = Math.max(11, maxNumericChapter);
        List<String> groups = new ArrayList<>();
        groups.add("Q");

        for(int chapter = 1;
                chapter <= maxNumericChapter;
                chapter++){
            groups.add(String.valueOf(chapter));
        }

        int totalRowIndex = 30;
        int templateGroupCapacity =
                (totalRowIndex - 6) / 2;

        if(groups.size() > templateGroupCapacity){
            int addedRows =
                    (groups.size() - templateGroupCapacity) * 2;
            sheet.shiftRows(
                    totalRowIndex,
                    sheet.getLastRowNum(),
                    addedRows,
                    true,
                    false);
            totalRowIndex += addedRows;
        }

        ensureMergedRegion(
                sheet,
                6,
                totalRowIndex - 1,
                1,
                1);

        int totalCount = 0;
        Row descriptionStyleSource = sheet.getRow(28);
        Row compositionStyleSource = sheet.getRow(29);

        for(int index = 0; index < groups.size(); index++){
            int descriptionRowIndex = 6 + index * 2;
            int compositionRowIndex = descriptionRowIndex + 1;
            Row descriptionRow =
                    sheet.getRow(descriptionRowIndex);
            Row compositionRow =
                    sheet.getRow(compositionRowIndex);

            if(descriptionRow == null){
                descriptionRow =
                        sheet.createRow(descriptionRowIndex);
                copyRowStyle(
                        descriptionStyleSource,
                        descriptionRow);
            }

            if(compositionRow == null){
                compositionRow =
                        sheet.createRow(compositionRowIndex);
                copyRowStyle(
                        compositionStyleSource,
                        compositionRow);
            }

            int count = counts.getOrDefault(
                    groups.get(index),
                    0);
            totalCount += count;

            if(index == 0){
                setCellValue(
                        descriptionRow,
                        1,
                        vehicleName);
            }

            setCellValue(
                    descriptionRow,
                    2,
                    groups.get(index));
            ensureMergedRegion(
                    sheet,
                    descriptionRowIndex,
                    compositionRowIndex,
                    2,
                    2);
            setCellValue(
                    descriptionRow,
                    3,
                    "설명도");
            setCellValue(
                    descriptionRow,
                    4,
                    UNIT_PRICE);
            setCellValue(
                    descriptionRow,
                    5,
                    count);
            setCellValue(
                    descriptionRow,
                    6,
                    count * UNIT_PRICE);
            setCellValue(
                    compositionRow,
                    3,
                    "구성도");
            setCellValue(
                    compositionRow,
                    4,
                    UNIT_PRICE);
            setCellValue(
                    compositionRow,
                    5,
                    0);
            setCellValue(
                    compositionRow,
                    6,
                    "-");
        }

        Row totalRow = sheet.getRow(totalRowIndex);
        setCellValue(totalRow, 5, totalCount);
        setCellValue(totalRow, 6, totalCount * UNIT_PRICE);
    }

    private void ensureMergedRegion(
            Sheet sheet,
            int firstRow,
            int lastRow,
            int firstColumn,
            int lastColumn) {

        CellRangeAddress target =
                new CellRangeAddress(
                        firstRow,
                        lastRow,
                        firstColumn,
                        lastColumn);

        for(int index = sheet.getNumMergedRegions() - 1;
                index >= 0;
                index--){
            CellRangeAddress existing =
                    sheet.getMergedRegion(index);

            if(existing.formatAsString()
                    .equals(target.formatAsString())){
                return;
            }

            if(existing.intersects(target)){
                sheet.removeMergedRegion(index);
            }
        }

        sheet.addMergedRegion(target);
    }

    private String normalizeChapterNumber(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);

        if(normalized.equals("Q")
                || normalized.matches("[1-9][0-9]*")){
            return normalized;
        }

        return "1";
    }

    private String formatCell(
            Row row,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column);
        return cell == null
                ? ""
                : formatter.formatCellValue(cell, evaluator);
    }

    private void clearRowValues(
            Row row,
            int firstColumn,
            int lastColumn) {
        for(int column = firstColumn;
                column <= lastColumn;
                column++){
            row.getCell(
                    column,
                    Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setBlank();
        }
    }

    private void copyRowStyle(Row source, Row target) {

        if(source == null){
            return;
        }

        target.setHeight(source.getHeight());

        for(int column = source.getFirstCellNum();
                column < source.getLastCellNum();
                column++){
            Cell sourceCell = source.getCell(column);

            if(sourceCell == null){
                continue;
            }

            Cell targetCell = target.getCell(
                    column,
                    Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            CellStyle style = sourceCell.getCellStyle();
            targetCell.setCellStyle(style);
        }
    }

    private void setCellValue(
            Row row,
            int column,
            String value) {
        row.getCell(
                column,
                Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                .setCellValue(value == null ? "" : value);
    }

    private void setCellValue(
            Row row,
            int column,
            double value) {
        row.getCell(
                column,
                Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                .setCellValue(value);
    }

    private String extractVehicleName(String sourceFileName) {
        String baseName = sourceFileName == null
                ? "color-check"
                : sourceFileName.trim()
                        .replaceFirst("(?i)\\.xlsx?$", "")
                        .replaceFirst(
                                "(?i)_컬러체크$",
                                "");

        String parsedPdfName =
                parseVehicleNameFromPdfBaseName(baseName);

        if(parsedPdfName != null){
            return parsedPdfName;
        }

        baseName = baseName.replaceAll(
                "^\\d{6}_도안발주내역서_",
                "");
        baseName = baseName.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_");
        return baseName.isBlank()
                ? "color-check"
                : baseName;
    }

    /**
     * PDF 기본명에서 발주서용 차종명을 만든다.
     *
     * <p>예:
     * {@code 260317_KIA-SP3-ICE-HEV-en_GB-2027-OM_Full-PDF-260317-4.0_ALL}
     * -> {@code SP3_ICE-HEV_27MY_GB_HTML}</p>
     *
     * <p>파일명 앞에 날짜가 있어도 KIA-부터 읽는다. 차종 다음부터 로케일
     * 직전까지의 ICE, HEV, PHEV 같은 토큰은 구동 타입으로 보존한다.</p>
     */
    private String parseVehicleNameFromPdfBaseName(
            String baseName) {

        if(baseName == null){
            return null;
        }

        int kiaIndex = baseName.toUpperCase(
                Locale.ROOT).indexOf("KIA-");

        if(kiaIndex < 0){
            return null;
        }

        String[] tokens = baseName.substring(
                kiaIndex).split("-");
        int yearIndex = -1;

        for(int index = 3;
                index < tokens.length;
                index++){
            if(FOUR_DIGIT_YEAR.matcher(
                    tokens[index]).matches()){
                yearIndex = index;
                break;
            }
        }

        if(tokens.length < 4
                || yearIndex < 3){
            return null;
        }

        int localeStartIndex;
        String language;
        String country;
        String localeToken = tokens[yearIndex - 1];
        String[] underscoreLocaleParts =
                localeToken.split("_", -1);

        if(underscoreLocaleParts.length == 2
                && underscoreLocaleParts[0].length() == 2
                && underscoreLocaleParts[1].length() == 2){
            localeStartIndex = yearIndex - 1;
            language = underscoreLocaleParts[0];
            country = underscoreLocaleParts[1];
        }else if(yearIndex >= 2
                && tokens[yearIndex - 2].matches(
                        "(?i)[a-z]{2}")
                && localeToken.matches(
                        "(?i)[a-z]{2}")){
            localeStartIndex = yearIndex - 2;
            language = tokens[yearIndex - 2];
            country = localeToken;
        }else{
            localeStartIndex = yearIndex - 1;
            language = "";
            country = localeToken;
        }

        String[] modelParts = tokens[1].split("_");
        String model = sanitizeNamePart(modelParts[0]);
        List<String> driveTypes = new ArrayList<>();

        /*
         * KA4_PE_ICE처럼 모델 토큰 안에 PE와 구동 타입이 함께 들어오면
         * PE는 제외하고 EV/ICE/HEV/PHEV만 구동 타입으로 사용한다.
         */
        for(int index = 1;
                index < modelParts.length;
                index++){
            String modelPart =
                    sanitizeNamePart(modelParts[index]);

            if(isPowertrain(modelPart)){
                driveTypes.add(modelPart);
            }
        }

        for(int index = 2;
                index < localeStartIndex;
                index++){
            String driveType =
                    sanitizeNamePart(tokens[index]);

            if(isPowertrain(driveType)){
                driveTypes.add(driveType);
            }
        }

        String vehicle = driveTypes.isEmpty()
                ? model
                : model
                + "_"
                + String.join("-", driveTypes);
        String normalizedLanguage =
                sanitizeNamePart(language);
        String normalizedCountry =
                sanitizeNamePart(country);
        String region = normalizedLanguage.equals("KO")
                ? normalizedLanguage
                : normalizedCountry;
        region = region.equals("GB")
                ? "EG"
                : region;
        String modelYear =
                tokens[yearIndex].substring(2)
                + "MY";

        if(vehicle.isBlank()
                || region.isBlank()){
            return null;
        }

        return vehicle
                + "_"
                + modelYear
                + "_"
                + region
                + "_HTML";
    }

    private boolean isPowertrain(String value) {
        return value.equals("ICE")
                || value.equals("EV")
                || value.equals("HEV")
                || value.equals("PHEV");
    }

    private String sanitizeNamePart(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replaceAll(
                                "[^A-Za-z0-9]",
                                "")
                        .toUpperCase(Locale.ROOT);
    }

    private String createOutputFileName(String vehicleName) {
        return LocalDate.now().format(FILE_DATE_FORMAT)
                + "_도안발주내역서_"
                + vehicleName
                + ".xlsx";
    }

    private String removeHtmlSuffix(String vehicleName) {
        return vehicleName == null
                ? ""
                : vehicleName.replaceFirst(
                        "(?i)_HTML$",
                        "");
    }

    private Path findAvailableFile(
            Path outputDirectory,
            String fileName) {
        Path firstChoice = outputDirectory.resolve(fileName);

        if(!Files.exists(firstChoice)){
            return firstChoice;
        }

        String baseName = fileName.replaceFirst(
                "(?i)\\.xlsx$",
                "");

        for(int sequence = 1; ; sequence++){
            Path candidate = outputDirectory.resolve(
                    baseName
                    + " ("
                    + sequence
                    + ").xlsx");

            if(!Files.exists(candidate)){
                return candidate;
            }
        }
    }

    private record ImportSheet(
            Sheet sheet,
            int headerRow,
            int drawingNameColumn,
            int colorCheckColumn,
            int chapterColumn,
            int chapterNumberColumn) {
    }

    private record OrderEntry(
            String drawingName,
            String chapter,
            String chapterNumber) {
    }

    /**
     * POI의 보호된 관계 제거 API를 계산 체인 정리에만 제한해 노출한다.
     */
    private static final class CleanXSSFWorkbook
            extends XSSFWorkbook {

        private CleanXSSFWorkbook(
                InputStream input) throws IOException {
            super(input);
        }

        private void removeCalculationChain(
                CalculationChain calculationChain) {
            removeRelation(
                    calculationChain,
                    true);
        }
    }
}
