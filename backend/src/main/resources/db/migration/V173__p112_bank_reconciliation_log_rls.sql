-- =====================================================================
-- V173: SPEC-P112 / REQ-2026-112 —— 补齐 RLS 第三层：t_bank_reconciliation_log
--
-- 【背景】M5b-V2 已把应用切到非超管 huicai_app，RLS 兜底对 71 张表生效
-- （实测行为：huicai_app 无 GUC→0 行 / =1→23 行 / =999999→0 行；超管对照不被过滤）。
-- 但本表此前未开策略，落在兜底之外（83 张 t_* 表里仅 71 张开了 RLS+FORCE）。
--
-- 【为何只补这一张】逐表实测后另两张不能补，理由见 SPEC-P112 §2：
--   - t_user：4 行里 3 行 enterprise_id=NULL（V114 种子种的 AGENCY 用户），
--     且登录走「无企业上下文按用户名查人」(UserServiceImpl#getByUsername)，
--     加策略 ⇒ NULL 行对所有人不可见 + 全员无法登录。
--   - t_agency_enterprise：代理须跨客户读，加策略即功能破坏。
--   本表 BankReconciliationLogEntity extends BaseEntity 且 enterpriseId 带
--   fill=INSERT，MyMetaObjectHandler 无条件覆盖为上下文企业 ⇒ 不会产生 NULL。
--
-- 【谓词与既有 71 张逐字一致】复用 t_subject 的 enterprise_policy 写法，
-- 且必须用 V167 的 NULLIF(...,'') 硬化，否则「曾 SET LOCAL 的会话」空串
-- ::bigint 会抛 invalid input syntax（§4.5 第 28 条）。
--
-- 【幂等】DROP POLICY IF EXISTS + CREATE POLICY，可重跑。
-- =====================================================================

ALTER TABLE t_bank_reconciliation_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_bank_reconciliation_log FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS enterprise_policy ON t_bank_reconciliation_log;
CREATE POLICY enterprise_policy ON t_bank_reconciliation_log
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );