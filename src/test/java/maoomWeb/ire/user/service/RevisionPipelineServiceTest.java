package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
