package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
    private static final String SHARED_XSL_ROOT = "shared-xsl";
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
            "xsl/09-doctype-remove.xsl",
            "xsl/10-namespace-remove.xsl",
            "xsl/11-toc-create.xsl",
            "xsl/12-bookmap-create.xsl",
            "xsl/13-topic-merge.xsl",
            "xsl/29-kus-text-normalize.xsl",
            "xsl/kus-normalize-text.xsl",
            "xsl/30-kus-inline-normalize.xsl",
            "xsl/34-kus-db-apply_ber_exclude.xsl",
            "xsl/34-kus-db-apply_ber.xsl",
            "xsl/asis-tobe_eu.xml",
            "xsl/asis-tobe_us.xml",
            "xsl/asis-tobe_exclude.xml",
            "xsl/31-kus-beautify.xsl",
            "xsl/kus-beautify.xsl",
            "xsl/14-namespace-remove.xsl",
            "xsl/15-id-clean_NotFileNameChange.xsl",
            "xsl/16-xref-clean_NotFileNameChange.xsl",
            "xsl/17-related-links_NotFileNameChange.xsl",
            "xsl/05-topicalize_2.xsl",
            "xsl/08-dita-beautify.xsl",
            "xsl/41-make-change-report_ber.xsl",
            "xsl/Convert_Xml_To_Excel.vbs"
    };
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(30);
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
     *   <li>Output 경로가 있으면 {@code Output\결과}, 없으면 {@code DITA\결과}를
     *       최종 결과 위치로 잡는다.</li>
     *   <li>{@code .maoomtool\ber-*} 작업 폴더에 tool 리소스와 topics를 복사한다.</li>
     *   <li>BAT를 실행하고, 실행 이후 새로 생성된 DITA와 bookmap만 결과 topics로 모은다.</li>
     *   <li>결과 temp/topics를 최종 결과 위치로 이동한 뒤 작업 폴더를 삭제한다.</li>
     * </ol>
     */
    public BerApplyResult apply(String ditaPath, String outputPath) {
        // 1. 사용자가 입력한 경로는 브라우저 PC가 아니라 서버 PC 기준으로 해석된다.
        Path inputDirectory = validateInputDirectory(ditaPath);
        // 2. 입력 폴더가 상위 폴더인지 topics 폴더 자체인지 자동 판별한다.
        Path sourceTopicsDirectory = resolveSourceTopicsDirectory(inputDirectory);
        // 3. 최종 산출물은 항상 선택된 기준 경로 아래의 "결과" 폴더에 둔다.
        Path resultDirectory = resolveResultDirectory(inputDirectory, outputPath);
        // 4. BAT 실행 중간 산출물이 원본과 섞이지 않도록 매 실행마다 별도 작업 폴더를 쓴다.
        Path workDirectory = createWorkDirectory(inputDirectory);
        List<String> logs = new ArrayList<>();

        try{
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
            // 배치 실행 전 시간을 기록해, 원본에서 복사된 기존 DITA와 배치가 새로 만든 DITA를 구분한다.
            Instant batchStartedAt = Instant.now();
            // 실제 04_KUS_asis-tobe-apply_NotFileNameChange.bat 실행 지점.
            runBatch(workDirectory, logs);
            // BAT 실행 후 새로 생성/수정된 DITA와 xsl/bookmap.xml만 결과 topics로 모은다.
            Path generatedTopicsDirectory =
                    createGeneratedTopicsDirectory(workDirectory, batchStartedAt);
            moveResultDirectory(
                    workDirectory.resolve("temp"),
                    resultDirectory.resolve("temp"));
            moveResultDirectory(
                    generatedTopicsDirectory,
                    resultDirectory.resolve("topics"));
            logs.add("temp 이동 완료: " + resultDirectory.resolve("temp"));
            logs.add("topics 이동 완료: " + resultDirectory.resolve("topics"));

            return new BerApplyResult(
                    inputDirectory.toString(),
                    resultDirectory.resolve("temp").toString(),
                    resultDirectory.resolve("topics").toString(),
                    List.copyOf(logs));
        }catch(IOException exception){
            throw new IllegalArgumentException(
                    "BER 반영 파일 처리 중 오류가 발생했습니다: "
                    + exception.getMessage(),
                    exception);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(
                    "BER 반영 배치가 중단되었습니다.",
                    exception);
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
     * Output 경로가 있으면 그 경로 아래에, 없으면 DITA 입력 경로 아래에 "결과" 폴더를 만든다.
     * 예: Output이 {@code V:\Tools\test}면 {@code V:\Tools\test\결과\temp/topics}가 된다.
     */
    private Path resolveResultDirectory(
            Path inputDirectory,
            String outputPath) {
        Path resultBaseDirectory = outputPath == null || outputPath.isBlank()
                ? inputDirectory
                : Path.of(outputPath.trim()).toAbsolutePath().normalize();
        Path resultDirectory = resultBaseDirectory.resolve("결과");

        if(Files.exists(resultDirectory) && !Files.isDirectory(resultDirectory)){
            throw new IllegalArgumentException(
                    "Output 경로가 폴더가 아닙니다: " + resultDirectory);
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
        if(Files.exists(target)){
            deleteDirectory(target);
        }

        ClassPathResource root = new ClassPathResource(SHARED_XSL_ROOT);
        if(root.isFile()){
            copyDirectory(root.getFile().toPath(), target);
            return;
        }

        Resource[] resources = resourceResolver.getResources(
                "classpath*:" + SHARED_XSL_ROOT + "/**/*");

        for(Resource resource : resources){
            if(!resource.exists() || !resource.isReadable()){
                continue;
            }

            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.lastIndexOf(SHARED_XSL_ROOT + "/");
            if(index < 0){
                continue;
            }

            String relativePath = url.substring(
                    index + (SHARED_XSL_ROOT + "/").length());
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
     * BAT 로그는 화면 오류 메시지에 활용할 수 있도록 logs에 모은다.</p>
     */
    private void runBatch(Path workDirectory, List<String> logs)
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
                        Charset.defaultCharset()))){
            String line;

            while((line = reader.readLine()) != null){
                if(!line.isBlank()){
                    logs.add(line);
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
    }

    /**
     * 배치가 만든 최종 topics 결과만 별도 폴더로 모은다.
     *
     * <p>작업 폴더의 topics에는 원본에서 복사한 파일과 배치가 새로 쓴 파일이 같이 있다.
     * 결과로 원본 전체를 내보내면 불필요하게 크고 혼란스러우므로, 배치 시작 이후 수정된
     * .dita 파일과 xsl/bookmap.xml에서 만들어진 ditamap만 generated-topics에 모은다.</p>
     */
    private Path createGeneratedTopicsDirectory(
            Path workDirectory,
            Instant batchStartedAt)
            throws IOException {
        Path generatedTopicsDirectory = workDirectory.resolve("generated-topics");
        Path sourceTopicsDirectory = workDirectory.resolve("topics");

        deleteDirectory(generatedTopicsDirectory);
        Files.createDirectories(generatedTopicsDirectory);
        copyGeneratedDitamap(workDirectory, generatedTopicsDirectory);

        try(Stream<Path> stream = Files.walk(sourceTopicsDirectory)){
            for(Path sourcePath : stream.toList()){
                if(!Files.isRegularFile(sourcePath)
                        || !sourcePath.getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".dita")){
                    continue;
                }

                if(Files.getLastModifiedTime(sourcePath)
                        .toInstant()
                        .isBefore(batchStartedAt.minusSeconds(1))){
                    continue;
                }

                Path targetPath = generatedTopicsDirectory.resolve(
                        sourceTopicsDirectory.relativize(sourcePath))
                        .normalize();

                if(!targetPath.startsWith(generatedTopicsDirectory)){
                    throw new IllegalArgumentException(
                            "BER 생성 topics 경로가 올바르지 않습니다: "
                            + sourcePath);
                }

                Files.createDirectories(targetPath.getParent());
                Files.copy(
                        sourcePath,
                        targetPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
        }

        return generatedTopicsDirectory;
    }

    /** xsl/bookmap.xml을 읽어 실제 mapname 속성 이름으로 결과 ditamap 파일을 복사한다. */
    private void copyGeneratedDitamap(
            Path workDirectory,
            Path generatedTopicsDirectory)
            throws IOException {
        Path generatedBookmap = workDirectory.resolve("xsl")
                .resolve("bookmap.xml");

        if(!Files.isRegularFile(generatedBookmap)){
            throw new IllegalArgumentException(
                    "BER 생성 ditamap을 찾지 못했습니다: " + generatedBookmap);
        }

        String mapName = readGeneratedMapName(generatedBookmap);
        Path targetMap = generatedTopicsDirectory.resolve(mapName)
                .normalize();

        if(!targetMap.startsWith(generatedTopicsDirectory)){
            throw new IllegalArgumentException(
                    "BER 생성 ditamap 이름이 올바르지 않습니다: " + mapName);
        }

        Files.copy(
                generatedBookmap,
                targetMap,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** 생성된 bookmap.xml의 루트 mapname 속성을 읽어 결과 ditamap 파일명을 결정한다. */
    private String readGeneratedMapName(Path generatedBookmap) {
        try(var input = Files.newInputStream(generatedBookmap)){
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(input);
            Element root = document.getDocumentElement();
            String mapName = root == null
                    ? ""
                    : root.getAttribute("mapname");

            if(mapName == null || mapName.isBlank()){
                throw new IllegalArgumentException(
                        "BER 생성 ditamap 이름을 찾지 못했습니다: "
                        + generatedBookmap);
            }

            return mapName.trim();
        }catch(IllegalArgumentException exception){
            throw exception;
        }catch(Exception exception){
            throw new IllegalArgumentException(
                    "BER 생성 ditamap을 읽지 못했습니다: " + generatedBookmap,
                    exception);
        }
    }

    /** 실패 메시지가 너무 길어지지 않도록 최근 로그 일부만 잘라낸다. */
    private List<String> tail(List<String> logs, int count) {
        int fromIndex = Math.max(0, logs.size() - count);
        return logs.subList(fromIndex, logs.size());
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
