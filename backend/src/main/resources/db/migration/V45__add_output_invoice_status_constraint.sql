-- V45__add_output_invoice_status_constraint.sql

-- 1. t_output_invoice.status 加 CHECK 约束
ALTER TABLE t_output_invoice
    DROP CONSTRAINT IF EXISTS t_output_invoice_status_check;
ALTER TABLE t_output_invoice
    ADD CONSTRAINT t_output_invoice_status_check
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
    ));

-- 2. status 字段索引（前端按状态过滤）
CREATE INDEX IF NOT EXISTS idx_t_output_invoice_status
    ON t_output_invoice(status);

-- 3. 已有数据校验（确保没有非法状态）
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count
    FROM t_output_invoice
    WHERE status IS NOT NULL
      AND status NOT IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
    );
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 't_output_invoice.status 存在 % 条非法值，迁移前请人工修正', invalid_count;
    END IF;
END $$;

COMMENT ON COLUMN t_output_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED';
