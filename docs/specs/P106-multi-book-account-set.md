# P106 SPEC — 多账套（企业级收口 + 账簿级立项）

> **版本**：V2.1 | **最后修改**：2026-10-07 | **作者**：opencode（V2.1 为实施回写：D-3 补做删 `t_ai_feedback_log.tenant_id`，关闭双列并存的未决债）
> **编号**：HUICAI-SPC-P106 | 优先级：**P1** | 状态：🚧 **实施中 —— 批次 1a（含 1a-3 / D-1）、批次 1b（D-2a 定性）、批次 2、批次 3（D-3 删列）已完成**；遗留仅 `t_dept` 隔离缺口（见 §0.1.1）与 `t_ai_feedback_log.tenant_id` 删列待决
> **来源**：P101 商用化差距总纲 → P106「内控深度」中的「多账套」子项
> **关联需求**：**REQ-2026-133**（⚠️ V1.0 原写 REQ-2026-134 **有误** —— 134 归 P107 存量缺陷修复包且已实施完成，P101 line 28 已明文「为它让出 134，133 保持不变」；更正记录见登记册 V1.77）| **前置**：RLS 三层已落地（PR #26/#27）、DTO 入参隔离已归零（PR #28~#30）
> **test_ref**：**已存在** —— `TenantRlsRealDBTest`（6 项真库隔离断言）、`TenantRlsGucRealDBTest`、`TenantIsolationSecurityTest`、`TenantIsolationHttpTest`、`ReconciliationServiceImplTest`、`ReconciliationToleranceServiceImplTest`、`ReconciliationIntegrationTest`；**本 SPEC 新增** —— `AccountSetIsolationRealDBTest`（**8 例**，承载 AT-106-1~5 + 1a-3 的两条 + **D-3 schema 守卫一条**）、`TenantSwitchRealDBTest`（11 例，批次 2）、`PaginationTotalTenantIsolationRealDBTest`（2 例，DIR-004）、`SharedTablesExemptionRealDBTest`（5 例，D-2a 定性守卫）
> **⚠️ 范围声明（2026-10-08 取证更正）**：本 SPEC 只覆盖 P106 的**「多账套」子项**。原声明称其余子项（年结、制单≠审核、数据权限粒度、部门级扩展）**仍未立项**，**该声明已被实测推翻** —— 逐项取证结果：年结（`PeriodCloseServiceImpl.closePeriod` + `BatchCloseServiceImpl` + SPEC `S-17` + 真库测试）、制单≠审核（`BusinessDocServiceImpl:337-339`「制单人不能审核自己提交的单据」）、数据权限粒度（`DataPermissionInterceptor` 已实现 ALL/CUSTOM/DEPT/SELF 且已注册进 `MyBatisPlusConfig:50`）**均已实现**。真正未立项的只有「**非 ACTIVE 账套禁写**」，已由 **SPEC-P113** 立项并实施完成（`V1.0`）。⚠️ `DEPT_AND_CHILD` 目前**降级为 `DEPT` 行为**（`DataPermissionInterceptor:219-220` 注释自认「暂不递归查子部门」），属**已知的有意简化**，不在本轮范围。

---

## 0. 事实基线（**全部实测，非推演**）

立项前必须先回答「多账套到底缺什么」。逐条实测结论如下：

| # | 事实 | 证据 |
|---|---|---|
| F1 | **`backend/src/main` 内无账套/账簿维度**：隔离维度只有 `enterprise_id` | `rg -ni "book_set\|bookSet\|账套\|套账\|账簿" backend/src/main` 仅命中 **2 条注释**（`SubjectBalanceServiceImpl:495-496` 「新账套首期」）。⚠️ **V1.1 曾写「无配置项」，实测证伪**：`t_sys_config` 已由 `V1__baseline.sql:1510-1511` 种入 `accounting.start_year='账套启用年度'`、`accounting.start_month='账套启用月份'` 两条**账套级配置**，而该表**既无 `enterprise_id` 也无 `tenant_id`**（见 F2）⇒ 账套概念在配置层已存在但**无企业维度**，是 §8 批次 1 取证的直接线索 |
| F2 | 隔离列的真实分布是**三类**，不是 V1.1 所说的「有/无 `tenant_id`」二类 | 逐表核对 84 条 `CREATE TABLE` 后修正：**①主流形态** = 只有 `enterprise_id`（V102~V105 四个批次补列 + `t_agency_enterprise`）；**②双列并存** = `enterprise_id` 与 `tenant_id` 同时存在（4 张，见 §0.2）；**③两列都没有** = 纯平台全局表（10 张，见 §0.1）。V1.1 把③误述为「仍用 `tenant_id`」，会把取证方向带偏 |
| F3 | 唯一约束**已按企业分段** | `t_subject` `uq_subject_code_ent (code, enterprise_id)`（`V116:7`）；`t_period` `uq_period_code_ent (period_code, enterprise_id)`（`V116:19`）；`t_bank_account` `uq_bank_account_no_enterprise (account_no, enterprise_id)`（`V118:5`） |
| F4 | **「企业 = 账套」已经落地，且有服务端强制边界** | 前端 `layouts/components/EnterpriseSwitcher.vue` + `X-Enterprise-Id` 头；`JwtAuthenticationFilter:84-111` 调 `EnterpriseMembershipChecker.isMember`（三源并集：直属 ∪ 代理授权 ∪ SUPER_ADMIN），不通过即 **403** 并回写 JSON（`:97-104`）；成功后写审计 `auditService.recordEnterpriseSwitch`（`:105-106`）；`:120-122` **无条件** `EnterpriseContextHolder.set(enterpriseId)` |
| F5 | `t_enterprise` 已有**部分账套档案字段** | `V100:34-52`：`mode`（`NOT NULL DEFAULT 'SME'`，CHECK `('SME','AGENCY_CLIENT')`）、`status`（`NOT NULL DEFAULT 'PENDING'`）、`seed_data_done`（`NOT NULL DEFAULT FALSE`）、`agency_id`、`version`；`V134:16` 增 `start_period VARCHAR(6)`（`YYYYMM`，`NULL`=未建账） |
| F6 | **租户/企业维度的硬编码是 4 个常量、8 个有效使用点**（V1.1 写「2 处」，**漏 2 个常量**；V1.2 写「6 处」，**计数与自己的列举不符**，本版更正） | ①`ReconciliationToleranceServiceImpl:29` `DEFAULT_ENTERPRISE_ID = 1L` —— **4 个使用点**（`:43`/`:72`/`:87`/`:104`），读写 `t_reconciliation_tolerance.enterprise_id`（真实列，`V103:45`），**全部有效**；②`OutputInvoiceStateMachineServiceImpl:288-292` `enterpriseId = 1L` —— 上下文为 null 时的兜底，赋给 `BusinessDocEntity.enterpriseId`（`BaseEntity:23-24` 真实列），**有效**，注释自认「理论上不应发生」；③`ReconciliationServiceImpl:73` `DEFAULT_TENANT_ID = 1L` —— 3 处调用但**只有 2 处有效**（`:364`/`:780` 写 `t_reconciliation_log.tenant_id`，真实列 `V94:189` ⇒ **真双列并存**）；`:892` 写 `ReconciliationExceptionEntity.tenantId`，该字段 `@TableField(exist=false)`（`:21-22`）且 `t_reconciliation_exception` **根本没有 `tenant_id` 列** ⇒ **幽灵字段死代码，赋值完全无效**；④`PrepaymentServiceImpl:55` `DEFAULT_TENANT_ID = 1L` —— 1 个使用点（`:107 if (entity.getTenantId() == null)`），写 `t_prepayment.tenant_id`（真实列 `V5:6`）⇒ **真双列并存** |
| F6b | **操作人维度的硬编码另有 6 处，与 F6 不是同一类**（V1.2 曾把两者混计） | `AutoGenerationService:72`、`PrepaymentServiceImpl:56`、`PurchaseReturnServiceImpl:45`、`ReconciliationServiceImpl:74`、`SalesInvoiceImportService:50`、`InputInvoiceImportService:56` 的 `DEFAULT_USER_ID = 1L`。属审计操作人归属（铁律 #5），**已排除出本 SPEC 范围**，详见 §0.3 |
| F7 | 一个普通企业用户**挂不了第二家企业** | `t_user.enterprise_id` 单归属；`uq_username UNIQUE (username)` 为**全局**唯一（`V1__baseline.sql:197`），非 `UNIQUE(username, enterprise_id)`。⇒ 同一用户名在两个企业各建一次会撞唯一键 |
| F8 | **账套状态机实际允许集与本文 §5 流转图不一致**（V1.2 新增，V1.1 的图含非法值） | `chk_enterprise_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','TERMINATED'))`（`V100:51`）；`EnterpriseStatus` 枚举与 `isValidTransition` 同样只有这 4 态，合法迁移为 `PENDING→ACTIVE`、`ACTIVE→SUSPENDED`、`SUSPENDED→{ACTIVE,TERMINATED}`。⚠️ **无 `CLOSED`**，V1.1 的流转图写了非法值（AGENTS §4.2 第 9/14 条的复发形态） |

### 0.1 「两列都没有」的 10 张纯平台全局表（**V1.8 已定性收口 / D-2a**）

`t_agency`、`t_agency_user`、`t_audit_log`、`t_dept`、`t_enterprise`、`t_menu`、`t_role`、`t_role_menu`、`t_sys_config`、`t_user_role`

⚠️ **V1.1 把它们写成「仍用 `tenant_id`」，实测证伪**：逐表核对建表语句，10 张表**既无 `enterprise_id` 也无 `tenant_id`**（`t_agency` 只有 `mode`/`status`/`seed_data_done` 等；`t_audit_log` 13 列全为业务/审计字段 + `deleted`；`t_dept` 12 列含 `dept_code`/`sort_order`）。`rg "ADD COLUMN.*tenant_id"` 在全部 migration 中**零命中** ⇒ 这些表从未有过租户列，「漏加租户列」与「刻意设计为全局」是两种不同解读，必须逐表取证而非默认前者。

⇒ 全部 `relrowsecurity = false`（无 RLS，因为无隔离列可写谓词）。

#### 0.1.1 D-2a 定性结论（**V1.8 落定，零 DDL**）

逐表核对建表语句与后续 `ALTER`（**全部实测**）：这 10 张表**从未有过** `enterprise_id` 或 `tenant_id`
—— `rg "ADD COLUMN.*(enterprise_id|tenant_id)"` 在全部 migration 中对它们**零命中**，
且全部 `relrowsecurity = false`。

| # | 表 | 建表来源 | 定性 | 依据 |
|---|---|---|---|---|
| 1 | `t_menu` | `V1:…`（18 列） | **刻意全局共享** | 权限与导航骨架。一份定义供所有企业复用是主流 SaaS 做法（金蝶/用友的「功能点」即如此）；按企业隔离会导致每个新企业都要重配一遍菜单 |
| 2 | `t_role` | `V1` + `V3` 补列（13 列） | **刻意全局共享** | 角色字典同理；`chk_role_type` 只有 3 个值，是全局枚举而非企业自定义 |
| 3 | `t_role_menu` | `V1`（7 列） | **刻意全局共享** | 角色-菜单授权骨架，`uq_role_menu(role_id, menu_id)` |
| 4 | `t_user_role` | `V1`（7 列） | **刻意全局共享** | 用户-角色绑定骨架。⚠️ `t_user.enterprise_id` 是**归属企业**（非上下文），已在 §0.5 说明 |
| 5 | `t_sys_config` | `V1` + 种子（13 列） | **平台级**（D-2b 裁定 A） | 含 `accounting.start_year`/`start_month` 两条**账套级配置**，裁定为「同一套会计制度参数」全局统一，**不加 `enterprise_id`**（零 DDL） |
| 6 | `t_audit_log` | `V1`（13 列） | **平台级**（D-2b 裁定 A） | 当前**无 `enterprise_id` 列**，历史审计记录**无需归属**（不是「归属不了」，而是设计上就平台级），**不加列** |
| 7 | `t_dept` | `V1` + **`V171`** | ✅ **已按企业隔离（V2.0）** | 部门是企业内主数据。`V171` 补 `enterprise_id`、把 `uq_dept_code`（全局唯一）改为 `uq_dept_code_ent (dept_code, enterprise_id)`、开 RLS + FORCE；`t_dept` 同步移出 `SHARED_TABLES`，`DeptEntity` 补 `enterpriseId (fill=INSERT)`。守卫 `DeptTenantIsolationRealDBTest`（5 例） |
| 8 | `t_agency` | `V100`（14 列） | **平台元数据** | 代理主体，位于隔离维度**之上** |
| 9 | `t_agency_user` | `V112`（14 列） | **平台元数据** | 代理人员，位于隔离维度**之上** |
| 10 | `t_enterprise` | `V100` + `V134`（18 列） | **平台元数据** | 企业主体本身。（其 `enterprise_id` 出现在 `t_agency_enterprise` 里，是**外键**不是本表的租户列） |

⚠️ **`t_dept` 原为本组唯一的真缺口（「已知未修」而非「判定无需修」），V2.0 已修复**：
`AssetReportServiceImpl` / `ExpenseSummaryReportServiceImpl` 的注释自认「t_dept 为共享表（维度名查用，无敏感）」——
这个理由对「只查部门名」成立，但对 `DeptServiceImpl` 的**部门增删改查**不成立。
⇒ 已在批次 1b' 修掉（见下），实施要点与两个踩坑：

**① 必须同时改三处，缺一即半成品**
- **DB**（`V171`）：加 `enterprise_id` + 唯一约束按企业分段 + RLS + FORCE；
- **应用层白名单**：`t_dept` 移出 `SHARED_TABLES`（否则第二层继续不过滤，实测部门树互相可见）；
- **Entity**：`DeptEntity` 补 `enterpriseId` 且标 `@TableField(fill = FieldFill.INSERT)`。

