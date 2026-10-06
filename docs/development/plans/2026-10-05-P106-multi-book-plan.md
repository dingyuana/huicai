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

### 1a-1 实施结果（2026-10-05，Red → Green 完成）

**红因与 SPEC §0.2 的预测不同，且更严重** —— 预测是「`tenant_id` 写死 1 而 `enterprise_id` 正确」，实测 **`enterprise_id` 本身就是错的**：

```
expected: <990001> but was: <1>     ← enterprise_id 没跟着上下文走
```

**根因链**：`PrepaymentEntity` 不继承 `BaseEntity` 且全类无任何 `@TableField`/`FieldFill` ⇒ MyBatis-Plus 的 `TableInfo.withInsertFill = false` ⇒ `MyMetaObjectHandler.insertFill` **根本不被调用**（不是「调用了但没填」，是「压根没进」）⇒ `enterpriseId` 永不被上下文覆盖；而 `t_prepayment.enterprise_id` 是 `V105` 加的 `NOT NULL DEFAULT 1` ⇒ **任何非企业 1 的上下文创建的预付款都落进企业 1**。⇒ 实际后果不是「两列不一致」，而是**跨租户默认写入**（沉淀为 AGENTS §4.5 第 34 条）。

**修法（两处，各有实测依据）**：

| # | 改动 | 依据 |
|---|---|---|
| 1 | `PrepaymentEntity.enterpriseId` 补 `@TableField(fill = FieldFill.INSERT)` | 「有任意一个 fill 字段」即触发 `insertFill` 回调。⚠️ **不能改用继承 `BaseEntity`** —— 真实库核对列类型：`created_at`/`updated_at` 是 `date`（基类 `LocalDateTime`）、`created_by` 是 `varchar(50)`（基类 `Long`）⇒ 3 处不匹配。该实体 `:12` 的既有注释早已写明这一点 |
| 2 | `PrepaymentServiceImpl#create` 的 `setTenantId(DEFAULT_TENANT_ID)` 改为 `setTenantId(EnterpriseContextHolder.get())`，并删除 `DEFAULT_TENANT_ID` 常量 | `tenant_id` 全库**无任何读取方**（`rg` 仅命中原这一行）⇒ 可安全与 `enterprise_id` 同源。有上下文时两列同为上下文企业；**无上下文时两列都落 DB `DEFAULT 1`，仍然一致**。⚠️ 未改成「无上下文即抛异常」，`TenantRlsInitializer:65-69` 的 null-return 语义保持不变（SPEC §1.2 L1-3 边界） |

**顺带查明（影响所有隔离类断言）**：L2 Testcontainers 的连接角色 `test` 是**超级用户**（实测 `rolsuper=t`/`bypassrls=t`），而超级用户**绕过 RLS** ⇒ 「切到别的企业应查不到」的断言在 L2 里恒绿。已改用 `SET LOCAL ROLE` 到 `NOSUPERUSER` 探针（做法同 `TenantRlsRealDBTest`），并加守卫用例 `l2RoleIsSuperuserSoRlsAssertionsNeedSetRole`。**修正后该断言转绿 ⇒ RLS 隔离层本身有效**，缺陷只在写入路径。沉淀为 AGENTS §4.5 第 33 条。

**验证**：`AccountSetIsolationRealDBTest` **5/5 绿**；L1 `mvn clean test` = **1645/0/0/5** + `All coverage checks have been met`；L2 `mvn test -DexcludedGroups=` = **2043/0/0/5** + `All coverage checks have been met`（较基线 +5 即本类）。

**未做**：§0.2 的另外 3 张表（`t_reconciliation_log` / `t_ai_feedback_log` / `t_classification_rule`）—— 见 1a-2~1a-5。

### 微循环 1a-2（Red→Green）`AT-106-2` 核销日志两处写入

`ReconciliationServiceImpl:364` / `:780`（写 `t_reconciliation_log.tenant_id`）⇒ 改为写 `enterprise_id`。断言：两行 `enterprise_id = E` 且 `tenant_id` 不再被 Service 独立赋值。

### 微循环 1a-3（Red→Green）`AT-106-2b` 幽灵字段赋值移除

`ReconciliationServiceImpl:892` `ex.setTenantId(DEFAULT_TENANT_ID)` ⇒ **删代码，不改列**。
**负向断言（关键）**：不得改成 `ex.setEnterpriseId(...)` —— 那会给一个不存在的租户列加值语义，掩盖 Entity 层缺陷。同时断言 `information_schema` 中 `t_reconciliation_exception` **不存在** `tenant_id` 列。

### 微循环 1a-3（**已调查，暂挂起**）`t_classification_rule` / `t_ai_feedback_log` 读写过滤

**实测结论：这两张表的问题比预付款更严重 —— 读路径也按 `tenant_id` 过滤。** 共 **6 个方法**把 `tenantId` 当参数，其中 5 处用于过滤或写入：

