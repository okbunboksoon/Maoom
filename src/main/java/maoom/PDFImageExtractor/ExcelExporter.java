package maoom.PDFImageExtractor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.SheetUtil;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class ExcelExporter {

	private static double getColumnWidthPx(Sheet sh, int colIdx) {
	    //XSSF면 정확한 픽셀값 제공
	    if (sh instanceof XSSFSheet) {
	        return ((XSSFSheet) sh).getColumnWidthInPixels(colIdx);
	    }

	    //HSSF 등일 때 대충 변환(대부분 XSSF 쓰니까 여기 거의 안 탐)
	    // columnWidth는 1/256 character 단위라서 7px 정도로 근사
	    return (sh.getColumnWidth(colIdx) / 256.0) * 7.0;
	}

	
    public static class RowData {
        public final int page;
        public final File pngFile;

        public RowData(int page, File pngFile) {
            this.page = page;
            this.pngFile = pngFile;
        }
    }

    // ===== 템플릿 채우기 설정 =====
    private static final int START_ROW_EXCEL = 5;      // 엑셀 행 번호(1-based)
    private static final int START_ROW_IDX = START_ROW_EXCEL - 1; // POI row index(0-based)

    private static final int COL_NAME = 4;   // E = 4 (0-based)
    private static final int COL_IMAGE = 5;  // F = 5 (0-based)

    // ===== 이미지 여백(픽셀) =====
    private static final int PAD_PX = 8; // 사방 최소 여백. 8~16 추천

    /**
     * resources/도안의뢰서.xlsx 템플릿을 불러와서
     * - 5행부터(E열=도안명, F열=이미지) 채운 뒤
     * outPath로 저장
     */
    public static void exportFromTemplate(String outPath, List<RowData> rows) throws Exception {

    	try (InputStream is = ExcelExporter.class.getResourceAsStream("/artwork-request/template.xlsx")) {
    	    if (is == null) {
    	        throw new FileNotFoundException("classpath에 artwork-request/template.xlsx 없음");
    	    }

            try (Workbook wb = new XSSFWorkbook(is)) {
                Sheet sh = wb.getSheetAt(0); // 필요하면 시트명으로 변경해도 됨

                Drawing<?> drawing = sh.createDrawingPatriarch();

                for (int i = 0; i < rows.size(); i++) {
                    int rowIdx = START_ROW_IDX + i;

                    Row row = sh.getRow(rowIdx);
                    if (row == null) row = sh.createRow(rowIdx);

                    RowData d = rows.get(i);

                    // ===== E열: 도안명(파일명) =====
                    Cell cName = row.getCell(COL_NAME);
                    if (cName == null) cName = row.createCell(COL_NAME);

                    String prettyName = buildPrettyName(d.pngFile);
                    cName.setCellValue(prettyName);

                    // ===== F열: 이미지(센터링 + 사방 여백) =====
                    if (d.pngFile != null && d.pngFile.exists()) {
                        insertPngCenteredWithPadding(wb, sh, drawing, d.pngFile, row, rowIdx, COL_IMAGE, PAD_PX);
                    }
                }

                // 저장
                try (FileOutputStream fos = new FileOutputStream(outPath)) {
                    wb.write(fos);
                }
            }
        }
    }

    /**
     * 파일명 규칙:
     * - 확장자 제거
     * - p{페이지}_ 접두어 제거
     * 예) p13_N_SP327_C00_188.png -> N_SP327_C00_188
     */
    private static String buildPrettyName(File pngFile) {
        if (pngFile == null) return "";
        String name = pngFile.getName();

        // 확장자 제거
        name = name.replaceFirst("(?i)\\.png$", "");
        name = name.replaceFirst("(?i)\\.jpg$", "");
        name = name.replaceFirst("(?i)\\.jpeg$", "");

        // p숫자_ 제거 (대소문자 무시)
        name = name.replaceFirst("(?i)^p\\d+_", "");

        return name;
    }

    /**
     *핵심: 셀 안에서
     * - 사방 최소 여백(padPx)을 확보한 영역에 들어가도록 스케일 결정
     * - 남는 공간을 좌우/상하 동일 분배 → 완전 가운데 정렬
     * - 픽셀 단위로 anchor(dx/dy) 지정
     */
    private static void insertPngCenteredWithPadding(
            Workbook wb,
            Sheet sh,
            Drawing<?> drawing,
            File pngFile,
            Row row,
            int rowIdx,
            int colIdx,
            int padPx
    ) throws Exception {

        // 1) 이미지 픽셀 크기
        BufferedImage bi = ImageIO.read(pngFile);
        if (bi == null) return;

        int imgW = bi.getWidth();
        int imgH = bi.getHeight();
        if (imgW <= 0 || imgH <= 0) return;

        // 2) 셀 크기(픽셀) 계산
        double cellWpx = getColumnWidthPx(sh, colIdx);


        float rowPt = row.getHeightInPoints();
        if (rowPt <= 0) rowPt = sh.getDefaultRowHeightInPoints();

        // points -> pixels (96dpi 기준)
        double cellHpx = rowPt * (96.0 / 72.0);

        // 3) usable 영역(사방 여백 뺀 실제 사용 가능)
        double usableW = Math.max(1, cellWpx - (padPx * 2.0));
        double usableH = Math.max(1, cellHpx - (padPx * 2.0));

        // 4) 스케일(셀 안에 들어가게)
        double scale = Math.min(usableW / imgW, usableH / imgH);
        scale = Math.min(scale, 1.0); // 원본보다 키우진 않음

        double targetW = imgW * scale;
        double targetH = imgH * scale;

        // 5) 중앙 정렬 오프셋
        double offX = (cellWpx - targetW) / 2.0;
        double offY = (cellHpx - targetH) / 2.0;

        // 최소 여백 보장(셀 너무 작을 때 대비)
        offX = Math.max(offX, padPx);
        offY = Math.max(offY, padPx);

        // 6) PNG 바이트 등록
        byte[] bytes;
        try (InputStream is = new FileInputStream(pngFile)) {
            bytes = IOUtils.toByteArray(is);
        }
        int picIdx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);

        // 7) 앵커: "같은 셀" 내부에서 dx/dy로 크기 고정
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(colIdx);
        anchor.setRow1(rowIdx);
        anchor.setCol2(colIdx);
        anchor.setRow2(rowIdx);

        int dx1 = Units.pixelToEMU((int) Math.round(offX));
        int dy1 = Units.pixelToEMU((int) Math.round(offY));
        int dx2 = Units.pixelToEMU((int) Math.round(offX + targetW));
        int dy2 = Units.pixelToEMU((int) Math.round(offY + targetH));

        anchor.setDx1(dx1);
        anchor.setDy1(dy1);
        anchor.setDx2(dx2);
        anchor.setDy2(dy2);

        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

        drawing.createPicture(anchor, picIdx);
    }
}
