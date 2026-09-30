-- ============================================================
-- V159: 补齐银行流水硬编码制证依赖的 4 个缺失科目（REQ-2026-127）
--
-- 背景：服务器手工测试实锤 —— 开发库缺 2203 预收账款，100/99/98/95/94 号
-- 流水（business_receipt）提交审核时 AutoGenerationService.generateDocThenVoucher
-- 的 arAcct.getId() 直接 NPE，整条提交审核链路崩溃。
-- 经全量审计 findSubjectByCode 的 15 处调用点，以下 4 个代码在库中完全缺失：
--   1221 其他应收款   — internal_transfer 的 1012 回退科目
--   2203 预收账款     — 收款流水无未结清应收时的贷方科目（本次崩溃点）
--   2211 应付职工薪酬 — salary_social 流水贷方科目
--   6603 财务费用     — bank_interest_fee 利息/手续费流水科目
-- 前三个在 generateDocThenVoucher 直接 getId()（NPE），6603 在
-- generateVoucherDirect 被事后 null 守卫挡住（抛 BusinessException，无 NPE 但不可用）。
--
-- 同时代码侧已加 requireSubject() 把 NPE 换成指明科目代码的
-- BusinessException（铁律 #14），本迁移消除「缺科目」这个直接诱因。
--
-- 覆盖企业：动态取已建基础科目（以 1122 应收账款为标志）的全部企业，
-- 不硬编码 enterprise_id 列表（V146 硬编码 0,1,3,4,5，新企业会漏）。
--           dev 库现有企业 0 与 1。
--
-- 幂等：uq_subject_code_ent(code, enterprise_id) 是**非部分**唯一约束
-- （不含 deleted，已实测 pg_get_constraintdef 确认），故 NOT EXISTS 守卫
-- **不带 deleted=0** —— 否则已逻辑删除的同码行会让守卫放行、INSERT 撞唯一
-- 约束导致迁移失败（V152 教训）。此时该科目保持缺失，运行期由
-- requireSubject 抛出可读异常，由人工在基础数据里维护或恢复。
-- 未硬编码 id，走 IDENTITY 自增（同 V146 写法，避免 OVERRIDING SYSTEM VALUE）。
--
-- 验证：已实测首次 INSERT 4 行（企业0、企业1 各 4），重跑 INSERT 0 行。
-- ============================================================
INSERT INTO t_subject
    (enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type,
     is_active, remark, account_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT
    e.enterprise_id,
    m.code,
    m.name,
    NULL,
    1,
    m.direction,
    true,
    NULL,
    true,
    m.remark,
    m.account_type,
    1,
    NOW(),
    1,
    NOW(),
    0
FROM (VALUES
    ('1221', '其他应收款',   'debit',  'CURRENT_ASSET',     '内部转账 1012 缺失时的回退科目（REQ-2026-127 补齐）'),
    ('2203', '预收账款',     'credit', 'CURRENT_LIABILITY', '收款流水无未结清应收时的贷方科目（REQ-2026-127 补齐）'),
    ('2211', '应付职工薪酬', 'credit', 'CURRENT_LIABILITY', '工资类流水制证贷方科目（REQ-2026-127 补齐）'),
    ('6603', '财务费用',     'debit',  NULL,                '银行利息/手续费流水科目（REQ-2026-127 补齐）')
) AS m(code, name, direction, account_type, remark)
CROSS JOIN (
    SELECT DISTINCT enterprise_id
    FROM t_subject
    WHERE code = '1122'
      AND deleted = 0
) e
WHERE NOT EXISTS (
    SELECT 1
    FROM t_subject s
    WHERE s.code = m.code
      AND s.enterprise_id = e.enterprise_id
);
