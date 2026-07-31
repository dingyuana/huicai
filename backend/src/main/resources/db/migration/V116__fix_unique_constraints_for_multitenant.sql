-- V116: 修复唯一约束为多租户复合约束 (code, enterprise_id)
-- 原约束仅按 code 唯一，导致不同企业不能有相同科目编码/凭证类型等
-- 修复后每个企业可独立拥有自己的科目体系

-- 1. t_subject: 科目表
ALTER TABLE t_subject DROP CONSTRAINT IF EXISTS uq_subject_code;
ALTER TABLE t_subject ADD CONSTRAINT uq_subject_code_ent UNIQUE (code, enterprise_id);

-- 2. t_voucher_type: 凭证类型表
ALTER TABLE t_voucher_type DROP CONSTRAINT IF EXISTS uq_voucher_type_code;
ALTER TABLE t_voucher_type ADD CONSTRAINT uq_voucher_type_code_ent UNIQUE (code, enterprise_id);

-- 3. t_summary_lib: 摘要库
ALTER TABLE t_summary_lib DROP CONSTRAINT IF EXISTS uq_summary_code;
ALTER TABLE t_summary_lib ADD CONSTRAINT uq_summary_code_ent UNIQUE (summary_code, enterprise_id);

-- 4. t_period: 会计期间
ALTER TABLE t_period DROP CONSTRAINT IF EXISTS uq_period_code;
ALTER TABLE t_period ADD CONSTRAINT uq_period_code_ent UNIQUE (period_code, enterprise_id);

-- 5. 重新插入模板数据 (enterprise_id=0)，从 enterprise_id=1 复制
-- 注：OVERRIDING SYSTEM VALUE 避免 IDENTITY 序列与 V102.5 显式 ID 冲突
INSERT INTO t_subject (id, enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
OVERRIDING SYSTEM VALUE
SELECT (SELECT COALESCE(MAX(id), 100) FROM t_subject) + ROW_NUMBER() OVER (ORDER BY code), 0, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, 1, NOW(), 1, NOW(), 0
FROM t_subject WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_voucher_type (enterprise_id, code, name, sort_order, numbering_rule, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, code, name, sort_order, numbering_rule, is_active, remark, 1, NOW(), 1, NOW(), 0
FROM t_voucher_type WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (code, enterprise_id) DO NOTHING;

INSERT INTO t_summary_lib (enterprise_id, summary_code, summary_text, category, sort_order, is_active, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, summary_code, summary_text, category, sort_order, is_active, 1, NOW(), 1, NOW(), 0
FROM t_summary_lib WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (summary_code, enterprise_id) DO NOTHING;

INSERT INTO t_period (enterprise_id, year, month, period_code, start_date, end_date, status, version, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, year, month, period_code, start_date, end_date, status, 1, 1, NOW(), 1, NOW(), 0
FROM t_period WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (period_code, enterprise_id) DO NOTHING;

-- 验证
DO $$
DECLARE
    cnt integer;
BEGIN
    SELECT count(*) INTO cnt FROM t_subject WHERE enterprise_id = 0 AND deleted = 0;
    RAISE NOTICE 'V116: 模板科目数据: % 条', cnt;
    SELECT count(*) INTO cnt FROM t_voucher_type WHERE enterprise_id = 0 AND deleted = 0;
    RAISE NOTICE 'V116: 模板凭证类型: % 条', cnt;
    SELECT count(*) INTO cnt FROM t_summary_lib WHERE enterprise_id = 0 AND deleted = 0;
    RAISE NOTICE 'V116: 模板摘要库: % 条', cnt;
    SELECT count(*) INTO cnt FROM t_period WHERE enterprise_id = 0 AND deleted = 0;
    RAISE NOTICE 'V116: 模板会计期间: % 条', cnt;
END $$;