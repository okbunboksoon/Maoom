package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomaticNoticeServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void detectsKoAndUsMarketFromPdfName() throws Exception {
        AutomaticNoticeService service = new AutomaticNoticeService();
        Method method = AutomaticNoticeService.class
                .getDeclaredMethod("detectMarket", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "KIA-CV1a-EV-en-US-2027-OM"))
                .isEqualTo("US");
        assertThat(method.invoke(service, "KIA-CV1a-EV-en-CA-2027-OM"))
                .isEqualTo("US");
        assertThat(method.invoke(service, "KIA-CV1a-EV-en-MX-2027-OM"))
                .isEqualTo("US");
        assertThat(method.invoke(service, "KIA_CV1a_EV_KO_2027_OM"))
                .isEqualTo("KO");
        assertThat(method.invoke(service, "KIA-CV1a-EV-en-GB-2027-OM"))
                .isEqualTo("EG");
    }

    @Test
    void copiesBundledRulesToResultDirectory() throws Exception {
        AutomaticNoticeService service = new AutomaticNoticeService();
        Method method = AutomaticNoticeService.class.getDeclaredMethod(
                "copyBundledRules",
                String.class,
                Path.class);
        method.setAccessible(true);

        Path destination = tempDirectory.resolve("ANG_us_rules.xlsx");
        method.invoke(service, "ANG_us_rules.xlsx", destination);

        assertThat(Files.exists(destination)).isTrue();
        assertThat(Files.size(destination)).isGreaterThan(0);
    }
}
