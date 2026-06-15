-- Run once against the maoomtool database.
-- The internal BIGINT key remains unchanged for attachments and replies.
ALTER TABLE tb_comment
    ADD COLUMN comment_code VARCHAR(32) NULL;

UPDATE tb_comment
JOIN (
    SELECT
        comment_id,
        ROW_NUMBER() OVER (
            PARTITION BY pdf_id
            ORDER BY create_dt, comment_id
        ) - 1 AS comment_number
    FROM tb_comment
) numbered
    ON numbered.comment_id = tb_comment.comment_id
SET tb_comment.comment_code = CONCAT(
        'id',
        LPAD(
            CAST(numbered.comment_number AS CHAR),
            GREATEST(
                4,
                CHAR_LENGTH(
                    CAST(numbered.comment_number AS CHAR)
                )
            ),
            '0'
        )
    );

ALTER TABLE tb_comment
    MODIFY comment_code VARCHAR(32) NOT NULL,
    ADD UNIQUE KEY uk_tb_comment_pdf_code (
        pdf_id,
        comment_code
    );
