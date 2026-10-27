package com.sts.dita.core;

/** 장시간 검사 중 사용자 취소나 제한 시간 초과를 확인하는 콜백이다. */
@FunctionalInterface
public interface ValidationCancellation {
    /** 취소 검사가 필요 없는 독립 실행기에서 사용하는 빈 콜백이다. */
    ValidationCancellation NONE = () -> { };

    /** 취소 조건이면 호출부가 검사 중단 예외를 발생시킨다. */
    void check();
}
