package maoom.PDFImageExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.util.List;

public record PageContext(
        PDDocument doc,
        int page,                 // 1-based
        PDPage pageObj,
        float pageHeight,
        List<ImageExtractorWithBbox.ImageHit> pageImgs,
        List<CodeLineExtractor.CodeLine> codeLines, // 텍스트 기반(비어있을 수 있음)
        List<HighlightRects.RectF> hiRectsPdf
) {}
