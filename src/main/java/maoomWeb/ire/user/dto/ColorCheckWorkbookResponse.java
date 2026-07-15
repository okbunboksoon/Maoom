package maoomWeb.ire.user.dto;

/**
 * 컬러체크 엑셀 생성 서비스가 컨트롤러에 돌려주는 다운로드 정보.
 */
public record ColorCheckWorkbookResponse(
        String fileName,
        byte[] content,
        boolean databaseUpdated,
        DrawingColorCheckImportResult importResult) {
}
