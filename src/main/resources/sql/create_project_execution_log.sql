-- 사용자가 화면에서 실행한 큰 작업의 시작/성공/실패 이력을 저장한다.
-- 관리자 페이지는 나중에 이 테이블을 조회해서 최근 실행 내역, 실패 원인,
-- 입력/출력 경로를 보여주면 된다.
CREATE TABLE IF NOT EXISTS tb_project_execution_log (
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 작업 묶음. 예: REVISION, DITAMAP_BUILDER, COLOR_CHECK, COMMENT_EXPORT
    job_type VARCHAR(80) NOT NULL,

    -- 관리자 화면에 그대로 보여줄 사람이 읽기 쉬운 작업명.
    job_name VARCHAR(200) NOT NULL,

    -- STARTED로 INSERT하고, 작업 종료 후 SUCCESS 또는 FAILED로 UPDATE한다.
    status VARCHAR(30) NOT NULL,

    -- 로그인 사용자 ID. 로그인 없이 실행된 배치/테스트는 NULL일 수 있다.
    trigger_user_id VARCHAR(100),

    -- 작업의 기준 입력과 결과 위치. 다운로드 작업은 output_path에 파일명을 저장한다.
    input_path VARCHAR(1000),
    output_path VARCHAR(1000),

    -- 짧은 진행 설명과 실패 상세 메시지. 실패 원문은 길 수 있어 TEXT로 둔다.
    message VARCHAR(1000),
    error_message TEXT,

    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    elapsed_ms BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_project_execution_log_started (started_at),
    INDEX idx_project_execution_log_type_status (job_type, status),
    INDEX idx_project_execution_log_user (trigger_user_id)
);