**② 🔴 漏掉 Entity 那一处 ⇒ 症状极具误导性（已实测）**
`DeptEntity` 不继承 `BaseEntity`。只在 DB 加列而 Entity 不声明，MyBatis-Plus **不把该列纳入 INSERT**
⇒ 新建部门落 `enterprise_id = DEFAULT 1` ⇒ 改完唯一约束后，
两个企业建同名部门**仍然撞键**，报错是 `DuplicateKeyException` ——
看起来像「约束没改成功」，真实原因是**写入侧根本没带企业号**（AGENTS §4.5 第 34 条同型）。
而本类的 fill 回调**本来就会触发**（`createdAt` 已是 fill 字段），
`MyMetaObjectHandler.insertFill` 按字段名 `enterpriseId` 无条件覆盖为上下文企业 ⇒ 只加字段即可。

**③ 报表侧无需改动**：`AssetReportServiceImpl:301`、`ExpenseSummaryReportServiceImpl:259`、
`LedgerServiceImpl:429` 都只用 `t_dept` **查部门名**（`selectList(null)` / `selectBatchIds`），
按企业过滤正是期望语义（报告只应出现本企业的部门名）。

#### 0.1.2 ⚠️ V1.8 修正：白名单里另有 4 张表**不属于**「两列都没有」这一类（原 §0.1 漏登）

实施守卫时反查发现：`EnterpriseDataPermissionInterceptor.SHARED_TABLES` 实为 **14 张**，
比 §0.1 登记的 10 张**多 4 张**，而这 4 张**都有 `enterprise_id`** ⇒ 原 §0.1 的
「10 张纯平台全局表」分类**不完整**，与运行时事实已分叉。补登为 **B 类**：

⚠️ **V2.0 更新**：`t_dept` 已于批次 1b' **移出白名单**（V171 补了隔离列并开 RLS），白名单由 **14 张减为 13 张**；下表保留 `t_dept` 条目仅作「曾豁免、现已隔离」的历史留证。

| 表 | `enterprise_id` 来源 | 为何仍须豁免 |
|---|---|---|
| `t_dept` | **`V171`**（本批次新增） | ⚠️ **已不在白名单内** —— 保留此行仅作「曾豁免、现已隔离」的历史留证。其 `enterprise_id` 是真隔离列，语义与 A 类（无隔离列）不同，故归 B 类登记 |
| `t_user` | `V101`（**可空**列） | 该列语义是**归属企业**，不是当前上下文企业。且**存在循环依赖**：登录链路（`AuthController` → `UserDetailsServiceImpl` → `selectByUsername`）必须在**尚无企业上下文**时按用户名查人；若注入 `enterprise_id`，登录时 ctx 为 null ⇒ **所有人无法登录**。⚠️ **V1.9 修正**：此豁免**只对登录/成员校验/代理平台三条路径成立**，原写法把整张表放行，导致用户管理端点跨企业泄漏 PII（见 §0.1.4） |
| `t_agency_enterprise` | `V100` 建表即有（`COMMENT` 实证） | 该表是代理↔企业的**授权关系本身**（拓扑边），不是某企业的业务数据 |
| `t_agency_user_enterprise` | `V112` 建表即有（注释「分配的客户企业ID」） | 同上，授权关系本身，语义为「被服务对象」 |
| `t_service_progress` | `V148` 建表即 `NOT NULL` | 建表注释明写「`agency_id` 隔离 + `enterprise_id` 存被服务客户」，**查询手动按 `agency_id` 过滤以支持代理端跨客户查询**；注入上下文 `enterprise_id` 会把跨客户视图限死 |

⇒ **豁免有两条不同理由**：A 类是「**无隔离列**，注入必然 SQL 报错」；
B 类是「**有隔离列但语义非上下文企业**，注入会破坏其设计语义」。
把二者混为一谈，就会写出「白名单 = 无隔离列的表」这类**错误不变量** ——
而它恰好能通过「只看其中 10 张」的自检。

#### 0.1.3 定性守卫（`SharedTablesExemptionRealDBTest`，5 例）

D-2a 是**零 DDL 的文档收口**，但文档结论会与代码分叉，故必须把结论**锚定到运行时**：

| 断言 | 锁住什么 | 漂移后的后果 |
|---|---|---|
| `whitelistMustEqualAplusB` | 白名单 == A ∪ B，**两个方向都锁** | 少一张 ⇒ A 类**运行期 SQL 报错**、B 类跨客户语义被限死；多一张 ⇒ **静默关闭该表第二层防线且无报错** |
| `groupAMustHaveNoIsolationColumns` | A 类无隔离列 | 「无隔离列故须豁免」的理由失真 |
| `groupBMustHaveEnterpriseIdColumn` | B 类**必须**有 `enterprise_id` | 退化成 A 类却留在 B 类，理由失真 |
| `groupAMustNotHaveRowLevelSecurity` | A 类无 RLS | 无隔离列却开 RLS = 谓词无从表达 |
| `registryCardinalityMustMatchWhitelist` | A=10 / B=4 / 合计=14 | §0.1 增删表而守卫忘改 ⇒ 新表**静默脱离守卫**（§4.5 第 21 条「输入不全」形态） |

**反证 4 类全部实测转红**：①白名单多一张未登记表 ②从白名单移除一张 A 类表
③登记表漏登一张 ④给 A 类表加隔离列。
⚠️ **反证 ②首版无效** —— 注入脚本的匹配串与真实格式不符，`assert` 失败却未中止脚本，
导致跑的是未注入的绿；修正匹配串后立即转红。**判据：反证脚本自身也会失败，
必须确认「注入确实发生」再采信它给出的绿。**

#### 0.1.4 🔴 P0：`t_user` 用户管理端点跨企业泄漏 PII（V1.9 已修）

**发现路径**：D-2a 的定性守卫在写「B 类豁免」时，顺手反查 `t_user` 的全部调用点，
发现 §0.1.2 给自己写的豁免理由**只解释了登录路径，却把整张表放行了**。

**实测证据**（真库探针，非推演）：

```
t_user  relrowsecurity=false  force=false  policies=0
ctx=990007  pageUser(命中 probe_ 前缀)  total=2  records=2
   -> ent=1       username=probe_e1_a     ← 别的企业的用户
   -> ent=990007  username=probe_ex_a
非超级探针 ctx=990007 可见 probe_ 用户数=2   ← DB 层也无隔离
```

⇒ **三层防线全空**：
① 应用层 `UserServiceImpl` 对 `t_user` 的 7 个方法**全无企业维度**；
② 第二层 `EnterpriseDataPermissionInterceptor` 因 `t_user` 在 `SHARED_TABLES` 内而不注入；
③ DB 层 `relrowsecurity=false` + `policies=0`，非超级探针照样看到两家数据。

**暴露面**：`GET /api/v1/system/user/page` 返回用户名/真实姓名/邮箱/手机号；
`getById`/`update`/`updateStatus`/`resetPassword`/`assignRoles`/`delete`
可**按猜测 id 读改删**别家企业用户。

**为什么不能简单把 `t_user` 移出 `SHARED_TABLES`**：
登录链路必须在无上下文时按用户名查人，注入会让 ctx=null ⇒ 谓词恒不成立 ⇒ **全员无法登录**；
代理端（`AgencyUserServiceImpl`）需跨客户企业读本代理用户，其 `enterprise_id` 语义是「归属企业」。
⇒ 豁免**必须保留**，但**用户管理路径要自己补隔离**。

**修法（V1.9 已实施，9 处）**：
- `pageUser`：wrapper 加 `.eq(getEnterpriseId, ctx)` + **无上下文 fail-closed**；
- `getById` / `update` / `updateStatus` / `resetPassword` / `assignRoles` / `delete`：
  先 `selectById` 校验归属，越权**抛 `BusinessException`**；
- `create`：**显式落当前企业**（`UserSaveDTO` 刻意不含 `enterpriseId`，且 `UserEntity` 不继承
  `BaseEntity` ⇒ `insertFill` 不触发 ⇒ 原实现新建用户 `enterprise_id` 为 **NULL**，
  既是「不属于任何企业」的游离数据，也会在列表按企业过滤后**永远不可见**）。
- 负向断言：越权 `updateStatus` 被拒后**数据必须保持原样**（防「先改后校验」）；
  正向对照：本企业用户仍可正常读写，**防修复退化成「一律拒绝」**。

**守卫** `UserTenantIsolationRealDBTest`（6 例，全部走**真实 Service 回路** ——
裸调 mapper 会让用例通过而端点仍泄漏）+ `UserServiceImplTest` 补 2 条负向断言。

**反证 4 处逐条转红**：①去掉 `enterprise_id` 条件 ②退回 fail-open
③去掉 `getById` 归属校验 ④去掉 `create` 的企业落库。
⚠️ 反证 ①首版**无效**（删行后留下悬空语句 ⇒ 编译失败而非红灯），
修正为「整段替换且保持可编译」后才拿到真红 —— **编译失败不算反证成功**。


### 0.2 4 张「`enterprise_id` 与 `tenant_id` 双列并存」的表（**V1.2 新增；⚠️ V1.4 已把「隔离缺口」降级为「数据不一致」，真正的 P0 见 §0.5**）

| 表 | `tenant_id` 来源 | `enterprise_id` 来源 | 风险 |
|---|---|---|---|
| `t_prepayment` | `V5:6` `NOT NULL DEFAULT 1` | `V105:10` | `PrepaymentServiceImpl:55` 写死 `DEFAULT_TENANT_ID=1L`，而 RLS 谓词只认 `enterprise_id` ⇒ **两列可永久不一致且无人守** |
| `t_reconciliation_log` | `V94:189` `NOT NULL DEFAULT 1` | `V105:23` | `ReconciliationServiceImpl:73` 写死 `1L`，**2 个有效写入点**（`:364` 提报日志、`:780` 调整日志）。⚠️ 该常量还有第 3 处调用 `:892`，但写的是**另一张表**且是幽灵字段，见 §0.2 补注 |
| `t_ai_feedback_log` | `V1__baseline.sql:1246` `BIGINT`（可空） | `V104:23` | AI 反馈日志跨企业混存；`tenant_id` 可空 ⇒ 与 `enterprise_id NOT NULL` 语义不一致 |
| `t_classification_rule` | `V2:8` `NOT NULL DEFAULT 1` | `V104:26` | 分类规则（`idx_classification_rule_tenant` 索引仍建在 `tenant_id` 上） |

⚠️ **为什么这 4 张比 §0.1 的 10 张更危险 —— 但定性与 V1.3 不同（V1.4 实测修正）**：V1.3 写「看起来已受 RLS 保护，但代码写的是另一列 ⇒ **产生「已隔离」的错觉**」，暗示存在**隔离泄漏**。**批次 1a 实测证伪了「泄漏」这半句**：

| 核查项 | 实测结果（真实库，非超级用户 `huicai_app`） |
|---|---|
| 4 张表的 RLS 谓词 | 全部为 `(enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint)` ⇒ **只读 `enterprise_id`，完全不感知 `tenant_id`** |
| RLS 是否已开 | `relrowsecurity = t` + **`relforcerowsecurity = t`** + 各 1 条 `enterprise_policy` |
| 跨企业读是否泄漏 | **否**。即使客户端把 `tenant_id` 过滤条件改成任意值，它与 RLS 的 `enterprise_id` 条件是**两个 AND** ⇒ 读不到其它企业的行 |

⇒ **正确的定性是「RLS 隔离层有效，但两列数据不一致导致业务读错」**，不是「隔离失效」。真实危害有三类：①**按 `tenant_id` 的统计/索引基于错误数据**；②**读路径按 `tenant_id` 过滤会查不到行**（见 §0.4）；③**最严重的一类不是这 4 张表，而是 `enterprise_id` 本身写错**（见 §0.5）。

#### 0.2 补注：`t_reconciliation_exception` 的 15 处 `exist = false`（**V1.3 新增，独立缺口**）

`ReconciliationServiceImpl:892` 的 `ex.setTenantId(DEFAULT_TENANT_ID)` **不是双列并存问题，而是幽灵字段**：`ReconciliationExceptionEntity:21-22` 标了 `@TableField(exist = false)`，而 `t_reconciliation_exception` 建表（`V1__baseline:1284-1297`）无 `tenant_id` 列 ⇒ **该赋值完全不参与 SQL**，属 AGENTS §4.2 第 10 条说的**误导性代码**（赋值无效且不可断言）。

复核该 Entity 的 **15 处** `@TableField(exist = false)` 后，按性质分三类：

| 类别 | 字段 | 危害 |
|---|---|---|
| **反向缺口**（最隐蔽） | `updatedAt`（`:104`） | ⚠️ **`V127:34` 已给该表加真实列 `updated_at`**，Entity 却标 `exist = false` ⇒ 该列**从不写入、读回恒 null**。这正是 AGENTS §4.2 第 16 条「真实列存在但 Entity 标成不存在」—— 比幽灵字段更难发现，因为 `\d` 里一眼可见 |
| **被业务代码赋值的幽灵字段** | `tenantId`（`:22`） | `ReconciliationServiceImpl:892` 在赋值 ⇒ 读代码会误以为该表有租户列 |
| 字段遮蔽（无害但冗余） | `createdBy`（`:97`） | 遮蔽 `BaseEntity:27-28` 同名字段（父类同为 `exist=false`） |
| 纯幽灵字段（12 个） | `sourceDocType`、`sourceDocId`、`targetDocType`、`targetDocId`、`partyId`、`partyType`、`unsettledAmount`、`exceptionReason`、`matchSuggestion`、`retryCount`、`assignedTo`、`remark` | 赋值无效、读回恒 null |

⇒ **处置：登记为 P102 待办，不在本 SPEC 的 5 项遗留内**（本轮只清 §0.2 那 4 张表的租户列写入路径，不顺手动 Entity 的幽灵字段 —— 那是另一类缺陷，混在一起会让 DDL 变更无法归因）。已记入 §9 风险表。

