-- V63__add_audit_fields_to_core_tables.sql
-- ⚠️ 本文件放错了目录（db/ 而非 db/migration/），Flyway 从未执行！
-- t_input_invoice 的 audited_by/audited_at 已由 V90 补建。
-- t_output_invoice 的 audited_by/audited_at 仍标记为 @TableField(exist = false)，未报错但不可写。
-- 如需启用，需独立 migration。
-- 
-- 添加审核字段到核心业务表（状态转换红线基础设施）
-- 为所有需要审核的业务表添加：审核人ID + 审核时间
-- 命名说明:
--   - VoucherEntity / 应收/应付/发票: audited_by / audited_at（统一命名）
--   - BankStatementEntity: reviewed_by / reviewed_at（历史原因，已存在）
-- 最后更新：2026-06-27

-- 1. 应收单表 t_receivable
ALTER TABLE t_receivable 
ADD COLUMN audited_by BIGINT COMMENT '审核人ID',
ADD COLUMN audited_at TIMESTAMP COMMENT '审核时间';

-- 2. 应付单表 t_payable
ALTER TABLE t_payable 
ADD COLUMN audited_by BIGINT COMMENT '审核人ID',
ADD COLUMN audited_at TIMESTAMP COMMENT '审核时间';

-- 3. 销售发票表 t_output_invoice
ALTER TABLE t_output_invoice 
ADD COLUMN audited_by BIGINT COMMENT '审核人ID',
ADD COLUMN audited_at TIMESTAMP COMMENT '审核时间';

-- 4. 采购发票表 t_input_invoice
ALTER TABLE t_input_invoice 
ADD COLUMN audited_by BIGINT COMMENT '审核人ID',
ADD COLUMN audited_at TIMESTAMP COMMENT '审核时间';

-- 5. 银行流水表 t_bank_statement - 已有 reviewed_by/reviewed_at（V53 已添加），跳过

-- 注释:
-- - VoucherEntity 已经有这两个字段，无需重复添加
-- - BankStatementEntity 已经有 reviewed_by/reviewed_at（V53 已添加）
-- - 这些字段允许为空，因为草稿状态不需要审核
-- - 审核通过时必须设置这两个字段（由测试保证）
