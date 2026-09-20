-- ============================================================
-- V149: t_subject 增加 accounting_standard 字段
--
-- 背景：
--   P75 建账模块：需要标识科目所属的会计制度（企业会计准则/小企业会计准则等），
--   以支持按制度预置科目和一键导入。
--
-- 变更：
--   accounting_standard VARCHAR(50) 科目所属会计制度编码
--   取值范围：'CAS'(企业会计准则) / 'SME'(小企业会计准则) / 'NPO'(民间非营利组织会计制度) / NULL(自定义)
--
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE t_subject
    ADD COLUMN IF NOT EXISTS accounting_standard VARCHAR(50);

COMMENT ON COLUMN t_subject.accounting_standard IS
    '科目所属会计制度编码: CAS-企业会计准则, SME-小企业会计准则, NPO-民间非营利组织会计制度, NULL-自定义';