### 0.3 6 处 `DEFAULT_USER_ID = 1L`（操作人维度，**本 SPEC 不处理，仅登记**）

`AutoGenerationService:72`、`PrepaymentServiceImpl:56`、`PurchaseReturnServiceImpl:45`、`ReconciliationServiceImpl:74`、`SalesInvoiceImportService:50`、`InputInvoiceImportService:56`。

性质与 §0.2 不同：这是**审计/操作人归属**（铁律 #5 审计追踪的 `operator_id`），不是租户隔离。修它需要「无登录态的后台任务如何记操作人」的产品决策（记 0 / 记系统用户 / 记发起人），超出本 SPEC 范围 ⇒ **登记为 P102 待办，不在本轮实施**。此处记录是为了让 §0.2 的「6 处硬编码」与本节的 6 处**不混淆**（V1.1 的 F6 只数了租户维度的 2 处，把操作人维度完全漏掉，审核时需按维度分开计数）。

### 0.4 读路径也按 `tenant_id` 过滤的 6 处（**V1.4 新增；比 §0.2 更严重**）

§0.2 的 4 张表里，**有 2 张的读路径也按 `tenant_id` 过滤**，共 **6 个方法**把 `tenantId` 当参数，其中 5 处用于过滤或写入：

| 位置 | 用法 |
|---|---|
| `ClassificationRuleServiceImpl:43` | `page()` 读过滤 `WHERE tenant_id = <入参>` |
| `ClassificationRuleServiceImpl:56` | `create()` **写死 `setTenantId(1L)`** |
| `ClassificationRuleServiceImpl:105`/`:139`/`:187` | `seedForNewTenant()` 的存在性检查、插入、逐条插入 |
| `AiFeedbackLogServiceImpl:40`/`:77` | `page()` 与 `summaryByTenant()` 读过滤 |

且两个 Controller 都把它暴露成 **`@RequestParam`（客户端可任意传值）**：`ClassificationRuleController:27`（`page`）与 `:77`（`seed`）、`AiFeedbackLogController` 的 `page` 与 `summaryByTenant`。

**定性：功能缺陷（过滤条件失效），不是安全漏洞** —— RLS 的 `enterprise_policy` 与 `tenant_id = <客户端值>` 是两个 AND 条件，客户端传任意 `tenantId` 也读不到其它企业的行。真实后果是**多企业下 `tenant_id` 全为 1 ⇒ 客户端按企业过滤永远查不到行或查到错行**。

✅ **V1.6 已实施（批次 1a-3 / D-1）**：`tenantId` 请求参数**全部废弃**，6 个方法签名去参，读路径改按 `EnterpriseContextHolder.get()` → `enterprise_id` 过滤；`AT-106-9` 的 `@Disabled` 已解除并转绿，另**新增一条 `match()` 跨企业真库断言**（见 §0.4.1）。

⚠️ **实施中实测发现两处超出门禁预期的缺陷，均已在本批次修掉**（详见 §0.4.1 与 §0.7）：
1. **`match()` 也在按 `tenant_id` 过滤** —— §0.4 原列举的 6 处只覆盖 `page`/`seed`/`create`/`summary`，**漏了 `match()`**；它按 `tenant_id = SecurityUtils.getCurrentEnterpriseId()` 过滤，两列一旦不一致（本项目预付款已出现该形态）会**静默错分**，故一并改为 `enterprise_id`。
2. **D-1 初版是 fail-open** —— 初版写 `if (ctx != null) eq(getEnterpriseId, ctx)` 并在注释里声称「取不到上下文等价于读空集合（安全）」；实测该注释**与代码相反**：`ctx == null` 时整个条件被跳过，读路径退化为**返回全表**。已改为 `requireEnterpriseContext()` **缺失即抛 `BusinessException`**，并新增 6 条 fail-closed 负向断言钉死。

**建议方向：废弃 `tenantId` 请求参数，改用上下文企业。** 理由：①RLS 已按 `enterprise_id` 隔离，客户端再传租户号是**重复且不可信**的隔离维度；②保留即意味着「两个隔离列 + 一个客户端可控」，是 §0.2 双列隐患的延长线；③前端 `EnterpriseSwitcher` 已通过 `X-Enterprise-Id` 切换，无需在每个筛选器里再传一遍。

### 0.4.1 批次 1a-3（D-1）实施回写：两处「门禁没预见到」的缺陷 + 一处断言不敏感

**发现 1：`match()` 是第 7 处按 `tenant_id` 过滤的读路径（原 §0.4 漏列）**

`ClassificationRuleServiceImpl#match` 用
`.eq(ClassificationRuleEntity::getTenantId, SecurityUtils.getCurrentEnterpriseId())` 取规则。
它与已改的 `page()` 读**同一张表**、走**同一层过滤**，却在 §0.4 的 6 处清单外 —— 若只按清单改，
`page` 正确而 `match` 仍读旧列，**两处口径分裂**。真库断言用「两列故意不一致」的夹具
（`enterprise_id=990001 / tenant_id=1`）证伪：按 `tenant_id` 过滤时企业 E **取不到自己的规则**（静默漏匹配）。

> **教训**：§0.4 那种「按方法签名逐个列举」的取证方式，**天然会漏**——
> 判据应是「**哪些代码读/写隔离列**」（`grep getTenantId|setTenantId`），
> 而不是「已知的几个方法名」。本批次据此补上了清单外的第 7 处。

**发现 2：端到端断言对本次改动「不敏感」，反证时才发现是假绿**

按 SPEC 计划，`AT-106-9` 走真实 service 回路断言「企业 E 只看到自己的 1 条规则」。
但**删掉 `page()` 的 `enterprise_id` 过滤后，这条用例依然全绿** —— 因为第二层防线
`EnterpriseDataPermissionInterceptor` 已在 SQL 层注入同一条件，**掩盖了应用层的缺失**。
探针实测（反证过程中）：`total=10 / records=1`（10 = 全表行数）。

⇒ 补**应用层断言**：在纯单测里断言 `LambdaQueryWrapper` 的 SQL 片段含 `enterprise_id` 且
不含 `tenant_id`。**这一步踩了两个坑**，均已沉淀：
- `getCustomSqlSegment()` 需要 MP 的 `TableInfo` **lambda 缓存**，而该缓存**只在 Spring 上下文启动时建立**
  ⇒ 纯 Mockito 单测里直接抛 `MybatisPlusException: can not find lambda cache`（§4.3 第 7 条 Mock 盲区）。
  正解：`TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Entity.class)`。
- 先前退回用 `getParamNameValuePairs()`，**实测恒为空 `{}`** —— 该 map 在**渲染 SQL 时**才填充，
  不是 `eq()` 时就填。⇒ 只能走「显式初始化缓存 + 渲染 SQL 片段」这条路。

**发现 3（顺带查出，超出 D-1 范围）：分页 `total` 跨租户泄漏 —— 已登记 DIR-004（同日闭环）**

反证时探针打出 `total=10 / records=1`，追查发现**与 D-1 无关的既有缺陷**：
`MyBatisPlusConfig` 把 `PaginationInnerInterceptor` 注册在 `EnterpriseDataPermissionInterceptor` **之前**，
而 MP 的分页插件会**自行执行 COUNT 并短路拦截器链** ⇒ **COUNT 永远拿不到 `enterprise_id`**。
用已种子的 `t_subject` 复现（未加任何企业条件）：上下文企业 990001（无科目）时
**`total=43`（企业 1 的条数）/ `records=0`** ⇒ **产品级**：`records` 隔离、总条数泄漏。

⇒ 本批次**先不改全局配置**（铁律 #10），登记 **[DIR-004](dir/DIR-004-pagination-total-tenant-leak.md)**给出 A/B/C 三个方案。

✅ **DIR-004 已于 2026-10-07 按方案 A 闭环**：老丁裁定采方案 A，`MyBatisPlusConfig` 拦截器顺序改为「企业/部门权限 → 分页 → 乐观锁」；机理经 MP 3.5.7 **反编译确认**（`MybatisPlusInterceptor.intercept` 按注册顺序逐个调用 `willDoQuery`→`beforeQuery`，而 `PaginationInnerInterceptor.willDoQuery` 自行执行 COUNT 且该路径不回拦截器链）；新增 `PaginationTotalTenantIsolationRealDBTest` 2 例钉死，反证「还原旧顺序 ⇒ 2 条转红」已实测，修复前实测 `total=43 / records=0`（及 `total=5 / records=3`）。⚠️ **守卫刻意不加任何应用层企业条件、直接调 mapper** —— 加了就等于把缺陷重新掩盖、守卫退化为假绿（与本节第 2 点同一形态）。


### 0.5 `enterprise_id` 静默落 DB 默认值（**V1.4 新增，这才是最严重的一类**）

`PrepaymentEntity` **不继承 `BaseEntity`**（`implements Serializable`）且**全类无任何 `@TableField`/`FieldFill`** ⇒ MyBatis-Plus 的 `TableInfo.withInsertFill = false` ⇒ `MyMetaObjectHandler.insertFill` **根本不被调用**（不是「调用了但没填」，是「压根没进」）⇒ `enterpriseId` 永不被上下文覆盖；而 `t_prepayment.enterprise_id` 是 `V105` 加的 `NOT NULL DEFAULT 1` ⇒ **任何非企业 1 的上下文创建的预付款都落进企业 1**（跨租户默认写入）。沉淀为 AGENTS §4.5 第 34 条。

**影响面（全量扫描）**：18 个实体无 `FieldFill`，其中 8 个不继承 `BaseEntity`；5 个声明 `enterpriseId`，但 `UserEntity`/`AgencyUserEnterpriseEntity`/`AgencyEnterpriseEntity` 的 `enterprise_id` **按设计就是「归属企业」而非上下文**（被覆盖反而是错的）⇒ **只有 `PrepaymentEntity` 是缺陷**。**不能用「有无 `enterpriseId` 字段」判定，必须逐个看语义。**

**治法（实测后收窄）**：**只能**给 `enterpriseId` 补 `@TableField(fill = FieldFill.INSERT)`。**不可**改用「继承 `BaseEntity`」—— 真实库核对列类型即知会引入 **3 处不匹配**：`t_prepayment.created_at`/`updated_at` 是 `date`（基类 `LocalDateTime`）、`created_by` 是 `varchar(50)`（基类 `Long`），且基类还有 `updated_by`/`version` 两列本表没有。⚠️ `PrepaymentEntity:12` 的类注释**早已写明**「不继承 BaseEntity，因为 createdBy(String)/createdAt(LocalDate)/updatedAt(LocalDate) 类型与基类不兼容」—— **动手前先读既有注释**。

### 0.6 L2 的连接角色是超级用户 ⇒ RLS 类断言恒绿（**V1.4 新增，影响所有隔离类验收**）

`AbstractMapperTest:71-75` 用 `withUsername("test")`，而官方 postgres 镜像把 `POSTGRES_USER` 建为**超级用户**。探针实测：`current_user=test`、**`rolsuper=t`**、`bypassrls=t`、`tableowner=test`，同时 `relrowsecurity=t` + **`relforcerowsecurity=t`** + 策略 1 条 + Flyway 到 V167 ⇒ **配置全对，但谓词根本执行不到**。沉淀为 AGENTS §4.5 第 33 条。

⇒ **「切到别的企业应查不到」的断言在 L2 里恒绿**，且绿灯与「隔离已修好」无法区分。**正解**：显式降权探针（`CREATE ROLE ... NOSUPERUSER NOBYPASSRLS` + `GRANT SELECT` + `setAutoCommit(false)` 连接里 `SET LOCAL ROLE` + `set_config(..., true)`，跑完 `rollback()`），做法同 `TenantRlsRealDBTest`（**全库唯一做对的先例**）。⇒ **L2 全绿不代表 RLS 有效**；涉 RLS 的验收必须在「开发库 + `huicai_app`」或「L2 + 探针角色」两处之一做。

## 1. 需求边界：把「多账套」拆成两级

「多账套」在不同厂商语境里指两件不同的事，成本差一个数量级。本 SPEC 明确分层：

| 层级 | 定义 | 现状（实测） | 本轮定位 |
|---|---|---|---|
| **L1 企业级多账套** | 一个用户 / 一个代理同时使用**多个企业各自的独立账** | **已基本落地**（F3/F4/F5），但有 **4 张双列并存表**（F2/§0.2）与 **4 个硬编码常量 / 8 个有效使用点**（F6）待清 | ✅ **只做收口**，清 5 项遗留（§1.2） |
| **L2 账簿级多账套** | **同一个企业内**并存多套账（不同会计制度 / 不同启用期间 / 不同凭证簿） | **完全没有**（F1） | 📋 **只立项不实现**，等客户明确需求再动 |

### 1.1 L1 与 L2 的两种数据模型（决策点）

| 维度 | 模型 A：**复用 `enterprise_id`**（一企业一套账） | 模型 B：**新增 `book_id`**（一企业多套账） |
|---|---|---|
| 表结构改动 | **0 张表** | 全部已补 `enterprise_id` 的表 + 唯一约束 + RLS 谓词 + 全部 Mapper/Service/报表 |
| 隔离机制 | 已有的 `enterprise_id` + 已开 RLS 直接复用 | 需 `enterprise_id + book_id` 复合隔离，且 `TenantRlsInitializer` 需再注入一个 GUC（`app.book_id`），并重做 V167 同款谓词硬化 |
| 迁移风险 | 无数据迁移 | 需回填 `book_id`、双写过渡期、历史数据归属决策 |
| 何时必须 | 「客户/企业之间隔离」—— **当前商用即此形态** | 「同一法人多套账（一般纳税人+小规模、境内+境外）」—— 目前**无客户实例** |
| 结论 | ✅ **立即可用** | ⚠️ **本轮仅立项** |

**推荐路线**：商用化第一阶段只交付 **L1 收口**；L2 保持立项状态，触发条件写进 §9。

