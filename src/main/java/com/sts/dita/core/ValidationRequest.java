package com.sts.dita.core;

import java.nio.file.Path;
import java.util.Set;

/** 선택한 ditamap, Schematron, 검사 범위 및 map 자체 검사 여부를 코어 엔진에 전달한다. */
public record ValidationRequest(
        Path mapFile,
        Path schematronFile,
        ValidationScope scope,
        Set<String> selectedNodeIds,
        boolean validateMap) {
}
