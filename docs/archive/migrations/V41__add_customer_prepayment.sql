-- G2+G7: 客户预收款 — 扩展 t_prepayment 支持客户侧
-- 原表只支持供应商预付 (vendor_id NOT NULL), 现增加客户预收支持

ALTER TABLE t_prepayment
    ADD COLUMN customer_id BIGINT,
    ALTER COLUMN vendor_id DROP NOT NULL;

CREATE INDEX idx_prepay_customer ON t_prepayment(customer_id);
