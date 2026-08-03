package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.BerAsisTobePair;

/** BER 실행 직전에 region별 asis-tobe 데이터를 조회한다. */
@Mapper
public interface BerAsisTobePairMapper {

    List<BerAsisTobePair> findByRegion(@Param("region") String region);

    int countByRegion(@Param("region") String region);

    List<BerAsisTobePair> findAll();

    BerAsisTobePair findByRegionAndHash(
            @Param("region") String region,
            @Param("hash") String hash);

    int upsert(BerAsisTobePair pair);

    int deleteByRegionAndHash(
            @Param("region") String region,
            @Param("hash") String hash);
}
