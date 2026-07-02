package maoomWeb.ire.user.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.dto.UserAccountUpdateDto;
import maoomWeb.ire.user.mapper.UserMapper;


@Service
/**
 * 사용자 인증과 멘션 대상 사용자 조회를 담당한다.
 */
public class UserService {

    private static final Logger log =
            LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 아이디와 비밀번호를 검증한다.
     * 기존 평문 비밀번호가 확인되면 BCrypt로 변환하여 점진적으로 마이그레이션한다.
     */
    @Transactional
    public Map<String,Object> checkLogin(String username, String password){

        Map<String,Object> resultMap = new HashMap<String,Object>();
        boolean result = false;

        User user = userMapper.getUserInfoById(username);

        if(user != null){
            String checkPw = user.getUserPw();

            if(isBcryptHash(checkPw)){

                result =
                        passwordEncoder.matches(
                                password,
                                checkPw);

            }else if(password.equals(checkPw)){

                result = true;

                String encodedPassword =
                        passwordEncoder.encode(password);

                try{

                    userMapper.updatePassword(
                            username,
                            encodedPassword);

                    user.setUserPw(encodedPassword);

                }catch(RuntimeException e){

                    log.warn(
                            "BCrypt password migration failed. Run migrate_user_password_to_bcrypt.sql",
                            e);
                }
            }
        }

        resultMap.put("result", result);
        resultMap.put("userInfo", user);

        return resultMap;
    }

    /** Slack 멘션 자동완성에 사용할 사용자 목록을 반환한다. */
    public List<User> getMentionUsers(){
        return userMapper.getMentionUsers();
    }

    /** 로그인 사용자의 이름과 선택적으로 새 비밀번호를 변경한다. */
    @Transactional
    public User updateAccount(
            String userId,
            UserAccountUpdateDto dto) {

        User savedUser =
                userMapper.getUserInfoById(userId);

        if(savedUser == null){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "사용자 정보를 찾을 수 없습니다.");
        }

        String userName =
                dto.getUserName() == null
                ? ""
                : dto.getUserName().trim();

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

        userMapper.updateUserName(userId, userName);

        String newPassword = dto.getNewPassword();

        if(newPassword != null && !newPassword.isBlank()){
            if(newPassword.length() < 6){
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "비밀번호는 6자 이상 입력해주세요.");
            }

            if(newPassword.length() > 72){
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "비밀번호는 72자 이하로 입력해주세요.");
            }

            userMapper.updatePassword(
                    userId,
                    passwordEncoder.encode(newPassword));
        }

        savedUser.setUserName(userName);
        savedUser.setUserPw(null);
        return savedUser;
    }

    /** 저장된 문자열이 BCrypt 해시 형식인지 확인한다. */
    private boolean isBcryptHash(String password){

        return password != null
                && password.matches(
                        "^\\$2[ayb]\\$.{56}$");
    }
}
