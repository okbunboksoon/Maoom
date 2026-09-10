package maoomWeb.ire.user.dto;

/**
 * 협조문 자동 완성 API가 팝업 화면으로 돌려주는 결과.
 *
 * @param success 생성 성공 여부
 * @param fileName 사용자가 업로드한 PDF 파일명
 * @param fileSize 업로드 PDF 크기(byte)
 * @param market 적용한 규칙 market
 * @param resultDirectory 결과 폴더 경로
 * @param resultPath 생성된 검토표 엑셀 경로
 * @param rulesPath 실행에 사용한 rules.xlsx 경로
 * @param message 화면에 보여줄 완료 메시지
 */
public record AutomaticNoticeResult(
        boolean success,
        String fileName,
        long fileSize,
        String market,
        String resultDirectory,
        String resultPath,
        String rulesPath,
        String message) {
}
