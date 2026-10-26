# SPEC-P136：前端批量操作按权限前置拦截（禁点 + 弹窗解释，替代 403 报错）

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已实施并验证**（2026-10-20，老丁批准收尾选项 1；前端全量 316/316 绿） |
| REQ | REQ-2026-152 |
| 前序 | SPEC-P133（后端 `enforcement: enforcing` 已生效）、SPEC-P131/P130（权限码必须真实存在） |
| 关联 | AGENTS §4.5 第 43 条（守卫须先答「谁被放行」再答「该拦谁」）、第 45 条（用门禁自己的口径）、§1 铁律 #13、§3 R9/R10 |

---

## §0 取证（全部实测，2026-10-19）

### 0.1 用户报障现象

代理端会计 `liu`（刘会计）在「凭证管理」勾选 `SUBMITTED` 凭证点「批量审核」：

```
POST http://localhost:3001/api/base/voucher/v1/vouchers/batch-audit  →  403 (Forbidden)
浏览器控制台：Failed to load resource: the server responded with a status of 403 (Forbidden)
页面：红色错误提示「权限不足，请联系管理员」（request.ts:121）
```

用户诉求（原文）：「权限没有不能出现错误提示，而是应该弹窗提示」。

### 0.2 刘会计的真实权限（真库实测）

| 项 | 值 |
|---|---|
| `t_user` | id=6, `username=liu`, `real_name=刘会计`, `user_type=AGENCY`, `agency_role=ACCOUNTANT` |
| 角色 | `ACCOUNTANT`（会计） |
| 凭证类权限码 | `voucher:list` / `voucher:create` / `voucher:submit` / `voucher:update` / `voucher:type:list` |
| `voucher:audit` | ❌ **无** |

**这不是数据配错**：`V188`（SPEC-P133 D-8）按「制单人不能自审」（铁律 #1）**刻意不给 ACCOUNTANT**：

```sql
-- ↓ 以下为「审核与记账族」，按 D-8 **刻意不给 ACCOUNTANT**
('FINANCE_MGR', 'voucher:audit'),
('ADMIN',       'voucher:audit'),
```

真库实测持有 `voucher:audit` 的账号仅 5 个：`admin`(ADMIN)、`reviewer01`(FINANCE_MGR)、`qingdao_a`/`qingdao_b`/`shandong_c`(ADMIN)。

⇒ **后端拒绝是正确的**。缺陷在**前端**：按钮不该可点，且被拒时应给弹窗而非错误 toast。

### 0.3 后端行为（已生效，不改）

```
2026-10-19T08:51:08.112Z WARN PermissionEnforcementInterceptor :
  端点权限拒绝: POST /api/base/voucher/v1/vouchers/batch-audit 需要=voucher:audit
```

`permission-manifest.yml:2` 为 `enforcement: enforcing`，清单第 334~335 行登记：

```yaml
- method: POST
  path: /api/base/voucher/v1/vouchers/batch-audit
  permission: voucher:audit
  write: true
```

### 0.4 🔴 根因：前端权限执行层**从未落地**

| 层 | 现状 | 证据 |
|---|---|---|
| 路由守卫 | ✅ 有（`router/index.ts:92` 按 `meta.permission`） | 页面级可控 |
| `v-permission` 指令 | ✅ 已注册（`main.ts:22`） | **但全前端使用次数 = 0** |
| `hasPermission()` | ✅ 可用（`auth.store.ts:47`） | 无调用方 |
| **批量操作条** | 🔴 **完全不看权限** | `BatchActionDef` 无 `permission` 字段；`BatchActionBar.vue` 只判「选中数 + 状态矩阵」 |
| 403 处理 | 🔴 `ElMessage.error` 全局 toast | `request.ts:121` |
| `useBatchOperation` catch | 🔴 再弹一次 `ElMessage.error(msg)` | `useBatchOperation.ts` → **一次点击两条红字** |

`SPEC-P133 §8 D-4` 原文：

> 前端按钮是否全接 `v-permission` → **是，但排在后端之后**（前端是体验层）

后端已于批次 4 切 `enforcing`，故 **D-4 的「之后」已到**。

### 0.5 影响面（实测三处批量条）

`grep -rln 'BatchActionBar' frontend/src` = **3 个页面**：

| 页面 | 批量动作 | 对应权限码（清单实测） |
|---|---|---|
| `views/finance/voucher/VoucherList.vue` | submit / audit / post | `voucher:submit` / `voucher:audit` / `voucher:post` |
| `views/tax/output-invoice/OutputInvoiceList.vue` | submitReview / confirm / reject / revert / markVouchered / void / reverse | `tax:output:review` / `:confirm` / `:reject` / `:revert` / `:vouchered` / `:void` / `:reverse` |
| `views/finance/bank-statement/BankStatementView.vue` | confirm / audit | `bank:statement:list`（清单登记为 coarse，两动作同码） |

