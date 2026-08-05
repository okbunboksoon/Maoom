package maoom.PDFImageExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HighlightTextExtractor {

    // CodeLineExtractor랑 동일 패턴 유지
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "\\b(?=[A-Z0-9_]*\\d)(?:"
                    + "[A-Z][A-Z0-9]*_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*"
                    + "|"
                    + "[A-Z]{2,}[A-Z0-9]{7,}"
                    + ")\\b"
    );

    /**
     * 한 페이지의 highlight rect( PDF좌표 ) 내부 텍스트를 모아 코드 추출
     * - return: highlightRect별로 발견된 code 목록
     */
    public static Map<HighlightRects.RectF, List<String>> extractCodesInHighlightRects(
            PDDocument doc,
            int pageNumber1Based,
            float pageHeight,
            List<HighlightRects.RectF> hiRectsPdf
    ) throws IOException {

        if (hiRectsPdf == null || hiRectsPdf.isEmpty()) return Collections.emptyMap();

        // rect별 텍스트 누적
        Map<HighlightRects.RectF, StringBuilder> bufByRect = new LinkedHashMap<>();
        for (HighlightRects.RectF r : hiRectsPdf) bufByRect.put(r, new StringBuilder());

        // PDFTextStripper 커스텀
        PDFTextStripper stripper = new PDFTextStripper() {
            float lastXEnd = -1;
            float lastY = Float.NaN;

            @Override
            protected void processTextPosition(TextPosition tp) {
                String u = tp.getUnicode();
                if (u == null || u.isEmpty()) return;

                // TextPosition -> PDF좌표 bbox로 변환
                float x = tp.getXDirAdj();
                float w = tp.getWidthDirAdj();

                // yDirAdj는 "위에서 아래로" 느낌이라 pageHeight로 뒤집어서 PDF좌표(아래원점)로 맞춤
                // tp.getYDirAdj()는 baseline 성격이라 height로 위/아래 범위 잡음
                float yTopPdf = pageHeight - tp.getYDirAdj();
                float h = tp.getHeightDir();      // 글자 박스 높이(대략)
                if (h <= 0) h = tp.getFontSizeInPt(); // fallback
                if (h <= 0) h = 8f;

                float yMinPdf = yTopPdf - h;
                float yMaxPdf = yTopPdf;

                // 어느 highlight rect에 들어가는지 찾고 그쪽 버퍼에 추가
                for (HighlightRects.RectF r : bufByRect.keySet()) {
                    if (!intersects(r, x, yMinPdf, x + w, yMaxPdf)) continue;

                    StringBuilder sb = bufByRect.get(r);

                    // 줄바꿈/공백 대충 보정(너무 촘촘하면 붙어서 regex가 깨짐)
                    float y = tp.getYDirAdj();
                    if (!Float.isNaN(lastY) && Math.abs(lastY - y) > 2.5f) {
                        sb.append(' ');
                        lastXEnd = -1;
                    }

                    if (lastXEnd >= 0) {
                        float gap = x - lastXEnd;
                        float spaceW = tp.getWidthOfSpace();
                        if (spaceW > 0 && gap > spaceW * 0.5f) sb.append(' ');
                    }

                    sb.append(u);

                    lastXEnd = x + w;
                    lastY = y;

                    // 한 글자가 여러 rect에 걸칠 수도 있지만 보통 하나에만 들어가니까 여기서 break
                    break;
                }
            }
        };

        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber1Based);
        stripper.setEndPage(pageNumber1Based);
        stripper.getText(doc);

        // rect별로 코드 추출
        Map<HighlightRects.RectF, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<HighlightRects.RectF, StringBuilder> e : bufByRect.entrySet()) {
            String txt = e.getValue().toString();
            List<String> codes = findCodes(txt);
            if (!codes.isEmpty()) out.put(e.getKey(), codes);
        }
        return out;
    }

    private static boolean intersects(HighlightRects.RectF r, float minX, float minY, float maxX, float maxY) {
        float ox = Math.min(r.maxX, maxX) - Math.max(r.minX, minX);
        float oy = Math.min(r.maxY, maxY) - Math.max(r.minY, minY);
        return ox > 0 && oy > 0;
    }

    private static List<String> findCodes(String text) {
        if (text == null) return Collections.emptyList();
        String t = text.trim();
        if (t.isEmpty()) return Collections.emptyList();

        List<String> list = new ArrayList<>();
        Matcher m = CODE_PATTERN.matcher(t);
        while (m.find()) {
            String code = m.group().trim();
            if (code.length() >= 8) list.add(code);
        }
        // 중복 제거(순서 유지)
        LinkedHashSet<String> uniq = new LinkedHashSet<>(list);
        return new ArrayList<>(uniq);
    }
}
