package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.dto.UserAccountUpdateDto;
import maoomWeb.ire.user.mapper.UserMapper;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void updatesNameAndKeepsPasswordWhenBlank() {
        User savedUser = user("user@maoom.com", "기존이름");
        UserAccountUpdateDto dto = new UserAccountUpdateDto();
        dto.setUserName("새이름");
        dto.setNewPassword("");

        when(userMapper.getUserInfoById("user@maoom.com"))
                .thenReturn(savedUser);

        User result =
                new UserService(userMapper, passwordEncoder)
                .updateAccount("user@maoom.com", dto);

        assertThat(result.getUserName()).isEqualTo("새이름");
        verify(userMapper).updateUserName(
                "user@maoom.com", "새이름");
        verify(userMapper, never()).updatePassword(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void encodesNewPasswordBeforeSaving() {
        UserAccountUpdateDto dto = new UserAccountUpdateDto();
        dto.setUserName("사용자");
        dto.setNewPassword("newpass!");
        dto.setCurrentPassword("oldpass!");
        dto.setConfirmPassword("newpass!");

        when(userMapper.getUserInfoById("user@maoom.com"))
                .thenReturn(user(
                        "user@maoom.com",
                        "사용자",
                        "oldpass!"));
        when(passwordEncoder.encode("newpass!"))
                .thenReturn("encoded-password");

        new UserService(userMapper, passwordEncoder)
                .updateAccount("user@maoom.com", dto);

        verify(userMapper).updatePassword(
                "user@maoom.com",
                "encoded-password");
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        UserAccountUpdateDto dto = new UserAccountUpdateDto();
        dto.setUserName("사용자");
        dto.setCurrentPassword("wrongpass!");
        dto.setNewPassword("newpass!");
        dto.setConfirmPassword("newpass!");

        when(userMapper.getUserInfoById("user@maoom.com"))
                .thenReturn(user(
                        "user@maoom.com",
                        "사용자",
                        "oldpass!"));

        assertThatThrownBy(() ->
                new UserService(userMapper, passwordEncoder)
                .updateAccount("user@maoom.com", dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.");

        verify(userMapper, never()).updatePassword(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsPasswordChangeWhenConfirmPasswordDiffers() {
        UserAccountUpdateDto dto = new UserAccountUpdateDto();
        dto.setUserName("사용자");
        dto.setCurrentPassword("oldpass!");
        dto.setNewPassword("newpass!");
        dto.setConfirmPassword("different!");

        when(userMapper.getUserInfoById("user@maoom.com"))
                .thenReturn(user(
                        "user@maoom.com",
                        "사용자",
                        "oldpass!"));

        assertThatThrownBy(() ->
                new UserService(userMapper, passwordEncoder)
                .updateAccount("user@maoom.com", dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "신규 비밀번호와 확인 비밀번호가 일치하지 않습니다.");

        verify(userMapper, never()).updatePassword(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private User user(String userId, String userName) {
        return user(userId, userName, null);
    }

    private User user(
            String userId,
            String userName,
            String userPw) {
        User user = new User();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setUserPw(userPw);
        return user;
    }
}
