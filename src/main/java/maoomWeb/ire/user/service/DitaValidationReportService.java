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

    public List<GomsReportProject> findGomsReports() throws Exception {
        if (!Files.isDirectory(gomsOutputRoot)) {
            throw new IllegalStateException("GOMS 결과 폴더를 찾을 수 없습니다: " + gomsOutputRoot);
        }
        Map<String, GomsReportSummary> summaries = historyService.findSuccessfulReportSummaries();
        List<Path> projects;
        try (var paths = Files.list(gomsOutputRoot)) {
            projects = paths.filter(Files::isDirectory)
                    .filter(path -> !"old".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        List<GomsReportProject> result = new ArrayList<>();
        for (Path project : projects) {
            List<GomsReportFile> reports = new ArrayList<>();
            Path reportFolder = project.resolve("report");
            if (Files.isDirectory(reportFolder)) {
                try (var paths = Files.list(reportFolder)) {
                    for (Path report : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                            .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER).reversed()).toList()) {
                        GomsReportSummary summary = summaries.get(SchematronHistoryService.normalizedPathKey(report));
                        Long historyId = summary == null ? null : summary.historyId();
                        String fileName = report.getFileName().toString();
                        String url = historyId == null
                                ? "/api/dita-validation/goms/reports/file?project="
                                        + encode(project.getFileName().toString()) + "&file=" + encode(fileName)
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
            }
            result.add(new GomsReportProject(project.getFileName().toString(), reports));
        }
        return result;
    }

    public Path resolveGomsReport(String projectName, String fileName) {
        requireSinglePathName(projectName, "프로젝트 폴더명");
        requireSinglePathName(fileName, "리포트 파일명");
        if (!fileName.toLowerCase().endsWith(".html")) throw new IllegalArgumentException("HTML 리포트만 열 수 있습니다.");
        Path project = gomsOutputRoot.resolve(projectName).normalize();
        Path reportFolder = project.resolve("report").normalize();
        Path report = reportFolder.resolve(fileName).normalize();
        if (!project.startsWith(gomsOutputRoot) || "old".equalsIgnoreCase(project.getFileName().toString())
                || !report.getParent().equals(reportFolder) || !Files.isRegularFile(report)) {
            throw new IllegalArgumentException("리포트 파일을 찾을 수 없습니다.");
        }
        return report;
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
