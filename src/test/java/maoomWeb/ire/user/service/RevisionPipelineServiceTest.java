package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        assertThat(input.resolve("Result_Folder/revision.log"))
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
        assertThat(input.resolve("Result_Folder/revision.log"))
                .exists();
    }

    @Test
    void patchesUtf8ChapterizeBatchWhenCleanupOptionsAreSelected() throws Exception {
        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace"));
        Path xsl = Files.createDirectory(workspace.resolve("xsl"));
        Files.copy(
                Path.of("src/main/resources/revision-tool/02_topics_Chapterize.bat"),
                workspace.resolve("02_topics_Chapterize.bat"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(
                xsl.resolve("0402-Remove_Simple_Operation_And_DeliveryTarget.xsl"),
                "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>",
                StandardCharsets.UTF_8);
        Files.writeString(
                xsl.resolve("0401-remove_review_Delete_Draft_Comment.xsl"),
                "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>",
                StandardCharsets.UTF_8);

        RevisionPipelineService service = new RevisionPipelineService();
        List<String> logs = new ArrayList<>();
        Method method = RevisionPipelineService.class.getDeclaredMethod(
                "prepareBatchOptions",
                Path.class,
                String.class,
                Set.class,
                List.class);
        method.setAccessible(true);

        method.invoke(
                service,
                workspace,
                "02_topics_Chapterize.bat",
                Set.of(
                        RevisionPipelineCatalog.REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET,
                        RevisionPipelineCatalog.DELETE_DRAFT_COMMENT),
                logs);

        String patched = Files.readString(
                workspace.resolve("02_topics_Chapterize.bat"),
                StandardCharsets.UTF_8);
        assertThat(patched)
                .contains("0402-Remove_Simple_Operation_And_DeliveryTarget.xsl")
                .contains("0401-remove_review_Delete_Draft_Comment.xsl")
                .contains("-s:temp\\0401-remove_review_Delete_Draft_Comment.xml");
        assertThat(logs)
                .contains("옵션 추가: 속성 및 세션 지우기")
                .contains("옵션 추가: Draft Comment 지우기");
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
}
