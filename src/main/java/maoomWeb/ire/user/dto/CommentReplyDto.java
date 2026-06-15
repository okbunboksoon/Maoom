package maoomWeb.ire.user.dto;

import java.util.List;

/** 댓글에 달린 답글 정보를 전달하는 DTO. */
public class CommentReplyDto {

    private Long replyId;
    private Long commentId;
    private String replyText;
    private String userId;
    private String createDt;
    private List<CommentAttachmentDto> attachments;

    public Long getReplyId() {
        return replyId;
    }

    public void setReplyId(Long replyId) {
        this.replyId = replyId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCreateDt() {
        return createDt;
    }

    public void setCreateDt(String createDt) {
        this.createDt = createDt;
    }

    public List<CommentAttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(
            List<CommentAttachmentDto> attachments) {
        this.attachments = attachments;
    }
}
