package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.MultilingualRunRequest;
import maoomWeb.ire.user.dto.MultilingualRunResult;

/**
 * 다국어 변환 배치를 임시 작업 폴더에서 실행하고 결과만 입력 경로로 복사한다.
 */
@Service
public class MultilingualConversionService {

    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final String BATCH_FILE =
            "00_Split_Extracted_Folders_to_DITA_ver260710.bat";
    private static final String SHARED_XSL_ROOT = "shared-xsl";

    private final Path toolDirectory;

    public MultilingualConversionService() {
        try {
            this.toolDirectory = prepareToolDirectory();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "다국어 변환 도구 리소스를 준비하지 못했습니다.",
                    exception);
        }
    }

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
            copyDirectory(toolDirectory, workspace, Set.of());
            copySharedXslDirectory(workspace.resolve("xsl"));
            Files.createDirectories(workspace.resolve("topics"));

            Path source = resolveTopicsSource(input);
            copyDirectory(
                    source,
                    workspace.resolve("topics"),
                    Set.of("Result_Folder", "temp_out"));

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
            Files.write(
                    runOutput.resolve("multilingual.log"),
                    logs,
                    StandardCharsets.UTF_8);

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
            Files.write(
                    resultFolder.resolve("multilingual.log"),
                    logs,
                    StandardCharsets.UTF_8);
        } catch (IOException logException) {
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
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

    private Path prepareToolDirectory() throws IOException {
        ClassPathResource root =
                new ClassPathResource("multilingual-tool");

        if (root.isFile()) {
            return root.getFile().toPath();
        }

        Path extracted = Files.createTempDirectory(
                "maoom-multilingual-tool-");
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources(
                "classpath*:multilingual-tool/**/*")) {
            if (!resource.isReadable()) {
                continue;
            }

            String url = URLDecoder.decode(
                    resource.getURL().toString(),
                    StandardCharsets.UTF_8);
            int index = url.lastIndexOf("multilingual-tool/");
            if (index < 0) {
                continue;
            }

            String relative = url.substring(
                    index + "multilingual-tool/".length());
            Path destination = extracted.resolve(relative);
            Files.createDirectories(destination.getParent());
            try (var input = resource.getInputStream()) {
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return extracted;
    }

    private void copySharedXslDirectory(Path target) throws IOException {
        deleteDirectoryQuietly(target);

        ClassPathResource root = new ClassPathResource(SHARED_XSL_ROOT);
        if (root.isFile()) {
            copyDirectory(root.getFile().toPath(), target, Set.of());
            return;
        }

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources(
                "classpath*:" + SHARED_XSL_ROOT + "/**/*")) {
            if (!resource.isReadable()) {
                continue;
            }

            String url = URLDecoder.decode(
                    resource.getURL().toString(),
                    StandardCharsets.UTF_8);
            int index = url.lastIndexOf(SHARED_XSL_ROOT + "/");
            if (index < 0) {
                continue;
            }

            String relative = url.substring(
                    index + (SHARED_XSL_ROOT + "/").length());
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
}
