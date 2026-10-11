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

## §6 已知局限

1. **`@PreAuthorize` 统一化未做** —— 本轮沿用硬编码分流（Q1 裁定）。全仓 `@PreAuthorize` 仍仅 11 处，授权机制未统一，登记为独立工作项。
2. **前端无路由级角色拦截** —— 菜单由后端 `t_menu` 驱动，会计仍可能看到「派工管理」入口（点击后 403）。**UI 隐藏不是安全边界**，真正的防线在服务端；是否隐藏菜单属产品决策，未擅自改。
3. **`current-period` 的 `enterprise_id` 过滤只补在兜底分支** —— 其余分支走 `enterpriseMapper` 的按企业查询，本已带条件。

## §7 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-10-11 | 首版。修复 10 个无鉴权端点 + `page` 参数越权 + `current-period` 跨企业取数；新增 `AgencyEnterpriseRoleGuard` 与结构守卫 6 例 |