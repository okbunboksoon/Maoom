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
    void runsSelectedStylesheetAndWritesResult() throws Exception {
        Path input = Files.createDirectory(tempDirectory.resolve("topics"));
        Path output = Files.createDirectory(tempDirectory.resolve("output"));
        Files.writeString(
                input.resolve("sample.ditamap"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map/>");

        RevisionPipelineService service = new RevisionPipelineService();
        RevisionRunResult result = service.run(new RevisionRunRequest(
                input.toString(),
                output.toString(),
                List.of("NAMESPACE_REMOVE_1")));

        assertThat(result.success()).isTrue();
        assertThat(result.completedOptions())
                .containsExactly("NAMESPACE_REMOVE_1");
        assertThat(Path.of(result.outputPath())
                .resolve("revision-result.xml"))
                .exists();
    }
}
