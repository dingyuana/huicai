# SPEC-P114 — 代理端跨客户语义表的 RLS 误分类修复

> **状态**：✅ **已审核通过并实施完成**（2026-10-10，V1.0）
> **关联需求**：新登记 REQ-2026-135（线上 500 修复）
> **日期**：2026-10-10
> **触发**：用户报 `POST http://localhost:3001/api/v1/agency/assignments 500 (Internal Server Error)`

---

## §0 取证：缺陷真实存在，且比报障现象更严重

### §0.1 现象与日志

容器 `huicai-backend` 日志（非推测，直接取自 `docker logs`）：

```
ERROR c.h.c.exception.GlobalExceptionHandler : 系统异常:
### Error updating database. Cause: org.postgresql.util.PSQLException:
    ERROR: new row violates row-level security policy
           for table "t_agency_user_enterprise"
### The error may involve
    com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper.insert-Inline
```

⚠️ 该异常被 `GlobalExceptionHandler` 归类为「系统异常」⇒ 返回 **500** 而非 4xx。
这本身是个可观测性缺陷：业务上应回 4xx + 明确文案，现状把「配置错误」伪装成「服务故障」。

### §0.2 行为探针（非超级用户 `huicai_app` = `NOSUPERUSER NOBYPASSRLS`）

| # | 探针 | 结果 | 结论 |
|---|---|---|---|
| 1 | 无 GUC 时 `INSERT`（企业4） | `ERROR: new row violates row-level security policy` | ← **线上 500 的直接成因** |
| 2 | 同事务 `SET LOCAL app.enterprise_id=4` 后 `INSERT` | ✅ 成功 | **谓词本身正确，缺的只是 GUC** |
| 3 | GUC=1 时查「会计2 的派工」 | 只返 `enterprise_id=1`；超管对照返 `1,2,3` | 🔴 **读路径静默丢行** |

🔴 **探针 3 比 500 更危险**：代理管理员在派工页**看不到自己已派给其它客户的记录**，
前端「已分配」列表恒缺项 ⇒ 会重复派工、或误判客户未分配。全程无任何报错。

### §0.3 根因：RLS 与业务语义矛盾（分类错误，非配置遗漏）

`t_agency_user_enterprise` 的 `enterprise_id` 语义是「**被服务的客户**」，
不是「本行归属哪个租户」：

| 证据 | 内容 |
|---|---|
| 建表注释（V112） | 「会计-**客户**分配关系（派工记录）」 |
| 表结构 | `agency_user_id`（代理会计）→ `enterprise_id`（客户企业） |
| 业务职责 | 代理管理员的**本职就是跨客户派工**，必须能派企业 2/3/4 |

而 RLS 谓词 `enterprise_id = current_setting('app.enterprise_id')` 强制
「只能碰 `enterprise_id` 等于当前 GUC 的那一个企业」⇒ **与业务定义直接矛盾**。

**`V156` 的分类前提对本表不成立**。该迁移注释断言这 4 张表「均属漏网而非有意排除」，
但同为代理端的 `t_agency_enterprise` 恰恰是**因为「代理须跨客户读」才不开 RLS**（见 V106 排除清单）。
本表与它是同一语义，却被一并开了 RLS ⇒ **误分类**，不是遗漏。

### §0.4 应用层与 DB 层给出相反裁定（这才是可复制的教训）

`t_agency_user_enterprise` 与 `t_service_progress` **同时**出现在：

- `EnterpriseDataPermissionInterceptor.SHARED_TABLES`（应用层判为**语义豁免**，不注入 `enterprise_id`）
- `V156` 的 RLS 列表（DB 层判为**普通租户表**，按 `enterprise_id` 过滤）

⇒ 两层对同一张表裁定相反，**DB 层赢**，且赢的那一方语义不对。

> **判据沉淀**：白名单与 RLS 是同一张表的**两个隔离开关**，
> 二者不一致时**必然有一方是错的**。新增任一项必须同步确认另一项的状态。

