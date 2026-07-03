package maoomWeb.ire.user.dto;

/** DITA 파일 루트 속성값 수정 한 건. */
public record DitamapAttributeUpdate(
        String filePath,
        String sourceFilePath,
        String elementPath,
        String attributeName,
        String attributeValue) {
}
