-- ============================================================
-- V59: 补充标准凭证类型种子数据
-- 标准凭证类型: 收款凭证 / 付款凭证 / 转账凭证
-- ============================================================

-- 收款凭证（SK）
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule, is_active, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT 2, 'SK', '收款凭证', 2, 'SK-{year}{month}-{serial}', TRUE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_type WHERE code = 'SK');

-- 付款凭证（FK）
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule, is_active, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT 3, 'FK', '付款凭证', 3, 'FK-{year}{month}-{serial}', TRUE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_type WHERE code = 'FK');

-- 转账凭证（ZZ）
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule, is_active, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT 4, 'ZZ', '转账凭证', 4, 'ZZ-{year}{month}-{serial}', TRUE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_type WHERE code = 'ZZ');