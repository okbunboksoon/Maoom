package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.ArtworkRequestResult;
import maoomWeb.ire.user.service.ArtworkRequestService;

@RestController
public class ArtworkRequestController {

    private final ArtworkRequestService artworkRequestService;

    public ArtworkRequestController(
            ArtworkRequestService artworkRequestService) {
        this.artworkRequestService = artworkRequestService;
    }

    @PostMapping(
            value = "/api/artwork-request/run",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArtworkRequestResult run(
            @RequestParam("inputPath") String inputPath,
            @RequestParam("file") MultipartFile file) {
        try{
            return artworkRequestService.create(inputPath, file);
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
