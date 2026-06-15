ALTER TABLE tb_user
    ADD COLUMN profile_image_stored_name VARCHAR(100) NULL
        AFTER slack_user_id,
    ADD COLUMN profile_image_content_type VARCHAR(100) NULL
        AFTER profile_image_stored_name;
