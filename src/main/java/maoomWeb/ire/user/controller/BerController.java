package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.BerApplyRequest;
import maoomWeb.ire.user.dto.BerApplyResult;
import maoomWeb.ire.user.service.BerApplyService;

/**
 * BER 화면의 AJAX 요청을 서비스 계층으로 넘기는 REST 컨트롤러.
 *
 * <p>화면은 {@code ditaPath}와 {@code outputPath} 문자열만 보낸다. 실제 경로 검증,
 * revision-tool 복사, BAT 실행, 결과 폴더 이동은 {@link BerApplyService}에서 처리한다.</p>
 */
@RestController
public class BerController {

    private final BerApplyService berApplyService;

    public BerController(BerApplyService berApplyService) {
        this.berApplyService = berApplyService;
    }

    /** BER 반영 버튼 클릭 시 호출되는 API. 성공하면 결과 temp/topics 경로를 JSON으로 반환한다. */
    @PostMapping("/api/ber/apply")
    public BerApplyResult apply(
            @RequestBody BerApplyRequest request) {
        return berApplyService.apply(request.ditaPath(), request.outputPath());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}