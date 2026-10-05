# P106 SPEC — 多账套（企业级收口 + 账簿级立项）

> **版本**：V1.0 | **最后修改**：2026-10-05 | **作者**：opencode
> **编号**：HUICAI-SPC-P106 | 优先级：**P1** | 状态：📋 **待审核（仅 SPEC，本轮不改任何 Java/SQL 代码）**
> **来源**：P101 商用化差距总纲 → P106「内控深度」中的「多账套」子项（REQ-2026-133）
> **关联需求**：**REQ-2026-134**（本 SPEC 新登记） | **前置**：RLS 三层已落地（PR #26/#27）、DTO 入参隔离已归零（PR #28~#30）
> **test_ref**（拟）：`EnterpriseIsolationRealDBTest`、`SwitchAuthorizationRealDBTest`、`AccountSetLifecycleTest`

---

## 0. 事实基线（**全部实测，非推演**）

立项前必须先回答「多账套到底缺什么」。逐条实测结论如下：

| # | 事实 | 证据 |
|---|---|---|
| F1 | **隔离维度只有 `enterprise_id`，没有账套/账簿维度** | `grep -riE 'book_set\|账套\|套账'` 全仓仅命中 1 条**注释**（`SubjectBalanceServiceImpl:495` 提到「新账套首期」），无实体、无表、无配置项 |
| F2 | 83 张 `t_` 表中：**73 张有 `enterprise_id`**、**10 张仍用 `tenant_id`**、**70 张已开 RLS** | `information_schema.columns` / `pg_class.relrowsecurity` 统计 |
| F3 | 唯一约束**已按企业分段** | `t_subject` `uq_subject_code_ent (code, enterprise_id)`；`t_period` `uq_period_code_ent (period_code, enterprise_id)`；`t_bank_account` `uq_bank_account_no_enterprise (account_no, enterprise_id)` |
| F4 | **「企业 = 账套」其实已经落地，且有服务端强制边界** | 前端 `layouts/components/EnterpriseSwitcher.vue` + `X-Enterprise-Id` 头；`JwtAuthenticationFilter:84-107` 调 `EnterpriseMembershipChecker.isMember`（三源并集：直属 ∪ 代理授权 ∪ SUPER_ADMIN），不通过即 **403**，并写审计 `auditService.recordEnterpriseSwitch` |
| F5 | `t_enterprise` 已有**部分账套档案字段** | `mode`（默认 `SME`）、`start_period`、`seed_data_done`、`status`（默认 `PENDING`）、`agency_id`、`version` |
| F6 | **仍有 2 处硬编码单企业** | `OutputInvoiceStateMachineServiceImpl:291` `enterpriseId = 1L`；`ReconciliationToleranceServiceImpl:29` `DEFAULT_ENTERPRISE_ID = 1L` |
| F7 | 一个普通企业用户**挂不了第二家企业** | `t_user.enterprise_id` 单归属 + `uq_username` 为**全局**唯一（非 `UNIQUE(username, enterprise_id)`） |

### 0.1 10 张「只有 `tenant_id`、且 RLS 未开」的表（**实测清单，定性待定**）

`t_agency`、`t_agency_user`、`t_audit_log`、`t_dept`、`t_enterprise`、`t_menu`、`t_role`、`t_role_menu`、`t_sys_config`、`t_user_role`
⇒ 全部 `relrowsecurity = false`。

其中 `t_menu` / `t_role` / `t_dept` / `t_sys_config` 是**企业内主数据**，`t_audit_log` 是**审计数据** —— 多企业并行时它们是「故意全局共享」（如菜单模板）还是「应按企业隔离却漏了」，属于本 SPEC 必须逐表取证的围（§8 批次 1）。

> ⚠️ 特别提示：`t_agency_enterprise` **不在**此清单（它有 `enterprise_id`），它与 `t_agency`/`t_agency_user` 的 `tenant_id` 构成代理侧的**两套并行租户列**，是 §1.2 L1-1 取证时最容易漏掉的一处。

## 1. 需求边界：把「多账套」拆成两级

