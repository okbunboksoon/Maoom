package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DitamapBuilderServiceTest {

    @Test
    void treatsMappedDrivePathAsAllowedUncChild() throws Exception {
        DitamapPathService service = new DitamapPathService("");

        cacheMappedDrive(service, "V:", "\\\\172.16.10.15\\QC_Docs");

        assertThat(service.isSameOrChildPath(
                "V:\\Tools\\test\\BER\\KIA-MV1-EV-en_GB-2027",
                "\\\\172.16.10.15\\QC_Docs"))
                .isTrue();
    }

    @Test
    void rejectsMappedDrivePathOutsideAllowedUncRoot() throws Exception {
        DitamapPathService service = new DitamapPathService("");

        cacheMappedDrive(service, "V:", "\\\\172.16.10.15\\QC_Docs");

        assertThat(service.isSameOrChildPath(
                "V:\\Tools\\test\\BER",
                "\\\\172.16.10.15\\kia_om26"))
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private void cacheMappedDrive(
            DitamapPathService service,
            String drive,
            String remote)
            throws Exception {
        Field field =
                DitamapPathService.class.getDeclaredField("mappedDriveCache");
        field.setAccessible(true);
        ((Map<String, String>)field.get(service)).put(drive, remote);
    }
}
