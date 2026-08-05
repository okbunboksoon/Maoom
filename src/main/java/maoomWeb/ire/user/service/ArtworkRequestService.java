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

/**
 * 도안의뢰서 작성 기능의 실제 생성 로직.
 *
 * <p>입력 경로는 결과 폴더를 만들 서버 PC 기준 폴더이고, 업로드 PDF는
 * 임시 작업 폴더에 저장한 뒤 {@link PIEService}로 전달한다. 최종 산출물은
 * 입력 경로 아래 {@code yyyyMMdd_Result_Folder_ImageExtractor} 폴더에
 * {@code 원본PDF명_도안의뢰서.xlsx} 이름으로 생성된다.</p>
 */
@Service
public class ArtworkRequestService {

    private static final DateTimeFormatter RESULT_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final PIEService pieService =
            new PIEService();

    /**
     * PDF 업로드부터 엑셀 생성까지 한 번에 수행한다.
     *
     * <p>사용자가 보낸 파일은 MultipartFile 상태로는 외부 PDFImageExtractor가
     * 읽을 수 없으므로 임시 폴더에 실제 PDF 파일로 저장한 뒤 처리한다.</p>
     */
    public ArtworkRequestResult create(
            String inputPath,
            MultipartFile file) throws Exception {
        validatePdf(file);
        Path inputDirectory = validateInputDirectory(inputPath);
        // 생성 결과는 원본 PDF 폴더와 같은 위치의 날짜별 결과 폴더에 모은다.
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
            // PIEService는 파일 경로 기반 API라 업로드 파일을 먼저 실제 파일로 내려놓는다.
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

    /** 결과 폴더를 만들 기준 경로가 서버에서 접근 가능한 폴더인지 확인한다. */
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

    /** 도안의뢰서 생성 대상은 PDF 한 건이므로 빈 파일과 PDF가 아닌 파일을 먼저 차단한다. */
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

    /** 업로드 PDF를 저장했던 임시 작업 폴더를 실행 성공/실패와 관계없이 정리한다. */
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
