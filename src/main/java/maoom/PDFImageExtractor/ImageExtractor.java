package maoom.PDFImageExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * ImageExtractor
 * - "실제로 그려진" 이미지들만 수집
 */
public class ImageExtractor {

    public List<PDImageXObject> findDrawnImages(PDDocument doc) throws IOException {
        System.out.println("[ImageExtractor] findDrawnImages() start");

        List<PDImageXObject> result = new ArrayList<>();

        int pageCount = doc.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage page = doc.getPage(i);
            int pageNo = i + 1;

            DrawnImageEngine engine = new DrawnImageEngine(result);
            engine.processPage(page);

            System.out.println("[ImageExtractor] page " + pageNo + " drawnImages=" + engine.countOnPage);
        }

        System.out.println("[ImageExtractor] findDrawnImages() end. total=" + result.size());
        return result;
    }

    /** 콘텐츠 스트림에서 Do 연산자만 추적 */
    private static class DrawnImageEngine extends PDFStreamEngine {

        private final List<PDImageXObject> out;
        private int countOnPage = 0;

        DrawnImageEngine(List<PDImageXObject> out) {
            this.out = out;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName())) {
                COSName objectName = (COSName) operands.get(0);
                PDXObject xobj = getResources().getXObject(objectName);

                if (xobj instanceof PDImageXObject img) {
                    out.add(img);
                    countOnPage++;
                }
                else if (xobj instanceof PDFormXObject form) {
                    //PDFBox가 알아서 내부 stream + resources 처리
                    showForm(form);
                }
            }
            super.processOperator(operator, operands);
        }
    }
}
