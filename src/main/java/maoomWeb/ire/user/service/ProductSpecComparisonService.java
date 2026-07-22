package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.ProductSpecComparisonRequest;
import maoomWeb.ire.user.dto.ProductSpecComparisonResult;

/**
 * 제품사양서 비교 화면의 요청을 실제 배치 실행으로 연결한다.
 *
 * <p>이 서비스는 정제/BER/QSG와 같은 실행 모델을 따른다. 사용자가 입력한
 * V서버/H서버 경로는 원본 위치로만 사용하고, 실제 배치는 매 실행마다 생성하는
 * {@code %USERPROFILE%\.maoomtool\product-spec-comparison-*} 작업 폴더에서
 * 수행한다. 이렇게 해야 원본 폴더와 배치 중간 산출물이 섞이지 않고, 실행 후
 * 작업 폴더를 안전하게 삭제할 수 있다.</p>
 *
 * <ol>
 *   <li>입력 경로와 Before/After 제품사양서 파일명을 검증한다.</li>
 *   <li>실행별 작업 폴더와 {@code temp} 폴더를 만든다.</li>
 *   <li>classpath의 {@code bat}, {@code xsl}, {@code lib} 리소스를 작업 폴더로 복사한다.</li>
 *   <li>Before 파일을 temp에 복사하고 {@code 02_Spec_Filter_To_Xml.bat}로 {@code excel_before.xml}을 만든다.</li>
 *   <li>After 파일도 같은 Key 옵션으로 {@code excel_after.xml}을 만든다.</li>
 *   <li>{@code 03_make_excel_comparison.bat}로 비교 XML/XLSX를 생성한다.</li>
 *   <li>작업 폴더의 temp 산출물을 입력 경로의 {@code Result_Folder}로 복사하고 로그를 저장한다.</li>
 *   <li>성공/실패와 관계없이 작업 폴더를 삭제한다.</li>
 * </ol>
 */
@Service
public class ProductSpecComparisonService {

    private static final String BAT_ROOT = "bat";
    private static final String XSL_ROOT = "xsl";
    private static final String LIB_ROOT = "lib";
    private static final String SPEC_FILTER_BATCH =
            "02_Spec_Filter_To_Xml.bat";
    private static final String COMPARISON_BATCH =
            "03_make_excel_comparison.bat";
    private static final String RESULT_FILE =
            "Product_Equipment_List_Comparison.xlsx";
    private static final String LOG_FILE =
            "product-spec-comparison.log";
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(30);
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[^\\\\/:*?\"<>|]+\\.(?i:xlsx|xls)");
    private static final Pattern SAFE_KEYS =
            Pattern.compile("(?i:k\\d+)(\\s+(?i:k\\d+))*");
    private static final String[] REQUIRED_FILES = {
            SPEC_FILTER_BATCH,
            COMPARISON_BATCH,
            "lib/Saxon-HE-10.6.jar",
            "lib/xml-resolver-1.2.jar",
            "xsl/0200-oneline.xsl",
            "xsl/0300-make-excel-text.xsl",
            "xsl/0301-excel-comparison.xsl",
            "xsl/0400-normalize.xsl",
            "xsl/0401-highlight.xsl",
            "xsl/0402-product-equipment-list-comparison.xsl",
            "xsl/z02_xtractColumns.vbs",
            "xsl/z03_resultTorefine.vbs",
            "xsl/Convert_Xml_To_Excel_comparison.vbs",
            "xsl/Author.xml",
            "xsl/important_items.xml"
    };

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();
    private volatile Path lastResultFile;

