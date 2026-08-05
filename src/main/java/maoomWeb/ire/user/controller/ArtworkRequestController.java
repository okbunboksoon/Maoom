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

/**
 * 도안의뢰서 작성 팝업의 실행 요청을 받는 REST 컨트롤러.
 *
 * <p>화면은 서버 기준 결과 폴더 경로와 사용자가 드래그한 PDF 파일을
 * multipart/form-data로 보낸다. 컨트롤러는 HTTP 형식 처리만 맡고,
 * PDF 검증, 임시 파일 저장, 도안의뢰서 엑셀 생성은
 * {@link ArtworkRequestService}에 위임한다.</p>
 */
@RestController
public class ArtworkRequestController {

    private final ArtworkRequestService artworkRequestService;

    public ArtworkRequestController(
            ArtworkRequestService artworkRequestService) {
        this.artworkRequestService = artworkRequestService;
    }

    /** PDF 한 건을 받아 결과 폴더 아래에 도안의뢰서 엑셀 한 건을 만든다. */
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

    /** 서비스에서 발생한 검증/생성 오류를 화면에서 그대로 읽을 수 있는 plain text로 반환한다. */
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