⚠️ **银行流水的两个动作共用 `bank:statement:list`**（`permission-manifest.yml:729-741` 均标 `coarse: true`）⇒ 前端按码判断时**两个按钮要么都能点、要么都不能点**。这是后端粗粒度屏障的既有事实，本 SPEC **不擅自细分**（属 P133 D-9b 范围）。

### 0.6 为什么「只改错误提示」不够（方案 2 被否的理由）

若只把 403 的 toast 换成弹窗：

- 按钮**仍然可点** ⇒ 用户仍会反复点、反复吃弹窗；
- 前端仍不知道「为什么不能点」，无法提示「需要哪个权限 / 找谁开」；
- 与 `SPEC-P121`（消除「看得见却点不开」）的口径冲突：可发现性有，但**可解释性为零**。

---

## §1 方案（选项 1，老丁已选定）

### 1.1 三层改动

| # | 文件 | 改动 |
|---|---|---|
| A | `components/batch/BatchActionBar.vue` | `BatchActionDef` 增可选 `permission?: string`；`enabledOf()` 增 `authStore.hasPermission(permission)`；tooltip 区分「无权限」与「状态不符」 |
| B | `composables/useBatchOperation.ts` | `run()` 入口前置权限校验：缺权限 ⇒ `ElMessageBox.alert`（弹窗）并 `return`，**不调用 executor** |
| C | 三个页面 | 为每个 `BatchActionDef` 补 `permission` 字段（按 §0.5 映射） |

### 1.2 交互契约（BDD）

**场景 1：无权限 + 按钮禁用**

```gherkin
Given 当前用户权限码不含 voucher:audit
And   列表中存在 SUBMITTED 状态的凭证
When  用户勾选该凭证
Then  「批量审核」按钮 disabled=true
And   悬浮显示「无审核权限，请联系管理员开通」  ← 不是「当前选中记录不支持」
And   点击不发出任何 HTTP 请求
```

**场景 2：有权限 + 正常可用**

```gherkin
Given 当前用户权限码含 voucher:audit
When  用户勾选 SUBMITTED 凭证
Then  「批量审核」按钮 enabled=true
And   点击后正常调用 POST /vouchers/batch-audit
```

**场景 3：绕过 UI 直接调用（防回归）**

```gherkin
Given 当前用户权限码不含 voucher:audit
When  代码路径直接调用 useBatchOperation.run({permission:'voucher:audit'}, executor)
Then  弹出 ElMessageBox.alert（含「权限不足」与所需权限码）
And   executor 从未被调用
And   不发 HTTP 请求（无 403 产生）
```

**场景 4：后端仍拒绝（纵深防御，不回归）**

```gherkin
Given 前端权限判断被绕过（如 token 被篡改）
When  请求到达后端
Then  后端仍返 403（P133 拦截器，本 SPEC 不动）
And   前端 403 处理改为弹窗而非 toast（见 §1.3）
```

### 1.3 403 兜底提示（保留但改为弹窗）

`request.ts:120-123` 的 `ElMessage.error('权限不足，请联系管理员')` 改为 `ElMessageBox.alert`，文案含「当前账号缺少该操作权限，请联系管理员开通」。

**理由**：场景 4 无法用前端预判完全消除（权限可能在会话中途被撤销、token 可伪造）。此时**仍不能给用户一个「系统报错」的观感** —— 403 是**预期内的业务结果**，不是故障。

⚠️ **避免重复弹窗**：`useBatchOperation` 的 catch 目前对任何异常都 `ElMessage.error(msg)`。403 已经弹过窗后，catch 必须**跳过**（否则一次点击 = 弹窗 + 红字）。判据：以 `error.response.status === 403` 识别，而非比对中文文案。

---

## §2 输入 / 输出契约

### 2.1 `BatchActionDef` 扩展（向后兼容）

```ts
export interface BatchActionDef {
  key: string
  label: string
  type?: 'primary' | 'warning' | 'danger'
  needReason?: boolean
  needConfirm?: boolean
  confirmText?: string
  maxCount?: number
  /** 新增：执行该动作所需权限码；缺省 = 不做权限判断（向后兼容） */
  permission?: string
}
```

**为什么可选**：存量三处批量条之外，`batch-ops.test.ts` 等测试构造 `BatchActionDef` 时不带该字段；设为必填会波及既有用例（`__tests__/batch-ops.test.ts` 共 8 例）。**可选 + 缺省放行** 使本改动**不改变未声明权限的既有行为**。

⚠️ 但这带来一个风险：**新页面忘记声明 `permission` 时静默不设防**。由 §4 的结构守卫兜住（棘轮：三个页面 × 各自动作数 = 12 个 `BatchActionDef` 必须全部声明）。

