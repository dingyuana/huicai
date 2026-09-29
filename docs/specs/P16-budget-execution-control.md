# REQ-2026-126 SPEC — 预算执行控制落地（部门/项目维度 + 三态控制 + 使用额累计 + 审批时间戳）

> **版本**：V1.1（已实施） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P16-EXT（P16-budget-management 的功能落地拓展）
> **状态**：✅ **已开发完成**（2026-09-29；老丁拍板「只修最小集合」；`BudgetFlowE2ETest` 1/1 转绿，慢测 1996/0/0/5 全绿）
> **来源**：慢测唯一剩余失败 `BudgetFlowE2ETest`（REQ-2026-121 判定为「功能未实现」，非测试数据问题）
> **关联需求**：REQ-2026-126（新登记）
> **关联现有实现**：`BudgetServiceImpl`（`create`/`approve`/`activate`/`checkBudget`/`executionAnalysis`）、`BudgetEntryMapper`（`findBySubjectAndPeriod`/`addUsedAmount`）、`BudgetController`（`/check`、`/execution`）、`BudgetFlowE2ETest`

---

## 0. 背景与根因（全部实测核验，含 fail 现场）

### 0.1 现场事实

慢测 `BudgetFlowE2ETest.fullBudgetFlow_endToEnd_shouldCompleteSuccessfully` 实跑输出：

```
BudgetFlowE2ETest.java:119 expected: not <null>   ← assertNotNull(savedApproved.getApprovedAt())
Tests run: 1, Failures: 1, Errors: 0
```

### 0.2 根因链（5 个，全部已在真实 PG + 源码确认）

| # | 根因 | 证据 |
|---|---|---|
| 1 | **`t_budget_entry` 缺 5 列**：`dept_id`、`project_id`、`period_month`、`control_type`、`used_amount`。`BudgetEntryEntity` 对这 5 个字段全部标注 `@TableField(exist = false)` 且注释写「DB 无此列」 | `\d t_budget_entry` 实测无这 5 列；Entity 源码 `exist=false` |
| 2 | **`t_budget` 缺 `approved_at`/`approved_by`**：`approve()` 照常 `setApprovedAt(LocalDateTime.now())`，但列不存在 ⇒ 幽灵字段赋值无效、DB 往返恒 null | E2E L119 FAIL（已复现）；`\d t_budget` 无这 2 列；`BudgetEntity.approvedBy/approvedAt` 均 `exist=false` |
| 3 | **`BudgetEntryMapper.addUsedAmount()` 引用不存在的列**：`UPDATE t_budget_entry SET used_amount = used_amount + ...` —— `used_amount` 列不存在 ⇒ 任何调用路径运行必报 SQL 错 | Mapper 源码 `@Update("...SET used_amount = used_amount + #{amount}...")` |
| 4 | **`checkBudget()` 对 null `controlType` 做 `switch` ⇒ NPE**（**真实生产缺陷**）：`control_type` 列不存在 ⇒ `entry.get("controlType")` 恒 null ⇒ `switch(null)` 在 `BLOCK` 前直接 NPE。`GET /budget/check` 一调即崩 | `BudgetServiceImpl.checkBudget` 源码；接口已暴露于 Controller |
| 5 | **`executionAnalysis()` 读幽灵 `usedAmount` 恒 0**：`e.getUsedAmount()` 经 MyBatis-Plus 从无此列的表读回恒 null ⇒ `totalUsed` 恒 0、`remaining`/`executionRatio` 全部失真 | Service 源码 |

### 0.3 测试自身 3 处 bug（与根因不同源，须一并改，且不算「改弱断言」）

| # | 位置 | 问题 | 修法 |
|---|---|---|---|
| a | Step5 `checkBudget(6601L, ...)` / Step6 `checkBudget(6602L, ...)` | 传的是科目**编码**，而 `findBySubjectAndPeriod` 按 `be.subject_id = #{subjectId}`（**主键 id**）匹配。`ensureSubject` 返回的是真实自增 id，绝非 6601/6602。测试注释自己都写了「subjectId 是 t_subject 的**主键 id**，不是科目编码」 | 改为传 `subjectId1`/`subjectId2` |
| b | `Long entry2Id = savedEntries.get(1).getId()` | `selectList` 无 `ORDER BY`，`get(1)` 拿第几条不确定 | 改按 `subject_id=subjectId2` 精确查询条目2 |
| c | 末尾 `if (e.getSubjectId().equals(6602L))` | 又拿编码比较 | 改比 `subjectId2`；`else` 分支改比 `subjectId1` |

### 0.4 隐患：Mapper 返回 Map 的 key 大小写错位（即使补了列仍会挂）

`findBySubjectAndPeriod` 的 `be.*` 返回 **snake_case** 列名，而 `checkBudget` 读 **camelCase** key（`get("usedAmount")`、`get("controlType")`）。`BudgetServiceImplTest` 的 Mock 夹具恰好写的是 camelCase（`dbEntry.put("usedAmount", ...)`），因此单测全绿、真实 DB 全挂 —— 又一个 Mock 盲区（AGENTS §4.3.7）。**必须**在 Mapper SQL 里 `AS "usedAmount"`/`AS "controlType"` 显式别名对齐，不能只加列。

