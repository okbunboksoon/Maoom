package maoomWeb.ire.user.service;

import java.util.List;

import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.PdfFavoriteDto;
import maoomWeb.ire.user.mapper.PdfFavoriteMapper;

/** 로그인 사용자의 PDF·폴더 즐겨찾기 상태를 조회하고 토글하는 서비스다. */
@Service
public class PdfFavoriteService {

    private final PdfFavoriteMapper pdfFavoriteMapper;

    public PdfFavoriteService(PdfFavoriteMapper pdfFavoriteMapper) {
        this.pdfFavoriteMapper = pdfFavoriteMapper;
    }

    public List<PdfFavoriteDto> findFavorites(String userId) {
        return pdfFavoriteMapper.findByUserId(userId);
    }

    public List<PdfFavoriteDto> toggleFavorite(
            String userId,
            PdfFavoriteDto favorite) {

        if(pdfFavoriteMapper.existsFavorite(
                userId,
                favorite.getId()) > 0) {
            pdfFavoriteMapper.deleteFavorite(
                    userId,
                    favorite.getId());
        }else{
            favorite.setUserId(userId);
            favorite.setType(
                    "folder".equals(favorite.getType())
                    ? "folder"
                    : "pdf");
            pdfFavoriteMapper.insertFavorite(favorite);
        }

        return findFavorites(userId);
    }
}
