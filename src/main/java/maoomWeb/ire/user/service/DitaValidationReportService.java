package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import maoomWeb.ire.user.config.DitaValidationProperties;

/** GOMS 리포트 목록 조회와 안전한 리포트 파일 경로 해석을 담당한다. */
@Service
public class DitaValidationReportService {
    private final SchematronHistoryService historyService;
    private final Path gomsOutputRoot;

    @Autowired
    public DitaValidationReportService(SchematronHistoryService historyService,
            DitaValidationProperties properties) {
        this(historyService, Path.of(properties.getGomsOutputRoot()));
    }

    DitaValidationReportService(SchematronHistoryService historyService, Path gomsOutputRoot) {
        this.historyService = historyService;
        this.gomsOutputRoot = gomsOutputRoot.toAbsolutePath().normalize();
    }

    public List<GomsReportYear> findGomsReports() throws Exception {
        if (!Files.isDirectory(gomsOutputRoot)) {
            throw new IllegalStateException("GOMS 결과 폴더를 찾을 수 없습니다: " + gomsOutputRoot);
        }
        Map<String, GomsReportSummary> summaries = historyService.findSuccessfulReportSummaries();
        List<GomsReportYear> result = new ArrayList<>();
        for (Path year : directories(gomsOutputRoot, Comparator.reverseOrder())) {
            String yearName = year.getFileName().toString();
            if (!yearName.matches("\\d{4}")) continue;
            List<GomsReportProject> projects = new ArrayList<>();
            for (Path project : directories(year, String.CASE_INSENSITIVE_ORDER)) {
                List<GomsReportLanguage> languages = new ArrayList<>();
                for (Path language : directories(project, String.CASE_INSENSITIVE_ORDER)) {
                    Path reportFolder = language.resolve("report");
                    if (!Files.isDirectory(reportFolder)) continue;
                    List<GomsReportFile> reports = reportFiles(
                            yearName, project.getFileName().toString(),
                            language.getFileName().toString(), reportFolder, summaries);
                    languages.add(new GomsReportLanguage(
                            language.getFileName().toString(),
                            yearName + "/" + project.getFileName() + "/" + language.getFileName(),
                            reports));
                }
                if (!languages.isEmpty()) {
                    projects.add(new GomsReportProject(project.getFileName().toString(), languages));
                }
            }
            if (!projects.isEmpty()) result.add(new GomsReportYear(yearName, projects));
        }
        return result;
    }

    public Path resolveGomsReport(String yearName, String projectName,
            String languageName, String fileName) {
        requireSinglePathName(yearName, "연도 폴더명");
        requireSinglePathName(projectName, "프로젝트 폴더명");
        requireSinglePathName(languageName, "언어 폴더명");
        requireSinglePathName(fileName, "리포트 파일명");
        if (!yearName.matches("\\d{4}")) throw new IllegalArgumentException("올바르지 않은 연도 폴더명입니다.");
        if (!fileName.toLowerCase().endsWith(".html")) throw new IllegalArgumentException("HTML 리포트만 열 수 있습니다.");
        Path language = gomsOutputRoot.resolve(yearName).resolve(projectName)
                .resolve(languageName).normalize();
        Path reportFolder = language.resolve("report").normalize();
        Path report = reportFolder.resolve(fileName).normalize();
        if (!language.startsWith(gomsOutputRoot) || !report.getParent().equals(reportFolder)
                || !Files.isRegularFile(report)) {
            throw new IllegalArgumentException("리포트 파일을 찾을 수 없습니다.");
        }
        return report;
    }

    private List<Path> directories(Path root, Comparator<String> nameComparator) throws Exception {
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), nameComparator))
                    .toList();
        }
    }

    private List<GomsReportFile> reportFiles(String year, String project, String language,
            Path reportFolder, Map<String, GomsReportSummary> summaries) throws Exception {
        List<GomsReportFile> reports = new ArrayList<>();
        try (var paths = Files.list(reportFolder)) {
            for (Path report : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER).reversed()).toList()) {
                GomsReportSummary summary = summaries.get(SchematronHistoryService.normalizedPathKey(report));
                Long historyId = summary == null ? null : summary.historyId();
                String fileName = report.getFileName().toString();
                String url = historyId == null
                        ? "/api/dita-validation/goms/reports/file?year=" + encode(year)
                                + "&project=" + encode(project) + "&language=" + encode(language)
                                + "&file=" + encode(fileName)
                        : "/api/dita-validation/reports/" + historyId;
                reports.add(new GomsReportFile(fileName, historyId, historyId != null, url,
                        Files.getLastModifiedTime(report).toMillis(),
                        summary == null ? null : summary.validatedFileCount(),
                        summary == null ? null : summary.issueCount(),
                        summary == null ? null : summary.finishedAt(),
                        summary == null ? null : summary.testerName(),
                        summary == null ? null : summary.appliedCount(),
                        summary == null ? null : summary.scopeType()));
            }
        }
        return reports;
    }

    private void requireSinglePathName(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "이 없습니다.");
        Path path = Path.of(value);
        if (path.isAbsolute() || path.getNameCount() != 1 || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("올바르지 않은 " + label + "입니다.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
