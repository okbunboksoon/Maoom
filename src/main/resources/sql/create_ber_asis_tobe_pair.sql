CREATE TABLE IF NOT EXISTS tb_ber_asis_tobe_pair (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(10) NOT NULL,
    hash VARCHAR(128) NOT NULL,
    old_text LONGTEXT,
    new_text LONGTEXT NOT NULL,
    memo VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
            ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_ber_asis_tobe_region_hash (region, hash),
    INDEX idx_ber_asis_tobe_region (region)
);
