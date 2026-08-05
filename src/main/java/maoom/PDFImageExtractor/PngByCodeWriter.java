package maoom.PDFImageExtractor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * 매칭된 이미지를 PNG로 저장하는 책임만 분리.
 */
public class PngByCodeWriter {

    public static File buildPngFile(File outDir, int page, String code, int ordinal) {
    	String safe = (code == null ? "UNKNOWN" : code)
    	        .replaceAll("[^A-Za-z0-9_-]", "_");

        // 같은 페이지에 동일 코드가 여러 번 나올 수도 있어서 ordinal을 옵션으로 붙일 수 있게 해둠.
        // ordinal==0이면 기존처럼 p{page}_{code}.png
        String name = (ordinal <= 0)
                ? ("p" + page + "_" + safe + ".png")
                : ("p" + page + "_" + safe + "_" + ordinal + ".png");
        return new File(outDir, name);
    }

    public static void write(ImageExtractorWithBbox.ImageHit hit, File pngFile) throws IOException {
        BufferedImage bimg = hit.image.getImage();
        ImageIO.write(bimg, "png", pngFile);
    }
}
