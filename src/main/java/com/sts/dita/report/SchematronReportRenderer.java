package com.sts.dita.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 클래스패스의 HTML·CSS·JavaScript 리소스를 읽어 단독 실행 가능한 리포트 한 파일로 렌더링한다.
 */
public final class SchematronReportRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");
    private static final String TEMPLATE = readResource("report.html");
    private static final String CSS = readResource("report.css");
    private static final String JAVASCRIPT = String.join("\n",
            readResource("report-core.js"),
            readResource("report-filter.js"),
            readResource("report-state.js"),
            readResource("report-export.js"));

    public String render(SchematronReportModel model) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("REPORT_CSS", CSS),
                Map.entry("REPORT_JS", JAVASCRIPT),
                Map.entry("MAP_NAME", model.mapName()),
                Map.entry("REPORT_FILE_NAME", model.reportFileName()),
                Map.entry("HISTORY_ID", model.historyId()),
                Map.entry("MAP_PATH", model.mapPath()),
                Map.entry("FILE_COUNT", model.fileCount()),
                Map.entry("ISSUE_COUNT", model.issueCount()),
                Map.entry("CATEGORY_SUMMARY", model.categorySummary()),
                Map.entry("SEVERITY_SUMMARY", model.severitySummary()),
                Map.entry("FINISHED", model.finished()),
                Map.entry("USER_SUMMARY", model.userSummary()),
                Map.entry("MAP_TREE_JSON", model.mapTreeJson()),
                Map.entry("ROWS", model.rows()));
        Matcher matcher = PLACEHOLDER.matcher(TEMPLATE);
        StringBuilder html = new StringBuilder(TEMPLATE.length() + CSS.length() + JAVASCRIPT.length());
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("지원하지 않는 리포트 placeholder입니다: " + matcher.group());
            }
            matcher.appendReplacement(html, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(html);
        return html.toString();
    }

    private static String readResource(String fileName) {
        String path = "/dita-report/" + fileName;
        try (InputStream input = SchematronReportRenderer.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("리포트 리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("리포트 리소스를 읽지 못했습니다: " + path, ex);
        }
    }
}
