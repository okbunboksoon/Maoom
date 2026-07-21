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

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.BerApplyResult;

/**
 * BER 반영 화면에서 들어온 DITA/topics 경로를 실제 배치 실행으로 연결한다.
 *
 * <p>브라우저가 보내는 경로 문자열은 서버 PC 기준 경로다. 이 서비스는 그 경로의
 * topics 원본을 {@code C:\Users\...\.maoomtool\ber-*} 작업 폴더로 복사하고,
 * JAR 내부 {@code revision-tool} 리소스(BAT, lib, XSL, DB XML)를 같은 작업 폴더에
 * 풀어낸 뒤 BER BAT를 실행한다.</p>
 *
 * <p>BAT는 작업 폴더의 {@code topics}와 {@code temp}를 기준으로 동작하므로 원본
 * 폴더에서 직접 실행하지 않는다. 이렇게 해야 원본 DITA와 결과 폴더가 섞이지 않고,
 * 실행이 끝난 뒤 작업 폴더를 통째로 삭제할 수 있다.</p>
 */
@Service
public class BerApplyService {

    private static final String RESOURCE_ROOT = "revision-tool";
    private static final String SHARED_BAT_ROOT = "bat";
    private static final String SHARED_XSL_ROOT = "xsl";
    private static final String SHARED_LIB_ROOT = "lib";
    private static final String BER_BATCH =
            "04_KUS_asis-tobe-apply_NotFileNameChange.bat";
    /**
     * BAT가 직접 또는 간접으로 참조하는 필수 리소스 목록.
     * 누락된 파일이 있으면 BAT가 뒤 단계까지 계속 진행하며 원인을 흐리므로,
     * 실행 전에 여기서 먼저 막아 명확한 오류를 보여준다.
     */
    private static final String[] REQUIRED_TOOL_FILES = {
            BER_BATCH,
            "lib/saxon-ee-10.0.jar",
            "lib/xml-resolver-1.2.jar",
            "lib/saxon-license.lic",
            "xsl/0000-doctype-remove.xsl",
            "xsl/0001-namespace-remove.xsl",
            "xsl/0002-toc-create.xsl",
            "xsl/0003-bookmap-create.xsl",
            "xsl/0004-topic-merge.xsl",
            "xsl/0290-kus-text-normalize.xsl",
            "xsl/kus-normalize-text.xsl",
            "xsl/0300-kus-inline-normalize.xsl",
            "xsl/0340-kus-db-apply_ber_exclude.xsl",
            "xsl/0340-kus-db-apply_ber.xsl",
            "xsl/asis-tobe_eu.xml",
            "xsl/asis-tobe_us.xml",
            "xsl/asis-tobe_exclude.xml",
            "xsl/0310-kus-beautify.xsl",
            "xsl/kus-beautify.xsl",
            "xsl/0005-namespace-remove.xsl",
            "xsl/0006-id-clean_NotFileNameChange.xsl",
            "xsl/0007-xref-clean_NotFileNameChange.xsl",
            "xsl/0008-related-links_NotFileNameChange.xsl",
            "xsl/0210-topicalize_2.xsl",
            "xsl/0009-dita-rebeautify.xsl",
            "xsl/0410-make-change-report_ber.xsl",
            "xsl/Convert_Xml_To_Excel.vbs"
    };
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(30);
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    /**
     * BER 반영 버튼 클릭 한 번에 수행되는 전체 파이프라인.
     *
     * <ol>
     *   <li>입력 DITA 경로가 서버에서 접근 가능한 폴더인지 확인한다.</li>
     *   <li>입력 폴더 안의 {@code topics}가 있으면 그 폴더를 원본으로 쓰고,
     *       없으면 입력 폴더 자체를 topics 원본으로 본다.</li>
     *   <li>DITA 입력 경로 아래의 {@code Result_Folder}를 최종 결과 위치로 잡는다.</li>
     *   <li>{@code .maoomtool\ber-*} 작업 폴더에 tool 리소스와 topics를 복사한다.</li>
     *   <li>BAT를 실행하고, 작업 폴더의 topics 전체를 결과 topics로 이동한다.</li>
     *   <li>결과 topics와 리포트 엑셀을 최종 결과 위치로 이동한 뒤 작업 폴더를 삭제한다.</li>
     * </ol>
     */
    public BerApplyResult apply(String ditaPath) {
        List<String> logs = new ArrayList<>();
        List<String> batchLogs = new ArrayList<>();
        Path inputDirectory = null;
        Path resultDirectory = null;
        Path workDirectory = null;

        try{
            // 1. 사용자가 입력한 경로는 브라우저 PC가 아니라 서버 PC 기준으로 해석된다.
            inputDirectory = validateInputDirectory(ditaPath);
            // 2. 입력 폴더가 상위 폴더인지 topics 폴더 자체인지 자동 판별한다.
            Path sourceTopicsDirectory = resolveSourceTopicsDirectory(inputDirectory);
            // 3. 최종 산출물은 입력 경로 아래의 Result_Folder에 둔다.
            resultDirectory = resolveResultDirectory(inputDirectory);
            // 4. BAT 실행 중간 산출물이 원본과 섞이지 않도록 매 실행마다 별도 작업 폴더를 쓴다.
            workDirectory = createWorkDirectory(inputDirectory);
            // JAR 안에 포함된 revision-tool 전체를 일반 파일로 풀어 BAT가 접근할 수 있게 한다.
            prepareToolDirectory(workDirectory);
            copyDirectory(
                    sourceTopicsDirectory,
                    workDirectory.resolve("topics"),
                    resultDirectory);
            Files.createDirectories(workDirectory.resolve("temp"));

            logs.add("작업 폴더: " + workDirectory);
            logs.add("DITA 입력: " + inputDirectory);
            logs.add("topics 원본: " + sourceTopicsDirectory);
            // 실제 04_KUS_asis-tobe-apply_NotFileNameChange.bat 실행 지점.
            runBatch(workDirectory, logs, batchLogs);
            // BAT가 작업한 topics 폴더를 필터링하지 않고 그대로 결과로 내보낸다.
            validateResultTopicsDirectory(workDirectory.resolve("topics"));
            deleteDirectory(resultDirectory.resolve("temp"));
            moveReportFile(
                    workDirectory.resolve("temp")
                            .resolve("excel-change-report.xlsx"),
                    resultDirectory.resolve("excel-change-report.xlsx"));
            moveResultDirectory(
                    workDirectory.resolve("topics"),
                    resultDirectory.resolve("topics"));
            logs.add("리포트 엑셀 이동 완료: "
                    + resultDirectory.resolve("excel-change-report.xlsx"));
            logs.add("topics 이동 완료: " + resultDirectory.resolve("topics"));
            logs.add("완료: " + resultDirectory);
            writeBerLog(resultDirectory, batchLogs);

            return new BerApplyResult(
                    inputDirectory.toString(),
                    resultDirectory.resolve("excel-change-report.xlsx")
                            .toString(),
                    resultDirectory.resolve("topics").toString(),
                    List.copyOf(logs));
        }catch(IOException exception){
            logs.add("오류: " + exception.getMessage());
            writeFailureLog(inputDirectory, resultDirectory, batchLogs);
            throw new IllegalArgumentException(
                    "BER 반영 파일 처리 중 오류가 발생했습니다: "
                    + exception.getMessage(),
                    exception);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            logs.add("오류: BER 반영 배치가 중단되었습니다.");
            writeFailureLog(inputDirectory, resultDirectory, batchLogs);
            throw new IllegalArgumentException(
                    "BER 반영 배치가 중단되었습니다.",
                    exception);
        }catch(RuntimeException exception){
            logs.add("오류: "
                    + (exception.getMessage() == null
                    ? exception.getClass().getName()
                    : exception.getMessage()));
            writeFailureLog(inputDirectory, resultDirectory, batchLogs);
            throw exception;
        }finally{
            try{
                deleteDirectory(workDirectory);
            }catch(IOException exception){
                logs.add("작업 폴더 삭제 실패: " + exception.getMessage());
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

    /**
     * 입력 경로가 상위 폴더면 하위 topics를 사용하고, 입력 경로 자체가 topics면 그대로 사용한다.
     * 예: {@code V:\project} 안에 topics가 있으면 {@code V:\project\topics},
     *     {@code V:\project\topics}를 직접 넣으면 그 폴더 자체가 원본이다.
     */
    private Path resolveSourceTopicsDirectory(Path inputDirectory) {
        Path topicsDirectory = inputDirectory.resolve("topics");

        if(Files.isDirectory(topicsDirectory)){
            return topicsDirectory;
        }

        return inputDirectory;
    }



    /**
     * DITA 입력 경로 아래에 Result_Folder를 만든다.
     * 예: 입력이 {@code V:\Tools\test}면 {@code V:\Tools\test\Result_Folder}가 된다.
     */
    private Path resolveResultDirectory(Path inputDirectory) {
        Path resultDirectory = inputDirectory.resolve("Result_Folder");

        if(Files.exists(resultDirectory) && !Files.isDirectory(resultDirectory)){
            throw new IllegalArgumentException(
                    "Result_Folder 경로가 폴더가 아닙니다: " + resultDirectory);
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
                "ber-" + safeName + "-" + RUN_ID_FORMAT.format(
                        LocalDateTime.now()))
                .toAbsolutePath()
                .normalize();
    }

    /**
     * classpath의 revision-tool 리소스를 작업 폴더로 복사한다.
     * JAR 안 리소스는 BAT/Java 프로세스가 일반 파일 경로로 읽을 수 없으므로,
     * 실행 전에 반드시 실제 파일 시스템 위치로 풀어야 한다.
     */
    private void prepareToolDirectory(Path workDirectory) throws IOException {
        Resource[] resources = resourceResolver.getResources(
                "classpath*:" + RESOURCE_ROOT + "/**/*");

        for(Resource resource : resources){
            if(!resource.exists() || !resource.isReadable()){
                continue;
            }

            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.lastIndexOf(RESOURCE_ROOT + "/");

            if(index < 0){
                continue;
            }

            String relativePath = url.substring(
                    index + (RESOURCE_ROOT + "/").length());

            if(relativePath.isBlank() || relativePath.endsWith("/")){
                continue;
            }

            Path target = workDirectory.resolve(relativePath).normalize();

            if(!target.startsWith(workDirectory)){
                throw new IllegalArgumentException(
                        "BER 리소스 경로가 올바르지 않습니다: "
                        + relativePath);
            }

            Files.createDirectories(target.getParent());

            try(var input = resource.getInputStream()){
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        copySharedXslDirectory(workDirectory.resolve("xsl"));
        copySharedResourceDirectory(SHARED_BAT_ROOT, workDirectory, false);
        copySharedResourceDirectory(SHARED_LIB_ROOT, workDirectory.resolve("lib"));

        for(String requiredFile : REQUIRED_TOOL_FILES){
            Path requiredPath = workDirectory.resolve(requiredFile);

            if(!Files.isRegularFile(requiredPath)){
                throw new IllegalArgumentException(
                        "BER 배치 필수 파일을 찾지 못했습니다: "
                        + requiredFile);
            }
        }
    }

    private void copySharedXslDirectory(Path target) throws IOException {
        copySharedResourceDirectory(SHARED_XSL_ROOT, target);
    }

    private void copySharedResourceDirectory(String resourceRoot, Path target)
            throws IOException {
        copySharedResourceDirectory(resourceRoot, target, true);
    }

    private void copySharedResourceDirectory(
            String resourceRoot,
            Path target,
            boolean cleanTarget)
            throws IOException {
        if(cleanTarget && Files.exists(target)){
            deleteDirectory(target);
        }

        ClassPathResource root = new ClassPathResource(resourceRoot);
        if(root.isFile()){
            if(cleanTarget){
                copyDirectory(root.getFile().toPath(), target);
            }else{
                copyDirectoryContents(root.getFile().toPath(), target);
            }
            return;
        }

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
                        "공용 XSL 리소스 경로가 올바르지 않습니다: "
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

    /** source 폴더를 target으로 복사한다. 결과 폴더 제외가 필요 없을 때 쓰는 단순 래퍼다. */
    private void copyDirectory(Path source, Path target) throws IOException {
        copyDirectory(source, target, null);
    }

    private void copyDirectoryContents(Path source, Path target) throws IOException {
        if(!Files.isDirectory(source)){
            throw new IllegalArgumentException(
                    "복사할 리소스 폴더를 찾지 못했습니다: " + source);
        }

        Files.createDirectories(target);

        try(Stream<Path> stream = Files.walk(source)){
            for(Path sourcePath : stream.toList()){
                Path relativePath = source.relativize(sourcePath);
                if(relativePath.toString().isBlank()){
                    continue;
                }

                Path targetPath = target.resolve(relativePath).normalize();
                if(!targetPath.startsWith(target.normalize())){
                    throw new IllegalArgumentException(
                            "리소스 경로가 올바르지 않습니다: " + relativePath);
                }

                if(Files.isDirectory(sourcePath)){
                    Files.createDirectories(targetPath);
                }else if(Files.isRegularFile(sourcePath)){
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(
                            sourcePath,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void copyDirectory(
            Path source,
            Path target,
            Path excludedDirectory)
            throws IOException {
        if(!Files.isDirectory(source)){
            throw new IllegalArgumentException(
                    "topics로 복사할 DITA 폴더를 찾지 못했습니다: "
                    + source);
        }

        deleteDirectory(target);
        Files.createDirectories(target);
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedExcludedDirectory = excludedDirectory == null
                ? null
                : excludedDirectory.toAbsolutePath().normalize();

        try(Stream<Path> stream = Files.walk(source)){
            for(Path sourcePath : stream.toList()){
                Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();

                if(normalizedExcludedDirectory != null
                        && normalizedExcludedDirectory.startsWith(normalizedSource)
                        && normalizedSourcePath.startsWith(normalizedExcludedDirectory)){
                    continue;
                }

                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath).normalize();

                if(!targetPath.startsWith(target)){
                    throw new IllegalArgumentException(
                            "DITA 경로가 올바르지 않습니다: "
                            + sourcePath);
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
     * 작업 폴더에서 BER BAT를 실행한다.
     *
     * <p>BAT 마지막에 pause가 있어 서버 프로세스가 멈출 수 있으므로
     * {@code echo. | call ...bat} 형태로 빈 Enter를 흘려보내 pause를 자동 통과시킨다.
     * BAT 출력은 화면 오류 메시지와 ber.log에 활용할 수 있도록 별도로 모은다.</p>
     */
    private void runBatch(
            Path workDirectory,
            List<String> logs,
            List<String> batchLogs)
            throws IOException, InterruptedException {
        Path batchFile = workDirectory.resolve(BER_BATCH);
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "echo. | call \"" + batchFile.toString() + "\"");
        builder.directory(workDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put(
                "ROOT",
                workDirectory.toString() + "\\");

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
                    "BER 반영 배치 시간이 초과되었습니다.");
        }

        if(process.exitValue() != 0){
            throw new IllegalArgumentException(
                    "BER 반영 배치가 실패했습니다. "
                    + String.join(" / ", tail(logs, 12)));
        }

        if(!Files.isDirectory(workDirectory.resolve("temp"))
                || !Files.isDirectory(workDirectory.resolve("topics"))){
            throw new IllegalArgumentException(
                    "BER 반영 결과 폴더(temp/topics)가 생성되지 않았습니다.");
        }

        if(!Files.isRegularFile(workDirectory.resolve("temp")
                .resolve("excel-change-report.xlsx"))){
            throw new IllegalArgumentException(
                    "BER 변경 리포트 엑셀이 생성되지 않았습니다.");
        }
    }

    /** BAT 실행 후 결과로 옮길 topics 폴더가 비어 있지 않은지 확인한다. */
    private void validateResultTopicsDirectory(Path topicsDirectory)
            throws IOException {

        if(!Files.isDirectory(topicsDirectory)){
            throw new IllegalArgumentException(
                    "BER 반영 결과 topics 폴더가 생성되지 않았습니다.");
        }

        try(Stream<Path> stream = Files.walk(topicsDirectory)){
            boolean hasDita = stream.anyMatch(path ->
                    Files.isRegularFile(path)
                            && path.getFileName()
                            .toString()
                            .toLowerCase()
                            .endsWith(".dita"));

            if(!hasDita){
                throw new IllegalArgumentException(
                        "BER 반영 결과 DITA 파일이 생성되지 않았습니다.");
            }
        }
    }

    /** 실패 메시지가 너무 길어지지 않도록 최근 로그 일부만 잘라낸다. */
    private List<String> tail(List<String> logs, int count) {
        int fromIndex = Math.max(0, logs.size() - count);
        return logs.subList(fromIndex, logs.size());
    }

    /** QSG 로그와 같은 방식으로 BER 배치 출력 로그를 Result_Folder에 저장한다. */
    private void writeBerLog(Path resultDirectory, List<String> logs)
            throws IOException {

        Files.createDirectories(resultDirectory);
        Files.write(
                resultDirectory.resolve("ber.log"),
                logs,
                StandardCharsets.UTF_8);
    }

    /** 실패 시에도 가능한 범위에서 Result_Folder\ber.log를 남긴다. */
    private void writeFailureLog(
            Path inputDirectory,
            Path resultDirectory,
            List<String> logs) {

        Path logDirectory = resultDirectory;

        if(logDirectory == null && inputDirectory != null){
            logDirectory = inputDirectory.resolve("Result_Folder");
        }

        if(logDirectory == null){
            return;
        }

        try{
            writeBerLog(logDirectory, logs);
        }catch(IOException logException){
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
        }
    }

    /** BER 변경 리포트 엑셀을 Result_Folder 바로 아래로 이동한다. */
    private void moveReportFile(Path source, Path target)
            throws IOException {

        if(!Files.isRegularFile(source)){
            throw new IllegalArgumentException(
                    "BER 변경 리포트 엑셀을 찾지 못했습니다: " + source);
        }

        Files.createDirectories(target.getParent());
        Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 결과 폴더를 최종 위치로 이동한다.
     * 같은 드라이브면 move가 빠르고, 드라이브가 다르면 move가 실패할 수 있어 copy 후 삭제로 보완한다.
     */
    private void moveResultDirectory(Path source, Path target)
            throws IOException {
        if(!Files.isDirectory(source)){
            throw new IllegalArgumentException(
                    "결과 폴더를 찾지 못했습니다: " + source);
        }

        Path normalizedTarget = target.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTarget.getParent());
        deleteDirectory(normalizedTarget);

        try{
            Files.move(
                    source,
                    normalizedTarget,
                    StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException moveError){
            copyDirectory(source, normalizedTarget);
            deleteDirectory(source);
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
