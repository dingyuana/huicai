-- ============================================================
-- V102: S-26 Agency 分支 — 业务表加 enterprise_id（第一批：科目/期间/凭证类）
-- 关联 SPEC: S-26-agency-branch-development.md §5.1
-- 关联架构: 多租户架构设计.md §2.3
-- ============================================================

-- 科目体系
ALTER TABLE t_subject         ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_subject_balance ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_aux_dimension   ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 期间
ALTER TABLE t_period ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 凭证
ALTER TABLE t_voucher              ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_voucher_entry        ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_voucher_template     ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_voucher_template_line ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_voucher_type         ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 摘要库
ALTER TABLE t_summary_lib ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 索引
CREATE INDEX IF NOT EXISTS idx_t_subject_enterprise         ON t_subject(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_subject_balance_enterprise ON t_subject_balance(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_aux_dimension_enterprise   ON t_aux_dimension(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_period_enterprise          ON t_period(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_voucher_enterprise         ON t_voucher(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_voucher_entry_enterprise   ON t_voucher_entry(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_voucher_template_enterprise ON t_voucher_template(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_voucher_type_enterprise    ON t_voucher_type(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_summary_lib_enterprise     ON t_summary_lib(enterprise_id);
