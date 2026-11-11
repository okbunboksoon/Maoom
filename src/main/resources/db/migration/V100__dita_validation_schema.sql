CREATE TABLE IF NOT EXISTS tb_schematron_execution_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    status VARCHAR(30) NOT NULL,
    trigger_user_id VARCHAR(100),
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    elapsed_ms BIGINT,
    input_path VARCHAR(1000),
    requested_input_path VARCHAR(1000),
    execution_mode VARCHAR(30),
    map_path VARCHAR(1000),
    schema_name VARCHAR(255),
    schema_hash VARCHAR(64),
    schema_snapshot_path VARCHAR(1000),
    scope_type VARCHAR(50),
    selected_nodes TEXT,
    input_summary VARCHAR(2000),
    validated_file_count INT,
    issue_count INT,
    report_file_name VARCHAR(255),
    report_file_path VARCHAR(1000),
    error_message TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_schematron_history_started (started_at),
    INDEX idx_schematron_history_status (status),
    INDEX idx_schematron_history_user (trigger_user_id)
);

-- MySQL은 배포 버전에 따라 ADD INDEX IF NOT EXISTS를 지원하지 않으므로
-- information_schema 확인 후 동적 DDL로 인덱스를 한 번만 생성한다.
SET @recent_index_exists = (
    SELECT COUNT(*)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'tb_schematron_execution_history'
       AND index_name = 'idx_schematron_recent_lookup'
);
SET @recent_index_ddl = IF(
    @recent_index_exists = 0,
    'ALTER TABLE tb_schematron_execution_history ADD INDEX idx_schematron_recent_lookup (trigger_user_id, execution_mode, started_at)',
    'SELECT 1'
);
PREPARE recent_index_statement FROM @recent_index_ddl;
EXECUTE recent_index_statement;
DEALLOCATE PREPARE recent_index_statement;

CREATE TABLE IF NOT EXISTS tb_schematron_report_issue_state (
    history_id BIGINT NOT NULL,
    issue_id VARCHAR(32) NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6),
    confirmer_user_id VARCHAR(100),
    confirmer_name VARCHAR(255),
    memo VARCHAR(1000),
    false_positive BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (history_id, issue_id),
    INDEX idx_schematron_report_state_history (history_id)
);

CREATE TABLE IF NOT EXISTS tb_dita_validation_recent_path (
    user_id VARCHAR(100) NOT NULL,
    execution_mode VARCHAR(30) NOT NULL,
    path_hash CHAR(64) NOT NULL,
    input_path VARCHAR(1000) NOT NULL,
    last_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, execution_mode, path_hash),
    INDEX idx_dita_recent_path_time (user_id, execution_mode, last_used_at)
);
