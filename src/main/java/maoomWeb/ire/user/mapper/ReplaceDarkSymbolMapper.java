package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;

/**
 * Replace Symbol DB 접근 매퍼.
 *
 * <p>from_symbol은 기존 이미지 심볼명이고 유니크 키다. to_symbol은 XSL이
 * 실제 href 파일명으로 바꿀 대상 심볼명이다.</p>
 */
@Mapper
public interface ReplaceDarkSymbolMapper {

    List<ReplaceDarkSymbolItem> findAll();

    int countAll();

    ReplaceDarkSymbolItem findByFromSymbol(
            @Param("fromSymbol") String fromSymbol);

    int upsert(ReplaceDarkSymbolItem item);

    int deleteByFromSymbol(@Param("fromSymbol") String fromSymbol);
}
