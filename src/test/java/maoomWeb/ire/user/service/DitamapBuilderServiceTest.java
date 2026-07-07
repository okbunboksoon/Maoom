package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DitamapBuilderServiceTest {

    @Test
    void treatsMappedDrivePathAsAllowedUncChild() throws Exception {
        DitamapBuilderService service = new DitamapBuilderService(
                "",
                new DitamapLegalHashService(false, ""),
                null,
                null);

        cacheMappedDrive(service, "V:", "\\\\192.168.10.221\\QC_Docs");

        assertThat(service.isSameOrChildPath(
                "V:\\Tools\\test\\BER\\KIA-MV1-EV-en_GB-2027",
                "\\\\192.168.10.221\\QC_Docs"))
                .isTrue();
    }

    @Test
    void rejectsMappedDrivePathOutsideAllowedUncRoot() throws Exception {
        DitamapBuilderService service = new DitamapBuilderService(
                "",
                new DitamapLegalHashService(false, ""),
                null,
                null);

        cacheMappedDrive(service, "V:", "\\\\192.168.10.221\\QC_Docs");

        assertThat(service.isSameOrChildPath(
                "V:\\Tools\\test\\BER",
                "\\\\192.168.10.221\\kia_om26"))
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private void cacheMappedDrive(
            DitamapBuilderService service,
            String drive,
            String remote)
            throws Exception {
        Field field =
                DitamapBuilderService.class.getDeclaredField("mappedDriveCache");
        field.setAccessible(true);
        ((Map<String, String>)field.get(service)).put(drive, remote);
    }
}
