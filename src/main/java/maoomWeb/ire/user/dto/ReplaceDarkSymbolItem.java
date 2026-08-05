package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/**
 * replace_dark_symbol.xml의 {@code <replace from="..." to="..."/>} 한 줄.
 *
 * <p>관리자 화면에서는 From/To 컬럼으로 보여주고, 배치 실행 직전에는
 * {@link maoomWeb.ire.user.service.ReplaceDarkSymbolService}가 이 값을 다시
 * {@code xsl/replace_dark_symbol.xml}로 만든다.</p>
 */
public class ReplaceDarkSymbolItem {

    private Long id;
    private String fromSymbol;
    private String toSymbol;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFromSymbol() {
        return fromSymbol;
    }

    public void setFromSymbol(String fromSymbol) {
        this.fromSymbol = fromSymbol;
    }

    public String getToSymbol() {
        return toSymbol;
    }

    public void setToSymbol(String toSymbol) {
        this.toSymbol = toSymbol;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
