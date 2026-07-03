package maoomWeb.ire.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;

/**
 * 컬러체크 서비스와 MyBatis SQL 파일을 연결하는 인터페이스.
 *
 * <p>각 메서드 이름은
 * {@code resources/mapper/user/DrawingColorCheckMapper.xml}의
 * {@code id}와 같아야 한다. 서비스는 이 인터페이스만 호출하고,
 * 실제 SQL은 XML에서 관리한다.</p>
 */
@Mapper
public interface DrawingColorCheckMapper {

    /** 여러 도안명을 한 번의 SQL로 조회해 엑셀 생성 시 V/X를 채운다. */
    List<DrawingColorCheckDto> findByDrawingNames(
            @Param("drawingNames") List<String> drawingNames);

    /** DB에 저장된 전체 도안 목록을 조회한다. */
    List<DrawingColorCheckDto> findAll();

    /**
     * 도안명이 없으면 추가하고, 이미 있으면 V/X 값만 수정한다.
     * 반환값은 DB에서 영향을 받은 행 수다.
     */
    int upsert(DrawingColorCheckDto colorCheck);

    /** 도안명 기준으로 컬러체크 데이터를 삭제한다. */
    int deleteByDrawingName(@Param("drawingName") String drawingName);
}
