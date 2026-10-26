package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DitaOnlyLanguageCodeTransformTest {

    @TempDir
    Path tempDirectory;

    @Test
    void changesDitaFileNameRootIdAndHrefWithoutAddingXmlLang()
            throws Exception {

        // 261026_DITA전용: DITAMAP 없는 번역 DITA의 실제 변환 회귀 테스트
        Path inputDirectory = tempDirectory.resolve("input-fr");
        Files.createDirectories(inputDirectory);
        Path input = inputDirectory.resolve(
                "OV1k-EV-en_GB-2027-t0058.dita");
        Files.writeString(input, """
                <?xml version="1.0" encoding="utf-8"?>
                <concept id="OV1k-EV-en_GB-2027-t0058">
                    <title>Texte français</title>
                    <conbody><p>électricité</p></conbody>
                    <related-links>
                        <link href="OV1k-EV-en_GB-2027-t0057.dita#OV1k-EV-en_GB-2027-t0057"/>
                    </related-links>
                </concept>
                """);

        transform(input, "French");

        Path result = tempDirectory.resolve(
                "OV1k-EV-fr_FR-2027-t0058.dita");
        assertThat(result).exists();

        String xml = Files.readString(result);
        assertThat(xml)
                .contains("id=\"OV1k-EV-fr_FR-2027-t0058\"")
                .contains("href=\"OV1k-EV-fr_FR-2027-t0057.dita#OV1k-EV-fr_FR-2027-t0057\"")
                .contains("Texte français")
                .contains("électricité")
                .doesNotContain("en_GB")
                .doesNotContain("xml:lang");
    }

    @Test
    void keepsTNumberFileNameAndUsesMappedSerbianCode()
            throws Exception {

        // 261026_DITA전용: 코드 없는 t0000 파일명과 Serbian 매핑(sr_RS)을 함께 검증
        Path inputDirectory = tempDirectory.resolve("input-sr");
        Files.createDirectories(inputDirectory);
        Path plainInput = inputDirectory.resolve("t0000.dita");
        Files.writeString(plainInput, """
                <?xml version="1.0" encoding="utf-8"?>
                <concept id="t0000"><title>Naslov</title><conbody/></concept>
                """);
        transform(plainInput, "Serbian");

        assertThat(tempDirectory.resolve("t0000.dita")).exists();
        assertThat(Files.readString(tempDirectory.resolve("t0000.dita")))
                .contains("id=\"t0000\"")
                .doesNotContain("xml:lang");

        Path codedInput = inputDirectory.resolve(
                "OV1k-EV-en_GB-2027-t0174.dita");
        Files.writeString(codedInput, """
                <?xml version="1.0" encoding="utf-8"?>
                <concept id="OV1k-EV-en_GB-2027-t0174">
                    <title>Naslov</title><conbody/>
                </concept>
                """);
        transform(codedInput, "Serbian");

        assertThat(tempDirectory.resolve(
                "OV1k-EV-sr_RS-2027-t0174.dita"))
                .exists();
    }

    private void transform(Path input, String languageName)
            throws Exception {

        Path stylesheet = Path.of(
                "src/main/resources/xsl/0000-dita-language-code.xsl")
                .toAbsolutePath();
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(
                new StreamSource(stylesheet.toFile()));
        XsltTransformer transformer = executable.load();
        transformer.setSource(new StreamSource(input.toFile()));
        transformer.setParameter(
                new QName("langName"),
                new XdmAtomicValue(languageName));
        transformer.setParameter(
                new QName("srcFile"),
                new XdmAtomicValue(input.getFileName().toString()));

        Serializer serializer = processor.newSerializer(
                tempDirectory.resolve("dummy.xml").toFile());
        transformer.setDestination(serializer);
        transformer.transform();
    }
}
