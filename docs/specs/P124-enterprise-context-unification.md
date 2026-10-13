# SPEC-P124：企业上下文归一 —— 消除「当前工作企业」的三源两义零权威

| 项 | 内容 |
|---|---|
| 状态 | **方案草案，待老丁审核**（根因级改动，含破坏性契约变更，**不得先实施**） |
| 前序 | SPEC-P123（症状级：UX 提示 + store 覆盖条件） |
| 关联 | AGENTS §4.5 第 43 条（豁免理由过宽 / 概念混用）、第 49 条（数据在链路但没接线） |

---

## §0 根因

「**当前工作企业**」这一个概念，在系统里同时存在 **3 个来源、2 种语义、0 个权威**：

| # | 来源 | 实际语义 | 代理会计的值 |
|---|---|---|---|
| 1 | JWT claim `enterpriseId` | **登录瞬间的快照**，切换后永不更新 | `null` |
| 2 | `X-Enterprise-Id` 请求头 | 前端 localStorage 驱动的**每请求覆盖值**（经 `EnterpriseMembershipChecker` 校验） | localStorage 里的值 |
| 3 | `/auth/userinfo.enterpriseId` | `t_user.enterprise_id`，语义是**归属企业** | **恒 `null`**（设计如此，代理跨客户） |

**三者同名 `enterpriseId`，语义却两两不同。**

⇒ 由此派生今天已确认的两个故障：
- **静默弹回**：未选客户时守卫把导航弹回客户列表，菜单照常显示、无任何提示（P123 已修 UX）；
- **覆盖事故**：`/auth/userinfo` 的 `null` 被当作「当前企业为 null」而覆盖有效选择 —— `null !== undefined` 成立。
  ⚠️ 我**未能**在真实环境复现该覆盖（实测注入缺陷后刷新并未丢选择），故它是**潜在**缺陷而非已证故障；但它的成因与上表完全同源，不修则随时可能被下一次重构引爆。

**判据沉淀（§4.5 第 43 条同族）**：当一个字段名在系统里有 **N 个写入源**且语义不一致时，任何单点修补都只是把炸弹换个引信。**根治必须消灭多源，而不是在某一处加判断。**

---

## §1 方案：四处归一

### ① 拆名 —— 消灭「同名不同义」

`/auth/userinfo` 返回拆为两个**语义自明**的字段：

| 新字段 | 语义 | 来源 |
|---|---|---|
| `homeEnterpriseId` | **归属**企业（他属于哪家） | `t_user.enterprise_id` |
| `currentEnterpriseId` | **当前工作**企业（他正在为哪家干活） | 过滤器解析后**实际生效**的值 |

**破坏性**：契约变更。✅ 实测影响面小 —— 前端**无一处**直接消费 `userinfo.enterpriseId`（现有用法均为显式业务入参）。

### ② 换证 —— 把 JWT 确立为唯一权威（核心根治）

`POST /v1/enterprise/switch` 成功后**重签并返回新 token**（`JwtProvider.createToken(..., enterpriseId, ...)` 已支持指定企业号，**无需改签发机制**），前端替换 `huicai_token`。

- `X-Enterprise-Id` 头**降级为过渡兼容**（仍经 `EnterpriseMembershipChecker` 校验，保留多标签页切换能力），并登记废弃计划；
- 前端 localStorage 随之降级为「刷新恢复用的缓存」，**不再是权威**。

**收益**：跨刷新 / 跨标签页一致；后端能真实回答「当前在哪个企业」；切换从「改前端状态」升级为「换证」，语义自洽。

⚠️ **代价（必须决策，见 §4.1）**：无状态 JWT `access-token-expiration = 86400000`（**24 小时**），换证后**旧 token 在过期前仍能访问旧企业**。

### ③ 前置条件显式化 —— 后端明确给出「为什么」

- `/auth/userinfo` 增加 `enterpriseSelectionRequired: boolean`；
- 前端据此渲染**引导态**（而非靠 localStorage 推断）；
- 路由守卫兜底跳转时带 `reason` 参数，前端显示「因为未选择客户」而非空白弹回。

**这才是「禁用控件必须同时回答为什么灰与怎么才能点」的根本做法**（§4.5 第 50 条）：**原因由后端给出，而不是前端猜**。

### ④ 明确排除（§7.7）

- ❌ 不做 `tenant_id` 双列并存债清理（§7.7，另行立项）
- ❌ 不改 RLS / GUC 事务链路（P122 已闭环）
- ❌ 不动 `EnterpriseMembershipChecker` 的授权判定（实测该层是正确的）

---

## §2 输入 / 输出契约（变更点）

### 2.1 `POST /v1/auth/login`（响应新增/更名）
```
token, refreshToken
homeEnterpriseId:    number | null     // 更名，原 enterpriseId
currentEnterpriseId: number | null     // 新增，等于 currentEnterpriseId
enterpriseSelectionRequired: boolean   // 新增
userInfo: { ... }                     // 权限等不变
```

