# SPEC-P130：补齐幽灵权限码 voucher:update（凭证编辑页对非超管永久 403）

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已审核通过并实施**（2026-10-15，老丁裁定方案 A） |
| REQ | REQ-2026-146 |
| 症状 | 代理端会计打开 `/finance/voucher/detail?id=98`，点「编辑」跳 **403**；同模块其它操作正常 |
| 关联 | AGENTS §4.5 第 45 条（评估存量须用门禁自己的口径）、第 21 条（门禁能红才算数）、§4.2 第 11 条（`menu_code` NOT NULL） |

---

## §0 根因

### 0.1 现象的迷惑性

| 页面 | 路由 meta.permission | 结果 |
|---|---|---|
| `/finance/voucher/detail` | `voucher:list` | ✅ 正常（**库中存在**） |
| `/finance/voucher/edit` | `voucher:update` | 🔴 **403**（**库中不存在**） |

**同一模块、同一个用户，只因路由 meta 写了不同的权限码而表现相反** —— 这是定位方向的关键。

### 0.2 根因：`voucher:update` 是**幽灵权限码**

`finance/voucher/edit` 的 `meta.permission = 'voucher:update'`，路由守卫据此判定：

```js
if (requiredPerm && !authStore.hasPermission(requiredPerm)) return next({ path: '/403' })
```

而 **`t_menu` 中从来不存在 `voucher:update` 这一行**。全库 voucher 相关权限码只有两个：

```
voucher:list      → ACCOUNTANT, ADMIN, FINANCE_MGR
voucher:type:list → ACCOUNTANT, ADMIN, FINANCE_MGR
```

没有菜单行 ⇒ 没有角色菜单关联 ⇒ **`hasPermission` 恒 false** ⇒ 除 `SUPER_ADMIN`（守卫对其跳过）外，**任何角色都不可能拥有该权限**，该页面等于永久关闭。

### 0.3 业务依据

会计的职责是**制单**（P119 / S-26：ACCOUNTANT 制单、REVIEWER 审核）。不能编辑自己录入的草稿凭证，制单职责即被架空。实测被挡的用户正是凭证 98 的**制单人本人**（`created_by = 6`）。

### 0.4 判据沉淀

🔴 **「权限码」是前端路由与后端菜单表之间的隐式契约，但它没有任何校验**。前端写错一个码、或后端漏建一行，症状都是「点进去 403」，**且看起来像正常的权限不足**。

> 判据：**凡前端 `meta.permission` 引用的权限码，必须在 `t_menu` 中存在对应行。** 二者不一致 ⇒ 该页面对非超管永久 403。

---

## §1 方案（老丁裁定 A）

**不**把编辑页的 meta 改成 `voucher:list`：那会把「读」与「写」合并成一个权限，权限模型永久失去读写分离 —— 而 `voucher:update` 这个命名**已存在于代码中**，说明设计时就区分了读写，缺的只是**从未落成菜单行**。

`V185`：

1. 补 `t_menu` 行：`voucher:update` / `menu_code = voucher_update` / **`menu_type = BUTTON`**（动作权限、非可导航页面，形态对齐既有 BUTTON 行）/ `parent_id` = `voucher:list` 所在菜单；
2. 授予 **`ACCOUNTANT`** 与 **`FINANCE_MGR`**（按 `role_code` 而非 `role_id` —— id 会随种子变动）；
3. 两者均**幂等**（`NOT EXISTS` / `ON CONFLICT`）—— `t_menu` 已知存在重名记录，补数据必须自带幂等（§4.5）。

---

## §2 输入 / 输出契约

**无端点变更**，仅补数据。

| 变更点 | 内容 |
|---|---|
| `GET /auth/userinfo` | `ACCOUNTANT` / `FINANCE_MGR` 的 `permissions` 数组新增 `voucher:update` |
| `GET /api/base/voucher/v1/vouchers/{id}` | 不变 |
| `PUT /api/base/voucher/v1/vouchers/{id}` | 不变（后端本就只校验 `status=DRAFT` + 期间开启，**无归属/角色校验**） |

---

## §3 状态流转

**不涉及**（权限码落地不改变凭证状态机）。

---

## §4 异常处理