    /**
     * 제품사양서 비교 버튼 한 번에 수행되는 전체 파이프라인.
     *
     * <p>성공 시 {@code 입력경로\Result_Folder\Product_Equipment_List_Comparison.xlsx}
     * 경로를 반환한다. 실패해도 가능한 경우 {@code Result_Folder}에
     * {@code product-spec-comparison.log}를 남긴다.</p>
     */
    public synchronized ProductSpecComparisonResult run(
            ProductSpecComparisonRequest request) {

        List<String> logs = new ArrayList<>();
        Path inputDirectory = null;
        Path resultDirectory = null;
        Path workspace = null;

        try {
            inputDirectory = resolveInputDirectory(
                    request == null ? null : request.inputPath());
            Path beforeFile = resolveInputFile(
                    inputDirectory,
                    request == null ? null : request.beforeFileName());
            Path afterFile = resolveInputFile(
                    inputDirectory,
                    request == null ? null : request.afterFileName());
            String keys = normalizeKeys(request);

            resultDirectory = inputDirectory.resolve("Result_Folder");
            workspace = createWorkDirectory(inputDirectory);
            prepareToolDirectory(workspace);
            Files.createDirectories(workspace.resolve("temp"));
            validateRequiredFiles(workspace);

            logs.add("작업 폴더: " + workspace);
            logs.add("입력 경로: " + inputDirectory);
            logs.add("Before 파일: " + beforeFile.getFileName());
            logs.add("After 파일: " + afterFile.getFileName());

            copyAndCreateXml(
                    workspace,
                    beforeFile,
                    workspace.resolve("temp").resolve("excel_before.xml"),
                    keys,
                    logs);
            copyAndCreateXml(
                    workspace,
                    afterFile,
                    workspace.resolve("temp").resolve("excel_after.xml"),
                    keys,
                    logs);

            runBatch(workspace, COMPARISON_BATCH, null, logs);

            Path workspaceResult = workspace.resolve("temp").resolve(RESULT_FILE);
            if (!Files.isRegularFile(workspaceResult)) {
                throw new IllegalArgumentException(
                        "비교 결과 엑셀이 생성되지 않았습니다: " + workspaceResult);
            }

            replaceResultDirectory(workspace.resolve("temp"), resultDirectory);
            Path resultFile = resultDirectory.resolve(RESULT_FILE);
            writeLog(resultDirectory, logs);
            lastResultFile = resultFile;

            logs.add("완료: " + resultDirectory);
            return new ProductSpecComparisonResult(
                    true,
                    resultFile.toString(),
                    List.copyOf(logs));
        } catch (Exception exception) {
            logs.add("오류: " + formatException(exception));
            writeFailureLog(inputDirectory, resultDirectory, logs);
            return new ProductSpecComparisonResult(
                    false,
                    null,
                    List.copyOf(logs));
        } finally {
            deleteDirectoryQuietly(workspace);
        }
    }

    public Path getResultFile() {
        return lastResultFile;
    }

    /** 화면에서 받은 입력 경로를 서버 PC 기준 절대 경로로 정규화하고 폴더 존재 여부를 확인한다. */
    Path resolveInputDirectory(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("입력 경로를 입력해 주세요.");
        }

