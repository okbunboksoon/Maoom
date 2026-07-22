package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import maoomWeb.ire.user.dto.MultilingualRunRequest;
import maoomWeb.ire.user.dto.MultilingualRunResult;

/**
 * 다국어 변환 화면의 입력 경로를 배치 실행으로 연결한다.
 *
 * <p>제품사양서 비교, 정제, BER와 같은 방식으로 원본 폴더를 직접 수정하지 않는다.
 * 입력 경로의 원본 topics 또는 XML 폴더를 실행별 {@code .maoomtool\multilingual-*}
 * 작업 폴더로 복사하고, classpath의 {@code bat}, {@code xsl}, {@code lib}
 * 리소스를 같은 작업 폴더에 풀어 배치를 실행한다. 성공한 결과만 입력 경로의
 * {@code Result_Folder}로 복사하고 작업 폴더는 마지막에 삭제한다.</p>
 *
 * <ol>
 *   <li>입력 경로를 서버 PC 기준으로 검증한다.</li>
 *   <li>실행별 작업 폴더를 만들고 classpath 도구 리소스를 복사한다.</li>
 *   <li>입력 경로의 {@code topics}가 있으면 topics를, 없으면 입력 폴더 자체를 원본으로 복사한다.</li>
 *   <li>XML 입력이면 화면에서 받은 ditamap 이름으로 bookmap.xml을 준비한다.</li>
 *   <li>다국어 변환 배치를 실행하고 작업 폴더의 topics 결과를 검증한다.</li>
 *   <li>결과를 {@code 입력경로\Result_Folder\topics}로 복사하고 {@code multilingual.log}를 저장한다.</li>
 *   <li>성공/실패와 관계없이 작업 폴더를 삭제한다.</li>
 * </ol>
 */
@Service
public class MultilingualConversionService {

    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final Charset LOG_CHARSET = StandardCharsets.UTF_8;
    private static final String BOOKMAP_MAPNAME_REQUIRED =
            "BOOKMAP_MAPNAME_REQUIRED:";
    private static final String BATCH_FILE =
            "00_Split_Extracted_Folders_to_DITA_ver260710.bat";
    private static final String SHARED_BAT_ROOT = "bat";
    private static final String SHARED_XSL_ROOT = "xsl";
    private static final String SHARED_LIB_ROOT = "lib";

    /** 다국어 변환 버튼 한 번에 수행되는 전체 파이프라인. */
    public MultilingualRunResult run(MultilingualRunRequest request) {
        List<String> logs = new ArrayList<>();
        Path workspace = null;
        Path input = null;

        try {
            if (request == null) {
                throw new IllegalArgumentException(
                        "다국어 변환 요청이 비어 있습니다.");
            }

            input = requireDirectory(request.inputPath(), "Input");
            workspace = createWorkDirectory(input);
            copySharedResourceDirectory(SHARED_BAT_ROOT, workspace, false);
            copySharedResourceDirectory(SHARED_LIB_ROOT, workspace.resolve("lib"));
            copySharedXslDirectory(workspace.resolve("xsl"));
            Files.createDirectories(workspace.resolve("topics"));

            Path source = resolveTopicsSource(input);
            copyDirectory(
                    source,
                    workspace.resolve("topics"),
                    Set.of("Result_Folder", "temp_out"));
            prepareBookmapForXmlInput(
                    workspace,
                    logs,
                    request.bookmapMapName());

            logs.add("작업 폴더: " + workspace);
            logs.add("Input 원본: " + input);
            logs.add("배치 실행: " + BATCH_FILE);
            runBatch(workspace, logs);
            validateOutput(workspace.resolve("topics"));

            Path runOutput = input.resolve("Result_Folder");
            Files.createDirectories(runOutput);
            replaceDirectory(
                    workspace.resolve("topics"),
                    runOutput.resolve("topics"));
            logs.add("완료: " + runOutput);
            writeLog(runOutput.resolve("multilingual.log"), logs);

            return new MultilingualRunResult(
                    true,
                    runOutput.toString(),
                    logs);
        } catch (Exception exception) {
            logs.add("오류: " + exception.getMessage());
            writeFailureLog(input, logs);
            return new MultilingualRunResult(
                    false,
                    null,
                    logs);
        } finally {
            deleteDirectoryQuietly(workspace);
        }
    }

