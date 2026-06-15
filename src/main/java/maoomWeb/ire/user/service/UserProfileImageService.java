package maoomWeb.ire.user.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.mapper.UserMapper;

@Service
public class UserProfileImageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Map<String,String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private final UserMapper userMapper;
    private final Path uploadRoot;

    public UserProfileImageService(
            UserMapper userMapper,
            @Value("${app.user.profile-upload-dir}") String uploadDir) {
        this.userMapper = userMapper;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public void save(String userId, MultipartFile file) {
        User user = requireUser(userId);

        if(file == null || file.isEmpty()){
            throw new ResponseStatusException(
                    BAD_REQUEST, "프로필 이미지를 선택해주세요.");
        }

        if(file.getSize() > MAX_IMAGE_SIZE){
            throw new ResponseStatusException(
                    BAD_REQUEST, "프로필 이미지는 5MB 이하만 가능합니다.");
        }

        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);

        if(extension == null){
            throw new ResponseStatusException(
                    BAD_REQUEST, "PNG, JPG, GIF, WebP 이미지만 가능합니다.");
        }

        String storedName = UUID.randomUUID() + extension;
        Path target = resolveStoredFile(storedName);

        try{
            Files.createDirectories(uploadRoot);
            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
            userMapper.updateProfileImage(
                    userId, storedName, contentType);
            deleteStoredFile(user.getProfileImageStoredName());
        }catch(IOException e){
            throw new IllegalStateException(
                    "프로필 이미지를 저장하지 못했습니다.", e);
        }catch(RuntimeException e){
            try{
                Files.deleteIfExists(target);
            }catch(IOException ignored){
                // DB 저장 실패 시 새 파일 정리만 시도한다.
            }
            throw e;
        }
    }

    @Transactional
    public void delete(String userId) {
        User user = requireUser(userId);
        userMapper.clearProfileImage(userId);
        deleteStoredFile(user.getProfileImageStoredName());
    }

    public ProfileImage load(String userId) {
        User user = requireUser(userId);
        String storedName = user.getProfileImageStoredName();

        if(storedName == null || storedName.isBlank()){
            throw new ResponseStatusException(
                    NOT_FOUND, "등록된 프로필 이미지가 없습니다.");
        }

        try{
            Resource resource =
                    new UrlResource(
                            resolveStoredFile(storedName).toUri());

            if(!resource.exists() || !resource.isReadable()){
                throw new ResponseStatusException(
                        NOT_FOUND, "프로필 이미지 파일을 찾을 수 없습니다.");
            }

            return new ProfileImage(
                    resource,
                    user.getProfileImageContentType());
        }catch(IOException e){
            throw new ResponseStatusException(
                    NOT_FOUND, "프로필 이미지 파일을 찾을 수 없습니다.");
        }
    }

    private User requireUser(String userId) {
        User user = userMapper.getUserInfoById(userId);

        if(user == null){
            throw new ResponseStatusException(
                    NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    private Path resolveStoredFile(String storedName) {
        Path path = uploadRoot.resolve(storedName).normalize();

        if(!path.startsWith(uploadRoot)){
            throw new IllegalArgumentException("잘못된 이미지 경로입니다.");
        }
        return path;
    }

    private void deleteStoredFile(String storedName) {
        if(storedName == null || storedName.isBlank()){
            return;
        }

        try{
            Files.deleteIfExists(resolveStoredFile(storedName));
        }catch(IOException e){
            System.err.println(
                    "Previous profile image cleanup failed: "
                    + e.getMessage());
        }
    }

    public record ProfileImage(
            Resource resource,
            String contentType) {
    }
}
