# SPEC-P141：t_ai_task 未竟项结案 + t_user 种子归属回填迁移化 + 登记治理

| 项 | 内容 |
|---|---|
| 状态 | ✅ 实施记录（探针先行，实施与验证见 §4） |
| REQ | 无新立项——①归 REQ-2026-155（P139，已 DONE，本轮只结案回写）；②归 V1.90 遗留收口；③纯文档治理 |
| 前序 | AGENTS §0 V1.88 未竟项（t_ai_task「不得记作已修」）、V1.90 遗留（t_user 运行时修复未登记迁移）、V1.92 第 68 条（环境事故） |
| 关联 | §4.5 第 33 条（超管绕过 RLS ⇒ 唯一被判据的是行为探针）；第 29 条范式（双侧修正）；P139（MQ 写回已修） |

---

## §0 取证（全部实测，2026-10-28）

### 0.1 t_ai_task 全链路坐实已闭环（对比 2026-10-18 症状）

本机运行容器实测（admin / 企业 1 上下文）：

| 探针 | 2026-10-28 结果 | 2026-10-18 症状（`e2653883`/`16f0b1f9`） |
|---|---|---|
| `GET /api/v1/ai/tasks/page` | **200，total=3，ids=[5,6,3]**（与 DB ent1 行数精确一致） | total=0（RLS 滤空） |
| `GET /api/v1/ai/tasks/6` 详情 | **200** | 500「AI 任务不存在」 |
| 跨租户负向：读 ent4 行 id=4 | **被滤**（「AI 任务不存在」） | ——（隔离语义正确） |
| 对照组 `voucher-types` | 200 ✓ | 已知生效链路，无回归 |

配合既有证据链：读路径 2026-10-26 本机复测正常（`85e31b34`）→ MQ 写回由 **P139 D-1~D-4 修复**（`AiTaskMessage`/`AiTaskResult` 加 `enterpriseId` + 监听层 set/clear + DLQ 兜底），**D-4 生产探针 4+1 条全通过**（id=4 ent4 跨账套写回 ✓ 等），登记册 REQ-2026-155 已 DONE。⇒ **AGENTS V1.88 未竟项（「两种修法都无效」）正式结案**：症结从来不是一个而是两个——读路径 GUC（类级+Controller 事务已修）与 MQ 线程上下文（P139 已修），两者均已实证。

### 0.2 V1.90 遗留探针：全新环境会复现 NULL（决定性）

在运行中 postgres 建 scratch 库、按 Flyway 真实版本号排序重放全部 **106/106** 迁移（无失败）：

| 表 | scratch（迁移链组装） | 真库（曾手动 UPDATE） |
|---|---|---|
| `t_user` | admin=1；**accountant01/reviewer01/assistant01 三行 enterprise_id=NULL**；合计 NULL **3/4** | 12 行 NULL **0**（V1.90 手动修复经持久卷保留） |

结论：V1.90 的 `UPDATE t_user SET enterprise_id=1` **从未落进迁移链**，每次重建环境都复现 3 行 NULL（登录后 ThreadLocal enterpriseId 不设 ⇒ 该账号读接口空数据）。历史复核：`V165` 只补了 `t_agency_user_enterprise` 授权行，**从未修过 `t_user.enterprise_id` 本身**。

### 0.3 登记过期项核查（三项全部已收口，仅旧行未回写）

| 旧登记 | 实测现状 | 结论 |
|---|---|---|
| AT-104-3「门禁会红无证据」 | V1.61 `check_tenant_fixture.py` 已挂 `full-stack-test.yml:121` + 红→绿反证；V1.66 PR #25 真实 CI 已实证会红 | ✅ 已收口 |
| 「check_tenant_fixture 未挂 workflow」（V1.90 时代登记） | `full-stack-test.yml:121` 在挂 | ✅ 已收口 |
| 「30 个 Mock 同义反复 MapperTest」（REQ-131 剩余范围） | `*MapperTest` 共 14 个，全部 `extends AbstractMapperTest`，**Mockito 型残余 0**（连 `VoucherTemplateMapperTest` 都已完成） | ✅ 已收口 |

---

## §1 方案（D 项裁定）

**D-1（采纳）**：新增 `V191__backfill_seed_user_enterprise.sql`——幂等 `UPDATE t_user SET enterprise_id=1 WHERE enterprise_id IS NULL AND username IN ('accountant01','reviewer01','assistant01')`，把 V1.90 手动修复迁移化。**范围刻意限定三个系统种子账号**：运行时注册的 AGENCY 用户（王会计/liu/zhou_kj…）归属由注册业务决定，不打包修；不收紧 NOT NULL 约束（超范围）。

**D-2（采纳）**：真库回归锁 `UserSeedEnterpriseJdbcRealDBTest`（`com.huicai.base.system`，`AbstractMapperTest` → Flyway 全量含 V191）：断言三种子账号 enterprise_id=1 且**种子行不得有 NULL**。反证 = 摘除 V191 后该类必红（§4实测）。

**D-3（采纳）**：文档回写一批——AGENTS 未竟项行结案（引用 §0.1 探针）、§0 增 V1.93、登记册增 V1.93 行（含 §0.3 三项过期登记回写）。

---

## §4 验收记录（2026-10-28）

| 项 | 结果 |
|---|---|
| scratch 重放（含 V191）→ t_user NULL | **0/4**（修复前 3/4，§0.2） |
| 回归锁单跑 | 绿（详见 commit）；反证摘 V191 → 三断言红 |
| L2 全量（`mvn test -DexcludedGroups=""`） | 见 AGENTS §0 V1.93 |
| 今日 t_ai_task 行为探针 | §0.1 表，4/4 符合预期 |

> 实施人：opencode（GLM）；复合铁律 #1（不动业务语义）、#10（先 SPEC 后代码）、#13（出参不受影响）。
