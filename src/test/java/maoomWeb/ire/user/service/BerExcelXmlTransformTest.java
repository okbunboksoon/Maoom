package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

    @Test
    void keepsNoChangePairOutOfChangedStatus() throws Exception {
        String unchanged = "No service change; contact an authorized Kia dealer.";
        String oldText = "Old guidance; contact an authorized Kia dealer.";
        String newText = "New guidance; contact an authorized Kia dealer.";
        Path input = tempDirectory.resolve("input.xml");
        Path output = tempDirectory.resolve("output.xml");
        Path stylesheet = tempDirectory.resolve("0340-kus-db-apply_ber.xsl");
        Files.copy(
                Path.of("src/main/resources/xsl/0340-kus-db-apply_ber.xsl"),
                stylesheet);
        Files.writeString(input, """
                <?xml version="1.0" encoding="UTF-8"?>
                <map><title>KIA-EN_CA_LHD</title>
                  <p>%s</p>
                  <p>%s</p>
                </map>
                """.formatted(unchanged, oldText), StandardCharsets.UTF_8);
        Files.writeString(tempDirectory.resolve("asis-tobe_eu.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <pairs>
                  <pair hash="%s"><old>%s</old><new>%s</new></pair>
                  <pair hash="%s"><old>%s</old><new>%s</new></pair>
                </pairs>
                """.formatted(
                        sha256(unchanged), unchanged, unchanged,
                        sha256(oldText), oldText, newText),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                Path.of("src/main/resources/lib/saxon-ee-10.0.jar")
                        .toAbsolutePath().toString(),
                "net.sf.saxon.Transform",
                "-s:" + input,
                "-xsl:" + stylesheet,
                "-o:" + output,
                "flag=on")
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String processOutput = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(process.exitValue())
                .withFailMessage(processOutput)
                .isZero();

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(output.toFile());
        Element first = (Element) document.getElementsByTagName("p").item(0);
        Element second = (Element) document.getElementsByTagName("p").item(1);

        assertThat(first.hasAttribute("status")).isFalse();
        assertThat(first.getTextContent()).isEqualTo(unchanged);
        assertThat(second.getAttribute("status")).isEqualTo("ber_changed");
        assertThat(second.getTextContent()).isEqualTo(newText);
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().withUpperCase().formatHex(digest);
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
