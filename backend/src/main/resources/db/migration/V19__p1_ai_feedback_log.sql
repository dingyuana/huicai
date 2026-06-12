-- ============================================================
-- V19: P1 AI 反馈日志表
-- AI 分类人工修正记录, 用于模型学习 (SPEC §1.2)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_ai_feedback_log (
    id                     BIGINT PRIMARY KEY,
    tenant_id              BIGINT        NOT NULL,
    bank_txn_id            BIGINT        NOT NULL,
    ai_suggested_action    VARCHAR(50),
    ai_confidence          INTEGER,
    ai_business_scene      VARCHAR(100),
    human_action           VARCHAR(50)   NOT NULL,
    human_modified_fields  JSONB,
    created_by             BIGINT,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_feedback_txn    FOREIGN KEY (bank_txn_id) REFERENCES t_bank_statement(id),
    CONSTRAINT chk_ai_feedback_action CHECK (human_action IN ('CONFIRM_AI', 'MANUAL_RECLASSIFY', 'IGNORE_AI', 'BATCH_CONFIRM'))
);

CREATE INDEX IF NOT EXISTS idx_ai_feedback_txn    ON t_ai_feedback_log(bank_txn_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_tenant ON t_ai_feedback_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_created ON t_ai_feedback_log(created_at);

COMMENT ON TABLE  t_ai_feedback_log IS 'P1 AI 分类反馈日志 (人工修正记录, 用于模型学习)';

COMMENT ON COLUMN t_ai_feedback_log.id                   IS '主键';
COMMENT ON COLUMN t_ai_feedback_log.tenant_id            IS '租户 ID';
COMMENT ON COLUMN t_ai_feedback_log.bank_txn_id          IS '银行流水 ID (关联 t_bank_statement)';
COMMENT ON COLUMN t_ai_feedback_log.ai_suggested_action  IS 'AI 建议分类';
COMMENT ON COLUMN t_ai_feedback_log.ai_confidence        IS 'AI 置信度 0-100';
COMMENT ON COLUMN t_ai_feedback_log.ai_business_scene    IS 'AI 业务场景';
COMMENT ON COLUMN t_ai_feedback_log.human_action         IS '人工操作: CONFIRM_AI/MANUAL_RECLASSIFY/IGNORE_AI/BATCH_CONFIRM';
COMMENT ON COLUMN t_ai_feedback_log.human_modified_fields IS '人工修改字段 JSONB';
COMMENT ON COLUMN t_ai_feedback_log.created_by           IS '创建人';
COMMENT ON COLUMN t_ai_feedback_log.created_at           IS '创建时间';