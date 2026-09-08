package AutomaticNoticeGen;

public class TocItem {
    public final String title;
    public final int page; // 시작 페이지(1-based)
    public TocItem(String title, int page) { this.title = title; this.page = page; }
}
