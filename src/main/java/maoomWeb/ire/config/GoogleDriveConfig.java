package maoomWeb.ire.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@Configuration
/** Google Drive API 클라이언트를 애플리케이션 전역에서 공유한다. */
public class GoogleDriveConfig {

    @Bean
    public Drive googleDrive(
            @Value("${app.google-drive.credentials-path}")
            String credentialsPath) throws Exception {

        try(InputStream inputStream =
                Files.newInputStream(Path.of(credentialsPath))){

            GoogleCredentials credentials =
                    GoogleCredentials
                    .fromStream(inputStream)
                    .createScoped(
                            List.of(DriveScopes.DRIVE_READONLY));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("MaoomTool")
                    .build();
        }
    }
}
