-- V115: 为模板企业(enterprise_id=0)创建种子数据
-- 从 enterprise_id=1 (默认企业) 复制到 enterprise_id=0 (模板企业)
-- 新企业激活时从模板企业克隆这些基础数据

-- 科目体系
INSERT INTO t_subject (enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, 1, NOW(), 1, NOW(), 0
FROM t_subject WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT DO NOTHING;

-- 凭证类型
INSERT INTO t_voucher_type (enterprise_id, code, name, sort_order, numbering_rule, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, code, name, sort_order, numbering_rule, is_active, remark, 1, NOW(), 1, NOW(), 0
FROM t_voucher_type WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT DO NOTHING;

-- 摘要库
INSERT INTO t_summary_lib (enterprise_id, summary_code, summary_text, category, sort_order, is_active, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, summary_code, summary_text, category, sort_order, is_active, 1, NOW(), 1, NOW(), 0
FROM t_summary_lib WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT DO NOTHING;

-- 会计期间
INSERT INTO t_period (enterprise_id, year, month, period_code, start_date, end_date, status, version, created_by, created_at, updated_by, updated_at, deleted)
SELECT 0, year, month, period_code, start_date, end_date, status, 1, 1, NOW(), 1, NOW(), 0
FROM t_period WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT DO NOTHING;

-- 验证模板数据
DO $$
DECLARE
    cnt integer;
BEGIN
    SELECT count(*) INTO cnt FROM t_subject WHERE enterprise_id = 0 AND deleted = 0;
    IF cnt = 0 THEN
        RAISE WARNING 'V115: 模板科目数据为空，请检查 enterprise_id=1 的科目数据';
    ELSE
        RAISE NOTICE 'V115: 模板科目数据已创建，共 % 条', cnt;
    END IF;
END $$;