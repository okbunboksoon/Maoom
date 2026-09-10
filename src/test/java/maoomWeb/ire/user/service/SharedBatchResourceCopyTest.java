package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedBatchResourceCopyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void sharedBatchFilesUseWindowsLineEndings()
            throws Exception {

        Path batchRoot = Path.of(
                "src",
                "main",
                "resources",
                "bat");

        try (Stream<Path> files = Files.list(batchRoot)) {
            assertThat(files
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".bat"))
                    .filter(this::hasLoneLineFeeds)
                    .map(path -> path.getFileName().toString())
                    .toList())
                    .isEmpty();
        }
    }

    @Test
    void revisionResourceCopyKeepsWorkspaceWhenCopyingRootBatchFiles()
            throws Exception {

        Path workspace = tempDirectory.resolve("revision-work");
        Files.createDirectories(workspace);
        Path marker = Files.writeString(workspace.resolve("marker.txt"), "keep");

        invokeSharedCopy(
                new RevisionPipelineService(),
                "bat",
                workspace,
                false);

        assertThat(marker).isRegularFile();
        assertThat(workspace.resolve("02_topics_Chapterize.bat"))
                .isRegularFile();
        assertThat(workspace.resolve(
                "04_KUS_asis-tobe-apply_NotFileNameChange.bat"))
                .isRegularFile();
    }

    @Test
    void multilingualResourceCopyKeepsWorkspaceWhenCopyingRootBatchFiles()
            throws Exception {

        Path workspace = tempDirectory.resolve("multilingual-work");
        Files.createDirectories(workspace);
        Path marker = Files.writeString(workspace.resolve("marker.txt"), "keep");

        invokeSharedCopy(
                new MultilingualConversionService(),
                "bat",
                workspace,
                false);

        assertThat(marker).isRegularFile();
        assertThat(workspace.resolve(
                "02_topics_Chapterize_NotFileNameChange.bat"))
                .isRegularFile();
    }

    @Test
    void qsgResourceCopyKeepsWorkspaceWhenCopyingRootBatchFiles()
            throws Exception {

        Path workspace = tempDirectory.resolve("qsg-work");
        Files.createDirectories(workspace);
        Path marker = Files.writeString(workspace.resolve("marker.txt"), "keep");

        invokeSharedCopy(
                new QsgApplyService(),
                "bat",
                workspace,
                false);

        assertThat(marker).isRegularFile();
        assertThat(workspace.resolve("02_QSG_apply.bat"))
                .isRegularFile();
    }

    private void invokeSharedCopy(
            Object service,
            String resourceRoot,
            Path target,
            boolean cleanTarget)
            throws Exception {

        Method method = service.getClass().getDeclaredMethod(
                "copySharedResourceDirectory",
                String.class,
                Path.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(service, resourceRoot, target, cleanTarget);
    }

    private boolean hasLoneLineFeeds(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n'
                        && (i == 0 || bytes[i - 1] != '\r')) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "배치 파일 줄바꿈 검사 실패: "
                    + path.toString(),
                    exception);
        }
    }
}