---

## §0.5 根因二（连带查出，独立于 RLS，且**阻断修复本身**）

实施 §4 的归属校验时，两个既有用例报「代理用户不存在」。取证发现**夹具没错，是生产代码错了**：

`LoginUser` 的 **2 参构造器**（`UserDetailsServiceImpl:89` 是生产环境**唯一**调用点）
把 `agencyId` **硬编码为 `null`**：

```java
this(userEntity, authorities, userEntity.getEnterpriseId(), null, ...)   // ← 硬编码
```

而 `t_user.agency_id` **是真实存在且有值的列**（实测 6 行全部非空），`UserEntity` 也有对应字段
⇒ 这里本可以取到。⇒ **`SecurityUtils.getCurrentAgencyId()` 恒返回 null**。

**波及 8 处调用点**（按 agency 维度的隔离/过滤静默失效）：
`AgencySummaryService:29`、`ServiceProgressController:53/60/83`、
`EnterpriseController:60`、`InputInvoiceImportService:340`、`TaxServiceImpl:1012`、
`VoucherServiceImpl:412`、`PeriodCloseServiceImpl:354`，以及本 SPEC 新增的归属校验。

🔴 **为什么这个缺陷能长期存活**（本条最值得沉淀）：
JWT 里**确实带** `agencyId` claim（`JwtProvider.generateAccessToken` 有写），
`JwtAuthenticationFilter` 也**确实读了**（`getAgencyIdFromToken`）——
一切「看起来都在」，只是读出来之后**从未交给 `LoginUser`**。
与 AGENTS §4.5 第 27 条「有注解不等于会生效」同型，此处是
**数据取到了，但没有接线到使用点**。

⇒ **判据：凡是「读到了 X 却没传给消费方」的代码，都要用守卫钉住**，
否则一次「顺手简化构造器」就会静默恢复，且**线上无任何报错**。

**修法**：2 参构造器改从 `UserEntity.getAgencyId()` 取。
非代理用户（`agency_id` 为 NULL）仍为 null ⇒ 对 ENTERPRISE 类用户**零回归**。
新增 `LoginUserAgencyIdTest` 4 例锁死。

⚠️ **连带的设计决定**：修完后 `agencyId` 真的可能为 null（如 ENTERPRISE 用户访问代理端点）。
此时 `requireSameAgency` 采取 **fail-loud**（抛 500 并指明缺失字段）而**非**按「无权」处理 ——
否则会把「上下文构造有问题」伪装成「权限不足」，且**所有代理管理员被锁在门外**却无人知道根因（§4.3 第 10 条静默失败形态）。

---

## §1 范围

**做**：撤 `t_agency_user_enterprise` / `t_service_progress` 两表的 RLS，并**同批**补路径级校验。

**不做**：全量重审 V156 的另外两张（`t_contract` / `t_close_log`）。
⚠️ 二者**未**在 `SHARED_TABLES` 中，与本例不同型；且当前无线上症状。
按 §7.7「不得顺手扩大 DDL」，本 SPEC 只覆盖已取证的两张，另两张登记为待核项。

**`t_service_progress` 为何纳入**（与本表同批被 V156 开 RLS、同在白名单、同为代理端跨客户语义）：

| 证据 | 内容 |
|---|---|
| 查询收敛维度 | 全部以 `agency_id` 收敛（`ServiceProgressServiceImpl:121/168/223/250`） |
| `enterprise_id` 用法 | 仅作**可选收窄**条件（`:175` 可空），即天然跨客户 |
| 当前症状 | 0 行，尚无线上表现 —— 属**同型同类债**，一并清避免留债 |

---

## §2 方案选型

