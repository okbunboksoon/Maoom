package maoomWeb.ire.user.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.mapper.UserMapper;

/**
 * 사용자 프로필 이미지의 파일 저장과 tb_user 연결 정보를 함께 관리한다.
 *
 * <p>UserController의 업로드 API가 이 서비스를 호출한다. 이미지 파일은
 * {@code app.user.profile-upload-dir}에 UUID 이름으로 저장하고, 저장 파일명과
 * MIME 타입은 UserMapper를 통해 tb_user에 기록한다.</p>
 */
@Service
public class UserProfileImageService {

    private static final Logger log =
            LoggerFactory.getLogger(UserProfileImageService.class);

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

    /**
     * 이미지 형식과 5MB 제한을 검사한 뒤 새 파일과 DB 정보를 저장한다.
     * DB 저장까지 성공한 후에만 이전 프로필 이미지 파일을 삭제한다.
     */
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

    /** DB의 프로필 연결을 먼저 지운 뒤 기존 실제 파일을 삭제한다. */
    @Transactional
    public void delete(String userId) {
        User user = requireUser(userId);
        userMapper.clearProfileImage(userId);
        deleteStoredFile(user.getProfileImageStoredName());
    }

    /** 사용자 프로필 이미지 파일을 HTTP 응답에 사용할 Resource와 MIME 타입으로 반환한다. */
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

    /** 존재하는 사용자인지 확인하고 이후 파일 작업에 필요한 사용자 정보를 반환한다. */
    private User requireUser(String userId) {
        User user = userMapper.getUserInfoById(userId);

        if(user == null){
            throw new ResponseStatusException(
                    NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    /** 저장 파일 경로가 프로필 루트 폴더 밖으로 벗어나는 경로 조작을 막는다. */
    private Path resolveStoredFile(String storedName) {
        Path path = uploadRoot.resolve(storedName).normalize();

        if(!path.startsWith(uploadRoot)){
            throw new IllegalArgumentException("잘못된 이미지 경로입니다.");
        }
        return path;
    }

    /** 이전 이미지 정리 실패가 새 이미지 저장 성공을 취소하지 않도록 경고만 기록한다. */
    private void deleteStoredFile(String storedName) {
        if(storedName == null || storedName.isBlank()){
            return;
        }

        try{
            Files.deleteIfExists(resolveStoredFile(storedName));
        }catch(IOException e){
            log.warn(
                    "Previous profile image cleanup failed",
                    e);
        }
    }

    /** 컨트롤러가 이미지 본문과 Content-Type을 함께 응답하도록 묶은 내부 결과다. */
    public record ProfileImage(
            Resource resource,
            String contentType) {
    }
}
