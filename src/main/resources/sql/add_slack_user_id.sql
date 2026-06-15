ALTER TABLE tb_user
ADD COLUMN slack_user_id VARCHAR(30) NULL;

UPDATE tb_user
SET slack_user_id = 'U012ABCDEF'
WHERE user_id = 'admin';
