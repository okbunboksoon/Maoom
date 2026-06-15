package maoomWeb.ire.user.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.User;
import maoomWeb.ire.user.mapper.UserMapper;

@Service
/**
 * 현재 로그인 사용자의 정보를 제공한다.
 */
public class CurrentUserService {

    private final UserMapper userMapper;

    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** Spring Security 사용자명을 반환한다. */
    public String getUserId(Authentication authentication){

        if(authentication == null){
            return null;
        }

        return authentication.getName();
    }

    /** DB의 사용자 이름을 반환하고 없으면 로그인 ID를 사용한다. */
    public String getUserName(Authentication authentication){

        if(authentication == null){
            return null;
        }

        String userId = authentication.getName();
        User user = userMapper.getUserInfoById(userId);

        if(user == null
                || user.getUserName() == null
                || user.getUserName().isBlank()){
            return userId;
        }

        return user.getUserName();
    }
}
