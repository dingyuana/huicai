-- ============================================================
-- V17: P1 银行流水分类引擎前置迁移 (方案 C — 保留 V5, 仅新增)
-- ============================================================
-- 上游 SPEC: docs/specs/P1-bank-import-classification.md §1.3
-- 需求分析: docs/需求分析/01-银行流水智能处理.md §4
-- 任务书:   docs/tasks/P1-V17-migration_任务书_2026-06-12.md
--
-- 策略: V5 全部保留, 仅新增 P1 字段/约束/索引
-- 不动现有 tx_type / match_status / autoMatch / confirmMatch 逻辑
-- ============================================================

-- P1 业务分类相关
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS direction         VARCHAR(4);       -- 业务方向 in/out (分类引擎入口)
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS batch_id          VARCHAR(50);      -- 导入批号
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS classification    VARCHAR(50);      -- 业务分类 bank_fee/.../pending
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS rule_id           BIGINT;           -- 命中规则 ID

-- P1 AI 增强相关
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_confidence          INT       DEFAULT 0;    -- AI 置信度 0-100
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_suggested_action    VARCHAR(50);              -- AI 建议分类
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_business_scene      VARCHAR(100);             -- AI 业务场景

-- P1 出纳确认相关 (review_status 独立于 match_status)
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS review_status    VARCHAR(20) DEFAULT 'PENDING'; -- 出纳确认状态
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_by      BIGINT;                        -- 审核人
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMP;                     -- 审核时间

-- 约束 (PostgreSQL 不支持 ADD CONSTRAINT IF NOT EXISTS, 使用 DO 块实现幂等)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stmt_review_status') THEN
        ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_review_status
            CHECK (review_status IN ('PENDING', 'CONFIRMED', 'RECLASSIFIED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stmt_direction') THEN
        ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_direction
            CHECK (direction IN ('in', 'out') OR direction IS NULL);
    END IF;
END $$;

-- 索引 (高频查询)
CREATE INDEX IF NOT EXISTS idx_stmt_review_status   ON t_bank_statement(review_status);
CREATE INDEX IF NOT EXISTS idx_stmt_classification   ON t_bank_statement(classification);
CREATE INDEX IF NOT EXISTS idx_stmt_batch_id         ON t_bank_statement(batch_id);

-- 字段注释
COMMENT ON COLUMN t_bank_statement.direction            IS '业务方向: in-收入, out-支出 (分类引擎入口参数)';
COMMENT ON COLUMN t_bank_statement.batch_id             IS '导入批号';
COMMENT ON COLUMN t_bank_statement.classification       IS '业务分类: bank_fee/interest_income/business_receipt/business_payment/internal_transfer/tax_payment/social_security/insurance_fee/pending';
COMMENT ON COLUMN t_bank_statement.rule_id              IS '命中规则 ID (关联 t_classification_rule)';
COMMENT ON COLUMN t_bank_statement.ai_confidence        IS 'AI 置信度 0-100';
COMMENT ON COLUMN t_bank_statement.ai_suggested_action  IS 'AI 建议分类';
COMMENT ON COLUMN t_bank_statement.ai_business_scene    IS 'AI 业务场景';
COMMENT ON COLUMN t_bank_statement.review_status        IS '出纳确认状态: PENDING-待确认, CONFIRMED-已确认, RECLASSIFIED-已重分类';
COMMENT ON COLUMN t_bank_statement.reviewed_by          IS '审核人';
COMMENT ON COLUMN t_bank_statement.reviewed_at          IS '审核时间';
