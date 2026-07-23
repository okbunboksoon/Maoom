package maoomWeb.ire.user.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import maoomWeb.ire.user.dto.DrawingColorCheckDto;
import maoomWeb.ire.user.mapper.DrawingColorCheckMapper;

/**
 * PDF를 읽어 도안 목록과 도안 이미지를 컬러체크용 엑셀로 만드는 핵심 서비스.
 *
 * <p>이 클래스의 전체 처리 순서는 다음과 같다.</p>
 * <ol>
 *   <li>PDFBox로 각 페이지의 글자와 글자 좌표를 읽는다.</li>
 *   <li>작은 글씨로 표시된 도안 ID를 찾고 일반 표 데이터는 제외한다.</li>
 *   <li>도안 ID 바로 위의 실제 PDF 이미지 객체를 찾아 개별 도안만 자른다.</li>
 *   <li>동일한 도안명이 다시 나오면 첫 번째 항목만 남긴다.</li>
 *   <li>DB에서 도안별 V/X 값을 조회해 '컬러도안' 열에 합친다.</li>
 *   <li>Apache POI로 표와 이미지를 포함한 XLSX를 만든다.</li>
 * </ol>
 *
 * <p>PDF 분석과 엑셀 생성만 담당하며, 파일을 바탕화면에 저장하는 일은
 * {@code ColorCheckController}가 담당한다.</p>
 */
@Service
public class ColorCheckExportService {

    /** 사용자에게 제공하는 엑셀 열 순서. 기준 엑셀 양식과 동일해야 한다. */
    private static final String[] HEADERS = {
            "도안명", "레드도안", "컬러도안",
            "챕터 구별", "챕터 넘버", "이미지"
    };

    /**
     * 도안 ID의 공통 모양을 확인하는 정규식.
     * 영문자로 시작하고 숫자를 하나 이상 포함하며 영문/숫자/밑줄만 허용한다.
     * N_, OCL, OMQ, KIA 등 특정 접두어로 제한하지 않는다.
     */
    private static final Pattern DRAWING_NAME_PATTERN =
            Pattern.compile(
                    "(?i)^(?=[A-Z0-9_]*[0-9])"
                    + "[A-Z][A-Z0-9_]{6,}$");

    /** "Chapter 4 Door controls" 형식의 영문 챕터 제목을 읽는다. */
    private static final Pattern ENGLISH_CHAPTER_PATTERN =
            Pattern.compile(
                    "(?i)^(?:chapter|chap\\.?|ch\\.?)\\s*"
                    + "([0-9]+(?:[.-][0-9]+)*)"
                    + "\\s*[:.\\-]?\\s*(.*)$");
    /** "제 4장 ..." 형식의 한글 챕터 제목을 읽는다. */
    private static final Pattern KOREAN_CHAPTER_PATTERN =
            Pattern.compile(
                    "^제?\\s*([0-9]+(?:[.-][0-9]+)*)\\s*장"
                    + "\\s*[:.\\-]?\\s*(.*)$");
    /** "7 Hybrid vehicle guide"처럼 Chapter 단어 없이 시작하는 제목을 읽는다. */
    private static final Pattern NUMBERED_CHAPTER_PATTERN =
            Pattern.compile(
                    "^([0-9]+(?:[.-][0-9]+)*)"
                    + "\\s+(.+)$");
    /** PDF 북마크의 "7 Controls and Features" 같은 최상위 챕터를 읽는다. */
    private static final Pattern OUTLINE_CHAPTER_PATTERN =
            Pattern.compile(
                    "^([0-9]+|[A-Z])"
                    + "\\s+(.+)$");
    /*
     * PDF 렌더링/엑셀 이미지 설정.
     * SOURCE 크기는 엑셀 안에 저장할 PNG 해상도이고,
     * DISPLAY 크기는 엑셀에서 사용자가 실제로 보는 셀 크기다.
     * 저장 해상도를 표시 크기보다 크게 해 확대해도 덜 흐려지게 한다.
     */
    private static final float RENDER_DPI = 180f;
    private static final int HEADER_ROW = 2;
    private static final int FIRST_DATA_ROW = 3;
    private static final int IMAGE_COLUMN = 5;
    private static final int IMAGE_SOURCE_WIDTH_PX = 640;
    private static final int IMAGE_SOURCE_HEIGHT_PX = 440;
    private static final int IMAGE_DISPLAY_WIDTH_PX = 320;
    private static final int IMAGE_DISPLAY_HEIGHT_PX = 220;
    /**
     * 실제 도안 ID는 이 PDF에서 5pt로 작성되어 있다.
     * 퓨즈 표의 MEMORY1/MODULE9 같은 7pt 문자열을 도안명으로 오인하지 않도록
     * 글꼴 크기 상한을 둔다.
     */
    private static final float MAX_DRAWING_NAME_FONT_SIZE = 5.5f;

    /** 영문·한글 매뉴얼에서 챕터 제목으로 인정할 표준 제목과 별칭. */
    private static final Map<String,String> CHAPTER_TITLES =
            createChapterTitleMap();
    /** 번호가 없는 표준 챕터 제목만 추출됐을 때 사용하는 기본 챕터 번호. */
    private static final Map<String,String> CHAPTER_NUMBERS =
            createChapterNumberMap();

    /** 엑셀 생성 시 기존 DB의 V/X 값을 한 번에 조회하는 MyBatis 매퍼. */
    private final DrawingColorCheckMapper colorCheckMapper;

    public ColorCheckExportService(
            DrawingColorCheckMapper colorCheckMapper) {
        this.colorCheckMapper = colorCheckMapper;
    }

