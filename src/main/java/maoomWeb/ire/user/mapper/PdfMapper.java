package maoomWeb.ire.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import maoomWeb.ire.user.dto.PdfDto;

/**
 * Java 서비스와 {@code PdfMapper.xml}의 SQL을 연결하는 MyBatis 인터페이스다.
 *
 * <p>메서드 이름은 XML의 select/insert/update id와 같아야 한다.
 * PDF 메타데이터 조회뿐 아니라 댓글·답글·첨부가 등록된 PDF에 속하는지 확인하는
 * EXISTS 쿼리도 이 매퍼를 통해 실행된다.</p>
 */
@Mapper
public interface PdfMapper {

    PdfDto getPdfById(Long pdfId);

    PdfDto getPdfByDriveFileId(String driveFileId);
    
    PdfDto findByDriveFileId(String driveFileId);

    boolean hasPdfAccess(
            @Param("pdfId") Long pdfId,
            @Param("userId") String userId);

    boolean isPdfOwner(
            @Param("pdfId") Long pdfId,
            @Param("userId") String userId);

    boolean hasDriveFileAccess(
            @Param("driveFileId") String driveFileId,
            @Param("userId") String userId);

    boolean hasPdfFileAccess(
            @Param("pdfId") Long pdfId,
            @Param("driveFileId") String driveFileId,
            @Param("userId") String userId);

    boolean hasCommentAccess(
            @Param("commentId") Long commentId,
            @Param("userId") String userId);

    boolean hasCommentAccessForPdf(
            @Param("commentId") Long commentId,
            @Param("pdfId") Long pdfId,
            @Param("userId") String userId);

    boolean hasReplyAccess(
            @Param("replyId") Long replyId,
            @Param("userId") String userId);

    boolean hasAttachmentAccess(
            @Param("attachmentId") Long attachmentId,
            @Param("userId") String userId);

    int insertPdf(PdfDto pdfDto);

    int updateFileName(
            @Param("pdfId") Long pdfId,
            @Param("fileName") String fileName);

    int updateFileInfo(
            @Param("pdfId") Long pdfId,
            @Param("fileName") String fileName,
            @Param("filePath") String filePath);

}
