package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 인쇄데이터 검증 카드가 실행하는 외부 EXE 런처 서비스.
 *
 * <p>브라우저에서 직접 로컬 EXE를 실행할 수 없으므로 서버가 설정값
 * {@code pdf-check-scan-viewer.exe-path}에 지정된 파일을 실행한다. 경로 설정은
 * {@code application.properties}에서 관리하며, 운영 서버에서 실제 EXE 위치가
 * 바뀌면 이 설정값만 바꾸면 된다.</p>
 */
@Service
public class PdfCheckScanViewerService {

    private final Path configuredExePath;

    public PdfCheckScanViewerService(
            @Value("${pdf-check-scan-viewer.exe-path}") String exePath) {
        this.configuredExePath = normalizeExecutablePath(exePath);
    }

    private Path normalizeExecutablePath(String exePath) {
        // 설정값이 비어 있으면 launch 단계에서 사용자에게 명확한 오류를 보여준다.
        return exePath == null || exePath.isBlank()
                ? null
                : Path.of(exePath.trim()).toAbsolutePath().normalize();
    }

    /** 설정된 EXE를 별도 프로세스로 실행한다. 실행 후 프로그램의 내부 동작은 EXE가 담당한다. */
    public void launch() {
        Path exePath = resolveExecutable();
        validateExecutable(exePath);

        try{
            new ProcessBuilder(exePath.toString())
                    .directory(exePath.getParent().toFile())
                    .start();
        }catch(IOException exception){
            throw new IllegalStateException(
                    "PDF 검수 스캔 뷰어 실행에 실패했습니다: " + exception.getMessage(),
                    exception);
        }
    }

    private Path resolveExecutable() {
        return configuredExePath;
    }

    /** 실행 전 필수 조건을 확인해 잘못된 설정을 즉시 알 수 있게 한다. */
    private void validateExecutable(Path exePath) {
        if(exePath == null){
            throw new IllegalArgumentException(
                    "PDF 검수 스캔 뷰어 EXE 경로가 설정되지 않았습니다.");
        }

        if(!Files.isRegularFile(exePath)){
            throw new IllegalArgumentException(
                    "PDF 검수 스캔 뷰어 EXE 파일을 찾을 수 없습니다: " + exePath);
        }

        String fileName = exePath.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if(!fileName.endsWith(".exe")){
            throw new IllegalArgumentException(
                    "PDF 검수 스캔 뷰어 실행 파일은 .exe 파일이어야 합니다: " + exePath);
        }
    }
}
