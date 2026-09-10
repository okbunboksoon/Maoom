package maoomWeb.ire.user.dto;

public record AutomaticNoticeRuleImportDetail(
        int excelRowNumber,
        String region,
        String matchType,
        String matchKey,
        String status,
        String note) {
}
