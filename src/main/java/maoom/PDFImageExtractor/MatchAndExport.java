package maoom.PDFImageExtractor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class MatchAndExport {

    /** 텍스트 기반 코드라인(원래 방식): 하이라이트 교차 체크 후 처리 
     * @throws IOException */
	public static void processTextCodeLines(
	        PageContext ctx,
	        Set<Integer> usedIdx,
	        Set<String> exportedCodes,
	        File outDir,
	        List<ExcelExporter.RowData> excelRows
	) throws IOException {

	    List<CodeLineExtractor.CodeLine> highlighted = new java.util.ArrayList<>();
	    for (CodeLineExtractor.CodeLine cl : ctx.codeLines()) {
	        if (isCodeHighlightedPdf(cl, ctx.pageHeight(), ctx.hiRectsPdf())) {
	            highlighted.add(cl);
	        }
	    }

	    // ✅ 케이스: 하이라이트는 있는데 "코드 글자 위"가 아니라서 highlighted가 0개인 경우
	    // -> 같은 페이지의 코드 중 "하이라이트와 가장 가까운 코드" 1개를 선택해서 진행
	    if (highlighted.isEmpty()
	            && ctx.hiRectsPdf() != null
	            && !ctx.hiRectsPdf().isEmpty()
	            && ctx.codeLines() != null
	            && !ctx.codeLines().isEmpty()) {

	        CodeLineExtractor.CodeLine best = null;
	        float bestScore = Float.MAX_VALUE;

	        // 하이라이트 중심Y와 코드 중심Y 거리로 가장 가까운 코드 선택
	        for (CodeLineExtractor.CodeLine cl : ctx.codeLines()) {
	            float clCy = (cl.yMin + cl.yMax) / 2f;

	            for (HighlightRects.RectF r : ctx.hiRectsPdf()) {
	                float rCy = (r.minY + r.maxY) / 2f;
	                float dy = Math.abs(clCy - rCy);

	                if (dy < bestScore) {
	                    bestScore = dy;
	                    best = cl;
	                }
	            }
	        }

	        if (best != null) {
	            System.out.println("[HI-CLOSEST] page=" + ctx.page()
	                    + " pickClosestCode=" + best.code
	                    + " dy=" + bestScore);

	            exportOne(ctx, best, usedIdx, exportedCodes, outDir, excelRows);
	        }

	        return;
	    }

	    // ✅ 기존 방식: 코드 글자 위에 하이라이트 쳐진 것만 처리
	    for (CodeLineExtractor.CodeLine cl : highlighted) {
	        exportOne(ctx, cl, usedIdx, exportedCodes, outDir, excelRows);
	    }
	}

	private static void exportOne(
	        PageContext ctx,
	        CodeLineExtractor.CodeLine cl,
	        Set<Integer> usedIdx,
	        Set<String> exportedCodes,
	        File outDir,
	        List<ExcelExporter.RowData> excelRows
	) throws IOException {

	    if (exportedCodes.contains(cl.code)) {
	        System.out.println("[DUP] skip code=" + cl.code + " (already exported)");
	        return;
	    }

	    CodeImageMatcher.MatchResult mr =
	            CodeImageMatcher.matchOne(ctx.page(), ctx.pageHeight(), cl, ctx.pageImgs(), usedIdx);

	    System.out.println("[MATCH] page " + ctx.page() + " code=" + cl.code
	            + " used=" + mr.mode
	            + " (candidates=" + mr.candidates + ", pickIndex=" + mr.imageIndex + ")");

	    if (mr.image == null || mr.imageIndex < 0) {
	        System.out.println("[PNG] page " + ctx.page() + " code=" + cl.code + " -> no image");
	        return;
	    }

	    usedIdx.add(mr.imageIndex);

	    File png = PngByCodeWriter.buildPngFile(outDir, ctx.page(), cl.code, 0);
	    PngByCodeWriter.write(mr.image, png);

	    excelRows.add(new ExcelExporter.RowData(ctx.page(), png));
	    exportedCodes.add(cl.code);

	    System.out.println("[PNG] saved: " + png.getAbsolutePath()
	            + "  bbox=" + mr.image.bbox);
	}


    /** 하이라이트 fallback으로 만든 코드라인(이미 하이라이트 기반이므로 교차 체크 생략) 
     * @throws IOException */
    public static void processFallbackCodeLines(
            PageContext ctx,
            List<CodeLineExtractor.CodeLine> fallbackLines,
            Set<Integer> usedIdx,
            Set<String> exportedCodes,
            File outDir,
            List<ExcelExporter.RowData> excelRows
    ) throws IOException {
        for (CodeLineExtractor.CodeLine cl : fallbackLines) {

            if (exportedCodes.contains(cl.code)) {
                System.out.println("[DUP] skip code=" + cl.code + " (already exported)");
                continue;
            }

            CodeImageMatcher.MatchResult mr =
                    CodeImageMatcher.matchOne(ctx.page(), ctx.pageHeight(), cl, ctx.pageImgs(), usedIdx);

            System.out.println("[MATCH-HI] page " + ctx.page() + " code=" + cl.code
                    + " used=" + mr.mode
                    + " (candidates=" + mr.candidates + ", pickIndex=" + mr.imageIndex + ")");

            if (mr.image == null || mr.imageIndex < 0) {
                System.out.println("[PNG] page " + ctx.page() + " code=" + cl.code + " -> no image");
                continue;
            }

            usedIdx.add(mr.imageIndex);

            File png = PngByCodeWriter.buildPngFile(outDir, ctx.page(), cl.code, 0);
            PngByCodeWriter.write(mr.image, png);

            excelRows.add(new ExcelExporter.RowData(ctx.page(), png));
            exportedCodes.add(cl.code);

            System.out.println("[PNG] saved: " + png.getAbsolutePath()
                    + "  bbox=" + mr.image.bbox);
        }
    }

    /**
     *하이라이트 판정 (PDF 좌표로 통일)
     * - cl.yMin/yMax는 yDirAdj 성격일 수 있어 pageHeight로 뒤집어서 PDF좌표로 변환
     * - hiRectsPdf는 PDF좌표
     */
    private static boolean isCodeHighlightedPdf(
            CodeLineExtractor.CodeLine cl,
            float pageHeight,
            List<HighlightRects.RectF> hiRectsPdf
    ) {
        if (hiRectsPdf == null || hiRectsPdf.isEmpty()) return false;

        float codeMinX = cl.xMin;
        float codeMaxX = cl.xMax;

        float codeMinY = pageHeight - cl.yMax;
        float codeMaxY = pageHeight - cl.yMin;

        // 패딩
        float padX = 1.5f;
        float padY = 2.5f;
        codeMinX -= padX;
        codeMaxX += padX;
        codeMinY -= padY;
        codeMaxY += padY;

        float codeW = Math.max(1f, codeMaxX - codeMinX);
        float codeH = Math.max(1f, codeMaxY - codeMinY);

        for (HighlightRects.RectF r : hiRectsPdf) {
            float ox = Math.min(codeMaxX, r.maxX) - Math.max(codeMinX, r.minX);
            float oy = Math.min(codeMaxY, r.maxY) - Math.max(codeMinY, r.minY);
            if (ox <= 0 || oy <= 0) continue;

            float xRatio = ox / codeW;
            float yRatio = oy / codeH;

            if (xRatio >= 0.05f && yRatio >= 0.05f) return true;
        }
        return false;
    }
}
