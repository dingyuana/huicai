-- ============================================================
-- V58: 业务单据表添加发票号字段
-- ============================================================

-- 给业务单据主表添加发票号字段，用于从发票导入时记录原始发票号
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS invoice_no VARCHAR(64);

COMMENT ON COLUMN t_business_doc.invoice_no IS '发票号（从发票导入时记录）';

CREATE INDEX IF NOT EXISTS idx_business_doc_invoice_no ON t_business_doc(invoice_no);