| 方案 | 评估 |
|---|---|
| **撤 RLS + 应用层收敛（选中）** | ✅ 与既有 `t_agency_enterprise` 处置一致；应用层已有 `agency_id` 过滤与角色校验可承接 |
| 保留 RLS + 谓词改 `agency_id` 维度 | ❌ 需新增 `app.agency_id` GUC 及其设置切面，改动面大；且 agency 维度过滤应用层**已实现**，再加同义谓词属重复 |
| 写路径临时 `SET LOCAL` 成目标企业 | ❌ 把 GUC 语义从「当前上下文」偷换成「目标企业」，后续极易误用；且**读路径静默丢行仍未解决** |

---

## §3 输入契约 / 输出契约 / 异常处理

### 输入契约
- `V176`：`DROP POLICY IF EXISTS` + `DISABLE ROW LEVEL SECURITY`（两张表），并更新表注释写明语义。
- `AssignmentController` 三个端点补 `requireDispatcher()`：`AGENCY_ADMIN` 或 `SUPER_ADMIN`。
  ⚠️ **读端点也必须校验** —— 此前三个端点**均无任何鉴权**，而 `SecurityConfig`
  对它们只要求 `anyRequest().authenticated()`。

### 输出契约
- 非代理管理员访问三端点 ⇒ `BusinessException.forbidden`（铁律 #14）。
- ⚠️ 本项目约定：业务异常写进 body 的 `code` 字段，**HTTP 状态码仍为 200**
  （同 `AssignmentControllerTest#testUnassignNotFound` 既有口径）。
  故守卫断言 `$.code` 而非 HTTP 403 —— 否则会把「项目约定」误判成「守卫失效」。

### 异常处理
- 服务层 `requireSameAgency()`：目标代理用户不存在 ⇒ `notFound`；不属于当前代理公司 ⇒ `forbidden`。
  `SUPER_ADMIN` 豁免（与 `assign()` 既有口径一致，否则超管无法运维）。

---

## §4 护栏：撤 RLS ≠ 放行（§4.5 第 43 条）

撤掉 DB 层兜底后，隔离责任完全落到应用层。若不同批补校验，
**等于用一个洞换另一个洞**（此前该读端点本就无鉴权）。故以下三者**同批生效，缺一即视为开洞**：

| # | 护栏 | 位置 |
|---|---|---|
| ① | 端点角色鉴权（角色：能不能进这个功能） | `AssignmentController#requireDispatcher` |
| ② | 服务层 `agency_id` 归属校验（归属：能看哪一家） | `AgencyUserEnterpriseServiceImpl#requireSameAgency` |
| ③ | 真库守卫 + 反向自证 | `AgencyCrossClientRlsRealDBTest` |

> 角色与归属是**两道正交**校验：角色管「谁能用派工功能」，
> 归属管「能看哪一家」。缺任一道都会漏 —— 只做①则代理 A 的管理员能看代理 B 的名单。

---

## §5 验收标准（BDD / Given-When-Then）

