package maoomWeb.ire.user.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PDF 댓글의 본문, 작성자, 상태와 화면 좌표 정보를 전달하는 DTO.
 * 선택 영역 댓글인 경우 rectX/Y/W/H와 selectedText를 함께 사용한다.
 */
public class CommentDto {

	private Long commentId;
	private String commentCode;
	private Long pdfId;
	private Integer pageNum;
	private BigDecimal posX;
	private BigDecimal posY;
	private String commentText;
	private String userId;
	private String status;
	private LocalDateTime createDt;
	private LocalDateTime updateDt;
	private String commentType;
	private String selectedText;
	private BigDecimal rectX;
	private BigDecimal rectY;
	private BigDecimal rectW;
	private BigDecimal rectH;
	private String drawingPath;
	private List<CommentReplyDto> replies;
	private List<CommentAttachmentDto> attachments;
	
	public Long getCommentId() {
		return commentId;
	}

	public void setCommentId(Long commentId) {
		this.commentId = commentId;
	}

	public String getCommentCode() {
		return commentCode;
	}

	public void setCommentCode(String commentCode) {
		this.commentCode = commentCode;
	}

	public Long getPdfId() {
		return pdfId;
	}

	public void setPdfId(Long pdfId) {
		this.pdfId = pdfId;
	}

	public Integer getPageNum() {
		return pageNum;
	}

	public void setPageNum(Integer pageNum) {
		this.pageNum = pageNum;
	}

	public BigDecimal getPosX() {
		return posX;
	}

	public void setPosX(BigDecimal posX) {
		this.posX = posX;
	}

	public BigDecimal getPosY() {
		return posY;
	}

	public void setPosY(BigDecimal posY) {
		this.posY = posY;
	}

	public String getCommentText() {
		return commentText;
	}

	public void setCommentText(String commentText) {
		this.commentText = commentText;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getCreateDt() {
		return createDt;
	}

	public void setCreateDt(LocalDateTime createDt) {
		this.createDt = createDt;
	}

	public LocalDateTime getUpdateDt() {
		return updateDt;
	}

	public void setUpdateDt(LocalDateTime updateDt) {
		this.updateDt = updateDt;
	}
	
	public String getCommentType() {
		return commentType;
	}

	public void setCommentType(String commentType) {
		this.commentType = commentType;
	}

	public String getSelectedText() {
		return selectedText;
	}

	public void setSelectedText(String selectedText) {
		this.selectedText = selectedText;
	}

	public BigDecimal getRectX() {
		return rectX;
	}

	public void setRectX(BigDecimal rectX) {
		this.rectX = rectX;
	}

	public BigDecimal getRectY() {
		return rectY;
	}

	public void setRectY(BigDecimal rectY) {
		this.rectY = rectY;
	}

	public BigDecimal getRectW() {
		return rectW;
	}

	public void setRectW(BigDecimal rectW) {
		this.rectW = rectW;
	}

	public BigDecimal getRectH() {
		return rectH;
	}

	public void setRectH(BigDecimal rectH) {
		this.rectH = rectH;
	}

	public String getDrawingPath() {
		return drawingPath;
	}

	public void setDrawingPath(String drawingPath) {
		this.drawingPath = drawingPath;
	}

	public List<CommentReplyDto> getReplies() {
		return replies;
	}

	public void setReplies(List<CommentReplyDto> replies) {
		this.replies = replies;
	}

	public List<CommentAttachmentDto> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<CommentAttachmentDto> attachments) {
		this.attachments = attachments;
	}
}
