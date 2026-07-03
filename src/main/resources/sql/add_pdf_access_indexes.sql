-- Run once in existing environments. These indexes keep object-level
-- authorization checks index-only or near index-only.
CREATE INDEX idx_pdf_owner_pdf
    ON tb_pdf (reg_user_id, pdf_id);

CREATE INDEX idx_pdf_owner_drive_file
    ON tb_pdf (reg_user_id, drive_file_id);
