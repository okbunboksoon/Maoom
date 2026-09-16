package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

class BerExcelXmlTransformTest {

    @TempDir
    Path tempDirectory;

    @Test
    void skipsHeaderAndParsesInlineXmlInsideSentence() throws Exception {
        Path input = tempDirectory.resolve("excel.xml");
        Path output = tempDirectory.resolve("output.xml");
        Files.writeString(input, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                        xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
                  <Worksheet ss:Name="Sheet1"><Table>
                    <Row><Cell><Data ss:Type="String">as-is</Data></Cell>
                         <Cell><Data ss:Type="String">to-be</Data></Cell></Row>
                    <Row><Cell><Data ss:Type="String">Before &amp; after &lt;menucascade&gt;&lt;uicontrol&gt;Menu&lt;/uicontrol&gt;&lt;/menucascade&gt;.</Data></Cell>
                         <Cell><Data ss:Type="String">Changed &amp; retained &lt;xref href="target.dita"&gt;Target&lt;/xref&gt;.</Data></Cell></Row>
                  </Table></Worksheet>
                </Workbook>
                """, StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                Path.of("src/main/resources/lib/saxon-ee-10.0.jar")
                        .toAbsolutePath().toString(),
                "net.sf.saxon.Transform",
                "-s:" + input,
                "-xsl:" + Path.of(
                        "src/main/resources/xsl/0100-excel-to-xml-update.xsl")
                        .toAbsolutePath(),
                "-o:" + output)
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String processOutput = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(process.exitValue())
                .withFailMessage(processOutput)
                .isZero();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(output.toFile());

        assertThat(document.getElementsByTagName("p").getLength()).isEqualTo(1);
        assertThat(document.getElementsByTagName("menucascade").getLength())
                .isEqualTo(1);
        assertThat(document.getElementsByTagName("uicontrol").getLength())
                .isEqualTo(1);
        assertThat(document.getElementsByTagName("xref").getLength())
                .isEqualTo(1);
        assertThat(document.getDocumentElement().getTextContent())
                .contains("Before & after", "Changed & retained")
                .doesNotContain("as-is", "to-be");
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe"
                        : "java")
                .toString();
    }
}
