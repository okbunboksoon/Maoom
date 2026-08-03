package maoomWeb.ire.user.dto;

import java.util.List;

public record BerAsisTobeImportResult(
        int totalRows,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount,
        List<BerAsisTobeImportDetail> details) {

    public BerAsisTobeImportResult {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
