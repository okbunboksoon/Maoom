package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @Test
    void changeReportCollectsAuthorizedAndAuthorisedDealerSentences()
            throws Exception {

        String xsl = Files.readString(
                Path.of("src/main/resources/xsl/0410-make-change-report_ber.xsl"),
                StandardCharsets.UTF_8);

        assertThat(xsl)
                .contains("contains($txt, 'an authorized Kia dealer')")
                .contains("contains($txt, 'an authorised Kia dealer')")
                .contains("an authorized Kia dealer 또는 an authorised Kia dealer가 포함된 전체 문장");
    }

    @Test
    void usesSameSpecificRegionTokensAcrossBerStylesheets()
            throws Exception {

        for(String fileName : new String[]{
                "0320-kus-pair-extract_ber.xsl",
                "0340-kus-db-apply_ber.xsl",
                "0410-make-change-report_ber.xsl"}){
            String xsl = Files.readString(
                    Path.of("src/main/resources/xsl", fileName),
                    StandardCharsets.UTF_8);

            assertThat(xsl)
                    .contains("EN_CA_LHD")
                    .contains("EN_RG")
                    .contains("EN_US")
                    .contains("EN_CA")
                    .contains("EN_MX");
        }

        String applyXsl = Files.readString(
                Path.of(
                        "src/main/resources/xsl/0340-kus-db-apply_ber.xsl"),
                StandardCharsets.UTF_8);
        String reportXsl = Files.readString(
                Path.of(
                        "src/main/resources/xsl/0410-make-change-report_ber.xsl"),
                StandardCharsets.UTF_8);

        assertThat(applyXsl)
                .contains("if ($isCaLhd) then 'asis-tobe_eu.xml'");
        assertThat(reportXsl)
                .contains("if ($isCaLhd) then 'asis-tobe_eu.xml'")
                .contains("$candidate-targets[not(descendant::* intersect $candidate-targets)]")
                .contains("select=\"count($targets[@status = 'ber_changed'])\"")
                .contains("select=\"count($unchanged-targets)\"")
                .contains("<xsl:for-each select=\"$unchanged-targets\">")
                .contains("not(descendant-or-self::*[@status = 'ber_changed'])")
                .doesNotContain("contains(upper-case($mapTitle), 'IN')");
    }

}
