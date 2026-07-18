-- V89__add_status_to_input_invoice.sql
-- P40: 进项发票状态机 - 新增 status 列 + reject_reason
-- 详见 docs/specs/P40-input-invoice-state-machine.md

-- 1. 新增 status 列（审核状态机，与销项对称）
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING_CONFIRM';
COMMENT ON COLUMN t_input_invoice.status IS '审核状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED';

-- 2. 新增驳回原因列
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
COMMENT ON COLUMN t_input_invoice.reject_reason IS '审核驳回原因';

-- 3. CHECK 约束（与销项一致，使用 InvoiceStatus 8态）
ALTER TABLE t_input_invoice ADD CONSTRAINT chk_input_invoice_status
    CHECK (status IN ('PENDING_CONFIRM','PENDING_REVIEW','CONFIRMED','VOUCHERED','FULLY_RECONCILED','PARTIALLY_RECONCILED','VOIDED','REVERSED'));

-- 4. 历史数据迁移
-- 4a. 已有 doc_id + voucher_id 的导入数据 -> VOUCHERED
UPDATE t_input_invoice SET status = 'VOUCHERED'
    WHERE status IS NULL AND doc_id IS NOT NULL AND voucher_id IS NOT NULL;
-- 4b. 已有 doc_id 但无 voucher_id -> CONFIRMED
UPDATE t_input_invoice SET status = 'CONFIRMED'
    WHERE status IS NULL AND doc_id IS NOT NULL;
-- 4c. 其余 -> PENDING_CONFIRM
UPDATE t_input_invoice SET status = 'PENDING_CONFIRM'
    WHERE status IS NULL;

-- 5. 索引
CREATE INDEX IF NOT EXISTS idx_input_invoice_status ON t_input_invoice(status);
