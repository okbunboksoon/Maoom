package maoomWeb.ire.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.user.dto.PdfDto;
import maoomWeb.ire.user.service.PdfService;

@Controller
/**
 * Google Drive 파일 ID를 MAOOM 내부 PDF 정보와 연결하는 API 컨트롤러.
 */
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
        System.out.println("PdfService 생성");
    }

    /** Drive 파일 ID로 PDF를 조회하고, 없으면 서비스에서 기본 정보를 생성한다. */
    @GetMapping("/api/pdf/find-by-drive-file-id")
    @ResponseBody
    public PdfDto findPdf(@RequestParam String fileId){

        System.out.println("Controller 호출 = " + fileId);

        return pdfService.findByDriveFileId(fileId);

    }

    
    
    
    
}
