package maoom.PDFImageExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 작은 인라인 이미지(아이콘)처럼 보이는 이미지들을 필터링하는 유틸.
 * - PDF 상 bbox 크기(포인트)
 * - 원본 픽셀 크기
 */
public class InlineImageFilter {

    // ===== 작은 인라인 이미지(아이콘) 필터 기준 =====
    private static final float MIN_BBOX_W_PT = 80f;  // PDF 상 그려진 너비(포인트)
    private static final float MIN_BBOX_H_PT = 80f;  // PDF 상 그려진 높이(포인트)
    private static final int MIN_IMG_W_PX   = 150;   // 원본 픽셀 너비
    private static final int MIN_IMG_H_PX   = 150;   // 원본 픽셀 높이

    public static boolean isInlineLike(ImageExtractorWithBbox.ImageHit hit) {
        if (hit == null || hit.image == null || hit.bbox == null) return true;

        float bw = hit.bbox.getWidth();
        float bh = hit.bbox.getHeight();

        int pw = hit.image.getWidth();
        int ph = hit.image.getHeight();

        //PDF에 그려진 크기가 너무 작으면 제외
        if (bw < MIN_BBOX_W_PT && bh < MIN_BBOX_H_PT) return true;

        //원본 픽셀도 너무 작으면 제외
        if (pw < MIN_IMG_W_PX && ph < MIN_IMG_H_PX) return true;

        return false;
    }

    public static List<ImageExtractorWithBbox.ImageHit> filter(List<ImageExtractorWithBbox.ImageHit> all) {
        List<ImageExtractorWithBbox.ImageHit> out = new ArrayList<>();
        if (all == null) return out;
        for (ImageExtractorWithBbox.ImageHit hit : all) {
            if (!isInlineLike(hit)) out.add(hit);
        }
        return out;
    }
}