### 1.2 L1 收口的 5 项遗留（本 SPEC 的全部实施范围；**V1.4 补实施状态列**）

| 编号 | 遗留 | 依据 | 优先级 | **实施状态（V1.4）** |
|---|---|---|---|---|
| **L1-1** | **4 张双列并存表**（`t_prepayment`/`t_reconciliation_log`/`t_ai_feedback_log`/`t_classification_rule`）定性并收口：代码写 `tenant_id`、RLS 读 `enterprise_id`，两列可永久不一致 | F2/§0.2 | **P0** | 🟡 **部分完成** —— 4 张中 2 张写入路径已收口（`t_prepayment`、`t_reconciliation_log`）；`t_ai_feedback_log`/`t_classification_rule` 的 **6 个读过滤方法待决策**（§0.4，涉客户端契约）。索引缺口已由 `V168` 补齐 |
| **L1-2** | **10 张「两列都没有」的纯平台全局表**逐表定性：哪些刻意全局共享（权限/导航骨架）、哪些应按企业隔离却漏了（`t_dept` 全局唯一编码、`t_sys_config` 含账套配置、`t_audit_log` 跨企业混存） | F2/§0.1 | P1 | ⏸ **未启动** —— 等老丁裁定三类分组 |
| **L1-3** | 清掉 **4 个租户/企业维度硬编码常量**（共 8 个有效使用点，见 F6），取不到上下文一律抛 `BusinessException` | F6 | P1 | ✅ **已完成**（批次 1a-1/1a-2/1a-4）；⚠️ 实际清除的是 **6 个常量 / 8 个有效使用点 + 1 处幽灵字段赋值移除**；另新增发现 `enterprise_id` 落 DB 默认值（§0.5），一并修好 |
| **L1-4** | 明确「一个用户能否挂多家企业」产品决策，并落地（当前只能靠代理授权链） | F7 | P2 | ⏸ 未启动（批次 3） |
| **L1-5** | `t_user.uq_username` 全局唯一 vs `(username, enterprise_id)` 的取舍 | F7 | P2 | ⏸ 未启动（批次 3） |

⚠️ **L1-3 与已落地设计的边界（V1.2 新增）**：`TenantRlsInitializer:65-69` 的既定设计是「上下文为 null 时**直接 return，不设 GUC**」，注释理由是「定时任务/系统初始化等无登录态路径本就不属于任何企业」，且此时 RLS 返 0 行属**预期的 fail-closed**。⇒ **本项只清「有上下文却硬编码 1」这 4 个常量，不得把切面改成「null 即抛异常」** —— 那会把「静默返 0 行」变成「定时任务全站报错」，是 §9 风险表所列风险的放大而非修复。

⚠️ **L1-3 的两类清理动作不可混用（V1.3 新增）**：F6 的 8 个有效使用点分属两种修法 ——
- **真双列并存**（`t_prepayment`、`t_reconciliation_log` 的 4 个写入点）⇒ 改**写 `enterprise_id`**（由 `BaseEntity` 的 `FieldFill.INSERT` + `MyMetaObjectHandler` 强制覆盖，AGENTS §4.5 第 23 条），并保留对 `tenant_id` 的读取兼容；
- **幽灵字段**（`ReconciliationServiceImpl:892`）⇒ 赋值本就无效，**处置是删代码而不是改列**。若照「改成写 `enterprise_id`」去改，会给一个不存在的列加值语义，掩盖 Entity 层缺陷。
两类的验收断言也不同：前者断言「写入行 `enterprise_id` = 上下文企业」，后者断言「**不存在** `tenant_id` 列」+「`:892` 的赋值已移除」。

⚠️ **不在本轮范围但已登记**：`t_enterprise.status` 已有 `PENDING/ACTIVE/SUSPENDED/TERMINATED` 四态状态机（`EnterpriseStateMachineServiceImpl`，人工触发，符合铁律 #1），但**全库无任何一处校验「非 ACTIVE 账套不得产生业务数据」**（`rg "账套未启用|非ACTIVE|ENTERPRISE_NOT_ACTIVE"` 零命中）⇒ 这是一个**尚未立项的独立缺口**，不是本 SPEC 的 5 项遗留之一；§7 的对应场景因此标注为「现状缺口 + 需单独立项」，不在本轮实现。

## 2. 竞品对标（铁律 #15）

| 竞品 | 多账套形态 | 隔离维度 | 与本项目差异 |
|---|---|---|---|
| 用友（畅捷通/好会计） | **一账套一账簿**，多账套并列切换，各自独立科目体系与结账 | `账套`（含公司/账套/年度三层） | 本项目**无年度层**（`t_period` 按年+月，但年度不是独立切换维度）；好会计的账套切换是**登录后选择**而非登录中切换 |
| 金蝶（云星辰/精斗） | 多账套，账套内可切换**账簿/账套**，支持「同组织多账套」 | 组织 + 账套 | 金蝶的账套**共享组织主数据**，本项目是「企业=账套」，粒度更粗 |
| SAP S/4HANA | `Company Code`（公司代码）+ `Fiscal Year` + `Ledger`（账簿）三段式 | 公司代码 + 账簿 | SAP 的 `Ledger` 概念**正是本项目的 L2**；其成本也印证 L2 是重量级变更 |
| QuickBooks | **不支持多账套**，单实体多文件（多 `Company` 文件） | 文件 | 反向证据：轻量 SaaS **刻意不做**多账套 ⇒ 多账套不是商用化必需项 |

**对标结论（写进 SPEC 决策依据）**：
1. 主流厂商把「多账套」做成**并列切换 + 各自独立科目/期间/结账**，与本项目 L1 形态一致；
2. 「同一企业多套账」（SAP Ledger / 金蝶多账簿）确有真实需求，但**三家轻量 SaaS 中只有两家做**，QuickBooks 完全不做 ⇒ **本项目在无客户实例前不投 L2**；
3. 本项目**落后于主流的一项**是**年度层**：主流厂商的切换是「账套 → 年度 → 账簿」三级，本项目只有「企业 → 期间」，跨年切换靠 `t_period` 查询实现，**建议在 L1 收口后评估是否补「默认账期/当前年度」档案字段**（`t_enterprise.start_period` 已有，可作起点）。

## 3. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| `X-Enterprise-Id` | HTTP 头（可选） | 与 JWT 内 `enterpriseId` 不同则触发成员校验；不通过即 **403**，**不得**降级为「忽略并按 JWT 处理」（fail-closed，铁律同 P102） |
| 当前用户 | `t_user` + `t_agency_user` + `t_agency_user_enterprise` | 三源并集（直属 ∪ 代理授权 ∪ SUPER_ADMIN），见 `EnterpriseMembershipChecker` |
| 账套档案 | `t_enterprise`（`status` / `mode` / `start_period` / `seed_data_done` / `version`） | `status` 允许集**已查证**（`chk_enterprise_status`，见 F8）：`PENDING`/`ACTIVE`/`SUSPENDED`/`TERMINATED`，默认 `PENDING`；`mode` 为 `('SME','AGENCY_CLIENT')`，默认 `SME` |
| 业务上下文 | `EnterpriseContextHolder` | 事务内 `SET LOCAL app.enterprise_id`（`TenantRlsInitializer:74`）；**上下文为 null 时不设 GUC**（`:65-69` 既定设计，RLS 返 0 行属预期 fail-closed）；空串/NULL 两种退化形态已被 V167 硬化 |

## 4. 输出契约

| 输出 | 验收标准 |
|---|---|
| 切换鉴权 | 非成员切换 **403** + 审计落库（**已实现**，本轮补回归锁） |
| 跨企业读写隔离 | 切到 A 企业后查询返回 A 的行；写 A 的行在 B 的上下文下 **0 行可见 / 写入被拒**（**已实现**，`TenantRlsRealDBTest` 6/6 已覆盖，补跨企业写拒绝场景） |
| 双列并存表收口 | §0.2 的 4 张表给出结论：代码写 `tenant_id` 的路径改为写 `enterprise_id`（或证明两列恒等并加约束），`t_classification_rule` 的索引迁到 `enterprise_id` |
| 平台全局表定性 | §0.1 的 10 张表逐张给出结论并**落到 DDL 或文档**（不结论不算完成） |
| 硬编码清零 | F6 的 **4 个常量 / 8 个有效使用点**改为读上下文，取不到上下文时抛 `BusinessException`（铁律 #14），**不得静默用 1**；⚠️ **不改 `TenantRlsInitializer` 的 null-return 语义**（见 §1.2 L1-3 边界说明）；幽灵字段赋值**删代码而非改列**（见 §0.2 补注） |
| 唯一约束决策 | L1-4/L1-5 给出结论；若改 `uq_username` 必须走 Flyway 且提供回填脚本 |

## 5. 状态流转

```
t_enterprise.status   （允许集已查证：chk_enterprise_status，见 F8；无 CLOSED）
   PENDING ──(种子数据完成 seed_data_done=true，EnterpriseStateMachineServiceImpl:36-40)──▶ ACTIVE
                                                                          │
                                                            ┌─────────────┴─────────────┐
                                                            ▼                           ▼
                                                      SUSPENDED ──▶ ACTIVE        TERMINATED（终态）
                                                            │                            （不可迁出）
                                              （可恢复，铁律 #1：人工触发）
   ⚠️ 四态之外一律非法：全库无「非 ACTIVE 禁止产生业务数据」的校验（rg 零命中），
      故目前 PENDING/SUSPENDED 账套仍可写业务数据 —— 属未立项缺口，见 §1.2 末段

切换态（每请求，JwtAuthenticationFilter:84-122）
   JWT.enterpriseId ──请求头 X-Enterprise-Id──▶ 成员校验 ──通过/超管──▶ enterpriseId = requested
                                                      │                        │
                                                      └──不通过──▶ 403            ▼
                                                          （不区分「不存在」        上下文非 null 则
                                                            与「无权限」）      EnterpriseContextHolder.set(E)
   头非法(非数字) → requested=null → 跳过校验，沿用 JWT 内企业（:87-91、:92、:108-110）
```

## 6. 异常处理

| 场景 | 处理 | 错误码/行为 |
|---|---|---|
| 切换到非成员企业 | 403，且**不区分「企业不存在」与「无权限」**（避免企业存在性泄露，`JwtAuthenticationFilter:83` 现有约定，`:97-104` 已实现） | 403 |
| 请求头非法（非数字） | 按「未提供」处理（`NumberFormatException` → `requested=null`，`:87-91`），**不得**回退到「放行任意企业」 | 用 JWT 内企业 |
| **有上下文却硬编码 1**（L1-3） | 抛 `BusinessException` 并指明缺失的上下文，**禁止**回落 `enterpriseId/tenantId=1` | 4xx |
| **无上下文的定时任务/批处理** | **维持现状**：`TenantRlsInitializer:65-69` 不设 GUC，RLS 返 0 行（预期的 fail-closed）。⚠️ **不得**改成抛异常 —— 见 §1.2 L1-3 边界说明 | 0 行（预期） |
| 账套非 ACTIVE 时写业务数据 | **当前无校验**（现状缺口，未立项）。若后续立项，抛 `BusinessException` | 4xx |
| 跨企业唯一约束冲突 | 依赖 DB 唯一约束（已按企业分段）+ 友好错误信息，不靠应用层预检 | 409/业务异常 |
| 写入与 RLS 读列不一致（§0.2 双列并存） | 当前表现为「写入成功但 RLS 过滤后不可见」⇒ L1-1 收口前，此类写路径必须显式写 `enterprise_id` | 4xx（收口后） |

## 7. BDD 验收标准（每个场景对应一个 `@Test`，编号见 §7.1）

```gherkin
# 批次 1：双列并存表收口 + 平台全局表定性 + 硬编码清零

Scenario: 预付款写入的 tenant_id 恒等于 enterprise_id（负向：不允许两列不一致）
  Given 企业上下文为 E，t_prepayment 已有一行 enterprise_id = E
  When  通过 PrepaymentService 创建一笔预付款
  Then  该行 enterprise_id = E，且 tenant_id 不再被 Service 独立赋值
  And   反向断言：把 tenant_id 手工改成 ≠ E 后，按 tenant_id 的查询不得命中该行

Scenario: 核销日志写入企业上下文而非常量 1
  Given 企业上下文为 E（E ≠ 1）
  When  ReconciliationServiceImpl 写核销提报日志（:364）与核销调整日志（:780）
  Then  两行 enterprise_id = E；且 tenant_id 不再被 Service 独立赋值为常量 1

Scenario: 核销异常上的 tenantId 是幽灵字段，赋值须移除而非改列
  Given ReconciliationExceptionEntity.tenantId 标 @TableField(exist=false)
  When  清理 ReconciliationServiceImpl:892 的 ex.setTenantId(DEFAULT_TENANT_ID)
  Then  该行代码被移除（负向：不得改为 setEnterpriseId，否则掩盖 Entity 缺陷）
  And   断言 t_reconciliation_exception 无 tenant_id 列（information_schema 查证）

Scenario: 核销容忍度按当前企业查询而非企业 1
  Given 企业上下文为 E≠1，企业 1 与 E 各有一条 t_reconciliation_tolerance
  When  ReconciliationToleranceServiceImpl#getTolerance
  Then  返回 E 的那条；负向：不得返回企业 1 的配置

Scenario: 无企业上下文的批处理必须失败而非写进企业 1
  Given 某 Service 方法内 EnterpriseContextHolder 为空
  When  调用销项发票状态机 / 应收核销容忍度计算
  Then  抛 BusinessException，且 t_output_invoice 不出现 enterprise_id = 1 的新行
  And   负向：TenantRlsInitializer 在上下文为 null 时仍不设 GUC（不得因本项改成抛异常）

Scenario: 平台全局表的定性结论已落库或落文档
  Given §0.1 的 10 张表
  When  逐表核对隔离列与 RLS 状态
  Then  每张表在 §0.1 表格中有明确归类（刻意全局共享 / 应按企业隔离 / 平台元数据）
  And   「应按企业隔离」的表必须有对应 Flyway 号或明确的「不修」理由

# 批次 2：切换鉴权回归锁（补跨企业写拒绝场景）

Scenario: 成员切换成功
  Given 用户 U 通过 t_user.enterprise_id 直属企业 E
  When  带 X-Enterprise-Id: E 发请求
  Then  200，且查询只返回 E 的数据

Scenario: 非成员切换被拒
  Given 用户 U 与企业 E 无任何关联
  When  带 X-Enterprise-Id: E 发请求
  Then  403，且 t_audit_log（或切换审计表）无成功记录

Scenario: SUPER_ADMIN 切换会设置企业上下文（**修正 V1.1 的错误断言**）
  Given user_type = 'SUPER_ADMIN'
  When  带 X-Enterprise-Id: E（E ≠ JWT 内企业）发请求
  Then  200，且 EnterpriseContextHolder 被设为 E
  And   负向：不带该头时，SUPER_ADMIN 的上下文 = JWT 内 enterpriseId（可能为 null，
         为 null 时不设上下文，见 JwtAuthenticationFilter:120-122）
  ⚠️ V1.1 写的是「不设 EnterpriseContextHolder」，与 :120-122 的无条件 set 相反，已修正
```

