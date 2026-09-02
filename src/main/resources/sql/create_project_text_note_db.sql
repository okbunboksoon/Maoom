CREATE TABLE IF NOT EXISTS tb_project_text_db (
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
    UNIQUE KEY uk_project_text_region_hash (region, hash),
    INDEX idx_project_text_region (region)
);

CREATE TABLE IF NOT EXISTS tb_project_note_db (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(10) NOT NULL DEFAULT 'EG',
    hash VARCHAR(128) NOT NULL,
    note_type VARCHAR(30) NOT NULL,
    note_text LONGTEXT NOT NULL,
    memo VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
            ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_project_note_region_hash (region, hash),
    INDEX idx_project_note_region (region),
    INDEX idx_project_note_type (note_type)
);
