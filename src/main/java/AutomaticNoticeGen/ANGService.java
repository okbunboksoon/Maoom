package AutomaticNoticeGen;

import java.util.List;

public interface ANGService {
    /** 기존 로직 유지: 필요 시 협조문 생성 등에 사용 */
    String generateFromPdf(String pdfPath) throws Exception;

    /** 문서의 장(챕터) 범위를 추출 */
    List<ChapterRange> getChapterRanges(String pdfPath) throws Exception;

    /** 지정한 장의 소목차(섹션) 범위를 추출 */
    List<SectionRange> getSections(String pdfPath, ChapterRange chapter, int lookaheadPages) throws Exception;

    /** 분석 결과를 '검토표' 엑셀(.xlsx)로 저장하고 파일 경로 리턴 */
    String exportExcel(String pdfPath, String outXlsx) throws Exception;
}
