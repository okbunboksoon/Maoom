package maoomWeb.ire.user.dto;

/** 법규 DITAMAP 내보내기 저장 결과. */
public record DitamapLegalSaveResponse(
        int updatedCount,
        String savedDitamapFile) {
}
