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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.QsgRunRequest;
import maoomWeb.ire.user.dto.QsgRunResult;

/**
 * QSG 화면의 입력 경로와 언어 선택을 실제 QSG 배치 실행으로 연결한다.
 *
 * <p>다른 배치 기능과 동일하게 입력 경로는 원본으로만 사용한다. 실행별
 * {@code .maoomtool\qsg-*} 작업 폴더를 만들고 classpath의 {@code bat},
 * {@code xsl}, {@code lib} 리소스와 입력 topics를 복사한 뒤 언어별 배치를
 * 실행한다. 작업 폴더의 {@code result_Folder}만 입력 경로의
 * {@code Result_Folder}로 옮기고 로그를 저장한 뒤 작업 폴더를 삭제한다.</p>
 *
 * <ol>
 *   <li>입력 경로와 선택 언어 목록을 검증한다.</li>
 *   <li>작업 폴더를 만들고 classpath 도구 리소스를 복사한다.</li>
 *   <li>입력 경로의 {@code topics}가 있으면 topics를, 없으면 입력 폴더 자체를 복사한다.</li>
 *   <li>선택한 언어 코드마다 {@code 02_QSG_apply.bat}를 실행한다.</li>
 *   <li>작업 폴더의 {@code result_Folder} 결과를 검증한다.</li>
 *   <li>결과를 {@code 입력경로\Result_Folder}로 복사하고 {@code qsg.log}를 저장한다.</li>
 *   <li>성공/실패와 관계없이 작업 폴더를 삭제한다.</li>
 * </ol>
 */
@Service
public class QsgApplyService {

    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final String BAT_ROOT = "bat";
    private static final String XSL_ROOT = "xsl";
    private static final String LIB_ROOT = "lib";
    private static final String BATCH_FILE = "02_QSG_apply.bat";

    /** QSG 실행 버튼 한 번에 수행되는 전체 파이프라인. */
    public QsgRunResult run(QsgRunRequest request) {
        List<String> logs = new ArrayList<>();
        Path workspace = null;
        Path input = null;
        List<String> languageCodes = normalizeLanguageCodes(request);

        try {
            if (request == null) {
                throw new IllegalArgumentException("QSG 요청이 비어 있습니다.");
            }
            if (languageCodes.isEmpty()) {
                throw new IllegalArgumentException("QSG 언어를 선택해주세요.");
            }

            input = requireDirectory(request.inputPath(), "Input");
            workspace = createWorkDirectory(input);
            copySharedResourceDirectory(BAT_ROOT, workspace, false);
            copySharedResourceDirectory(LIB_ROOT, workspace.resolve("lib"));
            copySharedXslDirectory(workspace.resolve("xsl"));
            Files.createDirectories(workspace.resolve("topics"));

            Path source = resolveTopicsSource(input);
            copyDirectory(
                    source,
                    workspace.resolve("topics"),
                    Set.of("Result_Folder", "result_Folder", "temp_out"));

            logs.add("작업 폴더: " + workspace);
            logs.add("Input 원본: " + input);
            logs.add("선택 언어: " + String.join(", ", languageCodes));

            for (String languageCode : languageCodes) {
                logs.add("배치 실행: " + BATCH_FILE + " " + languageCode);
                runBatch(workspace, languageCode, logs);
            }

            Path workspaceResult = workspace.resolve("result_Folder");
            validateOutput(workspaceResult);

            Path runOutput = input.resolve("Result_Folder");
            replaceDirectory(workspaceResult, runOutput);
            logs.add("완료: " + runOutput);
            Files.write(
                    runOutput.resolve("qsg.log"),
                    logs,
                    StandardCharsets.UTF_8);

            return new QsgRunResult(
                    true,
                    runOutput.toString(),
                    languageCodes,
                    logs);
        } catch (Exception exception) {
            logs.add("오류: " + formatException(exception));
            writeFailureLog(input, logs);
            return new QsgRunResult(
                    false,
                    null,
                    languageCodes,
                    logs);
        } finally {
            deleteDirectoryQuietly(workspace);
        }
    }

    private List<String> normalizeLanguageCodes(QsgRunRequest request) {
        if (request == null || request.languageCodes() == null) {
            return List.of();
        }

        Set<String> codes = new LinkedHashSet<>();
        for (String code : request.languageCodes()) {
            if (code != null && !code.isBlank()) {
                codes.add(code.trim());
            }
        }
        return List.copyOf(codes);
    }

    private Path resolveTopicsSource(Path input) {
        Path topics = input.resolve("topics");
        if (Files.isDirectory(topics)) {
            return topics;
        }
        return input;
    }

    private void runBatch(Path workspace, String languageCode, List<String> logs)
            throws IOException, InterruptedException {

        Path batchFile = workspace.resolve(BATCH_FILE);
        if (!Files.isRegularFile(batchFile)) {
            throw new IllegalArgumentException(
                    "QSG 배치 파일을 찾지 못했습니다: " + BATCH_FILE);
        }

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "call \"" + batchFile.getFileName() + "\" \""
                        + languageCode + "\" < nul")
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
                    "QSG 배치 실행 실패(" + languageCode + ", " + exitCode + ")");
        }
    }

    private void validateOutput(Path resultFolder) throws IOException {
        if (!hasFileWithExtension(resultFolder, ".dita", ".ditamap")) {
            throw new IllegalStateException(
                    "QSG 결과가 없습니다. result_Folder에 dita/ditamap 파일이 생성되지 않았습니다: "
                    + resultFolder);
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
                    resultFolder.resolve("qsg.log"),
                    logs,
                    StandardCharsets.UTF_8);
        } catch (IOException logException) {
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
        }
    }

    private Path requireDirectory(String pathText, String label) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException(label + " 경로를 입력해주세요.");
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
                ? "qsg"
                : inputDirectory.getFileName().toString();
        String safeName = folderName.replaceAll("[^A-Za-z0-9._-]", "_");

        if (safeName.isBlank()) {
            safeName = "qsg";
        }

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "qsg-" + safeName + "-"
                + LocalDateTime.now().format(RUN_FORMAT))
                .toAbsolutePath()
                .normalize();
    }

    private void replaceDirectory(Path source, Path target)
            throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "복사할 결과 폴더가 없습니다: " + source);
        }

        deleteDirectory(target);
        copyDirectory(source, target, Set.of());
    }

    private void copySharedXslDirectory(Path target) throws IOException {
        copySharedResourceDirectory(XSL_ROOT, target);
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
            deleteDirectory(target);
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

            String relative = url.substring(index + (resourceRoot + "/").length());
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

    private String formatException(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
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

    private void deleteDirectory(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
