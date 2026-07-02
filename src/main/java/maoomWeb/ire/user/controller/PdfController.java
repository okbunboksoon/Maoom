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
 * PDF 리뷰 화면이 처음 열릴 때 Google Drive 파일과 내부 PDF 레코드를 연결한다.
 *
 * <p>처리 흐름:
 * pdfview.html -> 이 컨트롤러 -> PdfService -> PdfMapper(tb_pdf).
 * 댓글은 Drive 파일 ID가 아니라 내부 {@code pdfId}를 기준으로 저장하므로,
 * 화면은 댓글을 조회하기 전에 반드시 이 API에서 {@code pdfId}를 받아야 한다.</p>
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
