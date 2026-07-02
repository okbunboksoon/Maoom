package maoomWeb.ire.user.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.service.CommentAttachmentService;
import maoomWeb.ire.user.service.CommentExportService;
import maoomWeb.ire.user.service.CommentPdfExportService;
import maoomWeb.ire.user.service.CommentService;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.PdfAccessService;

/**
 * PDF 리뷰 화면에서 보내는 댓글·답글·첨부·내보내기 요청의 입구다.
 *
 * <p>전체 연결 순서는 {@code pdfview.html -> CommentController ->
 * CommentService/CommentAttachmentService/내보내기 서비스 -> MyBatis 또는
 * 파일 저장소}이다. 이 클래스는 요청값을 받고 접근 권한을 검사한 뒤 실제
 * 업무 처리를 각 서비스에 위임한다.</p>
 *
 * <p>브라우저가 보내는 작성자 ID는 위조될 수 있으므로 신뢰하지 않는다.
 * 등록·수정에 사용할 사용자 ID는 항상 Spring Security 로그인 정보에서 읽는다.</p>
 */
@RestController
public class CommentController {

    private final CommentService commentService;
    private final CurrentUserService currentUserService;
    private final CommentAttachmentService attachmentService;
    private final CommentExportService exportService;
    private final CommentPdfExportService pdfExportService;
    private final PdfAccessService pdfAccessService;

    public CommentController(
            CommentService commentService,
            CurrentUserService currentUserService,
            CommentAttachmentService attachmentService,
            CommentExportService exportService,
            CommentPdfExportService pdfExportService,
            PdfAccessService pdfAccessService) {
        this.commentService = commentService;
        this.currentUserService = currentUserService;
        this.attachmentService = attachmentService;
        this.exportService = exportService;
        this.pdfExportService = pdfExportService;
        this.pdfAccessService = pdfAccessService;
    }

    /**
     * PDF별 댓글 목록을 상태 조건과 함께 조회한다.
     * CommentService가 답글과 첨부까지 결합하므로 화면은 한 응답으로 댓글 카드를 그린다.
     */
    @GetMapping("/api/comment/list")
    public List<CommentDto> getCommentList(
            @RequestParam Long pdfId,
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication){

        pdfAccessService.requirePdf(
                pdfId,
                userId(authentication));

        return commentService.getCommentList(
                pdfId,
                status);

    }
    
