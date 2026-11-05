package com.sts.dita.report;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SchematronReportRendererTest {
    @Test
    void rendersAStandaloneReportWithoutUnresolvedPlaceholders() {
        SchematronReportModel model = new SchematronReportModel(
                "테스트 {{ROWS}}.ditamap", "report.html", "17", "C:/test/테스트.ditamap",
                "2", "1", "<span>유형</span>", "<span>심각도</span>",
                "2026-11-04 14:00:00", "<span>QA 테스터</span>", "[]",
                "<tr data-issue-id=\"issue-1\"><td>1</td></tr>");

        String html = new SchematronReportRenderer().render(model);

        assertAll(
                () -> assertTrue(html.startsWith("<!doctype html>")),
                () -> assertTrue(html.contains("테스트 {{ROWS}}.ditamap")),
                () -> assertTrue(html.contains("<style>")),
                () -> assertTrue(html.contains("function filterRows()")),
                () -> assertTrue(html.contains("function loadServerState")),
                () -> assertTrue(html.contains("id=\"mapTreeData\"")),
                () -> assertEquals(2, html.split("\\{\\{ROWS}}", -1).length - 1),
                () -> assertEquals(1, html.split("data-issue-id=\"issue-1\"", -1).length - 1));
    }
}
