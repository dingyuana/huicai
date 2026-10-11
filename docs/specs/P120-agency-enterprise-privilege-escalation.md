# SPEC-P120：代理端企业管理越权修复（会计角色权限收口）

| 项 | 内容 |
|---|---|
| **状态** | ✅ 已实施（V1.0） |
| **关联 REQ** | REQ-2026-143 |
| **前置** | SPEC-P114（撤 RLS + 端点鉴权）、SPEC-P116（代理端角色配对） |
| **严重度** | 🔴 **P0 越权** |
| **来源** | 用户直接指出：「会计不应该具有修改企业分配的权限，不应该看到与自己无关的企业」 |

---

## §1 缺陷描述

`EnterpriseController` 共 11 个端点，其中**只有 `switch` 一个**按 `agencyRole` 做了分流校验，其余 **10 个全部无任何角色校验**：

| 端点 | 会计（ACCOUNTANT）可做的事 | 应属于 |
|---|---|---|
| `POST /agency/enterprises` | **新建客户企业** | 代理经理 |
| `PUT /agency/enterprises/{id}` | **修改任意企业资料** | 代理经理 |
| `DELETE /agency/enterprises/{id}` | **删除企业** | 代理经理 |
| `POST .../bind`、`/unbind` | **变更企业归属代理** | 代理经理 |
| `POST .../activate`、`/suspend` | **启用/停用客户账套** | 代理经理 |
| `GET /agency/enterprises/page` | **列出任意代理的全部客户** | 代理经理 |

### 1.1 最严重的一处：`page` 接受调用方自填 `agencyId`

```java
// 修复前
public R<IPage<EnterpriseVO>> page(@RequestParam Long agencyId, ...)
```

会计登录后传**任意代理 id**，即可拿到该代理名下**全部客户企业**的列表 —— 而会计按设计只能看到派给自己的（P114/P116 已确立这条边界）。

### 1.2 连带发现：`current-period` 跨企业取数

该端点兜底分支查询会计期间时**未按 `enterprise_id` 过滤**，取的是全库最新期间：

```java
periodMapper.selectOne(new LambdaQueryWrapper<PeriodEntity>()
        .orderByDesc(PeriodEntity::getPeriodCode)   // ← 缺 enterprise_id 条件
        .last("LIMIT 1"));
```

租户隔离本应靠 `EnterpriseDataPermissionInterceptor`，但该层对本表不保证生效（§4.5 第 45 条 DIR-004 同型）。

## §2 根因判定

**不是「权限配错了」，而是「整批端点从未接入授权体系」。**

证据：
- 全仓 `@PreAuthorize` 仅 **11 处**，且全部在其它控制器（`SystemClearController`、`PeriodCloseController`），`EnterpriseController` **一处都没有**；
- `SecurityConfig` 只做 `anyRequest().authenticated()`，无任何路径级角色约束。

⇒ 这**不只影响会计**：助理、审核员同样能改删企业。会计只是最容易被察觉的角色。

这是 §4.5 第 43 条「只解释一条路径就等于整表放行」的同型 —— **P114 修了 `switch`，漏了 `list` 与整组 CRUD**。

## §3 修复方案（Q1~Q3 按老丁裁定）

| 裁定 | 选择 |
|---|---|
| **Q1 权限粒度** | **(A) 硬编码 `agencyRole` 分流** —— 与 `switchEnterprise` 既有写法一致、零新增基建。`@PreAuthorize` 统一化登记为独立工作项 |
| **Q2 `page` 参数** | **不接收调用方自填 `agencyId`**，可见范围由服务端按角色收敛 |
| **Q3 `current-period`** | **纳入统一保护**，并补上缺失的 `enterprise_id` 过滤 |

### 3.1 新增 `AgencyEnterpriseRoleGuard`

| 方法 | 语义 |
|---|---|
| `requireAgencyAdmin(op)` | 仅 `AGENCY_ADMIN` 可执行企业管理操作（`SUPER_ADMIN` 放行） |
| `resolveVisibleEnterpriseIds()` | 经理/审核器见本代理全部；会计/助理**仅见派工给自己的** |
| `requireVisibleEnterprise(id)` | 读路径断言可见性 |

### 3.2 可见范围按 `userType` 三分支

| userType | 可见集 |
|---|---|
| `SUPER_ADMIN` | 不受限 |
| `AGENCY` | 经理/审核员 = 本代理全部；会计/助理 = 派工企业 |
| `ENTERPRISE` 及其它 | **归属企业 ∪ 作为代理成员被派工的企业** |

⚠️ `ENTERPRISE` 分支不是「只能看自己的企业」—— 该用户可能同时是代理成员并被派工到其它企业（`TenantSwitchRealDBTest#memberSwitchRewritesContextAndIsolatesData` 即此例）。

## §4 🔴 实施中最贵的教训：守卫「收得过紧」连踩两次

| 轮次 | 守卫写法 | 后果 |
|---|---|---|
| 初版 | 只有 `AGENCY` 分支 | `ENTERPRISE` 自有用户被误判无权 ⇒ **8 例转红** |
| 二版 | `ENTERPRISE` = 「必须等于归属企业」 | 挡住「归属 A、派工授权访问 B」的合法用户 ⇒ **1 例转红** |
| 三版 | 三分支 + 归属 ∪ 派工 | ✅ 全绿 |

**判据：守卫必须先答「谁会被放行」，再答「该拦谁」。**
只写拒斥分支的守卫，在收紧时必然误伤合法路径 —— 而误伤的成本（线上功能不可用）与漏放的成本（越权）不对称，前者往往先暴露、后者可能长期静默。

