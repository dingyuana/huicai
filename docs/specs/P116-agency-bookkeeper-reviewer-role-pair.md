# SPEC-P116 — 代理端记账/审核角色对与数据范围收敛

> **状态**：📋 **待审核（SPEC 草案）**
> **关联需求**：承接 REQ-2026-142（S-26 状态失真修正）的代码层落地部分
> **日期**：2026-10-10
> **触发**：老丁要求「代理公司中需要有公司管理人员对全部客户账户的审核管理监督」，
> 并裁定（2026-10-10）监督模型取**账无忧式「记账员/审核员命名角色对」**
> **基线**：SPEC-P115（`agency_role` 单一事实源收敛，Green 待提交）

---

## §0 取证：缺口比 S-26 修正文档里记的更多

S-26 V2.1 已记录两条未落地（`ACCOUNTANT` 数据范围、`REVIEWER` 审核能力）。
本轮逐条追到**审核入口本身**，又查出两个同族缺陷：

| # | 缺陷 | 证据 | S-26 V2.1 是否已记 |
|---|---|---|---|
| 1 | `ACCOUNTANT`/`ASSISTANT` 数据范围未收敛 | `ServiceProgressController:53` 只按 `getCurrentAgencyId()` 聚合，无 `agency_role` 过滤 | ✅ 已记 |
| 2 | `REVIEWER` 无审核入口可用 | `BatchAuditServiceImpl:29` unimplemented；凭证/单据审核端点无角色限制 | ✅ 已记（但**根因描述不准**，见下） |
| 3 | 🔴 **凭证审核端点完全无鉴权** | `VoucherController:84-86` `POST /{id}/audit` 无 `@PreAuthorize`、无服务层角色校验，直接 `SecurityUtils.getCurrentUserId()` | ❌ **未记** |
| 4 | 🔴 **凭证层缺制审分离** | `VoucherServiceImpl.audit:289-295` 无自审拦截；而**业务单据层已有**（`BusinessDocServiceImpl:337-339`「制单人不能审核自己提交的单据」） | ❌ **未记** |

### §0.1 缺陷 3/4 的严重度

**缺陷 3 比缺陷 1 严重**。缺陷 1 是「看得太多」（内部权限过大）；
缺陷 3 是「**任何登录用户都能审任何凭证**」—— 审核是财务内控的核心动作，
铁律 #1（人是唯一审核主体）要求审核必须由**具备审核资格的人**主动触发。
当前实现里，"谁能审" 等于 "谁能登录"。

**缺陷 4 是本次最容易修的**：同表不同层 —— 业务单据层已实现制审分离，
凭证层没有。属**一致性缺口**，不是新功能。

### §0.2 已有的可复用资产（不要重建）

| 资产 | 位置 | 用途 |
|---|---|---|
| 业务单据制审分离 | `BusinessDocServiceImpl.java:337-339` | **凭证层照抄这段**，不要另创机制 |
| per-client 授权表 | `t_agency_user_enterprise`（`agency_user_id` → `enterprise_id`） | 数据范围的事实来源，**不新建表** |
| 四角色命名 | `t_agency_user.agency_role`（`AGENCY_ADMIN`/`ACCOUNTANT`/`REVIEWER`/`ASSISTANT`） | **不新增角色**，语义早已存在 |
| `agency_role` 单一事实源 | SPEC-P115（已 Green） | 本 SPEC 的前提，必须先合入 |
| 权限码机制 | `@PreAuthorize` + `t_menu.permission_code`（`PeriodCloseController:52` 已有先例） | 审核端点授权用既有机制，不自造 |

⇒ **本 SPEC 不新增角色、不新建表、不自造鉴权机制**，只把 S-26 早已声明的语义接到代码上。

---

## §1 范围

**做**：
1. **数据范围统一解析**：新增唯一一处「当前用户可见的客户企业集合」解析逻辑，
   按 `agency_role` 分流（`AGENCY_ADMIN`/`REVIEWER`/`SUPER_ADMIN` → 本代理全部客户；
   `ACCOUNTANT`/`ASSISTANT` → 仅 `t_agency_user_enterprise` 分配给自己的）；
   **代理端读端点统一改为走它**。
2. **审核端点授权**：凭证审核 / 批量审核 / 单据审批补角色校验（`REVIEWER`/`AGENCY_ADMIN`/`SUPER_ADMIN`）。
3. **凭证层补制审分离**：`VoucherServiceImpl.audit`/`batchAudit` 补自审拦截，
   与 `BusinessDocServiceImpl:337-339` 措辞与行为一致。

**不做**（登记遗留）：
- **结账/反结账的审核链**（P87 已有，另行评估是否纳入同一审核域）
- **`REVIEWER` 的「只读」细化**（当前连可写都没约束，先把授权补上）
- **`ASSISTANT` 的「仅录入」**（§1.2 声明了「仅录入权限」，实现复杂度高于本轮）
- **审批流引擎**（可配置节点、会签等）—— 账无忧的可配流程节点属远期
- **审计报表**（谁审了哪个客户的哪些单）—— 由 P103 审计追踪承接，本 SPEC 只保证字段可追

---

## §2 方案选型

### 2.1 为什么「不新增角色」

账无忧的范式是**记账员/审核员命名角色对**，本项目这两个角色**早已存在**：
`ACCOUNTANT`（记账员）/ `REVIEWER`（审核员）。缺的不是角色，是**授权没接到代码上**。

新增角色（如 `AGENCY_AUDITOR`）会造成第四套并行角色体系 —— 本项目已经有
`t_role`（RBAC）+ `t_user.agency_role` + `t_agency_user.agency_role` 三套，
再加一套是往错误方向走。**故本 SPEC 只做「接线」，不加概念。**

