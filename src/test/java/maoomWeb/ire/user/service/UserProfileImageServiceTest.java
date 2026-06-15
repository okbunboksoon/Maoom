package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.mapper.UserMapper;

@ExtendWith(MockitoExtension.class)
class UserProfileImageServiceTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void savesImageAndUpdatesUser(
            @TempDir Path uploadRoot) throws Exception {

        User user = user("user@maoom.com");
        when(userMapper.getUserInfoById(user.getUserId()))
                .thenReturn(user);

        UserProfileImageService service =
                new UserProfileImageService(
                        userMapper,
                        uploadRoot.toString());
        MockMultipartFile image =
                new MockMultipartFile(
                        "file",
                        "avatar.png",
                        "image/png",
                        new byte[]{1, 2, 3});

        service.save(user.getUserId(), image);

        try(var files = Files.list(uploadRoot)){
            assertThat(files).hasSize(1);
        }
        verify(userMapper).updateProfileImage(
                org.mockito.ArgumentMatchers.eq(user.getUserId()),
                org.mockito.ArgumentMatchers.endsWith(".png"),
                org.mockito.ArgumentMatchers.eq("image/png"));
    }

    @Test
    void deletesStoredImageAndClearsUser(
            @TempDir Path uploadRoot) throws Exception {

        User user = user("user@maoom.com");
        user.setProfileImageStoredName("old.png");
        Files.write(uploadRoot.resolve("old.png"), new byte[]{1});
        when(userMapper.getUserInfoById(user.getUserId()))
                .thenReturn(user);

        new UserProfileImageService(
                userMapper,
                uploadRoot.toString())
                .delete(user.getUserId());

        assertThat(uploadRoot.resolve("old.png")).doesNotExist();
        verify(userMapper).clearProfileImage(user.getUserId());
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }
}
