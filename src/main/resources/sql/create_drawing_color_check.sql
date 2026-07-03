-- 도안별 컬러도안 판정(V/X)을 저장하는 기준 테이블.
-- 애플리케이션의 DrawingColorCheckMapper가 이 테이블을 조회하고 수정한다.
CREATE TABLE IF NOT EXISTS tb_drawing_color_check (
    -- PDF/엑셀에 표시되는 도안 ID. 한 도안에는 하나의 판정만 존재한다.
    drawing_name VARCHAR(255) NOT NULL,

    -- V: 컬러도안, X: 컬러도안 아님. 다른 문자는 DB 단계에서도 차단한다.
    check_value CHAR(1) NOT NULL,

    -- 최초 등록 시각. 목록을 최근 등록 순으로 정렬할 때 사용한다.
    reg_dt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 동일한 도안명이 중복 저장되는 것을 막고 UPSERT 판단 기준으로 사용한다.
    PRIMARY KEY (drawing_name),

    -- 서비스 검증을 우회한 잘못된 값도 DB에 들어오지 못하게 하는 안전장치.
    CONSTRAINT chk_drawing_color_check_value
        CHECK (check_value IN ('V', 'X'))
);
