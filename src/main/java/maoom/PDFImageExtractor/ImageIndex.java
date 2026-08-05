package maoom.PDFImageExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.*;

public class ImageIndex {

    private final List<ImageExtractorWithBbox.ImageHit> allImages;
    private final List<ImageExtractorWithBbox.ImageHit> filteredImages;
    private final Map<Integer, List<ImageExtractorWithBbox.ImageHit>> imagesByPage;

    private ImageIndex(List<ImageExtractorWithBbox.ImageHit> allImages,
                       List<ImageExtractorWithBbox.ImageHit> filteredImages,
                       Map<Integer, List<ImageExtractorWithBbox.ImageHit>> imagesByPage) {
        this.allImages = allImages;
        this.filteredImages = filteredImages;
        this.imagesByPage = imagesByPage;
    }

    public static ImageIndex build(PDDocument doc) throws Exception {
        ImageExtractorWithBbox ex = new ImageExtractorWithBbox();
        List<ImageExtractorWithBbox.ImageHit> allImages = ex.findImagesWithBbox(doc);
        System.out.println("[PIEService] images (with bbox) total=" + allImages.size());

        int before = allImages.size();
        List<ImageExtractorWithBbox.ImageHit> filtered = InlineImageFilter.filter(allImages);
        System.out.println("[PIEService] images (with bbox) total=" + before
                + ", filtered=" + filtered.size()
                + ", skipped(inline-like)=" + (before - filtered.size()));

        Map<Integer, List<ImageExtractorWithBbox.ImageHit>> byPage = new HashMap<>();
        for (ImageExtractorWithBbox.ImageHit hit : filtered) {
            byPage.computeIfAbsent(hit.page, k -> new ArrayList<>()).add(hit);
        }

        return new ImageIndex(allImages, filtered, byPage);
    }

    public Map<Integer, List<ImageExtractorWithBbox.ImageHit>> imagesByPage() {
        return imagesByPage;
    }

    public List<ImageExtractorWithBbox.ImageHit> allImages() {
        return allImages;
    }

    public List<ImageExtractorWithBbox.ImageHit> filteredImages() {
        return filteredImages;
    }
}
