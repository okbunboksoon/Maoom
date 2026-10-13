package com.sts.dita.core;

/** 검사 화면에서 선택할 수 있는 DITA 파일 포함 범위다. */
public enum ValidationScope {
    /** ditamap에 연결된 모든 DITA 파일 */
    ALL,
    /** 사용자가 체크한 항목만 */
    SELECTED_ONLY,
    /** 사용자가 체크한 항목과 그 아래 모든 자손 */
    SELECTED_WITH_DESCENDANTS
}
