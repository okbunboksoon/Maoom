package maoom.PDFImageExtractor;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;

/**
 * PDF Highlight(PDAnnotationTextMarkup subtype=Highlight)에서
 * quadPoints 기반으로 (PDF 좌표계) rect들을 뽑는다.
 *
 * rect format: float[]{minX, minY, maxX, maxY} in PDF coordinates (origin: bottom-left)
 */
public class HighlightRects {

    public static class RectF {
        public final float minX, minY, maxX, maxY;
        public RectF(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
        @Override public String toString() {
            return "RectF[" + minX + "," + minY + "," + maxX + "," + maxY + "]";
        }
    }

    public static List<RectF> extractHighlightRectsPdf(PDPage page) throws java.io.IOException {
        List<RectF> out = new ArrayList<>();
        List<PDAnnotation> annots = page.getAnnotations();
        if (annots == null || annots.isEmpty()) return out;

        PDRectangle crop = page.getCropBox();
        float cropMinX = crop != null ? crop.getLowerLeftX() : 0f;
        float cropMinY = crop != null ? crop.getLowerLeftY() : 0f;

        for (PDAnnotation a : annots) {
            if (!(a instanceof PDAnnotationTextMarkup)) continue;

            PDAnnotationTextMarkup tm = (PDAnnotationTextMarkup) a;
            String sub = tm.getSubtype();
            if (sub == null || !sub.equalsIgnoreCase("Highlight")) continue;

            float[] q = tm.getQuadPoints();
            if (q != null && q.length >= 8) {
                // quadPoints: 8 floats per quad (x1,y1,x2,y2,x3,y3,x4,y4)
                for (int i = 0; i + 7 < q.length; i += 8) {
                    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                    float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

                    for (int k = 0; k < 8; k += 2) {
                        float x = q[i + k];
                        float y = q[i + k + 1];
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }

                    // cropBox offset 반영(가끔 0이 아닐 수 있음)
                    minX += cropMinX; maxX += cropMinX;
                    minY += cropMinY; maxY += cropMinY;

                    // 너무 얇은 rect는 살짝 padding
                    float pad = 0.5f;
                    out.add(new RectF(minX - pad, minY - pad, maxX + pad, maxY + pad));
                }
            } else {
                // quadPoints 없으면 rectangle로 fallback (정밀도 낮지만 안전)
                PDRectangle r = tm.getRectangle();
                if (r != null) {
                    float minX = r.getLowerLeftX() + cropMinX;
                    float minY = r.getLowerLeftY() + cropMinY;
                    float maxX = r.getUpperRightX() + cropMinX;
                    float maxY = r.getUpperRightY() + cropMinY;
                    out.add(new RectF(minX, minY, maxX, maxY));
                }
            }
        }

        return out;
    }
}
