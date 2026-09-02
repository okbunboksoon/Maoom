package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.RevisionRunRequest;
import maoomWeb.ire.user.dto.RevisionRunResult;

class RevisionPipelineServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsMissingOutputWhenBatchCreatesNoChapterFiles() throws Exception {
        Path input = Files.createDirectory(tempDirectory.resolve("topics"));
        Path output = Files.createDirectory(tempDirectory.resolve("output"));
        Files.writeString(
                input.resolve("sample.ditamap"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map/>");

        RevisionPipelineService service = new RevisionPipelineService();
        RevisionRunResult result = service.run(new RevisionRunRequest(
                input.toString(),
                output.toString(),
                null,
                "xml",
                List.of(),
                null));

        assertThat(result.success())
                .as(String.join(System.lineSeparator(), result.logs()))
                .isFalse();
        assertThat(result.completedOptions())
                .isEmpty();
        assertThat(result.logs())
                .anyMatch(log -> log.contains("XML 출력 결과가 없습니다."));
        assertThat(ResultFolderNames.resolve(input, "revision")
                .resolve("revision.log"))
                .exists();
    }

    @Test
    void requiresBookmapMapNameWhenXmlInputHasNoBookmap() throws Exception {
        Path input = Files.createDirectory(tempDirectory.resolve("xml-input"));
        Files.writeString(
                input.resolve("01_Intro.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><chapter/>");

        RevisionPipelineService service = new RevisionPipelineService();
        RevisionRunResult result = service.run(new RevisionRunRequest(
                input.toString(),
                null,
                null,
                "dita",
                List.of(),
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.logs())
                .anyMatch(log -> log.contains("BOOKMAP_MAPNAME_REQUIRED:"));
        assertThat(ResultFolderNames.resolve(input, "revision")
                .resolve("revision.log"))
                .exists();
    }

    @Test
    void writesRevisionLogWhenRunSucceeds() throws Exception {
        Path input = Files.createDirectory(tempDirectory.resolve("success-input"));
        Path workspace = Files.createDirectory(tempDirectory.resolve("success-workspace"));
        Path chapter = Files.createDirectory(workspace.resolve("chapter"));
        Files.writeString(chapter.resolve("01_Intro.xml"), "<chapter/>");

        RevisionPipelineService service = new RevisionPipelineService();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "writeLog",
                Path.class,
                List.class);
        method.setAccessible(true);

        Path resultFolder = ResultFolderNames.resolve(input, "revision");
        method.invoke(
                service,
                resultFolder,
                List.of("배치 실행: sample.bat", "완료: " + resultFolder));

        assertThat(resultFolder.resolve("revision.log"))
                .exists()
                .content(StandardCharsets.UTF_8)
                .contains("배치 실행: sample.bat")
                .contains("완료: " + resultFolder);
    }

    @Test
    void appendsChapterizeBatchArgumentsWhenOptionsAreSelected() throws Exception {
        RevisionPipelineService service = new RevisionPipelineService();
        List<String> logs = new ArrayList<>();
        List<String> command = new ArrayList<>(List.of(
                "cmd.exe",
                "/c",
                "02_topics_Chapterize_NotFileNameChange.bat"));
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "appendBatchArguments",
                String.class,
                RevisionFormat.class,
                RevisionFormat.class,
                Set.class,
                List.class,
                List.class);
        method.setAccessible(true);

        method.invoke(
                service,
                "02_topics_Chapterize_NotFileNameChange.bat",
                RevisionFormat.DITA,
                RevisionFormat.XML,
                Set.of(
                        RevisionPipelineCatalog.FILE_NAME_KEEP,
                        RevisionPipelineCatalog.REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET,
                        RevisionPipelineCatalog.DELETE_DRAFT_COMMENT),
                command,
                logs);

        assertThat(command)
                .contains(
                        "INPUT_TYPE=dita",
                        "OUTPUT_TYPE=xml",
                        "FILE_NAME_CHANGE=Y",
                        "REMOVE_DELIVERY_TARGET=Y",
                        "REMOVE_SIMPLE_OPERATION=Y",
                        "DELETE_DRAFT=Y");
        assertThat(logs)
                .contains("옵션 추가: 파일명 변경")
                .contains("옵션 추가: deliveryTarget 지우기")
                .contains("옵션 추가: Simple operation 지우기")
                .contains("옵션 추가: Draft Comment, review, hash, modified 지우기");
    }

    @Test
    void keepsSingleTopicalizeBatchForXmlToDitaWithoutCleanupOptions() {
        RevisionPipelineCatalog.BatchPlan plan =
                RevisionPipelineCatalog.createBatchPlan(
                        RevisionFormat.XML,
                        RevisionFormat.DITA,
                        Set.of());

        assertThat(plan.batchFiles())
                .containsExactly("03_chapter_Topicalize.bat");
    }

    @Test
    void reusesExistingRoundTripBatchesForXmlToDitaWithCleanupOptions() {
        RevisionPipelineCatalog.BatchPlan plan =
                RevisionPipelineCatalog.createBatchPlan(
                        RevisionFormat.XML,
                        RevisionFormat.DITA,
                        Set.of(RevisionPipelineCatalog.DELETE_DRAFT_COMMENT));

        assertThat(plan.batchFiles())
                .containsExactly(
                        "03_chapter_Topicalize.bat",
                        "02_topics_Chapterize_NotFileNameChange.bat",
                        "03_chapter_Topicalize.bat");
    }

    @Test
    void generatedBookmapIncludesNumberedFilesWithoutXmlExtension() throws Exception {
        Path input = Files.createDirectory(tempDirectory.resolve("input"));
        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace-bookmap"));
        Path chapter = Files.createDirectory(workspace.resolve("chapter"));
        Files.createDirectory(workspace.resolve("xsl"));
        Files.writeString(chapter.resolve("02_Intro.xml"), "<chapter/>");
        Files.writeString(chapter.resolve("00"), "<chapter/>");
        Files.writeString(chapter.resolve("01.txt"), "<chapter/>");
        Files.writeString(chapter.resolve("memo.txt"), "skip");

        RevisionPipelineService service = new RevisionPipelineService();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "prepareBookmap",
                Path.class,
                RevisionFormat.class,
                Path.class,
                String.class);
        method.setAccessible(true);

        method.invoke(service, input, RevisionFormat.XML, workspace, "sample.ditamap");

        String bookmap = Files.readString(
                workspace.resolve("bookmap.xml"),
                StandardCharsets.UTF_8);
        assertThat(bookmap)
                .containsSubsequence(
                        "filename=\"00\"",
                        "filename=\"01.txt\"",
                        "filename=\"02_Intro.xml\"")
                .doesNotContain("memo.txt");
    }

