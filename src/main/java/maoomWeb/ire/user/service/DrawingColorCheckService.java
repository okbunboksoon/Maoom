package maoomWeb.ire.user.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import maoomWeb.ire.user.dto.DrawingColorCheckImportDetail;
import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;
import maoomWeb.ire.user.mapper.DrawingColorCheckMapper;

/**
 * 검토 엑셀의 '컬러도안' 값을 읽어 DB에 저장하는 서비스.
 *
 * <p>화면의 "DB 반영" 버튼이 호출하는 실제 업무 규칙이 이 클래스에 있다.</p>
 * <ul>
 *   <li>도안명이 있고 컬러도안이 V이면 V로 저장한다.</li>
 *   <li>도안명이 있고 컬러도안이 X이면 X로 저장한다.</li>
 *   <li>도안명이 있고 컬러도안이 공백이면 X로 저장한다.</li>
 *   <li>V/X 이외의 값 또는 도안명이 없는 행은 제외한다.</li>
 *   <li>DB에 없는 도안은 추가하고, 있는 도안은 값이 다를 때만 수정한다.</li>
 * </ul>
 *
 * <p>실제 SQL 실행은 {@link DrawingColorCheckMapper}가 담당한다.</p>
 */
@Service
public class DrawingColorCheckService {

    /** 엑셀 상단 20행 안에서 '도안명', '컬러도안' 헤더를 찾는다. */
    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    /** MyBatis XML과 연결되어 DB 조회/저장을 수행하는 인터페이스. */
    private final DrawingColorCheckMapper colorCheckMapper;

    public DrawingColorCheckService(
            DrawingColorCheckMapper colorCheckMapper) {
        this.colorCheckMapper = colorCheckMapper;
    }

    /** DB에 저장된 도안별 V/X 목록을 최신 등록 순으로 반환한다. */
    public List<DrawingColorCheckDto> findAll() {
        return colorCheckMapper.findAll();
    }