| 位置 | 用法 |
|---|---|
| `ClassificationRuleServiceImpl:43` | `page()` 读过滤 `WHERE tenant_id = <入参>` |
| `ClassificationRuleServiceImpl:56` | `create()` **写死 `setTenantId(1L)`** |
| `ClassificationRuleServiceImpl:105`/`:139`/`:187` | `seedForNewTenant()` 的存在性检查、插入、逐条插入 |
| `AiFeedbackLogServiceImpl:40`/`:77` | `page()` 与 `summaryByTenant()` 读过滤 |

且两个 Controller 都把它暴露成 **`@RequestParam`（客户端可任意传值）**：`ClassificationRuleController:27`（`page`）与 `:77`（`seed`）、`AiFeedbackLogController` 的 `page` 与 `summaryByTenant`。

**为什么定性为「功能缺陷」而非「安全漏洞」**：RLS 的 `enterprise_policy` 谓词是 `enterprise_id = current_setting('app.enterprise_id', true)`（真实库已核对，`relforcerowsecurity = t`），与 `tenant_id = <客户端值>` 是**两个 AND 条件** ⇒ 客户端传任意 `tenantId` 也**读不到其它企业的行**。真实后果是**过滤条件失效**（多企业下 `tenant_id` 全为 1，客户端按企业过滤永远查不到/查到错行）。

**为何本轮不直接改**：修法必然要动**客户端契约** —— `tenantId` 请求参数要么废弃（前端要改）、要么改语义（属 API 变更），涉及 Controller 签名与前端联调。按铁律 #10，这是**须先在 SPEC 里决策**的事项，不应在实施批次里顺手改掉（AGENTS §4.3 第 17 条：功能缺口判为待立项，测试保持失败待实现）。

**已做的留证**：`AccountSetIsolationRealDBTest` 新增一条 **`@Disabled`** 用例（`P106 批次 1a-3 待 SPEC 决策：tenantId 请求参数的废弃与否属客户端契约变更`），把发现钉在测试里而不破坏 CI。恢复条件：SPEC 决策「废弃 `tenantId` 参数（改用上下文企业）」还是「保留但忽略」。

**建议的 SPEC 决策方向**：**废弃 `tenantId` 请求参数，改用上下文企业**。理由：①RLS 已经按 `enterprise_id` 隔离，客户端再传一个租户号属**重复且不可信**的隔离维度；②保留即意味着「两个隔离列 + 一个客户端可控」，是 §0.2 那类双列隐患的延长线；③前端 `EnterpriseSwitcher` 已经通过 `X-Enterprise-Id` 切换，无需在每个筛选器里再传一遍。

### 微循环 1a-5（已实施）索引补齐 —— **计划里的假设被实测推翻**

**计划原本写**：「把 `idx_classification_rule_tenant` 从 `tenant_id` 迁到 `enterprise_id`」。**实测该假设不成立** —— `enterprise_id` 的索引**早已存在**：

| 表 | 已有 enterprise_id 索引 | 来源 |
|---|---|---|
| `t_classification_rule` | `idx_t_classification_rule_enterprise (enterprise_id)` | V104:38 |
| `t_prepayment` | `idx_t_prepayment_enterprise (enterprise_id)` | V105:28 |
| `t_reconciliation_log` | `idx_t_reconciliation_log_enterprise (enterprise_id)` | V105:31 |

⇒ 真正的缺口是另外两处：`t_ai_feedback_log` **只有主键索引、完全没有 `enterprise_id` 索引**（V104 补了列但漏了索引）；`t_classification_rule` 的 `enterprise_id` 索引是**单列**，而实际查询同时过滤 `enterprise_id` 与 `deleted`。

**`V168__p106_add_enterprise_composite_indexes.sql`**（幂等，仅新增）：

| # | 迁移内容 | 依据 |
|---|---|---|
| ① | `idx_t_ai_feedback_log_enterprise_deleted (enterprise_id, deleted)` | 真实缺口：原先只有主键索引 |
| ② | `idx_t_classification_rule_enterprise_deleted (enterprise_id, deleted)` | 补 `deleted` 以匹配实际查询形状 |
| ③ | `COMMENT ON INDEX idx_classification_rule_tenant` 标注「待清理 / 1a-3 待决策」 | 只标注不删 |

**为什么不删旧索引**（两条理由）：①删索引属破坏性 DDL，按 AGENTS §7 需老丁确认；②`t_classification_rule.tenant_id` 列是否废弃取决于 **1a-3 的「`tenantId` 请求参数去留」决策**（尚未做），提前删索引等于抢先替业务方决策。

