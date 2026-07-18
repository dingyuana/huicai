-- V73__migrate_receivable_payable_to_business_doc.sql
-- 说明：将现有 t_receivable/t_payable 数据迁移到 t_business_doc
-- 前提：已执行 V68（结算字段）+ V72（business_doc_id 关联字段）
-- 回滚：DELETE FROM t_business_doc WHERE doc_type IN ('INVOICE_OUT','INVOICE_IN') AND source = 'IMPORTED';

-- ============================================================
-- 1. 迁移应收数据到业务单据（INVOICE_OUT）
-- ============================================================
INSERT INTO t_business_doc (
    doc_no, doc_type, doc_date, period, amount, status,
    customer_id, summary, invoice_no, source,
    voucher_id, voucher_no,
    settled_amount, unsettled_amount, due_date,
    created_by, created_at, updated_at, version
)
SELECT
    COALESCE(r.receivable_no, 'YS' || r.period || LPAD(CAST(r.id AS TEXT), 4, '0')),
    'INVOICE_OUT',
    r.tx_date,
    r.period,
    r.amount,
    CASE
        WHEN r.status = 'REVERSED' THEN 'REVERSED'
        WHEN r.settled_amount >= r.amount AND r.amount > 0 THEN 'FULLY_RECONCILED'
        WHEN r.settled_amount > 0 THEN 'PARTIALLY_RECONCILED'
        WHEN r.voucher_id IS NOT NULL THEN 'VOUCHERED'
        WHEN r.status = 'CONFIRMED' THEN 'APPROVED'
        ELSE 'DRAFT'
    END,
    r.customer_id,
    r.summary,
    r.invoice_no,
    'IMPORTED',
    r.voucher_id,
    r.voucher_no,
    COALESCE(r.settled_amount, 0),
    COALESCE(r.unsettled_amount, r.amount),
    r.due_date,
    1, r.created_at, r.updated_at, 1
FROM t_receivable r
WHERE NOT EXISTS (
    SELECT 1 FROM t_business_doc d
    WHERE d.doc_type = 'INVOICE_OUT' AND d.invoice_no = r.invoice_no
);

-- ============================================================
-- 2. 迁移应付数据到业务单据（INVOICE_IN）
-- ============================================================
INSERT INTO t_business_doc (
    doc_no, doc_type, doc_date, period, amount, status,
    supplier_id, summary, invoice_no, source,
    voucher_id, voucher_no,
    settled_amount, unsettled_amount, due_date,
    created_by, created_at, updated_at, version
)
SELECT
    COALESCE(p.doc_no, 'YF' || p.period || LPAD(CAST(p.id AS TEXT), 4, '0')),
    'INVOICE_IN',
    p.tx_date,
    p.period,
    p.amount,
    CASE
        WHEN p.status = 'REVERSED' THEN 'REVERSED'
        WHEN p.settled_amount >= p.amount AND p.amount > 0 THEN 'FULLY_RECONCILED'
        WHEN p.settled_amount > 0 THEN 'PARTIALLY_RECONCILED'
        WHEN p.voucher_id IS NOT NULL THEN 'VOUCHERED'
        WHEN p.status = 'CONFIRMED' THEN 'APPROVED'
        ELSE 'DRAFT'
    END,
    p.vendor_id,
    p.summary,
    p.invoice_no,
    'IMPORTED',
    p.voucher_id,
    p.voucher_no,
    COALESCE(p.settled_amount, 0),
    COALESCE(p.unsettled_amount, p.amount),
    p.due_date,
    1, p.created_at, p.updated_at, 1
FROM t_payable p
WHERE NOT EXISTS (
    SELECT 1 FROM t_business_doc d
    WHERE d.doc_type = 'INVOICE_IN' AND d.invoice_no = p.invoice_no
);

-- ============================================================
-- 3. 回填核销明细的 business_doc_id（V72 已建列，补数据）
-- ============================================================
UPDATE t_arap_settlement_entry e
SET business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_receivable r ON d.invoice_no = r.invoice_no
    WHERE e.receivable_id = r.id
    AND d.doc_type = 'INVOICE_OUT'
    LIMIT 1
)
WHERE e.receivable_id IS NOT NULL AND e.business_doc_id IS NULL;

UPDATE t_arap_settlement_entry e
SET business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_payable p ON d.invoice_no = p.invoice_no
    WHERE e.payable_id = p.id
    AND d.doc_type = 'INVOICE_IN'
    LIMIT 1
)
WHERE e.payable_id IS NOT NULL AND e.business_doc_id IS NULL;

-- ============================================================
-- 4. 回填 reconciliation_log 的 target_business_doc_id（V72 已建列，补数据）
-- ============================================================
UPDATE t_reconciliation_log l
SET target_business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_receivable r ON d.invoice_no = r.invoice_no
    WHERE l.target_doc_id = r.id
    AND l.target_doc_type = 'INVOICE_OUT'
    LIMIT 1
)
WHERE l.target_doc_type = 'INVOICE_OUT' AND l.target_business_doc_id IS NULL;

UPDATE t_reconciliation_log l
SET target_business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_payable p ON d.invoice_no = p.invoice_no
    WHERE l.target_doc_id = p.id
    AND l.target_doc_type = 'INVOICE_IN'
    LIMIT 1
)
WHERE l.target_doc_type = 'INVOICE_IN' AND l.target_business_doc_id IS NULL;

-- ============================================================
-- 5. 迁移审计日志
-- ============================================================
DO $$
DECLARE
    recv_count BIGINT;
    pay_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO recv_count FROM t_receivable;
    SELECT COUNT(*) INTO pay_count FROM t_payable;
    RAISE NOTICE 'V73 迁移完成: t_receivable=% 条, t_payable=% 条',
        recv_count, pay_count;
END $$;