| 场景 | 处理 |
|---|---|
| 代理端会计点编辑 | 正常进入（此前 403） |
| 未获授权角色（如出纳 CASHIER） | 仍 403 —— 本次**不**授予 CASHIER |
| 超管 | 始终可进（守卫跳过） |

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-130-1 | 角色 `ACCOUNTANT` | 读 `/auth/userinfo` | `permissions` 含 `voucher:update` |
| AT-130-2 | 角色 `ACCOUNTANT` | 访问 `/finance/voucher/edit` | **不跳 403** |
| AT-130-3 | 角色 `CASHIER` | 访问 `/finance/voucher/edit` | 仍跳 403（未授予） |
| AT-130-4 | 角色 `FINANCE_MGR` | 读 `/auth/userinfo` | 含 `voucher:update` |
| AT-130-5 | 重复执行 `V185` | 迁移 | 不产生重复行（幂等） |

---

## §6 实施结果（2026-10-15）

`V185__spec_p130_seed_voucher_update_permission.sql` 已应用：

```
t_menu:  id=203  parent=31  menu_name=编辑凭证  menu_code=voucher_update
                   menu_type=BUTTON  permission=voucher:update
授予:    ACCOUNTANT(1)  FINANCE_MGR(1)
验证:    accountant01（同为 AGENCY/ACCOUNTANT）的 permissions 含 voucher:update
```

L1 `1754/0/0/5` + `All coverage checks have been met`；L2 `2273/0/0/5`。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不把 `voucher:update` 授予 `CASHIER`（出纳职责不含制单）
- ❌ 不改 `PUT /vouchers/{id}` 的后端守卫（见下「已知局限」）

**🔴 已知局限（不在本 SPEC 范围，但已取证，须如实登记）**：

1. **凭证编辑后端无归属/角色校验** —— `VoucherServiceImpl#update` 只校验 `status=DRAFT` + 期间开启，**不校验操作人是否为制单人**。本 SPEC 修的是「进不去编辑页」，但**进得去之后，任何有访问权的人都能改别人的草稿凭证**。这属 P119/P120 同系列的内控缺口，**须单独立项**。
2. **其余 10 个幽灵权限码未处理** —— 全量比对（43 个路由引用 vs 35 个库中权限码）发现 11 个幽灵码，本 SPEC 只修 `voucher:update` 一个：

   | 幽灵权限码 | 影响页面 |
   |---|---|
   | `voucher:update` | 凭证编辑 ✅**本 SPEC 已修** |
   | `system:role:list` / `system:menu:list` / `system:dept:list` / `system:audit:list` | 角色/菜单/部门/审计日志 —— **系统管理整块** |
   | `arap:settlement:list` | 核销、往来汇总、预付汇总 |
   | `arap:expense:create` | 费用报销编辑 |
   | `lab:ai` / `lab:analysis` / `lab:budget` / `lab:salary` | 实验室模块（可能是有意隔离，**须确认**） |

   ⇒ **须单独立项**：逐个裁定「建菜单行」还是「改路由 meta」，并加守卫**禁止路由 meta 引用库中不存在的权限码**，否则该类缺陷持续再生。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: MenuEntity
module: system
table: t_menu

# 不涉及业务状态机（权限码落地），契约以「规则型」表达。
rules:
  - id: C-01
    source: "P130 §1"
    rule: "t_menu 必须存在 permission='voucher:update' 的行，且 menu_type='BUTTON'、menu_code='voucher_update'"
    mapping: { permission: voucher:update, menu_type: BUTTON, menu_code: voucher_update }
    implementation: "V185 migration"
  - id: C-02
    source: "P130 §1"
    rule: "voucher:update 须授予 role_code 为 ACCOUNTANT 与 FINANCE_MGR 的角色；不得授予 CASHIER"
    mapping: { grant: [ACCOUNTANT, FINANCE_MGR], forbid: [CASHIER] }
    implementation: "V185 migration"
  - id: C-03
    source: "P130 §1"
    rule: "补权限码必须幂等，重复执行不产生重复行（t_menu 已知存在重名记录）"
    mapping: { idempotent: true, technique: [NOT_EXISTS, ON_CONFLICT] }
    implementation: "V185 migration"
  - id: C-04
    source: "P130 §0.4 判据沉淀"
    rule: "前端路由 meta.permission 引用的权限码必须在 t_menu 中存在对应行；幽灵码使该页面对非超管永久 403"
    mapping: { invariant: "route_permission_exists_in_menu", symptom: "永久 403 且形似正常权限不足" }
    implementation: "待立项：幽灵权限码守卫"
  - id: C-05
    source: "P130 §7 已知局限 1"
    rule: "VoucherServiceImpl#update 当前只校验 status=DRAFT 与期间开启，不校验操作人是否为制单人"
    mapping: { known_gap: no_ownership_check, status: 已取证待立项 }
    implementation: "n/a"