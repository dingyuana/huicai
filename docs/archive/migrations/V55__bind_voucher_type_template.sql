-- V55: 凭证类型绑定默认模板
-- 选凭证类型时自动加载对应模板的分录行
ALTER TABLE t_voucher_type
    ADD COLUMN template_id BIGINT;

ALTER TABLE t_voucher_type
    ADD CONSTRAINT fk_voucher_type_template
    FOREIGN KEY (template_id) REFERENCES t_voucher_template (id) ON DELETE SET NULL;

CREATE INDEX idx_voucher_type_template ON t_voucher_type (template_id);
