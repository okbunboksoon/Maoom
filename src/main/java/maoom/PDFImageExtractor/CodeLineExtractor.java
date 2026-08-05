package maoom.PDFImageExtractor;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class CodeLineExtractor {

	private static final Pattern CODE_PATTERN = Pattern.compile(
		    "\\b(?=[A-Za-z0-9_]*\\d)(?:"
		  + "[A-Z][A-Z0-9]*_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*"
		  + "|"
		  + "[A-Z]{2,}[A-Z0-9]{7,}"
		  + ")\\b"
		);

    // 필요하면 true로 켜고 bbox 확인해
    private static final boolean DEBUG = false;

    public static class CodeLine {
        public final int page;      // 1-based
        public final String code;   // 도안명

        // ✅ 토큰(코드) 범위 bbox (TextPosition 기반, X는 yDirAdj 기준)
        public final float xMin;
        public final float xMax;

        // ✅ yDirAdj 기반 bbox
        public final float yMin;
        public final float yMax;

        // 디버그용
        public final int startIndex;
        public final int endIndex;

        public CodeLine(int page, String code, float xMin, float xMax, float yMin, float yMax,
                        int startIndex, int endIndex) {
            this.page = page;
            this.code = code;
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public String toString() {
            return "CodeLine{page=" + page + ", code=" + code +
                   ", xMin=" + xMin + ", xMax=" + xMax +
                   ", yMin=" + yMin + ", yMax=" + yMax +
                   ", start=" + startIndex + ", end=" + endIndex + "}";
        }
    }

    /**
     * 페이지별 도안명 "토큰(코드) 단위" 추출
     * - 한 줄에 코드가 2개 이상 있어도 각각의 bbox를 따로 만듦
     */
    public static Map<Integer, List<CodeLine>> extractCodeLinesByPage(PDDocument doc) throws IOException {
        LineCapturingStripper stripper = new LineCapturingStripper();
        stripper.setSortByPosition(true);

        Map<Integer, List<CodeLine>> out = new LinkedHashMap<>();
        int pageCount = doc.getNumberOfPages();

        for (int page = 1; page <= pageCount; page++) {
            stripper.resetForPage(page);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(doc);

            List<Line> lines = stripper.buildLines();

            List<CodeLine> codes = new ArrayList<>();
            for (Line line : lines) {

                Matcher m = CODE_PATTERN.matcher(line.text);
                while (m.find()) {
                    String code = m.group().trim();
                    if (code.length() < 8) continue;

                    int s = m.start();
                    int e = m.end();

                    BBox tokenBox = computeTokenBBox(line, s, e);
                    if (tokenBox == null) continue;

                    CodeLine cl = new CodeLine(
                        page, code,
                        tokenBox.xMin, tokenBox.xMax,
                        tokenBox.yMin, tokenBox.yMax,
                        s, e
                    );

                    if (DEBUG) {
                        System.out.println("[CODE] " + cl);
                        System.out.println("       lineText=" + line.text);
                    }

                    codes.add(cl);
                }
            }

            if (!codes.isEmpty()) out.put(page, codes);
        }

        return out;
    }

    // ===== 내부 구현 =====

    private static class Line {
        String text;
        float xMin, xMax;
        float yMin, yMax;

        // ✅ text의 각 문자 인덱스에 대응하는 TextPosition
        // - 삽입 공백/진짜 공백은 null
        List<TextPosition> charTPs;

        Line(String text, float xMin, float xMax, float yMin, float yMax, List<TextPosition> charTPs) {
            this.text = text;
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
            this.charTPs = charTPs;
        }
    }

    private static class BBox {
        float xMin, xMax, yMin, yMax;
        BBox(float xMin, float xMax, float yMin, float yMax) {
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
        }
    }

    /**
     * ✅ 개선 포인트:
     * - 기존: fontSizeInPt로 y범위 추정 -> PDF마다 흔들림
     * - 변경: TextPosition.getHeightDir() 기반으로 y범위를 잡음(더 안정적)
     */
    private static BBox computeTokenBBox(Line line, int s, int e) {
        if (line.charTPs == null || line.charTPs.isEmpty()) return null;
        if (s < 0 || e <= s) return null;

        float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;
        float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;

        int limit = Math.min(e, line.charTPs.size());

        for (int i = s; i < limit; i++) {
            TextPosition tp = line.charTPs.get(i);
            if (tp == null) continue;

            float x = tp.getXDirAdj();
            float w = tp.getWidthDirAdj();

            // ✅ yDirAdj는 baseline이 아니라 "표시 좌표"에 가깝게 나올 때가 많음
            // getHeightDir()는 글자 박스 높이에 가까워서 y범위가 훨씬 안정적
            float yTopLike = tp.getYDirAdj();           // 기준점(대개 top 쪽으로 취급됨)
            float h = tp.getHeightDir();                // 글자 높이(방향 고려)
            if (h <= 0) h = Math.max(6f, tp.getFontSizeInPt()); // 최후 fallback

            // yDirAdj 기준으로 "세로 박스" 구성
            float thisMin = yTopLike - h; // 아래쪽
            float thisMax = yTopLike;     // 위쪽

            xMin = Math.min(xMin, x);
            xMax = Math.max(xMax, x + w);
            yMin = Math.min(yMin, thisMin);
            yMax = Math.max(yMax, thisMax);
        }

        if (xMin == Float.MAX_VALUE || xMax == -Float.MAX_VALUE) return null;

        // 너무 얇게 잡히면 살짝 패딩(하이라이트/매칭 안정)
        float padY = 1.5f;
        return new BBox(xMin, xMax, yMin - padY, yMax + padY);
    }

    private static class LineCapturingStripper extends PDFTextStripper {
        private final List<TextPosition> positions = new ArrayList<>();
        @SuppressWarnings("unused")
        private int currentPage = 1;

        LineCapturingStripper() throws IOException { super(); }

        void resetForPage(int page) {
            this.currentPage = page;
            this.positions.clear();
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            positions.add(text);
        }

        List<Line> buildLines() {
            final float yTol = 2.0f;

            // y 내림차순(위->아래처럼 보이게) + x 오름차순
            positions.sort((a, b) -> {
                int cy = Float.compare(b.getYDirAdj(), a.getYDirAdj());
                if (cy != 0) return cy;
                return Float.compare(a.getXDirAdj(), b.getXDirAdj());
            });

            List<List<TextPosition>> buckets = new ArrayList<>();
            for (TextPosition tp : positions) {
                float y = tp.getYDirAdj();
                boolean placed = false;

                for (List<TextPosition> bucket : buckets) {
                    float by = bucket.get(0).getYDirAdj();
                    if (Math.abs(by - y) <= yTol) {
                        bucket.add(tp);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    List<TextPosition> nb = new ArrayList<>();
                    nb.add(tp);
                    buckets.add(nb);
                }
            }

            List<Line> lines = new ArrayList<>();

            for (List<TextPosition> bucket : buckets) {
                bucket.sort(Comparator.comparing(TextPosition::getXDirAdj));

                StringBuilder sb = new StringBuilder();
                List<TextPosition> charTPs = new ArrayList<>();

                float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;
                float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;

                float lastXEnd = -1;

                for (TextPosition tp : bucket) {
                    String u = tp.getUnicode();
                    if (u == null || u.isEmpty()) continue;

                    float x = tp.getXDirAdj();
                    float w = tp.getWidthDirAdj();

                    // 간격이 크면 공백 삽입
                    if (lastXEnd >= 0) {
                        float gap = x - lastXEnd;
                        float spaceW = tp.getWidthOfSpace();
                        if (spaceW > 0 && gap > spaceW * 0.5f) {
                            sb.append(' ');
                            charTPs.add(null);
                        }
                    }

                    sb.append(u);
                    for (int i = 0; i < u.length(); i++) {
                        charTPs.add(tp);
                    }

                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x + w);

                    // 라인 y범위도 getHeightDir로 안정화
                    float yTopLike = tp.getYDirAdj();
                    float h = tp.getHeightDir();
                    if (h <= 0) h = Math.max(6f, tp.getFontSizeInPt());

                    float thisMin = yTopLike - h;
                    float thisMax = yTopLike;

                    yMin = Math.min(yMin, thisMin);
                    yMax = Math.max(yMax, thisMax);

                    lastXEnd = x + w;
                }

                if (sb.length() > 0) {
                    // 라인도 너무 얇으면 패딩
                    float padY = 1.5f;
                    lines.add(new Line(sb.toString(), xMin, xMax, yMin - padY, yMax + padY, charTPs));
                }
            }

            return lines;
        }
    }
}
