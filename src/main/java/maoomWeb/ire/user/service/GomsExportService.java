package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.GomsExportResponse;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import maoomWeb.ire.user.config.DitaValidationProperties;

/** GOMS EXPORT 설정 변경과 배치 실행을 직렬화하고 생성된 ValidationCheck 경로를 반환한다. */
@Service
public class GomsExportService {
    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "^(?<year>\\d{4})/(?<project>[A-Za-z0-9._-]+)/(?<sub>[A-Za-z0-9._-]+)$");
    private final Path exportRoot;
    private final Path outputRoot;
    private final Duration timeout;
    private final BatchRunner batchRunner;
    private final ReentrantLock exportLock = new ReentrantLock();

    @Autowired
    public GomsExportService(DitaValidationProperties properties) {
        this(Path.of(properties.getGomsExportRoot()), Path.of(properties.getGomsOutputRoot()),
                Duration.ofMinutes(Math.max(1, properties.getGomsTimeoutMinutes())),
                GomsExportService::runBatch);
    }

    GomsExportService(Path exportRoot, Path outputRoot, Duration timeout, BatchRunner batchRunner) {
        this.exportRoot = exportRoot.toAbsolutePath().normalize();
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.batchRunner = batchRunner;
    }

    public GomsExportResponse export(String inputPath) throws Exception {
        ExportInput input = parse(inputPath);
        if (!exportLock.tryLock()) {
            throw new IllegalStateException("다른 GOMS EXPORT가 실행 중입니다. 완료 후 다시 실행해 주세요.");
        }
        try {
            Path properties = exportRoot.resolve("classes").resolve("common.properties");
            Path batch = exportRoot.resolve("run.bat");
            if (!Files.isRegularFile(properties)) {
                throw new IllegalStateException("GOMS 설정 파일을 찾을 수 없습니다: " + properties);
            }
            if (!Files.isRegularFile(batch)) {
                throw new IllegalStateException("GOMS 실행 파일을 찾을 수 없습니다: " + batch);
            }
            updateProperties(properties, input);
            int exitCode = batchRunner.run(batch, exportRoot, timeout);
            if (exitCode != 0) {
                throw new IllegalStateException("GOMS EXPORT 실행에 실패했습니다. 종료 코드: " + exitCode);
            }
            Path generated = outputRoot.resolve(input.project() + "_" + input.sub()).normalize();
            if (!generated.getParent().equals(outputRoot) || !Files.isDirectory(generated)) {
                throw new IllegalStateException("GOMS 결과 폴더를 찾을 수 없습니다: " + generated);
            }
            // DITAMAP 목록은 검사 화면에서 한 번만 조회한다. 여기서 V 서버 전체를 다시 순회하지 않는다.
            return new GomsExportResponse(generated.toString(), input.project(), input.sub());
        } finally {
            exportLock.unlock();
        }
    }

    public boolean isManagedOutput(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(outputRoot) && !normalized.equals(outputRoot);
    }

    private static ExportInput parse(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        normalized = normalized.replaceAll("^/+|/+$", "");
        Matcher matcher = INPUT_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "GOMS 입력 경로는 연도/프로젝트/언어 형식이어야 합니다. 예: 2026/OV1K_EV_27MY/English_LHD");
        }
        return new ExportInput(matcher.group("year"), matcher.group("project"), matcher.group("sub"));
    }

    private static void updateProperties(Path path, ExportInput input) throws Exception {
        byte[] original = Files.readAllBytes(path);
        TextFile textFile = decode(original);
        String updated = replaceSetting(textFile.text(), "EXPORT.TARGET",
                "/Project/" + input.year() + "/" + input.project());
        updated = replaceSetting(updated, "EXPORT.TARGET_SUB", input.sub());
        byte[] content = encode(updated, textFile);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String replaceSetting(String text, String key, String value) {
        Pattern setting = Pattern.compile("(?m)^" + Pattern.quote(key) + "=[^\\r\\n]*$");
        Matcher matcher = setting.matcher(text);
        if (!matcher.find()) throw new IllegalStateException("GOMS 설정 항목이 없습니다: " + key);
        String updated = matcher.replaceFirst(Matcher.quoteReplacement(key + "=" + value));
        if (setting.matcher(updated).results().count() != 1) {
            throw new IllegalStateException("GOMS 설정 항목이 중복되어 있습니다: " + key);
        }
        return updated;
    }

    private static TextFile decode(byte[] bytes) throws CharacterCodingException {
        boolean utf8Bom = bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf;
        byte[] body = utf8Bom ? Arrays.copyOfRange(bytes, 3, bytes.length) : bytes;
        try {
            Charset utf8 = StandardCharsets.UTF_8;
            CharBuffer decoded = utf8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body));
            return new TextFile(decoded.toString(), utf8, utf8Bom);
        } catch (CharacterCodingException exception) {
            Charset ms949 = Charset.forName("MS949");
            return new TextFile(ms949.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(),
                    ms949, false);
        }
    }

    private static byte[] encode(String value, TextFile textFile) {
        byte[] body = value.getBytes(textFile.charset());
        if (!textFile.utf8Bom()) return body;
        ByteArrayOutputStream output = new ByteArrayOutputStream(body.length + 3);
        output.writeBytes(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        output.writeBytes(body);
        return output.toByteArray();
    }

    private static int runBatch(Path batch, Path workingDirectory, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/d", "/c", "call", batch.getFileName().toString());
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new IllegalStateException("GOMS EXPORT 제한 시간을 초과했습니다.");
        }
        return process.exitValue();
    }

    @FunctionalInterface
    interface BatchRunner {
        int run(Path batch, Path workingDirectory, Duration timeout) throws Exception;
    }

    private record ExportInput(String year, String project, String sub) { }
    private record TextFile(String text, Charset charset, boolean utf8Bom) { }
}
