package maoomWeb.ire.user.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;
import maoomWeb.ire.user.service.ColorCheckExportService;
import maoomWeb.ire.user.service.ColorCheckFinalWorkbookService;
import maoomWeb.ire.user.service.DrawingColorCheckService;

/**
 * 컬러체크 화면에서 사용하는 HTTP API를 한곳에 모은 컨트롤러.
 *
 * <p>처음 보는 사람은 다음 흐름으로 이해하면 된다.</p>
 * <ol>
 *   <li>사용자가 {@code /pdf/color-check} 화면에서 PDF를 선택한다.</li>
 *   <li>{@code POST /api/pdf/color-check/excel}이 PDF를 받아 엑셀을 만든다.</li>
 *   <li>사용자가 엑셀의 '컬러도안' 열을 검토한다.</li>
 *   <li>{@code POST /api/pdf/color-check/import}가 V/X 값을 DB에 저장한다.</li>
 *   <li>다음 엑셀 생성부터 DB의 V/X 값이 자동으로 채워진다.</li>
 * </ol>
 *
 * <p>컨트롤러는 파일 형식 확인과 HTTP 응답만 담당한다.
 * 실제 PDF 분석은 {@link ColorCheckExportService}, DB 반영은
 * {@link DrawingColorCheckService}가 담당한다.</p>
 */
@RestController
public class ColorCheckController {

    /** 브라우저가 응답을 XLSX 파일로 인식하도록 사용하는 표준 MIME 타입. */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet");

    /** PDF를 읽고 도안 이미지가 포함된 엑셀을 만드는 서비스. */
    private final ColorCheckExportService colorCheckExportService;

    /** 도안별 V/X 값을 조회하고 DB에 저장하는 서비스. */
    private final DrawingColorCheckService drawingColorCheckService;

    /** 검토 엑셀을 최종 도안 발주 내역서 양식으로 만드는 서비스. */
    private final ColorCheckFinalWorkbookService finalWorkbookService;

    public ColorCheckController(
            ColorCheckExportService colorCheckExportService,
            DrawingColorCheckService drawingColorCheckService,
            ColorCheckFinalWorkbookService finalWorkbookService) {
        this.colorCheckExportService = colorCheckExportService;
        this.drawingColorCheckService = drawingColorCheckService;
        this.finalWorkbookService = finalWorkbookService;
    }

    /**
     * DB에 저장된 모든 도안명과 V/X 값을 조회한다.
     *
     * <p>현재 화면에서는 직접 사용하지 않지만, 관리 화면이나 목록 기능에서
     * 재사용할 수 있도록 제공하는 조회 API다.</p>
     */
    @GetMapping("/api/pdf/color-check/items")
    public List<DrawingColorCheckDto> getItems() {
        return drawingColorCheckService.findAll();
    }

    /**
     * 도안 한 건의 V/X 값을 저장한다.
     *
     * <p>동일한 도안명이 이미 있으면 수정하고, 없으면 새로 등록한다.</p>
     */
    @PutMapping("/api/pdf/color-check/items")
    public DrawingColorCheckDto saveItem(
            @RequestBody DrawingColorCheckDto colorCheck) {
        return drawingColorCheckService.save(colorCheck);
    }

