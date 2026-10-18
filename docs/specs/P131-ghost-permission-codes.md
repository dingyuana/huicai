# SPEC-P131：消除幽灵权限码 —— 路由 meta 与 t_menu 的隐式契约加上校验

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已审核通过并实施**（2026-10-15，老丁裁定「立项 + 加守卫」） |
| REQ | REQ-2026-148 |
| 前序 | SPEC-P130（`voucher:update` 是首个被点名的幽灵码） |
| 关联 | AGENTS §4.5 第 21 条（门禁能红才算数）、第 31 条（判定依据须覆盖违规全部形态）、第 45 条（用门禁自己的口径） |

---

## §0 根因

**权限码是前端路由 meta 与后端菜单表之间的隐式契约，却从无任何校验。**

```js
// 前端路由
{ path: 'finance/voucher/edit', meta: { permission: 'voucher:update' } }
// 路由守卫
if (requiredPerm && !authStore.hasPermission(requiredPerm)) return next('/403')
```

若 `voucher:update` 在 `t_menu` 中不存在 ⇒ 无菜单行 ⇒ 无角色菜单关联 ⇒ `hasPermission` **恒 false** ⇒ **该页面对所有非超管角色永久 403**。

症状是「点进去 403」，**看起来像正常的权限不足**，极难联想到「这个权限码压根不存在」。

### 0.1 全量比对结果

以「前端路由 meta.permission」对照「`t_menu.permission`」：

```
路由引用 43 个   库中存在 35 个   幽灵码 11 个
```

| # | 幽灵权限码 | 影响路由 | 性质 |
|---|---|---|---|
| 1 | `voucher:update` | `finance/voucher/edit` | 动作权限缺失 → **P130 已修** |
| 2 | `system:role:list` | `system/role` | 页面**连菜单行都没有** |
| 3 | `system:menu:list` | `system/menu` | 同上 |
| 4 | `system:dept:list` | `system/dept` | 同上 |
| 5 | `system:audit:list` | `system/audit-log` | 同上 |
| 6 | `arap:settlement:list` | `arap/reconciliation`、`balance-summary`、`prepayment-balance-summary` | 库中只有 `arap:reconciliation:workbench` |
| 7 | `arap:expense:create` | `arap/expense/edit` | 库中只有 `arap:expense:list` |
| 8-11 | `lab:ai` / `lab:analysis` / `lab:budget` / `lab:salary` | 实验室 8 个路由 | 见 §1② |

🔴 **影响比「点进去 403」更广**：第 2-5 项连菜单行都不存在 ⇒ **侧边栏对所有角色都不显示**，角色管理/菜单管理/部门管理/审计日志这 4 个页面**对 ADMIN 也是不可达的**。

---

## §1 方案

### ① 补缺失的权限码与菜单行（`V186`）

**角色分配原则（可审计的默认，不是业务裁定）**：**新权限码继承其「同页面 / 同模块」既有权限码的角色集合**。

| 新增权限码 | 继承自 | 得到的角色 |
|---|---|---|
| `system:role:list` / `menu:list` / `dept:list` / `audit:list` | `system:user:list` | ADMIN |
| `arap:expense:create` | `arap:expense:list` | ADMIN、FINANCE_MGR、OPERATOR |
| `arap:settlement:list` | `arap:reconciliation:workbench` | ADMIN、FINANCE_MGR、OPERATOR |

> ⚠️ 该原则**不是业务裁定**。若某码确应给更多/更少角色，须在 §7 写明理由后再改，**不得在迁移里静默扩权**。

### ② lab 模块：**移除**路由 permission，而非补码

实验室路由本就有**独立门禁** —— 功能开关 `labStore.enabled`（关闭即 404）。叠加一个**无人能拥有的权限码**，等于把页面**双重锁死**。

⇒ 移除 `lab:*` 权限声明，保留功能开关作为唯一门禁。

> 判据：**门禁应只有一道且可解释**。凭空多出的第二道门禁（且无人能通过）只会掩盖第一道的意图。

---

## §2 输入 / 输出契约

无端点变更。变更的是**数据（菜单与授权）**与**路由 meta**。

| 变更点 | 内容 |
|---|---|
| `t_menu` | 新增 7 行（4 个系统管理页面 MENU + 1 个核销往来 MENU + `arap:expense:create` BUTTON） |
| `t_role_menu` | 按 §1① 原则授权 |
| `GET /auth/userinfo` | 相关角色的 `permissions` 数组新增对应码 |
| 前端 `router/routes/lab.ts` | 移除 8 处 `permission: 'lab:*'` |

---

## §3 状态流转

**不涉及**。

---

## §4 异常处理

