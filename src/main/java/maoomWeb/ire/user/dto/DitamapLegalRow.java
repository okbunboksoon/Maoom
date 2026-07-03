package maoomWeb.ire.user.dto;

/** 화면에서 편집한 법규 DITAMAP 한 행의 저장 정보. */
public record DitamapLegalRow(
        String title,
        int level,
        String attributeValue,
        String attributeName,
        String fileName,
        String filePath,
        String sourceFilePath,
        String elementPath,
        String href) {
}
