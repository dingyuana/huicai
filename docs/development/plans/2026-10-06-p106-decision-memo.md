# P106 待决策项决策备忘（D-1 / D-2 / D-3）

> **状态**：🆕 **待老丁裁定**（2026-10-06）—— 三项裁定可**一次性解锁 P106 批次 1a-3 与批次 1b**
> **来源**：P106 多账套 SPEC §10 待决策项（V1.4 新增，V1.5 仍未裁定）
> **性质**：本文**只做决策准备，不含任何代码/DDL**。每项都给出：事实依据 → 选项 → 影响 → 推荐
> **关联**：[P106 SPEC](P106-multi-book-account-set.md) | [P106 计划](2026-10-05-P106-multi-book-plan.md)

---

## 摘要：一页看懂

| 决策 | 一句话 | 我的推荐 | 不裁定的后果 |
|---|---|---|---|
| **D-1** | `tenantId` 请求参数**留还是废**？（6 个后端方法 + 2 个 Controller 的 `@RequestParam`） | **废弃该参数，改用上下文企业** | P106 批次 1a-3 永久挂起；分类规则/AI 反馈的企业过滤**继续失效** |
| **D-2** | §0.1 十张平台全局表**怎么分类**？（批次 1b 的前提） | 按 SPEC §0.1 建议的三类分组 | P106 批次 1b 永久挂起 |
| **D-3** | `t_classification_rule.tenant_id` 列与 `idx_classification_rule_tenant` 索引**删不删** | **等 D-1 定案后**再定，不单独裁 | 旧列与旧索引继续留在库里（当前已用 `COMMENT` 标注待清理） |

⚠️ **D-3 依赖 D-1**，建议**一次性裁定 D-1 + D-2**，D-3 随之确定。

---

## D-1：`tenantId` 请求参数去留

### 事实依据（已实测，非推断）

| 位置 | 用法 |
|---|---|
| `ClassificationRuleServiceImpl:43` | `page()` 读过滤 `WHERE tenant_id = <入参>` |
| `ClassificationRuleServiceImpl:56` | `create()` **写死 `setTenantId(1L)`** |
| `ClassificationRuleServiceImpl:105/139/187` | `seedForNewTenant()` 存在性检查 / 插入 / 逐条插入 |
| `AiFeedbackLogServiceImpl:40/77` | `page()` 与 `summaryByTenant()` 读过滤 |
| `ClassificationRuleController:27`（`page`）、`:77`（`seed`）、`AiFeedbackLogController` 的 `page` / `summaryByTenant` | **暴露成 `@RequestParam`，客户端可任意传值** |

**关键定性：这不是越权读，是过滤失效。** RLS 的 `enterprise_policy` 与 `tenant_id = <客户端值>` 是**两个 AND 条件** ⇒ 客户端传任意 `tenantId` 也读不到其它企业的行。真实后果是：多企业下 `tenant_id` 全为 1，**客户端按企业筛选永远查不到 / 查到错行**。

已用 `@Disabled` 用例（`AT-106-9`）钉在 `AccountSetIsolationRealDBTest` 里，不破坏 CI。

### 选项

| | 做法 | 影响面 | 风险 |
|---|---|---|---|
| **A. 废弃参数**（推荐） | 后端删 4 个 `@RequestParam`、6 个方法签名去掉 `tenantId`、读过滤改用 `EnterpriseContextHolder.get()` | **前端 4 处调用需同步改**（`ClassificationRuleController` 2 处 + `AiFeedbackLogController` 2 处） | 🟡 中。属**客户端契约变更**，须前后端同期发版，否则筛选参数被服务端忽略（功能退化但不报错） |
| **B. 保留但忽略** | 参数继续接受，传什么都不影响过滤 | 前端**零改动** | 🟡 低。但留下一个「看起来能用、实际无效」的参数 ⇒ 迟早有人问「为什么传了没用」，且**双列隐患延续** |
| **C. 保留并真正生效** | 让 `tenant_id` 与 `enterprise_id` 真正同源 | 无 | 🔴 **高**。等于承认双列隔离维度，而 RLS 只读 `enterprise_id` ⇒ 要么加 RLS 谓词（70 张表大改），要么两列继续可能不一致 |

### 推荐 **A** 的理由

1. RLS 已按 `enterprise_id` 隔离，客户端再传一个租户号属**重复且不可信**的隔离维度；
2. 保留即意味着「两个隔离列 + 一个客户端可控」，正是 §0.2 那类双列隐患的延长线；
3. 前端 `EnterpriseSwitcher` 已通过 `X-Enterprise-Id` 切换，无需在每个筛选器里再传一遍；
4. **B 的代价被低估**：留一个无效参数比删掉更贵 —— 它会让「过滤失效」这个缺陷**继续隐身**，因为调用方以为自己在筛选。

⚠️ 若选 A，需**前后端同期**；建议本项裁定后**单独排一个批次**，不要混进 1a（变更性质不同：1a 是修数据落点，本项是改客户端契约）。

---

## D-2：§0.1 十张平台全局表的三类分组

### 事实依据

这 10 张表**既无 `enterprise_id` 也无 `tenant_id`**（V1.1 曾误称有 `tenant_id`，V1.2 已用 `rg "ADD COLUMN.*tenant_id"` 全 migration 零命中证伪）。

### 建议分组（SPEC §0.1 已给，供参考裁定）

