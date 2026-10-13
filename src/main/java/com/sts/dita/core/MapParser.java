package com.sts.dita.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ditamap과 연결된 하위 ditamap을 재귀적으로 읽어 검사 범위 선택용 트리를 만든다.
 * 외부 URL은 로컬 검사 대상에서 제외하고, 순환 map 참조는 activeMaps로 차단한다.
 */
public final class MapParser {
    public MapModel parse(Path mapFile) throws Exception {
        Path normalized = mapFile.toAbsolutePath().normalize();
        return new MapModel(normalized, List.copyOf(parseRoots(normalized, "n", new AtomicInteger(), new HashSet<>())));
    }

    private List<MapNode> parseRoots(Path mapFile, String idPrefix, AtomicInteger order, Set<Path> activeMaps) throws Exception {
        // 같은 map이 현재 재귀 경로에 다시 나타나면 순환 참조이므로 더 내려가지 않는다.
        if (!activeMaps.add(mapFile)) return List.of();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document document = builder.parse(mapFile.toFile());
        List<MapNode> roots = new ArrayList<>();
        int rootIndex = 0;
        for (Element element : childElements(document.getDocumentElement())) {
            if (isTopicReference(element)) {
                roots.add(parseNode(element, mapFile, idPrefix + rootIndex++, order, activeMaps));
            }
        }
        activeMaps.remove(mapFile);
        return roots;
    }

    private MapNode parseNode(Element element, Path map, String id, AtomicInteger order, Set<Path> activeMaps) throws Exception {
        String rawHref = blankToNull(element.getAttribute("href"));
        String scope = element.getAttribute("scope");
        String href = "external".equals(scope) || isRemote(rawHref) ? null : rawHref;
        String title = findTitle(element);
        if (title == null) title = rawHref;
        MapNode result = new MapNode(id, title, href, map, order.getAndIncrement());
        int childIndex = 0;
        for (Element child : childElements(element)) {
            if (isTopicReference(child)) {
                result.addChild(parseNode(child, map, id + "." + childIndex++, order, activeMaps));
            }
        }
        if (href != null && isSubmap(element, href)) {
            // mapref/ditamap topicref의 자식 구조도 현재 트리 아래에 이어 붙인다.
            Path submap = map.getParent().resolve(href.split("#", 2)[0]).normalize().toAbsolutePath();
            if (java.nio.file.Files.isRegularFile(submap)) {
                for (MapNode child : parseRoots(submap, id + ".m", order, activeMaps)) result.addChild(child);
            }
        }
        return result;
    }

    private static boolean isSubmap(Element element, String href) {
        String format = element.getAttribute("format");
        return "ditamap".equalsIgnoreCase(format) || href.toLowerCase().split("#", 2)[0].endsWith(".ditamap");
    }

    private static boolean isRemote(String href) {
        if (href == null) return false;
        String lower = href.toLowerCase();
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:");
    }

    private static String findTitle(Element element) {
        NodeList navtitles = element.getElementsByTagName("navtitle");
        if (navtitles.getLength() > 0) return normalizedText(navtitles.item(0).getTextContent());
        String navtitle = blankToNull(element.getAttribute("navtitle"));
        return navtitle == null ? null : navtitle.trim();
    }

    private static boolean isTopicReference(Element element) {
        String local = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
        if ("topicref".equals(local) || "mapref".equals(local)) return true;
        String cls = element.getAttribute("class");
        return cls.contains(" map/topicref ") || cls.contains(" mapgroup-d/mapref ");
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    private static String normalizedText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
