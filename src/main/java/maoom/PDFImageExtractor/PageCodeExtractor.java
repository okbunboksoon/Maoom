package maoom.PDFImageExtractor;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PageCodeExtractor {

    /**
     * 도안명 코드 패턴 (대문자/숫자/언더스코어 + 마지막에 소문자(nn) 등도 허용)
     */
	private static final Pattern CODE_PATTERN =
	    Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*\\b");

    /**
     * 페이지별 도안명 코드 추출 (중복 제거 + 등장 순서 유지)
     */
    public static Map<Integer, List<String>> extractCodesByPage(PDDocument doc) throws IOException {
        Map<Integer, List<String>> result = new LinkedHashMap<>();

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);

        int pageCount = doc.getNumberOfPages();
        for (int page = 1; page <= pageCount; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);

            String text = stripper.getText(doc);

            // 등장 순서 유지 + 중복 제거
            LinkedHashSet<String> orderedUnique = new LinkedHashSet<>();

            Matcher m = CODE_PATTERN.matcher(text);
            while (m.find()) {
                String code = m.group();

                // 안전하게 트림
                if (code != null) code = code.trim();

                // 혹시 모를 잡음 방지: 너무 짧은 건 제외
                if (code != null && code.length() >= 8) {
                    orderedUnique.add(code);
                }
            }

            result.put(page, new ArrayList<>(orderedUnique));
        }

        return result;
    }
}
