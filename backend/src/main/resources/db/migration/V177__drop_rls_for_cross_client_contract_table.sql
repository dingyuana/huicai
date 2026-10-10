-- =============================================================================
-- V177: 撤 t_contract 的 RLS —— V156 审核认定的第三张「代理端跨客户语义」表
-- 关联：REQ-2026-140 / SPEC-P114 §7 遗留第 1 条（V156 另两张审核）
-- =============================================================================
-- 【审核结论：与 t_agency_user_enterprise 同型，故同批处置】
--   t_close_log        enterprise_id = 本行归属租户（结账日志）⇒ RLS 正确，**保留**
--   t_contract         enterprise_id = 被服务的客户             ⇒ RLS 错误，**撤**
--
-- 【t_contract 的三条独立佐证（非推演，行为探针实测）】
--   ① 表**同时存在 agency_id 与 enterprise_id**，且两者都是 NOT NULL。
--      纯租户表不会有 agency_id —— 这是「代理端关系表」的形态信号。
--   ② ContractMapper.findRenewalReminders() 的查询**无任何 enterprise_id 条件**，
--      业务上就是「扫本代理全部客户、找出 30 天内到期的合同」，
--      即该查询**按设计就是跨客户**的。
--   ③ V156 未逐表定性就一刀切开，与 t_agency_user_enterprise 同款误判
--      （V156 原注释断言 4 张表「均属漏网」，该前提对本表同样不成立）。
--
-- 【行为探针实证：huicai_app（NOSUPERUSER + NOBYPASSRLS）】
--   造同一代理(agency_id=1)名下企业 1 / 2 / 4 的三条合同（均 30 天内到期）：
--     超管对照                        → 3 条
--     huicai_app + GUC=1 跑同一条查询 → **1 条**（仅 enterprise_id=1）
--   ⇒ 代理管理员只能看到自己名下企业的合同，
--     **另外两个客户的续费提醒静默消失**（无任何报错）。
--   与 t_agency_user_enterprise 同型：写路径不一定炸，读路径一定丢行。
--
-- 【为什么不能只写 SET LOCAL 绕过】
--   与 V176 同款：谓词按 enterprise_id 过滤会把「本代理的其它客户」全部挡掉，
--   而这恰是代理端的主职。改谓词为 agency_id 维度需要新增 app.agency_id GUC
--   及其设置切面，而 agency 维度过滤在应用层已由 requireSameAgency 承接，
--   再加同义谓词属重复。
--
-- 【护栏：撤 RLS ≠ 放行，必须同批补（AGENTS §4.5 第 43 条）】
--   🔴 本表比 t_agency_user_enterprise 更危险 —— ClientController 五个端点
--   （含只读的 GET /page 与 GET /renewal-reminders）**完全无鉴权**，
--   SecurityConfig 仅 anyRequest().authenticated()，服务层也无 agency 归属校验。
--   撤掉 RLS 后任何登录用户都能遍历 page 读出**全部客户的合同金额与到期日**。
--   故本迁移与以下改动同批生效，缺一即视为开洞：
--     ① ClientController 五端点补 AGENCY_ADMIN / SUPER_ADMIN 角色校验
--     ② ContractServiceImpl 按 agency_id 收敛 create / getById / renew / page
--     ③ AgencyCrossClientRlsRealDBTest 扩到三张表 + ClientControllerTest 负向用例
--
-- 【幂等】IF EXISTS 包裹，可重复执行。
-- =============================================================================

DROP POLICY IF EXISTS enterprise_policy ON t_contract;
ALTER TABLE t_contract NO FORCE ROW LEVEL SECURITY;
ALTER TABLE t_contract DISABLE ROW LEVEL SECURITY;

COMMENT ON TABLE t_contract IS
    '客户合同表。enterprise_id 语义为「签约客户」而非「归属租户」：'
    '与 agency_id 并存，代理须按 agency_id 跨客户查看/续约，'
    '故 V177 起不开 RLS，隔离由应用层 agency_id + 角色校验负责。';