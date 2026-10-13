package com.sts.dita.core;

import java.nio.file.Path;

/** 파일 단위 검사 진행 상태를 웹 화면으로 전달하기 위한 콜백이다. */
@FunctionalInterface
public interface ValidationProgressListener {
    /** 진행 표시가 필요 없는 독립 실행기와 기존 호출부에서 사용하는 빈 콜백이다. */
    ValidationProgressListener NONE = (completed, total, currentFile) -> { };

    /** 현재 파일 처리 전후에 완료 수, 전체 수, 현재 파일을 전달한다. */
    void onProgress(int completed, int total, Path currentFile);
}
