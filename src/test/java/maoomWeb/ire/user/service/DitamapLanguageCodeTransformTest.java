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

class DitamapLanguageCodeTransformTest {

    @TempDir
    Path tempDirectory;

    @Test
    void changesDitamapFileNameAndAllInternalLanguageReferences()
            throws Exception {

        Path input = tempDirectory.resolve(
                "KIA-OVc-EV-en_CN-2026.ditamap");
        Files.writeString(input, """
                <?xml version="1.0" encoding="utf-8"?>
                <map xml:lang="en-US">
                    <title>KIA-OVc-EV-en_CN-2026</title>
                    <topicmeta><metadata>
                        <data name="lang">en_CN</data>
                    </metadata></topicmeta>
                    <topicref href="OVc-EV-en_CN-2026-t0001.dita">
                        <topicref href="OVc-EV-en_GB-2026-t0002.dita#OVc-EV-en_US-2026-t0002"/>
                    </topicref>
                </map>
                """);

        transform(input, "Chinese");

        Path result = tempDirectory.resolve(
                "KIA-OVc-EV-zh_CN-2026.ditamap");
        assertThat(result).exists();

        String xml = Files.readString(result);
        assertThat(xml)
                .contains("xml:lang=\"zh-CN\"")
                .contains("KIA-OVc-EV-zh_CN-2026")
                .contains("<data name=\"lang\">zh_CN</data>")
                .contains("href=\"OVc-EV-zh_CN-2026-t0001.dita\"")
                .contains("href=\"OVc-EV-zh_CN-2026-t0002.dita#OVc-EV-zh_CN-2026-t0002\"")
                .doesNotContain("en_CN")
                .doesNotContain("en_GB")
                .doesNotContain("en_US");
    }

    private void transform(Path input, String languageName)
            throws Exception {

        Path stylesheet = Path.of(
                "src/main/resources/xsl/0000-ditamap-language-code.xsl")
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
