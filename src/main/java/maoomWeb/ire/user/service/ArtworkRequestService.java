package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import maoom.PDFImageExtractor.PIEService;
import maoomWeb.ire.user.dto.ArtworkRequestResult;

@Service
public class ArtworkRequestService {

    private static final DateTimeFormatter RESULT_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final PIEService pieService =
            new PIEService();

    public ArtworkRequestResult create(
            String inputPath,
            MultipartFile file) throws Exception {
        validatePdf(file);
        Path inputDirectory = validateInputDirectory(inputPath);
        Path resultDirectory = inputDirectory.resolve(
                LocalDate.now().format(RESULT_DATE_FORMATTER)
                + "_Result_Folder_ImageExtractor");
        Files.createDirectories(resultDirectory);

        String originalFileName = Path.of(file.getOriginalFilename())
                .getFileName()
                .toString();
        String baseName = originalFileName.replaceFirst("(?i)\\.pdf$", "");
        Path resultPath = resultDirectory.resolve(
                baseName + "_도안의뢰서.xlsx");

        Path workDirectory = Files.createTempDirectory(
                "artwork-request-");
        Path workPdf = workDirectory.resolve(originalFileName);

        try{
            file.transferTo(workPdf);
            pieService.exportExcel(
                    workPdf.toString(),
                    resultPath.toString());
        }finally{
            deleteDirectory(workDirectory);
        }

        return new ArtworkRequestResult(
                true,
                originalFileName,
                file.getSize(),
                resultPath.toString(),
                "도안의뢰서 엑셀을 생성했습니다.");
    }

    private Path validateInputDirectory(String inputPath) {
        if(inputPath == null || inputPath.isBlank()){
            throw new IllegalArgumentException("입력 경로를 입력해 주세요.");
        }

        Path path = Path.of(inputPath.trim())
                .toAbsolutePath()
                .normalize();

        if(!Files.isDirectory(path)){
            throw new IllegalArgumentException(
                    "입력 경로를 찾을 수 없습니다: " + path);
        }

        return path;
    }

    private void validatePdf(MultipartFile file) {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("PDF 파일을 선택해 주세요.");
        }

        String originalName = file.getOriginalFilename();
        if(originalName == null
                || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")){
            throw new IllegalArgumentException("PDF 파일만 선택할 수 있습니다.");
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if(directory == null || !Files.exists(directory)){
            return;
        }

        try(var paths = Files.walk(directory)){
            for(Path path : paths
                    .sorted((left, right) -> right.compareTo(left))
                    .toList()){
                Files.deleteIfExists(path);
            }
        }
    }
}
