package com.sts.dita.report;

/**
 * Schematron HTML 리포트 렌더링에 필요한 값만 보관한다.
 * 검사 결과 계산과 HTML 템플릿 치환을 분리해 화면 변경의 영향 범위를 줄인다.
 */
public record SchematronReportModel(
        String mapName,
        String reportFileName,
        String historyId,
        String mapPath,
        String fileCount,
        String issueCount,
        String categorySummary,
        String severitySummary,
        String finished,
        String userSummary,
        String mapTreeJson,
        String rows) {
}