| 组 | 表 | 建议定性 | 理由 |
|---|---|---|---|
| **① 权限/导航骨架** | `t_menu`、`t_role`、`t_role_menu`、`t_user_role` | **刻意全局共享** | 权限是导航的引导，一套数据全企业共用。若按企业隔离，每个新企业都要复制一遍角色菜单，且角色语义会随企业漂移 |
| **② 应隔离** | `t_dept` | **应按企业隔离** | 部门是**企业内**组织数据。⚠️ 现存真实缺陷：业务表 `t_employee.dept_id` 是 FK，而 `t_dept` 无 `dept_code` 全局唯一约束 ⇒ **不同企业可创建同名部门** ⇒ 部门级数据权限天然有缺口 |
| | `t_sys_config` | **应隔离（但需先答一个问题）** | ⚠️ F1 已证它含 `accounting.start_year` / `accounting.start_month` 两条**账套级配置**。若判「应隔离」，必须先确认：这两条是**全局共享**（所有企业同值）还是**应按企业分设**？若是后者，这是**产品决策**，不是技术决策 |
| | `t_audit_log` | **应按企业隔离** | 审计日志天然按企业切分。⚠️ 但它**没有 `enterprise_id` 列** ⇒ 加列意味着**历史审计记录无法归属**，只能全部归到某个默认企业或标为「未知」 |
| **③ 平台元数据** | `t_agency`、`t_agency_user`、`t_enterprise` | **平台元数据（不属于任何企业）** | 它们描述的是「企业-代理」拓扑本身，按企业隔离在语义上就是错的 |

### 选项

| | 做法 | 影响 |
|---|---|---|
| **A. 按上表三类裁定**（推荐） | ①③ 写明「刻意全局共享 / 平台元数据」并落文档；② 的 3 张表落 Flyway（加列 + 回填 + 开 RLS） | 中等工作量。`t_sys_config` 需先答「start_year/start_month 是否分企业」 |
| **B. 全部判「刻意全局共享」** | 只写文档，不动 DDL | 工作量最小，但**放弃修 `t_dept` 的同名部门缺口** ⇒ 部门级数据权限的隐患继续存在 |
| **C. 全部判「应隔离」** | 10 张表全部加列 + RLS | ❌ **强烈不推荐**：`t_agency_user` / `t_enterprise` 按企业隔离在语义上自相矛盾（代理和企业是拓扑两端，不是租户），且会**破坏 `EnterpriseMembershipChecker` 的成员校验**（它查 `t_agency_user` 时若被 RLS 按企业过滤，跨企业成员授权会全部失效） |

### 推荐 **A**，但**建议拆成两个决策**

- **D-2a（低风险，可立即裁定）**：①③ 共 7 张表定性为「刻意全局共享 / 平台元数据」，**落文档即可，不动 DDL**。
- **D-2b（需产品输入）**：② 的 3 张表。其中：
  - `t_dept` —— 技术方案明确（加 `enterprise_id` + 回填 + RLS + 补 `uq_dept_code_ent`），**可直接排期**；
  - `t_sys_config` —— **需先确认 `start_year`/`start_month` 是否按企业分设**；
  - `t_audit_log` —— **需确认历史审计记录如何归属**（加列后旧行 `enterprise_id` 填什么？）。

⚠️ **我强烈不建议一次性裁定 10 张表**：D-2b 的两张表卡在**产品语义**上（账套级配置是否分企业、审计历史归属），硬裁会让下游 DDL 方案建立在未经确认的假设上。

---

## D-3：`t_classification_rule.tenant_id` 列与旧索引删否

### 事实依据

| 项 | 状态 |
|---|---|
| `t_classification_rule.tenant_id` 列 | **仍存在**（V1 baseline 建表） |
| `idx_classification_rule_tenant (tenant_id, deleted)` | **仍存在**，且**仍建在旧列上** |
| 处理现状 | V168 已用 `COMMENT ON INDEX` 标注「待清理 / 1a-3 待决策」—— **只标注，不删** |

**为什么不先删**：①删列属**破坏性 DDL**（AGENTS §7 需老丁确认）；②该列是否废弃**完全取决于 D-1**（若 D-1 选 B/C 保留 `tenantId`，删列会直接破坏功能）⇒ 提前删等于抢先替业务方决策。

### 选项

| D-1 的裁定 | D-3 的对应结论 |
|---|---|
| **A（废弃参数）** | ⇒ `tenant_id` 列与 `idx_classification_rule_tenant` **一并删除**（新 migration，`DROP INDEX IF EXISTS` + `DROP COLUMN IF EXISTS`）。⚠️ 需先确认全库无其它读取方（当前 `rg` 仅命中该 1 处索引定义 + Service 的 6 处**参数**用法，参数在 A 下已改） |
| **B（保留但忽略）** | ⇒ 列与索引**保留**，继续 `COMMENT` 标注「已废弃，勿使用」 |
| **C（真正生效）** | ⇒ 列保留，且需把 RLS 谓词扩展到该列（**大规模改动，不建议**） |

---

## 裁定后各批次可解锁什么

| 裁定 | 解锁 |
|---|---|
| **D-1 = A** | 批次 **1a-3**（`AT-106-9` 的 `@Disabled` 用例恢复为绿）+ D-3 的删列 migration |
| **D-2 = D-2a** | 批次 **1b** 的文档部分（7 张表定性落档） |
| **D-2 = D-2b** | 批次 **1b** 的 DDL 部分（`t_dept` 可立即排期；另 2 张待产品确认） |

⚠️ **批次 3（L1-4/L1-5「一个用户能否挂多家企业」+ `uq_username` 全局唯一 vs `(username, enterprise_id)`）不在本文三项之内**，需**单独审核**（影响登录，且可能需回填脚本）。