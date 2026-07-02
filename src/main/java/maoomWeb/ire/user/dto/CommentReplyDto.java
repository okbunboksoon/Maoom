package maoomWeb.ire.user.dto;

import java.util.List;

/**
 * 댓글에 달린 답글 한 건을 화면과 DB 사이에서 전달한다.
 * 목록 조회 시 attachments에는 이 답글에 직접 연결된 첨부파일만 들어간다.
 */
public class CommentReplyDto {

    private Long replyId;
    private Long commentId;
    private String replyText;
    private String userId;
    private String userName;
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
