package maoomWeb.ire.user.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PDF 리뷰 화면, 서비스, MyBatis 사이에서 댓글 한 건을 전달하는 데이터 묶음이다.
 *
 * <p>{@code rectX/Y/W/H}, {@code posX/Y}는 픽셀이 아니라 페이지 크기 기준
 * 0~1 비율이다. 이렇게 저장하면 화면 확대 배율이나 모니터 크기가 달라도 같은
 * 위치에 주석을 다시 그릴 수 있고, PDF 내보내기 때 실제 PDF 좌표로 변환할 수 있다.</p>
 */
public class CommentDto {

    // 식별/소속 정보
	private Long commentId;
	private String commentCode;
	private Long pdfId;
	private Integer pageNum;

    // 기존 포인트 댓글과 빠른 페이지 이동에 사용하는 대표 위치
	private BigDecimal posX;
	private BigDecimal posY;

    // 댓글 본문, 작성자, 처리 상태
	private String commentText;
	private String userId;
	private String userName;
	private String status;
	private LocalDateTime createDt;
	private LocalDateTime updateDt;

    // TEXT, RECT, CALLOUT, DRAW 등 화면 주석의 종류와 좌표 정보
	private String commentType;
	private String selectedText;
	private BigDecimal rectX;
	private BigDecimal rectY;
	private BigDecimal rectW;
	private BigDecimal rectH;
	private String drawingPath;

    // 목록 조회 시 CommentService가 함께 채워 주는 하위 데이터
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

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
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
