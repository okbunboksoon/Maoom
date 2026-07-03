package maoomWeb.ire.user.dto;

import java.util.List;

/** 화면에 표시할 DITAMAP 한 항목의 트리 정보. */
public record DitamapTreeNode(
        String title,
        int level,
        String attributeValue,
        String attributeName,
        String fileName,
        String filePath,
        String sourceFilePath,
        String elementPath,
        String href,
        List<DitamapTreeNode> children) {
}