    /**
     * 업로드된 PDF 전체를 분석해 완성된 XLSX 파일 바이트를 반환한다.
     *
     * @param pdfBytes 브라우저에서 업로드한 PDF 파일 내용
     * @return 컨트롤러가 파일로 저장하거나 응답으로 보낼 수 있는 XLSX 내용
     * @throws IOException PDF를 읽거나 엑셀을 쓰는 중 파일 처리에 실패한 경우
     */
    public byte[] createWorkbook(byte[] pdfBytes) throws IOException {

        if(pdfBytes == null || pdfBytes.length == 0){
            throw new IllegalArgumentException("PDF 파일이 비어 있습니다.");
        }

        try(PDDocument document = Loader.loadPDF(pdfBytes);
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()){

            // 1단계: PDF에서 도안명, 챕터, 개별 이미지를 수집한다.
            List<DrawingEntry> entries = extractEntries(document);

            if(entries.isEmpty()){
                throw new IllegalArgumentException(
                        "PDF에서 도안명을 찾지 못했습니다.");
            }

            /*
             * 번호는 PDF에서 챕터가 처음 감지된 순서로 부여된다.
             * 안정 정렬이므로 같은 챕터 안의 도안은 PDF 이미지 등장 순서를
             * 그대로 유지한다.
             */
            entries.sort(
                    Comparator.comparingInt(
                            entry -> chapterSortOrder(
                                    entry.chapterNumber())));

            // 2단계: 수집된 도안명으로 DB를 조회해 V/X 값을 합친다.
            entries = applyColorChecks(entries);

            // 3단계: 최종 데이터를 화면에서 사용할 엑셀 양식으로 작성한다.
            writeSheet(workbook, entries);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /**
     * PDF에서 찾은 도안명과 DB의 V/X 데이터를 결합한다.
     *
     * <p>도안명을 한 건씩 조회하지 않고 한 번의 IN 쿼리로 가져와
     * 대용량 PDF에서도 DB 호출 횟수가 늘어나지 않게 한다.</p>
     */
    private List<DrawingEntry> applyColorChecks(
            List<DrawingEntry> entries) {

        List<String> drawingNames = entries.stream()
                .map(DrawingEntry::drawingName)
                .distinct()
                .toList();
        // 대소문자와 앞뒤 공백 차이를 없앤 도안명을 Map의 키로 사용한다.
        Map<String,DrawingColorCheckDto> checks =
                colorCheckMapper.findByDrawingNames(drawingNames)
                .stream()
                .collect(Collectors.toMap(
                        check -> canonicalDrawingName(
                                check.getDrawingName()),
                        Function.identity(),
                        (left, right) -> left));

        return entries.stream()
                .map(entry -> {
                    DrawingColorCheckDto check =
                            checks.get(canonicalDrawingName(
                                    entry.drawingName()));
                    String checkValue = normalizeCheckValue(
                            check == null
                                    ? null
                                    : check.getCheckValue());
                    return new DrawingEntry(
                            entry.drawingName(),
                            checkValue,
                            entry.chapter(),
                            entry.chapterNumber(),
                            entry.image());
                })
                .toList();
    }

    /**
     * PDF를 앞 페이지부터 순서대로 읽어 엑셀 한 행에 해당하는 데이터를 만든다.
     *
     * <p>{@code currentChapter}는 챕터 표지가 나온 뒤 다음 챕터가 발견될 때까지
     * 유지된다. 따라서 도안 페이지에 챕터 제목이 없더라도 직전 챕터 정보를
     * 사용할 수 있다.</p>
     */
    private List<DrawingEntry> extractEntries(
            PDDocument document) throws IOException {

        PDFRenderer renderer = new PDFRenderer(document);
        List<DrawingEntry> entries = new ArrayList<>();
        // 입력 순서를 보존하는 Set으로 첫 도안만 남기고 이후 중복을 제거한다.
        LinkedHashSet<String> seenDrawingNames =
                new LinkedHashSet<>();
        ChapterInfo currentChapter = ChapterInfo.EMPTY;
        NavigableMap<Integer,ChapterInfo> outlineChapters =
                extractOutlineChapters(document);

        for(int pageIndex = 0;
                pageIndex < document.getNumberOfPages();
                pageIndex++){

            PDPage page = document.getPage(pageIndex);
            // PDFTextStripper를 확장해 글자뿐 아니라 x/y 좌표와 글꼴 크기도 얻는다.
            PositionedTextStripper stripper =
                    new PositionedTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(document);

            List<TextLine> lines = stripper.getLines()
                    .stream()
                    .filter(line -> !line.text().isBlank())
                    .sorted(Comparator.comparing(TextLine::y))
                    .toList();
            // 일반 본문과 표 셀은 제외하고 도안 ID로 판단되는 줄만 남긴다.
            List<TextLine> drawingLines = lines.stream()
                    .filter(this::isDrawingNameLine)
                    .toList();
            // PDF 안에 실제로 배치된 이미지 객체의 좌표를 미리 수집한다.
            List<ImageRegion> imageRegions =
                    extractImageRegions(page);

            ChapterInfo pageChapter =
                    findOutlineChapterInfo(
                            outlineChapters,
                            pageIndex);

            if(pageChapter.isEmpty()){
                pageChapter = findChapterInfo(lines);
            }

            if(!pageChapter.isEmpty()){
                currentChapter = pageChapter;
            }

            if(drawingLines.isEmpty()){
                continue;
            }

            // 도안이 있는 페이지에 한해서만 렌더링해 불필요한 작업을 줄인다.
            BufferedImage pageImage =
                    renderer.renderImageWithDPI(
                            pageIndex,
                            RENDER_DPI,
                            ImageType.RGB);

            for(TextLine line : drawingLines){
                String drawingName = normalizeDrawingName(
                        line.text());
                String canonicalName =
                        canonicalDrawingName(drawingName);

                // add가 false면 앞 페이지에서 이미 나온 도안이므로 엑셀에서 제외한다.
                if(!seenDrawingNames.add(canonicalName)){
                    continue;
                }

                byte[] image = renderDrawingImage(
                        pageImage,
                        page,
                        line,
                        drawingLines,
                        imageRegions);
                entries.add(new DrawingEntry(
                        drawingName,
                        "",
                        currentChapter.title(),
                        currentChapter.number(),
                        image));
            }
        }

        return entries;
    }

    /**
     * PDF 북마크의 최상위 항목을 실제 챕터 범위로 사용한다.
     *
     * <p>차종/사양별로 중간 챕터가 빠지면 고정 매핑 번호가 밀릴 수 있으므로,
     * 북마크에 있는 번호와 제목을 우선한다.</p>
     */
    private NavigableMap<Integer,ChapterInfo> extractOutlineChapters(
            PDDocument document) throws IOException {

        NavigableMap<Integer,ChapterInfo> chapters =
                new TreeMap<>();
        PDDocumentOutline outline =
                document.getDocumentCatalog()
                .getDocumentOutline();

        if(outline == null){
            return chapters;
        }

        for(PDOutlineItem item : outline.children()){
            ChapterInfo chapterInfo =
                    recognizeOutlineChapterInfo(
                            item.getTitle());

            if(chapterInfo.isEmpty()){
                continue;
            }

            PDPage destinationPage =
                    item.findDestinationPage(document);

            if(destinationPage == null){
                continue;
            }

            int pageIndex =
                    document.getPages()
                    .indexOf(destinationPage);

            if(pageIndex >= 0){
                chapters.put(pageIndex, chapterInfo);
            }
        }

        return chapters;
    }

    private ChapterInfo findOutlineChapterInfo(
            NavigableMap<Integer,ChapterInfo> outlineChapters,
            int pageIndex) {

        if(outlineChapters.isEmpty()){
            return ChapterInfo.EMPTY;
        }

        Map.Entry<Integer,ChapterInfo> entry =
                outlineChapters.floorEntry(pageIndex);
        return entry == null
                ? ChapterInfo.EMPTY
                : entry.getValue();
    }

    /**
     * PDF 페이지의 이미지 명령을 읽어 각 이미지가 놓인 사각형 좌표를 반환한다.
     *
     * <p>회전된 페이지는 PDF 좌표와 렌더링 좌표 변환이 달라질 수 있으므로
     * 안전하게 빈 목록을 반환하고 아래의 픽셀 기반 보조 방식으로 처리한다.</p>
     */
    private List<ImageRegion> extractImageRegions(
            PDPage page) throws IOException {

        if(page.getRotation() != 0){
            return List.of();
        }

        ImageRegionScanner scanner =
                new ImageRegionScanner(page);
        scanner.processPage(page);
        return scanner.getRegions();
    }

    /**
     * 한 줄의 텍스트가 실제 도안 ID인지 판단한다.
     *
     * <p>형식만 보면 MEMORY1 같은 표 셀도 도안처럼 보일 수 있다.
     * 그래서 공백 여부, 글꼴 크기, 문자열 모양을 함께 확인한다.</p>
     */
    private boolean isDrawingNameLine(TextLine line) {

        if(line.text().matches(".*\\s+.*")){
            return false;
        }

        if(line.fontSize() > MAX_DRAWING_NAME_FONT_SIZE){
            return false;
        }

        return DRAWING_NAME_PATTERN.matcher(
                normalizeDrawingName(line.text()))
                .matches();
    }

    /** 현재 페이지에서 영문 또는 한글 챕터 제목을 찾는다. */
    private ChapterInfo findChapterInfo(List<TextLine> lines) {

        for(TextLine line : lines){
            ChapterInfo chapterInfo =
                    recognizeChapterInfo(
                            line.text());

            if(!chapterInfo.isEmpty()){
                return chapterInfo;
            }
        }

        return ChapterInfo.EMPTY;
    }

    /**
     * 한 줄이 챕터 제목이면 표시할 제목을 반환한다.
     * 테스트에서 영문·한글 제목 인식을 직접 검증할 수 있도록 패키지 범위로 둔다.
     */
    String recognizeChapterTitle(String value) {
        return recognizeChapterInfo(value).title();
    }

    /**
     * 한 줄이 챕터 제목이면 표시할 제목과 PDF 원문 챕터 번호를 반환한다.
     */
    ChapterInfo recognizeChapterInfo(String value) {
        String text = value == null
                ? ""
                : value.trim()
                .replaceAll("\\s+", " ");

        if(text.isBlank()){
            return ChapterInfo.EMPTY;
        }

        Matcher english =
                ENGLISH_CHAPTER_PATTERN.matcher(text);

        if(english.matches()){
            String title = english.group(2).trim();
            return title.isBlank()
                    ? ChapterInfo.EMPTY
                    : new ChapterInfo(
                            CHAPTER_TITLES.getOrDefault(
                                    canonicalChapterTitle(title),
                                    title),
                            english.group(1));
        }

        Matcher korean =
                KOREAN_CHAPTER_PATTERN.matcher(text);

        if(korean.matches()){
            String title = korean.group(2).trim();
            return title.isBlank()
                    ? ChapterInfo.EMPTY
                    : new ChapterInfo(
                            CHAPTER_TITLES.getOrDefault(
                                    canonicalChapterTitle(title),
                            title),
                            korean.group(1));
        }

        Matcher numbered =
                NUMBERED_CHAPTER_PATTERN.matcher(text);

        if(numbered.matches()){
            String title = numbered.group(2).trim();
            String knownNumberedTitle = CHAPTER_TITLES.get(
                    canonicalChapterTitle(title));

            if(knownNumberedTitle != null){
                return new ChapterInfo(
                        knownNumberedTitle,
                        numbered.group(1));
            }
        }

        String knownTitle = CHAPTER_TITLES.get(
                canonicalChapterTitle(text));

        if(knownTitle != null){
            return new ChapterInfo(
                    knownTitle,
                    CHAPTER_NUMBERS.getOrDefault(
                            canonicalChapterTitle(knownTitle),
                            ""));
        }

        return ChapterInfo.EMPTY;
    }

    private ChapterInfo recognizeOutlineChapterInfo(String value) {
        String text = value == null
                ? ""
                : value.trim()
                .replaceAll("\\s+", " ");

        if(text.isBlank()){
            return ChapterInfo.EMPTY;
        }

        Matcher outline =
                OUTLINE_CHAPTER_PATTERN.matcher(text);

        if(outline.matches()){
            String title = outline.group(2).trim();
            return title.isBlank()
                    ? ChapterInfo.EMPTY
                    : new ChapterInfo(
                            CHAPTER_TITLES.getOrDefault(
                                    canonicalChapterTitle(title),
                                    title),
                            outline.group(1));
        }

        return recognizeChapterInfo(text);
    }

    private String canonicalChapterTitle(String title) {
        return title.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 페이지 렌더링 결과에서 한 도안만 잘라 PNG 바이트로 만든다.
     * 도안 ID 아래쪽까지 포함하여 이미지와 도안명을 함께 확인할 수 있게 한다.
     */
    private byte[] renderDrawingImage(
            BufferedImage pageImage,
            PDPage page,
            TextLine drawingNameLine,
            List<TextLine> drawingLines,
            List<ImageRegion> imageRegions) throws IOException {

        float scale = RENDER_DPI / 72f;
        CropBounds bounds = findDrawingBounds(
                pageImage,
                page,
                drawingNameLine,
                drawingLines,
                imageRegions,
                scale);

        BufferedImage drawingImage = pageImage.getSubimage(
                bounds.left(),
                bounds.top(),
                bounds.width(),
                bounds.height());
        drawingImage = resizeForExcel(drawingImage);
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        ImageIO.write(drawingImage, "png", output);
        return output.toByteArray();
    }

    /**
     * 개별 도안을 자를 사각형을 찾는다.
     *
     * <p>우선 PDF 내부의 실제 이미지 객체 좌표를 사용한다. 이미지 객체를
     * 찾지 못하는 특수 페이지만 픽셀의 흰 여백을 검사하는 보조 로직을 사용한다.</p>
     */
    private CropBounds findDrawingBounds(
            BufferedImage image,
            PDPage page,
            TextLine label,
            List<TextLine> drawingLines,
            List<ImageRegion> imageRegions,
            float scale) {

        CropBounds imageObjectBounds =
                findImageObjectBounds(
                        image,
                        page,
                        label,
                        imageRegions,
                        scale);

        if(imageObjectBounds != null){
            return imageObjectBounds;
        }

        int labelCenter = Math.round(
                (label.x() + label.width() / 2f
                - page.getCropBox().getLowerLeftX())
                * scale);
        int labelTop = Math.round(label.y() * scale);
        int labelBottom = Math.round(
                (label.y() + label.height() + 3f)
                * scale);
        int[] horizontalLimits = findHorizontalLimits(
                image.getWidth(),
                page,
                label,
                drawingLines,
                scale);
        int probeHalfWidth = Math.max(
                24,
                Math.round(label.width() * scale));
        int probeLeft = Math.max(
                horizontalLimits[0],
                labelCenter - probeHalfWidth);
        int probeRight = Math.min(
                horizontalLimits[1],
                labelCenter + probeHalfWidth);
        int cropBottom = Math.min(
                image.getHeight(),
                labelBottom + 4);
        int cropTop = findContentTop(
                image,
                probeLeft,
                probeRight,
                Math.min(image.getHeight() - 1, cropBottom - 1),
                labelTop);
        int horizontalScanBottom = Math.max(
                cropTop + 1,
                labelTop - 12);
        int cropLeft = findContentLeft(
                image,
                horizontalLimits[0],
                labelCenter,
                cropTop,
                horizontalScanBottom);
        int cropRight = findContentRight(
                image,
                labelCenter,
                horizontalLimits[1],
                cropTop,
                horizontalScanBottom);

        cropLeft = Math.max(0, cropLeft - 5);
        cropRight = Math.min(image.getWidth(), cropRight + 5);
        cropTop = Math.max(0, cropTop - 5);

        if(cropRight <= cropLeft || cropBottom <= cropTop){
            return new CropBounds(
                    horizontalLimits[0],
                    0,
                    horizontalLimits[1]
                            - horizontalLimits[0],
                    Math.max(1, cropBottom));
        }

        return new CropBounds(
                cropLeft,
                cropTop,
                cropRight - cropLeft,
                cropBottom - cropTop);
    }

    /**
     * 도안명과 가장 가까운 PDF 이미지 객체를 선택해 정확한 크롭 영역을 만든다.
     * 이 방식은 이미지 오른쪽의 설명 문장을 함께 캡처하는 문제를 막는다.
     */
    private CropBounds findImageObjectBounds(
            BufferedImage renderedPage,
            PDPage page,
            TextLine label,
            List<ImageRegion> imageRegions,
            float scale) {

        float labelCenter = label.x()
                + label.width() / 2f;
        ImageRegion match = imageRegions.stream()
                .filter(region -> region.width() >= 40f
                        && region.height() >= 40f)
                .filter(region -> region.bottom()
                        <= label.y() + 12f)
                .filter(region -> horizontalDistance(
                        labelCenter,
                        region) <= Math.max(
                                60f,
                                region.width() * 1.5f))
                .min(Comparator.comparingDouble(
                        region -> Math.abs(
                                label.y()
                                - region.bottom())
                                + horizontalDistance(
                                        labelCenter,
                                        region)))
                .orElse(null);

        if(match == null
                || Math.abs(label.y() - match.bottom())
                        > 160f){
            return null;
        }

        float leftPoint = Math.min(
                match.left(),
                label.x()) - 3f;
        float rightPoint = Math.max(
                match.right(),
                label.x() + label.width()) + 3f;
        float topPoint = Math.max(
                match.top() - 3f,
                label.y() - 240f);
        float bottomPoint = label.y()
                + label.height() + 1f;
        int left = clamp(
                Math.round(leftPoint * scale),
                0,
                renderedPage.getWidth() - 1);
        int top = clamp(
                Math.round(topPoint * scale),
                0,
                renderedPage.getHeight() - 1);
        int right = clamp(
                Math.round(rightPoint * scale),
                left + 1,
                renderedPage.getWidth());
        int bottom = clamp(
                Math.round(bottomPoint * scale),
                top + 1,
                renderedPage.getHeight());
        return new CropBounds(
                left,
                top,
                right - left,
                bottom - top);
    }

    private float horizontalDistance(
            float x,
            ImageRegion region) {

        if(x < region.left()){
            return region.left() - x;
        }

        if(x > region.right()){
            return x - region.right();
        }

        return 0f;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 같은 높이에 도안이 여러 개 있을 때 각 도안이 사용할 좌우 구역을 나눈다.
     * 인접한 도안명 중심점의 중간 위치를 경계로 사용한다.
     */
    private int[] findHorizontalLimits(
            int imageWidth,
            PDPage page,
            TextLine current,
            List<TextLine> drawingLines,
            float scale) {

        float currentCenter = current.x()
                + current.width() / 2f;
        List<Float> sameRowCenters = drawingLines.stream()
                .filter(line -> Math.abs(
                        line.y() - current.y()) < 20f)
                .map(line -> line.x()
                        + line.width() / 2f)
                .sorted()
                .toList();
        int position = sameRowCenters.indexOf(
                sameRowCenters.stream()
                        .min(Comparator.comparingDouble(
                                center -> Math.abs(
                                        center - currentCenter)))
                        .orElse(currentCenter));
        float leftPoint;
        float rightPoint;

        if(sameRowCenters.size() <= 1){
            leftPoint = page.getCropBox().getLowerLeftX();
            rightPoint = page.getCropBox().getUpperRightX();
        }else{
            float center = sameRowCenters.get(position);
            float leftDistance = position > 0
                    ? center - sameRowCenters.get(position - 1)
                    : sameRowCenters.get(1) - center;
            float rightDistance =
                    position < sameRowCenters.size() - 1
                    ? sameRowCenters.get(position + 1) - center
                    : center - sameRowCenters.get(position - 1);
            leftPoint = center - leftDistance / 2f;
            rightPoint = center + rightDistance / 2f;
        }

        int left = Math.max(
                0,
                Math.round(
                        (leftPoint
                        - page.getCropBox().getLowerLeftX())
                        * scale));
        int right = Math.min(
                imageWidth,
                Math.round(
                        (rightPoint
                        - page.getCropBox().getLowerLeftX())
                        * scale));
        return new int[]{left, Math.max(left + 1, right)};
    }

    /**
     * 픽셀 기반 보조 크롭: 도안명에서 위쪽으로 이동하며
     * 충분한 흰 여백이 나타나는 지점을 이미지의 위 경계로 판단한다.
     */
    private int findContentTop(
            BufferedImage image,
            int left,
            int right,
            int startY,
            int labelTop) {

        int blankRun = 0;
        boolean foundContent = false;
        int contentRows = 0;

        for(int y = startY; y >= 0; y--){
            if(hasInkInRow(image, left, right, y)){
                foundContent = true;
                contentRows++;
                blankRun = 0;
                continue;
            }

            if(foundContent){
                blankRun++;

                if(blankRun >= 8
                        && contentRows >= 30
                        && y < labelTop - 8){
                    return y + blankRun;
                }
            }
        }

        return 0;
    }

    /** 픽셀 기반 보조 크롭에서 왼쪽 흰 여백의 끝을 찾는다. */
    private int findContentLeft(
            BufferedImage image,
            int limit,
            int center,
            int top,
            int bottom) {

        int blankRun = 0;
        boolean foundContent = false;

        for(int x = center; x >= limit; x--){
            if(hasInkInColumn(image, x, top, bottom)){
                foundContent = true;
                blankRun = 0;
                continue;
            }

            if(foundContent && ++blankRun >= 18){
                return x + blankRun;
            }
        }

        return limit;
    }

    /** 픽셀 기반 보조 크롭에서 오른쪽 흰 여백의 시작을 찾는다. */
    private int findContentRight(
            BufferedImage image,
            int center,
            int limit,
            int top,
            int bottom) {

        int blankRun = 0;
        boolean foundContent = false;

        for(int x = center; x < limit; x++){
            if(hasInkInColumn(image, x, top, bottom)){
                foundContent = true;
                blankRun = 0;
                continue;
            }

            if(foundContent && ++blankRun >= 18){
                return x - blankRun + 1;
            }
        }

        return limit;
    }

    /** 한 가로줄에 흰색이 아닌 픽셀이 일정 수 이상 있는지 확인한다. */
    private boolean hasInkInRow(
            BufferedImage image,
            int left,
            int right,
            int y) {

        if(y < 0 || y >= image.getHeight()){
            return false;
        }

        int safeLeft = Math.max(0, left);
        int safeRight = Math.min(image.getWidth(), right);

        if(safeLeft >= safeRight){
            return false;
        }

        int required = Math.max(2, (safeRight - safeLeft) / 200);
        int count = 0;

        for(int x = safeLeft; x < safeRight; x++){
            if(isInk(image.getRGB(x, y))
                    && ++count >= required){
                return true;
            }
        }

        return false;
    }

    /** 한 세로줄에 흰색이 아닌 픽셀이 일정 수 이상 있는지 확인한다. */
    private boolean hasInkInColumn(
            BufferedImage image,
            int x,
            int top,
            int bottom) {

        if(x < 0 || x >= image.getWidth()){
            return false;
        }

        int safeTop = Math.max(0, top);
        int safeBottom = Math.min(image.getHeight(), bottom);

        if(safeTop >= safeBottom){
            return false;
        }

        int required = Math.max(2, (safeBottom - safeTop) / 250);
        int count = 0;

        for(int y = safeTop; y < safeBottom; y++){
            if(isInk(image.getRGB(x, y))
                    && ++count >= required){
                return true;
            }
        }

        return false;
    }

    /** 거의 흰색인 픽셀은 여백, 그 외의 픽셀은 그림/글자로 판단한다. */
    private boolean isInk(int rgb) {

        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red < 245 || green < 245 || blue < 245;
    }

    /**
     * 크롭된 도안을 고정 크기의 흰 PNG 캔버스 가운데에 배치한다.
     *
     * <p>원본 비율을 유지하므로 가로 또는 세로로 찌그러지지 않는다.
     * 엑셀 표시 크기보다 큰 PNG를 저장해 화면 확대 시 선명도를 확보한다.</p>
     */
    private BufferedImage resizeForExcel(BufferedImage source) {

        double scale = Math.min(
                (double) (IMAGE_SOURCE_WIDTH_PX - 12)
                        / source.getWidth(),
                (double) (IMAGE_SOURCE_HEIGHT_PX - 12)
                        / source.getHeight());

        int width = Math.max(
                1,
                (int) Math.round(source.getWidth() * scale));
        int height = Math.max(
                1,
                (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(
                IMAGE_SOURCE_WIDTH_PX,
                IMAGE_SOURCE_HEIGHT_PX,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();

        try{
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(
                    0,
                    0,
                    IMAGE_SOURCE_WIDTH_PX,
                    IMAGE_SOURCE_HEIGHT_PX);
            int x = (IMAGE_SOURCE_WIDTH_PX - width) / 2;
            int y = (IMAGE_SOURCE_HEIGHT_PX - height) / 2;
            graphics.drawImage(
                    source,
                    x,
                    y,
                    width,
                    height,
                    null);
        }finally{
            graphics.dispose();
        }

        return resized;
    }

    /**
     * 최종 도안 목록을 기준 양식의 '컬러체크' 시트로 작성한다.
     *
     * <p>데이터 셀과 이미지는 같은 행에 배치되며, 필터와 상단 행 고정을
     * 설정해 도안 수가 많아도 검토하기 쉽게 만든다.</p>
     */
    private void writeSheet(
            Workbook workbook,
            List<DrawingEntry> entries) {

        var sheet = workbook.createSheet("컬러체크");
        var drawing = sheet.createDrawingPatriarch();
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);
        // 기준 양식처럼 1~2행은 "체크" 제목 영역으로 합친다.
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("체크");
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(
                new org.apache.poi.ss.util.CellRangeAddress(
                        0,
                        1,
                        0,
                        HEADERS.length - 1));
        // 실제 열 제목은 엑셀 3행(0부터 세면 인덱스 2)에 작성한다.
        Row header = sheet.createRow(HEADER_ROW);

        for(int column = 0;
                column < HEADERS.length;
                column++){
            var cell = header.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = FIRST_DATA_ROW;

        for(DrawingEntry entry : entries){
            Row row = sheet.createRow(rowIndex);
            row.setHeightInPoints(
                    (float) IMAGE_DISPLAY_HEIGHT_PX
                    * 72f / 96f);
            row.createCell(0).setCellValue(entry.drawingName());
            row.createCell(1).setCellValue("");
            row.createCell(2).setCellValue(entry.colorCheck());
            row.createCell(3).setCellValue(entry.chapter());
            row.createCell(4).setCellValue(
                    entry.chapterNumber());

            for(int column = 0;
                    column < HEADERS.length;
                    column++){
                row.getCell(column,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellStyle(bodyStyle);
            }

            // PNG를 통합 문서에 등록한 뒤 현재 행의 이미지 열에 고정한다.
            int pictureIndex = workbook.addPicture(
                    entry.image(),
                    Workbook.PICTURE_TYPE_PNG);
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(IMAGE_COLUMN);
            anchor.setRow1(rowIndex);
            anchor.setCol2(IMAGE_COLUMN + 1);
            anchor.setRow2(rowIndex + 1);
            anchor.setDx1(Units.pixelToEMU(4));
            anchor.setDy1(Units.pixelToEMU(4));
            anchor.setDx2(Units.pixelToEMU(-4));
            anchor.setDy2(Units.pixelToEMU(-4));
            anchor.setAnchorType(
                    org.apache.poi.ss.usermodel.ClientAnchor
                    .AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(anchor, pictureIndex);
            rowIndex++;
        }

        // 헤더까지 고정하고 데이터 영역에는 자동 필터를 적용한다.
        sheet.createFreezePane(0, FIRST_DATA_ROW);
        sheet.setAutoFilter(
                new org.apache.poi.ss.util.CellRangeAddress(
                        HEADER_ROW,
                        FIRST_DATA_ROW + entries.size() - 1,
                        0,
                        HEADERS.length - 1));
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(
                IMAGE_COLUMN,
                Math.min(
                        255 * 256,
                        IMAGE_DISPLAY_WIDTH_PX * 256 / 7));
    }

    /** 시트 맨 위 "체크" 제목에 사용하는 스타일. */
    private CellStyle createTitleStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** 노란 배경, 굵은 글씨, 테두리를 가진 열 제목 스타일. */
    private CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(
                IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /** 데이터 셀을 가운데 정렬하고 긴 내용은 줄바꿈하는 기본 스타일. */
    private CellStyle createBodyStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /** PDF 글자 조각 사이에 생긴 공백을 제거해 원래 도안 ID로 복원한다. */
    private String normalizeDrawingName(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** 중복 제거와 DB 비교에 사용할 대소문자 무관 도안명 키를 만든다. */
    private String canonicalDrawingName(String drawingName) {
        return drawingName.trim()
                .toUpperCase(Locale.ROOT);
    }

    /** DB 값 중 V/X만 엑셀에 표시하고 그 외 값은 빈칸으로 안전하게 처리한다. */
    private String normalizeCheckValue(String checkValue) {

        if(checkValue == null){
            return "";
        }

        String normalized = checkValue.trim()
                .toUpperCase(Locale.ROOT);
        return normalized.equals("V")
                || normalized.equals("X")
                ? normalized
                : "";
    }

    /** 챕터 번호를 숫자 순으로 정렬하고 Q/미확인 값은 뒤쪽에 둔다. */
    private int chapterSortOrder(String chapterNumber) {

        if(chapterNumber != null
                && chapterNumber.trim()
                .matches("[1-9][0-9]*")){
            return Integer.parseInt(
                    chapterNumber.trim());
        }

        if(chapterNumber != null
                && chapterNumber.trim()
                .equalsIgnoreCase("Q")){
            return Integer.MAX_VALUE - 1;
        }

        return Integer.MAX_VALUE;
    }

    /** 번호가 아닌 제목 식별만 담당하는 영문·한글 챕터 별칭표다. */
    private static Map<String,String> createChapterTitleMap() {

        Map<String,String> titles = new LinkedHashMap<>();
        addChapterTitle(titles, "Introduction");
        addChapterTitle(titles, "Overview");
        addChapterTitle(titles, "Specifications");
        addChapterTitle(titles, "Opening and closing");
        addChapterTitle(titles, "Seating and safety restraints");
        addChapterTitle(titles, "Driver Adjustments");
        addChapterTitle(
                titles,
                "Hybrid vehicle guide",
                "Hybrid system overview",
                "Hybrid/Plug-in Hybrid system overview");
        addChapterTitle(titles, "Electric vehicle guide");
        addChapterTitle(
                titles,
                "Controls and Features",
                "Control and Features");
        addChapterTitle(titles, "Driver assistance guide");
        addChapterTitle(titles, "Driving your vehicle");
        addChapterTitle(titles, "What to do in an emergency");
        addChapterTitle(titles, "Maintenance");

        addChapterTitle(titles, "안전 주의 사항");
        addChapterTitle(
                titles,
                "그림 목차",
                "그림 목차/안전 주의 사항",
                "그림목차/안전 주의 사항");
        addChapterTitle(titles, "소개", "서론");
        addChapterTitle(
                titles,
                "차량 정보",
                "차량 살펴보기",
                "차량 개요");
        addChapterTitle(titles, "제원", "차량 제원");
        addChapterTitle(
                titles,
                "차량 열림과 닫힘 장치",
                "열림 및 닫힘",
                "개폐 장치");
        addChapterTitle(
                titles,
                "주행 준비",
                "운전자 조절 장치");
        addChapterTitle(
                titles,
                "좌석 및 안전 장치",
                "안전 장치");
        addChapterTitle(titles, "하이브리드 전용 기능");
        addChapterTitle(
                titles,
                "차량 편의 장치",
                "편의 장치",
                "조작 장치 및 편의 기능");
        addChapterTitle(titles, "운전자 보조 시스템");
        addChapterTitle(titles, "시동 및 주행");
        addChapterTitle(
                titles,
                "비상시 응급 조치",
                "비상시 응급조치");
        addChapterTitle(titles, "정기 점검");
        return Map.copyOf(titles);
    }

    private static Map<String,String> createChapterNumberMap() {

        Map<String,String> numbers = new LinkedHashMap<>();
        addChapterNumber(numbers, "Introduction", "1");
        addChapterNumber(numbers, "Overview", "2");
        addChapterNumber(numbers, "Specifications", "3");
        addChapterNumber(numbers, "Opening and closing", "4");
        addChapterNumber(numbers, "Driver Adjustments", "5");
        addChapterNumber(numbers, "Seating and safety restraints", "6");
        addChapterNumber(numbers, "Hybrid vehicle guide", "7");
        addChapterNumber(numbers, "Electric vehicle guide", "7");
        addChapterNumber(numbers, "Hybrid system overview", "7");
        addChapterNumber(numbers, "Hybrid/Plug-in Hybrid system overview", "7");
        addChapterNumber(numbers, "Controls and Features", "8");
        addChapterNumber(numbers, "Driving your vehicle", "9");
        addChapterNumber(numbers, "Driver assistance guide", "10");
        addChapterNumber(numbers, "What to do in an emergency", "11");
        addChapterNumber(numbers, "Maintenance", "12");
        return Map.copyOf(numbers);
    }

    private static void addChapterNumber(
            Map<String,String> numbers,
            String title,
            String number) {
        numbers.put(
                title.toLowerCase(Locale.ROOT),
                number);
    }

    private static void addChapterTitle(
            Map<String,String> titles,
            String standardTitle,
            String... aliases) {
        titles.put(
                standardTitle.toLowerCase(Locale.ROOT),
                standardTitle);

        for(String alias : aliases){
            titles.put(
                    alias.toLowerCase(Locale.ROOT),
                    standardTitle);
        }
    }

    /**
     * 엑셀 한 행에 들어갈 완성된 데이터.
     * 내부 처리용 자료형이므로 별도 DTO 파일로 외부에 노출하지 않는다.
     */
    private record DrawingEntry(
            String drawingName,
            String colorCheck,
            String chapter,
            String chapterNumber,
            byte[] image) {
    }

    /** 현재 페이지 또는 직전 페이지에서 찾은 챕터명과 번호. */
    private record ChapterInfo(
            String title,
            String number) {

        private static final ChapterInfo EMPTY =
                new ChapterInfo("", "");

        private boolean isEmpty() {
            return title.isBlank() && number.isBlank();
        }
    }

    /**
     * PDF에서 읽은 한 줄의 문자열과 위치 정보.
     * 글꼴 크기는 일반 표 셀과 작은 도안 ID를 구분하는 데 사용한다.
     */
    private record TextLine(
            String text,
            float x,
            float y,
            float width,
            float height,
            float fontSize) {
    }

    /** 렌더링된 페이지 이미지에서 잘라낼 픽셀 사각형. */
    private record CropBounds(
            int left,
            int top,
            int width,
            int height) {
    }

    /** PDF 페이지 좌표 기준으로 배치된 원본 이미지 객체의 사각형. */
    private record ImageRegion(
            float left,
            float top,
            float right,
            float bottom) {

        private float width() {
            return right - left;
        }

        private float height() {
            return bottom - top;
        }
    }

    /**
     * PDF의 그리기 명령 중 이미지 명령만 수집하는 스캐너.
     *
     * <p>선이나 도형 그리기 메서드는 이 기능에 필요하지 않아 빈 구현으로 두고,
     * {@link #drawImage(PDImage)}에서 이미지 좌표만 기록한다.</p>
     */
    private static final class ImageRegionScanner
            extends PDFGraphicsStreamEngine {

        private final List<ImageRegion> regions =
                new ArrayList<>();

        private ImageRegionScanner(PDPage page) {
            super(page);
        }

        @Override
        public void drawImage(PDImage image) {

            // PDF 변환 행렬을 이용해 이미지의 실제 페이지 좌표를 구한다.
            var matrix = getGraphicsState()
                    .getCurrentTransformationMatrix();
            Point2D.Float first =
                    matrix.transformPoint(0, 0);
            Point2D.Float second =
                    matrix.transformPoint(1, 1);
            var cropBox = getPage().getCropBox();
            float minimumX = Math.min(
                    first.x,
                    second.x);
            float maximumX = Math.max(
                    first.x,
                    second.x);
            float minimumY = Math.min(
                    first.y,
                    second.y);
            float maximumY = Math.max(
                    first.y,
                    second.y);
            float left = minimumX
                    - cropBox.getLowerLeftX();
            float right = maximumX
                    - cropBox.getLowerLeftX();
            float top = cropBox.getUpperRightY()
                    - maximumY;
            float bottom = cropBox.getUpperRightY()
                    - minimumY;

            if(right > left && bottom > top){
                regions.add(new ImageRegion(
                        left,
                        top,
                        right,
                        bottom));
            }
        }

        private List<ImageRegion> getRegions() {
            return List.copyOf(regions);
        }

        @Override
        public void appendRectangle(
                Point2D first,
                Point2D second,
                Point2D third,
                Point2D fourth) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(
                float x1,
                float y1,
                float x2,
                float y2,
                float x3,
                float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float();
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
    }

    /**
     * 기본 PDFTextStripper에 글자 위치와 글꼴 크기 수집 기능을 추가한다.
     *
     * <p>일반 PDFTextStripper는 문자열만 반환하므로, 어떤 이미지 아래에 있는
     * 도안명인지 판단하려면 이 확장 클래스가 필요하다.</p>
     */
    private static final class PositionedTextStripper
            extends PDFTextStripper {

        private final List<TextLine> lines =
                new ArrayList<>();

        private PositionedTextStripper() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(
                String text,
                List<TextPosition> positions) {

            if(positions == null || positions.isEmpty()){
                return;
            }

            float x = Float.MAX_VALUE;
            float y = Float.MAX_VALUE;
            float right = 0f;
            float height = 0f;
            float fontSize = 0f;

            // 여러 글자 조각을 감싸는 하나의 사각형과 최대 글꼴 크기를 계산한다.
            for(TextPosition position : positions){
                x = Math.min(x, position.getXDirAdj());
                y = Math.min(y, position.getYDirAdj());
                right = Math.max(
                        right,
                        position.getXDirAdj()
                        + position.getWidthDirAdj());
                height = Math.max(
                        height,
                        position.getHeightDir());
                fontSize = Math.max(
                        fontSize,
                        position.getFontSizeInPt());
            }

            lines.add(new TextLine(
                    text.trim(),
                    x,
                    y,
                    right - x,
                    height,
                    fontSize));
        }

        private List<TextLine> getLines() {
            return List.copyOf(lines);
        }
    }
}
