package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import AutomaticNoticeGen.ANGServiceImpl;
import maoomWeb.ire.user.dto.AutomaticNoticeResult;

/** 협조문 자동 완성 기능의 실행 로직. */
@Service
public class AutomaticNoticeService {

    private static final DateTimeFormatter RUN_INFO_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AutomaticNoticeRuleWorkbookService ruleWorkbookService;

    @Autowired
    public AutomaticNoticeService(
            AutomaticNoticeRuleWorkbookService ruleWorkbookService) {
        this.ruleWorkbookService = ruleWorkbookService;
    }

    public AutomaticNoticeService() {
        this.ruleWorkbookService = null;
    }

    public AutomaticNoticeResult create(
            String inputPath,
            MultipartFile file) throws Exception {
        validatePdf(file);
        Path inputDirectory = validateInputDirectory(inputPath);

        String originalFileName = Path.of(file.getOriginalFilename())
                .getFileName()
                .toString();
        String baseName = originalFileName.replaceFirst("(?i)\\.pdf$", "");
        String market = detectMarket(baseName);
        String rulesFileName = rulesFileName(market);

        Path resultDirectory = inputDirectory.resolve(
                baseName + "_협조문자동완성");
        Files.createDirectories(resultDirectory);

        Path rulesPath = resultDirectory.resolve(rulesFileName);
        prepareRuntimeRules(market, rulesFileName, rulesPath);

        Path resultPath = resultDirectory.resolve(
                baseName + "_설계중점_확인사항.xlsx");
        Path workDirectory = Files.createTempDirectory(
                "automatic-notice-");
        Path workPdf = workDirectory.resolve(originalFileName);

        try{
            file.transferTo(workPdf);
            new ANGServiceImpl(resultDirectory).exportExcel(
                    workPdf.toString(),
                    resultPath.toString());
            writeRunInfo(
                    resultDirectory,
                    originalFileName,
                    market,
                    resultPath,
                    rulesPath,
                    "SUCCESS",
                    null);
        }catch(Exception exception){
            writeRunInfo(
                    resultDirectory,
                    originalFileName,
                    market,
                    resultPath,
                    rulesPath,
                    "FAILED",
                    exception.getMessage());
            throw exception;
        }finally{
            deleteDirectory(workDirectory);
        }

        return new AutomaticNoticeResult(
                true,
                originalFileName,
                file.getSize(),
                market,
                resultDirectory.toString(),
                resultPath.toString(),
                rulesPath.toString(),
                "협조문 자동 완성이 완료되었습니다.");
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

    private String detectMarket(String baseName) {
        String normalized = Normalizer.normalize(
                baseName == null ? "" : baseName,
                Normalizer.Form.NFKC);
        String[] tokens = normalized.split("_");
        for(String token : tokens){
            if("KO".equalsIgnoreCase(token)){
                return "KO";
            }
            if("US".equalsIgnoreCase(token)
                    || "CA".equalsIgnoreCase(token)
                    || "MX".equalsIgnoreCase(token)){
                return "US";
            }
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:^|[_\\-\\.])(KO|US|CA|MX)(?:[_\\-\\.]|$)")
                .matcher(normalized);
        if(matcher.find()){
            return matcher.group(1).equalsIgnoreCase("KO") ? "KO" : "US";
        }
        return "EG";
    }

    private String rulesFileName(String market) {
        return switch(market){
            case "KO" -> "ANG_ko_rules.xlsx";
            case "US" -> "ANG_us_rules.xlsx";
            default -> "ANG_eg_rules.xlsx";
        };
    }

    private void copyBundledRules(
            String rulesFileName,
            Path destination) throws IOException {
        String resourcePath = "/notice-rules/" + rulesFileName;
        try(InputStream input = AutomaticNoticeService.class
                .getResourceAsStream(resourcePath)){
            if(input == null){
                throw new IOException(
                        "협조문 규칙 파일을 찾을 수 없습니다: " + resourcePath);
            }
            Files.copy(
                    input,
                    destination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prepareRuntimeRules(
            String market,
            String rulesFileName,
            Path destination) throws IOException {

        if(ruleWorkbookService != null){
            try{
                if(ruleWorkbookService.writeRuntimeRulesWorkbook(
                        market,
                        destination)){
                    return;
                }
            }catch(RuntimeException exception){
                /*
                 * 룰 DB 이관 초기에는 테이블이 없거나 비어 있을 수 있다.
                 * 기존 기능이 멈추지 않도록 번들 룰 엑셀로 fallback한다.
                 */
            }
        }

        copyBundledRules(rulesFileName, destination);
    }

    private void writeRunInfo(
            Path resultDirectory,
            String pdfName,
            String market,
            Path resultPath,
            Path rulesPath,
            String status,
            String errorMessage) throws IOException {
        String escapedError = errorMessage == null
                ? ""
                : errorMessage.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = """
                {
                  "pdf": "%s",
                  "market": "%s",
                  "output": "%s",
                  "rulesFile": "%s",
                  "status": "%s",
                  "error": "%s",
                  "runAt": "%s"
                }
                """.formatted(
                pdfName,
                market,
                resultPath.toString().replace("\\", "\\\\"),
                rulesPath.toString().replace("\\", "\\\\"),
                status,
                escapedError,
                LocalDateTime.now().format(RUN_INFO_DATE_FORMATTER));
        Files.writeString(
                resultDirectory.resolve("run_info.json"),
                json,
                StandardCharsets.UTF_8);
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
