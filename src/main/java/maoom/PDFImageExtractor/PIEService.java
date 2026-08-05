package maoom.PDFImageExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.util.*;

public class PIEService {

    private final PdfLoader pdfLoader = new PdfLoader();

    // === 디버그 토글 ===
    private static final boolean DBG = true;
    private static final int DBG_PAGE = 37;

    public String exportExcel(String pdfPath, String outPath) throws Exception {
        System.out.println("[PIEService] exportExcel() start");

        Set<String> exportedCodes = new HashSet<>();

        if (outPath == null || outPath.trim().isEmpty()) {
            throw new IllegalArgumentException("[PIEService] outPath 비어있음");
        }
        System.out.println("[PIEService] outPath=" + outPath);

        List<ExcelExporter.RowData> excelRows = new ArrayList<>();

        File pdfFile = new File(pdfPath);
        File outDir = new File(pdfFile.getParentFile(), "png_by_code");

        boolean excelSaved = false;

        try (PDDocument doc = pdfLoader.load(pdfPath)) {
            int pageCount = doc.getNumberOfPages();
            System.out.println("[PIEService] PDF opened OK. pageCount=" + pageCount);

            // 1) 코드 라인 추출(텍스트 기반)
            Map<Integer, List<CodeLineExtractor.CodeLine>> codeLinesByPage =
                    CodeLineExtractor.extractCodeLinesByPage(doc);

            int codeLinePages = codeLinesByPage.size();
            int codeLineTotal = 0;
            for (List<CodeLineExtractor.CodeLine> v : codeLinesByPage.values()) codeLineTotal += v.size();
            System.out.println("[PIEService] codeLines pages=" + codeLinePages + ", totalLines=" + codeLineTotal);

            // 2) 이미지 인덱스 생성(추출+필터+페이지그룹)
            ImageIndex imageIndex = ImageIndex.build(doc);
            Map<Integer, List<ImageExtractorWithBbox.ImageHit>> imagesByPage = imageIndex.imagesByPage();

            // 디버그 덤퍼
            PageDebugDumper dumper = new PageDebugDumper(DBG, DBG_PAGE);

            // 출력 폴더 생성
            if (!outDir.exists()) outDir.mkdirs();
            System.out.println("[PIEService] png outDir=" + outDir.getAbsolutePath());

            //시작 디버그(원하면)
            if (DBG) {
                dumper.dumpNeighborStats(DBG_PAGE, 2, codeLinesByPage, imagesByPage);
                dumper.dumpPageBoxes(doc, DBG_PAGE);
                dumper.dumpPageText(doc, DBG_PAGE);
            }

            HighlightFallback fallback = new HighlightFallback();

            //전체 페이지 루프
            for (int page = 1; page <= pageCount; page++) {
                PDPage pageObj = doc.getPage(page - 1);
                float pageHeight = pageObj.getMediaBox().getHeight();

                List<ImageExtractorWithBbox.ImageHit> pageImgs = imagesByPage.getOrDefault(page, Collections.emptyList());
                List<CodeLineExtractor.CodeLine> codeLines = codeLinesByPage.getOrDefault(page, Collections.emptyList());

                System.out.println("[PIEService] page=" + page + " codeLines=" + codeLines.size() + " images=" + pageImgs.size());

                if (pageImgs.isEmpty()) continue; // 이미지 없으면 매칭 불가

                // 페이지별 하이라이트 rect (PDF 좌표)
                List<HighlightRects.RectF> hiRectsPdf = HighlightRects.extractHighlightRectsPdf(pageObj);
                if (DBG && page == DBG_PAGE) dumper.dumpHiRects(page, hiRectsPdf);

                if (hiRectsPdf == null || hiRectsPdf.isEmpty()) {
                    System.out.println("[HI] page=" + page + " no highlight -> skip page");
                    continue;
                }

                // 컨텍스트 생성
                PageContext ctx = new PageContext(doc, page, pageObj, pageHeight, pageImgs, codeLines, hiRectsPdf);

                // 같은 페이지에서 이미 저장한 이미지 재사용 금지
                Set<Integer> usedIdx = CodeImageMatcher.newUsedIndexSet();

                // 5-A) 텍스트 기반 코드라인이 있으면 그대로 처리
                if (!codeLines.isEmpty()) {
                    MatchAndExport.processTextCodeLines(ctx, usedIdx, exportedCodes, outDir, excelRows);
                    continue;
                }

                // 5-B) 코드라인이 0개면 fallback으로 코드라인 만들기
                List<CodeLineExtractor.CodeLine> fallbackLines =
                        fallback.resolveFromHighlightOrNeighbor(ctx, codeLinesByPage, 2);

                if (DBG && page == DBG_PAGE) dumper.dumpFallbackLines(page, fallbackLines);

                if (fallbackLines.isEmpty()) {
                    System.out.println("[HI] page=" + page + " highlight exists but no text codes in highlight area -> skip");
                    continue;
                }

                MatchAndExport.processFallbackCodeLines(ctx, fallbackLines, usedIdx, exportedCodes, outDir, excelRows);
            }

            // 6) 엑셀 생성
            System.out.println("[PIEService] excel rows=" + excelRows.size());
            ExcelExporter.exportFromTemplate(outPath, excelRows);
            excelSaved = true;

            return outPath;

        } finally {
            if (excelSaved) deleteDirectory(outDir);
            System.out.println("[PIEService] exportExcel() end");
        }
    }

    // ===== 폴더 삭제 =====
    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    // 연결 확인용 (유지)
    public void testPdfConnection(String pdfPath) throws Exception {
        System.out.println("[PIEService] testPdfConnection() start");

        PDDocument doc = null;
        try {
            doc = pdfLoader.load(pdfPath);
            System.out.println("[PIEService] PdfLoader 연결 성공 (pageCount=" + doc.getNumberOfPages() + ")");
        } finally {
            if (doc != null) {
                doc.close();
                System.out.println("[PIEService] PDF closed");
            }
        }

        System.out.println("[PIEService] testPdfConnection() end");
    }
}