「多账套」在不同厂商语境里指两件不同的事，成本差一个数量级。本 SPEC 明确分层：

| 层级 | 定义 | 现状（实测） | 本轮定位 |
|---|---|---|---|
| **L1 企业级多账套** | 一个用户 / 一个代理同时使用**多个企业各自的独立账** | **已基本落地**（F2/F3/F4/F5） | ✅ **只做收口**，清 4 项遗留（§1.2） |
| **L2 账簿级多账套** | **同一个企业内**并存多套账（不同会计制度 / 不同启用期间 / 不同凭证簿） | **完全没有**（F1） | 📋 **只立项不实现**，等客户明确需求再动 |

### 1.1 L1 与 L2 的两种数据模型（决策点）

| 维度 | 模型 A：**复用 `enterprise_id`**（一企业一套账） | 模型 B：**新增 `book_id`**（一企业多套账） |
|---|---|---|
| 表结构改动 | **0 张表** | 73 张表 + 唯一约束 + RLS 谓词 + 全部 Mapper/Service/报表 |
| 隔离机制 | 已有的 `enterprise_id` + 70 张 RLS 直接复用 | 需 `enterprise_id + book_id` 复合隔离，且 `TenantRlsInitializer` 需再注入一个 GUC（`app.book_id`），并重做 V167 同款谓词硬化 |
| 迁移风险 | 无数据迁移 | 需回填 `book_id`、双写过渡期、历史数据归属决策 |
| 何时必须 | 「客户/企业之间隔离」—— **当前商用即此形态** | 「同一法人多套账（一般纳税人+小规模、境内+境外）」—— 目前**无客户实例** |
| 结论 | ✅ **立即可用** | ⚠️ **本轮仅立项** |

**推荐路线**：商用化第一阶段只交付 **L1 收口**；L2 保持立项状态，触发条件写进 §9。

### 1.2 L1 收口的 4 项遗留（本 SPEC 的全部实施范围）

| 编号 | 遗留 | 依据 | 优先级 |
|---|---|---|---|
| **L1-1** | 7~10 张 `tenant_id` 表逐表定性：哪些是**故意全局共享**（如 `t_menu` 模板），哪些是**应按企业隔离却漏了**（`t_audit_log`/`t_dept`/`t_sys_config` 嫌疑最大） | F2 | P1 |
| **L1-2** | 清掉 2 处 `enterpriseId = 1L` 硬编码 | F6 | P1 |
| **L1-3** | 明确「一个用户能否挂多家企业」产品决策，并落地（当前只能靠代理授权链） | F7 | P2 |
| **L1-4** | `t_user.uq_username` 全局唯一 vs `(username, enterprise_id)` 的取舍 | F7 | P2 |

## 2. 竞品对标（铁律 #15）

| 竞品 | 多账套形态 | 隔离维度 | 与本项目差异 |
|---|---|---|---|
| 用友（畅捷通/好会计） | **一账套一账簿**，多账套并列切换，各自独立科目体系与结账 | `账套`（含公司/账套/年度三层） | 本项目**无年度层**（`t_period` 按年+月，但年度不是独立切换维度）；好会计的账套切换是**登录后选择**而非登录中切换 |
| 金蝶（云星辰/精斗） | 多账套，账套内可切换**账簿/账套**，支持「同组织多账套」 | 组织 + 账套 | 金蝶的账套**共享组织主数据**，本项目是「企业=账套」，粒度更粗 |
| SAP S/4HANA | `Company Code`（公司代码）+ `Fiscal Year` + `Ledger`（账簿）三段式 | 公司代码 + 账簿 | SAP 的 `Ledger` 概念**正是本项目的 L2**；其成本也印证 L2 是重量级变更 |
| QuickBooks | **不支持多账套**，单实体多文件（多 `Company` 文件） | 文件 | 反向证据：轻量 SaaS **刻意不做**多账套 ⇒ 多账套不是商用化必需项 |

