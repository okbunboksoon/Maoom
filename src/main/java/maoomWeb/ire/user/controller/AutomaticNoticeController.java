package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.AutomaticNoticeResult;
import maoomWeb.ire.user.service.AutomaticNoticeService;

/** 협조문 자동 완성 팝업의 실행 요청을 받는 REST 컨트롤러. */
@RestController
public class AutomaticNoticeController {

    private final AutomaticNoticeService automaticNoticeService;

    public AutomaticNoticeController(
            AutomaticNoticeService automaticNoticeService) {
        this.automaticNoticeService = automaticNoticeService;
    }

    @PostMapping(
            value = "/api/automatic-notice/run",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AutomaticNoticeResult run(
            @RequestParam("inputPath") String inputPath,
            @RequestParam("file") MultipartFile file) {
        try{
            return automaticNoticeService.create(inputPath, file);
        }catch(Exception exception){
            throw new IllegalStateException(exception);
        }
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleInvalidFile(
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
