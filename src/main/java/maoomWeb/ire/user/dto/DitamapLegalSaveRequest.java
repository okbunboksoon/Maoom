package maoomWeb.ire.user.dto;

import java.util.List;

/** 법규 DITAMAP 화면 편집 결과 저장 요청. */
public record DitamapLegalSaveRequest(
        String ditamapFile,
        String baseDitamapFile,
        List<DitamapLegalRow> rows) {
}
