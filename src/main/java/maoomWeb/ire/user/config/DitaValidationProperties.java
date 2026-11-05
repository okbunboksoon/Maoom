package maoomWeb.ire.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.nio.file.Path;

/**
 * DITA Validation에서 사용하는 경로·제한시간·비동기 실행 설정을 한곳에서 관리한다.
 */
@ConfigurationProperties(prefix = "dita.validation")
public class DitaValidationProperties implements InitializingBean {
    private String schematronDir = "./schematron";
    private String reportDir = "./dita-validation-reports";
    private String stagingRoot = System.getProperty("java.io.tmpdir") + "/maoomtool-dita-validation";
    private long timeoutMinutes = 30;
    private long fileTimeoutSeconds = 120;
    private int asyncThreads = 2;
    private int asyncQueueCapacity = 20;
    private long progressRetentionMinutes = 60;
    private String allowedUsers = "admin,hkim";
    private boolean executionModeVisible;
    private String gomsExportRoot = "C:/MaoomTool/Tools/EXPORT_260626";
    private String gomsOutputRoot = "V:/ValidationCheck";
    private long gomsTimeoutMinutes = 30;
    private long stagingRetentionHours = 24;

    public String getSchematronDir() { return schematronDir; }
    public void setSchematronDir(String value) { this.schematronDir = value; }
    public String getReportDir() { return reportDir; }
    public void setReportDir(String value) { this.reportDir = value; }
    public String getStagingRoot() { return stagingRoot; }
    public void setStagingRoot(String value) { this.stagingRoot = value; }
    public long getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(long value) { this.timeoutMinutes = value; }
    public long getFileTimeoutSeconds() { return fileTimeoutSeconds; }
    public void setFileTimeoutSeconds(long value) { this.fileTimeoutSeconds = value; }
    public int getAsyncThreads() { return asyncThreads; }
    public void setAsyncThreads(int value) { this.asyncThreads = value; }
    public int getAsyncQueueCapacity() { return asyncQueueCapacity; }
    public void setAsyncQueueCapacity(int value) { this.asyncQueueCapacity = value; }
    public long getProgressRetentionMinutes() { return progressRetentionMinutes; }
    public void setProgressRetentionMinutes(long value) { this.progressRetentionMinutes = value; }
    public String getAllowedUsers() { return allowedUsers; }
    public void setAllowedUsers(String value) { this.allowedUsers = value; }
    public boolean isExecutionModeVisible() { return executionModeVisible; }
    public void setExecutionModeVisible(boolean value) { this.executionModeVisible = value; }
    public String getGomsExportRoot() { return gomsExportRoot; }
    public void setGomsExportRoot(String value) { this.gomsExportRoot = value; }
    public String getGomsOutputRoot() { return gomsOutputRoot; }
    public void setGomsOutputRoot(String value) { this.gomsOutputRoot = value; }
    public long getGomsTimeoutMinutes() { return gomsTimeoutMinutes; }
    public void setGomsTimeoutMinutes(long value) { this.gomsTimeoutMinutes = value; }
    public long getStagingRetentionHours() { return stagingRetentionHours; }
    public void setStagingRetentionHours(long value) { this.stagingRetentionHours = value; }

    /**
     * 배포 설정 오류를 실제 검사 시점이 아니라 애플리케이션 시작 시점에 발견한다.
     * GOMS 원본 폴더의 존재 여부는 해당 기능 실행 시 검사해 일반 기능의 시작을 막지 않는다.
     */
    @Override
    public void afterPropertiesSet() {
        requireText(schematronDir, "schematron-dir");
        requireText(reportDir, "report-dir");
        requireText(stagingRoot, "staging-root");
        requireText(gomsOutputRoot, "goms-output-root");
        requirePositive(timeoutMinutes, "timeout-minutes");
        requirePositive(fileTimeoutSeconds, "file-timeout-seconds");
        requirePositive(asyncThreads, "async-threads");
        requirePositive(asyncQueueCapacity, "async-queue-capacity");
        requirePositive(progressRetentionMinutes, "progress-retention-minutes");
        requirePositive(gomsTimeoutMinutes, "goms-timeout-minutes");
        requirePositive(stagingRetentionHours, "staging-retention-hours");
        Path staging = Path.of(stagingRoot).toAbsolutePath().normalize();
        Path goms = Path.of(gomsOutputRoot).toAbsolutePath().normalize();
        if (staging.startsWith(goms)) {
            throw new IllegalStateException("dita.validation.staging-root는 GOMS 결과 경로 내부일 수 없습니다.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("dita.validation." + name + " 설정이 비어 있습니다.");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) {
            throw new IllegalStateException("dita.validation." + name + " 설정은 1 이상이어야 합니다.");
        }
    }
}
