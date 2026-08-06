package maoom.PDFImageExtractor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CodeImageMatcher {

    private static final boolean DEBUG =
            Boolean.getBoolean("artwork.request.debug");

    // 모드 구분 (로그에서 바로 보이게)
    public enum Mode { NORMAL, NOFLIP, FLIP }

    // 경계값 여유 (pt)
    // - ORJ048151 케이스: imgBottomY(250.194) vs codeY(250.245) → -0.051pt
    // - EPS=1.0이면 안전하게 후보로 통과됨
    private static final float EPS = 1.0f;

    // ===== NORMAL/NOFLIP에서 "옆 도안" 방지용 파라미터 =====
    // X 가중치 (너무 크면 X만 보고 뽑아서 오히려 틀어질 수 있음)
    private static final float X_WEIGHT = 0.60f;   // 0.3 ~ 1.0 추천
    // overlap이 있으면 점수에서 빼주는 보너스 (클수록 overlap 우선)
    private static final float OVERLAP_BONUS = 80f; // 40~120 튜닝
    // overlapRatio가 이 이상이면 "겹친다"로 판단
    private static final float MIN_OVERLAP_RATIO = 0.10f;

    public static class MatchResult {
        public final CodeLineExtractor.CodeLine codeLine;
        public final ImageExtractorWithBbox.ImageHit image;
        public final int imageIndex;
        public final Mode mode;
        public final int candidates;

        public MatchResult(CodeLineExtractor.CodeLine codeLine,
                           ImageExtractorWithBbox.ImageHit image,
                           int imageIndex,
                           Mode mode,
                           int candidates) {
            this.codeLine = codeLine;
            this.image = image;
            this.imageIndex = imageIndex;
            this.mode = mode;
            this.candidates = candidates;
        }
    }

    public static Set<Integer> newUsedIndexSet() {
        return new LinkedHashSet<>();
    }

    public static MatchResult matchOne(int page,
                                       float pageHeight,
                                       CodeLineExtractor.CodeLine cl,
                                       List<ImageExtractorWithBbox.ImageHit> pageImgs,
                                       Set<Integer> usedIdx) {

        float clYCenter = (cl.yMin + cl.yMax) / 2f;
        float codeCenterX = (cl.xMin + cl.xMax) / 2f;

        float codeY_flip   = pageHeight - clYCenter;
        float codeY_noflip = clYCenter;

        debug(
                "[DBG-BASE] page=" + page +
                " code=" + cl.code +
                " pageHeight=" + pageHeight +
                " yMin=" + cl.yMin +
                " yMax=" + cl.yMax +
                " clYCenter=" + clYCenter +
                " codeY_flip=" + codeY_flip +
                " codeY_noflip=" + codeY_noflip);

        // 실패 시 디버깅용: 후보를 가장 많이 만든 시도 기록
        MatchResult bestFail = null;

        // 1) NORMAL (뒤집기)
        MatchResult r1 = matchNormal(cl, codeY_flip, codeCenterX, Mode.NORMAL, pageImgs, usedIdx, page);
        if (r1.image != null) return r1;
        bestFail = betterFail(bestFail, r1);

        // 2) NOFLIP (안 뒤집기)
        MatchResult r2 = matchNormal(cl, codeY_noflip, codeCenterX, Mode.NOFLIP, pageImgs, usedIdx, page);
        if (r2.image != null) return r2;
        bestFail = betterFail(bestFail, r2);

        // 3) FLIP (bbox가 뒤집혀 들어오는 특수 케이스)
        MatchResult r3 = matchFlip(cl, pageHeight, codeY_flip, codeCenterX, pageImgs, usedIdx, page);
        if (r3.image != null) return r3;
        bestFail = betterFail(bestFail, r3);

        // 전부 실패: candidates라도 가장 나았던 걸 유지
        if (bestFail != null) {
            return new MatchResult(cl, null, -1, bestFail.mode, bestFail.candidates);
        }
        return new MatchResult(cl, null, -1, Mode.NORMAL, 0);
    }

    private static MatchResult betterFail(MatchResult a, MatchResult b) {
        if (a == null) return b;
        if (b == null) return a;
        return (b.candidates > a.candidates) ? b : a;
    }

    // -------------------------
    // NORMAL/NOFLIP 매칭 (수정본)
    // - 후보 조건: imgBottomY > codeY - EPS  (기존 유지)
    // - 선택 기준: dy + dx*X_WEIGHT - overlapBonus(겹치면 유리)
    //   => dy 동점이어도 X/overlap로 "옆 도안" 방지
    // -------------------------
    private static MatchResult matchNormal(CodeLineExtractor.CodeLine cl,
                                          float codeY,
                                          float codeCenterX,
                                          Mode mode,
                                          List<ImageExtractorWithBbox.ImageHit> pageImgs,
                                          Set<Integer> usedIdx,
                                          int page) {

        ImageExtractorWithBbox.ImageHit best = null;
        int bestIndex = -1;
        float bestScore = Float.MAX_VALUE;
        int candidates = 0;

        float codeXMin = cl.xMin;
        float codeXMax = cl.xMax;
        float codeW = Math.max(1f, codeXMax - codeXMin);

        for (int i = 0; i < pageImgs.size(); i++) {
            if (usedIdx.contains(i)) continue;

            ImageExtractorWithBbox.ImageHit img = pageImgs.get(i);

            float imgBottomY = img.bbox.getLowerLeftY();

            // 후보 탈락 원인 확인용 로그(기존 유지)
            debug(
                    "[DBG-IMG] page=" + page +
                    " mode=" + mode +
                    " code=" + cl.code +
                    " imgIdx=" + i +
                    " imgBottomY=" + imgBottomY +
                    " codeY=" + codeY +
                    " diff=" + (imgBottomY - codeY));

            // 기존 후보 조건 유지 (코드보다 위쪽 이미지만)
            if (imgBottomY <= codeY - EPS) continue;

            candidates++;

            // === dy ===
            float dy = imgBottomY - codeY; // 작을수록 좋음(0에 가까울수록)

            // === dx ===
            float imgCenterX = (img.bbox.getLowerLeftX() + img.bbox.getUpperRightX()) / 2f;
            float dx = Math.abs(imgCenterX - codeCenterX);

            // === overlap (코드 X범위와 이미지 X범위의 겹침 비율) ===
            float imgL = img.bbox.getLowerLeftX();
            float imgR = img.bbox.getUpperRightX();
            float overlapW = Math.min(codeXMax, imgR) - Math.max(codeXMin, imgL);
            if (overlapW < 0) overlapW = 0;

            float overlapRatio = overlapW / codeW;
            boolean isOverlapped = overlapRatio >= MIN_OVERLAP_RATIO;

            // === score (핵심) ===
            float score = dy + (dx * X_WEIGHT) - (isOverlapped ? (OVERLAP_BONUS * overlapRatio) : 0f);

            // 점수 로그(추적하기 쉽게 추가)
            debug(
                    "[DBG-SCORE] page=" + page +
                    " mode=" + mode +
                    " code=" + cl.code +
                    " imgIdx=" + i +
                    " dy=" + dy +
                    " dx=" + dx +
                    " overlapRatio=" + overlapRatio +
                    " score=" + score);

            if (score < bestScore) {
                bestScore = score;
                best = img;
                bestIndex = i;
            }
        }

        if (best != null) {
            return new MatchResult(cl, best, bestIndex, mode, candidates);
        }
        return new MatchResult(cl, null, -1, mode, candidates);
    }

    // -------------------------
    // FLIP 매칭: bbox 좌표계가 뒤집혀 들어오는 케이스 대비
    // - 기존 로직 유지(조금 더 안정적으로 overlapRatio 반영)
    // -------------------------
    private static MatchResult matchFlip(CodeLineExtractor.CodeLine cl,
                                         float pageHeight,
                                         float codeY,
                                         float codeCenterX,
                                         List<ImageExtractorWithBbox.ImageHit> pageImgs,
                                         Set<Integer> usedIdx,
                                         int page) {

        ImageExtractorWithBbox.ImageHit best = null;
        int bestIndex = -1;
        float bestScore = Float.MAX_VALUE;
        int candidates = 0;

        float codeXMin = cl.xMin;
        float codeXMax = cl.xMax;
        float codeW = Math.max(1f, codeXMax - codeXMin);

        for (int i = 0; i < pageImgs.size(); i++) {
            if (usedIdx.contains(i)) continue;

            ImageExtractorWithBbox.ImageHit img = pageImgs.get(i);

            // 뒤집힌 것처럼 계산한 bottomY
            float imgBottomY = pageHeight - img.bbox.getUpperRightY();

            debug(
                    "[DBG-FLIP] page=" + page +
                    " code=" + cl.code +
                    " imgIdx=" + i +
                    " imgBottomY(flip)=" + imgBottomY +
                    " codeY=" + codeY +
                    " diff=" + (imgBottomY - codeY));

            // EPS 동일 적용
            if (imgBottomY <= codeY - EPS) continue;

            float imgCenterX = (img.bbox.getLowerLeftX() + img.bbox.getUpperRightX()) / 2f;

            float dy = imgBottomY - codeY;
            float dx = Math.abs(imgCenterX - codeCenterX);

            float imgL = img.bbox.getLowerLeftX();
            float imgR = img.bbox.getUpperRightX();
            float overlapW = Math.min(codeXMax, imgR) - Math.max(codeXMin, imgL);
            if (overlapW < 0) overlapW = 0;

            float overlapRatio = overlapW / codeW;
            boolean isOverlapped = overlapRatio >= MIN_OVERLAP_RATIO;

            // 기존은 dx*0.15 - 50 고정이었는데, NORMAL과 동일한 개념으로 ratio 반영
            float score = dy + (dx * 0.15f) - (isOverlapped ? (50f * overlapRatio) : 0f);

            debug(
                    "[DBG-SCORE] page=" + page +
                    " mode=" + Mode.FLIP +
                    " code=" + cl.code +
                    " imgIdx=" + i +
                    " dy=" + dy +
                    " dx=" + dx +
                    " overlapRatio=" + overlapRatio +
                    " score=" + score);

            candidates++;
            if (score < bestScore) {
                bestScore = score;
                best = img;
                bestIndex = i;
            }
        }

        if (best != null) {
            return new MatchResult(cl, best, bestIndex, Mode.FLIP, candidates);
        }
        return new MatchResult(cl, null, -1, Mode.FLIP, candidates);
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }
}