| 编号 | 场景 | 对应测试 |
|---|---|---|
| AT-135-1 | Given 两表免 RLS，When 以探针角色换不同 GUC 读同一张表，Then 两次行数**相等** | `assignmentTableIsRlsFree` / `serviceProgressTableIsRlsFree` |
| AT-135-2 | Given 会话 GUC=1，When 写入 `enterprise_id`≠1 的派工行，Then **成功**（线上 500 的回归锁） | `crossEnterpriseInsertSucceeds` |
| AT-135-3 | Given V176 已执行，When 查 `pg_class`/`pg_policies`，Then 两表均 `relrowsecurity=f` 且无 `enterprise_policy` | `bothTablesHaveRlsDisabledAndNoPolicy` |
| AT-135-4 | Given 登录用户为 `ACCOUNTANT`，When 访问三端点，Then `$.code=403` **且服务层方法零调用** | `AssignmentControllerTest` 三条负向用例 |
| AT-135-5 | Given 对真租户表施加同形状策略，When 用同一探针读，Then 行数**变化**（证明守卫非恒绿） | `probeCanDetectRlsWhenItExists` |
| AT-135-6 | Given `UserEntity.agencyId=7`，When 走 2 参构造器，Then `LoginUser.getAgencyId()==7` | `LoginUserAgencyIdTest` |
| AT-135-7 | Given 登录上下文 `agencyId` 为 null，When 校验归属，Then **fail-loud**（报错含 `agencyId`）且**未查库** | `missingAgencyIdInContextFailsLoud` |
| AT-135-8 | Given 操作者与目标属不同 agency，When 查派工，Then 403 且**未触达数据查询** | `crossAgencyLookupRejected` |
| AT-135-11 | Given `t_contract` 换 GUC 读，Then 可见行数不变（V177：代理跨客户合同语义） | `contractTableIsRlsFree` |
| AT-135-12 | Given 非代理管理员读三处合同读端点，Then 403 **且服务层零调用** | `ClientControllerAuthTest` 三条 |
| AT-135-13 | Given 同代理名下两个 enterprise，When 查续费提醒，Then 两条都返回 | `getRenewalReminders` 按 `agency_id` 收敛 |
| AT-135-14 | Given 为别家代理企业建合同，Then 403（`create` 防跨代理写入） | `ContractServiceImpl#create` |

⚠️ **AT-135-12 只覆盖 3 个读端点**：两个写端点被上游 `EnterpriseWriteGuard` 抢先拦，
无法证伪角色守卫，已在测试 javadoc 与 §7 遗留 1 如实声明。

🔴 **负向断言的必要性**：AT-135-4 除断言 403 外，还 `verify(never())` 服务层未被调用。
只断言 403 不够 —— 若实现是「先查后判」，数据已被读出，那就是**假拒绝**（§4.5 第 43 条）。

---

## §6 反向自证（实施后实测，均已转红）

| 反证 | 手法 | 结果 |
|---|---|---|
| 把 `V176` 对 `t_agency_user_enterprise` 的三条 DDL 注释掉 | 恢复 V156 状态 | ✅ **3/5 转红**：结构断言 `expected:<0> but was:<1>`、行为断言 `换 GUC 后可见行数变了（3 vs 0）`、写路径复现**线上同一个错**（`bad SQL grammar`） |
| 注释掉 `requireDispatcher()` 三处调用 | 去掉角色鉴权 | ✅ **3 条负向用例转红**：`$.code expected:<403> but was:<200>` |
| 把 `LoginUser` 2 参构造器改回硬编码 `null` | 还原根因二 | ✅ `LoginUserAgencyIdTest` 转红（agencyId 丢失） |

⚠️ 反证①须**同时**去掉 `DROP POLICY` / `NO FORCE` / `DISABLE` 三条 ——
只去掉 `DROP POLICY` 保留 `DISABLE`，RLS 仍不生效，守卫照样绿 ⇒ **无效反证**。

### 回归结果

| 套件 | 结果 |
|---|---|
| L1（`mvn clean test`） | **1698 通过 / 0 Failures / 0 Errors / 5 Skipped**，`All coverage checks have been met`（`Skipping JaCoCo execution` 计数 0 ⇒ 门禁真执行） |
| 覆盖率（clean 口径） | INSTR **49.46%** / BRANCH **42.04%** / METHOD **68.11%** —— 三项**全部上升**（此前 49.14/41.59/67.83）；对阈值 49/41/67 的余量 0.46 / 1.04 / 1.11 |
| L2 真库（`-DexcludedGroups=`） | **2180 通过 / 0 / 0 / 5**，零回归 |
| 登记册漂移守卫 / `check-entity-schema.mjs` | 均 exit 0 |

⚠️ `LoginUser` 是**共享鉴权类**，故 L1/L2 均做**全量**回归（非定向）。
L2 跑前已 `docker start huicai-redis`（否则 11 例报 `RedisConnectionFailure`，属环境型红，§4.5 第 24 条）。

---

## §6.5 机器可读契约

