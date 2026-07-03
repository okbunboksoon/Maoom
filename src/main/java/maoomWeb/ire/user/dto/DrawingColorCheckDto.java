package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/**
 * Java 서비스와 DB 사이에서 도안별 컬러체크 데이터를 전달하는 DTO.
 *
 * <p>MyBatis의 {@code DrawingColorCheckResultMap}이 DB 열을 아래 필드에
 * 연결한다. 별도의 업무 로직은 없고 데이터만 담는 객체다.</p>
 */
public class DrawingColorCheckDto {

    /** PDF와 엑셀에서 사용하는 고유 도안 ID. DB 기본키다. */
    private String drawingName;

    /** 컬러도안 여부. 허용 값은 V 또는 X다. */
    private String checkValue;

    /** DB에 처음 등록된 시각. 목록 정렬과 확인 용도로 사용한다. */
    private LocalDateTime regDt;

    public String getDrawingName() {
        return drawingName;
    }

    public void setDrawingName(String drawingName) {
        this.drawingName = drawingName;
    }

    public String getCheckValue() {
        return checkValue;
    }

    public void setCheckValue(String checkValue) {
        this.checkValue = checkValue;
    }

    public LocalDateTime getRegDt() {
        return regDt;
    }

    public void setRegDt(LocalDateTime regDt) {
        this.regDt = regDt;
    }
}
