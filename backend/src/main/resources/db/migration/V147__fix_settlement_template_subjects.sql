-- ============================================================
-- V140: 修正核销单凭证模板科目（预收取消双重记账）
--
-- 背景：V130 种子模板的核销科目错误，导致收款单已贷2203预收后，
-- 核销凭证再次借1002银行存款 = 双重记账。
--
-- 修复：
--   TPL_SETTLEMENT_RECEIVABLE: 借方 1002银行存款 → 2203预收账款
--   TPL_SETTLEMENT_PAYMENT:   贷方 1002银行存款 → 1123预付账款
-- ============================================================

-- ─── 1. 更新 t_voucher_template_line 科目引用 ───
-- 应收核销：借方科目从1002改为2203（按企业映射）
UPDATE t_voucher_template_line tpl
SET subject_id = (
    SELECT s2203.id
    FROM t_voucher_template vt
    JOIN t_subject s1002 ON s1002.code = '1002' AND s1002.id = tpl.subject_id AND s1002.enterprise_id = vt.enterprise_id
    JOIN t_subject s2203 ON s2203.code = '2203' AND s2203.enterprise_id = vt.enterprise_id
    WHERE vt.id = tpl.template_id AND vt.template_code = 'TPL_SETTLEMENT_RECEIVABLE'
)
WHERE tpl.template_id IN (SELECT id FROM t_voucher_template WHERE template_code = 'TPL_SETTLEMENT_RECEIVABLE')
  AND tpl.direction = 'debit'
  AND tpl.subject_id IN (SELECT id FROM t_subject WHERE code = '1002');

-- 应付核销：贷方科目从1002改为1123（按企业映射）
UPDATE t_voucher_template_line tpl
SET subject_id = (
    SELECT s1123.id
    FROM t_voucher_template vt
    JOIN t_subject s1002 ON s1002.code = '1002' AND s1002.id = tpl.subject_id AND s1002.enterprise_id = vt.enterprise_id
    JOIN t_subject s1123 ON s1123.code = '1123' AND s1123.enterprise_id = vt.enterprise_id
    WHERE vt.id = tpl.template_id AND vt.template_code = 'TPL_SETTLEMENT_PAYMENT'
)
WHERE tpl.template_id IN (SELECT id FROM t_voucher_template WHERE template_code = 'TPL_SETTLEMENT_PAYMENT')
  AND tpl.direction = 'credit'
  AND tpl.subject_id IN (SELECT id FROM t_subject WHERE code = '1002');

-- ─── 2. 更新 t_voucher_template.entries JSONB 科目编码 ───
UPDATE t_voucher_template SET entries = jsonb_set(
    entries, '{0, debitSubjectCode}', '"2203"'
)
WHERE template_code = 'TPL_SETTLEMENT_RECEIVABLE'
  AND entries -> 0 ->> 'debitSubjectCode' = '1002';

UPDATE t_voucher_template SET entries = jsonb_set(
    entries, '{0, creditSubjectCode}', '"1123"'
)
WHERE template_code = 'TPL_SETTLEMENT_PAYMENT'
  AND entries -> 0 ->> 'creditSubjectCode' = '1002';

-- ─── 3. 验证修复结果 ───
DO $$
DECLARE
    v_bad_count BIGINT;
BEGIN
    SELECT count(*) INTO v_bad_count
    FROM t_voucher_template vt
    JOIN t_voucher_template_line vtl ON vtl.template_id = vt.id
    JOIN t_subject s ON s.id = vtl.subject_id
    WHERE vt.template_code IN ('TPL_SETTLEMENT_RECEIVABLE','TPL_SETTLEMENT_PAYMENT')
      AND vtl.deleted = 0 AND vt.deleted = 0
      AND s.code IN ('1002');

    IF v_bad_count > 0 THEN
        RAISE WARNING 'V140: 仍有 % 条模板行科目未修正', v_bad_count;
    ELSE
        RAISE NOTICE 'V140: 核销模板科目修正完成，全部正确';
    END IF;
END $$;
