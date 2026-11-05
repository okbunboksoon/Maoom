package maoomWeb.ire.user.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.sts.dita.core.ValidationResult;
import com.sts.dita.report.HtmlReportWriter;
import maoomWeb.ire.user.config.DitaValidationProperties;

/** 규칙 스냅샷과 검사 결과 HTML의 저장 위치 및 파일 생성을 담당한다. */
@Service
public class DitaValidationReportWriterService {
    private final SchematronFileService schematronFileService;
    private final Path reportRoot;

    @Autowired
    public DitaValidationReportWriterService(SchematronFileService schematronFileService,
            DitaValidationProperties properties) {
        this(schematronFileService, Path.of(properties.getReportDir()));
    }

    DitaValidationReportWriterService(SchematronFileService schematronFileService, Path reportRoot) {
        this.schematronFileService = schematronFileService;
        this.reportRoot = reportRoot.toAbsolutePath().normalize();
    }

    Path historyFolder(long historyId) {
        LocalDate today = LocalDate.now();
        return reportRoot.resolve(Integer.toString(today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()))
                .resolve(Long.toString(historyId));
    }

    Path snapshot(Path schema, String expectedHash, Path historyFolder) throws Exception {
        Path snapshot = schematronFileService.copySnapshot(schema, historyFolder);
        if (!expectedHash.equals(schematronFileService.sha256(snapshot))) {
            throw new IllegalStateException("Schematron 파일이 실행 준비 중 변경되었습니다. 다시 실행해 주세요.");
        }
        return snapshot;
    }

    Path write(ValidationResult result, Path sourceRoot, boolean gomsExecution,
            Path historyFolder, Long historyId, String userName, String userId) throws Exception {
        Path folder = gomsExecution ? sourceRoot.resolve("report") : historyFolder;
        Files.createDirectories(folder);
        Path report = folder.resolve(gomsExecution
                ? HtmlReportWriter.gomsReportFileName(result)
                : HtmlReportWriter.reportFileName(result));
        new HtmlReportWriter().write(result, report, historyId, userName, userId);
        return report;
    }
}
