package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.IndexExtractRequest;
import maoomWeb.ire.user.dto.IndexExtractResult;

/**
 * Index 추출 화면에서 들어온 DITA/topics 경로를 실제 배치 실행으로 연결한다.
 *
 * <p>브라우저가 보내는 경로 문자열은 서버 PC 기준 경로다. 이 서비스는 그 경로의
 * topics 원본을 {@code C:\Users\...\.maoomtool\index-*} 작업 폴더로 복사하고,
 * classpath의 {@code bat}, {@code xsl}, {@code lib} 리소스를 같은 작업 폴더에
 * 풀어낸 뒤 {@code 07_make-excel-index.bat}을 실행한다.</p>
 *
 * <p>BAT는 작업 폴더의 {@code topics}와 {@code temp}를 기준으로 동작하므로 원본
 * 폴더에서 직접 실행하지 않는다. 입력 경로에는 최종 결과 폴더와 로그만 남기고,
 * 실행이 끝난 뒤 작업 폴더는 삭제한다.</p>
 */
@Service
public class IndexExtractService {

    private static final String BATCH_FILE = "07_make-excel-index.bat";
    private static final String RESULT_FILE_NAME = "index-review.xlsx";
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(30);
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] RESOURCE_ROOTS = {
            "bat",
            "xsl",
            "lib"
    };
    private static final String[] REQUIRED_FILES = {
            BATCH_FILE,
            "xsl/catalog.xml",
            "xsl/dummy.xml",
            "xsl/0000-doctype-remove.xsl",
            "xsl/0001-namespace-remove.xsl",
            "xsl/0002-toc-create.xsl",
            "xsl/0003-bookmap-create.xsl",
            "xsl/0004-topic-merge.xsl",
            "xsl/0310-kus-beautify.xsl",
            "xsl/0350-indexterm-extract.xsl",
            "xsl/0360-excel-xml-create.xsl",
            "xsl/Convert_Xml_To_Excel_index.vbs",
            "lib/saxon-ee-10.0.jar",
            "lib/xml-resolver-1.2.jar"
    };

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    /**
     * Index 추출 버튼 클릭 한 번에 수행되는 전체 파이프라인.
     *
     * <ol>
     *   <li>입력 DITA 경로가 서버에서 접근 가능한 폴더인지 확인한다.</li>
     *   <li>레벨 값이 비어 있으면 기본 10으로 처리하고, 1~99 범위인지 확인한다.</li>
     *   <li>입력 폴더 안의 {@code topics}가 있으면 그 폴더를 쓰고,
     *       없으면 입력 폴더 자체를 topics 원본으로 본다.</li>
     *   <li>입력 경로 아래의 날짜별 Index 결과 폴더를 최종 결과 위치로 잡는다.</li>
     *   <li>{@code .maoomtool\index-*} 작업 폴더에 tool 리소스와 topics를 복사한다.</li>
     *   <li>BAT에 레벨 값을 인자로 넘겨 실행한다.</li>
     *   <li>작업 폴더의 {@code temp\excel.xlsx}를 결과 폴더의
     *       {@code index-review.xlsx}로 이동한다.</li>
     *   <li>배치 출력 로그를 {@code index-extract.log}로 저장하고 작업 폴더를 삭제한다.</li>
     * </ol>
     */
    public IndexExtractResult run(IndexExtractRequest request) {
        List<String> logs = new ArrayList<>();
        List<String> batchLogs = new ArrayList<>();
        Path inputDirectory = null;
        Path resultDirectory = null;
        Path workDirectory = null;

        try{
            // 1. 사용자가 입력한 경로는 브라우저 PC가 아니라 서버 PC 기준으로 해석된다.
            inputDirectory = validateInputDirectory(
                    request == null ? null : request.ditaPath());
            // 2. 화면의 레벨 입력값이 비어 있으면 기본 10을 사용한다.
            int indexLevel = resolveIndexLevel(
                    request == null ? null : request.indexLevel());
            // 3. 입력 폴더가 상위 폴더인지 topics 폴더 자체인지 자동 판별한다.
            Path sourceTopicsDirectory = resolveSourceTopicsDirectory(
                    inputDirectory);
            // 4. 최종 산출물은 입력 경로 아래의 날짜별 Index 결과 폴더에 둔다.
            resultDirectory = resolveResultDirectory(inputDirectory);
            // 5. BAT 실행 중간 산출물이 원본과 섞이지 않도록 매 실행마다 별도 작업 폴더를 쓴다.
            workDirectory = createWorkDirectory(inputDirectory);

            // 6. JAR/classpath 리소스를 일반 파일로 풀어 BAT가 접근할 수 있게 한다.
            prepareToolDirectory(workDirectory);
            // 7. 원본 topics를 작업 폴더로 복사한다. 기존 Result_Folder 계열은 제외한다.
            copyDirectory(
                    sourceTopicsDirectory,
                    workDirectory.resolve("topics"),
                    resultDirectory);
            Files.createDirectories(workDirectory.resolve("temp"));

            logs.add("작업 폴더: " + workDirectory);
            logs.add("DITA 입력: " + inputDirectory);
            logs.add("topics 원본: " + sourceTopicsDirectory);
            logs.add("추출 레벨: " + indexLevel);

            // 8. 실제 07_make-excel-index.bat 실행 지점. 레벨 값은 BAT 인자로 전달된다.
            runBatch(workDirectory, indexLevel, logs, batchLogs);

            Path sourceReport = workDirectory
                    .resolve("temp")
                    .resolve("excel.xlsx");
            Path targetReport = resultDirectory.resolve(RESULT_FILE_NAME);
            // 9. BAT가 만든 엑셀을 입력 경로의 결과 폴더로 이동한다.
            moveReportFile(sourceReport, targetReport);
            // 10. 배치 출력은 결과 폴더 로그로 남겨 화면 오류 추적에 사용한다.
            writeIndexLog(resultDirectory, batchLogs);
            logs.add("인덱스 엑셀 이동 완료: " + targetReport);

            return new IndexExtractResult(
                    inputDirectory.toString(),
                    indexLevel,
                    targetReport.toString(),
                    List.copyOf(logs));
        }catch(IOException exception){
            writeFailureLog(inputDirectory, resultDirectory, batchLogs);
            throw new IllegalArgumentException(
                    "Index 추출 파일 처리 중 오류가 발생했습니다: "
                    + exception.getMessage(),
                    exception);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            writeFailureLog(inputDirectory, resultDirectory, batchLogs);
            throw new IllegalArgumentException(
                    "Index 추출 배치가 중단되었습니다.",
                    exception);
        }finally{
            try{
                deleteDirectory(workDirectory);
            }catch(IOException ignored){
                // 작업 폴더 정리 실패는 결과 생성 여부에 영향을 주지 않는다.
            }
        }
    }

    /** 사용자가 입력한 DITA 경로를 서버 PC 기준 절대경로로 정규화하고 존재 여부를 확인한다. */
    private Path validateInputDirectory(String ditaPath) {
        if(ditaPath == null || ditaPath.isBlank()){
            throw new IllegalArgumentException("DITA 경로를 입력해 주세요.");
        }

        Path inputDirectory = Path.of(ditaPath.trim())
                .toAbsolutePath()
                .normalize();

        if(!Files.isDirectory(inputDirectory)){
            throw new IllegalArgumentException(
                    "DITA 경로를 찾지 못했습니다: " + inputDirectory);
        }

        return inputDirectory;
    }

    /** 화면에서 받은 출력 레벨을 검증한다. 값이 없으면 기본 10레벨까지 출력한다. */
    private int resolveIndexLevel(Integer indexLevel) {
        if(indexLevel == null){
            return 10;
        }

        if(indexLevel < 1 || indexLevel > 99){
            throw new IllegalArgumentException(
                    "Index 레벨은 1~99 사이 숫자로 입력해 주세요.");
        }

        return indexLevel;
    }

    /**
     * 입력 경로가 상위 폴더면 하위 topics를 사용하고, 입력 경로 자체가 topics면 그대로 사용한다.
     */
    private Path resolveSourceTopicsDirectory(Path inputDirectory) {
        Path topicsDirectory = inputDirectory.resolve("topics");
        if(Files.isDirectory(topicsDirectory)){
            return topicsDirectory;
        }
        return inputDirectory;
    }

    /**
     * DITA 입력 경로 아래에 날짜별 Index 결과 폴더를 만든다.
     * 예: 입력이 {@code V:\Tools\test}면 {@code V:\Tools\test\260804_Result_Folder_Index} 형태가 된다.
     */
    private Path resolveResultDirectory(Path inputDirectory) {
        Path resultDirectory = ResultFolderNames.resolve(
                inputDirectory,
                "Index");

        if(Files.exists(resultDirectory) && !Files.isDirectory(resultDirectory)){
            throw new IllegalArgumentException(
                    "결과 폴더 경로가 폴더가 아닙니다: " + resultDirectory);
        }

        return resultDirectory;
    }

    /** .maoomtool 아래에 실행별 임시 작업 폴더명을 만든다. 실제 생성은 prepare/copy 단계에서 한다. */
    private Path createWorkDirectory(Path inputDirectory) {
        String folderName = inputDirectory.getFileName() == null
                ? "dita"
                : inputDirectory.getFileName().toString();
        String safeName = folderName.replaceAll("[^A-Za-z0-9._-]", "_");

        if(safeName.isBlank()){
            safeName = "dita";
        }

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "index-" + safeName + "-" + RUN_ID_FORMAT.format(
                        LocalDateTime.now()))
                .toAbsolutePath()
                .normalize();
    }

    /**
     * classpath의 bat/xsl/lib 리소스를 작업 폴더로 복사한다.
     * JAR 안 리소스는 BAT/Java 프로세스가 일반 파일 경로로 읽을 수 없으므로,
     * 실행 전에 반드시 실제 파일 시스템 위치로 풀어야 한다.
     */
    private void prepareToolDirectory(Path workDirectory) throws IOException {
        for(String resourceRoot : RESOURCE_ROOTS){
            copyResourceDirectory(resourceRoot, workDirectory.resolve(resourceRoot));
        }

        Files.move(
                workDirectory.resolve("bat").resolve(BATCH_FILE),
                workDirectory.resolve(BATCH_FILE),
                StandardCopyOption.REPLACE_EXISTING);

        for(String requiredFile : REQUIRED_FILES){
            Path requiredPath = workDirectory.resolve(requiredFile);
            if(!Files.isRegularFile(requiredPath)){
                throw new IllegalArgumentException(
                        "Index 추출 필수 파일을 찾지 못했습니다: "
                        + requiredFile);
            }
        }
    }

    /** classpath 리소스 폴더 하나를 작업 폴더의 대응 위치로 복사한다. */
    private void copyResourceDirectory(String resourceRoot, Path target)
            throws IOException {
        Resource[] resources = resourceResolver.getResources(
                "classpath*:" + resourceRoot + "/**/*");

        for(Resource resource : resources){
            if(!resource.exists() || !resource.isReadable()){
                continue;
            }

            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.lastIndexOf(resourceRoot + "/");
            if(index < 0){
                continue;
            }

            String relativePath = url.substring(
                    index + (resourceRoot + "/").length());
            if(relativePath.isBlank() || relativePath.endsWith("/")){
                continue;
            }

            Path destination = target.resolve(relativePath).normalize();
            if(!destination.startsWith(target.normalize())){
                throw new IllegalArgumentException(
                        "Index 추출 리소스 경로가 올바르지 않습니다: "
                        + relativePath);
            }

            Files.createDirectories(destination.getParent());
            try(var input = resource.getInputStream()){
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * source 폴더를 target으로 복사한다.
     * 입력 경로 안에 이전 실행 결과 폴더가 있으면 다시 처리하지 않도록 제외한다.
     */
    private void copyDirectory(
            Path source,
            Path target,
            Path excludedDirectory)
            throws IOException {
        if(!Files.isDirectory(source)){
            throw new IllegalArgumentException(
                    "topics로 복사할 DITA 폴더를 찾지 못했습니다: " + source);
        }

        deleteDirectory(target);
        Files.createDirectories(target);
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedExcludedDirectory = excludedDirectory == null
                ? null
                : excludedDirectory.toAbsolutePath().normalize();

        try(Stream<Path> stream = Files.walk(source)){
            for(Path sourcePath : stream.toList()){
                Path normalizedSourcePath =
                        sourcePath.toAbsolutePath().normalize();

                if(normalizedExcludedDirectory != null
                        && normalizedExcludedDirectory.startsWith(normalizedSource)
                        && normalizedSourcePath.startsWith(normalizedExcludedDirectory)){
                    continue;
                }

                Path relativePath = source.relativize(sourcePath);
                if(relativePath.getNameCount() > 0
                        && ResultFolderNames.isGeneratedResultFolder(
                                relativePath.getName(0).toString())){
                    continue;
                }

                Path targetPath = target.resolve(relativePath).normalize();
                if(!targetPath.startsWith(target)){
                    throw new IllegalArgumentException(
                            "DITA 경로가 올바르지 않습니다: " + sourcePath);
                }

                if(Files.isDirectory(sourcePath)){
                    Files.createDirectories(targetPath);
                }else if(Files.isRegularFile(sourcePath)){
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(
                            sourcePath,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /**
     * 작업 폴더에서 Index 추출 BAT를 실행한다.
     *
     * <p>BAT 마지막에 pause가 있어 서버 프로세스가 멈출 수 있으므로
     * {@code echo. | call ...bat 10} 형태로 빈 Enter를 흘려보내 pause를 자동 통과시킨다.
     * 사용자가 입력한 레벨은 BAT 인자로 넘기며, BAT 내부에서 XSL 파라미터
     * {@code indexLevel}로 다시 전달된다.</p>
     */
    private void runBatch(
            Path workDirectory,
            int indexLevel,
            List<String> logs,
            List<String> batchLogs)
            throws IOException, InterruptedException {
        Path batchFile = workDirectory.resolve(BATCH_FILE);
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "echo. | call \"" + batchFile + "\" " + indexLevel);
        builder.directory(workDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        try(BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        BATCH_OUTPUT_CHARSET))){
            String line;
            while((line = reader.readLine()) != null){
                if(!line.isBlank()){
                    logs.add(line);
                    batchLogs.add(line);
                }
            }
        }

        boolean finished = process.waitFor(
                BATCH_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS);

        if(!finished){
            process.destroyForcibly();
            throw new IllegalArgumentException(
                    "Index 추출 배치 시간이 초과되었습니다.");
        }

        if(process.exitValue() != 0){
            throw new IllegalArgumentException(
                    "Index 추출 배치가 실패했습니다. "
                    + String.join(" / ", tail(logs, 12)));
        }

        if(!Files.isRegularFile(workDirectory
                .resolve("temp")
                .resolve("excel.xlsx"))){
            throw new IllegalArgumentException(
                    "Index 추출 엑셀이 생성되지 않았습니다.");
        }
    }

    /** 실패 메시지가 너무 길어지지 않도록 최근 로그 일부만 잘라낸다. */
    private List<String> tail(List<String> logs, int count) {
        int fromIndex = Math.max(0, logs.size() - count);
        return logs.subList(fromIndex, logs.size());
    }

    /** Index 검토 엑셀을 결과 폴더 바로 아래로 이동한다. */
    private void moveReportFile(Path source, Path target)
            throws IOException {
        if(!Files.isRegularFile(source)){
            throw new IllegalArgumentException(
                    "Index 추출 엑셀을 찾지 못했습니다: " + source);
        }

        Files.createDirectories(target.getParent());
        Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** 배치 출력 로그를 결과 폴더에 저장한다. */
    private void writeIndexLog(Path resultDirectory, List<String> logs)
            throws IOException {
        Files.createDirectories(resultDirectory);
        Files.write(
                resultDirectory.resolve("index-extract.log"),
                logs,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** 실패 시에도 가능한 범위에서 결과 폴더에 index-extract.log를 남긴다. */
    private void writeFailureLog(
            Path inputDirectory,
            Path resultDirectory,
            List<String> logs) {
        Path logDirectory = resultDirectory;

        if(logDirectory == null && inputDirectory != null){
            logDirectory = ResultFolderNames.resolve(inputDirectory, "Index");
        }

        if(logDirectory == null){
            return;
        }

        try{
            writeIndexLog(logDirectory, logs);
        }catch(IOException ignored){
            // 실패 로그 저장 실패는 원래 예외 메시지를 가리지 않는다.
        }
    }

    /** 작업 폴더와 기존 결과 폴더를 안전하게 비우기 위한 재귀 삭제 유틸리티. */
    private void deleteDirectory(Path directory) throws IOException {
        if(directory == null || !Files.exists(directory)){
            return;
        }

        try(Stream<Path> stream = Files.walk(directory)){
            for(Path path : stream
                    .sorted(Comparator.reverseOrder())
                    .toList()){
                Files.deleteIfExists(path);
            }
        }
    }
}
