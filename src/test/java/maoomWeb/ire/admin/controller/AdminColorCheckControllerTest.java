package maoomWeb.ire.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.DrawingColorCheckImportResult;
import maoomWeb.ire.user.service.DrawingColorCheckService;

class AdminColorCheckControllerTest {

    @Test
    void importsUploadedExcelThroughColorCheckService()
            throws Exception {
        DrawingColorCheckService service =
                mock(DrawingColorCheckService.class);
        DrawingColorCheckImportResult expected =
                new DrawingColorCheckImportResult(
                        3,
                        1,
                        1,
                        1,
                        0,
                        List.of());
        when(service.importExcel(any(InputStream.class)))
                .thenReturn(expected);
        AdminColorCheckController controller =
                new AdminColorCheckController(service);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "color-check.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[]{1, 2, 3});

        DrawingColorCheckImportResult result =
                controller.importColorCheckDb(file);

        assertThat(result).isSameAs(expected);
        verify(service).importExcel(any(InputStream.class));
    }

    @Test
    void rejectsEmptyUpload() {
        DrawingColorCheckService service =
                mock(DrawingColorCheckService.class);
        AdminColorCheckController controller =
                new AdminColorCheckController(service);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "empty.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[0]);

        assertThatThrownBy(() -> controller.importColorCheckDb(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("엑셀 파일");
    }
}
