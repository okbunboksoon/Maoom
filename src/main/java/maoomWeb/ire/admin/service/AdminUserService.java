package maoomWeb.ire.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** 관리자 화면에서 사용자 테이블을 조회한다. */
@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /** tb_user의 사용자 목록을 아이디순으로 가져온다. */
    public List<AdminUserDto> findAll() {
        return jdbcTemplate.query(userSelectSql() + """
                ORDER BY user_id
                """,
                this::mapUser);
    }

    private String userSelectSql(){
        return """
                SELECT
                    user_id,
                    CASE
                        WHEN user_pw IS NULL OR user_pw = '' THEN '미설정'
                        ELSE '********'
                    END AS password_status,
                    user_name,
                    user_email,
                    user_role,
                    slack_user_id
                FROM tb_user
                """;
    }

    private AdminUserDto mapUser(
            ResultSet resultSet,
            int rowNumber)
            throws SQLException {

        return new AdminUserDto(
                resultSet.getString("user_id"),
                resultSet.getString("password_status"),
                resultSet.getString("user_name"),
                resultSet.getString("user_email"),
                resultSet.getString("user_role"),
                resultSet.getString("slack_user_id"));
    }

    /** 관리자 사용자 테이블에서 허용한 필드만 수정한다. */
    @Transactional
    public AdminUserDto updateUser(
            String userId,
            AdminUserUpdateRequest request) {

        if(userId == null || userId.isBlank()){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "사용자 아이디가 없습니다.");
        }

        String field = request == null ? "" : request.field();
        String value = request == null || request.value() == null
                ? ""
                : request.value().trim();

        int updated;

        if("userName".equals(field)){
            validateUserName(value);
            updated = jdbcTemplate.update("""
                    UPDATE tb_user
                    SET user_name = ?
                    WHERE user_id = ?
                    """,
                    value,
                    userId);
        }else if("userEmail".equals(field)){
            validateEmail(value);
            updated = jdbcTemplate.update("""
                    UPDATE tb_user
                    SET user_email = ?
                    WHERE user_id = ?
                    """,
                    value,
                    userId);
        }else if("slackUserId".equals(field)){
            updated = jdbcTemplate.update("""
                    UPDATE tb_user
                    SET slack_user_id = ?
                    WHERE user_id = ?
                    """,
                    blankToNull(value),
                    userId);
        }else if("password".equals(field)){
            validatePassword(value);
            updated = jdbcTemplate.update("""
                    UPDATE tb_user
                    SET user_pw = ?
                    WHERE user_id = ?
                    """,
                    passwordEncoder.encode(value),
                    userId);
        }else if("userRole".equals(field)){
            String normalizedRole = normalizeRole(value);
            updated = jdbcTemplate.update("""
                    UPDATE tb_user
                    SET user_role = ?
                    WHERE user_id = ?
                    """,
                    normalizedRole,
                    userId);
        }else{
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "수정할 수 없는 항목입니다.");
        }

        if(updated == 0){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "사용자 정보를 찾을 수 없습니다.");
        }

        return findByUserId(userId);
    }

    /** 관리자 화면에서 신규 사용자를 등록한다. */
    @Transactional
    public AdminUserDto createUser(AdminUserCreateRequest request) {
        String userId = request == null || request.userId() == null
                ? ""
                : request.userId().trim();
        String password = request == null || request.password() == null
                ? ""
                : request.password().trim();
        String userName = request == null || request.userName() == null
                ? ""
                : request.userName().trim();
        String userEmail = request == null || request.userEmail() == null
                ? ""
                : request.userEmail().trim();
        String userRole = normalizeRole(request == null ? "" : request.userRole());
        String slackUserId = request == null || request.slackUserId() == null
                ? ""
                : request.slackUserId().trim();

        validateUserId(userId);
        validatePassword(password);
        validateUserName(userName);
        validateEmail(userEmail);

        Integer existingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tb_user
                WHERE user_id = ?
                """,
                Integer.class,
                userId);

        if(existingCount != null && existingCount > 0){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "이미 등록된 아이디입니다.");
        }

        jdbcTemplate.update("""
                INSERT INTO tb_user (
                    user_id,
                    user_pw,
                    user_name,
                    user_email,
                    user_role,
                    slack_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                userId,
                passwordEncoder.encode(password),
                userName,
                userEmail,
                userRole,
                blankToNull(slackUserId));

        return findByUserId(userId);
    }

    /** 관리자 화면에서 사용자를 삭제한다. */
    @Transactional
    public void deleteUser(String userId) {
        if(userId == null || userId.isBlank()){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "사용자 아이디가 없습니다.");
        }

        int deleted = jdbcTemplate.update("""
                DELETE FROM tb_user
                WHERE user_id = ?
                """,
                userId);

        if(deleted == 0){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "사용자 정보를 찾을 수 없습니다.");
        }
    }

    private AdminUserDto findByUserId(String userId) {
        return jdbcTemplate.queryForObject(userSelectSql() + """
                WHERE user_id = ?
                """,
                this::mapUser,
                userId);
    }

    private String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);

        if(role.startsWith("ROLE_")){
            role = role.substring("ROLE_".length());
        }

        if("USER".equals(role)){
            role = "Y";
        }

        if(!"Y".equals(role) && !"ADMIN".equals(role)){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "권한은 Y 또는 ADMIN만 입력할 수 있습니다.");
        }

        return role;
    }

    private void validateUserId(String userId) {
        if(userId.isBlank()){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "아이디를 입력해주세요.");
        }
        if(userId.length() > 50){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "아이디는 50자 이하로 입력해주세요.");
        }
    }

    private void validateUserName(String userName) {
        if(userName.isBlank()){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "이름을 입력해주세요.");
        }
        if(userName.length() > 20){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "이름은 20자 이하로 입력해주세요.");
        }
    }

    private void validateEmail(String email) {
        if(email.isBlank()){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "이메일을 입력해주세요.");
        }
        if(email.length() > 100){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "이메일은 100자 이하로 입력해주세요.");
        }
    }

    private void validatePassword(String password) {
        if(password.length() < 6){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "비밀번호는 6자 이상 입력해주세요.");
        }
        if(password.length() > 72){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "비밀번호는 72자 이하로 입력해주세요.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value;
    }
}
