package maoomWeb.ire.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserServiceTest {

    @Test
    void findsUsersForAdminTable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminUserDto> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("user_id")).thenReturn("admin");
                    when(resultSet.getString("password_status")).thenReturn("설정됨");
                    when(resultSet.getString("user_name")).thenReturn("관리자");
                    when(resultSet.getString("user_email")).thenReturn("admin@test.com");
                    when(resultSet.getString("user_role")).thenReturn("ROLE_ADMIN");
                    when(resultSet.getString("slack_user_id")).thenReturn("UADMIN");
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        AdminUserService service = new AdminUserService(jdbcTemplate, passwordEncoder);

        List<AdminUserDto> users = service.findAll();

        assertThat(users).containsExactly(new AdminUserDto(
                "admin",
                "설정됨",
                "관리자",
                "admin@test.com",
                "ROLE_ADMIN",
                "UADMIN"));
    }

    @Test
    void updatesUserNameFromAdminTable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.update(anyString(), eq("김예림1"), eq("admin")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("admin")))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminUserDto> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("user_id")).thenReturn("admin");
                    when(resultSet.getString("password_status")).thenReturn("설정됨");
                    when(resultSet.getString("user_name")).thenReturn("김예림1");
                    when(resultSet.getString("user_email")).thenReturn("admin@test.com");
                    when(resultSet.getString("user_role")).thenReturn("ADMIN");
                    when(resultSet.getString("slack_user_id")).thenReturn("UADMIN");
                    return mapper.mapRow(resultSet, 0);
                });

        AdminUserService service = new AdminUserService(jdbcTemplate, passwordEncoder);

        AdminUserDto updated = service.updateUser(
                "admin",
                new AdminUserUpdateRequest("userName", "김예림1"));

        assertThat(updated.userName()).isEqualTo("김예림1");
        verify(jdbcTemplate).update(anyString(), eq("김예림1"), eq("admin"));
    }

    @Test
    void updatesUserRoleToYFromAdminTable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.update(anyString(), eq("Y"), eq("user1")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("user1")))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminUserDto> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("user_id")).thenReturn("user1");
                    when(resultSet.getString("password_status")).thenReturn("설정됨");
                    when(resultSet.getString("user_name")).thenReturn("사용자");
                    when(resultSet.getString("user_email")).thenReturn("user1@test.com");
                    when(resultSet.getString("user_role")).thenReturn("Y");
                    when(resultSet.getString("slack_user_id")).thenReturn("UUSER1");
                    return mapper.mapRow(resultSet, 0);
                });

        AdminUserService service = new AdminUserService(jdbcTemplate, passwordEncoder);

        AdminUserDto updated = service.updateUser(
                "user1",
                new AdminUserUpdateRequest("userRole", "Y"));

        assertThat(updated.userRole()).isEqualTo("Y");
        verify(jdbcTemplate).update(anyString(), eq("Y"), eq("user1"));
    }

    @Test
    void createsUserFromAdminTable() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("newuser")))
                .thenReturn(0);
        when(passwordEncoder.encode("secret1"))
                .thenReturn("encoded-secret");
        when(jdbcTemplate.update(
                anyString(),
                eq("newuser"),
                eq("encoded-secret"),
                eq("신규"),
                eq("newuser@test.com"),
                eq("Y"),
                eq("UNEW")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("newuser")))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminUserDto> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("user_id")).thenReturn("newuser");
                    when(resultSet.getString("password_status")).thenReturn("설정됨");
                    when(resultSet.getString("user_name")).thenReturn("신규");
                    when(resultSet.getString("user_email")).thenReturn("newuser@test.com");
                    when(resultSet.getString("user_role")).thenReturn("Y");
                    when(resultSet.getString("slack_user_id")).thenReturn("UNEW");
                    return mapper.mapRow(resultSet, 0);
                });

        AdminUserService service = new AdminUserService(jdbcTemplate, passwordEncoder);

        AdminUserDto created = service.createUser(
                new AdminUserCreateRequest(
                        "newuser",
                        "secret1",
                        "신규",
                        "newuser@test.com",
                        "Y",
                        "UNEW"));

        assertThat(created.userId()).isEqualTo("newuser");
        verify(jdbcTemplate).update(
                anyString(),
                eq("newuser"),
                eq("encoded-secret"),
                eq("신규"),
                eq("newuser@test.com"),
                eq("Y"),
                eq("UNEW"));
    }

    @Test
    void deletesUserFromAdminTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.update(anyString(), eq("user1")))
                .thenReturn(1);

        AdminUserService service = new AdminUserService(jdbcTemplate, passwordEncoder);

        service.deleteUser("user1");

        verify(jdbcTemplate).update(anyString(), eq("user1"));
    }
}