    /**
     * 기존 작업 엑셀의 도안명과 컬러도안 V/X 값을 DB에 일괄 반영한다.
     *
     * <p>헤더 위치를 고정 숫자로 가정하지 않고 실제 헤더 이름을 찾아서
     * 읽기 때문에, 제목 행이 위에 추가되어도 사용할 수 있다.</p>
     *
     * @param excelBytes 사용자가 선택한 XLS 또는 XLSX 파일 내용
     * @return 전체/신규/수정/동일/제외 건수를 담은 처리 결과
     */
    @Transactional
    public DrawingColorCheckImportResult importExcel(
            byte[] excelBytes) throws IOException {

        if(excelBytes == null || excelBytes.length == 0){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        return importExcel(new ByteArrayInputStream(excelBytes));
    }

    /**
     * 큰 검토 엑셀을 불필요한 byte 배열 복사 없이 읽는 실제 가져오기 메서드.
     * 컨트롤러는 MultipartFile의 입력 스트림을 이 메서드에 바로 전달한다.
     *
     * @param excelInput 업로드된 XLS 또는 XLSX 파일 스트림
     * @return 전체/신규/수정/동일/제외 건수를 담은 처리 결과
     */
    @Transactional
    public DrawingColorCheckImportResult importExcel(
            InputStream excelInput) throws IOException {

        if(excelInput == null){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            // 필요한 두 헤더가 들어 있는 첫 번째 시트를 작업 대상으로 선택한다.
            Sheet sheet = findImportSheet(workbook);
            HeaderColumns headers = findHeaders(sheet);

            // DataFormatter를 사용하면 문자, 숫자, 수식 셀을 화면에 보이는 값으로 읽는다.
            DataFormatter formatter = new DataFormatter(
                    Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper()
                    .createFormulaEvaluator();
            /*
             * 같은 도안명이 여러 번 나오면 마지막 행 값을 사용한다.
             * LinkedHashMap은 입력 순서를 유지해 결과를 예측하기 쉽게 한다.
             */
            Map<String,ImportCandidate> imports =
                    new LinkedHashMap<>();
            List<DrawingColorCheckImportDetail> details =
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

                String drawingName = formatCell(
                        row,
                        headers.drawingNameColumn(),
                        formatter,
                        evaluator).trim();
                String inputValue = formatCell(
                        row,
                        headers.colorCheckColumn(),
                        formatter,
                        evaluator)
                        .trim()
                        .toUpperCase(Locale.ROOT);

                if(drawingName.isBlank()
                        && inputValue.isBlank()){
                    continue;
                }

                totalRows++;

                if(drawingName.isBlank()){
                    skippedCount++;
                    details.add(new DrawingColorCheckImportDetail(
                            rowIndex + 1,
                            "",
                            inputValue,
                            "",
                            "",
                            "제외",
                            "도안명이 없습니다."));
                    continue;
                }

                String checkValue = inputValue;
                String normalizationNote = "";

                // 업무 규칙: 도안명이 있는데 컬러도안이 비어 있으면 X로 본다.
                if(checkValue.isBlank()){
                    checkValue = "X";
                    normalizationNote =
                            "컬러도안 공백을 X로 처리했습니다.";
                }else if(!checkValue.equals("V")
                        && !checkValue.equals("X")){
                    skippedCount++;
                    details.add(new DrawingColorCheckImportDetail(
                            rowIndex + 1,
                            drawingName,
                            inputValue,
                            "",
                            "",
                            "제외",
                            "컬러도안 값은 V, X 또는 공백만 사용할 수 있습니다."));
                    continue;
                }

                DrawingColorCheckDto item =
                        new DrawingColorCheckDto();
                item.setDrawingName(drawingName);
                item.setCheckValue(checkValue);
                String key = canonicalDrawingName(drawingName);
                ImportCandidate candidate =
                        new ImportCandidate(
                                rowIndex + 1,
                                inputValue,
                                normalizationNote,
                                item);

                // 중복 도안명은 앞 행을 제외 처리하고 마지막 유효 행을 DB 반영 대상으로 삼는다.
                if(imports.containsKey(key)){
                    skippedCount++;
                    ImportCandidate previous =
                            imports.get(key);
                    details.add(new DrawingColorCheckImportDetail(
                            previous.excelRowNumber(),
                            previous.item().getDrawingName(),
                            previous.inputValue(),
                            previous.item().getCheckValue(),
                            "",
                            "제외",
                            "같은 도안명이 이후 행에 다시 있어 마지막 값을 사용했습니다."));
                }

                imports.put(key, candidate);
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 V/X 도안 데이터가 없습니다.");
            }

            // 엑셀 도안명을 한 번에 조회해 신규/수정 여부를 판정한다.
            Map<String,DrawingColorCheckDto> existing =
                    colorCheckMapper.findByDrawingNames(
                            imports.values()
                            .stream()
                            .map(ImportCandidate::item)
                            .map(DrawingColorCheckDto::getDrawingName)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(
                            item -> canonicalDrawingName(
                                    item.getDrawingName()),
                            Function.identity(),
                            (left, right) -> left));
            int insertedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for(Map.Entry<String,ImportCandidate> entry
                    : imports.entrySet()){
                DrawingColorCheckDto oldValue =
                        existing.get(entry.getKey());
                ImportCandidate candidate =
                        entry.getValue();
                DrawingColorCheckDto newValue =
                        candidate.item();
                String previousValue =
                        oldValue == null
                        ? ""
                        : safeCheckValue(oldValue.getCheckValue());
                String note =
                        candidate.normalizationNote();

                // DB에 없으면 INSERT 대상이다.
                if(oldValue == null){
                    colorCheckMapper.upsert(newValue);
                    insertedCount++;
                    details.add(createDetail(
                            candidate,
                            previousValue,
                            "신규",
                            note));
                    continue;
                }

                // 이미 같은 값이면 불필요한 UPDATE를 실행하지 않는다.
                if(oldValue.getCheckValue() != null
                        && oldValue.getCheckValue()
                        .equalsIgnoreCase(
                                newValue.getCheckValue())){
                    unchangedCount++;
                    details.add(createDetail(
                            candidate,
                            previousValue,
                            "변경 없음",
                            note));
                    continue;
                }

                colorCheckMapper.upsert(newValue);
                updatedCount++;
                details.add(createDetail(
                        candidate,
                        previousValue,
                        "수정",
                        note));
            }

            details.sort(
                    java.util.Comparator.comparingInt(
                            DrawingColorCheckImportDetail::excelRowNumber));

            return new DrawingColorCheckImportResult(
                    totalRows,
                    insertedCount,
                    updatedCount,
                    unchangedCount,
                    skippedCount,
                    details);
        }
    }

    /**
     * API로 도안 한 건을 직접 저장할 때 사용하는 메서드.
     * 엑셀 일괄 등록과 달리 이 API는 V 또는 X를 반드시 전달해야 한다.
     */
    @Transactional
    public DrawingColorCheckDto save(
            DrawingColorCheckDto colorCheck) {

        if(colorCheck == null
                || colorCheck.getDrawingName() == null
                || colorCheck.getDrawingName().isBlank()){
            throw new IllegalArgumentException(
                    "도안명을 입력해 주세요.");
        }

        String checkValue = colorCheck.getCheckValue() == null
                ? ""
                : colorCheck.getCheckValue()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if(!checkValue.equals("V")
                && !checkValue.equals("X")){
            throw new IllegalArgumentException(
                    "체크값은 V 또는 X만 사용할 수 있습니다.");
        }

        colorCheck.setDrawingName(
                colorCheck.getDrawingName()
                .trim());
        colorCheck.setCheckValue(checkValue);
        colorCheckMapper.upsert(colorCheck);
        return colorCheck;
    }

    /** 관리자 화면에서 도안 한 건을 삭제한다. */
    @Transactional
    public void delete(String drawingName) {
        if(drawingName == null || drawingName.isBlank()){
            throw new IllegalArgumentException(
                    "삭제할 도안명을 입력해 주세요.");
        }

        colorCheckMapper.deleteByDrawingName(drawingName.trim());
    }

    /** 여러 시트 중 필수 헤더가 있는 첫 번째 시트를 찾는다. */
    private Sheet findImportSheet(Workbook workbook) {

        for(Sheet sheet : workbook){
            try{
                findHeaders(sheet);
                return sheet;
            }catch(IllegalArgumentException ignored){
                // 필요한 헤더가 있는 다음 시트를 확인한다.
            }
        }

        throw new IllegalArgumentException(
                "엑셀에서 '도안명'과 '컬러도안' 헤더를 찾지 못했습니다.");
    }

    /**
     * 시트 상단에서 '도안명'과 '컬러도안'이 있는 행과 열 번호를 찾는다.
     * 헤더의 공백은 제거하므로 "컬러 도안"처럼 띄어써도 비교할 수 있다.
     */
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

            Integer drawingNameColumn = null;
            Integer colorCheckColumn = null;

            for(int column = row.getFirstCellNum();
                    column >= 0
                    && column < row.getLastCellNum();
                    column++){
                String header = new DataFormatter(
                        Locale.KOREA)
                        .formatCellValue(row.getCell(column))
                        .replaceAll("\\s+", "");

                if(header.equals("도안명")){
                    drawingNameColumn = column;
                }else if(header.equals("컬러도안")){
                    colorCheckColumn = column;
                }
            }

            if(drawingNameColumn != null
                    && colorCheckColumn != null){
                return new HeaderColumns(
                        rowIndex,
                        drawingNameColumn,
                        colorCheckColumn);
            }
        }

