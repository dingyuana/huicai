# SPEC-P123：代理端会计切换客户后刷新丢失当前企业 + 菜单「点了不跳」无任何提示

| 项 | 内容 |
|---|---|
| 状态 | **草案，待老丁审核** |
| 关联 REQ | 待登记（建议 REQ-2026-141） |
| 关联缺陷 | 用户报障 REQ-2026-140 后续；同族于 AGENTS §4.5 第 50 条 |
| 前序 | SPEC-P121（代理端角色基础权限 + 侧边栏按权限过滤） |

---

## §0 背景与取证（**本节推翻了两条既有前提**）

### 0.1 现象

代理端会计（`agency_role = ACCOUNTANT`）登录后：侧边栏菜单**正常显示**，但点击**不跳转**，URL 始终停在客户列表页。

### 0.2 取证结论（全部为实测，非推断）

| # | 事实 | 取证方式 |
|---|---|---|
| 1 | 会计登录响应 `enterpriseId = null`、`permissions` 有 24 条 | `POST /auth/login`（accountant01）实测 |
| 2 | 会计的 `t_user.enterprise_id` **恒为 NULL**（设计如此：代理用户跨客户） | `SELECT` 实测 |
| 3 | `t_user.agency_role = ACCOUNTANT`，`t_agency_user.agency_role = ACCOUNTANT`，平台角色 `ACCOUNTANT` 已绑 24 个菜单 | SQL 实测 |
| 4 | 前端路由守卫 `router/index.ts:73-78`：`isAgency && !currentEnterpriseId` 时，**除 `/agency/enterprise-list` 外一律弹回该页** | 读码 |
| 5 | `switchEnterprise` **只设 ThreadLocal**，不落库、不重签 JWT | 读 `EnterpriseController:46-108` |
| 6 | `request.ts:18-20` 每次请求都从 localStorage 带 `X-Enterprise-Id` | 读码 |
| 7 | `auth.store:22` 的 `currentEnterpriseId` 本就从 localStorage 初始化 | 读码 |
| 8 | `auth.store:115` 用 `/auth/userinfo` 的 `enterpriseId` **无条件覆盖**本地值 | 读码 |
| 9 | 切换后 `GET /auth/userinfo` 返回 `enterpriseId = null` | 实测 |
| 10 | 由 8+9 ⇒ **一刷新页面，本地有效选择被 null 覆盖 ⇒ 守卫弹回客户列表** | 8+9 推得 |

### 0.3 推翻的前提一：不是「没选中客户」这么简单

第一直觉是「用户没点进入」。但即使点了「进入」（实测切换接口返回 `code:200 / enterpriseId=2`），**只要刷新页面就会再次丢失**（事实 10）。故这是**功能缺陷**，不是操作指引问题。

### 0.4 推翻的前提二：**不要改 `AuthController`**

最初方案是让 `/auth/userinfo` 返回「切换后的企业」。取证后发现：

- 请求头机制（事实 6）已使「切换」在功能上成立，**后端不需要记住选择**；
- `t_user.enterprise_id` 的语义是**归属企业**，而代理会计的「当前客户」是**会话态选择** —— 两者被当成一个，是本缺陷的语义根因（AGENTS §4.5 第 43 条同族）；
- 若要服务端返回「当前企业」，必须新增列（DDL）或重签 JWT —— **扩大范围且语义错误**。

⇒ 裁定：**改前端覆盖逻辑，不动后端**。

### 0.5 P121 的 E2E 盲区（本案未被任何测试发现的原因）

`agency-role-usability.spec.ts` 只断言菜单**可见**（数量 + 标题），其注释明确写着「el-menu 以 `:router=true` 自行跳转 ⇒ 路径清单避开 DOM 读取」。**它从未点击过菜单**，故「可见但不可点」这类缺陷对它是恒绿的。

---

## §1 范围

### 1.1 在范围内

1. `auth.store.ts` 的 `fetchUserInfo`：不得用 `null` 覆盖有效本地选择。
2. 未选企业时，业务类菜单**禁用**并给出可执行出路（不是「点了没反应」）。
3. E2E 新增**点击可跳转**用例，补上 §0.5 盲区。

### 1.2 明确排除（§7.7 不擅自扩大）

- ❌ 不改 `AuthController` / 不加 Flyway 列 / 不重签 JWT。
- ❌ 不改后端守卫与权限判定。
- ❌ 不动其它未提交改动（当前工作区 10 个文件属 P122，另行提交）。

---

## §2 输入 / 输出契约

### 2.1 企业上下文的三个来源及其优先级（新）

| 来源 | 语义 | 优先级 |
|---|---|---|
| `localStorage.huicai_current_enterprise_id` | 当前**工作**企业（会话态选择） | 1（最高） |
| `X-Enterprise-Id` 请求头 | 同上，随请求下发 | 2 |
| `/auth/userinfo.enterpriseId` | **归属**企业（`t_user.enterprise_id`） | 3（**仅在为 null 时不参与覆盖**） |

### 2.2 覆盖规则（改 `fetchUserInfo`）

```
if (info.enterpriseId != null && info.enterpriseId !== currentEnterpriseId)
    currentEnterpriseId.value = info.enterpriseId
// 为 null 时：保留本地已有选择（可能来自 switchEnterprise）
```

⚠️ 不用 `!== undefined`（`null !== undefined` 为真，正是缺陷本身）。
⚠️ 不用 `||`（企业号恒为正数，`||` 语义含糊，判据：兜底常量须与同类写法一致，见 §4.5 第 53 条）。

