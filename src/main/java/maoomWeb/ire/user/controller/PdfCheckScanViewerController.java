package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 메인 화면의 '인쇄데이터 검증' 카드에서 호출하는 REST 컨트롤러.
 *
 * <p>운영 서버에서 EXE를 실행하면 원격 사용자가 눌러도 서버 PC에서 프로그램이
 * 열린다. 현재 화면은 사용자 PC의 로컬 프로토콜을 호출하므로 이 API는 남아 있는
 * 직접 호출이 서버 프로세스를 실행하지 못하게 차단한다.</p>
 */
@RestController
public class PdfCheckScanViewerController {

    /** 서버 PC에서 EXE가 실행되지 않도록 기존 런처 API를 비활성화한다. */
    @PostMapping("/api/pdf-check-scan-viewer/launch")
    public ResponseEntity<String> launch() {
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body("PDF 검수 스캔 뷰어는 사용자 PC에서 실행해야 합니다.");
    }

    /** EXE 경로 누락, 파일 없음, 실행 실패를 팝업 alert에서 읽을 수 있는 한글 문장으로 반환한다. */
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleLaunchException(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
