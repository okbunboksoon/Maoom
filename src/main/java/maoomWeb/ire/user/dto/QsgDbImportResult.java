package maoomWeb.ire.user.dto;

/** QSG DB 엑셀 업로드 결과를 관리자 화면에 알려주는 요약 DTO. */
public record QsgDbImportResult(
        int totalRows,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount) {
}