**对标结论（写进 SPEC 决策依据）**：
1. 主流厂商把「多账套」做成**并列切换 + 各自独立科目/期间/结账**，与本项目 L1 形态一致；
2. 「同一企业多套账」（SAP Ledger / 金蝶多账簿）确有真实需求，但**三家轻量 SaaS 中只有两家做**，QuickBooks 完全不做 ⇒ **本项目在无客户实例前不投 L2**；
3. 本项目**落后于主流的一项**是**年度层**：主流厂商的切换是「账套 → 年度 → 账簿」三级，本项目只有「企业 → 期间」，跨年切换靠 `t_period` 查询实现，**建议在 L1 收口后评估是否补「默认账期/当前年度」档案字段**（`t_enterprise.start_period` 已有，可作起点）。

## 3. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| `X-Enterprise-Id` | HTTP 头（可选） | 与 JWT 内 `enterpriseId` 不同则触发成员校验；不通过即 **403**，**不得**降级为「忽略并按 JWT 处理」（fail-closed，铁律同 P102） |
| 当前用户 | `t_user` + `t_agency_user` + `t_agency_user_enterprise` | 三源并集（直属 ∪ 代理授权 ∪ SUPER_ADMIN），见 `EnterpriseMembershipChecker` |
| 账套档案 | `t_enterprise`（`status` / `mode` / `start_period` / `seed_data_done` / `version`） | `status` 取值域需查证（默认 `PENDING`）；**非 ACTIVE 的账套不得产生业务数据**（本轮只加校验，不改已有数据） |
| 业务上下文 | `EnterpriseContextHolder` | 事务内 `SET LOCAL app.enterprise_id`；空串/NULL 两种退化形态已被 V167 硬化 |

## 4. 输出契约

| 输出 | 验收标准 |
|---|---|
| 切换鉴权 | 非成员切换 **403** + 审计落库（**已实现**，本轮补回归锁） |
| 跨企业读写隔离 | 切到 A 企业后查询返回 A 的行；写 A 的行在 B 的上下文下 **0 行可见 / 写入被拒**（**已实现**，补真库回归） |
| 主数据隔离定性 | §1.2 L1-1 的每张表给出结论并**落到 DDL 或文档**（不结论不算完成） |
| 硬编码清零 | F6 的 2 处改为读上下文，取不到上下文时抛 `BusinessException`（铁律 #14），**不得静默用 1** |
| 唯一约束决策 | L1-3/L1-4 给出结论；若改 `uq_username` 必须走 Flyway 且提供回填脚本 |

## 5. 状态流转

```
t_enterprise.status
   PENDING ──(种子数据完成 seed_data_done=true)──▶ ACTIVE ──▶ CLOSED
      │                                             │            │
      └──────────── 禁止产生业务数据 ──────────────┴────────────┘
   （status 的完整允许集须 pg_get_constraintdef 查证；默认值为 'PENDING'）

切换态（每请求）
   JWT.enterpriseId ──请求头 X-Enterprise-Id──▶ 成员校验 ──通过──▶ EnterpriseContextHolder.set(E)
                                       └────不通过──▶ 403（不区分「不存在」与「无权限」）
```

## 6. 异常处理

| 场景 | 处理 | 错误码/行为 |
|---|---|---|
| 切换到非成员企业 | 403，且**不区分「企业不存在」与「无权限」**（避免企业存在性泄露，`JwtAuthenticationFilter:83` 现有约定） | 403 |
| 请求头非法（非数字） | 按「未提供」处理，**不得**回退到「放行任意企业」 | 用 JWT 内企业 |
| 账套非 ACTIVE 时写业务数据 | 抛 `BusinessException` | 4xx |
| 上下文取不到（定时任务/批处理） | 抛 `BusinessException`，**禁止**回落 `enterpriseId=1`（F6 的根因） | 5xx/4xx |
| 跨企业唯一约束冲突 | 依赖 DB 唯一约束（已按企业分段）+ 友好错误信息，不靠应用层预检 | 409/业务异常 |

## 7. BDD 验收标准（每个场景对应一个 `@Test`）

