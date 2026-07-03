package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.DrawingColorCheckImportDetail;
import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;

class ColorCheckImportReportServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsDetailedReportWithoutOverwritingExistingFile()
            throws Exception {

        DrawingColorCheckImportResult result =
                new DrawingColorCheckImportResult(
                        2,
                        1,
                        1,
                        0,
                        0,
                        List.of(
                                new DrawingColorCheckImportDetail(
                                        4,
                                        "N_NEW",
                                        "V",
                                        "V",
                                        "",
                                        "신규",
                                        ""),
                                new DrawingColorCheckImportDetail(
                                        5,
                                        "N_CHANGED",
                                        "",
                                        "X",
                                        "V",
                                        "수정",
                                        "컬러도안 공백을 X로 처리했습니다.")));
        ColorCheckImportReportService service =
                new ColorCheckImportReportService();

        Path first = service.createReport(
                result,
                "sample_컬러체크.xlsx",
                tempDirectory);
        Path second = service.createReport(
                result,
                "sample_컬러체크.xlsx",
                tempDirectory);

        assertThat(first.getFileName().toString())
                .isEqualTo(
                        "sample_컬러체크_DB반영리포트.xlsx");
        assertThat(second.getFileName().toString())
                .isEqualTo(
                        "sample_컬러체크_DB반영리포트 (1).xlsx");
        assertThat(Files.size(first)).isPositive();

        try(var input = Files.newInputStream(first);
                var workbook = WorkbookFactory.create(input)){
            var sheet = workbook.getSheet("DB 반영 결과");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(12).getCell(1)
                    .getStringCellValue()).isEqualTo("N_NEW");
            assertThat(sheet.getRow(13).getCell(5)
                    .getStringCellValue()).isEqualTo("수정");
        }
    }
}
