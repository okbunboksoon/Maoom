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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DitaSchematronServiceTest {
    @TempDir Path tempDir;

    @Test
    void validatesAllSelectedAndDescendantScopesAndWritesCollaborativeReport() throws Exception {
        Path a = write("a.dita", "<concept id=\"a\"><title>A</title><conbody><p/></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><title>B</title><conbody><p>ok</p></conbody></concept>");
        Path map = write("root.ditamap", "<map><topicref href=\"a.dita\"><topicmeta><navtitle>A...</navtitle><linktext>A linktext</linktext></topicmeta><topicref href=\"b.dita\"><topicmeta><navtitle>B...</navtitle><linktext>B linktext</linktext></topicmeta></topicref></topicref></map>");
        Path sch = write("check.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" xmlns:maoom=\"urn:maoom:schematron\" queryBinding=\"xslt2\"><pattern id=\"empty\"><rule context=\"p\">"
                + "<assert role=\"none\" maoom:category=\"image\" test=\"normalize-space(.) != ''\">빈 문장</assert>"
                + "<assert role=\"critical\" test=\"normalize-space(.) != ''\">Critical 검사</assert>"
                + "<assert role=\"major\" test=\"normalize-space(.) != ''\">Major 검사</assert>"
                + "<assert role=\"minor\" test=\"normalize-space(.) != ''\">Minor 검사</assert>"
                + "<assert role=\"preference\" test=\"normalize-space(.) != ''\">Preference 검사</assert>"
                + "</rule></pattern></schema>");
        DitaSchematronService service = new DitaSchematronService();
        MapModel model = service.loadMap(map);
        assertEquals("A linktext", model.roots().get(0).title());

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
                () -> assertTrue(html.contains("<button id=\"exportJson\" class=\"hidden\">상태 JSON 저장</button>")),
                () -> assertTrue(html.contains("<button id=\"importJson\" class=\"hidden\">상태 JSON 불러오기</button>")),
                () -> assertTrue(html.contains("HTML 로컬 다운")),
                () -> assertTrue(html.contains("검사 ditamap: <b>root.ditamap</b>")),
                () -> assertTrue(html.contains("<div class=\"category-summary\"><span class=\"category-summary-label\">유형별</span>")),
                () -> assertTrue(html.contains("class=\"category-chip category-chip-total\">전체 <b>5</b>건</button>")),
                () -> assertTrue(html.contains("data-category=\"image\">이미지 <b>1</b></button>")),
                () -> assertTrue(html.contains("data-category=\"uncategorized\">미분류 <b>4</b></button>")),
                () -> assertTrue(html.contains("심각도별")),
                () -> assertTrue(html.contains("data-severity=\"none\">none <b>1</b></button>")),
                () -> assertTrue(html.contains("/state/batch")),
                () -> assertTrue(html.contains(".summary{gap:10px 24px;padding:16px 18px")),
                () -> assertTrue(html.contains(".category-chip-total{border-color:#9fc1ee")),
                () -> assertTrue(html.contains("data-map-name=\"root.ditamap\"")),
                () -> assertTrue(html.contains("return date+'-'+time+'-'+mapName+'-SchematronReport.html'")),
                () -> assertTrue(html.contains("download(reportDownloadName()")),
                () -> assertTrue(html.contains("ditamap 경로: " + map)),
                () -> assertTrue(html.contains("data-issue-id")),
                () -> assertTrue(html.contains("<tr class=\"level-1\" data-issue-id=")),
                () -> assertTrue(html.contains(".level-1 td:nth-child(2),.level-1 td:nth-child(3){color:#1f4f8a;font-weight:700}")),
                () -> assertTrue(html.contains("localStorage.setItem")),
                () -> assertTrue(html.contains("isLocalFile=location.protocol==='file:'")),
                () -> assertTrue(html.contains("historyId&&!isLocalFile")),
                () -> assertTrue(html.contains("확인자 이름은 기록되지 않습니다")),
                () -> assertTrue(html.contains("class=\"confirmer center\"")),
                () -> assertTrue(html.contains(".table-wrap{overflow:auto;border:1px solid #cfd8e3")),
                () -> assertTrue(html.contains("tbody tr:hover td{background:#edf5ff}")),
                () -> assertTrue(html.contains("tbody tr.completed td{background:#eef9f2")),
                () -> assertTrue(html.contains("<td class=\"center\">1</td>")),
                () -> assertTrue(html.contains("class=\"file-filter\" data-file=\"a.dita\"")),
                () -> assertTrue(html.contains("<td class=\"report-title\" data-search-title=\"A\" title=\"A\"><span class=\"breadcrumb\" dir=\"auto\"><span class=\"breadcrumb-current\">A</span></span></td>")),
                () -> assertTrue(html.contains("<td class=\"report-message\">빈 문장</td>")),
                () -> assertTrue(html.contains("th:nth-child(3),td:nth-child(3){width:20%}")),
                () -> assertTrue(html.contains("th:nth-child(9),td:nth-child(9){width:25%}")),
                () -> assertTrue(html.contains(".report-title,.location-cell,.report-message{overflow-wrap:anywhere")),
                () -> assertTrue(html.contains("<div class=\"search-tools\">")),
                () -> assertTrue(html.contains("<div class=\"table-wrap\"><table id=\"result\">")),
                () -> assertTrue(html.contains("table.querySelectorAll('.report-title,.location-cell,.report-message')")),
                () -> assertTrue(html.contains("function prepareSearchIndex()")),
                () -> assertTrue(html.contains("let title=r.cells[2]?.dataset.searchTitle||''")),
                () -> assertTrue(html.contains("searchTimer=setTimeout(filterRows,180)")),
                () -> assertTrue(html.contains("id=\"exportCsv\">현재 결과 CSV")),
                () -> assertTrue(html.contains("id=\"completion\" class=\"completion\"")),
                () -> assertTrue(html.contains("filterStorageKey=storageKey+':filters'")),
                () -> assertTrue(html.contains("다른 사용자가 '+changed.length+'개 항목")),
                () -> assertTrue(html.contains("function showToast(message,error=false)")),
                () -> assertTrue(html.contains("확인 상태를 저장했습니다.")),
                () -> assertTrue(html.contains("setInterval(()=>{if(!document.hidden)loadServerState(true)")),
                () -> assertTrue(html.contains("<td class=\"updated center\"></td>")),
                () -> assertTrue(html.contains("<th data-type=\"num\">순번</th><th>파일명</th><th class=\"report-title\">타이틀</th><th data-type=\"num\">행</th><th>규칙 ID</th><th>심각도</th><th>유형</th><th>위치</th><th class=\"report-message\">메시지</th><th>작업 메모</th><th>확인</th><th>수정 시각</th><th>확인자</th>")),
                () -> assertTrue(html.contains("/memo',{method:'POST'")),
                () -> assertTrue(html.contains("id=\"clearFileFilter\"")),
                () -> assertTrue(html.contains(".memo-cell{box-sizing:border-box;padding:5px 7px}")),
                () -> assertTrue(html.contains(".memo-wrap{box-sizing:border-box;width:100%;max-width:100%;overflow:hidden}")),
                () -> assertTrue(html.contains("size=\"1\" maxlength=\"500\"")),
                () -> assertTrue(html.indexOf(".memo-cell{") < html.indexOf("<script id=\"embeddedState\"")),
                () -> assertTrue(html.indexOf(".file-filter{") < html.indexOf("<body class=\"report-loading\"")),
                () -> assertTrue(html.contains("class=\"report-loading\"")),
                () -> assertTrue(html.contains(".report-loading .table-wrap{visibility:hidden}")),
                () -> assertTrue(html.contains(".severity-error .severity-badge{background:#7f1d1d;color:#fff}")),
                () -> assertTrue(html.contains("allRows=[...body.rows],rowByIssueId=new Map")),
                () -> assertTrue(html.contains("let r=rowByIssueId.get(v.issueId)")),
                () -> assertFalse(html.contains("<th>경로</th>")),
                () -> assertTrue(html.contains("data-severity=\"none\"><span class=\"severity-badge\">none</span></td>")),
                () -> assertTrue(html.contains("data-category=\"image\"><span class=\"category-badge\">이미지</span></td>")),
                () -> assertTrue(html.contains("data-category=\"uncategorized\"><span class=\"category-badge\">미분류</span></td>")),
                () -> assertTrue(html.contains("data-severity=\"critical\"><span class=\"severity-badge\">Critical</span></td>")),
                () -> assertTrue(html.contains("data-severity=\"major\"><span class=\"severity-badge\">Major</span></td>")),
                () -> assertTrue(html.contains("data-severity=\"minor\"><span class=\"severity-badge\">Minor</span></td>")),
                () -> assertTrue(html.contains("data-severity=\"preference\"><span class=\"severity-badge\">Preference</span></td>")),
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

    @Test
    void blocksSchematronDocumentAccessOutsideProjectAndNetwork() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path outside = write("outside.xml", "<root><secret>outside</secret></root>");
        Path map = project.resolve("root.ditamap");
        Files.writeString(map, "<map/>", StandardCharsets.UTF_8);
        Path schema = project.resolve("restricted.sch");
        Files.writeString(schema, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="outside"><rule context="map">
                    <report role="none" test="exists(doc('../outside.xml')/root)">외부 파일</report>
                  </rule></pattern>
                  <pattern id="network"><rule context="map">
                    <report role="none" test="exists(doc('https://example.com/test.xml')/root)">네트워크</report>
                  </rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);

        ValidationResult result = new DitaSchematronService().validate(
                new ValidationRequest(map, schema, ValidationScope.ALL, Set.of(), true, project));

        assertTrue(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("document-parse-error")
                && issue.message().contains("doc() 접근이 차단")), () -> result.issues().toString());
        assertTrue(Files.isRegularFile(outside));
    }

    @Test
    void doesNotResolveExternalEntitiesWhileAllowingDoctype() throws Exception {
        Path secret = write("secret.txt", "TOP-SECRET");
        Path map = tempDir.resolve("xxe.ditamap");
        Files.writeString(map, "<!DOCTYPE map [<!ENTITY xxe SYSTEM \""
                + secret.toUri() + "\">]><map><title>&xxe;</title></map>", StandardCharsets.UTF_8);

        MapModel model = new DitaSchematronService().loadMap(map);

        assertFalse(model.title().contains("TOP-SECRET"));
    }

    @Test
    void writesBreadcrumbWithTruncatedParentAndFullCurrentTitle() throws Exception {
        write("parent.dita", "<concept id=\"parent\"><title>Parent document title</title><conbody><p>정상</p></conbody></concept>");
        write("child.dita", "<concept id=\"child\"><title>Current file title without truncation</title><conbody><p/></conbody></concept>");
        String longParent = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345";
        Path map = write("breadcrumb.ditamap", "<map><topicref href=\"parent.dita\"><topicmeta><linktext>"
                + longParent + "</linktext></topicmeta><topicref href=\"child.dita\"><topicmeta><linktext>Child</linktext>"
                + "</topicmeta></topicref></topicref></map>");
        Path schema = write("breadcrumb.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\">"
                + "<pattern id=\"empty\"><rule context=\"p\"><assert role=\"none\" test=\"normalize-space(.) != ''\">빈 문장</assert>"
                + "</rule></pattern></schema>");
        Path report = tempDir.resolve("breadcrumb-report.html");

        new DitaSchematronService().validateAndWriteReport(
                new ValidationRequest(map, schema, ValidationScope.ALL, Set.of(), false), report);

        String html = Files.readString(report, StandardCharsets.UTF_8);
        String fullBreadcrumb = longParent + " › Current file title without truncation";
        assertAll(
                () -> assertTrue(html.contains("data-search-title=\"" + fullBreadcrumb + "\"")),
                () -> assertTrue(html.contains("title=\"" + fullBreadcrumb + "\"")),
                () -> assertTrue(html.contains("<span class=\"breadcrumb-parent\" title=\"" + longParent
                        + "\">ABCDEFGHIJKLMNOPQRSTUVWXY…</span>")),
                () -> assertTrue(html.contains("<span class=\"breadcrumb-current\">Current file title without truncation</span>")),
                () -> assertTrue(html.contains("<tr class=\"level-2\" data-issue-id=")),
                () -> assertTrue(html.contains(".breadcrumb-parent{display:inline-block;max-width:180px")));
    }

    @Test
    void validatesDuplicateTargetOnceAndMarksAdditionalBreadcrumbPaths() throws Exception {
        write("shared.dita", "<concept id=\"shared\"><title>Shared</title><conbody><p/></conbody></concept>");
        Path map = write("duplicate-path.ditamap", "<map>"
                + "<topicref navtitle=\"First\"><topicref href=\"shared.dita\"/></topicref>"
                + "<topicref navtitle=\"Second\"><topicref href=\"shared.dita\"/></topicref></map>");
        Path schema = write("duplicate-path.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\">"
                + "<pattern id=\"p\"><rule context=\"p\"><assert role=\"none\" test=\"normalize-space(.)\">빈 문장</assert>"
                + "</rule></pattern></schema>");
        Path report = tempDir.resolve("duplicate-path.html");

        ValidationResult result = new DitaSchematronService().validateAndWriteReport(
                new ValidationRequest(map, schema, ValidationScope.ALL, Set.of(), false), report);

        assertEquals(1, result.validatedFileCount());
        assertTrue(Files.readString(report).contains("외 1개 경로"));
    }

    @Test
    void detectsLinkedFnXrefAndIgnoresXrefsThatDoNotMeetBothConditions() throws Exception {
        Path invalid = write("invalid.dita", "<concept id=\"invalid\"><title>Invalid</title><conbody><p>"
                + "<xref type=\"fn\" href=\"target.dita#target\">각주 링크</xref>"
                + "</p></conbody></concept>");
        Path noHref = write("no-href.dita", "<concept id=\"no-href\"><title>No href</title><conbody><p>"
                + "<xref type=\"fn\">각주</xref>"
                + "</p></conbody></concept>");
        Path otherType = write("other-type.dita", "<concept id=\"other-type\"><title>Other</title><conbody><p>"
                + "<xref type=\"concept\" href=\"target.dita#target\">일반 링크</xref>"
                + "</p></conbody></concept>");
        write("target.dita", "<concept id=\"target\"><title>Target</title><conbody><p>대상</p></conbody></concept>");
        Path map = write("xref-fn.ditamap", "<map><topicref href=\"invalid.dita\"/>"
                + "<topicref href=\"no-href.dita\"/><topicref href=\"other-type.dita\"/></map>");
        Path schema = Path.of(getClass().getClassLoader()
                .getResource("schematron/DOCS_CHECK_v1.0.6.sch").toURI());

        ValidationResult result = new DitaSchematronService().validate(
                new ValidationRequest(map, schema, ValidationScope.ALL, Set.of(), false));

        var fnIssues = result.issues().stream()
                .filter(issue -> issue.ruleId().equals("xref-fn-element-check"))
                .toList();
        assertAll(
                () -> assertEquals(1, fnIssues.size()),
                () -> assertEquals(invalid, fnIssues.get(0).file()),
                () -> assertEquals("<fn> 태그를 사용하여 내용을 입력해야 합니다.", fnIssues.get(0).message()),
                () -> assertEquals("none", fnIssues.get(0).severity()),
                () -> assertTrue(fnIssues.stream().noneMatch(issue -> issue.file().equals(noHref))),
                () -> assertTrue(fnIssues.stream().noneMatch(issue -> issue.file().equals(otherType))));
    }

    @Test
    void stopsValidationWhenCancellationIsRequested() throws Exception {
        write("a.dita", "<concept id=\"a\"><title>A</title><conbody><p>A</p></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><title>B</title><conbody><p>B</p></conbody></concept>");
        Path map = write("cancel.ditamap", "<map><topicref href=\"a.dita\"/><topicref href=\"b.dita\"/></map>");
        Path sch = write("cancel.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\">"
                + "<pattern id=\"p\"><rule context=\"p\"><assert role=\"none\" test=\"true()\">정상</assert>"
                + "</rule></pattern></schema>");
        AtomicInteger checks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> new DitaSchematronService().validate(
                new ValidationRequest(map, sch, ValidationScope.ALL, Set.of(), false),
                (completed, total, currentFile) -> { },
                () -> {
                    if (checks.incrementAndGet() >= 3) throw new CancellationException("검사 취소");
                }));
        assertTrue(checks.get() >= 3);
    }

    private Path write(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().normalize();
    }
}