    /**
     * 사용자가 검토한 엑셀을 읽어 '도안명'과 '컬러도안' 값을 DB에 반영한다.
     *
     * <p>컬러도안 규칙은 V, X, 공백이며 공백은 X로 처리한다.
     * 자세한 행 처리 규칙은 {@link DrawingColorCheckService#importExcel(InputStream)}
     * 에서 관리한다.</p>
     */
    @PostMapping(
            value = "/api/pdf/color-check/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateDatabase", defaultValue = "true") boolean updateDatabase)
            throws IOException {

        validateExcel(file);

        /*
         * 이미지가 많은 대용량 엑셀을 byte[]로 복사하지 않고 임시 파일에 한 번
         * 저장한다. 최종 발주 엑셀은 임시 폴더에 만든 뒤 접속한 사용자 PC에
         * 내려보낸다. DB 반영 상세 리포트는 파일로 만들지 않고 화면에 건수만 보여준다.
         */
        String extension = file.getOriginalFilename()
                .toLowerCase()
                .endsWith(".xls")
                ? ".xls"
                : ".xlsx";
        Path uploadedFile = Files.createTempFile(
                "color-check-import-",
                extension);
        Path outputDirectory = Files.createTempDirectory(
                "color-check-output-");
        Path finalWorkbook = null;

        try{
            file.transferTo(uploadedFile);
            finalWorkbook =
                    finalWorkbookService.createFinalWorkbook(
                            uploadedFile,
                            file.getOriginalFilename(),
                            outputDirectory);

            DrawingColorCheckImportResult result = updateDatabase
                    ? importColorCheckValues(uploadedFile)
                    : new DrawingColorCheckImportResult(
                            0,
                            0,
                            0,
                            0,
                            0,
                            List.of());
            byte[] excel = Files.readAllBytes(finalWorkbook);
            String fileName = finalWorkbook.getFileName()
                    .toString();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(XLSX_MEDIA_TYPE);
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(excel.length);
            headers.set("X-Color-Check-Db-Updated", String.valueOf(updateDatabase));
            headers.set("X-Color-Check-Total", String.valueOf(result.totalRows()));
            headers.set("X-Color-Check-Inserted", String.valueOf(result.insertedCount()));
            headers.set("X-Color-Check-Updated", String.valueOf(result.updatedCount()));
            headers.set("X-Color-Check-Unchanged", String.valueOf(result.unchangedCount()));
            headers.set("X-Color-Check-Skipped", String.valueOf(result.skippedCount()));
            return new ResponseEntity<>(
                    excel,
                    headers,
                    HttpStatus.OK);
        }finally{
            deleteIfExists(uploadedFile);
            deleteIfExists(finalWorkbook);
            deleteIfExists(outputDirectory);
        }
    }
    /**
     * 업로드된 PDF를 분석해 컬러체크용 XLSX를 생성한다.
     *
     * <p>응답에도 같은 XLSX 바이트를 넣는 이유는 브라우저가 작업 완료와
     * 실제 저장 파일명을 확인할 수 있게 하기 위해서다.</p>
     */
    @PostMapping(
            value = "/api/pdf/color-check/excel",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> createExcel(
            @RequestParam("file") MultipartFile file)
            throws IOException {

        validatePdf(file);
        byte[] excel = colorCheckExportService.createWorkbook(
                file.getBytes());
        String fileName = createOutputFileName(
                file.getOriginalFilename());
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

    /**
     * 사용자가 잘못된 파일을 선택했을 때 서버 오류(500) 대신
     * 이해하기 쉬운 400 응답 메시지를 돌려준다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidPdf(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .contentType(
                        new MediaType(
                                "text",
                                "plain",
                                StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }

    /**
     * 최종 엑셀 생성 또는 DB 반영 중 서버 내부 예외가 나도
     * 브라우저 화면에서 실제 원인 메시지를 확인할 수 있게 한다.
     */
    @ExceptionHandler({
            IOException.class,
            RuntimeException.class
    })
    public ResponseEntity<String> handleServerError(
            Exception exception) {
        Throwable cause = exception;

        while(cause.getCause() != null){
            cause = cause.getCause();
        }

        String message = cause.getMessage() == null
                || cause.getMessage().isBlank()
                ? exception.getMessage()
                : cause.getMessage();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(
                        new MediaType(
                                "text",
                                "plain",
                                StandardCharsets.UTF_8))
                .body("서버 처리 중 오류가 발생했습니다: " + message);
    }

    /** PDF 생성 API에 빈 파일이나 PDF가 아닌 파일이 들어오는 것을 막는다. */
    private void validatePdf(MultipartFile file) {

        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "PDF 파일을 선택해 주세요.");
        }

        String originalName = file.getOriginalFilename();

        if(originalName == null
                || !originalName.toLowerCase()
                .endsWith(".pdf")){
            throw new IllegalArgumentException(
                    "PDF 파일만 선택할 수 있습니다.");
        }
    }

    private DrawingColorCheckImportResult importColorCheckValues(
            Path uploadedFile)
            throws IOException {
        try(InputStream input = Files.newInputStream(uploadedFile)){
            return drawingColorCheckService.importExcel(input);
        }
    }
    /** DB 반영 API에는 XLS/XLSX 파일만 들어오도록 확인한다. */
    private void validateExcel(MultipartFile file) {

        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "엑셀 파일을 선택해 주세요.");
        }

        String originalName = file.getOriginalFilename();

        if(originalName == null
                || (!originalName.toLowerCase()
                .endsWith(".xlsx")
                && !originalName.toLowerCase()
                .endsWith(".xls"))){
            throw new IllegalArgumentException(
                    "XLSX 또는 XLS 파일만 선택할 수 있습니다.");
        }
    }

    /** 원본 PDF 이름 뒤에 "_컬러체크.xlsx"를 붙여 기본 파일명을 만든다. */
    private String createOutputFileName(String originalName) {

        String baseName = originalName == null
                ? "color-check"
                : originalName.replaceFirst("(?i)\\.pdf$", "");
        return baseName + "_도안분류용.xlsx";
    }

    /** 임시 파일 경로가 만들어진 경우에만 삭제한다. */
    private void deleteIfExists(Path path) throws IOException {

        if(path != null){
            Files.deleteIfExists(path);
        }
    }

    /**
     * 기존 파일을 보존하면서 저장할 수 있는 첫 번째 파일명을 찾는다.
     *
     * <p>예: {@code sample_컬러체크.xlsx}가 있으면
     * {@code sample_컬러체크 (1).xlsx}를 확인하고 계속 번호를 증가시킨다.</p>
     */
    private Path findAvailableOutputFile(
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
}