| 场景 | 处理 |
|---|---|
| 新增页面仍对非授权角色 403 | 符合预期（授权粒度生效） |
| lab 模块 | 由 `labStore.enabled` 决定 404，与权限无关 |

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-131-1 | 全仓路由 | 比对 `meta.permission` 与 `t_menu` | **无幽灵码**（守卫） |
| AT-131-2 | ADMIN | 访问 `system/role` | 不再 403 |
| AT-131-3 | FINANCE_MGR | 访问 `arap/reconciliation` | 不再 403 |
| AT-131-4 | 注入一个不存在的权限码到路由 | 跑守卫 | **转红并点名该码** |
| AT-131-5 | lab 功能开关关闭 | 访问 lab 页面 | 404（门禁仍生效） |

⚠️ **AT-131-5 不可省**：移除 permission 后若未验证功能开关仍在，等于把双重锁死换成了「完全无门禁」。

---

## §6 实施结果（2026-10-15）

`V186__spec_p131_seed_missing_permission_codes.sql` 已应用；`lab.ts` 移除 8 处权限声明。

守卫 `RoutePermissionGhostCodeRealDBTest` 3 例：

| 例 | 作用 |
|---|---|
| `noGhostPermissionCode` | 主判定，精确报出 10 个幽灵码（实施前） |
| `guardDetectsGhost` | 控制组：已存在的码不报、构造的假码必报 |
| `parserActuallyFindsPermissions` | **解析器自证** —— 只解析到 <20 个码即判失败，防解析器随路由写法变化而静默失效 |

**反证已做**：向 `sme-base.ts` 注入 `ghost:does:not:exist` ⇒ **1 例转红**。

🔴 **守卫必须是「真库」测试**：权限码的真值在**数据库**里，不在文件里。用文件推断（例如只 grep 迁移 SQL）会在种子方式变化时立刻失真 —— 与 SPEC-P127 记录的「工具读数对实现细节过度敏感时不可作为存量依据」同型。

L1 `1754/0/0/5` + `All coverage checks have been met`；L2 `2280/0/0/5`。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不做「后端端点也按权限码鉴权」—— 当前 `SecurityConfig` 仅 `authenticated()` + `@PreAuthorize` 仅 13 处，**权限模型本身尚未成体系**（P120 已记录）。本 SPEC 只保证「前端引用的码真实存在」，**不宣称已建立服务端授权**。
- ❌ 不逐个复核 5 个角色的权限集合是否「业务上正确」—— 本 SPEC 只修「引用了不存在的码」，不重做权限设计。

**已知局限**：
1. **守卫只管「存在性」，不管「授权是否恰当」** —— 一个码存在但没授予任何角色，仍会让页面永久 403，守卫不报。
2. **lab 模块现在只由功能开关把守** —— 开关开启后**任何已登录用户**都能访问实验室页面。这是移除权限声明的直接后果，**须老丁确认可接受**；若不可接受，应改为补 `lab:*` 权限码并授予 ADMIN。
3. **角色分配是「继承同组」的默认**，非业务裁定（见 §1①）。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: MenuEntity
module: system
table: t_menu

rules:
  - id: C-01
    source: "P131 §0 判据"
    rule: "前端路由 meta.permission 引用的每个权限码，必须在 t_menu 中存在对应行"
    mapping: { invariant: route_permission_exists_in_menu, symptom: "永久 403 且形似正常权限不足" }
    implementation: "RoutePermissionGhostCodeRealDBTest"
  - id: C-02
    source: "P131 §1①"
    rule: "新增权限码须继承其「同页面/同模块」既有权限码的角色集合；不得在迁移中静默扩权"
    mapping: { default: inherit_sibling_roles, expansion_requires: "SPEC §7 写明理由" }
    implementation: "V186 migration"
  - id: C-03
    source: "P131 §1①"
    rule: "system:role/menu/dept/audit:list 须存在且授予 ADMIN；arap:expense:create 继承 arap:expense:list；arap:settlement:list 继承 arap:reconciliation:workbench"
    mapping: { system_pages: [role, menu, dept, audit], arap_expense_create: arap:expense:list, arap_settlement_list: arap:reconciliation:workbench }
    implementation: "V186 migration"
  - id: C-04
    source: "P131 §1②"
    rule: "lab 路由不得声明 permission；其唯一门禁为功能开关 labStore.enabled"
    mapping: { lab_permission: forbidden, sole_gate: labStore.enabled }
    implementation: "frontend/src/router/routes/lab.ts + 路由守卫"
  - id: C-05
    source: "P131 §5 / §4.5 第 21 条"
    rule: "守卫必须自证：解析器若解析到的权限码少于预期数量即判失败，防其随路由写法变化而静默失效"
    mapping: { parser_self_check_min_count: 20 }
    implementation: "RoutePermissionGhostCodeRealDBTest.parserActuallyFindsPermissions"