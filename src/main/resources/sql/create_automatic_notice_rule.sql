CREATE TABLE IF NOT EXISTS tb_automatic_notice_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(10) NOT NULL,
    match_type VARCHAR(20) NOT NULL,
    match_key VARCHAR(500) NOT NULL,
    detail LONGTEXT,
    teams LONGTEXT,
    priority INT NOT NULL DEFAULT 100,
    alias_text LONGTEXT,
    enabled CHAR(1) NOT NULL DEFAULT 'Y',
    memo VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
            ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_automatic_notice_region_type_key (
        region,
        match_type,
        match_key
    ),
    INDEX idx_automatic_notice_region (region),
    INDEX idx_automatic_notice_enabled (enabled)
);
