package maoom.PDFImageExtractor;

import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import java.awt.geom.Point2D;

public class ImageExtractorWithBbox {

    public static class ImageHit {
        public final int page; // 1-based
        public final PDImageXObject image;
        public final PDRectangle bbox; // user space bbox
        public final int indexOnPage;

        public ImageHit(int page, PDImageXObject image, PDRectangle bbox, int indexOnPage) {
            this.page = page;
            this.image = image;
            this.bbox = bbox;
            this.indexOnPage = indexOnPage;
        }

        @Override
        public String toString() {
            return "ImageHit{page=" + page + ", bbox=" + bbox + ", idx=" + indexOnPage + "}";
        }
    }

    public List<ImageHit> findImagesWithBbox(PDDocument doc) throws IOException {
        List<ImageHit> hits = new ArrayList<>();

        int pageCount = doc.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage page = doc.getPage(i);
            int pageNumber = i + 1;

            ImageBboxEngine engine = new ImageBboxEngine(pageNumber, hits);
            engine.processPage(page);
        }

        return hits;
    }

    private static class ImageBboxEngine extends PDFStreamEngine {
        private final int pageNumber;
        private final List<ImageHit> out;
        private int indexOnPage = 0;

        ImageBboxEngine(int pageNumber, List<ImageHit> out) {
            this.pageNumber = pageNumber;
            this.out = out;

            //CTM / 그래픽 상태가 업데이트되도록 필수 operator 등록
            addOperator(new Concatenate(this));              // cm
            addOperator(new Save(this));                     // q
            addOperator(new Restore(this));                  // Q
            addOperator(new SetGraphicsStateParameters(this)); // gs
        }


        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            String opname = operator.getName();

            if ("Do".equals(opname)) {
                COSName name = (COSName) operands.get(0);
                PDXObject xobj = getResources().getXObject(name);

                if (xobj instanceof PDImageXObject img) {
                    // 현재 CTM으로 이미지가 그려지는 영역 계산
                	Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

                	Point2D.Float p0 = ctm.transformPoint(0, 0);
                	Point2D.Float p1 = ctm.transformPoint(1, 0);
                	Point2D.Float p2 = ctm.transformPoint(1, 1);
                	Point2D.Float p3 = ctm.transformPoint(0, 1);

                	float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
                	float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
                	float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
                	float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));

                	PDRectangle bbox = new PDRectangle(minX, minY, maxX - minX, maxY - minY);


                	indexOnPage++;
                	out.add(new ImageHit(pageNumber, img, bbox, indexOnPage));

                }
            }

            super.processOperator(operator, operands);
        }
    }
}
