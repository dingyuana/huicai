-- =============================================================================
-- V176: 撤两张「代理端跨客户语义」表的 RLS（REQ-2026-135 / 线上 500 修复）
-- =============================================================================
-- 【缺陷：线上 500 的根因】
--   POST /api/v1/agency/assignments 返回 500，容器日志：
--     ERROR: new row violates row-level security policy
--            for table "t_agency_user_enterprise"
--   出自 AgencyUserEnterpriseMapper.insert-Inline。
--
-- 【根因：RLS 与业务语义矛盾，不是配置遗漏】
--   V156 把 t_agency_user_enterprise 当作普通租户表开了
--   enterprise_policy（USING enterprise_id = app.enterprise_id）。
--   但该表的 enterprise_id 语义是「**被服务的客户**」，不是「本行归属哪个租户」：
--     建表注释 = 「会计-客户分配关系（派工记录）」
--     V112 = agency_user_id（代理会计）→ enterprise_id（客户企业）
--   代理管理员的职责就是**跨客户派工** —— 他必须能派企业 2/3/4。
--   而谓词强制「只能碰 enterprise_id 等于当前 GUC 的那一个企业」，
--   于是派给他名下其它客户时被 with_check 拒绝 ⇒ 功能 100% 不可用。
--
--   ⚠️ V156 原注释断言这 4 张表「均属漏网而非有意排除」，该前提对本表**不成立**：
--   同批被排除的 t_agency_enterprise 恰恰是因为「代理须跨客户读」才不开 RLS，
--   本表与它是同一语义，V156 却一并开了。属**误判分类**，非遗漏。
--
-- 【行为探针实证（非推演，角色 huicai_app = NOSUPERUSER + NOBYPASSRLS）】
--   1) 无 GUC 时 INSERT 企业4        → ERROR violates RLS   ← 线上 500 的成因
--   2) 同事务 SET LOCAL app.enterprise_id=4 后 INSERT → 成功
--      ⇒ **谓词本身正确，缺的只是 GUC**；不是谓词写错
--   3) GUC=1 时查「会计2 的派工」→ 只返 enterprise_id=1；超管对照返 1,2,3
--      ⇒ **读路径静默丢行**：代理管理员看不到自己已派给其它客户的记录，
--        前端「已分配」列表恒缺项 ⇒ 比 500 更隐蔽
--
-- 【为什么连带处理 t_service_progress】
--   与本表同批被 V156 开 RLS，且同样在 EnterpriseDataPermissionInterceptor
--   的 SHARED_TABLES 里（应用层已按「语义豁免」放行）—— 应用层与 DB 层
--   对同一张表给出相反裁定，DB 层赢了，功能随之失效。
--   实测其查询全部以 agency_id 收敛（ServiceProgressServiceImpl:121/168/223/250），
--   enterprise_id 仅作收窄条件（:175 可空），即**天然跨客户**。
--   当前 0 行故无线上症状，但同型同批，一并撤避免留下同类债（§7.7 不得扩大 DDL
--   的前提是「不扩大裁定范围」—— 本裁定已含此表，见 SPEC-P114 §2）。
--
-- 【护栏：撤 RLS 不等于放行，必须同批补路径级校验】
--   AGENTS §4.5 第 43 条：整表豁免 + 只解释一条路径 = 必然漏。
--   故本迁移与以下改动同批生效，缺一即视本迁移为「开洞」：
--     ① AssignmentController 三个端点补 AGENCY_ADMIN/SUPER_ADMIN 角色校验
--        （此前 SecurityConfig 仅 anyRequest().authenticated()，读路径无任何鉴权）
--     ② AgencyUserEnterpriseServiceImpl 的查询按 agency_id 收敛
--     ③ AgencyAssignmentRlsRealDBTest 真库守卫（含两条反向自证）
--
-- 【为什么不用「保留 RLS + 改谓词为 agency 维度」】
--   该方案需新增 app.agency_id GUC 及其设置切面，改动面更大，
--   且 agency 维度过滤在应用层已由 SecurityUtils.getCurrentAgencyId() + 各查询
--   的 agency_id 条件实现（见 ServiceProgressServiceImpl），
--   再加一层同义谓词属重复。本表走「撤 RLS + 应用层收敛」路线。
--
-- 【幂等】IF EXISTS 包裹，可重复执行。
-- =============================================================================

-- ── t_agency_user_enterprise ──
DROP POLICY IF EXISTS enterprise_policy ON t_agency_user_enterprise;
ALTER TABLE t_agency_user_enterprise NO FORCE ROW LEVEL SECURITY;
ALTER TABLE t_agency_user_enterprise DISABLE ROW LEVEL SECURITY;

COMMENT ON TABLE t_agency_user_enterprise IS
    '会计-客户分配关系（派工记录）。enterprise_id 语义为「被服务客户」而非「归属租户」：'
    '代理管理员须跨客户派工，故 V176 起不开 RLS，隔离由应用层 agency_id + 角色校验负责。';

-- ── t_service_progress ──
DROP POLICY IF EXISTS enterprise_policy ON t_service_progress;
ALTER TABLE t_service_progress NO FORCE ROW LEVEL SECURITY;
ALTER TABLE t_service_progress DISABLE ROW LEVEL SECURITY;

COMMENT ON TABLE t_service_progress IS
    '代理服务进度跟踪（P79）。查询以 agency_id 收敛、enterprise_id 仅作收窄，'
    '天然跨客户，故 V176 起不开 RLS，隔离由应用层 agency_id 负责。';