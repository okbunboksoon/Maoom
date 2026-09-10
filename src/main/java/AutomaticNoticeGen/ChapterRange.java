package AutomaticNoticeGen;

public class ChapterRange {
    public final int chapterNum;
    public final String chapterTitle;
    public final int startPage; // 1-based
    public final int endPage;   // 1-based
    public ChapterRange(int n, String t, int s, int e) { chapterNum=n; chapterTitle=t; startPage=s; endPage=e; }
    public int pageCount() { return endPage - startPage + 1; }
}
