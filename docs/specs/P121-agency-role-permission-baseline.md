# SPEC-P121：代理端角色的基础权限设定 + 多角色登录 E2E

| 项 | 内容 |
|---|---|
| **状态** | 📋 实施中（老丁已批准方案） |
| **关联 REQ** | REQ-2026-144 |
| **前置** | SPEC-P115（代理角色单一事实源）、SPEC-P120（代理端越权） |
| **严重度** | 🟠 功能不可用（代理端角色登录后几乎无法使用系统） |

---

## §1 缺陷现象

以 `liu`（`AGENCY` / `agency_role=ACCOUNTANT`）登录后，**点击任何菜单都无效**。

### 1.1 实测的三层断裂

```
侧边栏：58 个菜单项【全部无条件渲染】—— 只按 userType 分组，无权限过滤
   ↓ 点「凭证管理」
路由守卫：meta.permission = 'voucher:list'
   ↓ hasPermission() → permissions.value.includes('voucher:list')
后端数据：liu 在 t_user_role 里【0 条记录】⇒ permissions = []
   ↓ 恒 false
守卫：return next('/403')   ← 用户感知为「点了没反应」
```

### 1.2 数据事实（实测）

| 用户 | user_type | agency_role | t_user_role 条数 |
|---|---|---|---|
| admin | SUPER_ADMIN | — | 1（ADMIN，44 菜单） |
| accountant01 | AGENCY | ACCOUNTANT | **0** |
| reviewer01 | AGENCY | REVIEWER | **0** |
| assistant01 | AGENCY | ASSISTANT | **0** |
| 王会计 | AGENCY | ACCOUNTANT | **0** |
| **liu** | AGENCY | ACCOUNTANT | **0** |

⇒ **5 个代理端用户全部无平台角色**，全部点不动菜单。

### 1.3 关键判断：这是「执行缺失」，不是「设计缺失」

`t_role` 中 `ACCOUNTANT` 角色**早已配好 24 个菜单权限**（`is_system=true`），覆盖凭证/账簿/报表/资产 —— 会计要的东西**设计侧早就有**，只是没有任何绑定把它接到人身上。

## §2 Q2 的答案：E2E 从未覆盖代理端角色（覆盖漏洞）

| 项 | 实测 |
|---|---|
| E2E 基建 | ✅ 存在（Playwright + `nightly-e2e-full.yml` + 29 个 spec） |
| 使用账号 | `admin` / `zhang` / `zhangsan` / `li` / `agent01` |
| **其中库内有效的** | **仅 `admin`（SUPER_ADMIN）** |
| `zhang`/`zhangsan`/`li`/`agent01` | ❌ **库内不存在** |
| 代理端角色 E2E | ❌ **一个都没有** |

⇒ **所有 E2E 都以超管身份跑**，恰好绕开了出问题的角色。这解释了「本地全绿 + CI 全绿，但会计登录就是不能用」。

⚠️ 这是 §4.3 第 9 条的典型形态：**「测试通过」≠「功能可用」，当测试只覆盖了一个角色时，其余角色等于没测。**

## §3 方案设计（老丁已批准）

### 3.1 口径选择：**独立 + 一次性迁移**（不做级联同步）

| 维度 | `agency_role`（`t_agency_user`） | 平台角色（`t_user_role`） |
|---|---|---|
| 语义 | 「在代理公司里是什么职位」 | 「能打开哪些页面」 |
| 唯一性 | P115 已收敛为**单一事实源** | 独立维护 |
| 关系 | **正交**，不强制同步 | |

**为什么不做级联同步**：
- 两者本是正交维度（一个是职位，一个是页面访问权），强行同步会让 P115 的收敛白做；
- 级联意味着改 `agency_role` 时自动改 `t_user_role`，等于引入**第二处角色写入方** —— 正是 P115 刚刚消除的分叉。

**「后期可调整」如何落地**：无需新增任何代码。管理员在既有 `/system/user` 页面可给单个用户增删平台角色；角色本身的菜单在既有 `/system/role` 页面配置。

### 3.2 映射表（一次性迁移，不硬编码在业务逻辑中）

| `agency_role` | → 平台角色 | 菜单数 | 理由 |
|---|---|---|---|
| `ACCOUNTANT` | `ACCOUNTANT`(id=3) | 24 | 设计侧已配好，直接对接 |
| `REVIEWER` | `FINANCE_MGR`(id=2) | 42 | 审核需跨账套复核视野 |
| `ASSISTANT` | `CASHIER`(id=4) | 8 | 助理做票据/银行辅助 |
| `AGENCY_ADMIN` | `ADMIN`(id=1) | 44 | 经理需全量 |

⚠️ 映射写在 **Flyway 迁移**里（一次性数据修复），**不进业务代码** —— 避免成为第二套需要维护的角色逻辑。

## §4 实施内容

1. **`V179`**：按 `agency_role` 为代理端用户绑定平台角色（`INSERT ... SELECT ... WHERE NOT EXISTS`，幂等；受 `uq_user_role` 约束保护）
2. **前端侧边栏**：菜单项按 `permission` 过滤，与路由守卫**同一套口径**（消除「看得见却点不开」）
3. **E2E**：新增多角色登录 E2E —— 用真实代理端账号登录 → 逐个点击其可见菜单 → 断言不出现 `/403`

## §5 已知局限

1. **代理端用户仍看不到其它代理端同事** —— `/system/user` 按 `enterprise_id` 过滤，代理端用户 `enterprise_id` 为 NULL。这是**两套用户模型的正常边界**，代理端同事列表在 `/agency/accountant-list`（需 `AGENCY_ADMIN`）。⚠️ 因此 `ACCOUNTANT`/`REVIEWER`/`ASSISTANT` 目前**没有查看同事的入口** —— 是否需要属产品决策。
2. **`t_menu` 存在重名记录** —— 观察到「业务单据」同时出现在 ACCOUNTANT 的 24 项与 ADMIN 独有项中，说明 `t_menu` 有**两条同名不同 id** 的记录。与本 SPEC 无关，但会导致菜单显示重复，登记为待办。

## §6 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-10-12 | 首版。取证三层断裂 + E2E 覆盖漏洞；定映射表与独立口径 |