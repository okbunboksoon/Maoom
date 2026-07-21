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
import maoomWeb.ire.user.service.RevisionPipelineCatalog.BatchPlan;

/**
 * DITA/DITAMAP 원본에 선택된 XSL 변환을 순서대로 적용하는 정제 파이프라인이다.
 *
 * <p>RevisionController에서 요청을 받아 원본과 revision-tool 리소스를
 * {@code .maoomtool} 작업 폴더로 복사한 뒤, {@code xsl}의 스타일시트를
 * Saxon으로 실행한다. 각 단계의 결과가
 * 다음 단계 입력이 되며, 최종 결과와 로그만 사용자가 지정한 Output 폴더로 복사한다.</p>
 *
 * <p>원본 보호를 위해 Input과 Output이 서로 포함된 경로는 허용하지 않으며,
 * 작업 완료 여부와 관계없이 작업 폴더는 마지막에 정리한다.</p>
 */
@Service
public class RevisionPipelineService {

    private static final DateTimeFormatter RUN_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String BOOKMAP_MAPNAME_REQUIRED =
            "BOOKMAP_MAPNAME_REQUIRED:";
    private static final Charset BATCH_FILE_CHARSET = StandardCharsets.UTF_8;
    private static final Charset BATCH_OUTPUT_CHARSET = Charset.forName("MS949");
    private static final String SHARED_BAT_ROOT = "bat";
    private static final String SHARED_XSL_ROOT = "xsl";
    private static final String SHARED_LIB_ROOT = "lib";

