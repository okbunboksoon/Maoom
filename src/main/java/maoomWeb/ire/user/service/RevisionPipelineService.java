package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.RevisionOptionDto;
import maoomWeb.ire.user.dto.RevisionRunRequest;
import maoomWeb.ire.user.dto.RevisionRunResult;

@Service
public class RevisionPipelineService {

    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final List<RevisionStep> STEPS = List.of(
        doctypeStep("DOCTYPE_REMOVE", "DOCTYPE 제거 및 Map 병합",
                "ditamap의 DOCTYPE을 제거하고 시작 Map을 생성합니다.",
                "09-doctype-remove.xsl", "09-doctype-removed.xml", true),
        step("NAMESPACE_REMOVE_1", "1차 Namespace 제거",
                "Map과 Topic의 Namespace를 정리합니다.",
                "10-namespace-remove.xsl", "10-namespace-removed.xml", false),
        step("METADATA_INSERT", "메타데이터 추가",
                "Map에 필요한 topicmeta 정보를 추가합니다.",
                "11_metadata_Insert.xsl", "11-metadata-inserted.xml", false),
        step("SVG_UPDATE", "SVG 정보 추가",
                "navtitle 기준 SVG 관련 메타데이터를 갱신합니다.",
                "12_svg_update.xsl", "12-svg-updated.xml", false),
        step("TOC_CREATE", "TOC 생성",
                "Topicref를 목차 구조로 정리합니다.",
                "11-toc-create.xsl", "11-toc-created.xml", false),
        auxiliary("BOOKMAP_CREATE", "Bookmap 정보 생성",
                "후속 DITA 재정렬에서 사용하는 bookmap.xml을 생성합니다.",
                "12-bookmap-create.xsl", "bookmap.xml"),
        step("TOPIC_MERGE", "Topic 병합",
                "topics 폴더의 DITA 파일을 Map 구조에 병합합니다.",
                "13-topic-merge.xsl", "13-topic-merged.xml", true),
        step("TGROUP_MERGE", "복수 Tgroup 병합",
                "하나의 table 안에 있는 여러 tgroup을 하나로 합칩니다.",
                "13-1_merge_tgroup.xsl", "14-topic-merged.xml", false),
        step("IMAGE_ATTRIBUTE", "이미지 속성 정리",
                "이미지 href와 placement, outputclass 속성을 정리합니다.",
                "01c_image_attr.xsl", "image-attribute.xml", false),
        step("REFINEMENT_TAG", "정제 태그 적용",
                "용어와 인라인 요소의 정제 태그를 적용합니다.",
                "00_refinement_tag.xsl", "refinement-tag.xml", false),
        step("TRANSLATE_NO_TAGGING", "번역 제외 태그 적용",
                "번역 제외 대상에 필요한 태그를 적용합니다.",
                "00_translate_no_tagging.xsl", "translate-no-tagging.xml", false),
        step("REMOVE_REVIEW", "Review 속성 제거",
                "검토용 outputclass와 modified 속성을 제거합니다.",
                "14_1-remove_review.xsl", "remove-review.xml", true),
        step("NAMESPACE_REMOVE_2", "2차 Namespace 제거",
                "병합 이후 생성된 Namespace를 다시 정리합니다.",
                "14-namespace-remove.xsl", "14-namespace-removed.xml", false),
        step("ID_CLEAN", "ID 정리",
                "파일명은 유지하면서 요소 ID를 정리합니다.",
                "15-id-clean_NotFileNameChange.xsl", "15-id-cleaned.xml", false),
        step("XREF_CLEAN", "Xref 정리",
                "파일명은 유지하면서 xref 경로와 속성을 정리합니다.",
                "16-xref-clean_NotFileNameChange.xsl", "16-xref-cleaned.xml", false),
        step("RELATED_LINKS", "Related-links 생성",
                "부모·자식 관계를 related-links로 생성합니다.",
                "17-related-links_NotFileNameChange.xsl", "17-related-links.xml", false),
        step("DITA_REBEAUTIFY", "DITA 구조 정렬",
                "bookmap 정보를 이용해 chapter 파일 정보를 정렬합니다.",
                "18-dita-rebeautify.xsl", "18-dita-rebeautified.xml", false),
        chapter("RECHAPTERIZE", "Chapter 파일 분리",
                "최종 Map을 chapter XML 파일들로 분리합니다.",
                "19-rechapterize.xsl")
    );

    private final Path toolDirectory;