> ⚠️ **标记必须是裸 `# MACHINE-READABLE CONTRACT`**（写在 ```yaml 围栏**内**，
> 即校验器的 Case A）—— 写成 `## §6.5 MACHINE-READABLE CONTRACT` 会被判「无契约」。
> 这正是 AGENTS 记录的「有契约 ≠ 门禁看得见契约」（§0 硬数字第 ⑯ 条）：
> 首次写下时用了 `## ` 前缀，门禁实测仍报 `no_contract 88`，改回裸标记才被识别。

```yaml
# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: AgencyUserEnterpriseEntity
module: agency
table: t_agency_user_enterprise

# 本 SPEC 不涉及业务状态机（派工记录无 status 语义），契约以「规则型」表达：
# 校验器支持无 states/transitions 但有 rules 的契约（同 P112）。
rules:
  - id: C-01
    source: "P114 §0.2 探针1（线上 500 成因）"
    rule: "t_agency_user_enterprise 的 enterprise_id 语义为「被服务的客户」，非「本行归属租户」；跨客户派工不得被 enterprise_id = app.enterprise_id 谓词过滤"
    mapping: { table: t_agency_user_enterprise, rls: false, semantic: cross_client }
    implementation: "AgencyUserEnterpriseEntity"
  - id: C-02
    source: "P114 §2 方案选型 / V176"
    rule: "V176 撤 RLS：DROP POLICY IF EXISTS enterprise_policy + NO FORCE + DISABLE ROW LEVEL SECURITY，与 SHARED_TABLES 口径对齐"
    mapping: { table: t_agency_user_enterprise, drop_policy: true, disable_rls: true, no_force: true }
    implementation: "AgencyUserEnterpriseEntity"
  - id: C-03
    source: "P114 §1 为何连带 t_service_progress"
    rule: "t_service_progress 同批撤 RLS：查询全部以 agency_id 收敛，enterprise_id 仅作可选收窄（ServiceProgressServiceImpl:175 可空），天然跨客户"
    mapping: { table: t_service_progress, drop_policy: true, disable_rls: true, convergence: agency_id }
    implementation: "ServiceProgressEntity"
  - id: C-07
    source: "V177（遗留 1 闭环）/ t_contract 三条佐证"
    rule: "t_contract 同时存在 agency_id 与 enterprise_id（均 NOT NULL），enterprise_id 语义为签约客户；续费提醒查询按设计无 enterprise_id 条件 ⇒ 撤 RLS 并改按 agency_id 收敛"
    mapping: { table: t_contract, drop_policy: true, disable_rls: true, convergence: agency_id }
    implementation: "ContractEntity"
  - id: C-08
    source: "P114 §4 护栏 / ClientController 五端点"
    rule: "ClientController 五个端点（含只读 GET）均须校验 AGENCY_ADMIN 或 SUPER_ADMIN；此前完全无鉴权，SecurityConfig 仅 anyRequest().authenticated()"
    mapping: { endpoint: "/api/v1/agency/contracts", roles: [AGENCY_ADMIN, SUPER_ADMIN] }
    implementation: "ContractEntity"
  - id: C-09
    source: "P114 §4 护栏 / ContractServiceImpl 写路径"
    rule: "create 须校验 dto.agencyId 等于当前操作者 agencyId，否则 forbidden（防跨代理为客户建合同）"
    mapping: { method: create, scope: agency_id, forbid_cross_agency: true }
    implementation: "ContractEntity"
  - id: C-10
    source: "V177 覆盖度诚实声明（反证逼出）"
    rule: "ClientControllerAuthTest 中两个写端点负向用例被上游 EnterpriseWriteGuard 抢先拦截，不构成本守卫的证伪证据；可证伪的仅三个 GET"
    mapping: { falsifiable_endpoints: 3, blocked_by: EnterpriseWriteGuard }
    implementation: "ContractEntity"
  - id: C-04
    source: "P114 §4 护栏①（端点角色鉴权）"
    rule: "AssignmentController 三个端点（含只读的 GET）均须校验 AGENCY_ADMIN 或 SUPER_ADMIN；此前读端点完全无鉴权"
    mapping: { endpoint: "/api/v1/agency/assignments", roles: [AGENCY_ADMIN, SUPER_ADMIN], reject_before_service: true }
    implementation: "AgencyUserEnterpriseEntity"
  - id: C-05
    source: "P114 §4 护栏②（服务层归属校验）"
    rule: "listByAgencyUserId 须按 agency_id 校验目标归属；SUPER_ADMIN 豁免；agencyId 缺失时 fail-loud 抛 500 并指明字段名，不得按「无权」处理"
    mapping: { method: requireSameAgency, scope: agency_id, fail_loud: true, exempt: [SUPER_ADMIN] }
    implementation: "AgencyUserEnterpriseEntity"
  - id: C-06
    source: "P114 §0.5 根因二（LoginUser 字段丢失）"
    rule: "LoginUser 2 参构造器（生产唯一调用点 UserDetailsServiceImpl:89）必须从 UserEntity 取 agencyId，不得硬编码 null；否则 getCurrentAgencyId() 恒 null，波及 8 处调用点"
    mapping: { constructor: "LoginUser(UserEntity, List)", field: agencyId, from: UserEntity, hardcoded_null: false }
    implementation: "AgencyUserEnterpriseEntity"

acceptance_tests:
  - id: AT-135-1
    description: "t_agency_user_enterprise 换 GUC 不改变可见行数（免 RLS）"
    method: assignmentTableIsRlsFree
    assertion: "probeRowCounts(1L, 999999L) 两值相等"
    status: covered
  - id: AT-135-2
    description: "t_service_progress 换 GUC 不改变可见行数（免 RLS）"
    method: serviceProgressTableIsRlsFree
    assertion: "probeRowCounts(1L, 999999L) 两值相等"
    status: covered
  - id: AT-135-3
    description: "写路径：GUC 与被派企业不同的会话仍能写入（线上 500 的回归锁）"
    method: crossEnterpriseInsertSucceeds
    assertion: "探针角色 SET LOCAL app.enterprise_id=1 后 INSERT enterprise_id≠1 不抛异常"
    status: covered
  - id: AT-135-4
    description: "结构：两表均已 DISABLE RLS 且无 enterprise_policy 残留"
    method: bothTablesHaveRlsDisabledAndNoPolicy
    assertion: "pg_class 的 relrowsecurity/relforcerowsecurity 计数 == 0 且 pg_policies 计数 == 0"
    status: covered
  - id: AT-135-5
    description: "反证：对真租户表施加同形状策略后，本类探针必须能观测到行数变化"
    method: probeCanDetectRlsWhenItExists
    assertion: "施加策略后 probeRowCounts(t_subject) 两值不相等"
    status: covered
  - id: AT-135-6
    description: "非代理管理员读派工列表应被拒，且服务层一次都不被调用"
    method: listByAgencyUserIdRejectsNonDispatcher
    assertion: "$.code == 403 且 verify(never()).listByAgencyUserId"
    status: covered
  - id: AT-135-7
    description: "非代理管理员派工/取消派工应被拒（负向）"
    method: assignRejectsNonDispatcher
    assertion: "$.code == 403 且 verify(never()).assign"
    status: covered
  - id: AT-135-8
    description: "查看别家代理公司的派工记录应 403，且不得触达查询"
    method: crossAgencyLookupRejected
    assertion: "ex.getCode()==403 且 verify(never()).getEnterpriseIdsByAgencyUserId"
    status: covered
  - id: AT-135-9
    description: "登录上下文缺 agencyId 时必须 fail-loud，不得静默拒绝"
    method: missingAgencyIdInContextFailsLoud
    assertion: "异常消息含 agencyId 且未查库"
    status: covered
  - id: AT-135-10
    description: "LoginUser 2 参构造器必须带上 t_user.agency_id"
    method: twoArgConstructorCarriesAgencyId
    assertion: "new LoginUser(userWith(7L), List.of()).getAgencyId() == 7"
    status: covered
  - id: AT-135-11
    description: "t_contract 换 GUC 不改变可见行数（V177 免 RLS）"
    method: contractTableIsRlsFree
    assertion: "probeRowCounts(t_contract, 1L, 999999L) 两值相等"
    status: covered
  - id: AT-135-12
    description: "非代理管理员读三个合同读端点应被拒，且服务层零调用"
    method: nonDispatcherCannotPage
    assertion: "$.code == 403 且 verify(never()).page"
    status: covered
  - id: AT-135-13
    description: "同代理名下两个 enterprise 的续费提醒都应返回（V177 核心场景）"
    method: getRenewalReminders
    assertion: "按 agency_id 收敛后两条都返回（原先 RLS 只返回 GUC 那一个企业）"
    status: covered
  - id: AT-135-14
    description: "为别家代理的企业创建合同应 403"
    method: requireSameAgency
    assertion: "create 校验 dto.agencyId == 当前 agencyId，否则 forbidden"
    status: covered

out_of_scope:
  - "t_user 3 行 enterprise_id=NULL 数据修复与策略补齐（老丁裁定单独立项）"
  - "t_agency_enterprise 策略补齐（代理须跨客户读）"
  - "9 张无 enterprise_id 隔离列的平台级表（维持 D-2b 裁定不加列）"
  - "t_close_log：enterprise_id 为「本行归属租户」，RLS 判定为**正确**，V156 审核后保留不动"
  - "把 BadSqlGrammarException 归类为 4xx / 运维告警"
  - "ServiceProgressController 的端点级角色校验"

dependencies:
  - spec: P108
    relation: "V167 RLS 谓词空串硬化；本 SPEC 反证①证明撤销 RLS 时三条 DDL 必须同时去掉"
  - spec: P112
    relation: "同为 RLS 分类治理；P112 补第三层兜底，P114 撤销错分类的第三层"
  - spec: P113
    relation: "EnterpriseWriteGuard 拦截 t_contract 的写端点，使 ClientControllerAuthTest 的两个写负向用例无法证伪本 SPEC 的角色守卫"

---

## §7 已知遗留

### ✅ 遗留 1 已闭环（V177 / 2026-10-10 同日审核并处置）

**V156 审核结论**（探针实测，非推演）：

| 表 | `enterprise_id` 语义 | RLS 现状 | 判定 |
|---|---|---|---|
| `t_close_log` | **本行归属租户**（期末结账日志） | ENABLE+FORCE | ✅ 正确，**保留** |
| `t_contract` | **签约客户**（与 `agency_id` 并存） | ENABLE+FORCE | ❌ 同型误分类，**V177 已撤** |

**`t_contract` 的三条独立佐证**：
1. 表**同时存在 `agency_id` 与 `enterprise_id`**（均 NOT NULL）—— 纯租户表不会有 `agency_id`
2. `ContractMapper.findRenewalReminders()` 查询**无任何 `enterprise_id` 条件**，按设计就是「扫本代理全部客户」
3. V156 未逐表定性就一刀切开，与 `t_agency_user_enterprise` 同款误判

**行为探针实证**（`huicai_app` = NOSUPERUSER + NOBYPASSRLS）：造同代理（`agency_id=1`）名下企业 1/2/4 的三条合同 → 超管对照 **3 条**，应用以 `GUC=1` 跑同一条查询只返 **1 条**（仅 `enterprise_id=1）。
⇒ 代理管理员只能看到自己名下企业的合同，**另外两个客户的续费提醒静默消失**。

