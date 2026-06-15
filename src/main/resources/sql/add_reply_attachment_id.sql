ALTER TABLE tb_comment_attachment
    ADD COLUMN reply_id BIGINT NULL AFTER comment_id;

CREATE INDEX idx_comment_attachment_reply_id
    ON tb_comment_attachment (reply_id);
