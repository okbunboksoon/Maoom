package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/** Google Drive 파일과 MAOOM 내부 PDF 레코드의 연결 정보를 담는 DTO. */
public class PdfDto {

	private Long pdfId;
	private String fileName;
	private String driveFileId;
	private String filePath;
	private String regUserId;
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