⚠️ **探针自纠**：审核 `t_close_log` 时，首版探针在同一事务里 `INSERT` 后 `ROLLBACK` 再查询，得出「0 行」。该结果**无意义**（是探针写错，不是缺陷）。改用 `COMMIT` 造数后重测，得 `GUC=1 → 只看到 enterprise_id=1` 一行，才是有效证据。
**判据：探针返回「异常干净」的结果时，先怀疑探针本身。**

**V177 与 V176 的关键差异**：`ClientController` 五个端点（含只读 `GET /page`、`GET /renewal-reminders`）此前**完全无鉴权**，`SecurityConfig` 仅 `anyRequest().authenticated()`，服务层也无 agency 归属校验。撤掉 RLS 后**任何登录用户都能遍历全部客户的合同金额与到期日**（跨代理泄漏，比丢行严重）。故护栏同批：
① ClientController 五端点补 `AGENCY_ADMIN`/`SUPER_ADMIN` 角色校验
② `ContractServiceImpl` 按 `agency_id` 收敛 `create`/`getById`/`renew`/`page`/`getRenewalReminders`
③ 守卫扩到三张表 + `ClientControllerAuthTest` 8 例

🔴 **覆盖度诚实声明（反证逼出来的）**：`ClientControllerAuthTest` 的 8 例里，**只有 3 个 GET 的负向用例能证伪角色守卫**。两个写端点被更上游的 `EnterpriseWriteGuard`（P113）抢先拦截（它用 `JdbcTemplate` 真查 `t_enterprise.status` 并 fail-closed），恒返 403 且**到不了** `requireContractAccess()`。
实测反证：注释掉 `create`/`renew` 两处守卫，那两条用例**仍然全绿** ⇒ 对本守卫是无效反证。
已在用例 javadoc 如实写明「拦截者是上游账套守卫，非角色守卫」，并只以 `verify(never())` 证明「未到服务层」。
**判据：一个端点上多个守卫时，只看响应码无法区分是谁拒的；反证必须逐个禁用守卫看是否转红。**

