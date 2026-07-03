package maoomWeb.ire.user.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 검토 엑셀을 DB에 반영한 뒤 화면과 리포트 생성에 전달하는 처리 결과.
 *
 * @param totalRows 값이 있어 처리 대상으로 확인한 전체 행 수
 * @param insertedCount DB에 새로 추가한 도안 수
 * @param updatedCount 기존 V/X 값이 달라 수정한 도안 수
 * @param unchangedCount 기존 DB 값과 같아 수정하지 않은 도안 수
 * @param skippedCount 도안명 누락, 잘못된 값, 중복 등으로 제외한 행 수
 * @param details 행별 처리 결과. Excel 리포트의 상세 목록으로 사용한다.
 * @param finalWorkbookPath 생성된 도안 발주 내역서의 전체 경로
 * @param reportPath 생성된 DB 반영 리포트의 전체 경로
 */
public record DrawingColorCheckImportResult(
        int totalRows,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount,
        @JsonIgnore
        List<DrawingColorCheckImportDetail> details,
        String finalWorkbookPath,
        String reportPath) {

    public DrawingColorCheckImportResult {
        details = details == null
                ? List.of()
                : List.copyOf(details);
    }

    /** 서비스 테스트처럼 아직 리포트를 만들지 않은 경우 사용하는 생성자다. */
    public DrawingColorCheckImportResult(
            int totalRows,
            int insertedCount,
            int updatedCount,
            int unchangedCount,
            int skippedCount,
            List<DrawingColorCheckImportDetail> details) {
        this(
                totalRows,
                insertedCount,
                updatedCount,
                unchangedCount,
                skippedCount,
                details,
                null,
                null);
    }

    /** DB 반영 결과에 생성된 두 Excel 파일 경로를 추가한다. */
    public DrawingColorCheckImportResult withOutputPaths(
            String finalWorkbookPath,
            String reportPath) {
        return new DrawingColorCheckImportResult(
                totalRows,
                insertedCount,
                updatedCount,
                unchangedCount,
                skippedCount,
                details,
                finalWorkbookPath,
                reportPath);
    }
}
