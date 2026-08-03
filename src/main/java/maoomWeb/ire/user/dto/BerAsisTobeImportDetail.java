package maoomWeb.ire.user.dto;

public record BerAsisTobeImportDetail(
        int excelRowNumber,
        String region,
        String hash,
        String status,
        String note) {
}
