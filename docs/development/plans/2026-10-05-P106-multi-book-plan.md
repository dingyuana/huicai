# P106 开发计划 —— 多账套 L1 收口（企业级）

> **状态**：🚧 实施中（SPEC V1.3 已于 2026-10-05 终审通过；本计划为其执行拆解）
> **关联**：`docs/specs/P106-multi-book-account-set.md`（V1.3）| **需求**：REQ-2026-133 | **编号**：HUICAI-SPC-P106
> **铁律约束**：①三步闭环 SPEC→Plan→**审核**→执行，本计划获批后方可跑批次 1b/2/3；②每个微循环 TDD-First（Red→Green），**跳过 Red 验证 = 违规**；③DDL 必走 Flyway；④三方对照审计（PG ↔ Entity ↔ 业务代码）
> **范围**：**只做 P106 的「多账套」子项**（5 项遗留），不含年结/制单≠审核/数据权限粒度/部门级扩展

---

## 0. 批次 1a 前置核查结果（2026-10-05，只读，已完成）

SPEC §0.2 的断言已在**真实开发库**（`huicai-postgres`，以非超级用户 `huicai_app` 连接）逐条验证：

| 核查项 | 实测结果 | 判定 |
|---|---|---|
| 4 张表是否真有双列 | `t_ai_feedback_log` / `t_classification_rule` / `t_prepayment` / `t_reconciliation_log` 的 `pg_attribute` 均为 `enterprise_id:bigint, tenant_id:bigint` | ✅ SPEC 成立 |
| RLS 是否已开 | 4 张表 `relrowsecurity=t`、**`relforcerowsecurity=t`**、`pg_policies` 各 1 条 | ✅ |
| **RLS 谓词读哪一列** | 4 张表的 `enterprise_policy` 谓词均为 `(enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint)` ⇒ **只读 `enterprise_id`，完全不感知 `tenant_id`** | ✅ **「看起来已隔离、实际从未隔离」坐实** |
| 历史数据是否需回填 | 4 张表 `total = 0`（开发库无数据）⇒ **mismatch=0 是「无数据」而非「已一致」**，**不构成任何回填依据** | ⚠️ 见 §0.1 |
| `t_reconciliation_exception` 是否无 `tenant_id` | 只有 `enterprise_id:bigint` | ✅ 幽灵字段判定成立 |
| `chk_enterprise_status` 真实允许集 | `ARRAY['PENDING','ACTIVE','SUSPENDED','TERMINATED']` ⇒ **无 `CLOSED`** | ✅ P0-1 修正在真实库成立 |
| `t_classification_rule` 索引建在哪列 | `idx_classification_rule_tenant ON (tenant_id, deleted)` ⇒ **仍建在旧列** | ✅ 需迁到 `enterprise_id` |

### 0.1 前置核查暴露的关键事实（影响实施方式）

⚠️ **「mismatch = 0」不等于「两列一致」** —— 4 张表在开发库**一行数据都没有**。这意味着：

1. **不需要历史数据回填迁移**（0 行），但
2. **也无法用现存数据证明缺陷曾发生** ⇒ 批次 1a 的 TDD **必须自己造数据**（在 `AbstractMapperTest` 基类里造，切 `useEnterprise(...)` 上下文，AGENTS §4.5 第 23 条），
3. 症状必须由**代码路径**产生而非由**存量脏数据**暴露：走 `PrepaymentServiceImpl#create` / `ReconciliationServiceImpl#execute` 写入后立即用**另一个企业上下文**查询，断言「写入的行查不到」。

---

## 1. 批次 1a（P0）：4 张双列并存表收口

**为什么先做**：这 4 张表**当前就在产生错误数据**（写入 `tenant_id` 而 RLS 读 `enterprise_id`）；批次 1b 的 10 张平台全局表是「一致地未隔离」，属设计取舍，可以慢。混做会让 DDL 变更无法归因。

### 微循环 1a-1（Red）`AT-106-1` 预付款两列一致

| 项 | 内容 |
|---|---|
| 红灯用例 | `AccountSetIsolationRealDBTest#prepaymentMustWriteEnterpriseIdNotTenantId`：上下文切到 E（E≠1）→ `prepaymentService.create(...)` → 断言落库行 `enterprise_id = E`；**负向**：把该行 `tenant_id` 手工改成 ≠ E 后，按 `tenant_id` 的查询不得命中 |
| 预期红因 | `PrepaymentServiceImpl:107` `if (entity.getTenantId() == null) entity.setTenantId(DEFAULT_TENANT_ID)` 写死 1，且 `enterprise_id` 由 `MyMetaObjectHandler` 填上下文 ⇒ 当前实现下 `enterprise_id = E` 但 `tenant_id = 1`，负向断言命中 |
| 绿法 | 删 `:55` 的 `DEFAULT_TENANT_ID` 与 `:107` 的赋值，让 `tenant_id` **恒等于** `enterprise_id`（保留对存量 `tenant_id` 的读取兼容，不加 CHECK 约束以免锁死历史数据） |
| 反证手法 | 保留一个「把 `tenant_id` 改成 ≠E」的负向用例，证明按旧列的查询确实不得命中 |

### 微循环 1a-2（Red→Green）`AT-106-2` 核销日志两处写入

`ReconciliationServiceImpl:364` / `:780`（写 `t_reconciliation_log.tenant_id`）⇒ 改为写 `enterprise_id`。断言：两行 `enterprise_id = E` 且 `tenant_id` 不再被 Service 独立赋值。

### 微循环 1a-3（Red→Green）`AT-106-2b` 幽灵字段赋值移除

