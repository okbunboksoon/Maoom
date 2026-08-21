package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

class BerAsisTobeXmlServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesEuEuRgAndUsXmlFilesFromDatabaseRows() throws Exception {
        BerAsisTobePairMapper mapper = new StubMapper(
                List.of(pair(
                        1L,
                        "EU",
                        "EU_HASH",
                        "Use <old> & check.",
                        "Use <new> & check.")),
                List.of(pair(
                        3L,
                        "EU_RG",
                        "EU_RG_HASH",
                        "RG old.",
                        "RG new.")),
                List.of(pair(
                        2L,
                        "US",
                        "US_HASH",
                        "Contact an authorized Kia dealer.",
                        "Contact an authorized Kia dealer or service partner.")));

        BerAsisTobeXmlService service = new BerAsisTobeXmlService(mapper);

        service.writeRegionXmlFiles(tempDirectory);

        String euXml = Files.readString(
                tempDirectory.resolve("asis-tobe_eu.xml"),
                StandardCharsets.UTF_8);
        String euRgXml = Files.readString(
                tempDirectory.resolve("asis-tobe_eu_rg.xml"),
                StandardCharsets.UTF_8);
        String usXml = Files.readString(
                tempDirectory.resolve("asis-tobe_us.xml"),
                StandardCharsets.UTF_8);

        assertThat(euXml)
                .contains("<pairs>")
                .contains("<pair hash=\"EU_HASH\">")
                .contains("<old>Use &lt;old&gt; &amp; check.</old>")
                .contains("<new>Use &lt;new&gt; &amp; check.</new>");
        assertThat(usXml)
                .contains("<pair hash=\"US_HASH\">")
                .contains("<new>Contact an authorized Kia dealer or service partner.</new>");
        assertThat(euRgXml)
                .contains("<pair hash=\"EU_RG_HASH\">")
                .contains("<new>RG new.</new>");
    }

    @Test
    void writesInlineXmlFragmentsWithoutEscapingTags() throws Exception {
        BerAsisTobePairMapper mapper = new StubMapper(
                List.of(pair(
                        1L,
                        "EU",
                        "INLINE_HASH",
                        "See <xref/> before <term>ON</term>.",
                        "See <xref/> after <term>OFF</term>.")),
                List.of(),
                List.of());

        BerAsisTobeXmlService service = new BerAsisTobeXmlService(mapper);

        service.writeRegionXmlFiles(tempDirectory);

        String euXml = Files.readString(
                tempDirectory.resolve("asis-tobe_eu.xml"),
                StandardCharsets.UTF_8);

        assertThat(euXml)
                .contains("<old>See <xref/> before <term>ON</term>.</old>")
                .contains("<new>See <xref/> after <term>OFF</term>.</new>");
    }

    @Test
    void keepsExistingXmlFileWhenRegionHasNoDatabaseRows() throws Exception {
        Files.writeString(
                tempDirectory.resolve("asis-tobe_eu.xml"),
                "<pairs><pair hash=\"EXISTING\"/></pairs>",
                StandardCharsets.UTF_8);

        BerAsisTobeXmlService service = new BerAsisTobeXmlService(
                new StubMapper(List.of(), List.of(), List.of()));

        service.writeRegionXmlFiles(tempDirectory);

        assertThat(Files.readString(
                tempDirectory.resolve("asis-tobe_eu.xml"),
                StandardCharsets.UTF_8))
                .contains("EXISTING");
    }

    private static BerAsisTobePair pair(
            Long id,
            String region,
            String hash,
            String oldText,
            String newText) {

        BerAsisTobePair pair = new BerAsisTobePair();
        pair.setId(id);
        pair.setRegion(region);
        pair.setHash(hash);
        pair.setOldText(oldText);
        pair.setNewText(newText);
        return pair;
    }

    private record StubMapper(
            List<BerAsisTobePair> euRows,
            List<BerAsisTobePair> euRgRows,
            List<BerAsisTobePair> usRows)
            implements BerAsisTobePairMapper {

        @Override
        public List<BerAsisTobePair> findByRegion(String region) {
            if("EU".equals(region)){
                return euRows;
            }
            if("EU_RG".equals(region)){
                return euRgRows;
            }
            return usRows;
        }

        @Override
        public int countByRegion(String region) {
            return findByRegion(region).size();
        }

        @Override
        public List<BerAsisTobePair> findAll() {
            return List.of();
        }

        @Override
        public BerAsisTobePair findByRegionAndHash(
                String region,
                String hash) {
            return null;
        }

        @Override
        public int upsert(BerAsisTobePair pair) {
            return 0;
        }

        @Override
        public int deleteByRegionAndHash(String region, String hash) {
            return 0;
        }
    }
}
