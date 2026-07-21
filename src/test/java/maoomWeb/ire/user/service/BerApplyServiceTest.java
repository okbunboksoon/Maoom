package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BerApplyServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void preparesSharedBatchResourcesBeforeRequiredFileCheck()
            throws Exception {

        Path workDirectory = tempDirectory.resolve("ber-work");
        BerApplyService service = new BerApplyService();
        Method method = BerApplyService.class.getDeclaredMethod(
                "prepareToolDirectory",
                Path.class);
        method.setAccessible(true);

        method.invoke(service, workDirectory);

        assertThat(workDirectory.resolve(
                "04_KUS_asis-tobe-apply_NotFileNameChange.bat"))
                .isRegularFile();
        assertThat(workDirectory.resolve("lib/saxon-ee-10.0.jar"))
                .isRegularFile();
        assertThat(workDirectory.resolve(
                "xsl/0006-id-clean_NotFileNameChange.xsl"))
                .isRegularFile();
    }
}
