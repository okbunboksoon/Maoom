package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultilingualConversionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesLogWithUtf8BomForWindowsTools() throws Exception {
        Path logFile = tempDirectory.resolve("multilingual.log");
        MultilingualConversionService service =
                new MultilingualConversionService();

        Method method = MultilingualConversionService.class.getDeclaredMethod(
                "writeLog",
                Path.class,
                List.class);
        method.setAccessible(true);
        method.invoke(service, logFile, List.of("작업 폴더: test"));

        byte[] bytes = Files.readAllBytes(logFile);
        assertThat(bytes)
                .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(Files.readString(logFile))
                .contains("작업 폴더: test");
    }

    @Test
    void createsBookmapFromXmlFileNamesOrderedByChapterNumber()
            throws Exception {

        Path workspace = tempDirectory.resolve("workspace");
        Path languageDirectory = workspace.resolve(
                "topics/ar-AE_2026MAR04/ar-AE");
        Files.createDirectories(languageDirectory);
        Files.createDirectories(workspace.resolve("xsl"));
        Files.writeString(
                languageDirectory.resolve("02_Overview.xml"),
                "<chapter filename=\"02_\" chapnum=\"03\"/>");
        Files.writeString(
                languageDirectory.resolve("Foreword.xml"),
                "<chapter filename=\"Foreword.xml\" chapnum=\"01\"/>");
        Files.writeString(
                languageDirectory.resolve("01_Intro.xml"),
                "<chapter filename=\"01_Intro.xml\" chapnum=\"02\"/>");

        MultilingualConversionService service =
                new MultilingualConversionService();
        Method method = MultilingualConversionService.class.getDeclaredMethod(
                "prepareBookmapForXmlInput",
                Path.class,
                List.class,
                String.class);
        method.setAccessible(true);
        method.invoke(
                service,
                workspace,
                new ArrayList<String>(),
                "KIA-SP3-ICE-HEV-en_GB-2027.ditamap");

        String bookmap =
                Files.readString(workspace.resolve("xsl/bookmap.xml"));

        assertThat(bookmap)
                .contains("mapname=\"KIA-SP3-ICE-HEV-en_GB-2027.ditamap\"")
                .contains("<chapter filename=\"Foreword.xml\"/>")
                .contains("<chapter filename=\"01_Intro.xml\"/>")
                .contains("<chapter filename=\"02_Overview.xml\"/>");
        assertThat(bookmap.indexOf("Foreword.xml"))
                .isLessThan(bookmap.indexOf("01_Intro.xml"));
        assertThat(bookmap.indexOf("01_Intro.xml"))
                .isLessThan(bookmap.indexOf("02_Overview.xml"));
        assertThat(bookmap)
                .doesNotContain("filename=\"02_\"");
    }
}
