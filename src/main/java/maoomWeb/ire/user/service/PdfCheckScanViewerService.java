package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.PdfCheckScanViewerResult;

/**
 * 인쇄데이터 검증 CLI EXE 실행 서비스.
 *
 * <p>대상 폴더와 매치테이블 경로를 CLI EXE에 전달하고, 결과 저장 경로 아래에
 * 날짜가 포함된 결과 xlsx를 생성한다.</p>
 */
@Service
public class PdfCheckScanViewerService {

    private static final DateTimeFormatter OUTPUT_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(60);

    private final Path configuredExePath;
    private final Path configuredMatchTablePath;

    public PdfCheckScanViewerService(
            @Value("${pdf-check-scan-viewer.exe-path}") String exePath,
            @Value("${pdf-check-scan-viewer.match-table-path}") String matchTablePath) {
        this.configuredExePath = normalizeExecutablePath(exePath);
        this.configuredMatchTablePath = normalizeOptionalPath(matchTablePath);
    }

    private Path normalizeExecutablePath(String exePath) {
        // 설정값이 비어 있으면 실행 단계에서 사용자에게 명확한 오류를 보여준다.
        return normalizeOptionalPath(exePath);
    }

    private Path normalizeOptionalPath(String rawPath) {
        return rawPath == null || rawPath.isBlank()
                ? null
                : Path.of(rawPath.trim()).toAbsolutePath().normalize();
    }

    /** 입력 경로를 검증한 뒤 CLI EXE를 실행하고 생성된 결과 xlsx 경로를 반환한다. */
    public PdfCheckScanViewerResult run(
            String targetDirectory,
            String outputDirectory) {
        RunContext context = prepareRun(
                targetDirectory,
                outputDirectory);

        String processOutput = executeCli(context.exePath(), context.command());
        validateRegularFile(
                context.outputFilePath(),
                "결과 xlsx 파일이 생성되지 않았습니다");

        return new PdfCheckScanViewerResult(
                "인쇄데이터 검증이 완료되었습니다.",
                context.outputFilePath().toString(),
                summarizeProcessOutput(processOutput));
    }

    private String summarizeProcessOutput(String processOutput) {
        if(processOutput == null || processOutput.isBlank()){
            return "";
        }

        List<String> summaryLines = processOutput.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("스캔:")
                        || line.startsWith("통짜 PDF")
                        || line.startsWith("저장:"))
                .toList();
        if(!summaryLines.isEmpty()){
            return String.join(System.lineSeparator(), summaryLines);
        }

        List<String> lines = processOutput.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
        int fromIndex = Math.max(0, lines.size() - 5);
        return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
    }

    private RunContext prepareRun(
            String targetDirectory,
            String outputDirectory) {
        Path exePath = resolveExecutable();
        validateExecutable(exePath);
        Path matchTablePath = resolveMatchTable();
        validateMatchTable(matchTablePath);

        Path targetPath = normalizeRequiredPath(
                targetDirectory,
                "대상 폴더 경로를 입력해 주세요.");
        Path outputDirectoryPath = normalizeRequiredPath(
                outputDirectory,
                "결과 저장 경로를 입력해 주세요.");
        Path outputFilePath = outputDirectoryPath
                .resolve(createOutputFileName(LocalDateTime.now()))
                .normalize();

        validateDirectory(targetPath, "대상 폴더를 찾을 수 없습니다");
        ensureOutputDirectory(outputDirectoryPath);

        List<String> command = new java.util.ArrayList<>(List.of(
                exePath.toString(),
                targetPath.toString(),
                "--xlsx",
                matchTablePath.toString(),
                "--out",
                outputFilePath.toString(),
                "--quiet"));

        return new RunContext(exePath, outputFilePath, command);
    }

    String createOutputFileName(LocalDateTime now) {
        return OUTPUT_FILE_TIMESTAMP.format(now) + "_인쇄데이터_검증.xlsx";
    }

    private Path normalizeRequiredPath(
            String rawPath,
            String message) {
        return Path.of(requireText(rawPath, message))
                .toAbsolutePath()
                .normalize();
    }

    private String requireText(String value, String message) {
        if(value == null || value.isBlank()){
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String executeCli(Path exePath, List<String> command) {
        Process process = null;
        Path logFile = null;
        try{
            logFile = Files.createTempFile(
                    "pdf-print-check-",
                    ".log");
            process = new ProcessBuilder(command)
                    .directory(exePath.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean finished = process.waitFor(
                    PROCESS_TIMEOUT.toMinutes(),
                    TimeUnit.MINUTES);
            if(!finished){
                process.destroyForcibly();
                throw new IllegalStateException(
                        "인쇄데이터 검증 시간이 초과되었습니다.");
            }

            String processOutput = Files.readString(logFile, consoleCharset());
            if(process.exitValue() != 0){
                throw new IllegalStateException(
                        "인쇄데이터 검증 실행에 실패했습니다. "
                                + processOutput.strip());
            }
            return processOutput.strip();
        }catch(IOException exception){
            throw new IllegalStateException(
                    "인쇄데이터 검증 실행에 실패했습니다: "
                            + exception.getMessage(),
                    exception);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "인쇄데이터 검증 실행이 중단되었습니다.",
                    exception);
        }finally{
            if(process != null && process.isAlive()){
                process.destroyForcibly();
            }
            if(logFile != null){
                try{
                    Files.deleteIfExists(logFile);
                }catch(IOException ignored){
                    // 임시 로그 삭제 실패는 검증 결과에 영향을 주지 않는다.
                }
            }
        }
    }

    private Charset consoleCharset() {
        try{
            return Charset.forName(System.getProperty("sun.stdout.encoding"));
        }catch(Exception exception){
            return StandardCharsets.UTF_8;
        }
    }

    private Path resolveExecutable() {
        return configuredExePath;
    }

    private Path resolveMatchTable() {
        return configuredMatchTablePath;
    }

    /** 실행 전 필수 조건을 확인해 잘못된 설정을 즉시 알 수 있게 한다. */
    private void validateExecutable(Path exePath) {
        if(exePath == null){
            throw new IllegalArgumentException(
                    "인쇄데이터 검증 CLI EXE 경로가 설정되지 않았습니다.");
        }

        validateRegularFile(exePath, "인쇄데이터 검증 CLI EXE 파일을 찾을 수 없습니다");

        String fileName = exePath.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if(!fileName.endsWith(".exe")){
            throw new IllegalArgumentException(
                    "인쇄데이터 검증 CLI 실행 파일은 .exe 파일이어야 합니다: "
                            + exePath);
        }
    }

    private void validateMatchTable(Path matchTablePath) {
        if(matchTablePath == null){
            throw new IllegalArgumentException(
                    "매치테이블 경로가 설정되지 않았습니다.");
        }
        validateRegularFile(matchTablePath, "매치테이블 파일을 찾을 수 없습니다");
    }

    private void validateDirectory(Path path, String message) {
        if(!Files.isDirectory(path)){
            throw new IllegalArgumentException(message + ": " + path);
        }
    }

    private void validateRegularFile(Path path, String message) {
        if(!Files.isRegularFile(path)){
            throw new IllegalArgumentException(message + ": " + path);
        }
    }

    private void ensureOutputDirectory(Path outputDirectoryPath) {
        try{
            Files.createDirectories(outputDirectoryPath);
        }catch(IOException exception){
            throw new IllegalArgumentException(
                    "결과 저장 경로를 만들 수 없습니다: " + outputDirectoryPath,
                    exception);
        }
    }

    private record RunContext(
            Path exePath,
            Path outputFilePath,
            List<String> command) {
    }
}