### 7.1 验收编号（与 REQUIREMENTS_REGISTRY / CI 对齐）

| 编号 | 场景 | 类型 | 落点 | **V1.4 状态** |
|---|---|---|---|---|
| AT-106-1 | 预付款两列一致（负向：手工改 `tenant_id` 后按旧列查不到） | 真实 DB | `AccountSetIsolationRealDBTest` | ✅ 绿（4 例全绿） |
| AT-106-2 | 核销日志（`:364`/`:780`）写入当前企业而非常量 1 | 真实 DB | `ReconciliationIntegrationTest#reconciliationLogMustFollowEnterpriseContext` | ✅ 绿 |
| AT-106-2b | 核销异常的 `tenantId` 幽灵字段赋值已移除（负向：不得改为 `setEnterpriseId`） | 单测（代码审查 + 真实库列核对） | `ReconciliationServiceImpl` 注释留证 | ✅ 已删（负向断言以代码注释与 §0.2 补注锁定） |
| AT-106-3 | 核销容差按当前企业查询（负向：无上下文抛异常而非回落 1） | 单测 | `ReconciliationToleranceServiceImplTest`（8 例全绿） | ✅ 绿 |
| AT-106-4 | 无上下文写路径抛 `BusinessException` 且不写进行 1 | 真实 DB | 同 AT-106-3（容差侧已覆盖） | ✅ 绿 |
| AT-106-4b | **L2 连接角色是超级用户的守卫**（断言 `rolsuper=t`，使隔离类断言必须走探针） | 真实 DB | `AccountSetIsolationRealDBTest#l2RoleIsSuperuserSoRlsAssertionsNeedSetRole` | ✅ 绿（新增，见 §0.6） |
| AT-106-5 | 平台全局表定性结论落文档（**含白名单 14 张全量**） | 文档（**零 DDL**） | §0.1.1 + §0.1.2 + `SharedTablesExemptionRealDBTest` | ✅ **V1.8 绿**（5 例；A 类 10 + B 类 4；反证 4 类全转红） |
| AT-106-6 | 成员切换成功、只返回本企业数据 | 真实 DB + HTTP | `TenantSwitchRealDBTest#memberSwitchRewritesContextAndIsolatesData` | ✅ 绿（**落点改**：见下） |
| AT-106-7 | 非成员切换 403 且审计无成功记录 | 真实 DB + HTTP | `TenantSwitchRealDBTest#nonMemberSwitchDeniedWithNoAuditTrail` | ✅ 绿（**落点改**：见下） |
| AT-106-8 | SUPER_ADMIN 切换**会**设置上下文（负向：不带头时按 JWT） | 真实 DB + HTTP | `TenantSwitchRealDBTest#superAdminSwitchSetsContext` + `#superAdminWithoutHeaderUsesJwtEnterprise` | ✅ 绿（**落点改**：见下） |
| **AT-106-10** | **跨企业写拒绝**（A 的记录在 B 上下文下不可确认，且状态不得被改动） | 真实 DB + HTTP | `TenantSwitchRealDBTest#crossEnterpriseWriteIsRejected` | ✅ 绿（新增） |
| **AT-106-11** | **观测手段守卫**：`current-period` 确实随上下文变化 / 无上下文时返 400 | 真实 DB + HTTP | `TenantSwitchRealDBTest#currentPeriodEndpointActuallyReflectsContext` + `#currentPeriodFailsWithoutContext` | ✅ 绿（新增） |
| **AT-106-12** | **夹具前提守卫**：`t_user.enterprise_id` 不被 fill 机制改写 | 真实 DB | `TenantSwitchRealDBTest#userHomeEnterpriseIsNotOverwrittenByContext` | ✅ 绿（新增） |
| **AT-106-13** | **切换放行必留痕**（否则 AT-106-7「拒绝时无记录」恒真） | 真实 DB + HTTP | `TenantSwitchRealDBTest#allowedSwitchIsAudited` | ✅ 绿（新增） |
| **AT-106-14** | **被拒切换不改写上下文** | 真实 DB + HTTP | `TenantSwitchRealDBTest#deniedSwitchDoesNotRewriteContext` | ✅ 绿（新增） |

### 7.2 批次 2 的落点偏离与理由（V1.5 新增，**偏离原 SPEC 计划**）

原 §7.1 计划把 AT-106-6/7 落在**已有的** `TenantIsolationHttpTest` / `TenantIsolationSecurityTest`「补断言」。**实测该方案不可行，已改落点**，理由三条：

1. **`TenantIsolationHttpTest` 结构上无法承载**。它只断言「无凭证 → 401」，原因是「无 JWT 时请求本就会被 401 挡下」（其原注释）。要回答「带合法凭证时上下文如何变」，必须**签发真 JWT**；而该类与 `EnterpriseControllerTest` 一样把 `JwtProvider`/`StringRedisTemplate`/`UserDetailsServiceImpl` 全部 `@MockBean` 掉 ⇒ 一旦引入真 JWT，就要连带把这一整套 mock 拆掉，等于重写该类而非「补断言」。
2. **三个场景需要同一套真库夹具**（两个企业 + 直管用户 + 代理授权用户 + STRANGER），拆到两个既有类里会重复造数，且两类的 mock 策略并不兼容。
3. **`TenantIsolationSecurityTest` 验的是「规则」不是「链路」**（其原注释已自陈分工）。上下文是否被改写只有走过滤器才观察得到。

⇒ 新建 `TenantSwitchRealDBTest`（11 例）一次承载 AT-106-6/7/8 + 跨企业写拒绝 + 4 条守卫。

### 7.3 观测手段的选择（V1.5 新增）

**用 `/api/v1/enterprise/current-period` 作上下文探针，而不是为测试新增端点**：该端点直接以 `EnterpriseContextHolder.get()` 为入参查 `t_enterprise`，而 `t_enterprise` 属 `EnterpriseDataPermissionInterceptor.SHARED_TABLES`（不被注入条件），故「响应的 `startPeriod` 是哪一家」严格等价于「过滤器把上下文设成了哪一家」。两个测试企业各设一个不同的 `start_period`（`209801` / `209802`）即可无歧义区分。

⚠️ **该探针自身必须被守卫**（AT-106-11）：若它某天改为读 JWT、读默认值或对所有企业返回同一常量，则 AT-106-6/8 会**集体恒绿**且无任何异常。故除「两次请求返回不同值」的正控外，另配一条**反向自检**（无上下文时该端点必须返 400）—— 后者实测抓到了「基类 `@BeforeEach` 预置上下文导致无上下文场景恒绿」的夹具陷阱，详见 AGENTS 新增条目。
| **AT-106-9** | **分类规则 / AI 反馈的读过滤应按 `enterprise_id`**（§0.4 的 6 个方法） | 待定 | `AccountSetIsolationRealDBTest` 中一条 **`@Disabled`** 用例 | ⏸ **挂起待决策** —— `tenantId` 请求参数去留属客户端契约变更（铁律 #10） |

⚠️ **与现有用例无冲突（已核实）**：`TenantIsolationSecurityTest#superAdminAllowedEverywhere:78-82` 只断言 `checker.isMember(...)` 返回 `true`，**未涉及 `EnterpriseContextHolder`** ⇒ AT-106-8 是**新增断言**而非修正既有断言，不会与 P102/AT-102-1c 打架。`nullTargetIsAllowed:94-97` 同理只覆盖 `isMember` 的 null 分支。

## 8. 实施分批（每批独立可回滚）

| 批次 | 内容 | 风险 | 门禁要求 | **V1.5 状态** |
|---|---|---|---|---|
| **1a** | 4 张双列并存表收口（写 `enterprise_id`、索引补齐） | 中（动 DDL 与写路径） | 三方对照审计（PG ↔ Entity ↔ 业务代码）；DDL 走 Flyway；`node scripts/check-entity-schema.mjs`；覆盖率门禁 `All coverage checks have been met` | 🟡 **部分完成** —— 5 个微循环：1a-1 / 1a-2（含 2b）/ 1a-4 / 1a-5 已完成；**1a-3 挂起待决策**（§0.4） |
| **1b** | 10 张平台全局表逐表定性 | 中 | 同上 | ⏸ **未启动** —— 等老丁裁定三类分组 |
| **2** | 切换鉴权与跨企业隔离的**真库回归锁**（补**跨企业写拒绝**与 AT-106-8） | 低 | L2 全绿；L1 `All coverage checks have been met` | ✅ **已完成**（2026-10-06）—— 新增 `TenantSwitchRealDBTest` 11 例，AT-106-6/7/8/10~14 全绿；**未发现新生产缺陷**（详见 §8.1） |
| **3** | L1-4 / L1-5 产品决策落地（可能含 `uq_username` 迁移） | 高（影响登录） | 需老丁单独审核 + 回填脚本 | ⏸ 未启动 |

### 8.1 批次 2 的结论：**零生产缺陷，回归锁已就位**（V1.5 新增）

与批次 1a 不同，批次 2 **没有发现任何生产缺陷** —— `JwtAuthenticationFilter` 的成员三源并集校验、上下文改写、审计留痕三条链路实测均正确。本批次的价值是**把这些行为钉成回归锁**，因为此前它们**完全没有 HTTP 层的回归保护**：

| 行为 | 批次 2 之前的保护 | 现状 |
|---|---|---|
| 非成员切换被拒 403 | 只有**规则层**（`TenantIsolationSecurityTest` 直接调 `checker.isMember`） | 规则层 + **链路层**双锁 |
| SUPER_ADMIN 切换会改写上下文 | **无**（`superAdminAllowedEverywhere` 只断言 `isMember` 返回 true） | AT-106-8 + AT-106-11 锁死 |
| 跨企业写被拒 | **无** | AT-106-10 锁死 |
| 拒绝时审计无成功记录 | **无** | AT-106-7 + AT-106-13（正控）锁死 |

⚠️ **「规则层绿 ≠ 链路层绿」**：规则正确但没接进过滤器，同样等于没修 —— 与 P104 覆盖率门禁假绿同族。批次 2 之前正是这个状态：`TenantIsolationSecurityTest` 6 例全绿，但**没有任何一条能证明过滤器真的调用了它**。

⚠️ **RLS 维度本批次未覆盖（诚实声明）**：§0.6 要求「涉 RLS 的断言必须走非超级探针」，而 HTTP 层用的是连接池连接（L2 为超级用户）⇒ **无法在 MockMvc 请求内降权**。故本批次的隔离断言全部落在**第二层（`EnterpriseDataPermissionInterceptor` 数据权限拦截器）**，该层是应用内 ThreadLocal 驱动的，与连接角色无关，**在超级用户下依然真实生效**。第三层（RLS 谓词）的真库探针验证仍由 `TenantRlsRealDBTest`（6 例）与 `AccountSetIsolationRealDBTest#prepaymentIsInvisibleUnderOtherEnterpriseContext` 承担。**两层各自有锁，但「一次请求内同时验证两层」目前无人验证** —— 已登记为遗留缺口。

### 8.2 批次 2 的反证矩阵（V1.5 新增，AGENTS §2.3 要求）

逐条注入缺陷、实测转绿→转红→还原（生产代码最终 `git status` 干净）：

| 注入的缺陷 | 转红的用例数 |
|---|---|
| ① 去掉成员校验（`allowed` 恒 `true`） | 2（AT-106-7 及其配套） |
| ② 不改写上下文（删掉 `enterpriseId = requested`） | **4**（AT-106-6 / AT-106-8 / AT-106-10 / AT-106-11） |
| ③ 审计多写一条 | 1（AT-106-13） |
| ④ 给 `UserEntity.enterpriseId` 补 `@TableField(fill=INSERT)` | 1（AT-106-12） |

**注入 ② 一处缺陷同时打红 4 条**，是本批次断言有效性的核心证据 —— 说明这些用例确实在观察「上下文」，而不是恒绿。

⚠️ 注入 ④ 顺带**证伪了本批次最初的一个前提假设**：原以为 `t_user.enterprise_id` 是「因为按设计属于归属企业、所以不被 `insertFill` 覆盖」，实测去掉 `withoutEnterpriseContext` 包裹后断言**依然绿** ⇒ 真实机制是 `UserEntity` 不继承 `BaseEntity` 且全类无 `@TableField` ⇒ `withInsertFill=false` ⇒ **`insertFill` 压根不被调用**（AGENTS §4.5 第 34 条同型）。结论虽相同，但**理由完全不同**，若按错误理由理解，会误以为「给该字段补 fill 注解是安全的风格统一」—— 实测证明补上即立刻转红（建账号时指定企业的功能失效）。AT-106-12 就是拦这一手的闸门。

