package com.sts.dita.core;

import java.nio.file.Path;
import java.util.List;

/** 검사 대상 ditamap 파일·제목과 화면에 표시할 최상위 topicref 트리를 함께 보관한다. */
public record MapModel(Path mapFile, String title, List<MapNode> roots) { }
