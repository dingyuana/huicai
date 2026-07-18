-- V90__add_audit_fields_to_input_invoice.sql
-- V63 原本应添加 audit 字段到 t_input_invoice，但 V63 放在了 db/ 目录下
-- （非 db/migration/），Flyway 未执行，导致列不存在。
-- 本迁移补建缺失的审计字段。

ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS audited_by BIGINT;
COMMENT ON COLUMN t_input_invoice.audited_by IS '审核人ID';

ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP;
COMMENT ON COLUMN t_input_invoice.audited_at IS '审核时间';