### 2.2 为什么必须「统一解析」而不是逐端点加过滤

S-26 失败的根本原因就是**逐端点各写一遍、写漏了** —— 现在 5 个代理端读端点
（`ServiceProgressController`×3、`AgencySummaryController`×2、`ClientController`×2）
全都没有收窄。

若本 SPEC 仍逐端点加，下一个新端点还会漏。故必须有一处**唯一**的解析逻辑，
且新端点忘记调用它时应能被守卫发现（见 §5 AT-116-6）。

### 2.3 制审分离为什么必须同时补

若只补「谁能审」而不补「不能自审」，则 `REVIEWER` 同时是记账员时可自审
（角色对的意义正是防止这个）。**两个条款是一个完整性单元，拆开都会退化成假内控。**

---

## §3 输入契约 / 输出契约 / 异常处理

### 输入契约

**数据范围解析器**（新增，唯一入口）：
```
当前登录用户 → agency_role + t_user.id
  AGENCY_ADMIN / SUPER_ADMIN / REVIEWER → 本代理全部客户企业 id
  ACCOUNTANT  / ASSISTANT               → t_agency_user_enterprise 分配给自己的
  无 agency_role（ENTERPRISE 类型）      → 不适用（企业用户不走代理端范围）
```

**审核端点**：`REVIEWER` / `AGENCY_ADMIN` / `SUPER_ADMIN` 可审；
`ACCOUNTANT` / `ASSISTANT` 一律拒绝（含**批量**路径）。

**凭证审核**：审核人 ≠ 制单人，否则拒绝（措辞与 `BusinessDocServiceImpl:339` 一致）。

### 输出契约
- 越权审核 ⇒ `BusinessException.forbidden`，消息含角色与所需角色（铁律 #14）
- 自审 ⇒ `BusinessException.badRequest("制单人不能审核自己提交的凭证")`
- 数据范围收窄 ⇒ 查询只返回可见客户；**不得**因为「看不到」而静默返回空
  （无法区分「无数据」与「无权限」是 §4.3 第 10 条静默失败形态）

### 异常处理
- 解析器取不到 `agency_role` ⇒ fail-loud 抛 500 并指明字段（同 SPEC-P115 `requireSameAgency` 口径）
- `SUPER_ADMIN` 豁免归属校验，但**不豁免制审分离**（超管也不能审自己的单）

---

## §4 验收标准（BDD）

| 编号 | Given | When | Then |
|---|---|---|---|
| AT-116-1 | `ACCOUNTANT` 被分配客户 A、B | 查服务进度/汇总 | 只见 A、B，不见同代理其它客户 |
| AT-116-2 | `REVIEWER` 无任何分配 | 查服务进度/汇总 | 见本代理**全部**客户（S-26 §1.2 第 78 行） |
| AT-116-3 | `AGENCY_ADMIN` | 同上 | 见全部客户 |
| AT-116-4 | `ACCOUNTANT` 调凭证审核 | — | 403，消息含所需角色 |
| AT-116-5 | `REVIEWER` 审他人制的凭证 | — | 成功 |
| AT-116-6 | `REVIEWER` 审**自己**制的凭证 | — | 400「制单人不能审核自己提交的凭证」 |
| AT-116-7 | 反证：把解析器分流逻辑删掉 | 跑守卫 | 守卫转红（证明非恒绿） |
| AT-116-8 | 反证：凭证层去掉自审拦截 | 跑守卫 | 守卫转红 |

---

## §5 门禁与反证要求

1. **AT-116-7/8 强制**：本 SPEC 守的正是「S-26 声明过但没人验」的条款，
   若守卫自身不能对删逻辑敏感，就是用一个新的恒绿换一个旧的恒绿。
2. **新增端点漏用解析器必须能被发现**：守卫应断言「代理端 `@RestController`
   的读方法不得绕过解析器」（结构级，同 `OutParamEntityStructureTest` 思路）。
3. **制审分离必须三条路径都验**：单条审核、批量审核、驳回（`reject` 同属审核动作）。
4. L1/L2 全量回归 —— 数据范围改动触及所有代理端读路径，**非定向**。

---

## §6 Plan（微循环）

| 步 | 内容 | 交付物 |
|---|---|---|
| 1 | 写守卫测试（先红）：权限矩阵 + 数据范围 + 凭证制审分离 | `AgencyRoleDataScopeAndAuditTest` |
| 2 | 反证 AT-116-7/8（确认守卫能红） | 反证记录 |
| 3 | Green：新增数据范围解析器；5+2 个代理端读端点接入 | 新类 + 改 7 处 |
| 4 | Green：凭证/单据审核端点补角色校验 | 改 Controller/Service |
| 5 | Green：`VoucherServiceImpl.audit`/`batchAudit` 补自审拦截 | 改 Service |
| 6 | 全量 L1/L2 + 5 静态门禁 + 回写 S-26/登记册 | 文档 |

---

## §7 已知遗留

1. `ASSISTANT`「仅录入权限」未实现（§1.2 已声明）
2. `REVIEWER`「只读」未细化 —— 当前已验证其可审，但未禁其写
3. 结账/反结账审核链是否纳入本审核域，待评估（P87）
4. 审批流可配置节点（账无忧式 workflow）属远期
5. `ACCOUNTANT` 数据范围收敛后，`AgencySummaryService` 的统计口径需同步确认
   （它跨会计汇总，收敛后经理/审核员看的仍是全量，会计看自己的 —— 汇总分母不同）

---

## §8 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V0.1 | 2026-10-10 | 草案：取证（新增缺陷 3/4）、方案选型（不新增角色/不自建机制）、验收、Plan、遗留 |
