package maoom.PDFImageExtractor;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;

public class HighlightAnnotationExtractor {

    /**
     * 반환 좌표계: "top-down"(원점=페이지 위쪽, 아래로 갈수록 y 증가)
     * - CodeLineExtractor의 TextPosition.getYDirAdj()와 맞춤
     */
    public static Map<Integer, List<Rectangle2D.Float>> extractHighlightsByPage(PDDocument doc) throws IOException {
        Map<Integer, List<Rectangle2D.Float>> out = new HashMap<>();

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            int pageNo = i + 1;
            PDPage page = doc.getPage(i);
            float pageH = page.getMediaBox().getHeight();

            List<Rectangle2D.Float> rects = new ArrayList<>();

            for (PDAnnotation ann : page.getAnnotations()) {
                if (!(ann instanceof PDAnnotationTextMarkup)) continue;

                PDAnnotationTextMarkup tm = (PDAnnotationTextMarkup) ann;

                // Highlight 주석만
                String subtype = tm.getSubtype(); // "Highlight"
                if (subtype == null || !"Highlight".equalsIgnoreCase(subtype)) continue;

                float[] quads = tm.getQuadPoints();

                if (quads != null && quads.length >= 8) {
                    for (int q = 0; q + 7 < quads.length; q += 8) {
                        float x1 = quads[q],   y1 = quads[q + 1];
                        float x2 = quads[q+2], y2 = quads[q + 3];
                        float x3 = quads[q+4], y3 = quads[q + 5];
                        float x4 = quads[q+6], y4 = quads[q + 7];

                        float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
                        float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
                        float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
                        float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

                        // PDF(bottom-up) -> top-down 변환
                        float top = pageH - maxY;
                        float bottom = pageH - minY;

                        rects.add(new Rectangle2D.Float(minX, top, (maxX - minX), (bottom - top)));
                    }
                } else {
                    // quad가 없으면 annotation rectangle 사용
                    PDRectangle r = tm.getRectangle();
                    if (r != null) {
                        float minX = r.getLowerLeftX();
                        float maxX = r.getUpperRightX();
                        float minY = r.getLowerLeftY();
                        float maxY = r.getUpperRightY();

                        float top = pageH - maxY;
                        float bottom = pageH - minY;

                        rects.add(new Rectangle2D.Float(minX, top, (maxX - minX), (bottom - top)));
                    }
                }
            }

            if (!rects.isEmpty()) out.put(pageNo, rects);
        }

        return out;
    }
}