### 0.5 竞品对标（铁律 #15）

| 竞品 | 预算维度 | 控制方式 | 使用额累计 | 执行分析 |
|---|---|---|---|---|
| 用友 NC/U8 预算 | 部门 + 科目 + 期间 ✓ | 三态：预警(WARN)/刚性阻断(BLOCK)/需审批 | ✓ 按单据占用 | ✓ 预算执行率报表 |
| 金蝶 KIS/EAS 预算 | 部门 + 科目 + 期间/项目 | 提示 / 硬性控制 | ✓ | ✓ |
| SAP BPC 预算 | 成本中心 + 科目 + 期间 | 承诺控制(commitment)+超额需释放 | ✓ | ✓ 差异分析 |
| QuickBooks 预算 | 科目 + 期间（无部门维度） | 仅报表对比，不阻断 | ✗ | 简 |

**结论**：本项目 `t_budget_entry` 设计（subject 维度 + 需补的 dept/project/period_month 维度、CHECK 三态控制、used_amount 占用累计、执行率分析）与用友/金蝶/SAP 主流模式一致，QuickBooks 类轻量产品无此深度。补齐真实列即回归行业标准「部门 + 科目 + 期间」多维预算体系。差异点：竞品普遍支持「预算与实际发生额自动比对」（凭证/单据实时占用），本项目 `checkBudget` 由业务端点显式调用、`used_amount` 由 `addUsedAmount` 手动累计 —— 口径偏轻量，本 SPEC 保持现状，后续可启用自动占用（列已就位）。

---

## 1. 输入契约

- `BudgetEntryEntity` 新增映射（对应 V158 新列）：
  - `deptId → dept_id`（bigint，可空，**不加 FK**——`t_dept` 当前为空表，强 FK 会卡住测试与空库新装；铁律 #9 的编号关联由业务端点/前端约束）
  - `projectId → project_id`（bigint，可空，无 `t_project` 表，纯预留列）
  - `periodMonth → period_month`（int，可空，1~12；与 `budget.period`（varchar(6) YYYYMM）并存）
  - `controlType → control_type`（varchar(20) **NOT NULL DEFAULT 'WARN'**，CHECK 允许 `WARN`/`BLOCK`/`APPROVE`）
  - `usedAmount → used_amount`（numeric(18,2) **NOT NULL DEFAULT 0**）
  - **修正**：`updatedAt` 从误标 `@TableField(fill=INSERT_UPDATE, exist=false)` 改为真实列（`t_budget_entry.updated_at` 实际存在，与 `t_budget_adjustment` 对称）
- `BudgetEntity` 新增映射：
  - `approvedBy → approved_by`（bigint，可空）
  - `approvedAt → approved_at`（timestamp，可空）
- `BudgetEntryMapper.findBySubjectAndPeriod`：`be.*` 基础上**显式别名** `be.used_amount AS "usedAmount"`、`be.control_type AS "controlType"`（对齐 Service 的 camelCase 读取）
- `BudgetServiceImpl.checkBudget`：对 `controlType` 为 **null/空白** 时兜底为 `"WARN"`（防御历史数据/手工 SQL，杜绝 `switch(null)` NPE）
- 权限：沿用现有预算管理权限，无新增鉴权面

## 2. 输出契约

- `approve(id)`：`approved_at`（+`approved_by`，若入口提供）**真落库**，回读非 null
- `create(entity, entries)`：条目 5 维度列 + `period` 一并持久化；`totalAmount` 仍为条目金额之和（不变）
- `checkBudget(subjectId, period, amount)`：返回结构不变（`pass`/`action`/`controlType`/`budget`/`used`/`newUsed`/`remaining`/`usageRatio`），但 `used`/`controlType` 来自真实列，语义正确：
  - BLOCK 且 `newUsed > budget` → `pass=false, action=BLOCK`
  - APPROVE 且超预算 → `pass=true, action=REQUIRE_APPROVE`
  - WARN 且 `usageRatio > 80%` → `action=WARN`；`pass` 恒 true（不阻断）
  - 无预算配置 → `pass=true, controlType=NONE`（不变）
- `addUsedAmount(id, amount)`：`used_amount` 原子累加落库（列已存在，SQL 原样可用）
- `executionAnalysis(period)`：`totalUsed`/`remaining`/`executionRatio` 由真实 `used_amount` 聚合
- 错误码：沿用 `BusinessException`，不新增

## 3. 状态流转与副作用

- 预算状态机（DRAFT→SUBMITTED→APPROVED→ACTIVE/CLOSED）**完全不变**，本批只补数据列，不改状态机
- `approve()` 增加副作用：写 `approved_at`/`approved_by`（**幂等**：重复审批仍校验 SUBMITTED，已拒绝）
- `checkBudget` 纯只读（只 SELECT），`addUsedAmount` 只改 `used_amount` 一列，均不触碰预算头/条目其它字段
- 负向断言：`checkBudget` 不得改任何预算行；空 `controlType` 兜底为 WARN 而非抛错

