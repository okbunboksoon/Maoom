package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.mapper.DrawingColorCheckMapper;

class DrawingColorCheckServiceTest {

    @Test
    void importsSampleStyleHeadersAndCountsChanges()
            throws Exception {

        DrawingColorCheckMapper mapper =
                mock(DrawingColorCheckMapper.class);
        DrawingColorCheckDto existing =
                new DrawingColorCheckDto();
        existing.setDrawingName("N_EXISTING_001");
        existing.setCheckValue("V");
        DrawingColorCheckDto changed =
                new DrawingColorCheckDto();
        changed.setDrawingName("N_CHANGED_001");
        changed.setCheckValue("X");
        DrawingColorCheckDto blankExisting =
                new DrawingColorCheckDto();
        blankExisting.setDrawingName(
                "N_BLANK_EXISTING_001");
        blankExisting.setCheckValue("V");
        when(mapper.findByDrawingNames(anyList()))
                .thenReturn(List.of(
                        existing,
                        changed,
                        blankExisting));
        DrawingColorCheckService service =
                new DrawingColorCheckService(mapper);

        var result = service.importExcel(
                new ByteArrayInputStream(createWorkbook()));

        assertThat(result.totalRows()).isEqualTo(7);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.unchangedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.details())
                .extracting(
                        detail -> detail.drawingName(),
                        detail -> detail.status(),
                        detail -> detail.appliedValue())
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                "N_NEW_001", "신규", "V"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_CHANGED_001", "수정", "V"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_EXISTING_001", "변경 없음", "V"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_INVALID_001", "제외", ""));
        assertThat(result.details())
                .filteredOn(detail ->
                        detail.drawingName().equals(
                                "N_BLANK_NEW_001"))
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.inputValue()).isEmpty();
                    assertThat(detail.appliedValue()).isEqualTo("X");
                    assertThat(detail.note()).contains("공백");
                });
        ArgumentCaptor<DrawingColorCheckDto> captor =
                ArgumentCaptor.forClass(
                        DrawingColorCheckDto.class);
        verify(mapper, times(4)).upsert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(
                        DrawingColorCheckDto::getDrawingName,
                        DrawingColorCheckDto::getCheckValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "N_NEW_001", "V"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_CHANGED_001", "V"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_BLANK_NEW_001", "X"),
                        org.assertj.core.groups.Tuple.tuple(
                                "N_BLANK_EXISTING_001", "X"));
    }

    private byte[] createWorkbook() throws Exception {

        try(XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){
            var sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0)
                    .setCellValue("체크");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("도안명");
            header.createCell(1).setCellValue("레드도안");
            header.createCell(2).setCellValue("컬러도안");
            addRow(sheet, 3, "N_NEW_001", "v");
            addRow(sheet, 4, "N_EXISTING_001", "V");
            addRow(sheet, 5, "N_CHANGED_001", "v");
            addRow(sheet, 6, "N_INVALID_001", "O");
            addRow(sheet, 7, "", "X");
            addRow(sheet, 8, "N_BLANK_NEW_001", "");
            addRow(sheet, 9, "N_BLANK_EXISTING_001", "");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addRow(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIndex,
            String drawingName,
            String checkValue) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(drawingName);
        row.createCell(2).setCellValue(checkValue);
    }
}