        Path inputDirectory = Path.of(inputPath.trim())
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(inputDirectory)) {
            throw new IllegalArgumentException(
                    "입력 경로를 찾지 못했습니다: " + inputDirectory);
        }
        return inputDirectory;
    }

    /**
     * 입력 경로 안에서 제품사양서 파일명을 찾는다.
     *
     * <p>파일명만 허용한다. {@code ..\foo.xlsx} 같은 경로 조작이나 다른 확장자는
     * 원본 폴더 밖 접근을 막기 위해 거부한다.</p>
     */
    Path resolveInputFile(Path inputDirectory, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "제품사양서 파일명을 입력해 주세요.");
        }

        String trimmed = fileName.trim();
        if (!SAFE_FILE_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "xlsx 또는 xls 파일명만 입력할 수 있습니다: " + trimmed);
        }

        Path inputFile = inputDirectory.resolve(trimmed)
                .toAbsolutePath()
                .normalize();
        if (!inputFile.startsWith(inputDirectory)) {
            throw new IllegalArgumentException(
                    "제품사양서 파일명이 올바르지 않습니다: " + trimmed);
        }
        if (!Files.isRegularFile(inputFile)) {
            throw new IllegalArgumentException(
                    "제품사양서 파일을 찾지 못했습니다: " + inputFile);
        }

        return inputFile;
    }

    /** 전체 추출이면 빈 문자열, 특정 Key 추출이면 {@code K1 K2 K3} 형식으로 정규화한다. */
    private String normalizeKeys(ProductSpecComparisonRequest request) {
        if (request == null
                || request.keyMode() == null
                || !"SPECIFIC".equalsIgnoreCase(request.keyMode())) {
            return "";
        }

        String keys = request.keys() == null
                ? ""
                : request.keys().trim().replaceAll("\\s+", " ");
        if (keys.isBlank()) {
            throw new IllegalArgumentException(
                    "특정 Key 추출을 선택한 경우 Key를 입력해 주세요.");
        }
        if (!SAFE_KEYS.matcher(keys).matches()) {
            throw new IllegalArgumentException(
                    "Key는 K1 K2 K3 형식으로 입력해 주세요.");
        }

        return keys.toUpperCase(Locale.ROOT);
    }

    /** 정제/BER처럼 사용자 홈의 .maoomtool 아래에 실행별 작업 폴더명을 만든다. */
    private Path createWorkDirectory(Path inputDirectory) {
        String folderName = inputDirectory.getFileName() == null
                ? "product-spec"
                : inputDirectory.getFileName().toString();
        String safeName = folderName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isBlank()) {
            safeName = "product-spec";
        }

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "product-spec-comparison-" + safeName + "-"
                + LocalDateTime.now().format(RUN_FORMAT))
                .toAbsolutePath()
                .normalize();
    }

    /** 배치가 일반 파일 경로로 읽을 수 있도록 classpath 리소스를 작업 폴더로 푼다. */
    private void prepareToolDirectory(Path workspace) throws IOException {
        copySharedResourceDirectory(BAT_ROOT, workspace, false);
        copySharedResourceDirectory(LIB_ROOT, workspace.resolve("lib"), true);
        copySharedResourceDirectory(XSL_ROOT, workspace.resolve("xsl"), true);
    }

    /** 배치 실행 전에 필요한 배치/XSL/VBS/XML/lib 파일이 모두 복사됐는지 확인한다. */
    private void validateRequiredFiles(Path workspace) {
        for (String requiredFile : REQUIRED_FILES) {
            Path file = workspace.resolve(requiredFile);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException(
                        "제품사양서 비교 필수 파일을 찾지 못했습니다: "
                        + requiredFile);
            }
        }
    }

    /**
     * 제품사양서 하나를 temp에 복사하고 {@code 02_Spec_Filter_To_Xml.bat}를 실행해 XML 하나를 만든다.
     *
     * <p>Before와 After 모두 같은 {@code keys} 값을 사용한다. 전체 추출이면 빈 Enter를,
     * 특정 Key 추출이면 {@code K1 K2} 같은 값을 배치의 {@code set /p KEYS} 입력으로 흘려보낸다.</p>
     */
    private void copyAndCreateXml(
            Path workspace,
            Path sourceWorkbook,
            Path targetXml,
            String keys,
            List<String> logs)
            throws IOException, InterruptedException {

        Path tempDirectory = workspace.resolve("temp");
        cleanTempInputAndIntermediateFiles(tempDirectory);
        Files.copy(
                sourceWorkbook,
                tempDirectory.resolve(sourceWorkbook.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        runBatch(workspace, SPEC_FILTER_BATCH, keys, logs);

        Path excelXml = tempDirectory.resolve("excel.xml");
        if (!Files.isRegularFile(excelXml)) {
            throw new IllegalArgumentException("excel.xml이 생성되지 않았습니다.");
        }

        Files.move(
                excelXml,
                targetXml,
                StandardCopyOption.REPLACE_EXISTING);
        logs.add(targetXml.getFileName() + " 생성 완료");
    }

    /** 작업 폴더에서 BAT를 실행하고 MS949 콘솔 출력을 화면/파일 로그용 리스트에 모은다. */
    private void runBatch(
            Path workspace,
            String batchFileName,
            String keys,
            List<String> logs)
            throws IOException, InterruptedException {

        Path batchFile = workspace.resolve(batchFileName);
        String command = buildBatchCommand(batchFile.getFileName().toString(), keys);
        Process process = new ProcessBuilder("cmd.exe", "/c", command)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        BATCH_OUTPUT_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    logs.add(line);
                }
            }
        }

        boolean finished = process.waitFor(
                BATCH_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalArgumentException(
                    batchFileName + " 실행 시간이 초과되었습니다.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalArgumentException(
                    batchFileName + " 실행에 실패했습니다.");
        }
    }

    /** 02 배치의 Keys 입력과 각 배치 마지막 pause를 자동 통과시키기 위한 cmd 명령을 만든다. */
    private String buildBatchCommand(String batchFileName, String keys) {
        if (SPEC_FILTER_BATCH.equals(batchFileName)) {
            String firstLine = keys == null || keys.isBlank()
                    ? "echo."
                    : "echo " + keys;
            return "(" + firstLine + " & echo.) | call \""
                    + batchFileName + "\"";
        }

        return "echo. | call \"" + batchFileName + "\"";
    }

    /** 최종으로 사용자에게 남길 비교 산출물을 입력 경로의 Result_Folder로 복사한다. */
    private void replaceResultDirectory(Path workspaceTemp, Path resultDirectory)
            throws IOException {

        deleteDirectory(resultDirectory);
        Files.createDirectories(resultDirectory);

        for (String fileName : List.of(
                "excel_before.xml",
                "excel_after.xml",
                "excel_comparison.xml",
                "normalized.xml",
                "test2.xml",
                "Product_Equipment_List_Comparison.xml",
                RESULT_FILE)) {
            Path source = workspaceTemp.resolve(fileName);
            if (Files.isRegularFile(source)) {
                Files.copy(
                        source,
                        resultDirectory.resolve(fileName),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Before/After를 각각 처리하기 전에 temp에 남아 있는 입력 엑셀과 02 배치 중간 파일을 정리한다. */
    private void cleanTempInputAndIntermediateFiles(Path tempDirectory)
            throws IOException {
        Files.createDirectories(tempDirectory);
        for (String fileName : List.of(
                "excel.xml",
                "onelined.xml",
                "refined.xml",
                "result.xlsx")) {
            Files.deleteIfExists(tempDirectory.resolve(fileName));
        }

        try (Stream<Path> stream = Files.list(tempDirectory)) {
            for (Path path : stream.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String name = path.getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT);
                if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /** 실행 로그를 Result_Folder에 UTF-8 텍스트로 저장한다. */
    private void writeLog(Path resultDirectory, List<String> logs)
            throws IOException {
        Files.createDirectories(resultDirectory);
        Files.write(
                resultDirectory.resolve(LOG_FILE),
                logs,
                StandardCharsets.UTF_8);
    }

    /** 실패 시에도 입력 경로를 알 수 있으면 Result_Folder에 로그를 남긴다. */
    private void writeFailureLog(
            Path inputDirectory,
            Path resultDirectory,
            List<String> logs) {

        Path logDirectory = resultDirectory;
        if (logDirectory == null && inputDirectory != null) {
            logDirectory = inputDirectory.resolve("Result_Folder");
        }
        if (logDirectory == null) {
            return;
        }

        try {
            writeLog(logDirectory, logs);
        } catch (IOException logException) {
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
        }
    }

    /** classpath의 bat/xsl/lib 폴더를 파일 시스템 작업 폴더로 복사한다. */
    private void copySharedResourceDirectory(
            String resourceRoot,
            Path target,
            boolean cleanTarget)
            throws IOException {

        if (cleanTarget) {
            deleteDirectory(target);
        }

        ClassPathResource root = new ClassPathResource(resourceRoot);
        if (root.isFile()) {
            copyDirectory(root.getFile().toPath(), target, Set.of());
            return;
        }

        Resource[] resources = resourceResolver.getResources(
                "classpath*:" + resourceRoot + "/**/*");

        for (Resource resource : resources) {
            if (!resource.exists() || !resource.isReadable()) {
                continue;
            }

            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.lastIndexOf(resourceRoot + "/");
            if (index < 0) {
                continue;
            }

            String relativePath = url.substring(
                    index + (resourceRoot + "/").length());
            if (relativePath.isBlank() || relativePath.endsWith("/")) {
                continue;
            }

            Path destination = target.resolve(relativePath).normalize();
            if (!destination.startsWith(target.normalize())) {
                throw new IllegalArgumentException(
                        "리소스 경로가 올바르지 않습니다: " + relativePath);
            }

            Files.createDirectories(destination.getParent());
            try (var input = resource.getInputStream()) {
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** 파일 시스템에 있는 리소스 폴더를 재귀 복사한다. 개발 환경의 exploded classpath 처리용이다. */
    private void copyDirectory(
            Path source,
            Path target,
            Set<String> excludedNames)
            throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "복사할 폴더를 찾지 못했습니다: " + source);
        }

        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path sourcePath : stream.toList()) {
                Path relative = source.relativize(sourcePath);
                if (relative.toString().isBlank()) {
                    continue;
                }
                if (excludedNames.contains(relative.getFileName().toString())) {
                    continue;
                }

                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target.normalize())) {
                    throw new IllegalArgumentException(
                            "복사 경로가 올바르지 않습니다: " + relative);
                }

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(sourcePath)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            sourcePath,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String formatException(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getName()
                : exception.getMessage();
    }

    private void deleteDirectoryQuietly(Path directory) {
        try {
            deleteDirectory(directory);
        } catch (IOException ignored) {
            // cleanup failure should not hide the batch result
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