---

### 遗留 2：异常分类
RLS 配置错误当前返回 500 而非 4xx，可观测性不足。
建议后续把 `BadSqlGrammarException` 归类为可诊断的 4xx/运维告警，本 SPEC 不含。

### 遗留 3：ServiceProgressController 端点角色校验
本次未补端点级角色校验（其查询已按 `agency_id` 收敛），但与 `AssignmentController`/`ClientController` 同属代理端，建议后续统一审计。

### 遗留 4：根因二的连带效应需复核
修好 `LoginUser` 后，那 8 处调用点**第一次真正拿到 `agencyId`** —— 此前它们拿到的是 null。
本次 L1/L2 均零回归，但**「从 null 变成真实值」属行为变更**，建议在真实环境走一遍
代理端主流程（企业切换 / 期末结账 / 税务 / 制证）确认。

---

## §8 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.2 | 2026-10-10 | **§7 遗留 1 闭环（V177）**：V156 审核逐表定性 —— `t_close_log` RLS **正确保留**（`enterprise_id`=归属租户）；`t_contract` **同型误分类已撤**（三条佐证 + 探针实测 3 条只返 1 条）。新增 `ClientControllerAuthTest` 8 例 + `ContractServiceImpl` 按 `agency_id` 收敛 5 处。🔴 反证逼出覆盖度降级：两个写端点被上游 `EnterpriseWriteGuard` 抢先拦，角色守卫不可证伪，已在测试与 SPEC 如实声明。AT-135-11~14、规则 C-07~C-10 同步补入契约 |
| V1.1 | 2026-10-10 | 补 §0.5 根因二（`LoginUser` 硬编码 `agencyId=null`，涉 8 处调用点）、AT-135-6~8、三条反证与 L1/L2 回归数据；`requireSameAgency` 改 fail-loud |
| V1.0 | 2026-10-10 | 初版：取证、方案选型、护栏、验收、反证。实施 `V176` + Controller/Service 校验 + 守卫 5 例 |