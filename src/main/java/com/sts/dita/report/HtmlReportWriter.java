package com.sts.dita.report;

import com.sts.dita.core.ValidationIssue;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.MapModel;
import com.sts.dita.core.MapNode;
import com.sts.dita.core.MapParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HtmlReportWriter {
    private static final DateTimeFormatter REPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyMMdd_HHmm")
            .withZone(ZoneId.systemDefault());

    /** 서버 저장과 사용자 다운로드가 동일한 이름을 사용하도록 검사 완료 시각으로 파일명을 고정한다. */
    public static String reportFileName(ValidationResult result) {
        return REPORT_FILE_TIME.format(result.finishedAt()) + "_" + mapBaseName(result)
                + "_SchematronReport.html";
    }

    public static String gomsReportFileName(ValidationResult result) {
        return REPORT_FILE_TIME.format(result.finishedAt()) + "_" + mapBaseName(result) + "_report.html";
    }

    private static String mapBaseName(ValidationResult result) {
        String mapName = result.mapFile().getFileName().toString().replaceFirst("(?i)\\.ditamap$", "")
                .replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return mapName.isBlank() ? "ditamap" : mapName;
    }

    /** 독립 실행기처럼 사용자 정보가 없는 호출도 동일한 보고서를 만들 수 있게 유지한다. */
    public void write(ValidationResult result, Path output) throws Exception {
        write(result, output, null, null, null);
    }

    public void write(
            ValidationResult result,
            Path output,
            String userName,
            String userId) throws Exception {
        write(result, output, null, userName, userId);
    }

    public void write(
            ValidationResult result,
            Path output,
            Long historyId,
            String userName,
            String userId) throws Exception {
        // 검사 완료 시간과 QA 테스터를 포함한 결과·확인 상태 관리 화면을 하나의 HTML로 생성한다.
        MapTreeData mapTree = mapTreeData(result.mapFile());
        StringBuilder rows = new StringBuilder();
        int index = 1;
        for (ValidationIssue issue : result.issues()) {
            // 고정 ID를 만들어 정렬 후에도 저장된 체크 상태가 같은 오류 항목에 연결되게 한다.
            String issueId = issueId(result.mapFile(), issue);
            rows.append("<tr class=\"level-").append(issue.level()).append("\" data-issue-id=\"")
                    .append(issueId).append("\" data-map-paths=\"")
                    .append(escape(String.join(";", mapTree.pathsByFile().getOrDefault(
                            issue.file().toAbsolutePath().normalize(), List.of())))).append("\">")
                    .append(cell(index++, "center"))
                    .append(fileCell(issue.file().getFileName().toString()))
                    .append(breadcrumbCell(issue.parentTitles(), issue.title())).append(cell(issue.line(), "center"))
                    .append(ruleTypeCell(issue.ruleType())).append(severityCell(issue.severity()))
                    .append(categoryCell(issue.category()))
                    .append(cell(issue.location(), "location-cell"))
                    .append(cell(issue.detectedContent(), "detected-content"))
                    .append(cell(issue.message(), "report-message"))
                    .append("<td class=\"memo-cell\"><div class=\"memo-wrap\"><input class=\"memo\" type=\"text\" size=\"1\" maxlength=\"500\" placeholder=\"작업 메모\" aria-label=\"수정 작업 메모\"></div></td>")
                    .append("<td class=\"center\"><input class=\"done\" type=\"checkbox\" aria-label=\"확인 완료\"></td>")
                    .append("<td class=\"center\"><input class=\"false-positive\" type=\"checkbox\" aria-label=\"False 항목\"></td>")
                    .append("<td class=\"confirmer center\"></td>")
                    .append("<td class=\"updated center\"></td></tr>\n");
        }
        // 보고서만 전달해도 검사 대상, 완료 시각, 실행 사용자를 식별할 수 있게 상단 요약에 넣는다.
        String finished = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(result.finishedAt());
        String displayUser = displayUser(userName, userId);
        String userSummary = displayUser.isBlank()
                ? ""
                : "<span>QA 테스터: <b>" + escape(displayUser) + "</b></span>";
        SchematronReportModel reportModel = new SchematronReportModel(
                escape(result.mapFile().getFileName().toString()),
                escape(downloadFileName(result, output)),
                historyId == null ? "" : historyId.toString(),
                escape(result.mapFile().toAbsolutePath().normalize().toString()),
                Integer.toString(result.validatedFileCount()),
                Integer.toString(result.issues().size()),
                categorySummary(result),
                severitySummary(result),
                escape(finished),
                userSummary,
                mapTree.json(),
                rows.toString());
        String html = new SchematronReportRenderer().render(reportModel);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }

    private static String downloadFileName(ValidationResult result, Path output) {
        String outputName = output.getFileName().toString();
        return outputName.matches("\\d{6}_\\d{4}_(?:.+_SchematronReport|.+_report|report)\\.html")
                ? outputName : reportFileName(result);
    }

    private static String displayUser(String userName, String userId) {
        String name = userName == null ? "" : userName.trim();
        String id = userId == null ? "" : userId.trim();
        return name.isBlank() ? id : name;
    }

    private static String cell(Object value, String cls) {
        return "<td class=\"" + cls + "\">" + escape(String.valueOf(value)) + "</td>";
    }

    private static String fileCell(String fileName) {
        return "<td class=\"center\"><button type=\"button\" class=\"file-filter\" data-file=\""
                + escape(fileName) + "\" title=\"이 파일의 오류만 보기\">" + escape(fileName) + "</button></td>";
    }

    private static String severityCell(String severity) {
        String code = severity == null || severity.isBlank() ? "none" : severity.trim().toLowerCase();
        String label = switch (code) {
            case "critical" -> "Critical";
            case "major" -> "Major";
            case "minor" -> "Minor";
            case "preference" -> "Preference";
            case "error" -> "Error";
            case "none" -> "none";
            default -> code;
        };
        return "<td class=\"severity severity-" + escape(code) + "\" data-severity=\""
                + escape(code) + "\"><span class=\"severity-badge\">" + escape(label) + "</span></td>";
    }

    private static String ruleTypeCell(String ruleType) {
        String code = ruleType == null ? "" : ruleType.trim().toUpperCase(Locale.ROOT);
        String label = switch (code) {
            case "PUB" -> "퍼블리싱";
            case "DOCS" -> "원고";
            default -> "-";
        };
        return cell(label, "center");
    }

    private static String categoryCell(String category) {
        String code = category == null || category.isBlank() ? "uncategorized" : category.trim().toLowerCase();
        return "<td class=\"category center\" data-category=\"" + escape(code) + "\"><span class=\"category-badge\">"
                + escape(categoryLabel(code)) + "</span></td>";
    }

    private static String categoryLabel(String code) {
        return switch (code) {
            case "image" -> "이미지";
            case "xref" -> "상호참조";
            case "table" -> "표";
            case "ditamap" -> "DITAMAP";
            case "structure" -> "구조";
            case "whitespace" -> "공백";
            case "element" -> "태그";
            case "language" -> "언어";
            case "unit" -> "숫자&단위";
            case "index" -> "색인";
            case "system" -> "시스템";
            case "uncategorized" -> "미분류";
            default -> code;
        };
    }

    private static String categorySummary(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] order = {"image", "xref", "ditamap", "structure", "whitespace", "element",
                "language", "unit", "index", "table", "system", "uncategorized"};
        for (String code : order) counts.put(code, 0);
        for (ValidationIssue issue : result.issues()) {
            String code = issue.category() == null || issue.category().isBlank()
                    ? "uncategorized" : issue.category().trim().toLowerCase();
            counts.merge(code, 1, Integer::sum);
        }
        StringBuilder summary = new StringBuilder("<span class=\"category-summary-label\">유형별</span>")
                .append("<button type=\"button\" class=\"category-chip category-chip-total\">전체 <b>")
                .append(result.issues().size()).append("</b>건</button>");
        counts.forEach((code, count) -> {
            if (count > 0) {
                summary.append("<button type=\"button\" class=\"category-chip\" data-category=\"")
                        .append(escape(code)).append("\">")
                        .append(escape(categoryLabel(code))).append(" <b>")
                        .append(count).append("</b></button>");
            }
        });
        return summary.toString();
    }

    private static String severitySummary(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : List.of("critical", "major", "minor", "preference", "none")) counts.put(code, 0);
        for (ValidationIssue issue : result.issues()) {
            String code = issue.severity() == null || issue.severity().isBlank()
                    ? "none" : issue.severity().trim().toLowerCase();
            counts.merge(code, 1, Integer::sum);
        }
        StringBuilder summary = new StringBuilder("<span class=\"category-summary-label\">심각도별</span>")
                .append("<button type=\"button\" class=\"category-chip category-chip-total\">전체 <b>")
                .append(result.issues().size()).append("</b>건</button>");
        counts.forEach((code, count) -> {
            if (count > 0) {
                String label = "none".equals(code) ? "none"
                        : Character.toUpperCase(code.charAt(0)) + code.substring(1);
                summary.append("<button type=\"button\" class=\"category-chip\" data-severity=\"")
                        .append(escape(code)).append("\">").append(escape(label)).append(" <b>")
                        .append(count).append("</b></button>");
            }
        });
        return summary.append("<button type=\"button\" class=\"category-chip severity-chip-false\" data-severity=\"false\">False <b>0</b>건</button>").toString();
    }

    private static String breadcrumbCell(List<String> parentTitles, String currentTitle) {
        String current = currentTitle == null || currentTitle.isBlank() ? "(제목 없음)" : currentTitle.trim();
        List<String> parents = parentTitles == null ? List.of() : parentTitles.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        String fullBreadcrumb = String.join(" › ", parents.isEmpty()
                ? List.of(current)
                : java.util.stream.Stream.concat(parents.stream(), java.util.stream.Stream.of(current)).toList());
        StringBuilder html = new StringBuilder("<td class=\"report-title\" data-search-title=\"")
                .append(escape(fullBreadcrumb)).append("\" title=\"")
                .append(escape(fullBreadcrumb)).append("\"><span class=\"breadcrumb\" dir=\"auto\">");
        for (String parent : parents) {
            html.append("<span class=\"breadcrumb-parent\" title=\"").append(escape(parent))
                    .append("\">").append(escape(truncateCharacters(parent, 25))).append("</span>")
                    .append("<span class=\"breadcrumb-separator\" aria-hidden=\"true\">›</span>");
        }
        return html.append("<span class=\"breadcrumb-current\">").append(escape(current))
                .append("</span></span></td>").toString();
    }

    private static String truncateCharacters(String value, int maximumCharacters) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int end = 0;
        for (int count = 0; count < maximumCharacters; count++) {
            int next = iterator.next();
            if (next == BreakIterator.DONE) return value;
            end = next;
        }
        return iterator.next() == BreakIterator.DONE ? value : value.substring(0, end).stripTrailing() + "…";
    }

    private static MapTreeData mapTreeData(Path mapFile) throws Exception {
        MapModel model = new MapParser().parse(mapFile);
        Map<Path, List<String>> pathsByFile = new LinkedHashMap<>();
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < model.roots().size(); index++) {
            if (index > 0) json.append(',');
            appendMapNode(model.roots().get(index), List.of(), pathsByFile, json);
        }
        return new MapTreeData(Map.copyOf(pathsByFile), json.append(']').toString());
    }

    private static void appendMapNode(MapNode node, List<String> parentPath,
                                      Map<Path, List<String>> pathsByFile, StringBuilder json) {
        List<String> nodePath = new ArrayList<>(parentPath);
        nodePath.add(node.id());
        Path target = node.resolvedTarget();
        if (target != null) {
            Path normalized = target.toAbsolutePath().normalize();
            List<String> paths = new ArrayList<>(pathsByFile.getOrDefault(normalized, List.of()));
            paths.add(String.join("/", nodePath));
            pathsByFile.put(normalized, List.copyOf(paths));
        }
        String title = node.title() == null || node.title().isBlank() ? "(제목 없음)" : node.title().trim();
        String fileName = target == null ? "" : target.getFileName().toString();
        json.append("{\"id\":").append(jsonString(node.id()))
                .append(",\"title\":").append(jsonString(title))
                .append(",\"fileName\":").append(jsonString(fileName))
                .append(",\"children\":[");
        for (int index = 0; index < node.children().size(); index++) {
            if (index > 0) json.append(',');
            appendMapNode(node.children().get(index), nodePath, pathsByFile, json);
        }
        json.append("]}");
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '<' -> result.append("\\u003c");
                case '>' -> result.append("\\u003e");
                case '&' -> result.append("\\u0026");
                case '\u2028' -> result.append("\\u2028");
                case '\u2029' -> result.append("\\u2029");
                default -> {
                    if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }

    private record MapTreeData(Map<Path, List<String>> pathsByFile, String json) { }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String issueId(Path mapFile, ValidationIssue issue) throws Exception {
        Path base = mapFile.toAbsolutePath().getParent();
        Path file = issue.file().toAbsolutePath().normalize();
        String relative;
        try { relative = base.relativize(file).toString(); }
        catch (IllegalArgumentException ex) { relative = file.toString(); }
        String source = relative.replace('\\', '/') + "|" + issue.ruleId() + "|" + issue.location() + "|" + issue.message();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i]));
        return hex.toString();
    }
}