⚠️ **批次 1a 必须先做而不能与 1b 合并**：§0.2 的 4 张表「看起来已受 RLS 保护」，属**当前就在产生错误数据**的缺口（写入 `tenant_id=1` 而 RLS 读 `enterprise_id`）；§0.1 的 10 张表是「一致地未隔离」，是**设计与实现的取舍问题**，可慢。混在一起做会让 DDL 变更的因果无法归因。

## 9. 风险与明确不做（Out of scope）

**不做**：
- L2 账簿级多账套（`book_id`）的任何代码/DDL —— 触发条件：①出现明确客户需求（同一法人多套账）；②或同时管理 ≥3 个企业的代理客户提出报表合并诉求。
- 不改 `t_enterprise` 的既有字段语义；不引入 `fiscal_year` 独立切换层（**先评估再立项**，见 §2 对标结论 3）。
- 不动已开 RLS 策略的形状（V167 谓词保持不变）。
- **不改 `TenantRlsInitializer` 的「上下文为 null 则不设 GUC」语义**（见 §1.2 L1-3 边界说明）—— 改了会把「静默返 0 行」变成「定时任务全站报错」。
- **不实现「非 ACTIVE 账套禁止产生业务数据」的校验** —— 该能力全库不存在（rg 零命中），属独立未立项缺口，需单独立项。
- **不处理 6 处 `DEFAULT_USER_ID = 1L`**（§0.3）—— 属审计操作人维度，需「后台任务如何记操作人」的产品决策，登记为 P102 待办。
- **不改 `tenantId` 请求参数契约**（§0.4 的 6 个方法）—— 需先在本文档 §10 之后单独决策；本轮只留 `@Disabled` 用例。
- **不删 `t_classification_rule.tenant_id` 列与其旧索引** —— 破坏性 DDL（AGENTS §7）+ 抢先替 §0.4 的业务决策；`V168` 只用 `COMMENT` 标注为待清理。

**风险**：
| 风险 | 缓解 | **V1.4 状态** |
|---|---|---|
| 「全局共享」误判为「应隔离」，导致代理端菜单/角色配置跨企业失效 | 每张表必须**先取证再定论**，定论写进 §0.1 表格并在 PR 描述留证；权限/导航骨架类默认按「刻意全局共享」处理，除非能举出具体越权/数据丢失场景 | ⏸ 待裁定 |
| 清硬编码后定时任务/批处理开始报错（此前靠 `1L` 侥幸跑通） | 盘点**所有无上下文的入口**；对**确实无上下文**的入口，处置是「显式声明 + 走独立事务边界」，不是抛异常 | ✅ 已按此实现：4 个常量改读上下文；`tenant_id` 在无上下文时与 `enterprise_id` 一同落 DB 默认 1；容差侧无上下文才抛异常（无替代值可猜） |
| 双列并存表收口时历史数据两列不一致 | 收口前先跑**一致性核查** | ✅ 已跑：4 张表开发库 `total = 0` ⇒ **无历史不一致行、不需回填**。⚠️ 但这也意味着「mismatch=0」是**「无数据」而非「已一致」**，不构成缺陷未发生的证据 —— 缺陷须由测试自己造数暴露 |
| 出参面 110 端点仍直出 Entity（含 `UserController#get/#page` 返回 `UserEntity`） | 与本 SPEC 无关但**同属 P102**。⚠️ V1.1 曾称其「泄漏 `password` 哈希」，实测 `UserEntity:20-21` 已有 `@JsonProperty(access = WRITE_ONLY)` ⇒ **不会序列化输出**，此风险描述已失效，改为「出参面未用 VO」的通用问题 | ⏸ 登记在 P102 |
| 覆盖率棘轮缓冲不足（BRANCH 仅 0.81 点，AGENTS §0） | 新增代码须同步补测试；实测口径必须 `mvn clean test` | ✅ 已验证：L1 `1647/0/0/5`、L2 `2047/0/0/6`，两次 `All coverage checks have been met` |
| 🔴 **`ReconciliationExceptionEntity` 有 15 处 `exist = false`，其中 `updatedAt` 是反向缺口**（V1.3 复审发现，**已登记 P102 待办**）：`V127:34` 已给该表加真实列 `updated_at`，Entity `:104` 却标 `exist = false` ⇒ 该列**永不写入、读回恒 null**；另有 `tenantId` 曾被 `ReconciliationServiceImpl:892` 赋值（幽灵字段误导性代码，**已在批次 1a-2 删除该赋值**）。**为何不并入本轮**：与 §0.2 的 DDL 收口不同源，混做会让变更无法归因 | ⏸ P102 待办 |
| 🔴 **L2 Testcontainers 的连接角色是超级用户 ⇒ RLS 类断言恒绿**（V1.4 实测，见 §0.6）：涉 RLS 的验收若在 L2 里直接查，**绿灯无意义** | 隔离断言必须显式降权探针（`SET LOCAL ROLE` 到 `NOSUPERUSER`），并保留 `l2RoleIsSuperuserSoRlsAssertionsNeedSetRole` 守卫 | ✅ 本轮已按此改造（跨企业不可见用例转绿即证明 RLS 层有效）；⚠️ **后续批次 2 必须沿用该写法** |
| 无机器可读契约导致 SPEC 门禁对本 SPEC 只报「coverage gap」 | 沿用现状（100 份中 92 份无契约），建议下批次补 YAML 契约 | ⏸ 未做 |

## 10. 待决策项 —— ✅ **V1.5 已裁定（2026-10-06，老丁）**

| # | 决策项 | 背景 | 建议 |
|---|---|---|---|
| **D-1** | **`tenantId` 请求参数去留**（§0.4 的 6 个方法 + 2 个 Controller 的 `@RequestParam`） | 客户端可控的租户号与 RLS 的 `enterprise_id` 重复；RLS 已挡住越权，故只是功能缺陷 | **废弃该参数，改用上下文企业**。保留即等于「两个隔离列 + 一个客户端可控」 |
| **D-2** | **§0.1 十张平台全局表的三类分组**（批次 1b 的前提） | 权限/导航骨架 4 张、`t_dept`/`t_sys_config`/`t_audit_log`、`t_agency`/`t_agency_user`/`t_enterprise` | 权限骨架按「刻意全局共享」；`t_dept`/`t_sys_config`/`t_audit_log` 按「应隔离」；代理拓扑 3 张按「平台元数据」 |
| **D-3** | `t_classification_rule.tenant_id` 列与 `idx_classification_rule_tenant` 索引是否删除 | 依赖 D-1；删列属破坏性 DDL | **等 D-1 定案后再定**，本轮已用 `COMMENT` 标注为待清理 |

### 10.1 裁定结论与解锁范围（V1.5 新增）

| # | 裁定 | 解锁 |
|---|---|---|
| **D-1** | ✅ **废弃 `tenantId` 请求参数，改用上下文企业**（改读 `EnterpriseContextHolder.get()`） | 批次 **1a-3 已完成（V1.6）**：`AT-106-9` 的 `@Disabled` 已解除并转绿，另新增 `match()` 跨企业真库断言 + 6 条 fail-closed 负向断言（§0.4.1） |
| **D-2** | ✅ **拆成 D-2a + D-2b；V1.8 两项均已落地** | **D-2a**：10 张「无隔离列」表定性落文档（**零 DDL**）+ 守卫 `SharedTablesExemptionRealDBTest`（5 例）；实施中反查发现白名单实为 14 张，补登 **B 类 4 张**（有 `enterprise_id` 但语义非上下文企业）；**D-2b**：`t_sys_config`/`t_audit_log` 均选 A **保持平台级不加列**；`t_dept` 定性为**真隔离缺口**，隔离排期到后续 DDL 批次 |
| **D-3** | ✅ **随 D-1 联动删除 —— V1.7 已执行 / V2.1 补做收尾** | **`V170__p106_d3_drop_classification_rule_tenant_id.sql`**：`DROP INDEX IF EXISTS idx_classification_rule_tenant` + `ALTER TABLE ... DROP COLUMN IF EXISTS tenant_id`（均非破坏性写法），并清理 `ClassificationRuleEntity.tenantId` 字段与 `ClassificationRuleServiceImpl` 的 `setTenantId` 赋值。⚠️ **`t_ai_feedback_log.tenant_id` 刻意未删** —— 它同属 §0.2 双列并存表但不在 D-3 裁定范围，按铁律 #7 与 §7.7 不得顺手扩大 DDL 范围；该列已无任何读写路径，删除需另行决策 |

⚠️ **D-1 是客户端契约变更，不是可以静默合入的后端单方面改动**：后端删 4 个 `@RequestParam`、6 个方法签名去 `tenantId`，而前端 `ClassificationRuleController` 2 处 + `AiFeedbackLogController` 2 处调用需**同期**改。若不同期，表现为「筛选参数被服务端忽略」—— 功能退化但**不报错**，属最难发现的一类回归。

✅ **D-2b 两项已由老丁裁定（2026-10-07，V1.6 回写）**，见
[决策备忘](../development/plans/2026-10-06-p106-d2b-and-account-mapping-memo.md)：
- **`t_sys_config` → 选 A：保持平台级全局共享**，不加 `enterprise_id`（零 DDL）。
  含 `accounting.start_year` / `start_month` 两条账套级配置**按全局统一**处理 ——
  即「同一套会计制度参数」，不是「每企业各设一套账期起点」。
- **`t_audit_log` → 选 A：保持平台级**，不加 `enterprise_id`。
  审计表**当前无该列**，历史记录无需归属（不是「归属不了」，而是**设计上就平台级**）。
- ⚠️ 连带影响：`EnterpriseDataPermissionInterceptor.SHARED_TABLES` 已包含这两张表，
  故该裁定与运行时行为**一致**，无需改拦截器。

---

