package maoomWeb.ire.user.dto;

/**
 * 도안의뢰서 작성 API가 팝업 화면으로 돌려주는 결과.
 *
 * @param success 생성 성공 여부
 * @param fileName 사용자가 업로드한 PDF 파일명
 * @param fileSize 업로드 PDF 크기(byte)
 * @param resultPath 서버에 생성된 도안의뢰서 엑셀 경로
 * @param message 화면에 보여줄 완료 메시지
 */
public record ArtworkRequestResult(
        boolean success,
        String fileName,
        long fileSize,
        String resultPath,
        String message) {
}