    // 이 목록의 순서가 화면 표시 순서이자 실제 XSL 실행 순서다.
    private static final List<RevisionStep> STEPS = List.of(
        doctypeStep("DOCTYPE_REMOVE", "DOCTYPE 제거 및 Map 병합",
                "ditamap의 DOCTYPE을 제거하고 시작 Map을 생성합니다.",
                "09-doctype-remove.xsl", "09-doctype-removed.xml", true),
        step("NAMESPACE_REMOVE_1", "1차 Namespace 제거",
                "Map과 Topic의 Namespace를 정리합니다.",
                "10-namespace-remove.xsl", "10-namespace-removed.xml", false),
        step("METADATA_INSERT", "메타데이터 추가",
                "Map에 필요한 topicmeta 정보를 추가합니다.",
                "0100-metadata_Insert.xsl", "11-metadata-inserted.xml", false),
        step("SVG_UPDATE", "SVG 정보 추가",
                "navtitle 기준 SVG 관련 메타데이터를 갱신합니다.",
                "0110-svg_update.xsl", "12-svg-updated.xml", false),
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
                "0130-merge_tgroup.xsl", "14-topic-merged.xml", false),
        step("IMAGE_ATTRIBUTE", "이미지 속성 정리",
                "이미지 href와 placement, outputclass 속성을 정리합니다.",
                "01c_image_attr.xsl", "image-attribute.xml", false),
        step("REFINEMENT_TAG", "정제 태그 적용",
                "용어와 인라인 요소의 정제 태그를 적용합니다.",
                "0170-refinement_tag.xsl", "refinement-tag.xml", false),
        step("TRANSLATE_NO_TAGGING", "번역 제외 태그 적용",
                "번역 제외 대상에 필요한 태그를 적용합니다.",
                "0180-translate_no_tagging.xsl", "translate-no-tagging.xml", false),
        step("REMOVE_REVIEW", "Review 속성 제거",
                "검토용 outputclass와 modified 속성을 제거합니다.",
                "0400-remove_review.xsl", "remove-review.xml", true),
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
                "0009-dita-rebeautify.xsl", "18-dita-rebeautified.xml", false),
        chapter("RECHAPTERIZE", "Chapter 파일 분리",
                "최종 Map을 chapter XML 파일들로 분리합니다.",
                "0010-rechapterize.xsl")
    );

    private final Path toolDirectory;

    /**
     * 클래스패스의 revision-tool을 실행 가능한 로컬 경로로 준비한다.
     * IDE 실행은 원본 폴더를 쓰고, JAR 실행은 임시 폴더에 리소스를 풀어 사용한다.
     */
    public RevisionPipelineService() {
        try {
            this.toolDirectory = prepareToolDirectory();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "정제 도구 리소스를 준비하지 못했습니다.", exception);
        }
    }

    /** 내부 파이프라인 정의에서 화면에 필요한 ID, 이름과 설명만 반환한다. */
    public List<RevisionOptionDto> getOptions() {
        return RevisionPipelineCatalog.options();
    }

    /**
     * 경로와 단계 의존성을 검증한 뒤 선택된 XSL을 순서대로 실행한다.
     * 예외는 화면에서 바로 표시할 수 있도록 실패 결과와 로그로 변환한다.
     */
    public RevisionRunResult run(RevisionRunRequest request) {
        List<String> logs = new ArrayList<>();
        Path workspace = null;
        Path input = null;

        try {
            input = requireDirectory(request.inputPath(), "Input");
            RevisionFormat inputType = detectInputFormat(input);
            RevisionFormat outputType = requireRevisionFormat(
                    request.outputType(),
                    "출력");
            Set<String> selectedOptions = RevisionPipelineCatalog.validateOptions(
                    request.optionIds());
            BatchPlan batchPlan = RevisionPipelineCatalog.createBatchPlan(
                    inputType,
                    outputType,
                    selectedOptions);

            // 원본을 직접 수정하지 않도록 실행 PC의 .maoomtool 아래 작업 폴더에서 처리한다.
            workspace = createWorkDirectory(input);
            copyDirectory(toolDirectory, workspace);
            copySharedResourceDirectory(SHARED_BAT_ROOT, workspace, false);
            copySharedResourceDirectory(SHARED_LIB_ROOT, workspace.resolve("lib"));
            copySharedXslDirectory(workspace.resolve("xsl"));
            Files.createDirectories(workspace.resolve("temp"));
            Files.createDirectories(workspace.resolve("topics"));
            Files.createDirectories(workspace.resolve("chapter"));
            prepareBatchInput(input, inputType, workspace);
            prepareBookmap(input, inputType, workspace, request.bookmapMapName());

            logs.add("작업 폴더: " + workspace);
            logs.add("Input 원본: " + input);
            logs.add("입력 형태: " + inputType.value());
            logs.add("출력 형태: " + outputType.value());

            List<String> completed = new ArrayList<>();
            for (String batchFile : batchPlan.batchFiles()) {
                prepareBatchOptions(
                        workspace,
                        batchFile,
                        selectedOptions,
                        logs);
                logs.add("배치 실행: " + batchFile);
                runBatch(workspace, batchFile, logs);
                completed.add(batchFile);
            }
            validateBatchOutput(workspace, outputType, batchPlan);

            Path runOutput = input.resolve("Result_Folder");
            Files.createDirectories(runOutput);

            if (outputType == RevisionFormat.XML) {
                Path chapterOutput = runOutput.resolve("chapter");
                replaceDirectory(workspace.resolve("chapter"), chapterOutput);
            } else {
                Path topicsOutput = runOutput.resolve("topics");
                replaceDirectory(workspace.resolve("topics"), topicsOutput);
            }

            copyBookmap(workspace, runOutput, input);
            copyIfExists(
                    workspace.resolve("temp/table_report.xml"),
                    runOutput.resolve("table_report.xml"));

            logs.add("완료: " + runOutput);
            return new RevisionRunResult(
                    true,
                    runOutput.toString(),
                    completed,
                    logs);
        } catch (Exception exception) {
            logs.add("오류: " + exception.getMessage());
            writeFailureLog(input, logs);
            return new RevisionRunResult(
                    false,
                    null,
                    List.of(),
                    logs);
        } finally {
            deleteDirectoryQuietly(workspace);
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
                    resultFolder.resolve("revision.log"),
                    logs,
                    StandardCharsets.UTF_8);
        } catch (IOException logException) {
            logs.add("오류 로그 저장 실패: " + logException.getMessage());
        }
    }

    private RevisionFormat requireRevisionFormat(
            String value,
            String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    label + " 형태를 선택해주세요.");
        }

        String normalized = value.trim().toLowerCase();
        if ("xml".equals(normalized)) {
            return RevisionFormat.XML;
        }
        if ("dita".equals(normalized)) {
            return RevisionFormat.DITA;
        }

        throw new IllegalArgumentException(
                label + " 형태는 xml 또는 dita만 선택할 수 있습니다.");
    }

    private RevisionFormat detectInputFormat(Path input)
            throws IOException {
        boolean hasXml = hasFileWithExtension(
                resolveSourceDirectory(input, "chapter"),
                ".xml");
        boolean hasDita = hasFileWithExtension(
                resolveSourceDirectory(input, "topics"),
                ".dita",
                ".ditamap");

        if (hasXml && hasDita) {
            throw new IllegalArgumentException(
                    "입력 경로에 xml과 dita가 함께 있습니다. 한 가지 형태만 넣어주세요.");
        }

        if (hasXml) {
            return RevisionFormat.XML;
        }

        if (hasDita) {
            return RevisionFormat.DITA;
        }

        throw new IllegalArgumentException(
                "입력 경로에서 xml 또는 dita 파일을 찾지 못했습니다.");
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

    private void prepareBatchInput(
            Path input,
            RevisionFormat inputType,
            Path workspace) throws IOException {

        if (inputType == RevisionFormat.XML) {
            Path chapterSource = resolveSourceDirectory(input, "chapter");
            copyDirectory(chapterSource, workspace.resolve("chapter"));
            return;
        }

        Path topicsSource = resolveSourceDirectory(input, "topics");
        validateFileNames(topicsSource);
        copyDirectory(topicsSource, workspace.resolve("topics"));
    }

    private Path resolveSourceDirectory(Path input, String childName) {
        Path child = input.resolve(childName);
        if (Files.isDirectory(child)) {
            return child;
        }
        return input;
    }

    private void prepareBookmap(
            Path input,
            RevisionFormat inputType,
            Path workspace,
            String bookmapMapName) throws IOException {

        Path source = input.resolve("bookmap.xml");
        if (Files.isRegularFile(source)) {
            copyInitialBookmap(source, workspace);
            return;
        }

        if (inputType != RevisionFormat.XML) {
            return;
        }

        if (bookmapMapName == null || bookmapMapName.isBlank()) {
            throw new IllegalArgumentException(
                    BOOKMAP_MAPNAME_REQUIRED
                    + " XML 입력에는 bookmap.xml이 필요합니다. mapname 파일명을 입력해주세요.");
        }

        writeGeneratedBookmap(
                workspace,
                bookmapMapName.trim(),
                collectChapterFileNames(workspace.resolve("chapter")));
    }

    private void copyInitialBookmap(Path source, Path workspace)
            throws IOException {
        Files.copy(
                source,
                workspace.resolve("bookmap.xml"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
                source,
                workspace.resolve("xsl/bookmap.xml"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private List<String> collectChapterFileNames(Path chapterDirectory)
            throws IOException {
        try (Stream<Path> files = Files.list(chapterDirectory)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(this::isBookmapChapterFile)
                .sorted(this::compareChapterFileName)
                .toList();
        }
    }

    private boolean isBookmapChapterFile(String fileName) {
        return fileName.toLowerCase().endsWith(".xml")
                || leadingChapterNumber(fileName) >= 0;
    }

    private int compareChapterFileName(String left, String right) {
        int leftGroup = chapterSortGroup(left);
        int rightGroup = chapterSortGroup(right);
        if (leftGroup != rightGroup) {
            return Integer.compare(leftGroup, rightGroup);
        }

        if (leftGroup == 1) {
            int leftNumber = leadingChapterNumber(left);
            int rightNumber = leadingChapterNumber(right);
            if (leftNumber != rightNumber) {
                return Integer.compare(leftNumber, rightNumber);
            }
        }

        return left.compareToIgnoreCase(right);
    }

    private int chapterSortGroup(String fileName) {
        if ("Foreword.xml".equalsIgnoreCase(fileName)) {
            return 0;
        }
        if (leadingChapterNumber(fileName) >= 0) {
            return 1;
        }
        return 2;
    }

    private int leadingChapterNumber(String fileName) {
        if (fileName.length() < 2) {
            return -1;
        }

        char first = fileName.charAt(0);
        char second = fileName.charAt(1);
        if (!Character.isDigit(first) || !Character.isDigit(second)) {
            return -1;
        }

        return Integer.parseInt(fileName.substring(0, 2));
    }

    private void writeGeneratedBookmap(
            Path workspace,
            String mapName,
            List<String> chapterFileNames) throws IOException {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
        xml.append("<bookmap mapname=\"")
                .append(escapeXmlAttribute(mapName))
                .append("\" xml:lang=\"en-GB\">\r\n");

        for (String fileName : chapterFileNames) {
            xml.append("\t<chapter filename=\"")
                    .append(escapeXmlAttribute(fileName))
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

    private void prepareBatchOptions(
            Path workspace,
            String batchFileName,
            Set<String> selectedOptions,
            List<String> logs) throws IOException {

        if (!batchFileName.startsWith("02_topics_Chapterize")) {
            return;
        }

        boolean removeSimpleOperation = selectedOptions.contains(
                RevisionPipelineCatalog.REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET);
        boolean deleteDraftComment = selectedOptions.contains(
                RevisionPipelineCatalog.DELETE_DRAFT_COMMENT);

        if (!removeSimpleOperation && !deleteDraftComment) {
            return;
        }

        Path batchFile = workspace.resolve(batchFileName);
        String content = Files.readString(batchFile, BATCH_FILE_CHARSET);
        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        String currentSource = "temp\\0180-translate_no_tagging.xml";
        List<String> extraLines = new ArrayList<>();

        if (removeSimpleOperation) {
            String output = "temp\\0402-remove_simple_operation_deliverytarget.xml";
            appendOptionTransform(
                    workspace,
                    extraLines,
                    currentSource,
                    output,
                    "0402-Remove_Simple_Operation_And_DeliveryTarget.xsl");
            currentSource = output;
            logs.add("옵션 추가: 속성 및 세션 지우기");
        }

        if (deleteDraftComment) {
            String output = "temp\\0401-remove_review_Delete_Draft_Comment.xml";
            appendOptionTransform(
                    workspace,
                    extraLines,
                    currentSource,
                    output,
                    "0401-remove_review_Delete_Draft_Comment.xsl");
            currentSource = output;
            logs.add("옵션 추가: Draft Comment 지우기");
        }

        List<String> patched = new ArrayList<>();
        for (String line : content.split("\\R", -1)) {
            String patchedLine = line;
            if (line.contains("-s:temp\\0180-translate_no_tagging.xml")
                    && line.contains("0400-remove_review.xsl")) {
                patchedLine = line.replace(
                        "-s:temp\\0180-translate_no_tagging.xml",
                        "-s:" + currentSource);
            }

            patched.add(patchedLine);
            if (line.contains("-o:temp\\0180-translate_no_tagging.xml")
                    && line.contains("0180-translate_no_tagging.xsl")) {
                patched.addAll(extraLines);
            }
        }

        Files.writeString(
                batchFile,
                String.join(lineSeparator, patched),
                BATCH_FILE_CHARSET);
    }

    private void appendOptionTransform(
            Path workspace,
            List<String> lines,
            String source,
            String output,
            String stylesheetName) {

        Path stylesheet = workspace.resolve("xsl").resolve(stylesheetName);
        if (!Files.isRegularFile(stylesheet)) {
            throw new IllegalArgumentException(
                    "옵션 XSL 파일을 찾지 못했습니다: " + stylesheetName);
        }

        lines.add("java net.sf.saxon.Transform "
                + "-s:" + source + " "
                + "-o:" + output + " "
                + "-xsl:xsl\\" + stylesheetName);
    }

    private void runBatch(
            Path workspace,
            String batchFileName,
            List<String> logs) throws IOException, InterruptedException {

        Path batchFile = workspace.resolve(batchFileName);
        if (!Files.isRegularFile(batchFile)) {
            throw new IllegalArgumentException(
                    "정제 배치 파일을 찾지 못했습니다: " + batchFileName);
        }

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/c",
                batchFile.getFileName().toString())
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();

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
                    "정제 배치 실행 실패(" + exitCode + "): "
                    + batchFileName);
        }
    }

    private void replaceDirectory(Path source, Path target)
            throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "결과 폴더가 생성되지 않았습니다: " + source);
        }
        deleteDirectoryQuietly(target);
        copyDirectory(source, target);
    }

    private void validateBatchOutput(
            Path workspace,
            RevisionFormat outputType,
            BatchPlan batchPlan) throws IOException {

        if (outputType == RevisionFormat.XML) {
            Path chapter = workspace.resolve("chapter");
            if (!hasFileWithExtension(chapter, ".xml")) {
                throw new IllegalStateException(
                        "XML 출력 결과가 없습니다. 마지막 배치("
                        + batchPlan.lastBatchFile()
                        + ") 실행 후 chapter 폴더에 xml 파일이 생성되지 않았습니다: "
                        + chapter);
            }
            return;
        }

        Path topics = workspace.resolve("topics");
        if (!hasFileWithExtension(topics, ".dita", ".ditamap")) {
            throw new IllegalStateException(
                    "DITA 출력 결과가 없습니다. 마지막 배치("
                    + batchPlan.lastBatchFile()
                    + ") 실행 후 topics 폴더에 dita/ditamap 파일이 생성되지 않았습니다: "
                    + topics);
        }
    }

    private void copyBookmap(Path workspace, Path runOutput, Path input)
            throws IOException {
        Path rootBookmap = workspace.resolve("bookmap.xml");
        Path xslBookmap = workspace.resolve("xsl/bookmap.xml");

        if (Files.isRegularFile(rootBookmap)) {
            Files.copy(
                    rootBookmap,
                    runOutput.resolve("bookmap.xml"),
                    StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        if (Files.isRegularFile(xslBookmap)) {
            Files.copy(
                    xslBookmap,
                    runOutput.resolve("bookmap.xml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 별도 Java 프로세스로 Saxon Transform을 실행하고 표준 출력을 로그에 모은다.
     * 종료 코드가 0이 아니면 해당 XSL 단계 실패로 처리한다.
     */
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
                workspace.resolve("lib/saxon-he-12.4.jar").toString(),
                workspace.resolve("lib/xmlresolver-5.2.2.jar").toString());

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

    /** 요청 단계 ID가 실제 파이프라인에 존재하는지 확인하고 중복 없는 집합으로 바꾼다. */
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

    /** 후속 단계가 필요로 하는 보조 파일 생성 단계도 함께 선택됐는지 검사한다. */
    private void validateDependencies(Set<String> selected) {
        if (selected.contains("DITA_REBEAUTIFY")
                && !selected.contains("BOOKMAP_CREATE")) {
            throw new IllegalArgumentException(
                    "DITA 구조 정렬을 선택하려면 Bookmap 정보 생성도 선택해야 합니다.");
        }
    }

    /** .maoomtool 아래 실행별 작업 폴더를 만든다. */
    private Path createWorkDirectory(Path inputDirectory) {
        String folderName = inputDirectory.getFileName() == null
                ? "revision"
                : inputDirectory.getFileName().toString();
        String safeName = folderName.replaceAll("[^A-Za-z0-9._-]", "_");

        if (safeName.isBlank()) {
            safeName = "revision";
        }

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "revision-" + safeName + "-"
                + LocalDateTime.now().format(RUN_FORMAT))
                .toAbsolutePath()
                .normalize();
    }

    /** Input 경로가 실제 폴더인지 확인하고 비교 가능한 절대경로로 정규화한다. */
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

    /** Output 경로를 절대경로로 정규화하고 없으면 생성한다. */
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

    /** 작업 결과가 원본 안에 생성되거나 원본이 출력 안에 들어가는 위험한 경로를 막는다. */
    private void validateSeparatePaths(Path input, Path output) {
        if (input.equals(output)
                || input.startsWith(output)
                || output.startsWith(input)) {
            throw new IllegalArgumentException(
                    "Input과 Output은 서로 포함되지 않는 별도 폴더로 지정해주세요.");
        }
    }

    /** Saxon과 DITA 참조에서 문제가 되는 공백·비 ASCII 파일명을 실행 전에 찾는다. */
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

    /** 정제 파이프라인의 최초 입력으로 사용할 첫 번째 ditamap을 찾는다. */
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

    /** JAR 내부 revision-tool 리소스를 일반 파일처럼 실행할 수 있는 경로로 준비한다. */
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
            copyDirectory(root.getFile().toPath(), target);
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

    /** 원본 topics와 XSL 디렉터리를 임시 작업 공간으로 재귀 복사한다. */
    private void copyDirectory(Path source, Path target)
            throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "복사할 원본 폴더가 없습니다: " + source);
        }
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

    /** 선택 단계에서 만들어질 수도 있는 보조 결과 파일만 최종 폴더로 복사한다. */
    private void copyIfExists(Path source, Path target)
            throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 실행 성공·실패와 관계없이 임시 작업 폴더를 뒤에서부터 조용히 제거한다. */
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

    /** 단계별 입력·출력 방식 차이를 구분한다. */
    private enum StepMode {
        TRANSFORM,
        DOCTYPE,
        AUXILIARY,
        CHAPTER
    }

    /** 파이프라인 한 단계의 화면 정보와 XSL 실행 설정을 함께 보관한다. */
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

