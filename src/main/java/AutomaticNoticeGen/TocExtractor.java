package AutomaticNoticeGen;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDestinationNameTreeNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDFBox 3.x 전용 TOC 추출기 (DEBUG + GoTo 액션 + L1 보정)
 * - 챕터: 깊이 1 북마크
 * - 섹션: 깊이 2+ (DFS)
 * - 페이지 해석 우선순위:
 *   1) PDOutlineItem.findDestinationPage(doc)
 *   2) getDestination() → PDPageDestination / PDNamedDestination(NameTree)
 *   3) getAction() → PDActionGoTo → PDPageDestination / PDNamedDestination(NameTree)
 * - L1(챕터)의 page가 -1이면 자식들의 최솟값 페이지로 보정
 * - 섹션이 0개면 챕터 전체 1개 섹션으로 보장
 */
public class TocExtractor {

    private static final boolean DEBUG = true;
    private static final Pattern CHAPTER_TITLE_IN_TEXT =
            Pattern.compile("(?:(?:제\\s*)?(\\d{1,3})\\s*장)\\b");

    private static void log(String tag, String fmt, Object... args) {
        if (!DEBUG) return;
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SS"));
        System.out.println("[TOC][" + ts + "][" + tag + "] " + String.format(fmt, args));
    }

    /* =========================
     * 공개 API
     * ========================= */

