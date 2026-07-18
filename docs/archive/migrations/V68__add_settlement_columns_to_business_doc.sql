-- ============================================================
-- V68: 业务单据表增加结算字段（P34 应收/应付恢复为业务单据）
-- 日期: 2026-06-29
-- 说明: 新增已核销/未核销/到期日字段，扩展状态约束支持核销状态
-- ============================================================

-- 1. 添加已核销金额
ALTER TABLE t_business_doc
ADD COLUMN IF NOT EXISTS settled_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_business_doc.settled_amount IS '已核销金额（P34）';

-- 2. 添加未核销金额
ALTER TABLE t_business_doc
ADD COLUMN IF NOT EXISTS unsettled_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_business_doc.unsettled_amount IS '未核销金额（P34）';

-- 3. 添加到期日
ALTER TABLE t_business_doc
ADD COLUMN IF NOT EXISTS due_date DATE;

COMMENT ON COLUMN t_business_doc.due_date IS '到期日（P34）';

-- 4. 更新状态约束：新增核销相关状态
ALTER TABLE t_business_doc DROP CONSTRAINT IF EXISTS chk_doc_status;
ALTER TABLE t_business_doc ADD CONSTRAINT chk_doc_status CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'VOUCHERED',
               'PARTIALLY_RECONCILED', 'FULLY_RECONCILED',
               'CLOSED', 'REJECTED', 'REVERSED')
);

-- 5. 创建索引
CREATE INDEX IF NOT EXISTS idx_business_doc_settled_amount ON t_business_doc(settled_amount);
CREATE INDEX IF NOT EXISTS idx_business_doc_due_date ON t_business_doc(due_date);