        throw new IllegalArgumentException(
                "필수 헤더가 없습니다.");
    }

    /** 셀 종류와 관계없이 사용자가 엑셀에서 보는 문자열 값으로 변환한다. */
    private String formatCell(
            Row row,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        return formatter.formatCellValue(
                row.getCell(column),
                evaluator);
    }

    /** 대소문자와 앞뒤 공백을 무시하기 위한 도안명 비교 키. */
    private String canonicalDrawingName(String drawingName) {
        return drawingName.trim().toUpperCase(Locale.ROOT);
    }

    /** DB 반영 대상 행을 상세 리포트 한 행으로 변환한다. */
    private DrawingColorCheckImportDetail createDetail(
            ImportCandidate candidate,
            String previousValue,
            String status,
            String note) {
        return new DrawingColorCheckImportDetail(
                candidate.excelRowNumber(),
                candidate.item().getDrawingName(),
                candidate.inputValue(),
                candidate.item().getCheckValue(),
                previousValue,
                status,
                note);
    }

    private String safeCheckValue(String value) {
        return value == null ? "" : value;
    }

    /** 찾은 헤더 행과 두 필수 열의 위치를 함께 보관하는 내부 자료형. */
    private record HeaderColumns(
            int rowIndex,
            int drawingNameColumn,
            int colorCheckColumn) {
    }

    /** 중복 판정 후 실제 DB 반영 대상으로 남은 Excel 행 정보다. */
    private record ImportCandidate(
            int excelRowNumber,
            String inputValue,
            String normalizationNote,
            DrawingColorCheckDto item) {
    }
}