### 2.2 `POST /v1/enterprise/switch`（响应变更）
```
token: string          // 新增：换证后的 access token（含新 enterpriseId）
enterpriseId, enterpriseName, seedDataDone, startPeriod   // 不变
```

### 2.3 `GET /v1/auth/userinfo`
```
homeEnterpriseId, currentEnterpriseId, enterpriseSelectionRequired, permissions, ...  // 前三者新增/更名
```

### 2.4 前端 `auth.store`
| 动作 | 变更 |
|---|---|
| `login` | 消费 `currentEnterpriseId`（不再是 `enterpriseId`） |
| `switchEnterprise` | **用响应的新 token 覆盖 localStorage** |
| `fetchUserInfo` | 不再把「归属」当「当前」；一律以 `currentEnterpriseId` 为准 |
| `logout` | 不变（回归锁） |

---

## §3 状态流转

```
登录 ─> currentEnterpriseId = null ─> enterpriseSelectionRequired = true
          └─[客户端列表点「进入」]─> switch 成功 ─> 换发 token ─> currentEnterpriseId = 2
                                              └─> F5 刷新 ─> 由 JWT 恢复 ─> 仍为 2（不再依赖 localStorage 权威）
                                              └─> 另一标签页 ─> 同一 JWT 语义一致
```

---

## §4 待决策项（**审核时必须逐条裁定**）

### 4.1 旧 token 的存续窗口（② 的代价）

| 选项 | 做法 | 代价 |
|---|---|---|
| **A（推荐）** | 缩短 `access-token-expiration` 至 15~30 分钟 + refresh token 续期 | 需实现/验证 refresh 流程；改动面中等 |
| B | 接受 24h 窗口，换证只保证「新请求用新企业」 | 旧 token 在 24h 内仍可访问旧企业；**换证的安全性收益大打折扣** |
| C | 引入 token 版本号（`t_user` 加列，JWT 带版本，换证即自增） | 需 DDL；最彻底 |

> **判据**：若选 B，则 ② 的收益只剩「一致性」，**换不回安全性**，需如实降级 SPEC 的宣称。

### 4.2 `X-Enterprise-Id` 头的去留

- 一并废止（更干净，但多标签页切换需重新登录）→ **推荐**
- 保留并标注废弃（渐进，风险低）

### 4.3 破坏性契约变更的节奏

`homeEnterpriseId` 更名会破坏任何未登记的外部消费者。建议**前后端同批发布**，或先加新字段、下一版再删旧字段。

---

## §5 BDD 验收

| 编号 | Given | When | Then |
|---|---|---|---|
| AT-124-1 | 会计登录 | 取 userinfo | `currentEnterpriseId=null` 且 `enterpriseSelectionRequired=true`，二者**同时**成立 |
| AT-124-2 | 会计已切到客户 2 | **重发 token 并只带该 token** 调业务接口 | 生效企业 = 2（**不依赖 localStorage / 请求头**） |
| AT-124-3 | 同上 | F5 刷新 | 仍为客户 2，且 `enterpriseSelectionRequired=false` |
| AT-124-4 | 同上 | 另一标签页用同一 token | 同样解析为客户 2（**权威唯一性**） |
| AT-124-5 | 未选客户 | 点任意业务菜单 | 显示「因未选择客户」的原因与入口，**不再空白弹回** |
| AT-124-6 | 恶意构造 `X-Enterprise-Id` 指向未分配客户 | 发请求 | **仍被拒**（`EnterpriseMembershipChecker`，回归锁） |
| AT-124-7 | 企业自有用户（`enterprise_id` 非空） | 登录 | `homeEnterpriseId == currentEnterpriseId`，行为与现状一致（回归锁） |

---

## §6 守卫与反证

| 守卫 | 反证 |
|---|---|
| `EnterpriseContextSemanticsTest`：`userinfo` **必须**返回 `homeEnterpriseId` 与 `currentEnterpriseId` 两个字段，且**不得**再出现裸 `enterpriseId` | 把字段改回 `enterpriseId` ⇒ 红 |
| `SwitchReissuesTokenTest`：切���成功必须返回**不同的 token**，且新 token 解出的 `enterpriseId` 为目标企业 | 返回原 token ⇒ 红 |
| `JwtEnterpriseContextRealDBTest`：只带 JWT（**不带** `X-Enterprise-Id`）也能解析出企业上下文 | 去掉换证逻辑 ⇒ 红 |
| E2E：AT-124-2/3/4 | —— |

⚠️ **必须逐条做反证**：今天已在「E2E 反证不转红」上栽过（断言写成空断言）。

---

## §7 遗留

1. P122 遗留的「MP default 方法缺事务」仍待立项。
2. `tenant_id` 双列并存债（§7.7）不在本 SPEC。
3. P123 已验证的 UX 修复**保留**，但它在本 SPEC 落地后属**兜底**，不再是主要防线。