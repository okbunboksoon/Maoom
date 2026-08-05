package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.service.PdfCheckScanViewerService;

/**
 * 사용자 메인 화면의 '인쇄데이터 검증' 카드에서 호출하는 REST 컨트롤러.
 *
 * <p>이 기능은 웹 화면 안에서 검증을 처리하지 않고, 서버 PC에 설치된
 * PDF 검수 스캔 뷰어 EXE를 실행하는 런처 역할만 한다. 실제 EXE 경로와 실행
 * 가능 여부 검증은 {@link PdfCheckScanViewerService}에서 담당한다.</p>
 */
@RestController
public class PdfCheckScanViewerController {

    private final PdfCheckScanViewerService pdfCheckScanViewerService;

    public PdfCheckScanViewerController(
            PdfCheckScanViewerService pdfCheckScanViewerService) {
        this.pdfCheckScanViewerService = pdfCheckScanViewerService;
    }

    /** 카드 클릭 한 번으로 외부 검증 프로그램을 실행하고 화면에는 실행 메시지만 돌려준다. */
    @PostMapping("/api/pdf-check-scan-viewer/launch")
    public Map<String, String> launch() {
        pdfCheckScanViewerService.launch();
        return Map.of("message", "PDF 검수 스캔 뷰어를 실행했습니다.");
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
