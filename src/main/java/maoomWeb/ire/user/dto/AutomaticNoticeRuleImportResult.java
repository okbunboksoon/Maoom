package maoomWeb.ire.user.dto;

import java.util.List;

public record AutomaticNoticeRuleImportResult(
        int totalRows,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount,
        List<AutomaticNoticeRuleImportDetail> details) {

    public AutomaticNoticeRuleImportResult {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
