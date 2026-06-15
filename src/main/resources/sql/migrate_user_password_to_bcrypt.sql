ALTER TABLE tb_user
MODIFY COLUMN user_pw VARCHAR(100) NOT NULL;

-- Existing plaintext passwords are converted automatically
-- after each user's first successful login.