`ReconciliationServiceImpl:892` `ex.setTenantId(DEFAULT_TENANT_ID)` ⇒ **删代码，不改列**。
**负向断言（关键）**：不得改成 `ex.setEnterpriseId(...)` —— 那会给一个不存在的租户列加值语义，掩盖 Entity 层缺陷。同时断言 `information_schema` 中 `t_reconciliation_exception` **不存在** `tenant_id` 列。

### 微循环 1a-4（Red→Green）`AT-106-3` 核销容忍度按当前企业

`ReconciliationToleranceServiceImpl:29` 的 `DEFAULT_ENTERPRISE_ID`（4 个使用点 `:43`/`:72`/`:87`/`:104`）⇒ 改读 `EnterpriseContextHolder.get()`，为 null 时抛 `BusinessException`（铁律 #14）。
**边界（SPEC §1.2）**：**不得**改 `TenantRlsInitializer:65-69` 的 null-return 语义；负向断言「切面在上下文为 null 时仍不设 GUC」。

### 微循环 1a-5（DDL）索引迁移

`t_classification_rule` 的 `idx_classification_rule_tenant (tenant_id, deleted)` ⇒ 新增 `idx_classification_rule_enterprise (enterprise_id, deleted)`。**新 Flyway 号，不改旧迁移**（AGENTS §4.5 第 20/26 条：Flyway 已应用的迁移不可修改）。

| 门禁 | 要求 |
|---|---|
| DDL | 走 Flyway（下一个可用号 **V168**，须先 `rg -l "V168" backend/src/main/resources/db/migration` 确认未被占用） |
| 三方对照 | PG（已验）↔ Entity（`PrepaymentEntity:21/23`、`ReconciliationLogEntity:17`）↔ 业务代码 |
| 工具 | `node scripts/check-entity-schema.mjs`；`python scripts/check_tenant_fixture.py`（A/B 两类判定） |
| 覆盖率 | `mvn clean test` 必须打印 `All coverage checks have been met`（**BRANCH 缓冲仅 0.81 点**，新增分支须同步补测试） |
| 回归 | L1 `mvn clean test` + L2 `mvn test -DexcludedGroups=`（**须先 `docker start huicai-redis`**，否则 11 个 `RedisConnectionFailure`，AGENTS §4.5 第 24 条） |

---

## 2. 批次 1b（P1）：10 张平台全局表定性

**状态**：⏸ **等老丁裁定分类**（SPEC §0.1 已给建议分组：权限/导航骨架 4 张按「刻意全局共享」、`t_dept`/`t_sys_config`/`t_audit_log` 按「应隔离」、`t_agency`/`t_agency_user`/`t_enterprise` 按「平台元数据」）。定性未裁定前**不动任何 DDL**。

- 对「应隔离」的表：`AT-106-5`，须落 Flyway（加列 + 回填 + 开 RLS）或写明「不修」理由
- ⚠️ `t_sys_config` 特殊：F1 已证其含 `accounting.start_year`/`start_month` 两条**账套级配置**且无隔离列 ⇒ 若判「应隔离」，需先确认这两条配置是全局共享（所有企业同值）还是应按企业分设

## 3. 批次 2（P1）：切换鉴权真库回归锁

现状：`TenantRlsRealDBTest`（6 项）/ `TenantRlsGucRealDBTest`（3 项）/ `TenantIsolationSecurityTest` / `TenantIsolationHttpTest` 已覆盖**读**隔离。
本批次只**补缺**：`AT-106-6`（切到 A 只返回 A 的数据）、`AT-106-7`（非成员 403 且审计无成功记录）、`AT-106-8`（SUPER_ADMIN **会**设置上下文，负向：不带头时按 JWT）、**跨企业写拒绝**。
⚠️ 已核实 `TenantIsolationSecurityTest#superAdminAllowedEverywhere:78-82` 只断言 `isMember`，**与 AT-106-8 无冲突**。

## 4. 批次 3（P2）：L1-4 / L1-5 产品决策落地

`「一个用户能否挂多家企业」` + `t_user.uq_username` 全局唯一 vs `(username, enterprise_id)`。
⚠️ 高风险（影响登录）⇒ **需老丁单独审核 + 回填脚本**，且优先用「登录后切换」而非改唯一约束（参考 SPEC §2 用友/金蝶对标：切换是登录后行为）。

---

## 5. 不在本计划范围（已登记，勿混入）

| 项 | 去向 |
|---|---|
| `ReconciliationExceptionEntity` 15 处 `exist = false`（含 `updatedAt` 反向缺口，`V127:34` 已加真实列却标不存在） | **P102 待办**，需单独立项 |
| 6 处 `DEFAULT_USER_ID = 1L`（操作人维度） | **P102 待办**，需「后台任务如何记操作人」的产品决策 |
| 「非 ACTIVE 账套禁止产生业务数据」（全库无校验，rg 零命中） | **未立项**，需单独立项 |
| L2 账簿级多账套（`book_id`） | 仅立项，触发条件见 SPEC §9 |
| 年结/制单≠审核/数据权限粒度/部门级扩展 | **未立项** |

---

## 6. 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-10-05 | opencode | 首版。SPEC V1.3 终审通过后编写。含批次 1a 的**真实库前置核查结果**（RLS 谓词只读 `enterprise_id`、`chk_enterprise_status` 无 `CLOSED`、`idx_classification_rule_tenant` 仍在旧列）、**「mismatch=0 是无数据而非已一致」的关键澄清**、5 个 TDD 微循环拆解、三批次门禁要求，以及 5 项「不在范围」的登记去向 |