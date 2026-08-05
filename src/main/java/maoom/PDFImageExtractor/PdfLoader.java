package maoom.PDFImageExtractor;

import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * PdfLoader
 * - PDF 여는 역할만 담당
 */
public class PdfLoader {

    public PDDocument load(String pdfPath) throws IOException {
        System.out.println("[PdfLoader] load() called");

        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("[PdfLoader] pdfPath 비어있음");
        }

        File f = new File(pdfPath.trim());
        if (!f.exists() || !f.isFile()) {
            throw new IllegalArgumentException("[PdfLoader] PDF 파일 없음: " + f.getAbsolutePath());
        }

        System.out.println("[PdfLoader] PDF file OK: " + f.getAbsolutePath());

        PDDocument doc = Loader.loadPDF(f);
        System.out.println("[PdfLoader] PDF loaded. pageCount=" + doc.getNumberOfPages());

        return doc;
    }
}
