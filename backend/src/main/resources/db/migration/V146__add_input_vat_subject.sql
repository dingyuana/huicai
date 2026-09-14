-- P70: 为缺少「2221.02 应交增值税-进项税额」的企业补齐科目。
-- 采购进项发票转凭证的进项税借方使用该科目（debit 方向），父科目为各自企业的 2221 应交税费。
-- 幂等：按 (enterprise_id, code) 判存在，已存在则跳过（如企业2已有，保留不动）。
-- 仅插入科目档案，不动任何余额/凭证。企业6已删除，不补；模板企业0一并补齐。
INSERT INTO t_subject
    (enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type,
     is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT
    p.enterprise_id,
    '2221.02',
    '应交增值税-进项税额',
    p.id,
    2,
    'debit',
    true,
    NULL,
    true,
    '进项税抵扣',
    1,
    NOW(),
    1,
    NOW(),
    0
FROM t_subject p
WHERE p.code = '2221'
  AND p.deleted = 0
  AND p.enterprise_id IN (0, 1, 3, 4, 5)
  AND NOT EXISTS (
      SELECT 1 FROM t_subject s
      WHERE s.enterprise_id = p.enterprise_id
        AND s.code = '2221.02'
        AND s.deleted = 0
  );
