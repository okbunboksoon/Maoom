package com.sts.dita.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
        Document document = parseDocument(normalized);
        return new MapModel(normalized, mapTitle(document, normalized),
                List.copyOf(parseRoots(normalized, "n", new AtomicInteger(), new HashSet<>(), document)));
    }

    private String mapTitle(Document document, Path mapFile) {
        for (Element child : childElements(document.getDocumentElement())) {
            String local = child.getLocalName() == null ? child.getTagName() : child.getLocalName();
            if ("title".equals(local)) return normalizedText(child.getTextContent());
        }
        String title = blankToNull(document.getDocumentElement().getAttribute("title"));
        return title == null ? mapFile.getFileName().toString() : title.trim();
    }

    private List<MapNode> parseRoots(Path mapFile, String idPrefix, AtomicInteger order, Set<Path> activeMaps) throws Exception {
        if (activeMaps.contains(mapFile)) return List.of();
        return parseRoots(mapFile, idPrefix, order, activeMaps, parseDocument(mapFile));
    }

    private List<MapNode> parseRoots(Path mapFile, String idPrefix, AtomicInteger order, Set<Path> activeMaps,
                                     Document document) throws Exception {
        // 같은 map이 현재 재귀 경로에 다시 나타나면 순환 참조이므로 더 내려가지 않는다.
        if (!activeMaps.add(mapFile)) return List.of();
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

    private Document parseDocument(Path mapFile) throws Exception {
        return SecureXml.newDocumentBuilder().parse(mapFile.toFile());
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
        // topicref의 navtitle이 화면용으로 이미 "..." 처리된 경우가 있어 전체 linktext를 먼저 사용한다.
        String linktext = topicMetaText(element, "linktext");
        if (linktext != null) return linktext;
        String navtitleText = topicMetaText(element, "navtitle");
        if (navtitleText != null) return navtitleText;
        String navtitle = blankToNull(element.getAttribute("navtitle"));
        return navtitle == null ? null : navtitle.trim();
    }

    private static String topicMetaText(Element topicref, String name) {
        // 하위 topicref의 메타데이터를 현재 항목의 제목으로 잘못 읽지 않도록 직계 topicmeta만 확인한다.
        for (Element child : childElements(topicref)) {
            String local = child.getLocalName() == null ? child.getTagName() : child.getLocalName();
            if (!"topicmeta".equals(local)) continue;
            for (Element metadata : childElements(child)) {
                String metadataName = metadata.getLocalName() == null
                        ? metadata.getTagName() : metadata.getLocalName();
                if (name.equals(metadataName)) return normalizedText(metadata.getTextContent());
            }
        }
        return null;
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
