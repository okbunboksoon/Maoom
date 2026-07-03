-- 2안 Ditamap Builder에서 자동 체크할 법규 대상 topic 파일명 목록.
-- LM 법규 마스터 DITAMAP의 3레벨 이하 t*.dita를 저장하고,
-- 해당 topic이 들어가야 할 법규 1~2레벨 부모 파일명도 함께 보관한다.
CREATE TABLE IF NOT EXISTS tb_ditamap_legal_target (
    -- 실제 매뉴얼 DITAMAP에서 찾을 topic 파일명. 예: t00227.dita
    file_name VARCHAR(255) NOT NULL,

    -- 화면 확인용 제목. 자동 체크 판단은 file_name을 우선 사용한다.
    title VARCHAR(1000) NOT NULL DEFAULT '',

    -- 법규 마스터의 1레벨 부모 파일명. 예: legal_01.dita
    parent_l1_file VARCHAR(255) NOT NULL DEFAULT '',

    -- 법규 마스터의 2레벨 부모 파일명. 예: legal_01_01.dita
    parent_l2_file VARCHAR(255) NOT NULL DEFAULT '',

    -- LM 법규 마스터 DITAMAP에서의 원래 레벨. 3 이상이 자동 체크 대상이다.
    level_no INT NOT NULL DEFAULT 3,

    reg_dt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (file_name),
    INDEX idx_ditamap_legal_target_parent (parent_l1_file, parent_l2_file)
);