    private Path resolveTopicsSource(Path input) {
        Path topics = input.resolve("topics");
        if (Files.isDirectory(topics)) {
            return topics;
        }
        return input;
    }

    private void prepareBookmapForXmlInput(
            Path workspace,
            List<String> logs,
            String bookmapMapName)
            throws IOException {

        // XML 원본은 배치가 참조하는 xsl/bookmap.xml이 필요하므로 화면에서 mapname을 받는다.
        Path xmlDirectory = findRepresentativeXmlDirectory(
                workspace.resolve("topics"));
        if (xmlDirectory == null) {
            return;
        }

        if (bookmapMapName == null || bookmapMapName.isBlank()) {
            throw new IllegalArgumentException(
                    BOOKMAP_MAPNAME_REQUIRED
                    + " XML 입력에는 ditamap 이름을 입력해주세요.");
        }

        List<ChapterFile> chapters = collectChapterFiles(xmlDirectory);
        if (chapters.isEmpty()) {
            return;
        }

        writeGeneratedBookmap(
                workspace,
                bookmapMapName.trim(),
                chapters);
        logs.add("Bookmap 자동 생성: " + chapters.size()
                + "개 XML 파일 기준");
    }

    private Path findRepresentativeXmlDirectory(Path topics)
            throws IOException {

        if (!Files.isDirectory(topics)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(topics)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(directory -> new XmlDirectory(
                            directory,
                            countXmlFiles(directory)))
                    .filter(item -> item.xmlFileCount() > 0)
                    .max(Comparator
                            .comparingInt(XmlDirectory::xmlFileCount)
                            .thenComparing(item -> item.directory().toString()))
                    .map(XmlDirectory::directory)
                    .orElse(null);
        }
    }

