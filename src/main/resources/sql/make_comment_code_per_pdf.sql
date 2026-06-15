-- Renumber existing comments from 0000 for each PDF.
ALTER TABLE tb_comment
    DROP INDEX uk_tb_comment_comment_code;

UPDATE tb_comment comment
JOIN (
    SELECT
        comment_id,
        ROW_NUMBER() OVER (
            PARTITION BY pdf_id
            ORDER BY create_dt, comment_id
        ) - 1 AS comment_number
    FROM tb_comment
) numbered
    ON numbered.comment_id = comment.comment_id
SET comment.comment_code = CONCAT(
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