    public RevisionPipelineService() {
        try {
            this.toolDirectory = prepareToolDirectory();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "정제 도구 리소스를 준비하지 못했습니다.", exception);
        }
    }

    public List<RevisionOptionDto> getOptions() {
        return STEPS.stream()
            .map(step -> new RevisionOptionDto(
                    step.id(), step.label(), step.description()))
            .toList();
    }

    public RevisionRunResult run(RevisionRunRequest request) {
        List<String> logs = new ArrayList<>();
        Path workspace = null;

        try {
            Path input = requireDirectory(request.inputPath(), "Input");
            Path output = requireOutputDirectory(request.outputPath());
            validateSeparatePaths(input, output);

            Set<String> selected = validateOptions(request.optionIds());
            validateDependencies(selected);

            Path topicsSource = Files.isDirectory(input.resolve("topics"))
                    ? input.resolve("topics")
                    : input;
            validateFileNames(topicsSource);

            workspace = Files.createTempDirectory("maoom-revision-");
            Path topics = workspace.resolve("topics");
            Path temp = workspace.resolve("temp");
            Path chapter = workspace.resolve("chapter");
            Path xsl = workspace.resolve("xsl");
            Files.createDirectories(temp);
            Files.createDirectories(chapter);
            copyDirectory(topicsSource, topics);
            copyDirectory(toolDirectory.resolve("xsl"), xsl);

            Path current = findStartingMap(topics);
            List<String> completed = new ArrayList<>();

            for (RevisionStep step : STEPS) {
                if (!selected.contains(step.id())) {
                    continue;
                }

                logs.add("실행: " + step.label());

                if (step.mode() == StepMode.DOCTYPE) {
                    runSaxon(
                            workspace,
                            xsl.resolve("dummy.xml"),
                            xsl.resolve("dummy.xml"),
                            xsl.resolve(step.stylesheet()),
                            true,
                            logs);
                    current = temp.resolve(step.outputName());
                } else if (step.mode() == StepMode.AUXILIARY) {
                    runSaxon(
                            workspace,
                            current,
                            xsl.resolve(step.outputName()),
                            xsl.resolve(step.stylesheet()),
                            step.catalog(),
                            logs);
                } else if (step.mode() == StepMode.CHAPTER) {
                    runSaxon(
                            workspace,
                            current,
                            xsl.resolve("dummy.xml"),
                            xsl.resolve(step.stylesheet()),
                            step.catalog(),
                            logs);
                } else {
                    Path next = temp.resolve(step.outputName());
                    runSaxon(
                            workspace,
                            current,
                            next,
                            xsl.resolve(step.stylesheet()),
                            step.catalog(),
                            logs);
                    current = next;
                }

                completed.add(step.id());
            }

            Path runOutput = output.resolve(
                    "revision-" + LocalDateTime.now().format(RUN_FORMAT));
            Files.createDirectories(runOutput);

            if (selected.contains("RECHAPTERIZE")) {
                Path chapterOutput = runOutput.resolve("chapter");
                copyDirectory(chapter, chapterOutput);
            } else {
                Files.copy(
                        current,
                        runOutput.resolve("revision-result.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            copyIfExists(
                    xsl.resolve("bookmap.xml"),
                    runOutput.resolve("bookmap.xml"));
            copyIfExists(
                    temp.resolve("table_report.xml"),
                    runOutput.resolve("table_report.xml"));
            Files.write(
                    runOutput.resolve("revision.log"),
                    logs,
                    StandardCharsets.UTF_8);

            logs.add("완료: " + runOutput);
            return new RevisionRunResult(
                    true,
                    runOutput.toString(),
                    completed,
                    logs);
        } catch (Exception exception) {
            logs.add("오류: " + exception.getMessage());
            return new RevisionRunResult(
                    false,
                    null,
                    List.of(),
                    logs);
        } finally {
            deleteDirectoryQuietly(workspace);
        }
    }

    private void runSaxon(
            Path workspace,
            Path source,
            Path result,
            Path stylesheet,
            boolean useCatalog,
            List<String> logs) throws IOException, InterruptedException {

        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException(
                    "이전 단계 결과가 없습니다: " + source.getFileName());
        }

        Files.createDirectories(result.getParent());

        Path javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java");
        String classPath = String.join(
                System.getProperty("path.separator"),
                toolDirectory.resolve("lib/saxon-he-12.4.jar").toString(),
                toolDirectory.resolve("lib/xmlresolver-5.2.2.jar").toString());

        List<String> command = new ArrayList<>(List.of(
                javaExecutable.toString(),
                "-cp",
                classPath,
                "net.sf.saxon.Transform"));

        if (useCatalog) {
            command.add("-catalog:" + workspace.resolve("xsl/catalog.xml"));
        }

        command.add("-s:" + source);
        command.add("-o:" + result);
        command.add("-xsl:" + stylesheet);

        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        StandardCharsets.UTF_8))) {
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
                    "Saxon 실행 실패(" + exitCode + "): "
                    + stylesheet.getFileName());
        }
    }

    private Set<String> validateOptions(List<String> requestedOptions) {
        if (requestedOptions == null || requestedOptions.isEmpty()) {
            throw new IllegalArgumentException(
                    "정제 항목을 하나 이상 선택해주세요.");
        }

        Map<String, RevisionStep> available = new LinkedHashMap<>();
        STEPS.forEach(step -> available.put(step.id(), step));

        for (String option : requestedOptions) {
            if (!available.containsKey(option)) {
                throw new IllegalArgumentException(
                        "알 수 없는 정제 항목입니다: " + option);
            }
        }

        return Set.copyOf(requestedOptions);
    }

    private void validateDependencies(Set<String> selected) {
        if (selected.contains("DITA_REBEAUTIFY")
                && !selected.contains("BOOKMAP_CREATE")) {
            throw new IllegalArgumentException(
                    "DITA 구조 정렬을 선택하려면 Bookmap 정보 생성도 선택해야 합니다.");
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

    private Path requireOutputDirectory(String pathText) throws IOException {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException(
                    "Output 경로를 입력해주세요.");
        }

        Path output = Path.of(pathText.trim())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(output);
        return output;
    }

    private void validateSeparatePaths(Path input, Path output) {
        if (input.equals(output)
                || input.startsWith(output)
                || output.startsWith(input)) {
            throw new IllegalArgumentException(
                    "Input과 Output은 서로 포함되지 않는 별도 폴더로 지정해주세요.");
        }
    }

    private void validateFileNames(Path topics) throws IOException {
        List<String> invalid = new ArrayList<>();

        try (Stream<Path> files = Files.list(topics)) {
            files.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".dita") || name.endsWith(".ditamap");
                })
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    if (name.contains(" ")
                            || !name.matches("[A-Za-z0-9._-]+")) {
                        invalid.add(name);
                    }
                });
        }

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "파일명에 공백 또는 비 ASCII 문자가 있습니다: "
                    + String.join(", ", invalid));
        }
    }

    private Path findStartingMap(Path topics) throws IOException {
        try (Stream<Path> files = Files.list(topics)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName()
                        .toString()
                        .toLowerCase()
                        .endsWith(".ditamap"))
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Input 폴더에서 ditamap 파일을 찾을 수 없습니다."));
        }
    }

    private Path prepareToolDirectory() throws IOException {
        ClassPathResource root =
                new ClassPathResource("revision-tool");

        if (root.isFile()) {
            return root.getFile().toPath();
        }

        Path extracted = Files.createTempDirectory(
                "maoom-revision-tool-");
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources(
                "classpath*:revision-tool/**/*")) {
            if (!resource.isReadable()) {
                continue;
            }

            String url = URLDecoder.decode(
                    resource.getURL().toString(),
                    StandardCharsets.UTF_8);
            int index = url.lastIndexOf("revision-tool/");
            if (index < 0) {
                continue;
            }

            String relative = url.substring(
                    index + "revision-tool/".length());
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

    private void copyDirectory(Path source, Path target)
            throws IOException {
        Files.createDirectories(target);

        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination =
                        target.resolve(source.relativize(path));
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

    private void copyIfExists(Path source, Path target)
            throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
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

    private boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win");
    }

    private static RevisionStep step(
            String id,
            String label,
            String description,
            String stylesheet,
            String outputName,
            boolean catalog) {
        return new RevisionStep(
                id,
                label,
                description,
                stylesheet,
                outputName,
                catalog,
                StepMode.TRANSFORM);
    }

    private static RevisionStep doctypeStep(
            String id,
            String label,
            String description,
            String stylesheet,
            String outputName,
            boolean catalog) {
        return new RevisionStep(
                id,
                label,
                description,
                stylesheet,
                outputName,
                catalog,
                StepMode.DOCTYPE);
    }

    private static RevisionStep auxiliary(
            String id,
            String label,
            String description,
            String stylesheet,
            String outputName) {
        return new RevisionStep(
                id,
                label,
                description,
                stylesheet,
                outputName,
                false,
                StepMode.AUXILIARY);
    }

    private static RevisionStep chapter(
            String id,
            String label,
            String description,
            String stylesheet) {
        return new RevisionStep(
                id,
                label,
                description,
                stylesheet,
                null,
                false,
                StepMode.CHAPTER);
    }

    private enum StepMode {
        TRANSFORM,
        DOCTYPE,
        AUXILIARY,
        CHAPTER
    }

    private record RevisionStep(
            String id,
            String label,
            String description,
            String stylesheet,
            String outputName,
            boolean catalog,
            StepMode mode) {
    }
}