### 4.1 「零回归」也可能是假象

P119 实施后 L2 全量 `2213/0/0/5` 零回归，但那些既有用例**全部无登录态** ⇒ `created_by` 落 `NULL` ⇒ 校验按设计放行。**零回归无法区分「校验正确放行」与「校验根本没生效」**，故必须另建真库行为测试（`AssetDisposalMakerCheckerRealDBTest` 5 例，含 `createdByIsPersisted` 自证前提）。

### 4.2 夹具连踩三次「凭语感写列名」

`AssetDisposalMakerCheckerRealDBTest` 初版裸 SQL 夹具：
1. 写 `t_asset_card.asset_category_id` —— 真实列名是 `category_id`
2. 漏了 NOT NULL 的 `residual_value` / `useful_life`
3. 写 `t_asset_category.category_name` / `category_type` / `sort_order` / `status` —— 真实列是 `code` / `name` / `level`

⇒ **改用 Entity 造数**，列名随映射自动对齐。

## §5 验证

| 验证项 | 结果 |
|---|---|
| 结构守卫 `AgencyEnterpriseRoleGuardStructureTest` | ✅ 6 例，**反证成立**（撤一处守卫即转红并列出端点） |
| 制审分离真库 `AssetDisposalMakerCheckerRealDBTest` | ✅ 5 例（自审被拒 / 他人放行 / NULL 放行 / 无登录态放行 / `created_by` 自证） |
| P119 结构守卫 `MakerCheckerAllApprovalsStructureTest` | ✅ 5 例，**反证成立**（删一行 `created_by` 写入即红） |
| L2 全量真库 | ✅ **2224 / 0 / 0 / 5** |
| L1 全量 | ✅ **1722 / 0 / 0 / 5**，`All coverage checks have been met` |
| 前端 `vue-tsc` | ✅ exit 0 |

### 5.1 前端契约同步

`getEnterpriseList(agencyId, page, size)` → `getEnterpriseList(page, size)`，两个调用点（`EnterpriseList.vue`、`AssignmentManage.vue`）同步。

## §6 已知局限与后续工作项

1. **`@PreAuthorize` 统一化未做** —— 本轮沿用硬编码分流（Q1 裁定）。全仓 `@PreAuthorize` 仍仅 11 处，授权机制未统一，登记为独立工作项。
2. **`BatchOperationController` 已补角色守卫（V1.2，见 §6.1）**。
3. **前端角色门禁已补**（V1.1，见 §6.2）。

### 6.1 🔴 一处必须更正的过度断言（V1.0 → V1.2）

V1.0 写：「`BatchOperationController` 4 端点全无守卫 ⇒ **会计仍可跨客户批量结账/审核**」。

**该结论不实，据实更正**：取到实现后确认，4 个端点**全是 501 未实现桩**
（`P107 D3` / `REQ-2026-134` 为避免假成功而显式禁用），
且 `BatchServiceNotImplementedTest` 7 例专门锁死「不得回报成功」。

⇒ **今天并无越权漏洞**：会计调 `POST /agency/batch/close` 只会拿到 501，不执行任何动作。

🔴 **教训（判据：判定「有漏洞」前，先确认端点到底是实现还是桩）**：
「端点没有角色守卫」**不等于**「可被打」。我因为看到「无守卫」就直接推导出
「会计可跨客户批量结账」，是把**形态**当成了**行为**。

V1.2 的实际动作（性质是**防未来回归**，不是修活洞）：
- 4 端点插入 `roleGuard.requireAgencyAdmin(...)`；
- 新增 `BatchOperationControllerGuardStructureTest` 4 例，把「端点必须有角色校验」
  钉成机械不变量 —— **实现放开而忘加守卫 ⇒ 立刻红**；
- 反证成立（撤守卫即精确列出 `/import`、`/close`）。
- ⚠️ 该守卫还锁了一条：控制器不得 `catch (BusinessException)` 或用 `try` 包裹批量调用 ——
  那会把 501 假成功包装成 200。

### 6.2 前端角色门禁（V1.1）

`AppSidebar.vue` 与 `routes/agency.ts` 原先漏标 `batch-operation` / `service-progress`。
已补 `v-if` + `meta.agencyRole`，并把角色守卫前移到企业门槛之前。
新增 `agency-role-gating.test.ts` 13 例（反证成立）。

⚠️ 同时更正 V1.0 的另一处错误表述：「前端无路由级角色拦截」是**错的** ——
`meta.agencyRole` 机制本就存在并覆盖 4 个路由，真实缺口只是漏标 2 个。

## §7 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-10-11 | 首版。修复 10 个无鉴权端点 + `page` 参数越权 + `current-period` 跨企业取数；新增 `AgencyEnterpriseRoleGuard` 与结构守卫 6 例 |
| V1.1 | 2026-10-11 | 前端角色门禁补齐（侧边栏 + 路由 meta + 守卫顺序前移）；新增 `agency-role-gating.test.ts` 13 例 |
| V1.2 | 2026-10-11 | 补 `BatchOperationController` 4 端点守卫 + `BatchOperationControllerGuardStructureTest` 4 例。🔴 **据实更正 V1.0 的过度断言**：「会计仍可跨客户批量结账」不实 —— 4 端点全是 501 未实现桩，今天并无活洞；本批性质是防未来回归 |

### 附：前端验证数据

| 项 | 结果 |
|---|---|
| 前端全量测试 | ✅ **27 个文件 / 278 用例全过** |
| `vue-tsc --noEmit` | ✅ exit 0 |
| 新守卫 `agency-role-gating.test.ts` | ✅ 13 例，两个反证均成立 |