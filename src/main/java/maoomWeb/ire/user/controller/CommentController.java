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
import maoomWeb.ire.user.service.CommentService;
import maoomWeb.ire.user.service.CurrentUserService;

@RestController
/**
 * PDF 댓글과 답글의 조회, 등록, 수정, 삭제 요청을 처리하는 REST 컨트롤러.
 * 작성자 ID는 요청값을 신뢰하지 않고 현재 인증 정보에서 설정한다.
 */
public class CommentController {

    private final CommentService commentService;
    private final CurrentUserService currentUserService;
    private final CommentAttachmentService attachmentService;
    private final CommentExportService exportService;

    public CommentController(
            CommentService commentService,
            CurrentUserService currentUserService,
            CommentAttachmentService attachmentService,
            CommentExportService exportService) {
        this.commentService = commentService;
        this.currentUserService = currentUserService;
        this.attachmentService = attachmentService;
        this.exportService = exportService;
    }

    /** PDF별 댓글 목록을 상태 조건과 함께 조회한다. */
    @GetMapping("/api/comment/list")
    public List<CommentDto> getCommentList(
            @RequestParam Long pdfId,
            @RequestParam(defaultValue = "ALL") String status){

        return commentService.getCommentList(
                pdfId,
                status);

    }
    
    /** 새 댓글을 현재 로그인 사용자 소유로 등록한다. */
    @PostMapping("/api/comment/add")
    public Long addComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        commentDto.setUserId(
                currentUserService.getUserId(authentication));
        commentDto.setStatus("OPEN");
        return commentService.addComment(commentDto);

    }

    /** 댓글에 파일 또는 이미지를 첨부한다. */
    @PostMapping(
            value = "/api/comment/attachment/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<CommentAttachmentDto> uploadAttachments(
            @RequestParam Long commentId,
            @RequestParam(required = false) Long replyId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) throws IOException {

        String userId =
                currentUserService.getUserId(authentication);

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
            @RequestParam Long commentId) {
        return attachmentService.getAttachmentList(commentId);
    }

    /** 첨부파일을 원래 파일명으로 다운로드한다. */
    @GetMapping("/api/comment/attachment/download")
    public ResponseEntity<Resource> downloadAttachment(
            @RequestParam Long attachmentId) throws IOException {

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
            @RequestParam Long attachmentId) throws IOException {

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

    /** PDF의 댓글과 답글 목록을 Excel 파일로 내려준다. */
    @GetMapping("/api/comment/export")
    public ResponseEntity<byte[]> exportComments(
            @RequestParam Long pdfId,
            @RequestParam(defaultValue = "PDF") String fileName)
            throws IOException {

        byte[] workbook =
                exportService.createWorkbook(pdfId);
        String exportFileName =
                buildExportFileName(fileName);

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

    private String buildExportFileName(String fileName) {

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
                + "_Comment.xlsx";
    }

    /** 댓글 작성자가 본문 내용을 수정한다. */
    @PostMapping("/api/comment/update")
    public int updateComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        return commentService.updateComment(
                commentDto,
                currentUserService.getUserId(authentication));

    }

    /** 사각형 댓글의 위치와 크기를 작성자 권한으로 변경한다. */
    @PostMapping("/api/comment/geometry")
    public int updateCommentGeometry(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        return commentService.updateCommentGeometry(
                commentDto,
                currentUserService.getUserId(authentication));
    }

    /** 댓글의 OPEN/RESOLVED 상태를 변경한다. */
    @PostMapping("/api/comment/status")
    public int updateCommentStatus(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        return commentService.updateCommentStatus(
                commentDto,
                currentUserService.getUserId(authentication));

    }

    /** 현재 사용자가 작성한 댓글을 삭제한다. */
    @PostMapping("/api/comment/delete")
    public int deleteComment(
            @RequestBody CommentDto commentDto,
            Authentication authentication){

        return commentService.deleteComment(
                commentDto.getCommentId(),
                currentUserService.getUserId(authentication));

    }
    
    /** 댓글에 새 답글을 등록한다. */
    @PostMapping("/api/comment/reply/add")
    @ResponseBody
    public Long addReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        dto.setUserId(
                currentUserService.getUserId(authentication));

        return commentService.addReply(dto);
    }

    /** 작성자가 자신의 답글을 수정한다. */
    @PostMapping("/api/comment/reply/update")
    @ResponseBody
    public int updateReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        return commentService.updateReply(
                dto,
                currentUserService.getUserId(authentication));
    }

    /** 작성자가 자신의 답글을 삭제한다. */
    @PostMapping("/api/comment/reply/delete")
    @ResponseBody
    public int deleteReply(
            @RequestBody CommentReplyDto dto,
            Authentication authentication){

        return commentService.deleteReply(
                dto.getReplyId(),
                currentUserService.getUserId(authentication));
    }
    
    /** 특정 댓글에 달린 답글 목록을 조회한다. */
    @GetMapping("/api/comment/reply/list")
    @ResponseBody
    public List<CommentReplyDto> getReplyList(
            Long commentId){

        return commentService.getReplyList(commentId);
    }

}
