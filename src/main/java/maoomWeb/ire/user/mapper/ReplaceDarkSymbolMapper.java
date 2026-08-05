package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;

/** replace_dark_symbol.xml 생성과 관리자 화면에서 사용할 치환 목록을 조회한다. */
@Mapper
public interface ReplaceDarkSymbolMapper {

    List<ReplaceDarkSymbolItem> findAll();

    int countAll();

    ReplaceDarkSymbolItem findByFromSymbol(
            @Param("fromSymbol") String fromSymbol);

    int upsert(ReplaceDarkSymbolItem item);

    int deleteByFromSymbol(@Param("fromSymbol") String fromSymbol);
}
