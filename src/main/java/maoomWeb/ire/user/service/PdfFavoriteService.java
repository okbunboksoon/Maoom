package maoomWeb.ire.user.service;

import java.util.List;

import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.PdfFavoriteDto;
import maoomWeb.ire.user.mapper.PdfFavoriteMapper;

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
