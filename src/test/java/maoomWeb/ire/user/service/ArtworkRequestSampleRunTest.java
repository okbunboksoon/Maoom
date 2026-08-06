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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.ArtworkRequestResult;

class ArtworkRequestSampleRunTest {

    private static final Path SAMPLE_DIRECTORY =
            Path.of("C:/Users/adobe/Desktop/ex");
    private static final Path SAMPLE_PDF =
            SAMPLE_DIRECTORY.resolve(
                    "KIA-QVe-EV-en_GB-2027-OM_Full-PDF-260109-1.4_ALL_LOW_Sammy_RHD도안.pdf");
    private static final List<String> EXPECTED_CODES = List.of(
            "N_QVe27_D02_003_3_E",
            "N_QVe27_D04_009_1_E",
            "N_QVe27_D04_010_E",
            "N_QVe27_D04_011_E",
            "N_QVe27_D04_001_1_E",
            "N_QVe27_D04_002_1_E",
            "N_QVe27_D04_003_1_E",
            "N_SP3i26_B04_003_E",
            "N_QVe27_D04_005_1_E",
            "N_QVe27_D04_006_1_E",
            "N_QVe27_D04_016_1_E",
            "N_QVe27_D04_017_E",
            "N_QVe27_D04_018_1_E",
            "N_QVe27_D04_019_E",
            "N_QVe27_B05_019_E",
            "N_QVe27_B05_020_E",
            "N_QVe27_B05_018_E",
            "N_QVe27_C05_002_1_E",
            "N_QVe27_B05_023_1_E",
            "N_QVe27_B05_025_1_E",
            "N_QVe27_B05_024_E",
            "N_QVe27_B05_026_1_E",
            "N_QVe27_B05_027_E",
            "N_QVe27_B05_028_1_E");

    @TempDir
    Path outputDirectory;

    @Test
    void createMatchesSampleWorkbookContent() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE_PDF));

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
                service.create(outputDirectory.toString(), file);

        Path actualWorkbook =
                Path.of(result.resultPath());
        assertThat(actualWorkbook).isRegularFile();
        assertThat(actualWorkbook.getParent()).isEqualTo(
                outputDirectory.resolve(
                        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "_Result_Folder_ImageExtractor"));
        assertThat(actualWorkbook.getFileName().toString())
                .isEqualTo(
                        "KIA-QVe-EV-en_GB-2027-OM_Full-PDF-260109-1.4_ALL_LOW_Sammy_RHD도안_도안의뢰서.xlsx");

        try(Workbook actual = new XSSFWorkbook(
                Files.newInputStream(actualWorkbook))){

            assertThat(readDrawingCodes(actual))
                    .containsExactlyElementsOf(EXPECTED_CODES);
            assertThat(actual.getAllPictures())
                    .hasSize(EXPECTED_CODES.size());
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
