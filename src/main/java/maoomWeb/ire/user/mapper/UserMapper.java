package maoomWeb.ire.user.mapper;

import java.util.List;

import maoomWeb.ire.user.dto.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Java 사용자 서비스와 {@code UserMapper.xml}의 SQL을 연결하는 MyBatis 인터페이스다.
 *
 * <p>메서드 이름과 XML의 select/update id가 같아야 한다. 로그인, 계정 변경,
 * 댓글 멘션, Slack ID 조회와 프로필 이미지 연결 정보가 모두 이 매퍼를 통과한다.</p>
 */
@Mapper
public interface UserMapper {

    /** 로그인·현재 사용자·프로필 기능에 필요한 사용자 한 건을 ID로 조회한다. */
    public User getUserInfoById(String userId);

    /** 댓글 @멘션 자동완성에 표시할 전체 사용자 목록을 반환한다. */
    List<User> getMentionUsers();

    /** 로그인 마이그레이션 또는 계정 변경에서 BCrypt 비밀번호를 저장한다. */
    int updatePassword(
            @Param("userId") String userId,
            @Param("userPw") String userPw);

    /** 계정 설정에서 사용자 표시 이름을 변경한다. */
    int updateUserName(
            @Param("userId") String userId,
            @Param("userName") String userName);

    /** 새 프로필 이미지의 저장 파일명과 MIME 타입을 사용자 행에 연결한다. */
    int updateProfileImage(
            @Param("userId") String userId,
            @Param("storedName") String storedName,
            @Param("contentType") String contentType);

    /** 프로필 이미지 삭제 시 사용자 행의 파일 연결 정보를 비운다. */
    int clearProfileImage(String userId);

    /** 댓글의 @이름 또는 @아이디를 Slack 멤버 ID로 변환한다. */
    String getSlackUserIdByMention(String mention);
	

}