---

## §3 状态流转

```
登录 ──> 未选企业 ──[点击业务菜单]──> 禁用 + 提示「请先在客户列表选择客户」
         │
         └──[客户列表点「进入」]──> 已选企业 ──> 菜单可点 ──> 正常跳转
                                        │
                                        └──[F5 刷新]──> 仍为已选企业（不再丢失）
```

---

## §4 异常处理

| 场景 | 期望 |
|---|---|
| localStorage 有值、userinfo 返回 null | **保留本地值**（本 SPEC 主修点） |
| localStorage 有值、userinfo 返回**不同**非空值 | 以 userinfo 为准（服务端归属优先，防串号） |
| localStorage 为空、userinfo 返回非空 | 采用 userinfo |
| 退出登录 | 清空 localStorage（已有 `reset()`，不新增） |
| 菜单禁用态点击 | **不得静默**：须有 tooltip/提示指明下一步 |

---

## §5 BDD 验收场景

| 编号 | Given | When | Then |
|---|---|---|---|
| AT-123-1 | 会计已登录且 `enterpriseId` 为 null | 侧边栏渲染 | 业务菜单项为**禁用**态，且带「请先选择客户」提示 |
| AT-123-2 | 同上 | 点击业务菜单 | **URL 不变**，且**出现明确提示**（不得静默） |
| AT-123-3 | 会计已在客户列表点「进入」 | 刷新页面 | `currentEnterpriseId` **仍为所选企业**，不被置空 |
| AT-123-4 | 会计已选中企业 | 点击「期初建账」 | **URL 变为 `/finance/beginning-balance`**（补 P121 盲区） |
| AT-123-5 | 企业用户（`enterprise_id` 非空） | 登录 | 行为与现状一致（**回归锁**） |
| AT-123-6 | 任意角色 | 退出登录 | localStorage 中企业键被清除（**回归锁**） |

---

## §6 守卫与反证

| 守卫 | 类型 | 反证 | 反证是否成立 |
|---|---|---|---|
| `agency-enterprise-selection.test.ts`（store 行为） | 行为（驱动 store，非源码匹配） | 改回 `!== undefined` ⇒ 转红 | ✅ 成立 |
| 同上（对照组） | 对照组自证装置有效 | 撤 `needsEnterpriseSelection` ⇒ 转红 | ✅ 成立 |
| E2E AT-123-1/2（置灰 + 出路） | 行为 | 撤 `.menu-locked` / 提示条 ⇒ 转红 | ✅ 成立 |
| E2E AT-123-4（点击可跳转） | 行为 | — | 补 P121 盲区，无需反证 |
| E2E AT-123-3（刷新后仍可导航） | 行为 | 改回缺陷写法 | 🔴 **未转红**，见 §7.2 |

⚠️ 首版曾把 AT-123-3 写成反证行，实际是**空断言**（对缺陷恒绿）。
现已改为「预期行为回归锁」并如实标注 —— 判据：§4.5 第 41/45 条，
**没通过反证的守卫不得计入验收证据**。

---

## §7 遗留与已知局限

### 7.1 已验证有效（保留）

1. **UX**：未选客户时侧边栏置灰 + 提示原因与出路（`.menu-lock-hint`），且
   「客户列表」保持可点（否则用户彻底死锁）。E2E AT-123-1/2 稳定覆盖。
2. **store 覆盖条件**：`info.enterpriseId != null` 才覆盖。`null !== undefined`
   为真，原写法确为错误。守卫 4/4，反证成立（改回 `!= null` 移除即转红）。

### 7.2 🔴 未获证实（必须如实登记，不得宣称已修）

- **端到端反证未转红**：把覆盖条件改回缺陷写法（`!== undefined`）后，实测
  刷新后 `huicai_current_enterprise_id` 仍为 2、菜单仍可点、E2E 仍全绿。
  ⇒ 该缺陷在**当前环境未能复现**，store 改动**未被证明修掉了真实症状**。
- 已排除的解释：不是「token 未持久化」（`huicai_token` 确实持久化），
  也不是「userinfo 不含该字段」（实测该键存在且为 `null`）。
- 仍未解释的疑点：调用链上似有其它机制保住了选择，**未取证，不做断言**。
- ⇒ E2E AT-123-3 是「预期行为」回归锁，**不是** store 改动的反证。

### 7.3 其余遗留

1. **`IService` default 方法缺事务**（P122 遗留）：仍未立项。
2. **Playwright 浏览器**：本机原未安装，`npx playwright install chromium`
   （114MB）后 E2E 方可运行。
3. **E2E 稳定性**：修好 `expandAllGroups`（须跳过已展开分组，否则会把分组**点关**）
   后连跑 3 轮全绿，样本不足以断言长期稳定。
4. **P124 关联**：「归属企业 vs 当前工作企业」的语义拆分已升级为 SPEC-P124 的
   核心议题；其落地后，本 SPEC 的 store 改动将成为兜底而非主防线。
5. ~~**禁用态是否更优**~~ —— **已裁定（老丁，2026-10-13）**：采用「**置灰 + 提示出路**」——
   业务菜单置灰，用提示条说明**为什么灰**与**怎么才能点**，并给**可点击的跳转入口**
   直达 `/agency/enterprise-list`。判据：§4.5 第 50 条「禁用一个控件时必须同时回答
   『为什么灰』与『怎么才能点』」，只置灰不给出路，等于把「点了没反应」换成
   「点不动且不解释」，后者更难定位。