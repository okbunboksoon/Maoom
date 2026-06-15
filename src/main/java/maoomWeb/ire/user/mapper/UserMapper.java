package maoomWeb.ire.user.mapper;

import java.util.List;

import maoomWeb.ire.user.dto.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * 사용자 인증, 멘션 대상, Slack 사용자 ID를 조회하는 MyBatis 매퍼.
 */
public interface UserMapper {

    // 특정 회원 정보 조회
    public User getUserInfoById(String userId);

    List<User> getMentionUsers();

    int updatePassword(
            @Param("userId") String userId,
            @Param("userPw") String userPw);

    int updateUserName(
            @Param("userId") String userId,
            @Param("userName") String userName);

    int updateProfileImage(
            @Param("userId") String userId,
            @Param("storedName") String storedName,
            @Param("contentType") String contentType);

    int clearProfileImage(String userId);

    String getSlackUserIdByMention(String mention);
	

}
