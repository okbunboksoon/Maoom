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
