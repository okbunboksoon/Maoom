-- replace_dark_symbol.xml을 DB 원본으로 관리하기 위한 테이블.
-- from_symbol은 기존 이미지 심볼명이고, XSL의 replace/@from과 같은 값이다.
-- to_symbol은 치환 후 심볼명이고, XSL의 replace/@to와 같은 값이다.
CREATE TABLE IF NOT EXISTS tb_replace_dark_symbol (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_symbol VARCHAR(255) NOT NULL,
    to_symbol VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_replace_dark_symbol_from (from_symbol),
    INDEX idx_replace_dark_symbol_to (to_symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
