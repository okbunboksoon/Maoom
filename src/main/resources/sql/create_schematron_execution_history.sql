-- Schematron 검사는 기존 프로젝트 실행 로그와 보관 항목이 달라 전용 이력 테이블을 사용한다.
-- report_file_path와 schema_snapshot_path는 검사 결과 및 당시 규칙을 다시 열기 위한 서버 경로다.
CREATE TABLE IF NOT EXISTS tb_schematron_execution_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    status VARCHAR(30) NOT NULL,
    trigger_user_id VARCHAR(100),
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    elapsed_ms BIGINT,
    input_path VARCHAR(1000),
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

-- 결과 HTML의 확인 상태를 실행 이력별로 공유한다.
-- 확인을 해제하면 updated_at과 confirmer 값은 NULL로 저장된다.
CREATE TABLE IF NOT EXISTS tb_schematron_report_issue_state (
    history_id BIGINT NOT NULL,
    issue_id VARCHAR(32) NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6),
    confirmer_user_id VARCHAR(100),
    confirmer_name VARCHAR(255),
    PRIMARY KEY (history_id, issue_id),
    INDEX idx_schematron_report_state_history (history_id)
);
