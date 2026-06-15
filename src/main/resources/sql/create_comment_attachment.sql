CREATE TABLE IF NOT EXISTS tb_comment_attachment (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    reply_id BIGINT NULL,
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    content_type VARCHAR(200),
    file_size BIGINT NOT NULL,
    uploader_id VARCHAR(255) NOT NULL,
    create_dt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id),
    INDEX idx_comment_attachment_comment_id (comment_id),
    INDEX idx_comment_attachment_reply_id (reply_id)
);