```gherkin
# 批次 1：主数据表定性 + 硬编码清零
Scenario: 非 ACTIVE 账套不得产生业务数据
  Given 企业 E 的 status = 'PENDING'
  When  切换到 E 后创建一张费用报销单
  Then  请求被拒绝，且错误信息指明「账套未启用」

Scenario: 无企业上下文的批处理必须失败而非写进企业 1
  Given 某 Service 方法内 EnterpriseContextHolder 为空
  When  调用销项发票状态机 / 应收核销容忍度计算
  Then  抛 BusinessException，且 t_output_invoice 不出现 enterprise_id = 1 的新行

Scenario: 审计日志按企业隔离（或明确判定为全局共享并写进文档）
  Given 企业 A 与 B 都产生了业务操作
  When  以 A 的上下文查询 t_audit_log
  Then  只返回 A 的行；若结论是「全局共享」，则本场景改为断言文档结论存在

# 批次 2：切换鉴权回归锁（补真库用例）
Scenario: 成员切换成功
  Given 用户 U 通过 t_user.enterprise_id 直属企业 E
  When  带 X-Enterprise-Id: E 发请求
  Then  200，且查询只返回 E 的数据

Scenario: 非成员切换被拒
  Given 用户 U 与企业 E 无任何关联
  When  带 X-Enterprise-Id: E 发请求
  Then  403，且 t_audit_log（或切换审计表）无成功记录

Scenario: 切换到 SUPERADMIN 不设上下文（沿用现有约定）
  Given user_type = 'SUPER_ADMIN'
  When  不带 X-Enterprise-Id 发请求
  Then  不设 EnterpriseContextHolder（保留超管可运维能力）
```

## 8. 实施分批（每批独立可回滚）

| 批次 | 内容 | 风险 | 门禁要求 |
|---|---|---|---|
| **1** | 10 张 `tenant_id` 表逐表定性 + 2 处硬编码清零 | 中（动 DDL 与状态机） | 三方对照审计（PG ↔ Entity ↔ 业务代码）；DDL 走 Flyway |
| **2** | 切换鉴权与跨企业隔离的**真库回归锁**（当前只有单测） | 低 | L2 全绿 |
| **3** | L1-3 / L1-4 产品决策落地（可能含 `uq_username` 迁移） | 高（影响登录） | 需老丁单独审核 + 回填脚本 |

## 9. 风险与明确不做（Out of scope）

**不做**：
- L2 账簿级多账套（`book_id`）的任何代码/DDL —— 触发条件：①出现明确客户需求（同一法人多套账）；②或同时管理 ≥3 个企业的代理客户提出报表合并诉求。
- 不改 `t_enterprise` 的既有字段语义；不引入 `fiscal_year` 独立切换层（**先评估再立项**，见 §2 对标结论 3）。
- 不动 70 张 RLS 策略的形状（V167 谓词保持不变）。

**风险**：
| 风险 | 缓解 |
|---|---|
| 「全局共享」误判为「应隔离」，导致代理端菜单/角色配置跨企业失效 | 每张表必须**先取证再定论**，定论写进本文档 §1.1 并在 PR 描述留证 |
| 清硬编码后定时任务/批处理开始报错（此前靠 `1L` 侥幸跑通） | 批次 1 必须同时盘点**所有无上下文的入口**（定时任务、批处理、初始化），补齐或显式声明 |
| 出参面 110 端点仍直出 Entity（含 `UserController#get` 泄漏 `password` 哈希） | 与本 SPEC 无关但**同属 P102**，建议并行处理（不阻塞本 SPEC） |

## 10. 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| **V1.0** | 2026-10-05 | opencode | 首版。基于实测把「多账套」拆为 L1（企业级，**已基本落地**，本轮只收口 4 项遗留）/ L2（账簿级，**完全缺失**，仅立项）；给出「复用 `enterprise_id` vs 新增 `book_id`」两模型对比与推荐；补齐竞品对标（用友/金蝶/SAP/QuickBooks）与年度层差距；定稿 BDD 与三分批实施计划。**未改动任何代码。** |