    /** 1레벨(장) 추출 */
    public static List<ChapterRange> extractChapterRanges(File pdf, int ignored) throws IOException {
        log("CHAPTERS", "open: %s", pdf.getAbsolutePath());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int total = doc.getNumberOfPages();
            log("CHAPTERS", "total pages=%d", total);

            PDDocumentOutline outline = getOutline(doc);
            if (outline == null) {
                log("CHAPTERS", "outline == null → empty");
                return Collections.emptyList();
            }

            // L1을 직접 순회: L1 page가 -1일 땐 자식들의 최소 page로 보정
            List<L1Node> l1s = collectLevel1WithFix(doc, outline);
            log("CHAPTERS", "L1 count=%d", l1s.size());
            for (int i = 0; i < l1s.size(); i++) {
                log("CHAPTERS", "L1[%d] title='%s' page=%d", i, l1s.get(i).title, l1s.get(i).page);
                
                
            }
            if (l1s.isEmpty()) return Collections.emptyList();

            // start/end 계산
            List<ChapterRange> rows = new ArrayList<>();
            for (int i = 0; i < l1s.size(); i++) {
                L1Node cur = l1s.get(i);
                int start = (cur.page > 0) ? cur.page : -1;
                int nextStart = (i + 1 < l1s.size()) ? l1s.get(i + 1).page : -1;
                int end;
                if (start > 0 && nextStart > 0) end = Math.max(start, nextStart - 1);
                else if (start > 0) end = total;
                else end = -1;

                
                String title = normTitle(cleanTitle(cur.title));
                Integer num = parseChapterNumFromTitle(title);

                if (num == null) num = rows.size() + 1;

                ChapterRange ch = new ChapterRange(num, title, start, end);
                rows.add(ch);
                log("CHAPTERS", "chapter[%d] #%d '%s' %d-%d", i, ch.chapterNum, ch.chapterTitle, ch.startPage, ch.endPage);
            }
            return rows;
        }
    }

    /** 특정 장의 섹션 추출 */
    public static List<SectionRange> extractSectionsOfChapter(File pdf, ChapterRange chap, int ignored) throws IOException {
        log("SECTIONS", "open: %s", pdf.getAbsolutePath());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            log("SECTIONS", "chapter #%d '%s' %d-%d", chap.chapterNum, chap.chapterTitle, chap.startPage, chap.endPage);

            PDDocumentOutline outline = getOutline(doc);
            if (outline == null) {
                log("SECTIONS", "outline == null → fallback 1 section");
                return Collections.singletonList(new SectionRange(cleanTitle(chap.chapterTitle), posOr1(chap.startPage), posOr1(chap.endPage)));
            }

            // L1 찾기: 1) 제목 동치(정규화) → 2) startPage<= 기준 근접 → 3) 인덱스(챕터 번호-1)
            PDOutlineItem l1 = findL1ByTitle(doc, outline, chap.chapterTitle);
            if (l1 == null) l1 = findOutlineItemByStartPageRelaxed(doc, outline, chap.startPage);
            if (l1 == null) l1 = findL1ByIndex(outline, chap.chapterNum - 1);
            if (l1 == null) {
                log("SECTIONS", "parent L1 not found → fallback");
                return Collections.singletonList(new SectionRange(cleanTitle(chap.chapterTitle), posOr1(chap.startPage), posOr1(chap.endPage)));
            }
            int l1Page = pageOfItem(doc, l1);
            log("SECTIONS", "parent L1='%s' page=%d", safe(l1.getTitle()), l1Page);

            // DFS로 깊이2+ 전부 수집
            List<SectionRange> secs = new ArrayList<>();
            Deque<PDOutlineItem> stack = new ArrayDeque<>();
            for (PDOutlineItem c = l1.getFirstChild(); c != null; c = c.getNextSibling()) stack.push(c);

            int visit = 0;
            while (!stack.isEmpty()) {
                PDOutlineItem cur = stack.pop();
                visit++;
                String title = normTitle(cleanTitle(safe(cur.getTitle())));
                int page = pageOfItem(doc, cur);

                String actType = (cur.getAction() == null) ? "null" : cur.getAction().getClass().getSimpleName();
                String destType = (cur.getDestination() == null) ? "null" : cur.getDestination().getClass().getSimpleName();
                log("SECT-DFS", "dfs[%d] title='%s' page=%d dest=%s action=%s", visit, title, page, destType, actType);

                int chapStart = (chap.startPage > 0) ? chap.startPage : l1Page;
                int chapEnd   = (chap.endPage   > 0) ? chap.endPage   : Integer.MAX_VALUE;

                if (!title.isBlank() && page > 0 && page >= Math.max(1, chapStart) && page <= chapEnd) {
                    secs.add(new SectionRange(title, page, chapEnd));
                }
                for (PDOutlineItem c = cur.getFirstChild(); c != null; c = c.getNextSibling()) stack.push(c);
            }
            log("SECTIONS", "raw candidates=%d", secs.size());

            if (secs.isEmpty()) {
                log("SECTIONS", "no sections → fallback 1 section");
                int s = (chap.startPage > 0) ? chap.startPage : Math.max(1, l1Page);
                int e = (chap.endPage > 0) ? chap.endPage : s;
                return Collections.singletonList(new SectionRange(cleanTitle(chap.chapterTitle), s, e));
            }

            secs.sort(Comparator.comparingInt(a -> a.startPage));
            List<SectionRange> fixed = new ArrayList<>();
            for (int i = 0; i < secs.size(); i++) {
                SectionRange cur = secs.get(i);
                int end = (i + 1 < secs.size()) ? secs.get(i + 1).startPage - 1
                        : ((chap.endPage > 0) ? chap.endPage : Integer.MAX_VALUE);
                SectionRange f = new SectionRange(cur.title, cur.startPage, Math.max(cur.startPage, end));
                fixed.add(f);
                log("SECTIONS", "final[%d] '%s' %d-%d", i, f.title, f.startPage, f.endPage);
            }
            return fixed;
        }
    }

    /* =========================
     * L1 수집/보정
     * ========================= */

    private static class L1Node {
        final String title;
        final int page;
        final PDOutlineItem item;
        L1Node(String title, int page, PDOutlineItem item) {
            this.title = title; this.page = page; this.item = item;
        }
    }

    private static List<L1Node> collectLevel1WithFix(PDDocument doc, PDDocumentOutline outline) {
        List<L1Node> list = new ArrayList<>();
        int i = 0;
        for (PDOutlineItem l1 = outline.getFirstChild(); l1 != null; l1 = l1.getNextSibling()) {
            String t1 = cleanTitle(safe(l1.getTitle()));
            int p1 = pageOfItem(doc, l1);
            if (p1 <= 0) {
                // 자식들 중 최솟값 page로 보정
                int minChild = minPageInSubtree(doc, l1);
                if (minChild > 0) {
                    log("L1FIX", "L1[%d] '%s' page=-1 → fix to first child page=%d", i, t1, minChild);
                    p1 = minChild;
                } else {
                    log("L1FIX", "L1[%d] '%s' page=-1 and no child page found", i, t1);
                }
            }
            list.add(new L1Node(t1, p1, l1));
            i++;
        }
        // page>0 기준 정렬(페이지 없는 항목은 뒤로)
        list.sort(Comparator.comparingInt(a -> (a.page > 0 ? a.page : Integer.MAX_VALUE)));
        return list;
    }

    private static int minPageInSubtree(PDDocument doc, PDOutlineItem root) {
        int min = Integer.MAX_VALUE;
        Deque<PDOutlineItem> st = new ArrayDeque<>();
        for (PDOutlineItem c = root.getFirstChild(); c != null; c = c.getNextSibling()) st.push(c);
        while (!st.isEmpty()) {
            PDOutlineItem n = st.pop();
            int p = pageOfItem(doc, n);
            if (p > 0) min = Math.min(min, p);
            for (PDOutlineItem c = n.getFirstChild(); c != null; c = c.getNextSibling()) st.push(c);
        }
        return (min == Integer.MAX_VALUE) ? -1 : min;
    }

    /* =========================
     * Outline helpers
     * ========================= */

    private static PDDocumentOutline getOutline(PDDocument doc) {
        PDDocumentCatalog cat = doc.getDocumentCatalog();
        PDDocumentOutline outline = (cat != null) ? cat.getDocumentOutline() : null;
        log("OUTLINE", "hasOutline=%s", String.valueOf(outline != null));
        return outline;
    }

    private static PDOutlineItem findL1ByIndex(PDDocumentOutline outline, int idx) {
        if (outline == null || idx < 0) return null;
        int i = 0;
        for (PDOutlineItem l1 = outline.getFirstChild(); l1 != null; l1 = l1.getNextSibling(), i++) {
            if (i == idx) return l1;
        }
        return null;
    }

    private static PDOutlineItem findL1ByTitle(PDDocument doc, PDDocumentOutline outline, String targetTitle) {
        String tgt = normTitle(targetTitle);
        for (PDOutlineItem l1 = outline.getFirstChild(); l1 != null; l1 = l1.getNextSibling()) {
            String t = normTitle(safe(l1.getTitle()));
            if (!t.isEmpty() && t.equals(tgt)) return l1;
        }
        return null;
    }

    private static PDOutlineItem findOutlineItemByStartPageRelaxed(PDDocument doc, PDDocumentOutline outline, int startPage) {
        PDOutlineItem best = null; int bestPage = -1; int i = 0;
        for (PDOutlineItem l1 = outline.getFirstChild(); l1 != null; l1 = l1.getNextSibling(), i++) {
            int p = pageOfItem(doc, l1);
            log("MATCH", "L1[%d] '%s' page=%d target=%d", i, safe(l1.getTitle()), p, startPage);
            if (startPage <= 0) continue;
            if (p > 0 && p <= startPage && p > bestPage) { best = l1; bestPage = p; }
        }
        log("MATCH", "selected L1='%s' page=%d", best == null ? "null" : safe(best.getTitle()), bestPage);
        return best;
    }

    /* =========================
     * 페이지 해석 (PDFBox 3.x + GoTo)
     * ========================= */

    private static int pageOfItem(PDDocument doc, PDOutlineItem it) {
        try {
            if (it == null) return -1;

            // 1) 기본: findDestinationPage
            PDPage page = it.findDestinationPage(doc);
            if (page != null) {
                int no = indexOfPage(doc, page);
                log("PAGE", "findDestinationPage OK → %d (title='%s')", no, safe(it.getTitle()));
                return no;
            }

            // 2) Destination 직접 해석
            int fromDest = pageFromDestinationOrNamed(doc, it.getDestination());
            if (fromDest > 0) {
                log("PAGE", "from Destination → %d (title='%s')", fromDest, safe(it.getTitle()));
                return fromDest;
            }

            // 3) GoTo 액션 해석
            PDAction act = it.getAction();
            if (act instanceof PDActionGoTo) {
                PDActionGoTo go = (PDActionGoTo) act;
                int fromAct = pageFromDestinationOrNamed(doc, go.getDestination());
                log("PAGE", "from ActionGoTo(%s) → %d (title='%s')",
                        (go.getDestination() == null ? "null" : go.getDestination().getClass().getSimpleName()),
                        fromAct, safe(it.getTitle()));
                if (fromAct > 0) return fromAct;
            }

            log("PAGE", "unresolved (title='%s') → -1", safe(it.getTitle()));
            return -1;
        } catch (IOException e) {
            log("PAGE", "IOException title='%s' : %s", safe(it.getTitle()), e.toString());
            return -1;
        }
    }

    /** PDPageDestination / PDNamedDestination 공통 처리 */
    private static int pageFromDestinationOrNamed(PDDocument doc, PDDestination dest) {
        if (dest == null) return -1;

        if (dest instanceof PDPageDestination pd) {
            try {
                int num = pd.retrievePageNumber(); // 0-based
                if (num >= 0) return num + 1;
            } catch (Throwable ignore) {}
            PDPage p = pd.getPage();
            return (p != null) ? indexOfPage(doc, p) : -1;
        }

        if (dest instanceof PDNamedDestination nd) {
            return pageFromNamedDestination(doc, nd);
        }
        return -1;
    }

    /** PDNamedDestination → NameTree 매핑 (3.x에는 findPage가 없음) */
    private static int pageFromNamedDestination(PDDocument doc, PDNamedDestination nd) {
        if (nd == null) return -1;
        try {
            String name = nd.getNamedDestination();
            if (name == null || name.isEmpty()) return -1;

            PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
            if (names == null) {
                log("NDEST", "names is null (name=%s)", name);
                return -1;
            }
            PDDestinationNameTreeNode dests = names.getDests();
            if (dests == null) {
                log("NDEST", "dests is null (name=%s)", name);
                return -1;
            }

            PDDestination mapped = dests.getValue(name);
            if (mapped instanceof PDPageDestination pd) {
                try {
                    int num = pd.retrievePageNumber(); // 0-based
                    if (num >= 0) {
                        log("NDEST", "retrievePageNumber=%d for '%s'", num + 1, name);
                        return num + 1;
                    }
                } catch (Throwable ignore) {}
                PDPage p = pd.getPage();
                int idx = (p != null) ? indexOfPage(doc, p) : -1;
                log("NDEST", "getPage→%d for '%s'", idx, name);
                return idx;
            } else {
                log("NDEST", "mapped not PDPageDestination (%s) for '%s'",
                        (mapped == null ? "null" : mapped.getClass().getSimpleName()), name);
            }
            return -1;
        } catch (Exception e) {
            log("NDEST", "Exception: %s", e.toString());
            return -1;
        }
    }

    private static int indexOfPage(PDDocument doc, PDPage page) {
        if (page == null) return -1;
        try {
            int idx = doc.getPages().indexOf(page);
            return (idx >= 0) ? idx + 1 : -1; // 1-based
        } catch (Exception e) {
            int i = 0;
            for (PDPage p : doc.getPages()) {
                if (p == page) return i + 1;
                i++;
            }
            return -1;
        }
    }

    /* =========================
     * 보조
     * ========================= */

    private static String safe(String s) { return s == null ? "" : s; }

    private static String cleanTitle(String s) {
        if (s == null) return "";

        String original = s;
        // NBSP → 일반 공백
        s = s.replace('\u00A0', ' ');

        // 공백 정리
        s = s.replaceAll("\\s+", " ");

        // 끝에 오는 점/점점이/띄어쓰기 정리
        s = s.replaceAll("[.·•‧…\\s]+$", "")
             .trim();

        System.out.println("CLEAN_TITLE  IN  =[" + original + "]");
        System.out.println("CLEAN_TITLE  OUT =[" + s + "]");
        return s;
    }

    private static String normTitle(String s) {
        if (s == null) return "";
        String in = s;

        // 1일 / 1 회 / 1 번 같은 것들만 안전하게 붙이기
        s = s.replaceAll("(\\d)\\s+(일|회|번|부|장|차|단계)\\b", "$1$2");

        // ❗ 여기서는 절대 앞 숫자 제거 안 함 ❗
        // s = s.replaceAll("^\\s*(\\d+)[).:\\-]\\s*", "");  ← 이거 주석처리 유지!

        s = s.replaceAll("\\s+", " ").trim();

        System.out.println("NORM_TITLE   IN  =[" + in + "]");
        System.out.println("NORM_TITLE   OUT =[" + s + "]");
        return s;
    }




    private static int posOr1(int v) {
        return (v > 0) ? v : 1;
    }

    private static Integer parseChapterNumFromTitle(String title) {
        if (title == null) return null;
        Matcher m = CHAPTER_TITLE_IN_TEXT.matcher(title);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }
}
