package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.ProjectDbItem;

/** 관리자 프로젝트 DB 공통 CRUD 매퍼. */
@Mapper
public interface ProjectDbItemMapper {

    List<ProjectDbItem> findAll(@Param("tableName") String tableName);

    ProjectDbItem findByRegionAndHash(
            @Param("tableName") String tableName,
            @Param("region") String region,
            @Param("hash") String hash);

    int upsert(
            @Param("tableName") String tableName,
            @Param("item") ProjectDbItem item);

    int deleteByRegionAndHash(
            @Param("tableName") String tableName,
            @Param("region") String region,
            @Param("hash") String hash);
}
