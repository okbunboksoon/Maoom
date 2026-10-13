package com.sts.dita.xref;

import com.sts.dita.core.MapModel;
import com.sts.dita.core.MapNode;
import com.sts.dita.core.MapParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class XrefValidationEngine {
    private final MapParser mapParser = new MapParser();

    public XrefValidationResult validate(Path mapFile) throws Exception {
        MapModel map = mapParser.parse(mapFile);
        LinkedHashSet<Path> linkedFiles = new LinkedHashSet<>();
        for (MapNode root : map.roots()) collectLinked(root, linkedFiles);
        Map<Path, Set<String>> idCache = new HashMap<>();
        List<XrefCheckResult> results = new ArrayList<>();
        for (Path source : linkedFiles) {
            if (isTopic(source) && Files.isRegularFile(source)) {
                for (RawXref raw : readXrefs(source)) results.add(check(raw, linkedFiles, idCache));
            }
        }
        return new XrefValidationResult(map.mapFile(), Instant.now(), linkedFiles.size(), List.copyOf(results));
    }

    private XrefCheckResult check(RawXref raw, Set<Path> linkedFiles, Map<Path, Set<String>> idCache) {
        String href = raw.href();
        boolean excluded = "html".equalsIgnoreCase(raw.format()) || "external".equalsIgnoreCase(raw.scope())
                || href.matches("(?i)^(https?|mailto):.*");
        if (excluded) return result(raw, null, "", "검사 제외", "검사 제외", "검사 제외", "검사 제외", "외부/HTML 링크");

        List<String> messages = new ArrayList<>();
        String formatStatus = "정상";
        if (href.contains("%")) { formatStatus = "오류"; messages.add("href에 '%' 문자가 포함됨"); }
        if (!href.contains("#")) { formatStatus = "오류"; messages.add("'#id' 형식이 없음"); }
        String[] parts = href.split("#", 2);
        String filePart = parts[0];
        String fragment = parts.length > 1 ? parts[1] : "";
        if (fragment.contains("/")) { formatStatus = "오류"; messages.add("'#' 뒤에는 topic ID만 허용되며 element ID는 사용할 수 없음"); }
        Path target = filePart.isBlank() ? raw.source() : raw.source().getParent().resolve(filePart).normalize().toAbsolutePath();
        String fileStatus = Files.isRegularFile(target) ? "정상" : "파일 없음";
        if (!"정상".equals(fileStatus)) messages.add("대상 파일이 없음");
        String mapStatus = containsPath(linkedFiles, target) ? "정상" : "미연결";
        if ("미연결".equals(mapStatus) && Files.isRegularFile(target)) messages.add("대상 파일이 ditamap에 연결되지 않음");
        String idStatus = "확인 불가";
        if (Files.isRegularFile(target) && !fragment.isBlank()) {
            try {
                Set<String> ids = idCache.computeIfAbsent(key(target), p -> {
                    try { return readIds(target); } catch (Exception e) { return Set.of(); }
                });
                boolean found = !fragment.contains("/") && ids.contains(fragment);
                idStatus = found ? "정상" : "ID 없음";
                if (!found) messages.add("대상 ID가 없음");
            } catch (Exception ignored) { idStatus = "확인 불가"; }
        }
        return result(raw, target, fragment, formatStatus, fileStatus, mapStatus, idStatus,
                messages.isEmpty() ? "정상" : String.join("; ", messages));
    }

    private static XrefCheckResult result(RawXref raw, Path target, String id, String format, String file,
                                          String map, String idStatus, String message) {
        return new XrefCheckResult(raw.source(), raw.line(), raw.href(), target, id,
                format, file, map, idStatus, message);
    }

    private static void collectLinked(MapNode node, Set<Path> result) {
        Path target = node.resolvedTarget();
        if (target != null) result.add(key(target));
        for (MapNode child : node.children()) collectLinked(child, result);
    }

    private static boolean containsPath(Set<Path> paths, Path target) {
        Path wanted = key(target);
        return paths.stream().anyMatch(p -> key(p).equals(wanted));
    }

    private static Path key(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static boolean isTopic(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dita") || name.endsWith(".xml");
    }

    private static List<RawXref> readXrefs(Path file) throws Exception {
        List<RawXref> result = new ArrayList<>();
        parse(file, new DefaultHandler() {
            private Locator locator;
            public void setDocumentLocator(Locator value) { locator = value; }
            public void startElement(String uri, String local, String qName, Attributes a) {
                String name = local == null || local.isBlank() ? qName : local;
                if ("xref".equals(name) && a.getValue("href") != null) {
                    result.add(new RawXref(file, locator == null ? -1 : locator.getLineNumber(),
                            a.getValue("href"), a.getValue("format"), a.getValue("scope")));
                }
            }
        });
        return result;
    }

    private static Set<String> readIds(Path file) throws Exception {
        Set<String> ids = new HashSet<>();
        parse(file, new DefaultHandler() {
            public void startElement(String uri, String local, String qName, Attributes a) {
                String id = a.getValue("id"); if (id != null && !id.isBlank()) ids.add(id);
            }
        });
        return ids;
    }

    private static void parse(Path file, DefaultHandler handler) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        reader.setContentHandler(handler);
        reader.parse(file.toUri().toString());
    }

    private record RawXref(Path source, int line, String href, String format, String scope) { }
}
