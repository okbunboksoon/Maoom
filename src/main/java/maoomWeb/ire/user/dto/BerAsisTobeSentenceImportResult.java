package maoomWeb.ire.user.dto;

public record BerAsisTobeSentenceImportResult(
        Long backupId,
        String jobDir,
        int totalRows,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount) {
}
