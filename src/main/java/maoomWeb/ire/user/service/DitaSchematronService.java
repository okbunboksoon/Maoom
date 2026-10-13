package maoomWeb.ire.user.service;

import com.sts.dita.core.MapModel;
import com.sts.dita.core.ValidationEngine;
import com.sts.dita.core.ValidationRequest;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.ValidationProgressListener;
import com.sts.dita.report.HtmlReportWriter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * STS 웹 기능에서 DITA Schematron 검사 코어를 호출하는 진입점입니다.
 * 웹 워크플로가 검증한 서버 파일 경로와 진행 콜백을 코어 엔진에 전달합니다.
 */
@Service
public class DitaSchematronService {
    private final ValidationEngine validationEngine;
    private final HtmlReportWriter reportWriter;

    public DitaSchematronService() {
        this(new ValidationEngine(), new HtmlReportWriter());
    }

    DitaSchematronService(ValidationEngine validationEngine, HtmlReportWriter reportWriter) {
        this.validationEngine = validationEngine;
        this.reportWriter = reportWriter;
    }

    public MapModel loadMap(Path mapFile) throws Exception {
        return validationEngine.loadMap(mapFile);
    }

    public ValidationResult validate(ValidationRequest request) throws Exception {
        return validationEngine.validate(request);
    }

    public ValidationResult validate(ValidationRequest request, ValidationProgressListener progress) throws Exception {
        return validationEngine.validate(request, progress);
    }

    public ValidationResult validateAndWriteReport(ValidationRequest request, Path reportFile) throws Exception {
        ValidationResult result = validate(request);
        reportWriter.write(result, reportFile);
        return result;
    }
}
