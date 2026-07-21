package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedBatchResourceCopyTest {

    @TempDir
    Path tempDirectory;

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
}
