package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.AutomaticNoticeRule;

/** 협조문 자동 완성 실행 직전에 region별 룰을 조회한다. */
@Mapper
public interface AutomaticNoticeRuleMapper {

    List<AutomaticNoticeRule> findByRegion(@Param("region") String region);

    List<AutomaticNoticeRule> findEnabledByRegion(@Param("region") String region);

    int countByRegion(@Param("region") String region);

    List<AutomaticNoticeRule> findAll();

    AutomaticNoticeRule findByRegionTypeAndKey(
            @Param("region") String region,
            @Param("matchType") String matchType,
            @Param("matchKey") String matchKey);

    int upsert(AutomaticNoticeRule rule);

    int deleteByRegionTypeAndKey(
            @Param("region") String region,
            @Param("matchType") String matchType,
            @Param("matchKey") String matchKey);
}
