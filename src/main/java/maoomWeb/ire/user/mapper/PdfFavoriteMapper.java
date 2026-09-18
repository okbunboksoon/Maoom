package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.PdfFavoriteDto;

/** 사용자별 PDF 또는 폴더 즐겨찾기를 조회하고 추가·삭제하는 MyBatis 인터페이스다. */
@Mapper
public interface PdfFavoriteMapper {

    List<PdfFavoriteDto> findByUserId(String userId);

    int existsFavorite(
            @Param("userId") String userId,
            @Param("itemId") String itemId);

    int insertFavorite(PdfFavoriteDto favorite);

    int deleteFavorite(
            @Param("userId") String userId,
            @Param("itemId") String itemId);
}
