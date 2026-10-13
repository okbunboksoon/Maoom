package com.sts.dita.xref;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XrefValidationEngineTest {
    @TempDir Path dir;

    @Test
    void listsAllXrefsAndSeparatesFormatFileMapAndIdChecks() throws Exception {
        write("map.ditamap", "<map><topicref href=\"a.dita\"/><topicref href=\"b.dita\"/></map>");
        write("a.dita", "<concept id=\"a\"><title>A</title><conbody><p>"+
                "<xref href=\"b.dita#b\">normal</xref>"+
                "<xref href=\"c.dita#c\">unlinked</xref>"+
                "<xref href=\"missing.dita#x\">missing</xref>"+
                "<xref href=\"b.dita\">no hash</xref>"+
                "<xref href=\"bad%20name.dita#x\">percent</xref>"+
                "<xref href=\"b.dita#none\">bad id</xref>"+
                "<xref href=\"b.dita#b/e00\">element id forbidden</xref>"+
                "</p></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><title>B</title><conbody><p id=\"e00\">text</p></conbody></concept>");
        write("c.dita", "<concept id=\"c\"><title>C</title><conbody/></concept>");

        XrefValidationResult result = new XrefValidationEngine().validate(dir.resolve("map.ditamap"));
        assertEquals(7, result.xrefs().size());
        assertCheck(result, "b.dita#b", "정상", "정상", "정상", "정상");
        assertCheck(result, "c.dita#c", "정상", "정상", "미연결", "정상");
        assertCheck(result, "missing.dita#x", "정상", "파일 없음", "미연결", "확인 불가");
        assertCheck(result, "b.dita", "오류", "정상", "정상", "확인 불가");
        assertCheck(result, "bad%20name.dita#x", "오류", "파일 없음", "미연결", "확인 불가");
        assertCheck(result, "b.dita#none", "정상", "정상", "정상", "ID 없음");
        assertCheck(result, "b.dita#b/e00", "오류", "정상", "정상", "ID 없음");
        assertTrue(result.xrefs().stream().filter(x -> "b.dita#b/e00".equals(x.href()))
                .findFirst().orElseThrow().message().contains("element ID는 사용할 수 없음"));

        Path report = dir.resolve("xref-report.html");
        new XrefHtmlReportWriter().write(result, report);
        String html = Files.readString(report, StandardCharsets.UTF_8);
        assertAll(() -> assertTrue(html.contains("전체 xref")),
                () -> assertTrue(html.contains("map 연결")),
                () -> assertTrue(html.contains("오류만 보기")),
                () -> assertTrue(html.contains("c.dita#c")),
                () -> assertFalse(html.contains("xref 텍스트")),
                () -> assertFalse(html.contains("id=\"shown\"")),
                () -> assertFalse(html.contains("표시 '+n+'건")));
    }

    private void assertCheck(XrefValidationResult result, String href, String format, String file, String map, String id) {
        XrefCheckResult x = result.xrefs().stream().filter(v -> href.equals(v.href())).findFirst().orElseThrow();
        assertAll(href, () -> assertEquals(format, x.formatStatus()), () -> assertEquals(file, x.fileStatus()),
                () -> assertEquals(map, x.mapStatus()), () -> assertEquals(id, x.idStatus()));
    }

    private void write(String name, String xml) throws Exception {
        Files.writeString(dir.resolve(name), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xml, StandardCharsets.UTF_8);
    }
}
