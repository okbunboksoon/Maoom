package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import maoomWeb.ire.user.dto.DitamapLegalTarget;

/** 2안 Builder의 법규 대상 파일명 DB를 조회/초기화하는 MyBatis Mapper. */
@Mapper
public interface DitamapLegalTargetMapper {

    List<DitamapLegalTarget> findAll();

    int countAll();

    int insertIgnore(DitamapLegalTarget target);
}
