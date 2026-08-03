package maoomWeb.ire.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.user.dto.PdfDto;
import maoomWeb.ire.user.service.PdfService;
import maoomWeb.ire.user.service.CurrentUserService;

/**
 * PDF 리뷰 뷰어가 처음 열릴 때 Google Drive 파일과 내부 PDF 레코드를 연결한다.
 *
 * <p>화면 위치: {@code templates/user/pdf/pdfview.html}<br>
 * 호출 JS: 뷰어 HTML 하단 스크립트가 URL의 {@code fileId}를 읽어
 * {@code /api/pdf/find-by-drive-file-id}를 호출한다.<br>
 * 연결 라우트: {@code UserController#pdfView()}가 {@code /pdf/view} 화면을 반환하고,
 * {@code pdfList.html}에서 PDF 클릭 시 해당 화면으로 이동한다.<br>
 * 연결 서비스: {@link PdfService}가 {@code tb_pdf} 레코드를 조회하거나 생성한다.</p>
 *
 * <p>수정 시 주의점: 댓글은 Drive 파일 ID가 아니라 내부 {@code pdfId}를 기준으로
 * 저장된다. 화면은 댓글을 조회하기 전에 반드시 이 API에서 {@code pdfId}를 받아야
 * 하며, 이 연결 순서를 바꾸면 CommentController 권한 검사도 함께 확인해야 한다.</p>
 */
@Controller
public class PdfController {

    private static final Logger log =
            LoggerFactory.getLogger(PdfController.class);

    private final PdfService pdfService;
    private final CurrentUserService currentUserService;

    public PdfController(
            PdfService pdfService,
            CurrentUserService currentUserService) {
        this.pdfService = pdfService;
        this.currentUserService = currentUserService;
    }

    /**
     * Drive 파일 ID에 대응하는 내부 PDF 정보를 반환한다.
     * 아직 등록되지 않은 파일이면 {@link PdfService}가 tb_pdf 레코드를 먼저 만든다.
     */
    @GetMapping("/api/pdf/find-by-drive-file-id")
    @ResponseBody
    public PdfDto findPdf(
            @RequestParam String fileId,
            Authentication authentication){

        log.debug(
                "Finding PDF by Drive file id: {}",
                fileId);

        return pdfService.findByDriveFileId(
                fileId,
                currentUserService.getUserId(authentication));

    }

    
    
    
    
}
