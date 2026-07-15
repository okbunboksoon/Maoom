package maoomWeb.ire.user.service;

/** 정제 파이프라인의 입력/출력 형식. */
enum RevisionFormat {
    XML("xml"),
    DITA("dita");

    private final String value;

    RevisionFormat(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
