package maoomWeb.ire.user.service;

import com.sts.dita.core.MapModel;
import com.sts.dita.core.ValidationRequest;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.ValidationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DitaSchematronServiceTest {
    @TempDir Path tempDir;

    @Test
    void validatesAllSelectedAndDescendantScopesAndWritesCollaborativeReport() throws Exception {
        assertNotNull(getClass().getClassLoader().getResource("schematron/DOCS_CHECK_2026-05-27.sch"));
        Path a = write("a.dita", "<concept id=\"a\"><title>A</title><conbody><p/></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><title>B</title><conbody><p>ok</p></conbody></concept>");
        Path map = write("root.ditamap", "<map><topicref href=\"a.dita\"><topicmeta><navtitle>A</navtitle></topicmeta><topicref href=\"b.dita\"><topicmeta><navtitle>B</navtitle></topicmeta></topicref></topicref></map>");
        Path sch = write("check.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\"><pattern id=\"empty\"><rule context=\"p\">"
                + "<assert role=\"none\" test=\"normalize-space(.) != ''\">빈 문장</assert>"
                + "<assert role=\"critical\" test=\"normalize-space(.) != ''\">Critical 검사</assert>"
                + "<assert role=\"major\" test=\"normalize-space(.) != ''\">Major 검사</assert>"
                + "<assert role=\"minor\" test=\"normalize-space(.) != ''\">Minor 검사</assert>"
                + "<assert role=\"preference\" test=\"normalize-space(.) != ''\">Preference 검사</assert>"
                + "</rule></pattern></schema>");
        DitaSchematronService service = new DitaSchematronService();
        MapModel model = service.loadMap(map);

        ValidationResult all = service.validate(new ValidationRequest(map, sch, ValidationScope.ALL, Set.of(), true));
        assertEquals(3, all.validatedFileCount());
        assertTrue(all.issues().stream().anyMatch(i -> i.file().equals(a) && i.ruleId().equals("empty")));
        assertTrue(all.issues().stream().anyMatch(i -> i.file().equals(a) && i.severity().equals("none")));

        ValidationResult selected = service.validate(new ValidationRequest(map, sch, ValidationScope.SELECTED_ONLY,
                Set.of(model.roots().get(0).id()), false));
        assertEquals(1, selected.validatedFileCount());

        Path report = tempDir.resolve("report.html");
        ValidationResult descendants = service.validateAndWriteReport(new ValidationRequest(map, sch,
                ValidationScope.SELECTED_WITH_DESCENDANTS, Set.of(model.roots().get(0).id()), false), report);
        assertEquals(2, descendants.validatedFileCount());
        String html = Files.readString(report, StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(html.contains("상태 JSON 저장")),
                () -> assertTrue(html.contains("상태 포함 HTML 저장")),
                () -> assertTrue(html.contains("검사 ditamap: <b>root.ditamap</b>")),
                () -> assertTrue(html.contains("ditamap 경로: " + map)),
                () -> assertTrue(html.contains("data-issue-id")),
                () -> assertTrue(html.contains("localStorage.setItem")),
                () -> assertTrue(html.contains("textContent=checked?new Date().toLocaleString('ko-KR'):''")),
                () -> assertTrue(html.contains("th{cursor:pointer;background:#26384a;color:white;position:sticky;top:0;z-index:1;text-align:center}")),
                () -> assertTrue(html.contains("<td class=\"center\">1</td>")),
                () -> assertTrue(html.contains("<td class=\"center\">a.dita</td>")),
                () -> assertTrue(html.contains("<th data-type=\"num\">순번</th><th>파일명</th><th>경로</th><th data-type=\"num\">행</th><th>규칙 ID</th><th>심각도</th><th>위치</th><th>메시지</th><th>확인</th><th>수정 시각</th>")),
                () -> assertTrue(html.contains("data-severity=\"none\">none</td>")),
                () -> assertTrue(html.contains("data-severity=\"critical\">Critical</td>")),
                () -> assertTrue(html.contains("data-severity=\"major\">Major</td>")),
                () -> assertTrue(html.contains("data-severity=\"minor\">Minor</td>")),
                () -> assertTrue(html.contains("data-severity=\"preference\">Preference</td>")),
                () -> assertFalse(html.contains("<th>상태</th>")),
                () -> assertFalse(html.contains("<th>메모</th>")),
                () -> assertFalse(html.contains("<th>작업자</th>"))
        );
    }

    @Test
    void supportsPublishingDocumentLookupAndReportRules() throws Exception {
        write("publication.dita", "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA 1.3 Concept//EN\" \"concept.dtd\">"
                + "<concept id=\"publication\"><title>Required publication text</title><conbody><p>내용</p></conbody></concept>");
        Path map = write("publication.ditamap", "<map><topicref href=\"publication.dita\"/></map>");
        Path sch = write("PUB_CHECK_v1.0.0.sch", """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="external-document"><rule context="map">
                    <assert test="exists(document(resolve-uri(topicref/@href, base-uri(.)))//concept[title='Required publication text'])">외부 DITA 확인 실패</assert>
                    <report role="Preference" test="true()">육안 확인 필요</report>
                  </rule></pattern>
                </schema>
                """);

        ValidationResult result = new DitaSchematronService().validate(
                new ValidationRequest(map, sch, ValidationScope.ALL, Set.of(), true));

        assertFalse(result.issues().stream().anyMatch(issue -> issue.message().contains("외부 DITA 확인 실패")));
        assertFalse(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("document-parse-error")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("external-document")
                && issue.message().equals("육안 확인 필요") && issue.severity().equals("preference")));
    }

    private Path write(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().normalize();
    }
}
