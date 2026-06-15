package maoomWeb.ire.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import maoomWeb.ire.user.dto.PdfDto;

@Mapper
/**
 * MAOOM 내부 PDF 레코드를 조회하고 생성하는 MyBatis 매퍼.
 */
public interface PdfMapper {

    PdfDto getPdfById(Long pdfId);

    PdfDto getPdfByDriveFileId(String driveFileId);
    
    PdfDto findByDriveFileId(String driveFileId);

    int insertPdf(PdfDto pdfDto);

    int updateFileName(
            @Param("pdfId") Long pdfId,
            @Param("fileName") String fileName);

    int updateFileInfo(
            @Param("pdfId") Long pdfId,
            @Param("fileName") String fileName,
            @Param("filePath") String filePath);

}
