package AutomaticNoticeGen;

public class SectionRange {
    public final String title;
    public final int startPage; // 1-based
    public final int endPage;   // 1-based
    public SectionRange(String t, int s, int e) { title=t; startPage=s; endPage=e; }
    public int pageCount() { return endPage - startPage + 1; }
}