### 2.2 `run()` 新增前置校验的返回语义

| 条件 | 行为 | 返回值 |
|---|---|---|
| 无 `permission` | 直接执行 | 正常 |
| 有 `permission` 且 `hasPermission()` 为真 | 直接执行 | 正常 |
| 有 `permission` 且为假 | `ElMessageBox.alert` + `return` | `undefined`（**不抛异常**） |

**不抛异常的理由**：`run()` 是 UI 事件处理器，抛异常只会进 `catch` 再弹一次红字 —— 正是本次要消除的形态。

### 2.3 权限码来源（禁止前端硬编码）

`hasPermission()` 读的是登录响应 `userInfo.permissions`（`auth.store.ts:82`），即后端 `getUserButtonPermissions()` 从 `t_menu` 解析的结果 ⇒ **权限码真值只有一份**（符合 P133「单一事实源」）。

⚠️ 前端 `BatchActionDef.permission` 里写的字符串**必须与清单一致**。若写错（如 `voucher:auditVoucher`），`hasPermission` 恒 false ⇒ 按钮**永久禁用**，且症状形似「没配权限」。由 §4 守卫 G-2 校验「三个页面的 permission 字面量必须存在于权限清单」。

---

## §3 状态流转

本 SPEC **不涉业务状态机**，只涉「按钮可用性」这一 UI 状态：

```
[未勾选] --勾选--> [已勾选]
[已勾选] --有权限 + 状态匹配--> [可执行]
[已勾选] --无权限--> [禁用 + tooltip: 无权限]
[已勾选] --有权限但状态不符--> [禁用 + tooltip: 状态不支持]
```

**优先级**：`无权限` 判断**先于** `状态矩阵`。理由：状态会随操作变化，权限不会 —— 先告诉用户「你压根没这个权限」比「这批记录状态不对」更有行动指引（去找管理员，而不是去找别的记录）。

---

## §4 守卫（防漂移，机械可检）

| # | 守卫 | 判定 | 反证 |
|---|---|---|---|
| G-1 | 三个批量条页面的 `BatchActionDef` **全部声明 `permission`** | 扫描 3 个 `.vue` 的 `BATCH_ACTIONS` 数组，断言每项含 `permission:`；棘轮值 = 12 | 删除任一 `permission` 行 ⇒ 转红 |
| G-2 | 声明的 `permission` 字面量**必须存在于 `permission-manifest.yml`** | 解析清单取 `permission` 集合，与前端字面量求差集 | 注入 `voucher:auditXXX` ⇒ 转红 |
| G-3 | `run()` 的权限前置校验**必须在 executor 之前** | 单元测试：无权限时 `executor` 未被调用 + `alert` 被调用 | 把校验移到 `executor` 之后 ⇒ 转红 |
| G-4 | 403 不得再走 `ElMessage.error` | 源码断言 `request.ts` 的 403 分支不含 `ElMessage.error` | 改回 ⇒ 转红 |

⚠️ **G-2 是必要的**：P130/P131 已证明「权限码写错 = 页面静默永久 403」，而本次是「按钮静默永久禁用」—— **同一机制在 UI 层的再现**，且症状同样像「正常的权限不足」。

---

## §5 验收（BDD → 测试用例映射）

| 场景 | 测试文件 | 用例 |
|---|---|---|
| AT-136-1 无权限按钮禁用 | `__tests__/BatchActionBar.test.ts`（新建） | `disabledWhenLacksPermission` |
| AT-136-2 有权限按钮可用 | 同上 | `enabledWhenHasPermission` |
| AT-136-3 tooltip 区分无权限/状态不符 | 同上 | `tooltipExplainsPermissionVsStatus` |
| AT-136-4 run 前置拦截不调 executor | `__tests__/batch-ops.test.ts`（扩展） | `runBlocksWhenLacksPermission` |
| AT-136-5 run 无权限时弹 alert 不抛异常 | 同上 | `runAlertsInsteadOfThrowing` |
| AT-136-6 缺省 permission 不改变既有行为 | 同上（回归锁） | 既有 8 例必须全绿 |
| AT-136-7 凭证页三个动作的权限码 | `__tests__/VoucherList.test.ts`（扩展） | `batchActionsDeclarePermissions` |
| AT-136-8 403 走弹窗 | `__tests__/request.test.ts`（新建） | `forbiddenShowsDialogNotToast` |
| AT-136-9 结构守卫 G-1/G-2 | `__tests__/batch-action-permission.test.ts`（新建） | `allBatchActionsDeclarePermission` / `permissionCodesExistInManifest` |

