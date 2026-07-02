package maoomWeb.ire.config;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@Configuration
/** Google Drive API 클라이언트를 애플리케이션 전역에서 공유한다. */
public class GoogleDriveConfig {

    private static final Logger log =
            LoggerFactory.getLogger(GoogleDriveConfig.class);

    @Bean
    public Drive googleDrive(
            @Value("${app.google-drive.credentials-path}")
            String credentialsPath) throws Exception {

        GoogleCredentials credentials = loadCredentials(
                credentialsPath);
        HttpRequestInitializer requestInitializer = null;

        if(credentials != null){
            credentials = credentials.createScoped(
                    List.of(DriveScopes.DRIVE_READONLY));
            requestInitializer =
                    new HttpCredentialsAdapter(credentials);
        }

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("MaoomTool")
                .build();
    }

    private GoogleCredentials loadCredentials(
            String credentialsPath) {

        try{
            if(credentialsPath == null
                    || credentialsPath.isBlank()){
                return GoogleCredentials
                        .getApplicationDefault();
            }

            Path path = Path.of(credentialsPath);

            if(!Files.isRegularFile(path)){
                log.warn(
                        "Google Drive credentials file not found: {}. "
                        + "The application will start without "
                        + "service-account Drive access.",
                        path);
                return null;
            }

            try(InputStream inputStream =
                    Files.newInputStream(path)){
                return GoogleCredentials.fromStream(inputStream);
            }
        }catch(IOException exception){
            log.warn(
                    "Google Drive credentials could not be loaded. "
                    + "The application will start without "
                    + "service-account Drive access: {}",
                    exception.getMessage());
            return null;
        }
    }
}
