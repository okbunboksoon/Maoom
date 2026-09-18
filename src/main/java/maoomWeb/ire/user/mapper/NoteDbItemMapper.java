package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.NoteDbItem;

/** 지역과 hash를 복합 키로 사용하는 NOTE 기준 DB의 MyBatis 접근 인터페이스다. */
@Mapper
public interface NoteDbItemMapper {

    List<NoteDbItem> findAll();

    List<NoteDbItem> findByRegion(@Param("region") String region);

    NoteDbItem findByRegionAndHash(
            @Param("region") String region,
            @Param("hash") String hash);

    int upsert(NoteDbItem item);

    int deleteByRegionAndHash(
            @Param("region") String region,
            @Param("hash") String hash);
}
