CREATE TABLE IF NOT EXISTS tb_pdf_favorite (
    favorite_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    folder_id VARCHAR(255) NOT NULL,
    item_name VARCHAR(500) NOT NULL,
    item_type VARCHAR(20) NOT NULL DEFAULT 'pdf',
    create_dt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_pdf_favorite_user_item (user_id, item_id),
    INDEX idx_pdf_favorite_user_create_dt (user_id, create_dt)
);
