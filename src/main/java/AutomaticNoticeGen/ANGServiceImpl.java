package AutomaticNoticeGen;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ANGServiceImpl implements ANGService {

    // DataFormatter: 셀 타입(문자/숫자/수식) 상관없이 안전 파싱
    private static final DataFormatter DF = new DataFormatter();
    private final Path rulesDirectory;

    public ANGServiceImpl() {
        this(Paths.get("."));
    }

    public ANGServiceImpl(Path rulesDirectory) {
        this.rulesDirectory = rulesDirectory == null
                ? Paths.get(".")
                : rulesDirectory;
    }

    // [MERGE_ANY_ORDER] US/EG에서 챕터 내 순서/연속성 무관 병합 대상(정확매칭, 대소문자 무시)
    private static final java.util.Set<String> MERGE_TARGETS = java.util.Set.of(
        "introduction",
        "overview",
        "electric vehicle guide",
        "your vehicle at a glance"
    );

    // [D1_MERGE] US/EG에서 "1레벨 챕터 자체"를 한 줄로 찍을 대상(정확매칭, 대소문자 무시)
    private static final java.util.Set<String> D1_MERGE_TARGETS = java.util.Set.of(
        "foreword",
        "electric vehicle guide",
        "introduction",
        "your vehicle at a glance",
        "overview"
    );

    // ================================
    // 시장별 2레벨 섹션 제외 타이틀(정규화 보관)
    //  - normSet()으로 NFKC + 소문자 + 공백정리 + 공백제거변형까지 함께 저장
    //  - 아래 목록은 원하는 대로 채워 넣기
    // ================================
    private static final java.util.Map<String, java.util.Set<String>> L2_EXCLUDE_BY_MARKET =
        java.util.Map.of(
            "KO", normSet(
               "헤드레스트", "암레스트", "실외 편의 장치", "실내외 편의 장치", "암레스트 (사양 적용 시)"
            ),
            "US", normSet(
                "How to check the symbol on the charging label",
                     "Important safety precautions",
                     "California perchlorate notice",
                     "Consumer assistance (U.S. only)",
                     "Electrical equipment (U.S. only)",
                     "Reporting Safety Defects (U.S. only)",
                     "Online factory authorized manuals (U.S. only)",
                     "How to check the symbol on the charging label (For Europe)",
                     "How to check the symbol on the charging label (For Europe) (if equipped)"
            ),
            "DEFAULT", normSet(
                 "Armrest (if equipped)", "Headrest", "Seatback pocket","Armrest",
                     "ISOFIX anchorage and top-tether anchorage (ISOFIX Anchorage System) for children",
                     "ISOFIX anchorage and top-tether anchorage (ISOFIX Anchorage System) for children (if equipped)",
                     "Opening and closing", "Adjusting", "Display",
                     "Exterior features", "Interior and exterior features",
                     "Driving features of the vehicle", "Stopping the vehicle",
                     "Driving safety features", "Hazard warning flasher",
                     "Reference weight and distance when towing a trailer",
                     "Reference weight and distance when towing a trailer (if equipped)",
                     "How to check the symbol on the charging label (For Europe)",
                     "How to check the symbol on the charging label (For Europe) (if equipped)",
                     "Information for EU Battery Regulation"
            )
        );

    private static boolean isSpecialD1(String rawTitle) {
        if (rawTitle == null) return false;
        String t = normalizeTitle(rawTitle).toLowerCase();
        return D1_MERGE_TARGETS.contains(t);
    }

    @Override
    public String generateFromPdf(String pdfPath) throws Exception {
        return Paths.get(pdfPath).getFileName().toString();
    }

    @Override
    public List<ChapterRange> getChapterRanges(String pdfPath) throws Exception {
        return TocExtractor.extractChapterRanges(Paths.get(pdfPath).toFile(), 0);
    }

    @Override
    public List<SectionRange> getSections(String pdfPath, ChapterRange chapter, int lookaheadPages) throws Exception {
        return TocExtractor.extractSectionsOfChapter(Paths.get(pdfPath).toFile(), chapter, lookaheadPages);
    }

    @Override
    public String exportExcel(String pdfPath, String outXlsx) throws Exception {
        var pdfFile = Paths.get(pdfPath).toFile();
        List<ChapterRange> chapters = getChapterRanges(pdfPath);

        String fileName = pdfFile.getName();
        String baseNoExt = stripExt(fileName);
        String[] tokens = splitFileNameTokensWithoutExt(fileName);
        String market = detectMarket(tokens, baseNoExt);
        boolean isKO = "KO".equals(market);
        boolean isUS = "US".equals(market);

        // KO가 아닌 경우만 Intro/Overview/EV guide 병합
        boolean mergeTargetEveryChapter = !isKO;

        var rulesXlsx = switch (market) {
            case "KO" -> rulesDirectory.resolve("ANG_ko_rules.xlsx");
            case "US" -> rulesDirectory.resolve("ANG_us_rules.xlsx");
            default    -> rulesDirectory.resolve("ANG_eg_rules.xlsx");
        };
        List<Rule> rules = loadRulesFromXlsx(rulesXlsx);

        try (Workbook wb = new XSSFWorkbook()) {
            // ===== 스타일 =====
            CellStyle head = wb.createCellStyle();
            Font hfont = wb.createFont();
            hfont.setBold(true);
            hfont.setFontHeightInPoints((short) 10);
            head.setFont(hfont);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorderAll(head);

            CellStyle centerBody = wb.createCellStyle();
            centerBody.setVerticalAlignment(VerticalAlignment.CENTER);
            centerBody.setAlignment(HorizontalAlignment.CENTER);
            setBorderAll(centerBody);

            
            CellStyle leftBody = wb.createCellStyle();
         // 기존: leftBody.setVerticalAlignment(VerticalAlignment.VERTICAL_CENTER);
         leftBody.setVerticalAlignment(VerticalAlignment.CENTER);
         leftBody.setAlignment(HorizontalAlignment.LEFT);
         leftBody.setWrapText(true);
         setBorderAll(leftBody);


            final int START_ROW = 3;

            Sheet sheet = wb.createSheet("검토표");
            int r = START_ROW;

            // ===== 헤더 =====
            String[] hdr = {"그룹", "페이지", "검토 항목", "세부 검토 사항", "설계팀", "참조차종"};
            Row hr = sheet.createRow(r++);
            for (int i = 0; i < hdr.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(hdr[i]);
                c.setCellStyle(head);
            }

            String extracted = extractMiddleTokens(tokens);
            Row row2 = sheet.getRow(1);
            if (row2 == null) row2 = sheet.createRow(1);
            Cell cellA2 = row2.createCell(0);
            cellA2.setCellValue("● " + extracted + " 오너스매뉴얼 설계 중점 확인 요청사항");

            Font redBoldFont = wb.createFont();
            redBoldFont.setBold(true);
            redBoldFont.setColor(IndexedColors.RED.getIndex());
            CellStyle noticeStyle = wb.createCellStyle();
            noticeStyle.setFont(redBoldFont);
            noticeStyle.setAlignment(HorizontalAlignment.LEFT);
            noticeStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Row row3 = sheet.getRow(2);
            if (row3 == null) row3 = sheet.createRow(2);
            Cell cellA3 = row3.createCell(0);
            cellA3.setCellValue("※ 각 챕터 목차는 검토 대상에서 제외 바랍니다.");
            cellA3.setCellStyle(noticeStyle);

            // ===== Quick 처리 =====
            if (fileName.toLowerCase().contains("quick")) {
                int[] span = computeLevel2Span(pdfPath, chapters);
                int minPage = span[0], maxPage = span[1];

                Row dataRow = sheet.createRow(r++);
                addRow(dataRow, centerBody, leftBody, "요약본", pageSpan(minPage, maxPage), "EV가이드");
                applyAutoFill(dataRow, rules);

                fixColumnWidths(sheet, market, fileName);
                try (FileOutputStream fos = new FileOutputStream(outXlsx)) {
                    wb.write(fos);
                }
                return outXlsx;
            }

            // ===== Foreword(서문) 범위 계산 =====
            int minL1PosStart = Integer.MAX_VALUE;
            for (ChapterRange c : chapters) {
                if (c != null && c.startPage > 0) {
                    minL1PosStart = Math.min(minL1PosStart, c.startPage);
                }
            }

            int minL2PosStart = Integer.MAX_VALUE;
            for (ChapterRange cr : chapters) {
                List<SectionRange> secsTmp = null;
                try { secsTmp = getSections(pdfPath, cr, 0); } catch (Exception ignore) {}
                if (secsTmp == null) continue;
                for (SectionRange s : secsTmp) {
                    if (s != null && s.startPage > 0) {
                        minL2PosStart = Math.min(minL2PosStart, s.startPage);
                    }
                }
            }

            int forewordEnd = -1;
            int forewordStart = 1;
            int cand = Integer.MAX_VALUE;
            if (minL1PosStart != Integer.MAX_VALUE) cand = Math.min(cand, minL1PosStart);
            if (minL2PosStart != Integer.MAX_VALUE) cand = Math.min(cand, minL2PosStart);
            if (cand != Integer.MAX_VALUE && cand > 1) {
                forewordEnd = cand - 1;
            }

            // [FM] 페이지 라벨 사용을 위해 PDF 열기 (0-based index→라벨)
            String frontLabel = null;
            if (forewordEnd >= forewordStart) {
                try (PDDocument doc = Loader.loadPDF(pdfFile)) {
                    int startIdx0 = 0;                   // 0-based 내부 계산 시작
                    int endIdx0   = forewordEnd - 1;     // 1-based → 0-based 변환
                    if (endIdx0 >= startIdx0) {
                        frontLabel = labelRange(doc, startIdx0, endIdx0); // 예: "i–xi"
                    }
                }
            }

            // ===== Foreword(서문) 한 줄 생성 =====
            if (forewordEnd >= forewordStart) {
                Row prefaceRow = sheet.createRow(r++);
                String groupCol = isKO ? "0장" : "Foreword"; // [FM]
                String pageCol  = (frontLabel != null && !frontLabel.isBlank())
                        ? frontLabel
                        : pageSpan(forewordStart, forewordEnd);
                addRow(prefaceRow, centerBody, leftBody, groupCol, pageCol, isKO ? "서문" : "Foreword");
                applyAutoFill(prefaceRow, rules);
            }

            // ===== 챕터/섹션 기록 =====
            for (ChapterRange cr : chapters) {
                if (shouldSkipChapter(cr.chapterTitle)) continue;

                String chapTitleNorm = normalizeTitle(cr.chapterTitle);
                String groupLabel = isKO ? (cr.chapterNum + "장") : chapTitleNorm;

                List<SectionRange> secs = getSections(pdfPath, cr, 0);
                int groupStartRow = r;

                // [D1_MERGE] US/EG이고 1레벨 제목이 특수 목록이면: 섹션 무시하고 챕터 전체를 한 줄로 출력
                if (!isKO && isSpecialD1(chapTitleNorm)) {
                    if (cr.startPage > 0 && cr.endPage > 0) {
                        Row dataRow = sheet.createRow(r++);
                        addRow(dataRow, centerBody, leftBody, groupLabel, pageSpan(cr.startPage, cr.endPage), chapTitleNorm);
                        applyAutoFill(dataRow, rules);
                    }
                    safeMergeGroupA(sheet, groupStartRow, r - 1);
                    continue;
                }

                if (!mergeTargetEveryChapter) {
                    // ======= KO(일반) 분기: "그림 목차"+"안전 주의 사항" 한 줄로 합치기 =======
                    int targetMin = Integer.MAX_VALUE, targetMax = Integer.MIN_VALUE;
                    boolean hasFig = false, hasSafety = false;
                    List<SectionRange> nonTargetSecs = new ArrayList<>();

                    if (secs != null && !secs.isEmpty()) {
                        for (SectionRange s : secs) {
                            if (s == null || s.title == null) continue;

                            // 🔸 시장별 2레벨 제외 필터 (depth 없이 L2 판별)
                            if (isExcludedL2(market, s.title, secs, cr, s)) continue;

                            String t = normalizeTitle(s.title);
                            String tNoSpace = t.replaceAll("\\s+", "");

                            boolean isFig = t.equals("그림 목차") || tNoSpace.equals("그림목차");
                            boolean isSafety = t.equals("안전 주의 사항") || tNoSpace.equals("안전주의사항");

                            if (isFig || isSafety) {
                                if (s.startPage > 0) targetMin = Math.min(targetMin, s.startPage);
                                if (s.endPage   > 0) targetMax = Math.max(targetMax, s.endPage);
                                hasFig |= isFig;
                                hasSafety |= isSafety;
                            } else {
                                nonTargetSecs.add(s);
                            }
                        }
                    }

                    boolean firstInGroup = true;

                    // 1) 타겟 존재 시: 한 줄
                    if (targetMin != Integer.MAX_VALUE && targetMax != Integer.MIN_VALUE) {
                        Row row = sheet.createRow(r++);
                        String label = (hasFig && hasSafety) ? "그림 목차 / 안전 주의 사항"
                                     : hasFig ? "그림 목차"
                                     : "안전 주의 사항";
                        addRow(row, centerBody, leftBody, groupLabel, pageSpan(targetMin, targetMax), label);
                        applyAutoFill(row, rules);
                        firstInGroup = false;
                    }

                    // 2) 나머지 섹션
                    if (!nonTargetSecs.isEmpty()) {
                        for (SectionRange s : nonTargetSecs) {
                            if (s == null || s.title == null) continue;
                            String a = firstInGroup ? groupLabel : "";
                            Row dataRow = sheet.createRow(r++);
                            addRow(dataRow, centerBody, leftBody, a, pageSpan(s.startPage, s.endPage), normalizeTitle(s.title));
                            applyAutoFill(dataRow, rules);
                            firstInGroup = false;
                        }
                    } else if ((secs == null || secs.isEmpty()) && firstInGroup) {
                        if (cr.startPage > 0 && cr.endPage > 0) {
                            Row dataRow = sheet.createRow(r++);
                            addRow(dataRow, centerBody, leftBody, groupLabel, pageSpan(cr.startPage, cr.endPage), chapTitleNorm);
                            applyAutoFill(dataRow, rules);
                            firstInGroup = false;
                        }
                    }

                    safeMergeGroupA(sheet, groupStartRow, r - 1);
                    continue; // KO 분기 종료
                }

                // ======= KO가 아닌 경우(US/EG): 지정 타깃 병합 + 제외 필터 =======
                boolean printedAny = false;

                int tgtMin = Integer.MAX_VALUE, tgtMax = Integer.MIN_VALUE;
                java.util.LinkedHashSet<String> tgtTitles = new java.util.LinkedHashSet<>();
                java.util.List<SectionRange> others = new java.util.ArrayList<>();

                if (secs != null && !secs.isEmpty()) {
                    for (SectionRange s : secs) {
                        if (s == null || s.title == null) continue;

                        // 🔸 시장별 2레벨 제외 필터
                        if (isExcludedL2(market, s.title, secs, cr, s)) continue;

                        String titleNorm = normalizeTitle(s.title);
                        if (isIntroOverviewEvGuide(titleNorm)) {
                            if (s.startPage > 0) tgtMin = Math.min(tgtMin, s.startPage);
                            if (s.endPage   > 0) tgtMax = Math.max(tgtMax, s.endPage);
                            tgtTitles.add(titleNorm);
                        } else {
                            others.add(s);
                        }
                    }
                }

                // 1) 타깃 섹션 한 줄
                if (tgtMin != Integer.MAX_VALUE && tgtMax != Integer.MIN_VALUE) {
                    Row row = sheet.createRow(r++);
                    String a = groupLabel;
                    String labelJoined = String.join(" / ", tgtTitles);
                    addRow(row, centerBody, leftBody, a, pageSpan(tgtMin, tgtMax), labelJoined);
                    applyAutoFill(row, rules);
                    printedAny = true;
                }

                // 2) 비타깃 개별
                for (SectionRange s : others) {
                    if (s.startPage <= 0 || s.endPage <= 0) continue;
                    Row row = sheet.createRow(r++);
                    String a = printedAny ? "" : groupLabel;
                    addRow(row, centerBody, leftBody, a, pageSpan(s.startPage, s.endPage), normalizeTitle(s.title));
                    applyAutoFill(row, rules);
                    printedAny = true;
                }

                // 3) 백업
                // US 추출시 Consumer info 남아서 백업 잠금
               // if (!printedAny && cr.startPage > 0 && cr.endPage > 0) {
                //    Row dataRow = sheet.createRow(r++);
               //     addRow(dataRow, centerBody, leftBody, groupLabel, pageSpan(cr.startPage, cr.endPage), groupLabel);
               //     applyAutoFill(dataRow, rules);
               // }

                safeMergeGroupA(sheet, groupStartRow, r - 1);
            }

            // ===== 마무리 =====
            fixColumnWidths(sheet, market, fileName);
            try (FileOutputStream fos = new FileOutputStream(outXlsx)) {
                wb.write(fos);
            }
        }
        return outXlsx;
    }

    // Quick 유틸
    private static int getTotalPages(String pdfPath) throws Exception {
        try (PDDocument doc = Loader.loadPDF(java.nio.file.Paths.get(pdfPath).toFile())) {
            return doc.getNumberOfPages();
        }
    }

    private int[] computeLevel2Span(String pdfPath, List<ChapterRange> chapters) throws Exception {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (ChapterRange cr : chapters) {
            if (shouldSkipChapter(cr.chapterTitle)) continue;
            List<SectionRange> secs = getSections(pdfPath, cr, 0);
            if (secs == null || secs.isEmpty()) continue;

            for (SectionRange s : secs) {
                if (s == null) continue;
                if (s.startPage > 0) min = Math.min(min, s.startPage);
                if (s.endPage > 0) max = Math.max(max, s.endPage);
            }
        }

        if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) {
            int total = getTotalPages(pdfPath);
            min = 1;
            max = Math.max(1, total);
        }
        return new int[]{min, max};
    }

    // 규칙 매핑
    private enum MatchType { EXACT, CONTAINS, REGEX }

    private static class Rule {
        final MatchType type;
        final String key;
        final String detail;
        final String teams;
        Rule(MatchType type, String key, String detail, String teams) {
            this.type = type; this.key = key; this.detail = detail; this.teams = teams;
        }
        boolean matches(String target) {
            if (target == null) return false;
            String t = norm(target);
            String k = norm(key);
            return switch (type) {
                case EXACT -> t.equals(k);
                case CONTAINS -> t.contains(k);
                case REGEX -> t.matches(key);
            };
        }
    }

    private static List<Rule> loadRulesFromXlsx(java.nio.file.Path xlsxPath) {
        List<Rule> rules = new ArrayList<>();
        try {
            if (xlsxPath == null || !Files.exists(xlsxPath)) return rules;
            try (var fis = Files.newInputStream(xlsxPath);
                 var wb  = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {

                Sheet sh = wb.getSheet("Rules");
                if (sh == null && wb.getNumberOfSheets() > 0) {
                    sh = wb.getSheetAt(0);
                }
                if (sh == null) return rules;

                int last = sh.getLastRowNum();
                for (int i = 1; i <= last; i++) {
                    Row row = sh.getRow(i);
                    if (row == null) continue;

                    String type = getCellString(row.getCell(0)).toUpperCase();
                    String key  = getCellString(row.getCell(1));
                    String det  = getCellString(row.getCell(2));
                    String team = getCellString(row.getCell(3)).replace("\\n", "\n");

                    if (type.isBlank() || key.isBlank()) continue;
                    MatchType mt;
                    try { mt = MatchType.valueOf(type); } catch (Exception e) { continue; }

                    rules.add(new Rule(mt, key, det, team));
                }
            }
        } catch (Exception ignore) {}
        return rules;
    }

    private static void applyAutoFill(Row row, List<Rule> rules) {
        if (row == null || rules == null || rules.isEmpty()) return;
        Cell cCell = row.getCell(2);
        if (cCell == null) return;

        String cVal = safeString(DF.formatCellValue(cCell));
        if (cVal.isEmpty()) return;

        for (Rule r : rules) {
            if (r.matches(cVal)) {
                if (r.detail != null && !r.detail.isBlank()) {
                    nonNullCell(row, 3).setCellValue(r.detail);
                }
                if (r.teams != null && !r.teams.isBlank()) {
                    nonNullCell(row, 4).setCellValue(r.teams);
                }
                break;
            }
        }
    }

    private static Cell nonNullCell(Row row, int idx) {
        Cell c = row.getCell(idx);
        return (c != null) ? c : row.createCell(idx);
    }

    private static String getCellString(Cell c) {
        if (c == null) return "";
        String s = DF.formatCellValue(c);
        if (s == null) return "";
        return s.replace("\uFEFF","").replace("\u200B","").trim();
    }

    // 부록 접두어(A., B., …) 제거 후, 소문자 기준으로 'abbreviation' 포함 시 제외
    private static boolean shouldSkipChapter(String title) {
        if (title == null) return false;

        String t = title.replaceFirst("^[A-Za-z]\\.?\\s+", ""); // 부록 접두어 제거
        t = t.toLowerCase().trim();

        return t.contains("약어집") || t.contains("약어")
            || t.contains("색인") || t.contains("찾아보기")
            || t.contains("abbreviation") || t.contains("abbreviations")
            || t.contains("appendix") || t.contains("index")
            || t.contains("table of contents") || t.equals("contents") || t.equals("toc");
    }

    private static String normalizeTitle(String s) {
        if (s == null) return "";
        
        // 🔥 앞 숫자 절대 지우지 않음
        // (기존 replaceAll 제거)

        // 공백 정리만 수행
        String t = s.replaceAll("\\s+", " ").trim();
        return t;
    }



    private static void addRow(Row row, CellStyle centerBody, CellStyle leftBody,
                               String group, String pages, String item) {
        String[] vals = {group, pages, item, "", "", ""};
        for (int i = 0; i < vals.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(vals[i]);
            if (i <= 1) {
                c.setCellStyle(centerBody);
            } else {
                c.setCellStyle(leftBody);
            }
        }
    }

    private static void setBorderAll(CellStyle cs) {
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    private static void fixColumnWidths(Sheet sheet, String market, String fileName) {
        String up = fileName.toUpperCase();

        if ("KO".equals(market)) {
            sheet.setColumnWidth(0, 2500);
            sheet.setColumnWidth(1, 3000);
            sheet.setColumnWidth(2, 10000);
            sheet.setColumnWidth(3, 14000);
            sheet.setColumnWidth(4, 8000);
            sheet.setColumnWidth(5, 5000);

        } else if (up.contains("QUICK")) {
            sheet.setColumnWidth(0, 2500);
            sheet.setColumnWidth(1, 3000);
            sheet.setColumnWidth(2, 10000);
            sheet.setColumnWidth(3, 14000);
            sheet.setColumnWidth(4, 8000);
            sheet.setColumnWidth(5, 5000);

        } else {
            sheet.setColumnWidth(0, 8000);
            sheet.setColumnWidth(1, 3000);
            sheet.setColumnWidth(2, 12000);
            sheet.setColumnWidth(3, 15000);
            sheet.setColumnWidth(4, 6000);
            sheet.setColumnWidth(5, 6000);
        }
    }

    private static void safeMergeGroupA(Sheet sheet, int startRow, int endRow) {
        if (sheet == null) return;
        if (startRow < 0) return;
        if (endRow <= startRow) return;
        sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, 0, 0));
    }

    private static String safeString(String s) {
        return (s == null) ? "" : s.trim();
    }

    // 🔹 단일 페이지라면 "33"만 반환
    private static String pageSpan(int s, int e) {
        if (s <= 0 && e > 0) return String.valueOf(e);
        if (e <= 0 && s > 0) return String.valueOf(s);
        if (s <= 0 && e <= 0) return "";
        return (s == e) ? String.valueOf(s) : (s + "~" + e);
    }

    // === 대상 섹션 식별 헬퍼 ===
    private static boolean isIntroOverviewEvGuide(String rawTitle) {
        if (rawTitle == null) return false;
        String t = normalizeTitle(rawTitle).toLowerCase();
        return MERGE_TARGETS.contains(t);
    }

    // 파일명 유틸
    private static String[] splitFileNameTokensWithoutExt(String fileName) {
        String base = fileName;
        String lower = base.toLowerCase();
        if (lower.endsWith(".pdf")) base = base.substring(0, base.length() - 4);
        base = Normalizer.normalize(base, Normalizer.Form.NFKC);
        return base.split("_");
    }

    private static String stripExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private static String detectMarket(String[] tokens) {
        for (String t : tokens) {
            if ("KO".equalsIgnoreCase(t)) return "KO";
            if ("US".equalsIgnoreCase(t)) return "US";
        }
        return "EG";
    }

    private static String detectMarket(String[] tokens, String baseNoExt) {
        String tok = detectMarket(tokens);
        if (!"EG".equals(tok)) return tok;

        String s = baseNoExt == null ? "" : baseNoExt;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)(?:^|[_\\-\\.])(KO|US)(?:[_\\-\\.]|$)");
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find()) {
            String g = m.group(1);
            if ("KO".equalsIgnoreCase(g)) return "KO";
            if ("US".equalsIgnoreCase(g)) return "US";
        }
        return "EG";
    }

    private static String extractMiddleTokens(String[] tokens) {
        if (tokens == null || tokens.length < 3) return String.join("_", tokens);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.length - 2; i++) {
            if (sb.length() > 0) sb.append("_");
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    // =========================
    // 공통 정규화 함수 + 정규화 세트
    // =========================
    private static String norm(String s){
        if (s == null) return "";
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = s.toLowerCase();
        s = s.replaceAll("\\s+", " ").trim();      // 다중 공백 정리
        s = s.replaceAll("\\s*([()\\[\\]/-])\\s*", "$1"); // 괄호/슬래시/하이픈 주변 공백 정리
        return s;
    }

    // 입력들을 norm() 처리해서 Set에 저장 + 공백제거 변형도 함께 저장
    private static java.util.Set<String> normSet(String... vals) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (String v : vals) {
            if (v == null) continue;
            String n = norm(v);
            s.add(n);
            s.add(n.replaceAll("\\s+", "")); // "u.s. only" vs "usonly" 등 대비
        }
        return java.util.Collections.unmodifiableSet(s);
    }

    // =========================
    // [FM] 페이지 라벨 헬퍼 (리플렉션)
    // =========================

    /** [FM] 0-based page index → 라벨 문자열. 라벨 없으면 숫자. */
    private static String pageLabel(org.apache.pdfbox.pdmodel.PDDocument doc, int pageIndex) {
        try {
            var catalog = doc.getDocumentCatalog();
            Object labels = catalog.getPageLabels(); // 타입 선언 X
            if (labels != null) {
                var m = labels.getClass().getMethod("getLabel", int.class);
                Object r = m.invoke(labels, pageIndex);
                if (r instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Throwable ignore) {
        }
        return String.valueOf(pageIndex + 1);
    }

    /** [FM] 라벨 범위 "i–xi" 또는 "1–14" 생성 */
    private static String labelRange(org.apache.pdfbox.pdmodel.PDDocument doc, int startIndex0, int endIndex0) {
        String a = pageLabel(doc, startIndex0);
        String b = pageLabel(doc, endIndex0);
        return a.equals(b) ? a : a + "–" + b;
    }

    // ================================
    // 🔧 depth 없이 L2 판정 & 제외 여부
    // ================================

    // 섹션 정렬: 시작 오름차순, 끝 내림차순(부모 먼저)
    private static void sortBySpan(List<SectionRange> secs) {
        if (secs == null) return;
        secs.sort((a, b) -> {
            int as = a == null ? 0 : a.startPage;
            int bs = b == null ? 0 : b.startPage;
            if (as != bs) return Integer.compare(as, bs);
            int ae = a == null ? 0 : a.endPage;
            int be = b == null ? 0 : b.endPage;
            return -Integer.compare(ae, be);
        });
    }

    // target이 챕터 바로 하위(L2)인지 페이지 구간으로 판정
    private static boolean isLevel2(List<SectionRange> secs, ChapterRange chapter, SectionRange target) {
        if (secs == null || target == null || chapter == null) return false;

        final int C_START = Math.max(1, chapter.startPage);
        final int C_END   = (chapter.endPage > 0 ? chapter.endPage : Integer.MAX_VALUE);

        List<SectionRange> list = new ArrayList<>();
        for (SectionRange s : secs) {
            if (s == null) continue;
            int ss = Math.max(1, s.startPage);
            if (ss >= C_START && ss <= C_END) {
                list.add(s);
            }
        }
        sortBySpan(list);

        ArrayDeque<SectionRange> stack = new ArrayDeque<>();
        for (SectionRange s : list) {
            while (!stack.isEmpty()) {
                SectionRange top = stack.peek();
                int topEnd = (top.endPage > 0 ? top.endPage : Integer.MAX_VALUE);
                if (topEnd >= s.startPage) break;
                stack.pop();
            }
            SectionRange parent = stack.peek();
            if (s == target) {
                return (parent == null); // 부모가 없으면 챕터 직속(L2)
            }
            stack.push(s);
        }
        return false;
    }

    // 시장별 2레벨 제외 판단(페이지 기반 L2 판정 사용)
    private static boolean isExcludedL2(String market, String title,
                                        List<SectionRange> allInChapter,
                                        ChapterRange chapter, SectionRange s) {
        if (title == null) return false;

        // 시장 세트 꺼내기
        String mkt = (market == null || market.isBlank()) ? "DEFAULT" : market.toUpperCase();
        java.util.Set<String> set = L2_EXCLUDE_BY_MARKET.getOrDefault(mkt, L2_EXCLUDE_BY_MARKET.get("DEFAULT"));
        if (set == null || set.isEmpty()) return false;

        // 제목 정규화
        String t = norm(title);
        String tNoSpace = t.replaceAll("\\s+", "");

        // 제외 목록에 있으면 depth/L2 판정 무시하고 즉시 제외
        if (set.contains(t) || set.contains(tNoSpace)) return true;

        // 목록에 없으면 제외하지 않음 (기존 L2 전용 제외 로직 제거)
        return false;
    }
    
 // 이 챕터에 '제외 목록'에 걸리지 않는 2레벨 섹션이 하나라도 있는지?
    private static boolean hasAnyAllowedL2(String market,
                                           List<SectionRange> secs,
                                           ChapterRange chapter) {
        if (secs == null || chapter == null) return false;
        for (SectionRange s : secs) {
            if (s == null || s.title == null) continue;
            // 제외 목록에 있으면 패스
            if (isExcludedL2(market, s.title, secs, chapter, s)) continue;
            // depth 없이도 L2 판별
            if (isLevel2(secs, chapter, s)) return true;
        }
        return false;
    }

}
