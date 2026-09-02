package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.AutoCountingAnalyzeResult;
import maoomWeb.ire.user.service.AutoCountingService;
import maoomWeb.ire.user.service.AutoCountingService.WorkbookFile;

@RestController
public class AutoCountingController {

    private final AutoCountingService autoCountingService;

    public AutoCountingController(
            AutoCountingService autoCountingService) {
        this.autoCountingService = autoCountingService;
    }

    @PostMapping(
            value = "/api/auto-counting/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AutoCountingAnalyzeResult analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam(name = "pack32", defaultValue = "false")
            boolean pack32) throws Exception {

        return autoCountingService.analyzePdf(
                file,
                mode,
                pack32);
    }

    @PostMapping(
            value = "/api/auto-counting/first-edition/workbook",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> createFirstEditionWorkbook(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> fields) throws Exception {

        WorkbookFile workbook = autoCountingService.createFirstEditionWorkbook(
                file,
                fields);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        workbook.fileName(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(workbook.content());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleException(
            RuntimeException exception) {

        Throwable cause = exception.getCause() == null
                ? exception
                : exception.getCause();
        String message = cause.getMessage() == null
                ? exception.getMessage()
                : cause.getMessage();

        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(message);
    }
}