**验证**：Testcontainers 库 Flyway 日志出现 `Migrating schema "public" to version "168 - p106 add enterprise composite indexes"`；开发库手工应用同一份 SQL 后 `pg_indexes` 查到 2 个新索引；**重跑一次输出 `relation ... already exists, skipping` ⇒ 幂等成立**。L1 `1647/0/0/5` + L2 `2047/0/0/6`（Skipped +1 即新增的 `@Disabled` 用例），两次 `All coverage checks have been met`。

### 批次 1a 小结（5 个微循环）

| 微循环 | 状态 | 结果 |
|---|---|---|
| 1a-1 预付款 `enterprise_id` + `tenant_id` 对齐 | ✅ | 非企业 1 的上下文创建的预付款不再落进企业 1 |
| 1a-2 核销日志两处写入 + 幽灵字段赋值移除 | ✅ | 两列恒等；`:892` 的死代码删除而非改列 |
| 1a-3 分类规则 / AI 反馈的 `tenantId` 过滤 | ⏸ **待 SPEC 决策** | 定性为功能缺陷（过滤失效）非漏洞；已用 `@Disabled` 用例留证 |
| 1a-4 核销容差按上下文取 | ✅ | 4 个使用点；无上下文抛 `BusinessException` 而非回落 1 |
| 1a-5 索引补齐 | ✅ | 新增 2 个复合索引；计划假设被实测推翻 |

**批次 1a 的净收获（已修生产缺陷）**：预付款跨租户默认写入、核销日志两列不一致、核销容差按企业 1 判定（静默错账）。**新登记的未实现缺口**：1a-3 的 `tenantId` 参数去留、`ReconciliationExceptionEntity` 15 处 `exist = false`（含 `updatedAt` 反向缺口）、6 处 `DEFAULT_USER_ID = 1L`。

### 微循环 1a-4（Red→Green 已完成）`AT-106-3` 核销容忍度按当前企业

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

**状态**：✅ **已完成**（2026-10-06，SPEC V1.5 回写）

| 项 | 结果 |
|---|---|
| AT-106-6 成员切换成功、只返回本企业数据 | ✅ 绿 |
| AT-106-7 非成员切换 403 且审计无成功记录 | ✅ 绿 |
| AT-106-8 SUPER_ADMIN 会设上下文（负向：不带头按 JWT） | ✅ 绿 |
| 跨企业写拒绝（原计划项） | ✅ 绿（新增编号 AT-106-10） |
| 新增 4 条守卫（观测手段 / 夹具前提 / 放行必留痕 / 被拒不改写） | ✅ 绿（AT-106-11 ~ 14） |

**落点偏离原计划（已改）**：原计划写「AT-106-6 在 `TenantIsolationHttpTest` 补断言、AT-106-7 在 `TenantIsolationSecurityTest` 补断言」。**实测不可行**，改为新建 `TenantSwitchRealDBTest`（11 例）一次承载。理由见 SPEC §7.2，核心是：`TenantIsolationHttpTest` 把 `JwtProvider`/`StringRedisTemplate`/`UserDetailsServiceImpl` 全部 `@MockBean`，而回答「带合法凭证时上下文如何变」**必须签发真 JWT** ⇒ 引入真 JWT 就要拆掉整套 mock。

**本批次零生产缺陷** —— 过滤器三条链路实测均正确。价值在于此前这些行为**完全没有 HTTP 层回归保护**：`TenantIsolationSecurityTest` 6 例全绿，却无一能证明过滤器真的调用了 `EnterpriseMembershipChecker`（「规则层绿 ≠ 链路层绿」）。

**观测手段**：复用 `/api/v1/enterprise/current-period`（它直接以 `EnterpriseContextHolder.get()` 为入参查共享表 `t_enterprise`）⇒ 响应 `startPeriod` 严格等价于「上下文是谁」，**不为测试新增端点**。两个测试企业各设不同 `start_period`（209801/209802）。

**反证矩阵**（逐条注入缺陷实测转红，生产代码最终还原）：删掉 `enterpriseId = requested` ⇒ **打红 4 条**；去掉成员校验 ⇒ 打红 2 条；审计多写一条 ⇒ 打红 1 条；给 `UserEntity.enterpriseId` 补 `@TableField(fill)` ⇒ 打红 1 条。

**诚实声明**：RLS（第三层）维度**本批次未覆盖** —— MockMvc 用连接池连接，L2 为超级用户，无法在请求内 `SET LOCAL ROLE`。隔离断言全部落在**第二层数据权限拦截器**（应用内 ThreadLocal 驱动，超级用户下仍生效）。两层各有锁，但「一次请求内同时验证两层」无人验证，已登记为遗留缺口。

**遗留（原 §3 的 4 项已全部覆盖）**：`TenantRlsRealDBTest`（6 项）/ `TenantRlsGucRealDBTest`（3 项）/ `TenantIsolationSecurityTest` / `TenantIsolationHttpTest` 保持不动。

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