    private int countXmlFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .filter(this::isXmlFile)
                    .count();
        } catch (IOException exception) {
            return 0;
        }
    }

    private List<ChapterFile> collectChapterFiles(Path xmlDirectory)
            throws IOException {

        try (Stream<Path> files = Files.list(xmlDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isXmlFile)
                    .map(this::readChapterFile)
                    .sorted()
                    .toList();
        }
    }

    private ChapterFile readChapterFile(Path path) {
        String fileName = path.getFileName().toString();
        return new ChapterFile(fileName, readChapterNumber(path), fileName);
    }

    private int readChapterNumber(Path path) {
        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true);
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    "");
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "");

            Document document =
                    factory.newDocumentBuilder().parse(path.toFile());
            Element root = document.getDocumentElement();
            if (root == null || !root.hasAttribute("chapnum")) {
                return fallbackChapterNumber(
                        path.getFileName().toString());
            }

            return Integer.parseInt(root.getAttribute("chapnum").trim());
        } catch (Exception exception) {
            return fallbackChapterNumber(path.getFileName().toString());
        }
    }

    private int fallbackChapterNumber(String fileName) {
        if ("Foreword.xml".equalsIgnoreCase(fileName)) {
            return 1;
        }

        if (fileName.length() >= 2
                && Character.isDigit(fileName.charAt(0))
                && Character.isDigit(fileName.charAt(1))) {
            return Integer.parseInt(fileName.substring(0, 2));
        }

        return Integer.MAX_VALUE;
    }

    private boolean isXmlFile(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".xml");
    }

    private void writeGeneratedBookmap(
            Path workspace,
            String mapName,
            List<ChapterFile> chapters) throws IOException {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
        xml.append("<bookmap mapname=\"")
                .append(escapeXmlAttribute(mapName))
                .append("\"")
                .append(" xml:lang=\"en-GB\">\r\n");

        for (ChapterFile chapter : chapters) {
            xml.append("\t<chapter filename=\"")
                    .append(escapeXmlAttribute(chapter.fileName()))
                    .append("\"/>\r\n");
        }

        xml.append("</bookmap>\r\n");

        Files.writeString(
                workspace.resolve("bookmap.xml"),
                xml.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                workspace.resolve("xsl/bookmap.xml"),
                xml.toString(),
                StandardCharsets.UTF_8);
    }

    private String escapeXmlAttribute(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void runBatch(Path workspace, List<String> logs)
            throws IOException, InterruptedException {

        Path batchFile = workspace.resolve(BATCH_FILE);
        if (!Files.isRegularFile(batchFile)) {
            throw new IllegalArgumentException(
                    "다국어 변환 배치 파일을 찾지 못했습니다: " + BATCH_FILE);
        }

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "call \"" + batchFile.getFileName() + "\" < nul")
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

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "다국어 변환 배치 실행 실패(" + exitCode + ")");
        }
    }

    private void validateOutput(Path topics) throws IOException {
        if (!hasFileWithExtension(topics, ".dita", ".ditamap")) {
            throw new IllegalStateException(
                    "다국어 변환 결과가 없습니다. topics 폴더에 dita/ditamap 파일이 생성되지 않았습니다: "
                    + topics);
        }
    }

    private boolean hasFileWithExtension(
            Path directory,
            String... extensions) throws IOException {

        if (!Files.isDirectory(directory)) {
            return false;
        }

        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName()
                            .toString()
                            .toLowerCase())
                    .anyMatch(name -> {
                        for (String extension : extensions) {
                            if (name.endsWith(extension)) {
                                return true;
                            }
                        }
                        return false;
                    });
        }
    }

    private void writeFailureLog(Path input, List<String> logs) {
        if (input == null) {
            return;
        }

        try {
            Path resultFolder = input.resolve("Result_Folder");
            Files.createDirectories(resultFolder);
            writeLog(resultFolder.resolve("multilingual.log"), logs);
        } catch (IOException logException) {
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
        }
    }

    private void writeLog(Path logFile, List<String> logs) throws IOException {
        Files.createDirectories(logFile.getParent());
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(logFile),
                LOG_CHARSET)) {
            writer.write('\uFEFF');
            for (String log : logs) {
                writer.write(log);
                writer.write(System.lineSeparator());
            }
        }
    }

    private Path requireDirectory(String pathText, String label) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException(
                    label + " 경로를 입력해주세요.");
        }

        Path path = Path.of(pathText.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    label + " 폴더가 존재하지 않습니다: " + path);
        }
        return path;
    }

    private Path createWorkDirectory(Path inputDirectory) {
        String folderName = inputDirectory.getFileName() == null
                ? "multilingual"
                : inputDirectory.getFileName().toString();
        String safeName = folderName.replaceAll("[^A-Za-z0-9._-]", "_");

        if (safeName.isBlank()) {
            safeName = "multilingual";
        }

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "multilingual-" + safeName + "-"
                + LocalDateTime.now().format(RUN_FORMAT))
                .toAbsolutePath()
                .normalize();
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
        if (cleanTarget) {
            deleteDirectoryQuietly(target);
        }

        ClassPathResource root = new ClassPathResource(resourceRoot);
        if (root.isFile()) {
            copyDirectory(root.getFile().toPath(), target, Set.of());
            return;
        }

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources(
                "classpath*:" + resourceRoot + "/**/*")) {
            if (!resource.isReadable()) {
                continue;
            }

            String url = URLDecoder.decode(
                    resource.getURL().toString(),
                    StandardCharsets.UTF_8);
            int index = url.lastIndexOf(resourceRoot + "/");
            if (index < 0) {
                continue;
            }

            String relative = url.substring(
                    index + (resourceRoot + "/").length());
            if (relative.isBlank() || relative.endsWith("/")) {
                continue;
            }

            Path destination = target.resolve(relative).normalize();
            if (!destination.startsWith(target.normalize())) {
                throw new IllegalArgumentException(
                        "공용 XSL 리소스 경로가 올바르지 않습니다: " + relative);
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

    private void replaceDirectory(Path source, Path target)
            throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "복사할 결과 폴더가 없습니다: " + source);
        }

        deleteDirectoryQuietly(target);
        copyDirectory(source, target, Set.of());
    }

    private void copyDirectory(
            Path source,
            Path target,
            Set<String> excludedRootNames) throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "복사할 원본 폴더가 없습니다: " + source);
        }
        Files.createDirectories(target);

        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0
                        && excludedRootNames.contains(
                                relative.getName(0).toString())) {
                    continue;
                }

                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            path,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private record XmlDirectory(Path directory, int xmlFileCount) {
    }

    private record ChapterFile(
            String fileName,
            int chapterNumber,
            String sortName) implements Comparable<ChapterFile> {

        @Override
        public int compareTo(ChapterFile other) {
            int numberResult = Integer.compare(
                    chapterNumber,
                    other.chapterNumber);
            if (numberResult != 0) {
                return numberResult;
            }
            return sortName.compareToIgnoreCase(other.sortName);
        }
    }
}
