package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class PdfCheckScanViewerService {

    private static final String BUNDLED_EXE =
            "classpath:tools/PdfCheckScanViewer-0.1.0.exe";

    private final Path configuredExePath;
    private final Resource bundledExe;

    @Autowired
    public PdfCheckScanViewerService(
            @Value("${pdf-check-scan-viewer.exe-path:}") String exePath,
            org.springframework.core.io.ResourceLoader resourceLoader) {
        this(exePath, resourceLoader.getResource(BUNDLED_EXE));
    }

    PdfCheckScanViewerService(
            String exePath,
            Resource bundledExe) {
        this.configuredExePath = exePath == null || exePath.isBlank()
                ? null
                : Path.of(exePath.trim()).toAbsolutePath().normalize();
        this.bundledExe = bundledExe;
    }

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
        if(configuredExePath != null){
            return configuredExePath;
        }

        try{
            if(!bundledExe.exists()){
                throw new IllegalArgumentException(
                        "프로젝트에 포함된 PDF 검수 스캔 뷰어 EXE 파일을 찾을 수 없습니다.");
            }

            Path toolDirectory = Path.of(
                    System.getProperty("user.home"),
                    ".maoomtool",
                    "tools")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(toolDirectory);

            Path extractedExe = toolDirectory.resolve(
                    "PdfCheckScanViewer-0.1.0.exe");
            Files.copy(
                    bundledExe.getInputStream(),
                    extractedExe,
                    StandardCopyOption.REPLACE_EXISTING);
            return extractedExe;
        }catch(IOException exception){
            throw new IllegalStateException(
                    "PDF 검수 스캔 뷰어 EXE 준비에 실패했습니다: " + exception.getMessage(),
                    exception);
        }
    }

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
