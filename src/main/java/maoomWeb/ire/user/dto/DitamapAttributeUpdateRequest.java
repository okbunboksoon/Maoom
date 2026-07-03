package maoomWeb.ire.user.dto;

import java.util.List;

/** DITAMAP Builder 화면에서 수정한 속성값 목록. */
public record DitamapAttributeUpdateRequest(
        String ditamapFile,
        List<DitamapAttributeUpdate> updates) {
}
