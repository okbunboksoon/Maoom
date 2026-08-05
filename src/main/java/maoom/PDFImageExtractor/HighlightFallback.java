package maoom.PDFImageExtractor;

import java.io.IOException;
import java.util.*;

public class HighlightFallback {

    /**
     * 1) highlight rect 영역에서 텍스트 코드 추출 시도
     * 2) 실패하면 neighbor(±radius)에서 "단일 코드 1개"만 가져오는 우회
     * @throws IOException 
     */
    public List<CodeLineExtractor.CodeLine> resolveFromHighlightOrNeighbor(
            PageContext ctx,
            Map<Integer, List<CodeLineExtractor.CodeLine>> codeLinesByPage,
            int neighborRadius
    ) throws IOException {
        // 1) highlight rect 영역 텍스트 기반 추출
        Map<HighlightRects.RectF, List<String>> codesInRects =
                HighlightTextExtractor.extractCodesInHighlightRects(
                        ctx.doc(), ctx.page(), ctx.pageHeight(), ctx.hiRectsPdf()
                );

        if (codesInRects != null && !codesInRects.isEmpty()) {
            List<CodeLineExtractor.CodeLine> out = new ArrayList<>();
            for (Map.Entry<HighlightRects.RectF, List<String>> e : codesInRects.entrySet()) {
                HighlightRects.RectF r = e.getKey();
                for (String code : e.getValue()) {
                    out.add(new CodeLineExtractor.CodeLine(
                            ctx.page(),
                            code,
                            r.minX, r.maxX,
                            r.minY, r.maxY,
                            0, 0
                    ));
                }
            }
            System.out.println("[HI-FALLBACK] page=" + ctx.page() + " codesFromHighlightArea=" + out.size());
            return out;
        }

        // 2) neighbor fallback (오탐 방지: "단일 코드"만 채택)
        String neighborCode = findNeighborSingleCode(codeLinesByPage, ctx.page(), neighborRadius);
        if (neighborCode != null) {
            System.out.println("[HI-NEIGHBOR] page=" + ctx.page() + " use neighborCode=" + neighborCode);

            // matchOne이 yCenter를 참고하니, 대충 중앙 bbox로 만들어도 동작은 함(정교하진 않음)
            float midY = ctx.pageHeight() / 2f;

            return List.of(new CodeLineExtractor.CodeLine(
                    ctx.page(),
                    neighborCode,
                    0, 10,
                    midY - 5, midY + 5,
                    0, 0
            ));
        }

        return Collections.emptyList();
    }

    private static String findNeighborSingleCode(
            Map<Integer, List<CodeLineExtractor.CodeLine>> codeLinesByPage,
            int page,
            int radius
    ) {
        for (int d = 1; d <= radius; d++) {
            String c1 = firstIfSingle(codeLinesByPage.get(page - d));
            if (c1 != null) return c1;

            String c2 = firstIfSingle(codeLinesByPage.get(page + d));
            if (c2 != null) return c2;
        }
        return null;
    }

    private static String firstIfSingle(List<CodeLineExtractor.CodeLine> list) {
        if (list == null) return null;
        if (list.size() == 1) return list.get(0).code;
        return null;
    }
}
