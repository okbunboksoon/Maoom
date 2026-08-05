package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.PdfFavoriteDto;

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