## 11. 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| **V2.1** | 2026-10-07 | opencode | **D-3 补做：删除 `t_ai_feedback_log.tenant_id`，关闭双列并存的未决债**。**①为什么补做而不是继续「暂存」** —— V170 裁定只点名 `t_classification_rule`，`t_ai_feedback_log` 因此留下一张「暂存」的旧列。复核结论：两表同属 §0.2 双列并存形态、同处 V105 补 `enterprise_id` 的批次，D-1 已把 ai_feedback_log 的全部读写路径改为 `enterprise_id`（主代码 grep 0 处），且该列无 NOT NULL、无 FK、无索引 ⇒ 删列零风险，而「留着」只会继续向读写路径发出「这张表仍有两个隔离列」的错误信号。用 V172 封口，SPEC 里原先「刻意未删」的字样由此迁到「已删」。**②仍须三方同改** —— V172（`DROP COLUMN IF EXISTS`）+ `AiFeedbackLogEntity` 删字段（否则 MP 纳入 SELECT/INSERT 真库报 `column does not exist`）+ 测试夹具（`AiFeedbackLogServiceImplTest` 的 `setTenantId` 赋值已删；原 D-1 单测断言已不含该列，grep 零残留）。**③守卫** —— `AccountSetIsolationRealDBTest#aiFeedbackLogTenantIdColumnMustBeDropped`（严格时序观测：先从 `src` 与 `target/classes` **双删** V172 使库回到 V172 之前状态 => 守卫实测 **RED** `expected: <0> but was: <1>`，还原 => **GREEN**；双删是必须的，只删 `src` 会让 Flyway 因 `target/classes` 残留而照样应用 V172 => 假绿，见 §4.5 第 19 条）；**反证**：把列加回库 ⇒ 守卫转红（`mvn clean`，无编译失败污染）。**④「工具告警 ≠ 缺陷」复核（§4.5 第 23 条再次适用）** —— `check-entity-schema.mjs` 报「字段 enterpriseId → 列 enterprise_id」后，实测开发库停在 **V166**、落后 3 个版本；重启应用把开发库推到 **V171** 后脚本 ✅ 通过。本批次 V172 同样先推开发库，脚本随即通过。**验证**：L1 `1665/0/0/5`（clean 口径 49.14/41.57/67.87，阈值 49/41/67，`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；L2 `2124/0/0/5`（+1 真库；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests` 全绿；三静态门禁全过。 |
| **V2.0** | 2026-10-07 | opencode | **批次 1b'：`t_dept` 隔离落地；附 🔴 P0 `t_user` 泄漏修复**。**①三处同改，缺一即半成品** —— DB（`V171`：加 `enterprise_id` + `uq_dept_code` 改按企业分段 + RLS + FORCE）、应用层（`t_dept` 移出 `SHARED_TABLES`）、Entity（`DeptEntity` 补 `enterpriseId (fill=INSERT)`）。**②🔴 漏掉 Entity 那一处的症状极具误导性（实测）** —— `DeptEntity` 不继承 `BaseEntity`，只在 DB 加列则 MP 不纳入 INSERT ⇒ 新部门落 `DEFAULT 1` ⇒ **改完唯一约束后两个企业建同名部门仍撞键**，报错是 `DuplicateKeyException`，看着像「约束没改成功」，真实原因是**写入侧根本没带企业号**（§4.5 第 34 条同型：Entity 不继承 BaseEntity ⇒ insertFill 不触发）。本类 fill 回调本就会触发（`createdAt` 已是 fill 字段），`insertFill` 按字段名无条件覆盖为上下文企业 ⇒ 只加字段即可。**③D-2a 守卫如设计般报警** —— 加了 `enterprise_id` 后 A 类断言即转红；按其设计意图把 `t_dept` **重分类 A→B** 并把基数从 10+4 改为 9+5（白名单 14→13）。**④守卫 5 例** —— ①两企业可各建同名部门（核心价值）②同企业内仍唯一（防过度修复）③部门树不跨企业 ④**非超级探针下 RLS 真的生效**（L2 连接是超级用户会绕过 RLS，应用层断言无法证明第三层）⑤守卫前提 rolsuper=t。**⑤反证 4 处逐条转红**：放回白名单 / 去掉 Entity 字段 / 唯一约束改回全局 / RLS 谓词改 `USING true`；其中后两处**首版无效**（注入破坏了 Java 结构导致编译失败），改为「注入放方法首部 + 利用 clean 重建容器免恢复」后拿到真红。**⑥开发库滞后被工具如实报出** —— `check-entity-schema.mjs` 报「t_dept 无 enterprise_id 列」，实测开发库停在 **V166**、落后 3 个版本，且 `t_dept` 存量 **0 行**（DEFAULT 1 误归属风险不存在）；按 §4.5 第 23 条先确认版本再下结论，重启应用把开发库推到 **V171** 后脚本 ✅ 通过。**⑦报表侧零改动** —— 三处只用 `t_dept` 查名，按企业过滤正是期望语义。**验证**：L1 `1665/0/0/5`、L2 `2123/0/0/5`（+5 真库；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）、前端 265 全绿、三静态门禁全过、既有 `DeptMapperConstraintsRealDBTest` 全绿（同企业内仍撞唯一键，验证约束分段未做成过度修复）。 |
| **V1.9** | 2026-10-07 | opencode | **🔴 P0 修复：`t_user` 用户管理端点跨企业泄漏 PII**。**①缺陷实测坐实** —— `t_user` **三层防线全空**：应用层 `UserServiceImpl` 7 个方法全无企业维度；第二层因 `t_user` 在 `SHARED_TABLES` 内而不注入；DB 层 `relrowsecurity=false`/`policies=0`，非超级探针可见两家数据。实测 `ctx=990007` 的 `pageUser` 返回了企业 1 的用户，`getById`/`update`/`delete`/`resetPassword` 可**按猜测 id 读改删**别家企业用户。**②根因是我自己的豁免理由过宽** —— §0.1.2 写「成员校验要读它，存在循环依赖」，那只解释了**登录路径**，却把整张表放行；D-2a 的守卫在写 B 类登记时才发现。⚠️ **判据：给某张表开豁免时，必须逐路径列出「哪些路径需要豁免」，「整表豁免 + 只解释一条路径」必然漏**。**③不能简单移出白名单** —— 登录须在无上下文时按用户名查人，注入会让 ctx=null 谓词恒不成立 ⇒ 全员无法登录；代理端亦需跨客户企业读本代理用户。⇒ 豁免保留，用户管理路径**自己补隔离**。**④实施 9 处** —— `pageUser` 加 `enterprise_id` 条件 + 无上下文 fail-closed；`getById`/`update`/`updateStatus`/`resetPassword`/`assignRoles`/`delete` 先校验归属、越权抛异常；`create` 显式落当前企业。⚠️ **`create` 那处是连带发现**：`UserSaveDTO` 刻意不含 `enterpriseId`，且 `UserEntity` 不继承 `BaseEntity` ⇒ `insertFill` 不触发 ⇒ 原实现新建用户 `enterprise_id` 为 **NULL**，既是不属于任何企业的游离数据，也会在列表按企业过滤后**永远不可见**（与预付款 1a-1 同型的静默错位）。**⑤守卫 6 例全部走真实 Service 回路**（裸调 mapper 会让用例通过而端点仍泄漏），含「越权写被拒后数据保持原样」与「本企业仍可读写」两条双向断言。**⑥反证 4 处逐条转红**；⚠️ 反证 ①首版**无效** —— 删行后留下悬空语句，编译失败而非红灯，修正为整段替换后才拿到真红。**判据：编译失败不算反证成功**（§4.5 第 41 条同型）。**验证**：L1 `1665/0/0/5`（clean 口径 49.15/41.57/67.88，阈值 49/41/67，`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；L2 `2118/0/0/5`（+6 真库 +2 单测；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）—— **登录链路与代理端流程零回归**；前端 265 全绿、三静态门禁全过。 |
| **V1.8** | 2026-10-07 | opencode | **批次 1b（D-2a）实施回写：零 DDL 定性收口 + 守卫查出原 SPEC 一处漏登**。**①D-2a 是零 DDL** —— 不改列、不改 SQL，只把「哪些表刻意不做企业隔离」写成可查结论。**②逐表实测建表与后续 ALTER** —— 10 张表**从未有过** `enterprise_id`/`tenant_id`，`relrowsecurity` 全为 false；三类定性（权限骨架 4 / 平台级 2 / 平台元数据 3）+ `t_dept` 单列为真缺口。**③`t_dept` 明确为「已知未修」而非「判定无需修」** —— 其隔离理由（`uq_dept_code` 全局唯一 ⇒ 两企业不可能有同名部门编码 + `DeptServiceImpl` 实测不带企业维度查全表）成立；而报表侧注释自认的「维度名查用，无敏感」只对查名成立、对增删改不成立 ⇒ 排期到后续 DDL 批次。**④🔴 守卫反查查出原 §0.1 漏登 4 张表** —— `SHARED_TABLES` 实为 **14 张**，多出的 4 张（`t_user`/`t_agency_enterprise`/`t_agency_user_enterprise`/`t_service_progress`）**都有 `enterprise_id`**。⇒ 「10 张纯平台全局表」这个分类**不完整**，已与运行时事实分叉；补登为 **B 类（语义豁免）**。⚠️ **两类理由根本不同**：A 类是「无隔离列，注入必然 SQL 报错」，B 类是「有隔离列但语义非上下文企业，注入会破坏设计语义」——混为一谈会写出「白名单 = 无隔离列的表」这类**错误不变量**，而它恰好能靠「只看其中 10 张」通过自检。**⑤守卫 `SharedTablesExemptionRealDBTest`（5 例）** —— 断言白名单 == A ∪ B（**两个方向都锁**）、A 类无隔离列、B 类**必须**有 `enterprise_id`（反向对照）、A 类无 RLS、基数 10+4=14。**为什么「零 DDL 的文档收口」也需要守卫** —— 文档性结论最容易的失效形态是**与代码分叉**；白名单是「放行不过滤」的名单，**多一张业务表 = 静默关闭该表第二层防线且无任何报错**。**⑥反证 4 类全部转红** —— ①白名单多一张 ②移除一张 A 类表 ③登记表漏登一张 ④给 A 类表加隔离列。⚠️ **反证 ②首版无效** —— 注入脚本的匹配串与真实格式不符，`assert` 失败却未中止脚本，于是跑的是未注入的绿；修正匹配串后立即转红。**判据：反证脚本自身也会失败，必须确认「注入确实发生」再采信它给出的绿**（与 §4.5 第 41 条同型）。**验证**：L1 `1663/0/0/5`、L2 `2110/0/0/5`（+5 即本守卫；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）、前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests` 全绿、三个静态门禁全过。**遗留**：（无已知代码级隔离缺口）**V2.1 附：D-3 补做落地，`t_ai_feedback_log.tenant_id` 已随 `V172` 删除，双列并存的未决债闭环**（L2 `2124/0/0/5`）。 |
| **V1.7** | 2026-10-07 | opencode | **批次 3（D-3）实施回写：删列落地 + 一处夹具诚实降级 + 一处迁移顺序教训**。**①范围严格按裁定** —— 只删 `t_classification_rule.tenant_id` 列与 `idx_classification_rule_tenant` 索引；**`t_ai_feedback_log.tenant_id` 刻意未删**（同属 §0.2 双列表但不在 D-3 裁定内，按铁律 #7 三方对照 + §7.7 不得顺手扩大 DDL 范围），已登记为遗留待决策。**②TDD 先行** —— 新增 `AccountSetIsolationRealDBTest#tenantIdColumnAndIndexMustBeDropped` 先跑出 `expected: <0> but was: <1>`（列仍在）确认 RED，再写 V170 转绿。**③Entity 字段必须同步删** —— 只删 DB 列而留着 `ClassificationRuleEntity.tenantId`，MyBatis-Plus 会把该字段纳入 SELECT/INSERT ⇒ 真库直接报 `column "tenant_id" does not exist`（实测 2 条 ERROR）；同时清理 `ClassificationRuleServiceImpl#create` 的 `setTenantId` 与 `createSeed` 的赋值，两个测试类的 `getTenantId/setTenantId` 断言改为 `enterprise_id`。**④迁移内「留证型查询」必须在 DDL 之前（实测踩坑）** —— V170 首版把「统计两列不一致行数」的 `DO $$` 块放在 `DROP COLUMN` **之后**，Flyway 直接失败 `column "tenant_id" does not exist`。⇒ DDL 的执行顺序对留证查询是**硬约束**（不是风格问题），已调整为先留证再删。**⑤两处 D-1 夹具必须重写，且诚实记录证伪能力下降** —— `matchMustNotCrossEnterpriseBoundary` 首版靠「两列故意不一致」证伪旧实现，列删后该形态在库内不再可能 ⇒ 夹具改为只写 `enterprise_id`，**证伪能力从「能证伪按 tenant_id 过滤」降为「只能证伪不过滤/按错企业号」**；该缺口由 D-1 的单测断言（SQL 片段含 `enterprise_id` 且不含 `tenant_id`）承担，且列已删、MP 若仍引用必抛 SQL 错 ⇒ 整体覆盖不降级。已在用例 javadoc 写明。**⑥三类反证全部可证伪** —— ①把 `tenantId` 字段加回 Entity ⇒ 2 条 ERROR（`column does not exist`）；②把列加回库 ⇒ 守卫转红；③把 `enterprise_id` 索引删掉 ⇒ 反向对照转红。⚠️ **反证 2 首版自身无效** —— 注入的 `ALTER TABLE` 被放在断言**之后**，守卫恒绿；改为「先加列再断言」后立即转红。**判据：反证脚本自己也可能造假，必须确认「注入点在断言之前」。****验证**：L1 `1663/0/0/5`（clean 口径 49.14/41.56/67.87，阈值 49/41/67，`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；L2 `2105/0/0/5`（+1 即 D-3 守卫；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests` 全绿；`check_tenant_fixture.py` / `check_entity_status_massassignment.py` exit 0；`check-entity-schema.mjs` ✅（该脚本已能双向校验，删列后 Entity-DB 仍一致）。**遗留**：`t_ai_feedback_log.tenant_id` 删列待决策、批次 1b（D-2a 文档）未启动。 |
| **V1.6** | 2026-10-07 | opencode | **批次 1a-3（D-1）实施回写：3 处发现 + 6 处断言 + 1 个跨批次缺陷**。**①主实施** —— `ClassificationRuleService` 与 `AiFeedbackLogService` 共 **6 个方法签名去 `tenantId`**，两个 Controller 删 4 处 `@RequestParam`，前端 `classificationRule.ts` + `ClassificationRuleList.vue` 同步改；读路径一律改按 `EnterpriseContextHolder.get()` → `enterprise_id`；`AT-106-9` 解除 `@Disabled` 并转绿。**②发现 §0.4 漏了第 7 处** —— `match()` 也按 `tenant_id` 过滤（同表同层，清单外），一并改；取证判据从「逐个方法列举」改为「grep 隔离列的读写点」。**③D-1 初版是 fail-open（已修）** —— `if (ctx != null) eq(...)` 在无上下文时**返回全表**，且注释自称「等价于读空集合（安全）」与代码相反；改为 `requireEnterpriseContext()` 缺失即抛，并补 6 条负向断言。**④断言不敏感 → 假绿（已修）** —— 端到端用例被 `EnterpriseDataPermissionInterceptor` 的 SQL 层注入掩盖，删掉应用层过滤仍全绿；补「显式初始化 MP `TableInfo` 缓存后断言 SQL 片段」的应用层断言，5 处独立反证逐条转红。**⑤查出跨批次缺陷 DIR-004** —— `PaginationInnerInterceptor` 排在企业隔离拦截器之前 ⇒ **分页 `total` 跨租户泄漏**（`t_subject` 实测 `total=43 / records=0`），未在本批次改全局配置，已登记待裁定（**同日按方案 A 闭环**，见 §0.4.1）。**⑥D-2b 两项已裁定** —— `t_sys_config` 与 `t_audit_log` 均**保持平台级**（不加列），与 `SHARED_TABLES` 现状一致。**验证**：L1 `1663/0/0/5`（clean 口径 49.14/41.55/67.88，阈值 49/41/67，`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；L2 `2102/0/0/5`（**DIR-004 闭环后复测 `2104/0/0/5`**；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests` 全绿；`check_tenant_fixture.py` 与 `check_entity_status_massassignment.py` exit 0；`check-entity-schema.mjs` ✅。**遗留**：D-3 删列、批次 1b（D-2a 文档）。**附：DIR-004 同日按方案 A 闭环**，L2 升至 `2104/0/0/5`。 |
| **V1.5** | 2026-10-06 | opencode | **批次 2 实施回写：切换鉴权真库回归锁，零生产缺陷**。**①落点偏离原计划（已改）** —— V1.4 计划把 AT-106-6/7 落在既有 `TenantIsolationHttpTest` / `TenantIsolationSecurityTest`「补断言」，实测**不可行**：前者把 `JwtProvider`/`StringRedisTemplate`/`UserDetailsServiceImpl` 全部 `@MockBean`，而回答「带合法凭证时上下文如何变」必须签发**真 JWT** ⇒ 引入真 JWT 就要拆掉整套 mock，等于重写该类；后者验的是「规则」不是「链路」。故新建 `TenantSwitchRealDBTest`（11 例）一次承载 AT-106-6/7/8 + 跨企业写拒绝 + 4 条守卫（新增编号 **AT-106-10 ~ AT-106-14**）。**②零生产缺陷** —— `JwtAuthenticationFilter` 的成员三源并集、上下文改写、审计留痕三条链路实测均正确；本批次价值在于这些行为此前**完全没有 HTTP 层回归保护**（`TenantIsolationSecurityTest` 6 例全绿却无一能证明过滤器真的调用了它 —— 「规则层绿 ≠ 链路层绿」，与 P104 覆盖率门禁假绿同族）。**③新增 §7.3 观测手段**：用 `/api/v1/enterprise/current-period` 作上下文探针（它直接以 `EnterpriseContextHolder.get()` 为入参查共享表 `t_enterprise`，故响应 `startPeriod` 严格等价于「上下文是谁」），两个测试企业各设不同 `start_period` 即可无歧义区分 —— **不为测试新增端点**。**④新增 §8.2 反证矩阵**：逐条注入缺陷实测转红，其中「删掉 `enterpriseId = requested`」一处打红 **4 条**（AT-106-6/8/10/11），是断言有效性的核心证据。**⑤一处前提假设被自己的守卫证伪** —— 原以为 `t_user.enterprise_id` 是「按设计属归属企业故不被 `insertFill` 覆盖」，实测去掉 `withoutEnterpriseContext` 包裹后断言**依然绿** ⇒ 真实机制是 `UserEntity` 不继承 `BaseEntity` 且全类无 `@TableField` ⇒ **`insertFill` 压根不被调用**（§0.5 同型）；结论虽同但理由全异，若按错误理由理解会误以为「补 fill 注解是安全的风格统一」，实测补上即转红（建账号指定企业失效）⇒ 新增 AT-106-12 拦这一手。**⑥诚实声明 RLS 维度未覆盖**：MockMvc 用连接池连接（L2 为超级用户），**无法在请求内降权** ⇒ 本批次隔离断言全部落在**第二层数据权限拦截器**（应用内 ThreadLocal 驱动，超级用户下仍真实生效）；第三层 RLS 谓词仍由 `TenantRlsRealDBTest` 承担。**两层各有锁，但「一次请求内同时验证两层」无人验证**，已登记为遗留缺口。**验证**：L1 `1647/0/0/5`（clean 口径覆盖率 31.77/12.82/57.17，与批次前持平 —— 新类为 `@SlowTest` 不进 L1，故棘轮阈值无需重抬）、L2 `2058/0/0/6`（+11 即本类），两次 `All coverage checks have been met`，`check_tenant_fixture.py` 与 `check_entity_status_massassignment.py` 均 exit 0。**遗留**：1a-3（D-1）、批次 1b（D-2）、批次 3 未启动；新增「请求内同时验证两层防线」缺口 |
| **V1.4** | 2026-10-06 | opencode | **实施回写（批次 1a 执行后）**：3 处诊断修正 + 1 处计划假设被推翻 + 3 项实施状态。**①「隔离泄漏」的定性被实测证伪** —— V1.3 写「代码写 `tenant_id`、RLS 读 `enterprise_id` ⇒ 产生『已隔离』的错觉」，暗示存在泄漏；实测 4 张表的 RLS 谓词虽只读 `enterprise_id`，但它与 `tenant_id` 过滤是**两个 AND 条件**，**跨企业读并不泄漏**（探针角色下实测返 0 行）⇒ 正确的定性是「RLS 隔离层有效，但两列数据不一致导致业务读错」，危害降级。**②新增 §0.5：真正的 P0 是 `enterprise_id` 静默落 DB 默认值** —— `PrepaymentEntity` 不继承 `BaseEntity` 且全类无 `@TableField` ⇒ `insertFill` 根本不触发 ⇒ 非企业 1 的上下文创建的预付款落进企业 1（**跨租户默认写入**）。影响面已全量扫描：8 个实体不继承 `BaseEntity`，其中 3 个的 `enterprise_id` 按设计是「归属企业」而非上下文，**只有 `PrepaymentEntity` 是缺陷**；治法经实测收窄为「补 `fill` 注解」，**不可**改用继承（会引入 3 处列类型不匹配）。**③新增 §0.4：读路径也按 `tenant_id` 过滤的 6 处**（`ClassificationRuleServiceImpl` 4 处 + `AiFeedbackLogServiceImpl` 2 处，且两个 Controller 都暴露成 `@RequestParam`）—— 定性为**功能缺陷（过滤失效）非安全漏洞**；因修法必然动**客户端契约**，按铁律 #10 挂起待决策，留 `@Disabled` 用例（`AT-106-9`）钉住。**④新增 §0.6：L2 Testcontainers 的连接角色是超级用户 ⇒ RLS 类断言恒绿** —— 探针实测 `rolsuper=t`/`bypassrls=t`，配置全对但谓词执行不到；正解是 `SET LOCAL ROLE` 到非超级探针（`TenantRlsRealDBTest` 是全库唯一做对的先例）⇒ **L2 全绿不代表 RLS 有效**。**⑤计划里的假设被推翻**：原写「把 `idx_classification_rule_tenant` 从 `tenant_id` 迁到 `enterprise_id`」，实测 **`enterprise_id` 索引早已存在**（V104:38 / V105:28 / V105:31 各有一个单列索引）⇒ 照做会创建重复索引；真实缺口是 `t_ai_feedback_log` **完全没有 `enterprise_id` 索引** + `t_classification_rule` 缺 `deleted` 复合维度，已由 **`V168`** 幂等补齐（旧索引只用 `COMMENT` 标注待清理，不删 —— 删属破坏性 DDL 且会抢先替 D-1 决策）。**⑥§1.2 补实施状态列、§7.1 补状态列、新增 §10 待决策项（D-1 `tenantId` 参数去留 / D-2 十张表分组 / D-3 旧列旧索引删否）**。**批次 1a 成果**：已修 3 类生产缺陷（预付款跨租户默认写入、核销日志两列不一致、核销容差按企业 1 判定属静默错账），清 6 个常量 / 8 个有效使用点 + 1 处幽灵字段赋值；L1 `1647/0/0/5`、L2 `2047/0/0/6`，两次 `All coverage checks have been met`。**遗留**：1a-3 挂起（D-1）、批次 1b 未启动（D-2）、批次 2/3 未启动 |
| **V1.3** | 2026-10-05 | opencode | **复审定点修正 3 处（纯文档）**。复审方式是**重新独立复核，不信任 V1.2 自己的编辑** —— 4 项 P0 中 3 项确认闭环（P0-1 三处证据交叉一致；P0-2 分类正确；P0-4 与 `:108-110`/`:120-122` 一致），**P0-3 未闭环**，且复审又抓出 2 处 V1.2 自身的新错误：**①计数错误且自相矛盾** —— V1.2 的 F6 标题写「实为 6 处」，但它自己的列举是 4 个租户/企业维度常量 + 6 个操作人维度常量 ⇒ 本版改为「**4 个常量 / 8 个有效使用点**」，并把操作人维度拆成独立的 **F6b**（§0.3 保留）。**②把两类不同性质的东西混为一谈** —— V1.2 的 §0.2 写「`ReconciliationServiceImpl:73` 写死 `1L`，**3 个写入点**（`:364`/`:780`）」，**数字与列举自相矛盾**，且把 `:892` 错记到 `t_reconciliation_log`。追到 Entity 层后真相是：`:364`/`:780` 写真实列 `t_reconciliation_log.tenant_id`（**真双列并存**），而 `:892` 写的是 `ReconciliationExceptionEntity.tenantId` —— 该字段 `@TableField(exist = false)` 且 `t_reconciliation_exception` **根本没有 `tenant_id` 列** ⇒ **幽灵字段死代码，赋值完全无效**（AGENTS §4.2 第 10 条）⇒ §0.2 该行改为「**2 个有效写入点**」，并新增 **§0.2 补注**单列该 Entity。**③顺带发现一个反向缺口并登记 P102**：复核该 Entity 的 **15 处** `exist = false`，其中 **`updatedAt`（`:104`）是反向缺口** —— `V127:34` 已给该表加真实列 `updated_at`，Entity 却标 `exist = false` ⇒ **该列永不写入、读回恒 null**（AGENTS §4.2 第 16 条，比幽灵字段更隐蔽，因为 `\d` 里一眼可见）。**处置**：登记为 P102 待办 + §9 风险表，**不并入本 SPEC 的 5 项遗留**（与 §0.2 的 DDL 收口不同源，混做会让变更无法归因）。**同步**：`§1.2` L1-3 补「两类清理动作不可混用」（真双列 ⇒ 改写 `enterprise_id`；幽灵字段 ⇒ **删代码而不是改列**，验收断言也不同）；`§4`/`§8` 计数同步；`§7` 拆出独立的幽灵字段场景并新增 `AT-106-2b`（含负向断言「不得改为 `setEnterpriseId`」）。**方法论沉淀**：**审核 SPEC 不能只核对 SPEC 自己列的证据，必须把每个断言追到 Entity 字段映射层** —— V1.2 的 P0-3「改过仍错」是因为只在常量定义与调用点两层打转，没看 `@TableField`；而「幽灵字段 vs 真实双列」的区分**只有在 Entity 层才存在** |
| **V1.2** | 2026-10-05 | opencode | **审核意见落地（4 项 P0 + 4 项 P1 + 2 项 P2，仍未动代码）**。审核方法是**逐条实测复核 F1~F7 并新增 F8**，不是读 SPEC 找问题。**P0-1 状态机含非法值**：V1.1 §5 写 `ACTIVE ──▶ CLOSED`，而 `chk_enterprise_status`（`V100:51`）与 `EnterpriseStatus` 枚举都只有 `PENDING/ACTIVE/SUSPENDED/TERMINATED`，**`CLOSED` 不是合法值** —— 已在 §5 重画为真实四态与合法迁移（`PENDING→ACTIVE`、`ACTIVE→SUSPENDED`、`SUSPENDED→{ACTIVE,TERMINATED}`），并新增 **F8** 固化该证据。**P0-2 §0.1 事实错误**：V1.1 称那 10 张表「仍用 `tenant_id`」，实测**既无 `enterprise_id` 也无 `tenant_id`**（`rg "ADD COLUMN.*tenant_id"` 全 migration 零命中）；更严重的是 V1.1 **漏掉了真正的缺口** —— **4 张双列并存表**（`t_prepayment`/`t_reconciliation_log`/`t_ai_feedback_log`/`t_classification_rule`），代码写 `tenant_id` 而 RLS 读 `enterprise_id`，**产生「已隔离」的错觉**（新增 §0.2）。**P0-3 硬编码漏报**：V1.1 F6 写「2 处」，本版补入 `ReconciliationServiceImpl:73`、`PrepaymentServiceImpl:55`、`ReconciliationToleranceServiceImpl` 的多个使用点；另将 `DEFAULT_USER_ID=1L` 单列 §0.3 并**排除出本轮范围**（属审计操作人维度，需产品决策），避免与租户维度混计。⚠️ **本版的计数与 §0.2 的「3 个写入点」表述经 V1.3 复审判定错误**（混计了幽灵字段），**以 V1.3 的「4 个常量 / 8 个有效使用点」为准**；**P0-4 场景与代码相反**：V1.1 §7 写「SUPER_ADMIN 不设上下文」，而 `JwtAuthenticationFilter:120-122` **无条件** `EnterpriseContextHolder.set(...)` —— 已改为「切换会设置上下文 + 不带头时按 JWT」，并核实现有 `#superAdminAllowedEverywhere:78-82` 只测 `isMember` 不涉上下文，**无断言冲突**。**P1-5 与已落地设计冲突**：V1.1 §6 要求「上下文取不到即抛异常」，与 `TenantRlsInitializer:65-69` 的既定 null-return 设计相反（其注释明写定时任务「本就不属于任何企业」）—— 已在 §1.2/§6/§9 三处划边界：**只清「有上下文却硬编码 1」，不改切面语义**。**P1-6 删掉逃逸口场景**：「审计日志按企业隔离（或改为断言文档）」这类二选一不可判定，已删除；`t_audit_log` 两列都没有已是确定结论，改为 §0.1 的取证项。**P1-7 补 AT-106-1~8 验收编号**并标注类型与落点。**P1-8 修正 test_ref**：V1.1 的三个类名**全不存在**，改为「已存在 5 个 + 拟建 1 个」；批次 2 描述由「当前只有单测」改为「4 个类已覆盖读隔离，补跨企业写拒绝」。**P2**：F1 表述限定为 `backend/src/main`（并补上被 V1.1 漏掉的 `t_sys_config` 账套级配置证据）；§9 补「无机器可读契约」与覆盖率缓冲不足两项风险。**遗留**：§0.1 的 10 张表**定性仍待定**（需逐表取证，我不替业务方判定「刻意全局共享 vs 应隔离」）；§0.2 的 4 张表需先跑一致性核查再动 DDL；**另新增登记**：`t_reconciliation_exception` 的 15 处 `exist = false`（含 `updatedAt` 反向缺口）作为 P102 待办 |
| **V1.1** | 2026-10-05 | opencode | **关联需求编号更正：`REQ-2026-134` → `REQ-2026-133`**（纯文档，未动代码）。V1.0 把本项登记为 134，而该编号**已被 P107 存量缺陷修复包占用且已实施完成**（登记册 `REQ-2026-134` 行 = `SPC-P107`，2026-09-30 D1~D6+D8 全落地）—— 属 **P105 已修过的「REQ 重号」缺陷二次复发**。正确编号 `REQ-2026-133` 有三处交叉依据：①P101 line 28「已新增 P107（REQ-2026-134）承接，并**为它让出 REQ 编号**（原 P106 的 133 保持不变）」；②P101 子 SPEC 表「P106 | REQ-2026-133」；③P107 SPEC 头部「133 归 P106 内控深度」。**V1.0 自身即自相矛盾**：line 5（来源）写的是 133、line 6（关联需求）写的是 134。**同时补范围声明**：本 SPEC 只覆盖 P106 的「多账套」子项，年结/制单≠审核/数据权限粒度/部门级扩展仍未立项 |
| **V1.0** | 2026-10-05 | opencode | 首版。基于实测把「多账套」拆为 L1（企业级，**已基本落地**，本轮只收口 4 项遗留）/ L2（账簿级，**完全缺失**，仅立项）；给出「复用 `enterprise_id` vs 新增 `book_id`」两模型对比与推荐；补齐竞品对标（用友/金蝶/SAP/QuickBooks）与年度层差距；定稿 BDD 与三分批实施计划。**未改动任何代码。** |