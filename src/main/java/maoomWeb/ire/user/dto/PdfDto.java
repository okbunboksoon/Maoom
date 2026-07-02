package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/**
 * Google Drive 파일과 MAOOM 내부 PDF 레코드 사이에서 전달되는 데이터 묶음이다.
 * {@code driveFileId}는 실제 파일 조회에, {@code pdfId}는 댓글·답글 조회에 사용된다.
 */
public class PdfDto {

    /** tb_pdf의 기본키. 댓글 API가 PDF를 구분할 때 사용하는 값이다. */
	private Long pdfId;
    /** Drive에 표시되는 원본 파일명. */
	private String fileName;
    /** Google Drive API가 파일을 찾을 때 사용하는 고유 ID. */
	private String driveFileId;
    /** 공유 드라이브 루트 아래의 폴더 경로. */
	private String filePath;
    /** 처음 이 PDF 레코드를 등록한 사용자 ID. */
	private String regUserId;
    /** 내부 PDF 레코드 생성 시각. */
	private LocalDateTime createDt;
	
	public Long getPdfId() {
	    return pdfId;
	}

	public void setPdfId(Long pdfId) {
	    this.pdfId = pdfId;
	}

	public String getFileName() {
	    return fileName;
	}

	public void setFileName(String fileName) {
	    this.fileName = fileName;
	}

	public String getDriveFileId() {
	    return driveFileId;
	}

	public void setDriveFileId(String driveFileId) {
	    this.driveFileId = driveFileId;
	}

	public String getFilePath() {
	    return filePath;
	}

	public void setFilePath(String filePath) {
	    this.filePath = filePath;
	}

	public String getRegUserId() {
	    return regUserId;
	}

	public void setRegUserId(String regUserId) {
	    this.regUserId = regUserId;
	}

	public LocalDateTime getCreateDt() {
	    return createDt;
	}

	public void setCreateDt(LocalDateTime createDt) {
	    this.createDt = createDt;
	}
}
