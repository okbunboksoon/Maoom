package maoomWeb.ire.user.service;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 컬러체크 산출물이 저장될 서버 PC의 실제 폴더를 한곳에서 계산한다.
 */
@Service
public class ColorCheckOutputPathService {

    private final String configuredOutputDirectory;

    public ColorCheckOutputPathService(
            @Value("${color-check.output-dir:}")
            String configuredOutputDirectory) {
        this.configuredOutputDirectory = configuredOutputDirectory;
    }

    /**
     * 설정값이 있으면 그 경로를, 없으면 Java 실행 계정의 Desktop/temp를 사용한다.
     */
    public Path getOutputDirectory() {
        if(StringUtils.hasText(configuredOutputDirectory)){
            return Path.of(configuredOutputDirectory.trim())
                    .toAbsolutePath()
                    .normalize();
        }

        return Path.of(
                System.getProperty("user.home"),
                "Desktop",
                "temp")
                .toAbsolutePath()
                .normalize();
    }
}
