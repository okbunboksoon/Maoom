package com.sts.dita.core;

import java.nio.file.Path;
import java.util.List;

/** 검사 대상 ditamap 파일과 화면에 표시할 최상위 topicref 트리를 함께 보관한다. */
public record MapModel(Path mapFile, List<MapNode> roots) { }
