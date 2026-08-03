CREATE TABLE IF NOT EXISTS tb_ber_asis_tobe_backup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region VARCHAR(10) NOT NULL,
    source_file_name VARCHAR(255),
    job_dir VARCHAR(1000),
    created_by VARCHAR(100),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    total_count INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    INDEX idx_ber_backup_region_created (region, created_at)
);

CREATE TABLE IF NOT EXISTS tb_ber_asis_tobe_backup_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    backup_id BIGINT NOT NULL,
    region VARCHAR(10) NOT NULL,
    hash VARCHAR(128) NOT NULL,
    old_text LONGTEXT,
    new_text LONGTEXT NOT NULL,
    original_created_at DATETIME(6),
    original_updated_at DATETIME(6),

    PRIMARY KEY (id),
    INDEX idx_ber_backup_item_backup (backup_id),
    CONSTRAINT fk_ber_backup_item_backup
        FOREIGN KEY (backup_id)
        REFERENCES tb_ber_asis_tobe_backup (id)
        ON DELETE CASCADE
);