**负向断言强制**：AT-136-4 必须同时断言「`executor` 未被调用」与「无 HTTP 请求发出」，只断言弹窗无法排除「弹了窗但仍执行」（§4.5 第 43 条同型）。

---

## §6 明确排除（§7.7）

1. ❌ **不改后端权限分配** —— 不给 ACCOUNTANT 加 `voucher:audit`（违反 P133 D-8 制审分离）。
2. ❌ **不细分银行流水的粗粒度码** —— `batch-review`/`batch-audit` 共用 `bank:statement:list` 是 P133 D-9b 范围。
3. ❌ **不全站接 `v-permission`** —— 本 SPEC 只覆盖 3 个批量条；其余按钮（如凭证详情页的「审核」单条按钮）登记为待办。
4. ❌ **不改 `BatchActionBar` 的 `every()` 启用语义** —— 那是 P67 已裁定的「杜绝静默子集执行」。

---

## §7 已知局限（如实登记）

1. **单条操作按钮仍可点** —— `VoucherDetail.vue:10` 的「审核」按钮无权限判断，点了仍吃 403。本 SPEC 只做批量条（用户报障路径），单条按钮须同批或单独立项。
   - ⚠️ **同一缺陷的两种形态**：批量条和单条按钮调的是**同一个权限码**，只修批量条会让「批量不可点、单条可点」看起来像不一致。建议同批覆盖 `VoucherDetail.vue`（4 个按钮），但**须老丁确认是否扩大范围**。
2. **前端判断可被绕过** —— devtools 改 `permissions` 数组即可让按钮变可点。**后端 403 是真正的边界**，前端只是体验层（与 P133 D-4 表述一致）。
3. **权限变更需重新登录才生效** —— `permissions` 在登录时写入 store，`fetchUserInfo` 只在刷新页面时重取。管理员中途开权限后，用户需刷新页面。
4. **银行流水两个动作同码** —— 见 §0.5，前端无法区分「能确认不能审核」。

---

## §8 待老丁裁定

| # | 议题 | 建议 | 影响 |
|---|---|---|---|
| D-1 | 是否同批覆盖单条操作按钮（`VoucherDetail.vue` 4 个） | **建议同批** | 否则「批量灰、单条亮」，体验割裂且留洞 |
| D-2 | 403 全局处理改弹窗是否影响其它页面 | 建议改 | 全站 403 均属「预期业务结果」，toast 与弹窗的差别在于「是否需要用户确认知悉」 |
| D-3 | 银行流水粗粒度码是否本次细分 | **建议不做** | 属 P133 D-9b，混做会让变更无法归因 |
| D-4 | 守卫 G-1 的棘轮值 | 12（3 页面动作数之和） | 新增动作必须同步声明 |

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: BatchActionDef
module: frontend
table: null

rules:
  - id: C-01
    source: "P136 §1.1 A"
    rule: "BatchActionDef 可选字段 permission；BatchActionBar 的 enabledOf 必须在状态矩阵之外再判 hasPermission"
    mapping: { field: permission, optional: true, check: hasPermission }
    implementation: "frontend/src/components/batch/BatchActionBar.vue"
  - id: C-02
    source: "P136 §2.2"
    rule: "useBatchOperation.run 在调用 executor 之前校验 permission；缺权限时弹 alert 并 return，不得抛异常"
    mapping: { order: "permission_check -> executor", on_denied: alert_and_return, throw: false }
    implementation: "frontend/src/composables/useBatchOperation.ts"
  - id: C-03
    source: "P136 §1.3"
    rule: "HTTP 403 必须以对话框告知用户，不得使用 ElMessage.error；useBatchOperation 的 catch 对 403 不得重复提示"
    mapping: { http: 403, ui: ElMessageBox.alert, dedupe: by_status_code }
    implementation: "frontend/src/api/request.ts"
  - id: C-04
    source: "P136 §2.3 / §4 G-2"
    rule: "前端 BatchActionDef.permission 的字面量必须存在于 permission-manifest.yml 的 permission 集合"
    mapping: { invariant: "frontend_permission_in_manifest" }
    implementation: "待实施：__tests__/batch-action-permission.test.ts"
  - id: C-05
    source: "P136 §3"
    rule: "无权限判断优先于状态矩阵判断；tooltip 必须区分两者"
    mapping: { priority: [permission, status_matrix], tooltip: distinct }
    implementation: "frontend/src/components/batch/BatchActionBar.vue"

acceptance_tests:
  - id: AT-136-1
    ref: "__tests__/BatchActionBar.test.ts::disabledWhenLacksPermission"
  - id: AT-136-4
    ref: "__tests__/batch-ops.test.ts::runBlocksWhenLacksPermission"
  - id: AT-136-9
    ref: "__tests__/batch-action-permission.test.ts::allBatchActionsDeclarePermission"
