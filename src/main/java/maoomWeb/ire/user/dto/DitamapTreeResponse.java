package maoomWeb.ire.user.dto;

import java.util.List;

/** DITAMAP Builder 화면에 표시할 기준 DITAMAP 트리 응답. */
public record DitamapTreeResponse(
        String rootTitle,
        String ditamapFile,
        List<DitamapTreeNode> nodes) {
}
