-- V48__add_voucher_template_dimensions.sql
-- P26 凭证模板引擎: 给 t_voucher_template / t_voucher_template_line 新增维度字段

-- 1. t_voucher_template 新增维度字段
ALTER TABLE t_voucher_template
    ADD COLUMN IF NOT EXISTS source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS match_priority INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_voucher_template.source IS '来源: BANK_STMT/BUSINESS_DOC/INVOICE/PERIOD_CLOSE';
COMMENT ON COLUMN t_voucher_template.business_type IS '业务类型: RECEIPT/PAYMENT/EXPENSE/INVOICE_OUT/...';
COMMENT ON COLUMN t_voucher_template.direction IS '方向: in/out/空(双向)';
COMMENT ON COLUMN t_voucher_template.match_priority IS '匹配优先级, 越小越优先';

CREATE INDEX IF NOT EXISTS idx_vt_dimensions
    ON t_voucher_template(source, business_type, is_active, match_priority);

-- 2. t_voucher_template_line 新增辅助核算字段
ALTER TABLE t_voucher_template_line
    ADD COLUMN IF NOT EXISTS assist_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS assist_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_voucher_template_line.assist_type IS '辅助核算类型: CUSTOMER/VENDOR/DEPT/EMPLOYEE/PROJECT';
COMMENT ON COLUMN t_voucher_template_line.assist_required IS '是否必填辅助核算(强校验)';

-- 3. 已有分类模板补充 source 字段（向后兼容）
UPDATE t_voucher_template SET source = 'BANK_STMT' WHERE source IS NULL AND classification IS NOT NULL;