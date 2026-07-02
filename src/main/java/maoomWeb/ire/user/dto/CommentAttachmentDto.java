package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/**
 * 댓글 또는 답글에 연결된 첨부파일의 메타데이터를 전달한다.
 * 실제 파일 내용은 업로드 폴더에 있고 이 DTO에는 조회와 다운로드에 필요한 정보가 담긴다.
 */
public class CommentAttachmentDto {

    private Long attachmentId;
    private Long commentId;
    /** null이면 원댓글 첨부이고, 값이 있으면 해당 답글에 연결된 첨부다. */
    private Long replyId;
    /** 사용자가 업로드한 원래 이름으로 다운로드 화면에 표시한다. */
    private String originalName;
    /** 파일명 충돌과 경로 조작을 막기 위해 서버가 생성한 실제 저장 이름이다. */
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String uploaderId;
    private LocalDateTime createDt;

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Long getReplyId() {
        return replyId;
    }

    public void setReplyId(Long replyId) {
        this.replyId = replyId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public LocalDateTime getCreateDt() {
        return createDt;
    }

    public void setCreateDt(LocalDateTime createDt) {
        this.createDt = createDt;
    }
}