    /**
     * 새 댓글을 현재 로그인 사용자 소유로 등록한다.
     * 저장 완료 후 CommentService가 같은 PDF를 보는 사용자에게 갱신 신호를 보낸다.
     */
    @PostMapping("/api/comment/add")
    public Long addComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        commentDto.setUserId(
                userId(authentication));
        commentDto.setStatus("OPEN");
        pdfAccessService.requirePdf(
                commentDto.getPdfId(),
                commentDto.getUserId());
        return commentService.addComment(commentDto);

    }

    /**
     * 댓글 또는 답글에 파일을 첨부한다.
     * 파일 내용은 로컬 업로드 폴더에, 파일명과 크기 등의 정보는 DB에 저장된다.
     */
    @PostMapping(
            value = "/api/comment/attachment/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<CommentAttachmentDto> uploadAttachments(
            @RequestParam Long commentId,
            @RequestParam(required = false) Long replyId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) throws IOException {

        String userId =
                userId(authentication);
        pdfAccessService.requireComment(
                commentId,
                userId);

        if(replyId != null){
            return attachmentService.saveReplyAttachments(
                    commentId,
                    replyId,
                    files,
                    userId);
        }

        return attachmentService.saveAttachments(
                commentId,
                files,
                userId);
    }

    /** 댓글의 첨부파일 목록을 반환한다. */
    @GetMapping("/api/comment/attachment/list")
    public List<CommentAttachmentDto> getAttachmentList(
            @RequestParam Long commentId,
            Authentication authentication) {
        pdfAccessService.requireComment(
                commentId,
                userId(authentication));
        return attachmentService.getAttachmentList(commentId);
    }

    /** 첨부파일을 원래 파일명으로 다운로드한다. */
    @GetMapping("/api/comment/attachment/download")
    public ResponseEntity<Resource> downloadAttachment(
            @RequestParam Long attachmentId,
            Authentication authentication) throws IOException {

        pdfAccessService.requireAttachment(
                attachmentId,
                userId(authentication));

        CommentAttachmentDto attachment =
                attachmentService.getAttachment(attachmentId);
        Resource resource =
                attachmentService.getResource(attachment);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if(attachment.getContentType() != null){
            try{
                mediaType = MediaType.parseMediaType(
                        attachment.getContentType());
            }catch(IllegalArgumentException ignored){
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(attachment.getFileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                        .filename(
                                attachment.getOriginalName(),
                                StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    /** 이미지 첨부를 브라우저 안에서 미리 볼 수 있도록 반환한다. */
    @GetMapping("/api/comment/attachment/view")
    public ResponseEntity<Resource> viewAttachment(
            @RequestParam Long attachmentId,
            Authentication authentication) throws IOException {

        pdfAccessService.requireAttachment(
                attachmentId,
                userId(authentication));

        CommentAttachmentDto attachment =
                attachmentService.getAttachment(attachmentId);
        Resource resource =
                attachmentService.getResource(attachment);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if(attachment.getContentType() != null){
            try{
                mediaType = MediaType.parseMediaType(
                        attachment.getContentType());
            }catch(IllegalArgumentException ignored){
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(attachment.getFileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                        .filename(
                                attachment.getOriginalName(),
                                StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    /** PDF의 댓글·답글·첨부 이미지를 한 시트로 정리한 Excel 파일을 내려준다. */
    @GetMapping("/api/comment/export")
    public ResponseEntity<byte[]> exportComments(
            @RequestParam Long pdfId,
            @RequestParam(defaultValue = "PDF") String fileName,
            Authentication authentication)
            throws IOException {

        pdfAccessService.requirePdf(
                pdfId,
                userId(authentication));

        byte[] workbook =
                exportService.createWorkbook(pdfId);
        String exportFileName =
                buildCommentExportFileName(
                        fileName,
                        "xlsx");

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                        .filename(
                                exportFileName,
                                StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(workbook);
    }

    /**
     * Drive 원본 PDF에 화면 댓글을 표준 PDF 주석으로 변환해 내려준다.
     * pdfId와 fileId의 소속 관계를 먼저 확인해 서로 다른 PDF가 섞이지 않게 한다.
     */
    @GetMapping("/api/comment/pdf/export")
    public ResponseEntity<byte[]> exportCommentPdf(
            @RequestParam Long pdfId,
            @RequestParam String fileId,
            @RequestParam(defaultValue = "PDF") String fileName,
            Authentication authentication)
            throws IOException {

        pdfAccessService.requirePdfFile(
                pdfId,
                fileId,
                userId(authentication));

        byte[] pdf =
                pdfExportService.createAnnotatedPdf(
                        pdfId,
                        fileId);
        String exportFileName =
                buildCommentExportFileName(
                        fileName,
                        "pdf");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                        .filename(
                                exportFileName,
                                StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(pdf);
    }

    /** Excel과 PDF에 동일한 댓글 내보내기 파일명 규칙을 적용한다. */
    private String buildCommentExportFileName(
            String fileName,
            String extension) {

        String baseName =
                fileName == null || fileName.isBlank()
                ? "PDF"
                : fileName.trim();

        baseName = baseName.replaceFirst(
                "(?i)\\.pdf$",
                "");
        baseName = baseName.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_");

        String timestamp =
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                                "yyyyMMdd_HHmmss"));

        return timestamp
                + "_"
                + baseName
                + "_Comment."
                + extension;
    }

    /** 댓글 작성자가 본문 내용을 수정한다. */
    @PostMapping("/api/comment/update")
    public int updateComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireComment(
                commentDto.getCommentId(),
                userId);

        return commentService.updateComment(
                commentDto,
                userId);

    }

    /** 사각형 댓글의 위치와 크기를 작성자 권한으로 변경한다. */
    @PostMapping("/api/comment/geometry")
    public int updateCommentGeometry(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireComment(
                commentDto.getCommentId(),
                userId);

        return commentService.updateCommentGeometry(
                commentDto,
                userId);
    }

    /** 댓글의 OPEN/RESOLVED 상태를 변경한다. */
    @PostMapping("/api/comment/status")
    public int updateCommentStatus(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireComment(
                commentDto.getCommentId(),
                userId);

        return commentService.updateCommentStatus(
                commentDto,
                userId);

    }

    /** 현재 사용자가 작성한 댓글을 삭제한다. */
    @PostMapping("/api/comment/delete")
    public int deleteComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireComment(
                commentDto.getCommentId(),
                userId);

        return commentService.deleteComment(
                commentDto.getCommentId(),
                userId);

    }
    
    /** 댓글에 새 답글을 등록한다. */
    @PostMapping("/api/comment/reply/add")
    @ResponseBody
    public Long addReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        dto.setUserId(
                userId(authentication));
        pdfAccessService.requireComment(
                dto.getCommentId(),
                dto.getUserId());

        return commentService.addReply(dto);
    }

    /** 작성자가 자신의 답글을 수정한다. */
    @PostMapping("/api/comment/reply/update")
    @ResponseBody
    public int updateReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireReply(
                dto.getReplyId(),
                userId);

        return commentService.updateReply(
                dto,
                userId);
    }

    /** 작성자가 자신의 답글을 삭제한다. */
    @PostMapping("/api/comment/reply/delete")
    @ResponseBody
    public int deleteReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        String userId = userId(authentication);
        pdfAccessService.requireReply(
                dto.getReplyId(),
                userId);

        return commentService.deleteReply(
                dto.getReplyId(),
                userId);
    }
    
    /** 특정 댓글에 달린 답글 목록을 조회한다. */
    @GetMapping("/api/comment/reply/list")
    @ResponseBody
    public List<CommentReplyDto> getReplyList(
            Long commentId,
            Authentication authentication){

        pdfAccessService.requireComment(
                commentId,
                userId(authentication));

        return commentService.getReplyList(commentId);
    }

    private String userId(Authentication authentication) {
        return currentUserService.getUserId(authentication);
    }

}