## 4. 异常处理

| 场景 | 处理 | 级别 |
|---|---|---|
| `control_type` 为 null/空白（历史或手工数据） | 兜底 `WARN`，继续检查 | WARN（不抛错） |
| `checkBudget` 查无该科目/期间条目 | 返回 `pass=true, controlType=NONE`（不变） | INFO |
| BLOCK 模式超预算 | `pass=false, action=BLOCK`，**不落库、不改状态**（拦截由调用方决定） | INFO |
| `subjectId` 不存在 | `findBySubjectAndPeriod` 自然返回空 → NONE | INFO |
| `addUsedAmount` 目标条目已逻辑删除 | 命中 0 行，返回 0（调用方自行判断） | INFO |

---

## 5. BDD 验收标准

### AT-BUDGET-126-1 预算创建持久化新维度列
- Given 一个 `OVERALL` 预算含两条条目（科目 6601@部门101/WARN/80000、6602@部门102/BLOCK/20000，各自 `periodMonth`）
- When `budgetService.create(budget, entries)` 且回查 DB
- Then `totalAmount = 100000`；条目数 = 2；每条 `deptId`/`periodMonth`/`controlType` 回读与写入一致

### AT-BUDGET-126-2 审批时间戳真落库
- Given 已 `submit` 的预算（SUBMITTED）
- When `budgetService.approve(id)` 后从 DB 回查
- Then `status = APPROVED` 且 `approvedAt` **非 null**

### AT-BUDGET-126-3 WARN 模式预算内放行
- Given 科目 6601（WARN/80000，已用 0）
- When `checkBudget(subjectId1, "202607", 30000)`
- Then `pass=true`、`controlType=WARN`、`budget=80000`、`newUsed=30000`

### AT-BUDGET-126-4 BLOCK 模式超预算拦截
- Given 科目 6602（BLOCK/20000）已累计 `addUsedAmount(entry2, 15000)`
- When `checkBudget(subjectId2, "202607", 10000)`
- Then `pass=false`、`action=BLOCK`、`budget=20000`、`newUsed=25000`

### AT-BUDGET-126-5 执行分析基于真实使用额
- Given 上述预算 ACTIVE，科目 6602 累计使用 15000
- When `executionAnalysis("202607")`
- Then `totalBudget=100000`、`totalUsed=15000`、`remaining=85000`

### AT-BUDGET-126-6 空 control_type 不 NPE
- Given `findBySubjectAndPeriod` 返回的条目 `control_type` 为 null
- When `checkBudget(...)`
- Then 不抛异常，按 `WARN` 语义检查

### 负向断言
- `checkBudget`/`executionAnalysis` 调用前后，预算主表与条目非 `used_amount` 列无任何变更（快照比对）

---

## 6. 测试计划

| 文件 | 改动 |
|---|---|
| `BudgetFlowE2ETest` | 改 3 处测试 bug（a/b/c）；断言不变更严格性，仅修正口径；**测试即验收，全部 AV 场景一条 E2E 覆盖** |
| `BudgetServiceImplTest` | 新增 `checkBudget_nullControlType_兜底WARN`（Mock：`controlType` key 缺失/null）；已有 8 条其余不改（Mock 夹具已是 camelCase，与修后别名一致） |
| `BudgetEntryMapperTest`（如存在/新增） | 真实 DB 验证 `addUsedAmount` 原子累加 + `findBySubjectAndPeriod` 返回 camelCase key + 新列落库 |

## 7. 变更记录

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.1 | 2026-09-29 | opencode | **已实施**。老丁审核拍板「只修最小集合」：①V158 补列（`t_budget_entry` 5 列 + `chk_budget_entry_control_type` + 组合索引 + `t_budget` 2 列 + 审批列回填）；②Entity 去幽灵（`BudgetEntryEntity` 5 字段 + `updatedAt` 反向修正；`BudgetEntity` `approvedBy`/`approvedAt` + 补齐从未声明的 `usedAmount`）；③`checkBudget` 新增 `pick()` 兼容 camel/snake Map key + 空 `controlType` 兜底 `WARN`（消除 `switch(null)` NPE）；④测试 3 处口径修正 + 补 6 条维度/审计列正向断言。**遗留（治标未治本）**：Mapper `findBySubjectAndPeriod` 仍返回 `be.*` 的 snake_case key，Service 靠 `pick()` 兜底；正解是 SQL 加 `AS "usedAmount"`/`AS "controlType"` —— 按老丁「最小集合」要求本轮不改，留后续 REQ |
| V1.0 | 2026-09-29 | opencode | 初稿（待老丁审核）|

## 8. 不在范围

- 预算与实际发生额「自动占用」（凭证/单据实时写 `used_amount`）—— 竞品普遍有，本批仅保证列与手工累计可用，自动联动留后续 REQ
- 部门/项目维度主数据校验（`t_dept` 空表、无 `t_project` 表）—— 本批只落列，维度合法性由前端/后续 REQ 治理
- 预算调整单（`t_budget_adjustment`）状态机改动 —— 与本批无关