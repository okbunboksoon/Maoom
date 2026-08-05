package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.ArtworkRequestResult;

class ArtworkRequestSampleRunTest {

    private static final Path SAMPLE_DIRECTORY =
            Path.of("C:/Users/adobe/Desktop/ex");
    private static final Path SAMPLE_PDF =
            SAMPLE_DIRECTORY.resolve(
                    "KIA-QVe-EV-en_GB-2027-OM_Full-PDF-260109-1.4_ALL_LOW_Sammy_RHD도안.pdf");
    private static final Path EXPECTED_WORKBOOK =
            SAMPLE_DIRECTORY.resolve(
                    "KIA-QVe-EV-en_GB-2027-OM_Full-PDF-260109-1.4_ALL_LOW_Sammy_00_도안의뢰서.xlsx");

    @Test
    void createMatchesSampleWorkbookContent() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE_PDF));
        Assumptions.assumeTrue(Files.isRegularFile(EXPECTED_WORKBOOK));

        ArtworkRequestService service =
                new ArtworkRequestService();
        MockMultipartFile file;
        try(InputStream input = Files.newInputStream(SAMPLE_PDF)){
            file = new MockMultipartFile(
                    "file",
                    SAMPLE_PDF.getFileName().toString(),
                    "application/pdf",
                    input);
        }

        ArtworkRequestResult result =
                service.create(SAMPLE_DIRECTORY.toString(), file);

        Path actualWorkbook =
                Path.of(result.resultPath());
        assertThat(actualWorkbook).isRegularFile();
        assertThat(actualWorkbook.getParent()).isEqualTo(
                SAMPLE_DIRECTORY.resolve(
                        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "_Result_Folder_ImageExtractor"));
        assertThat(actualWorkbook.getFileName().toString())
                .isEqualTo(
                        "KIA-QVe-EV-en_GB-2027-OM_Full-PDF-260109-1.4_ALL_LOW_Sammy_RHD도안_도안의뢰서.xlsx");

        try(Workbook expected = new XSSFWorkbook(
                Files.newInputStream(EXPECTED_WORKBOOK));
                Workbook actual = new XSSFWorkbook(
                        Files.newInputStream(actualWorkbook))){

            assertThat(readDrawingCodes(actual))
                    .containsExactlyElementsOf(readDrawingCodes(expected));
            assertThat(actual.getAllPictures())
                    .hasSameSizeAs(expected.getAllPictures());
        }
    }

    private List<String> readDrawingCodes(Workbook workbook) {
        List<String> codes = new ArrayList<>();
        var sheet = workbook.getSheetAt(0);

        for(int rowIndex = 4; rowIndex <= sheet.getLastRowNum(); rowIndex++){
            var row = sheet.getRow(rowIndex);
            if(row == null || row.getCell(4) == null){
                continue;
            }

            String value = row.getCell(4).getStringCellValue();
            if(value == null || value.isBlank()){
                continue;
            }

            codes.add(value);
        }

        return codes;
    }
}
