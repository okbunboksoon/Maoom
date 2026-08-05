package maoom.PDFImageExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PageDebugDumper {

    private final boolean enabled;
    private final int dbgPage;

    public PageDebugDumper(boolean enabled, int dbgPage) {
        this.enabled = enabled;
        this.dbgPage = dbgPage;
    }

    private void dbg(String s) {
        if (enabled) System.out.println(s);
    }

    public void dumpNeighborStats(
            int centerPage,
            int radius,
            Map<Integer, List<CodeLineExtractor.CodeLine>> codeLinesByPage,
            Map<Integer, List<ImageExtractorWithBbox.ImageHit>> imagesByPage
    ) {
        if (!enabled) return;

        int from = Math.max(1, centerPage - radius);
        int to = centerPage + radius;

        dbg("========== [DBG] neighbor stats (" + from + "~" + to + ") center=" + centerPage + " ==========");
        for (int p = from; p <= to; p++) {
            int cl = codeLinesByPage.get(p) == null ? 0 : codeLinesByPage.get(p).size();
            int im = imagesByPage.get(p) == null ? 0 : imagesByPage.get(p).size();
            dbg(String.format("[DBG] page=%d codeLines=%d images=%d", p, cl, im));
        }
        dbg("=========================================================================");
    }

    public void dumpHiRects(int page, List<HighlightRects.RectF> hiRectsPdf) {
        if (!enabled || page != dbgPage) return;

        dbg("========== [DBG] hi rects page=" + page + " ==========");
        if (hiRectsPdf == null) {
            dbg("[DBG] hiRectsPdf=null");
            dbg("===============================================");
            return;
        }
        dbg("[DBG] hiRectsPdf.size=" + hiRectsPdf.size());

        for (int i = 0; i < Math.min(hiRectsPdf.size(), 10); i++) {
            HighlightRects.RectF r = hiRectsPdf.get(i);
            float w = r.maxX - r.minX;
            float h = r.maxY - r.minY;
            dbg(String.format("[DBG] hi[%d] x=%.2f~%.2f (w=%.2f)  y=%.2f~%.2f (h=%.2f)",
                    i, r.minX, r.maxX, w, r.minY, r.maxY, h));
        }
        if (hiRectsPdf.size() > 10) dbg("[DBG] ... (more)");
        dbg("===============================================");
    }

    public void dumpFallbackLines(int page, List<CodeLineExtractor.CodeLine> fallbackLines) {
        if (!enabled || page != dbgPage) return;

        dbg("========== [DBG] fallback lines page=" + page + " ==========");
        if (fallbackLines == null) {
            dbg("[DBG] fallbackLines=null");
            dbg("================================================");
            return;
        }
        dbg("[DBG] fallbackLines.size=" + fallbackLines.size());
        for (int i = 0; i < Math.min(10, fallbackLines.size()); i++) {
            CodeLineExtractor.CodeLine cl = fallbackLines.get(i);
            dbg("[DBG] code=" + cl.code + " x=" + cl.xMin + "~" + cl.xMax + " y=" + cl.yMin + "~" + cl.yMax);
        }
        if (fallbackLines.size() > 10) dbg("[DBG] ... (more)");
        dbg("================================================");
    }

    public void dumpPageBoxes(PDDocument doc, int page) {
        if (!enabled || page != dbgPage) return;

        try {
            PDPage p = doc.getPage(page - 1);
            dbg("========== [DBG] page boxes page=" + page + " ==========");
            dbg("[DBG] rotation=" + p.getRotation());
            dbg("[DBG] mediaBox=" + p.getMediaBox());
            dbg("[DBG] cropBox =" + p.getCropBox());
            dbg("================================================");
        } catch (Exception e) {
            dbg("[DBG][ERR] dumpPageBoxes failed: " + e.getMessage());
        }
    }

    public void dumpPageText(PDDocument doc, int page) {
        if (!enabled || page != dbgPage) return;

        try {
            PDFTextStripper s = new PDFTextStripper();
            s.setStartPage(page);
            s.setEndPage(page);
            String txt = s.getText(doc);
            int len = (txt == null) ? -1 : txt.length();

            dbg("========== [DBG] page text page=" + page + " ==========");
            dbg("[DBG] text length=" + len);

            if (txt != null) {
                int limit = 2000;
                String out = (txt.length() > limit) ? (txt.substring(0, limit) + "\n... (truncated)") : txt;
                dbg(out);
            }
            dbg("===============================================");
        } catch (Exception e) {
            dbg("[DBG][ERR] dumpPageText failed: " + e.getMessage());
        }
    }
}
