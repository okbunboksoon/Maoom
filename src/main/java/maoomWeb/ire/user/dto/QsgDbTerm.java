package maoomWeb.ire.user.dto;

/**
 * QSG_DB.xml의 term 한 건을 관리자 화면에 표시하기 위한 DTO.
 *
 * <p>원본 XML에서는 하나의 {@code entry/@hash} 아래에 여러 {@code term/@lang}이
 * 들어 있지만, 관리자 화면에서는 검색과 정렬을 위해 {@code hash + lang + term}
 * 한 줄로 펼쳐서 보여준다. 그래서 실제 한 행의 유니크 기준은 {@code hash + lang}이다.</p>
 */
public class QsgDbTerm {

    /** QSG XSL이 원문 텍스트로 계산한 SHA-256 해시. 같은 문장 묶음을 찾는 그룹 키다. */
    private String hash;

    /** QSG_DB.xml의 term/@lang 값. 예: EN-GB, ko-KR, ja-JP. */
    private String lang;

    /** 해당 hash/lang 조합에 적용할 실제 표시 문구. */
    private String term;

    public QsgDbTerm(String hash, String lang, String term) {
        this.hash = hash;
        this.lang = lang;
        this.term = term;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }
}
