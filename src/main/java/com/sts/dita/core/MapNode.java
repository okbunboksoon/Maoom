package com.sts.dita.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ditamap의 topicref 한 항목을 표현한다.
 * 화면 선택값이 실제 검사 파일로 정확히 연결되도록 원본 map과 문서 순서를 함께 보관한다.
 */
public final class MapNode {
    private final String id;
    private final String title;
    private final String href;
    private final Path sourceMap;
    private final int order;
    private final List<MapNode> children = new ArrayList<>();

    public MapNode(String id, String title, String href, Path sourceMap, int order) {
        this.id = id;
        this.title = title;
        this.href = href;
        this.sourceMap = sourceMap;
        this.order = order;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String href() { return href; }
    public Path sourceMap() { return sourceMap; }
    public int order() { return order; }
    public List<MapNode> children() { return Collections.unmodifiableList(children); }
    void addChild(MapNode child) { children.add(child); }

    public Path resolvedTarget() {
        // fragment(#id)는 파일 경로가 아니므로 제거한 뒤 topicref가 선언된 map을 기준으로 해석한다.
        if (href == null || href.isBlank()) return null;
        String withoutFragment = href.split("#", 2)[0];
        if (withoutFragment.isBlank()) return sourceMap;
        return sourceMap.getParent().resolve(withoutFragment).normalize().toAbsolutePath();
    }

    @Override public String toString() {
        String label = title == null || title.isBlank() ? "(제목 없음)" : title;
        return href == null || href.isBlank() ? label : label + "  [" + href + "]";
    }
}