    @Test
    void copiesBookmapFromXmlSourceParentDirectory() throws Exception {
        Path inputRoot = Files.createDirectory(tempDirectory.resolve("xml_dita"));
        Path topics = Files.createDirectory(inputRoot.resolve("topics"));
        Files.writeString(topics.resolve("01_Intro.xml"), "<chapter/>");
        Files.writeString(
                inputRoot.resolve("bookmap.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmap mapname=\"parent.ditamap\"/>",
                StandardCharsets.UTF_8);

        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace-parent-bookmap"));
        Path chapter = Files.createDirectory(workspace.resolve("chapter"));
        Files.createDirectory(workspace.resolve("xsl"));
        Files.writeString(chapter.resolve("01_Intro.xml"), "<chapter/>");

        RevisionPipelineService service = new RevisionPipelineService();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "prepareBookmap",
                Path.class,
                RevisionFormat.class,
                Path.class,
                String.class);
        method.setAccessible(true);

        method.invoke(service, topics, RevisionFormat.XML, workspace, null);

        assertThat(Files.readString(
                workspace.resolve("bookmap.xml"),
                StandardCharsets.UTF_8))
                .contains("mapname=\"parent.ditamap\"");
    }

    @Test
    void copiesBookmapFromChapterInputParentDirectory() throws Exception {
        Path inputRoot = Files.createDirectory(tempDirectory.resolve("xml_chapter"));
        Path chapterInput = Files.createDirectory(inputRoot.resolve("chapter"));
        Files.writeString(chapterInput.resolve("01_Intro.xml"), "<chapter/>");
        Files.writeString(
                inputRoot.resolve("bookmap.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmap mapname=\"chapter-parent.ditamap\"/>",
                StandardCharsets.UTF_8);

        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace-chapter-parent"));
        Path chapter = Files.createDirectory(workspace.resolve("chapter"));
        Files.createDirectory(workspace.resolve("xsl"));
        Files.writeString(chapter.resolve("01_Intro.xml"), "<chapter/>");

        RevisionPipelineService service = new RevisionPipelineService();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "prepareBookmap",
                Path.class,
                RevisionFormat.class,
                Path.class,
                String.class);
        method.setAccessible(true);

        method.invoke(service, chapterInput, RevisionFormat.XML, workspace, null);

        assertThat(Files.readString(
                workspace.resolve("bookmap.xml"),
                StandardCharsets.UTF_8))
                .contains("mapname=\"chapter-parent.ditamap\"");
    }

    @Test
    void ignoresBookmapInsideXmlSourceDirectory() throws Exception {
        Path topics = Files.createDirectory(tempDirectory.resolve("topics"));
        Files.writeString(topics.resolve("01_Intro.xml"), "<chapter/>");
        Files.writeString(
                topics.resolve("bookmap.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmap mapname=\"ignored.ditamap\"/>",
                StandardCharsets.UTF_8);

        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace-ignore-bookmap"));
        Path chapter = Files.createDirectory(workspace.resolve("chapter"));
        Files.createDirectory(workspace.resolve("xsl"));
        Files.writeString(chapter.resolve("01_Intro.xml"), "<chapter/>");

        RevisionPipelineService service = new RevisionPipelineService();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "prepareBookmap",
                Path.class,
                RevisionFormat.class,
                Path.class,
                String.class);
        method.setAccessible(true);

        method.invoke(
                service,
                topics,
                RevisionFormat.XML,
                workspace,
                "generated.ditamap");

        assertThat(Files.readString(
                workspace.resolve("bookmap.xml"),
                StandardCharsets.UTF_8))
                .contains("mapname=\"generated.ditamap\"")
                .doesNotContain("ignored.ditamap");
    }

    @Test
    void recognizesDatedResultFolders() {
        assertThat(ResultFolderNames.isGeneratedResultFolder(
                "260731_Result_Folder_revision"))
                .isTrue();
        assertThat(ResultFolderNames.isGeneratedResultFolder(
                "Result_Folder"))
                .isTrue();
        assertThat(ResultFolderNames.isGeneratedResultFolder(
                "topics"))
                .isFalse();
    }
}
