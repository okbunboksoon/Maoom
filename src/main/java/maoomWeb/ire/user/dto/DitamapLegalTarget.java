package maoomWeb.ire.user.dto;

/** 2안 Builder에서 자동 체크와 법규 위치 배치에 사용하는 법규 대상 topic 정보. */
public class DitamapLegalTarget {

    private String fileName;
    private String title;
    private String parentL1File;
    private String parentL2File;
    private int level;

    public DitamapLegalTarget() {
    }

    public DitamapLegalTarget(
            String fileName,
            String title,
            String parentL1File,
            String parentL2File,
            int level) {
        this.fileName = fileName;
        this.title = title;
        this.parentL1File = parentL1File;
        this.parentL2File = parentL2File;
        this.level = level;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getParentL1File() {
        return parentL1File;
    }

    public void setParentL1File(String parentL1File) {
        this.parentL1File = parentL1File;
    }

    public String getParentL2File() {
        return parentL2File;
    }

    public void setParentL2File(String parentL2File) {
        this.parentL2File = parentL2File;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
