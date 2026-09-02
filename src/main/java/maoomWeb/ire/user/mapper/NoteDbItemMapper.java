package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.NoteDbItem;

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
