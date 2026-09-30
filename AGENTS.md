# AGENTS.md — 慧财财务 (Huicai Financial Software)

---

## §0 项目状态（硬数字，每次 commit 后更新）

> **更新基准**：commit `79e9eec7`（REQ-2026-126 预算执行控制）+ REQ-2026-127（银行流水制证科目防护）+ **本次 REQ-2026-134（P107 存量缺陷修复包 D1~D6 + D8）** — D1 对账 DISPUTED 补 CHECK(V160)、D2 凭证分录物理删→软删、D3 批量空壳抛 501、D4 对账确认/驳回落库+审计表(V161)、D5 金额精度 3 处、D6 明文口令环境变量化、**D8 银行流水 PENDING_CONFIRM 补 CHECK(V162)**；全部 TDD 红→绿含反证，定向回归 83/83（**全量回归留夜间自动跑**）
> **当前分支**：`develop`（本地领先 origin，**未 push**）
> **关联文档**：[项目说明](docs/CORE-项目说明.md)、[技术方案](docs/CORE-技术方案.md)、[需求分析](docs/CORE-需求分析.md)、[需求登记册](docs/development/requirements/REQUIREMENTS_REGISTRY.md)、[文档注册表](docs/CORE-文档注册表.md)、[测试策略](docs/testing/TEST-STRATEGY.md)、[Flyway治理规范](docs/development/standards/flyway-governance.md)、[商用化修复总纲](docs/specs/P101-commercial-gap-remediation.md)、[修复开发计划](docs/development/plans/2026-09-29-commercial-gap-remediation-plan.md)、[存量缺陷包](docs/specs/P107-stock-defect-fix-pack.md)

| 维度 | 数据 |
|------|------|
| 后端代码 | 492 个 Java 主代码文件（另 237 个测试文件）|
| 测试用例 | **2023 个可执行测试注解**（`@Test` 2020 + `@TestFactory` 3）/ 236 个后端测试类 + 26 个前端测试文件 265 用例（最近一次**全量实测 1998 通过，0 Failures, 0 Errors, 5 Skipped**，为 REQ-2026-127 时的基线；本轮 REQ-2026-134 新增 7 个测试类 25 个用例：**定向回归 83/83 全绿**，全量回归留夜间自动跑。注：此前登记的「2002」为约数，按 `grep -P '@Test(?![A-Za-z])'` 重新核算）|
| 数据库 | PostgreSQL 16 / **78 个 migration，最新 V162**（注意：版本号非连续，实际为 V1-V5 + V63 + V92-V162，缺 V6-V62 与 V64-V91 共 85 个号；因 `out-of-order: true` + `validate-on-migrate: false` 不影响运行，但「V1 baseline merged V1-V146」的旧表述已失效）|
| API 端点 | 510+ 个后端端点 |
| 核心模块 | 基础数据、总账、应收应付、现金管理、固定资产、费用报销、发票税务、预算、财务报表、存储管理 |
| 业务单据类型 | 11 种（RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE/TRANSFER/SALARY/PRE_RECEIVE/PRE_PAY）|
| AI 服务 | Python FastAPI 5 端点（health/anomaly/embedding/match/ocr）|
| 技术栈 | Spring Boot 3.x + MyBatis-Plus + Redis 7 + MinIO + RabbitMQ |
| 开发流程 | 大闭环 + 内循环（three-phase-loop v3.0）|
| P0-P2 阶段 | ✅ 100% 完成（基础体系 + 缺陷修复 + AI 辅助能力）|
| P3 远期 | ⏳ 0%（经营分析/预算预测/风控/工资薪酬）|

---

## §1 核心设计铁律（强制执行，违反直接 reject PR）

1. **人是唯一审核主体**：所有审核/结转/状态变更必须由人主动触发，系统不允许自动调整任何业务实体状态
2. **AI 输出 = 建议**：永远不自动应用 AI 结果到财务数据，必须人工确认后才能写入。AI 是横切辅助能力，不替代 Java 确定性模块
3. **凭证不可变性**：已审核/已过账凭证不可修改，只能通过红冲（红-蓝 对冲）修正
4. **状态机严格转换**：所有核心实体（凭证/单据/发票/应收/应付）禁止非法状态跳转
5. **审计追踪**：所有关键写操作通过 AOP + `jsonb` 快照记录变更前后全量数据
6. **数据权限**：MyBatis 拦截器自动注入 SQL 条件，实现组织级数据隔离
7. **金额精度**：所有金额计算使用 `BigDecimal`，禁止 `double/float`，数据库 `NUMERIC(18,2)`
8. **核销架构**：银行流水不直接参与核销，必须先生成收款单/付款单后再匹配应收/应付
9. **编号关联溯源**：所有业务实体通过编号双向关联（xxx_id 外键 + xxx_no 编号冗余），支持全链路追溯
10. **三步闭环铁律**：每项开发任务必须走 SPEC→Plan→审核→执行，不能跳过前两步直接动手
11. **事务红线**：所有涉及资金流转、记账、冲红、核销的核心写操作，必须使用 `@Transactional(rollbackFor = Exception.class)`，严禁无事务执行
12. **逻辑删除**：关键财务表禁止物理删除，统一使用 `deleted` 字段（Integer，0=正常，1=删除），所有查询必须带 `deleted = 0` 条件
13. **DTO/VO 隔离**：禁止将数据库 Entity 直接暴露给前端 Controller 返回值，入参必须定义 DTO/Param，出参必须定义 VO
14. **异常处理**：禁止直接抛出原生 `Exception` 或 `RuntimeException`，必须使用 `BusinessException`（`com.huicai.common.exception`），统一错误码管理
15. **竞品对标**：创建或修改需求时，必须主动对比成熟竞品（用友、金蝶、SAP、QuickBooks等）的同类功能设计与开发经验，识别差异点和最佳实践，纳入 SPEC 背景分析。禁止闭门造车式设计
16. **需求变更审计**：任何需求修改必须记录变更原因、对标竞品结论、影响范围，同步更新 REQUIREMENTS_REGISTRY 版本历史

---

## §2 开发工作流标准

### 2.1 双层闭环流程

```
大闭环（closed-loop-doc-governance）— 项目级文档治理
├── Phase 1: PRD — 需求登记册 + REQ 编号 + 验收标准
├── Phase 2: Spec — SPEC 文档 + REQ 回链 + 版本历史
├── Phase 3: DEV — 开发实施 + DIR 捕获
└── Phase 4-5: 审核 + 闭环回写

内循环（three-phase-loop v3.1）- 功能级微循环开发
├── TRIAGE -> PLAN -> 审核门 -> BUILD(微循环) -> VERIFY -> REPORT
└── 每个微循环 5-15 分钟，TDD-First (Red->Green)，SDD+BDD 规范驱动
```

### 2.2 任务执行流程
```
需求理解 -> 写 SPEC（四段模板+BDD）-> 老丁审核 -> TDD微循环(Red->Green) -> 验证 -> commit -> push
```

### 2.3 开发规则
- **三步闭环**：SPEC->Plan->审核->执行，禁止跳过前两步
- **SDD 规范驱动**：每个 SPEC 必须含四段模板（输入契约/输出契约/状态流转/异常处理），Spec 定义边界，代码实现边界
- **BDD 行为契约**：验收标准必须用 Given-When-Then 格式，每个场景对应一个 @Test 方法
- **TDD-First (Red->Green)**：每个微循环先写测试，运行确认 FAIL(红)，再写实现，运行确认 PASS(绿)。跳过 RED 验证 = 违规
- **先写 SPEC 再写代码**：所有功能必须先有设计文档，禁止边想边写
- **测试必须 0 fail**：每次提交前 `mvn test` 必须 Failures: 0
- **负向断言强制**：所有状态机方法必须同时验证「该做的做了」+「不该做的没做」
- **BDD 场景覆盖率**：VERIFY 阶段必须确认每个 Given-When-Then 都有对应 @Test 且 PASS
- **轻量任务直写**：纯命令/查询/文档修改类任务，Hermes 直接执行

### 2.4 文档提交规范
- 新需求必须分配 REQ 编号，写入 REQUIREMENTS_REGISTRY.md
- 每个 SPEC 必须关联至少一个 REQ 编号，含版本历史
- 每次 commit 后更新 §0 硬数字
- commit message 首行 ≤ 50 字符，禁止 emoji、TODO、待办标记
- `git add` 必须指定具体文件路径，禁止根目录 `git add -A`

---

## §3 沟通铁律（强制执行）

### R9 — 选项呈现规则
- 选项必须在对话最后列出，不混入叙述文本
- 选项数量 ≤ 4 个，超过的先合并归类
- 每个选项必须标「推荐」或「⭐」
- 依据简短，不替用户做选择

### R10 — 任务完成回复规则
- 开头固定：`**任务已完成。**`
- 必须含 6 列总结表：`项 / 内容 / 状态 / 验证 / commit / push`
- 关键发现独立列出
- 遗留事项独立列出
- 查询任务：开头 `**任务已完成。**` + 结构化回答，不写 6 列表格

---

## §4 陷阱与经验库（持续更新）

### 4.1 业务逻辑类
1. **状态机副作用泄漏**：`confirm()` 只改状态，不生成凭证/业务单/应收单，后置逻辑由独立端点触发
2. **红冲匹配时机**：不在导入循环内匹配红字发票，导入完成后统一调 `batchLinkRedFlushInvoices()` 扫全库匹配
3. **发票导入模式**：导入时只创建发票（PENDING_CONFIRM），人工审核后才创建业务单

### 4.2 Entity-DB 不一致类
4. **Entity 字段 vs DB 列对不齐**：`OutputInvoiceEntity` 的 `auditedBy`/`auditedAt` 没有 `@TableField(exist = false)`，但 `t_output_invoice` 表没有这列。MyBatis-Plus 自动生成 SELECT 报 "column does not exist"。根源：注释写"V63 已添加列"但实际没加。修复：每次新增 Entity 字段时必须检查 DB schema，`exist = false` 和 `value=` 二选一，不可裸写。同类字段（`auditedBy/auditedAt/updatedBy/docStatus/voucherStatus`）是高风险模式。
5. **注释说"Vxx 列已添加"但 migration 实际没写**：V63 只给 `t_output_invoice` 加了 `version`，注释声称的 `audited_by`/`audited_at` 从未存在。教训：Entity 注释中的 migration 引用必须与 migration SQL 逐行对证。
6. **String 映射 JSONB 列缺 typeHandler**：`ai_mapping_result`/`aux_dimension`/`assist_json`/`ocr_data` 等字段在 Entity 中是 `String`，但 DB 列是 `JSONB`。全字段 UPDATE 时 PostgreSQL 报错 "column is of type jsonb but expression is of type character varying"。项目已有 `JsonbTypeHandler`，所有 String→JSONB 字段必须加 `@TableField(typeHandler = JsonbTypeHandler.class)`。已经用 AuditLogEntity 验证过正确的写法。
7. **自定义 SQL 中的表名必须与 DB 实际表名一致**：`ArapSettlementMapper.pageWithPartyName()` 的 JOIN 中用了 `t_supplier`，但数据库实际表名是 `t_vendor`。写 JOIN SQL 前先 `\dt` 确认表名，不要靠猜。

8. **`@TableField("col_name")` 指向不存在的列（2026-07-23 修复，反复出现）**：
   - `InputInvoiceEntity`：`amount_ex_tax`、`ai_risk_tag`、`process_status`、`ai_mapping_result`、`doc_no`、`voucher_no` 映射到不存在的列
   - `OutputInvoiceEntity`：同上 + `receivable_id`、`reversed_from`、`original_invoice_no`、`version`
   - `BankStatementEntity`：15+ 个字段无 `@TableField(exist = false)`，包括 `direction`、`batchId`、`purpose`、`transactionRemark`、`reviewedBy`、`reviewedAt`、`generatedDocId`、`generatedVoucherId`、`generatedAt`、`ruleId`、`aiConfidence`、`aiSuggestedAction`、`aiBusinessScene`、`version`
   - **根源**：Entity 按"未来完整 schema"写，但 DB 是另一个版本。注释写"Vxx 列已添加"但 migration 从未执行。
   - **预防**：统一用 `node backend/scripts/check-entity-schema.mjs` 在编译时检查字段映射一致性。后续每次改 Entity 都要跑这个检查。
   - **H-17 已集成 pre-commit hook**（2026-07-23）：提交涉及 `*Entity.java` 的变更时自动运行检查脚本。docker postgres 未运行时自动降级跳过列检查，仅做 typeHandler 警告。首次克隆仓库后执行 `bash backend/scripts/install-hooks.sh` 安装。
   - ⚠️ **工具告警 ≠ 缺陷**（2026-09-29 REQ-123 修正）：该脚本曾把 `cur.assist_json::text` 整体当成列名，误报「`assist_json` 列不存在」，而该列**真实存在**（`t_voucher_entry.assist_json jsonb`），SQL 实测可执行。根因是 `extractColumnRefs()` 的 `cleaned` 链未剥离 PostgreSQL `::type` 转型（已修）。**看到「引用了不存在的列」必须先在真实 DB 上把 SQL 跑一遍再下结论**，否则会把工具缺陷写进缺陷台账。

9. **同名字段在两张表的 CHECK 允许集不同（2026-09-29 慢测 REQ-119 沉淀）**：`status` 这个名字在三处允许集互不相同，**按「实体类型」猜合法值必然踩坑**：

   | 表 | 约束 | 允许值 |
   |---|---|---|
   | `t_business_doc` | `chk_doc_status` | DRAFT / SUBMITTED / **APPROVED** / VOUCHERED / PARTIALLY_RECONCILED / FULLY_RECONCILED / CLOSED / REJECTED / REVERSED |
   | `t_arap_settlement` | `chk_settlement_status` | DRAFT / SUBMITTED / **CONFIRMED** / REJECTED / VOUCHERED / REVERSED / CANCELLED |
   | `t_input_invoice` / `t_output_invoice` | `chk_input_invoice_status` / `chk_output_invoice_status` | PENDING_CONFIRM / PENDING_REVIEW / **CONFIRMED** / VOUCHERED / … |

   - `CONFIRMED` 对发票合法、对**业务单据非法**；`t_business_doc` 用 `APPROVED`/`VOUCHERED` 表达「已审核/已制证」。
   - 教训：写任何 Entity 前先查该表**自己的** CHECK 定义（`pg_get_constraintdef`），不要跨表沿用状态值。

10. **`exist = false` 字段是「幽灵字段」，赋值无效且不可断言（2026-09-29 慢测 REQ-117/119 沉淀）**：`@TableField(exist = false)` 的字段**完全不参与 SQL**，因此
    - 对它 `setXxx()` 后 `updateById` 不会落库 —— 代码里出现这类赋值属**误导性代码**；
    - 从 DB 读回**必然为 null** —— 任何 `assertEquals("xxx", loaded.getDocNo())` 都**永远不可能通过**。
    - 已确认的幽灵字段（注释均明写「DB 无此列」）：发票侧 `docNo`/`voucherNo`、`OutputInvoiceEntity.auditedBy/auditedAt`、凭证侧 `sourceDocId/sourceDocNo/sourceDocType`、`t_arap_settlement.voucherNo`、`BankStatementEntity.direction`、`AuditLogEntity.createdAt`。
    - **替代写法**：断言真实 id 列（`doc_id`/`voucher_id`/`business_doc_id`），并加 `assertNull(loaded.getGhostField())` 作为「该列确已废弃」的负向断言。
    - ⚠️ `t_input_invoice.audited_by/audited_at` **是**真实列（与 Output 侧不对称），别照搬。

11. **`MenuEntity` 缺 `menuCode` 字段 → 任何经 MP 插菜单必失败（2026-09-29 慢测 REQ-120 沉淀）**：`t_menu.menu_code` 是 `NOT NULL` 且**无默认值**，但 `MenuEntity` 只有 `name`/`permissionCode`/`type`/`parentId`… **没有 `menuCode`**。故 `menuMapper.insert(entity)` 必然报 `null value in column "menu_code"`，且 `SELECT` 也不回读该列。这是与第 8 条同源的 Entity-DB 不一致（且**方向相反**：不是 `@TableField` 指向不存在的列，而是 DB 的必填列在 Entity 里缺失）。测试侧暂用 `JdbcTemplate` 直插绕开（`AbstractMapperTest.createMenu()`），**待补 Entity 字段**。
    - 另注：`t_menu` **没有** `enterprise_id` 列，别想当然按多租户表插入。

12. **Flyway 种子用显式 id ⇒ identity 序列永久落后（2026-09-29 慢测 REQ-120 沉淀，慢测 B 类头号根因）**：种子 migration 普遍用**显式 id** 插基础数据（`t_menu` 1~200、`t_subject` 1~102、`t_role`/`t_sys_config` 1~5）却**从未调用 `nextval`**，故序列仍停在 1 或 18。测试首次让 DB 自行分配 id 时拿到的正是 `nextval(seq)=1`，正撞种子行 → `duplicate key ... t_menu_pkey`。
    - **根治办法（已落基类）**：`AbstractMapperTest` 用 `pg_class JOIN pg_depend(deptype='i') JOIN pg_attribute(attname='id')` 通用列出全部 82 个 identity 序列，`setval(seq, GREATEST(COALESCE(MAX(id),1),1))`，**每 JVM 只跑一次**（静态 `AtomicBoolean`）。
    - **为何一次就够**：PostgreSQL **序列不参与事务回滚** —— 测试方法回滚后行消失、序列不倒退，且只增不减，故一次对齐永久有效。逐类硬编码 `align()` 是治标。
    - **同类连锁**：序列落后还会伪装成**业务唯一键**撞码（`uq_role_menu`/`uq_user_role`），因为测试复用了种子 id（如 role_id=1/menu_id=1）而该组合已存在。**新造关联表数据一律用基类 `createRole()`/`createMenu()`/`createSysUser()`，禁止硬编码外键 id**；这三个助手**刻意不缓存**（唯一键要求每次全新 id，缓存会重新引入撞码）。

13. **`NOT NULL` 必填列在 Entity 里**完全缺失** ⇒ 写入路径生产必挂（2026-09-29 慢测 REQ-121 沉淀，与第 8/11 条同源）**：`t_dept.dept_code`、`t_menu.menu_code`、`t_budget.budget_name` 都是 `NOT NULL` 且无默认值，但 `DeptEntity`/`MenuEntity`/`BudgetEntity` **根本没有这些字段**（不是映射写错，是压根没写）。故 `DeptServiceImpl.create`、`MenuServiceImpl.create` 等任何经 MyBatis-Plus 的 `insert` 都会报 `null value in column`。
    - 识别法：`information_schema.columns WHERE is_nullable='NO' AND column_default IS NULL` 的列，逐一在 Entity 里找对应字段；找不到就是这类缺口。
    - REQ-121 已补齐这 3 个字段，基类 `createMenu()` 也从 `JdbcTemplate` 绕开改回 Mapper 路径（**测试绕开只会掩盖生产缺陷，不要用它当长期方案**）。

14. **CHECK 约束的允许集是**大小写敏感**的，且同名约束跨表不同（2026-09-29 慢测 REQ-121 沉淀，补强第 9 条）**：
    | 约束 | 允许值（注意大小写） |
    |---|---|
    | `chk_user_status` | `ACTIVE` / `INACTIVE` / `LOCKED`（**没有** `enabled`）|
    | `chk_user_type` | `SUPER_ADMIN` / `AGENCY` / `ENTERPRISE`（**没有** `employee`）|
    | `chk_menu_type` | **大写** `MENU` / `BUTTON` / `DIR`（小写 `menu` 直接违约）|
    | `chk_disposal_status` | `DRAFT` / `APPROVED` / `VOUCHERED`（**没有** `PENDING_APPROVAL`）|
    | `chk_settlement_type` | `RECEIVE` / `PAY`（**没有** `RECEIVABLE`/`PAYABLE`）|
    | `chk_budget_type` | `DEPARTMENT` / `PROJECT` / `SUBJECT` / `OVERALL`（**没有** `OPERATION`）|
    | `chk_direction` | **小写** `debit` / `credit`（**没有** `DEBIT`/`CREDIT`）|
    - `status` 方向仍见第 9 条（`t_business_doc` 无 CONFIRMED、`t_arap_settlement` 有）。
    - 铁律：**任何常量都要 `pg_get_constraintdef` 查证后再用，禁止照抄别处的字符串或凭业务语感猜。** 本轮 6 项慢测失败全是猜错允许集。

15. **「幽灵字段」比第 10 条更严重的一档：表里连列都不存在（2026-09-29 慢测 REQ-121 沉淀）**：`InputInvoiceEntity.processStatus`（`t_input_invoice` 无 `process_status`）、`OutputInvoiceEntity.aiMappingResult`（`t_output_invoice` **无任何 jsonb 列**）、`auditedBy`/`auditedAt` 全是 `exist=false`。对它们的断言**永远不可能通过**，包括：
    - 期望「DB 往返等于写入值」（如 `assertEquals("{...json...}", found.getAiMappingResult())`）→ 必挂；
    - 用它们构造 `LambdaQueryWrapper` 条件 → `MyBatisSystemException`（MP 无法解析列）。
    - **正确写法**：正向断言真实列 + `assertNull(loaded.getGhostField())` 负向锁死。
    - **判断依据**：`@TableField` 注解只代表「作者以为」，写测试前必须用 `information_schema.columns` 确认列真实存在。

16. **「反向缺口」：真实列存在但 Entity 未声明（2026-09-29 REQ-126 沉淀，与第 10/13 条同源但方向相反）**：`@TableField(exist = false)` 还有一种更隐蔽的误用 —— **DB 列真实存在，Entity 却标成不存在**：
    - `BudgetEntryEntity.updatedAt` 标 `exist=false`，而 `t_budget_entry.updated_at` **真实存在**（与 `t_budget_adjustment` 对称）⇒ 该列从不写入、读回恒 null；
    - `t_budget.used_amount` 是 `NOT NULL DEFAULT 0` 的真实列，**Entity 连字段都没有** ⇒ 头级使用额永不回读。
    - **危害比幽灵字段更隐蔽**：列在 `\d` 里一眼可见，不查 `information_schema` 根本发现不了；且 MP 只映射已声明字段，故**不报错、不警告、只是永远读不到**。
    - **预防**：`check-entity-schema.mjs` 应双向校验 —— 不仅查「Entity 引用了 DB 没有的列」，还要查「DB 有 NOT NULL/业务关键列但 Entity 未声明」。本轮靠人工发现，工具尚未覆盖。

17. **Mapper 返回 `Map` 时，key 大小写必须与 Service 读法对齐（2026-09-29 REQ-126 沉淀，Mock 盲区的典型变体）**：`@Select("SELECT be.* ...")` 返回的 key 是 **snake_case**（`used_amount`/`control_type`），而 Service 按 **camelCase** 读（`entry.get("usedAmount")`）⇒ **真实 DB 恒读出 null，Mock 夹具却恰好写 camelCase 而全绿**。
    - 在预算里这直接引发 `switch(controlType)` **NPE**（`/budget/check` 一调即崩），且 `executionAnalysis` 的 `totalUsed` 恒 0 —— 两处都靠 Mock 测不出来。
    - **正解**（治本）：SQL 里显式别名 `be.used_amount AS "usedAmount"`。
    - **临时兜底**（本轮采用）：Service 侧加 `pick(row, camelKey, snakeKey)` 兼容两种 key。
    - **教训**：凡 `@Select` 返回 `Map<String,Object>` 的方法，**必须在真实 DB 上验证一次**，Mock 夹具的 key 名不能作为「SQL 会返回什么」的依据（补强第 7 条 Mock 盲区：Mock 只能证明 Service 分支逻辑，不能证明 key 名正确）。

18. **代码按标准科目体系引用科目，但种子没种全 ⇒ 生产 NPE（2026-09-29 REQ-127 沉淀，P55 未竟项）**：`AutoGenerationService` 硬编码引用 `1002/1122/2203/1123/2202/1012/1221/2211/6603`，而 `t_subject` 实际**缺 4 个**（`2203` 预收账款、`1221` 其他应收款、`2211` 应付职工薪酬、`6603` 财务费用）。P55 只把利息/手续费科目**改指 6603** 并加了事后 null 守卫，却**从未把 6603 种进库** ⇒ 缺陷跨月潜伏。
    - **症状**：`generateDocThenVoucher` 的 `arAcct.getId()` NPE，报错文案是 `because "arAcct" is null` —— **不含科目代码**，用户无从得知缺哪个科目。
    - **两层修法缺一不可**：① 引用处 `requireSubject(code)` 抛 `BusinessException` 并**指明代码**（铁律 #14）；② 补种子 migration。只做①则功能仍不可用，只做②则换个科目又崩。
    - **教训**：**改引用必须同步验种子**。凡新增/改动硬编码科目代码，先 `SELECT count(*) FROM t_subject WHERE code='XXXX'`（含 `deleted` 过滤前也要看），再决定「补种子」还是「改引用」。
    - **审计法**：`grep -o 'findSubjectByCode("[0-9]\{4\}")' | sort -u` 提取全部代码，与库内实际 code 求差集 —— 一次性挖出全部缺失科目，比逐个崩溃再补高效。

19. **「CHECK 允许集漏掉设计态」比「写错值」更隐蔽（2026-09-30 D8 沉淀，与第 9/14 条同源但更难发现）**：`chk_stmt_match_status` 允许集为 `('UNMATCHED','MATCHED','MANUAL_MATCHED','IGNORED')`，**不含 `PENDING_CONFIRM`**，而 `BankReconciliationServiceImpl:332` 在自动匹配 60-84 分档写它 ⇒ 该分档**必然抛 SQL 错**。
    - **为什么比第 9 条难**：该值**不是照抄别处的错值，而是整个功能的设计意图** —— `summarize()` 按它统计待确认数、`unmatchedItems()` 按它归入未达账项、Controller `@Operation` 文档写明「60-84 PENDING_CONFIRM」、Service 注释三处描述它。**所有这些都自洽地"证明"它合法**，唯独 DB 不认。
    - **判据**：凡代码写入某状态值，除了 `pg_get_constraintdef` 查证，还要问一句「**这个值是不是某个状态机的合法中间态**」——若是，说明**约束漏了它**（补约束），而不是代码该改（改代码会连带推翻 summarize/Controller 语义）。
    - **衍生铁律**：D4 落地时若把 `rejectMatch` 门禁只放行 `MANUAL_MATCHED`，auto-match 产出的 `PENDING_CONFIRM` 行就**永远无法人工驳回** ⇒ 违反人审铁律 #1。**任何"待人工决策"态都必须同时是 confirm 和 reject 的合法输入。**
    - 🔴 **反证时 Flyway 会把回退"补"回来**：为证明红，先 `psql` 回退约束会被下次 `mvn test` 启动的 Flyway 再次应用 `V162` 抹平（假绿）⇒ 必须 **src + target/classes 双删迁移文件**（与 §4.5 target/classes 坑同源）才能拿到真红。

20. **判「Entity 缺某字段」前必须看父类（2026-09-30 D2 调查修正，直接推翻原判断）**：`VoucherEntity`/`VoucherEntryEntity` 类体里 grep 不到 `deleted`，我据此判定「MP 逻辑删除未生效、`deleteById` 是物理删」，并据此向用户提了两个方案（B1 仅分录 / B2 含父表）。**实际 `BaseEntity:43-44` 就有 `@TableLogic private Integer deleted;`**，两实体继承之 ⇒ **MP 逻辑删除本已生效**，方案 B2 的「Entity 补字段」根本不需要做，父表也不存在 CASCADE 抹审计问题。
    - **本可被两条线索当场否掉**：① 既有测试 `VoucherEntryMapperRealDBTest:49,76` 早在调 `s.setDeleted(0)`/`e.setDeleted(0)` —— **能编译就证明字段存在**；② 全局 `application.yml:69-70` 已配 `logic-delete-field: deleted`，MP 只对**声明了该字段**的实体生效，两者矛盾时该先怀疑自己看漏了。
    - **铁律**：任何「Entity 没有 X」的结论，必须同时 grep ① 父类 `BaseEntity` ② 既有测试的 `setXxx` 调用 ③ MP 全局逻辑删除配置。三者任一命中即证伪。
    - **同源提醒**：`@TableField(exist = false)` 有三种误用方向（第 8 条"指向不存在的列"、第 10 条"幽灵字段"、第 16 条"真实列被标不存在"），本条是第四种 —— **"父类已声明却以为自己没声明"**。

### 4.3 测试类
6. **测试假阳性**：测试通过 ≠ 功能完成。跨实体链路必须真实贯通，不能只测单个模块 CRUD。E2E 测试必须模拟真实用户操作路径
7. **Mock 测试盲区**：Mock 测试发现不了 DB 约束（NOT NULL、CHECK、UNIQUE）、Flyway 不匹配、SQL 语法错误。核心 Mapper 必须跑真实 DB 测试（Testcontainers）
8. **Service 签名变更同步**：扩展现有方法签名时，所有调用点（Controller、Service 实现、所有测试文件）必须同步更新
9. **双重遮蔽型测试假阳性：`assertNotNull` + fixture 镜像缺陷值**（2026-09-29 REQ-123 沉淀）：`MenuServiceImpl.getRoutesByUserId` 因 `"menu"`（小写）永远过滤不掉任何菜单，**用户路由恒空** —— 而这个缺陷从未被任何测试发现，靠的是两层遮蔽同时成立：
    - **断言只验「非 null」**：`assertNotNull(result)` 对「正确的非空树」和「错误的空列表」**同样通过**。凡是返回集合的接口，断言必须落到**内容**（`size()` + 关键字段）；
    - **fixture 值与缺陷值一模一样**：测试 `stubEntity()` 也写 `setType("menu")`，于是「生产写错、测试也写错」互相印证，看起来自洽。**fixture 必须用 DB 真实取值**（大写 `MENU`），否则测试是在给 bug 背书。
    - **正确做法**：正向断言内容 + 负向断言「不该做的没做」（BUTTON/DIR 必须被过滤、小写值不匹配）—— 这正是铁律「负向断言强制」的应用场景。

10. **测试断言可能落后于「有意的」生产重构**（2026-09-29 REQ-124 沉淀）：`ReconciliationServiceImpl.execute()` 有显式注释说明 P1-fix「统一核销写路径」把金额扣减延后到 `ArapSettlementService.approve()`（引用铁律 #1 人审铁律），而 3 个测试仍断言重构前的旧行为（`execute()` 后立刻 `CONFIRMED` + 金额已扣减）。**判据**：生产代码里若带着「为什么这样改」的注释且指向铁律，那是**有意重构**，该改测试而非生产代码；反之才是设计缺陷。
    - **识别信号**：`grep` 到 `// P1-fix: ...（人审铁律：...）` 这类注释 + 状态机出现「有前置校验却无任何方法可满足前置条件」的悬空状态（如 `SUBMITTED` 是终点而 `approve()` 要求 `CONFIRMED`）。
    - **禁止做法**：为了让旧断言变绿而放宽生产状态机（如把 `reverse()` 白名单加上 `SUBMITTED`），那是在拆掉人审铁律。
    - **正确做法**：测试走完整审批链（`execute` → `approve`），并**补「审批前金额不得变动」负向断言** —— 这条断言此前完全缺失，导致重构抽空的行为无人守门。
    - ⚠️ 顺带发现同源生产缺陷（REQ-2026-125 已修）：`ArapSettlementServiceImpl.logReconciliationLog()` 写审批日志时 `setTargetDocId(null)`，而 `ReconciliationServiceImpl.reverse()` 用 `reconLog.getTargetDocId()` 回查单据 → `selectById(null)` 返回 null → **金额静默不回滚却照常返回成功**。已改：按 `sourceDocType=SETTLEMENT` 委托 `ArapSettlementServiceImpl.reverse()` 红冲，缺 `targetDocId` 或单据不存在一律抛 `BusinessException`。
    - 🔴 **`if (doc != null)` 包住回滚逻辑 = 静默失败的典型反模式**（2026-09-29 REQ-2026-125 沉淀）：回查实体失败就整块 `skip`，但方法继续把日志置 `CANCELLED`/`REJECTED` 并返回成功 ⇒ DB 里留下「已反核销」状态而单据金额纹丝不动，**比直接抛异常危险得多**。凡「回滚/同步」类逻辑，回查不到实体**必须抛 `BusinessException`**。
    - 🔴 **「可空列」被当成「该空」**：`V144` 把 `target_doc_id` 放宽为可空（为解决核销单生命周期日志插入报错），于是 `logReconciliationLog()` 长期硬编码 `setTargetDocId(null)`。但核销单明细本就指向真实业务单据，补写即可自描述。**DB 放宽约束只解决「插不进去」，不解决「信息缺失导致下游查不到」**。

11. **Mock 把「库里根本不存在的主数据」stub 成存在 ⇒ 缺陷两年不暴露**（2026-09-29 REQ-127 沉淀，补强第 9 条）：`AutoGenerationServiceTest` 对 `2203` 预收账款写了 `stubSubject("2203", 2203L)`，而**生产库根本没有 2203** —— 测试替种子「补」了一行，缺陷因此完全不可见。
    - **两层遮蔽同时成立**：① 早期用例更粗，`subjectMapper.selectList(any())` **与 code 无关地回任何科目**（问 2203 也回 1122），连 `queryingCode` 匹配器都不用；② 后来的用例「精确」到 code，却精确地 stub 了一个**不存在的真实数据**。
    - **识别法**：`grep` 夹具里的科目/单据代码，与 `SELECT code FROM t_subject` 求差集 —— 测试里出现而库里没有的 code，就是被 stub 出来的幻觉数据。
    - **正确做法**：缺数据的场景要**显式留空**（不 stub → 默认空集合 → 触发防御分支）并断言异常；真实 DB 类则应真跑一次全新库迁移。
    - 🔴 **补种子后，测试夹具会撞唯一约束**（同源副作用）：V159 种入 6603 后，`VoucherEntryMapperRealDBTest.insertProfitSubject("6603",…)` 立即报 `duplicate key ... uq_subject_code_ent`。**夹具凡插主数据一律 find-or-insert**（先 select 再决定是否 insert），否则每补一次种子就要炸一批测试。同理，正文里凡 `insertXxx(code)` 这类造数助手都该如此。

### 4.4 技术类
7. **Jackson LocalDateTime 序列化**：`application.yml` 的 `date-format` 对 `LocalDateTime` 无效，必须注册专用序列化器
8. **Mockito `any()` 歧义**：MyBatis-Plus `updateById` 有双签名，必须用 `any(Entity.class)` 明确类型
9. **Flyway migration 漂移**：V 版本号必须连续，重复版本会导致迁移失败。迁移文件 commit 后不会自动执行，必须重启应用
10. **Flyway 重复版本**：新建迁移时版本号不得与已有文件冲突（V130 与 V140 各出现两份）。重命名后 `validate-on-migrate: false` 临时绕过校验，因 V130 修改导致后续校验链全部失效
11. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端
11. **MyBatis-Plus `updateById` 忽略 null 字段 → UpdateWrapper 双保险**（2026-09-11 反核销幽灵凭证修复，连续踩坑三次沉淀）：`updateById` 默认 NOT_NULL 策略，**实体字段置 null 不会更新对应 DB 列**（该列保持旧值）。凡需把某列更新为 NULL，必须：`①实体字段 setXxx(null)`（防止后续 `updateById` 把内存旧值回写覆盖）＋`②mapper.update(null, new UpdateWrapper<>().eq("id",id).set("col", null))`（显式清列）。两者缺一不可——只做 ① 列不清空，只做 ② 被 ① 的 updateById 回写。实例：`ArapSettlementServiceImpl.reverse()` 清 `voucher_id`、`restoreUnsettledAmount()` 清 `voucher_id`/`voucher_no`。**预防**：代码评审见到「需要置空某列」必须双查这两点；新增同类逻辑参考 SPC-111。

12. **`@Version` 乐观锁的两个陷阱**（2026-09-29 慢测 REQ-118 沉淀）：
    - **MyBatis-Plus 不把 `@Version` 列的 DB 默认值回填到插入时的内存对象**。若实体用 DB 默认值（如 `version DEFAULT 1`）而测试插入后直接拿原对象 `updateById`，`version` 仍为 null，`OptimisticLockerInnerInterceptor` 遇 null 会**同时跳过版本条件与递增**（既不校验并发也不 bump）。正确用法：插入后**重新 `selectById` 拿到实体**再更新。
    - **同一 SqlSession 内两次 `selectById` 返回同一个对象实例**（一级缓存），且更新成功后新 version 会回写进该对象。故「拿两个实体分别代表新旧 version」的乐观锁测试是**假的**——两个其实是同一个、且 version 已被刷新。必须**显式构造**一个带过期 version 的实体来验证「过期 version 更新命中 0 行」。

13. **测试基类 `@Transactional` 与 `REQUIRES_NEW` 互斥**（2026-09-29 慢测 REQ-118 沉淀）：`AbstractMapperTest` 类上带 `@Transactional`，每个测试方法结束即回滚。被测代码若内部开 `REQUIRES_NEW` 事务（如 `AutoGenerationService.autoGenerateInNewTx`），**新事务看不到外层未提交的夹具数据**，会报「记录不存在」这类看似无关的错误。修法：该测试类显式声明 `@Transactional(propagation = Propagation.NOT_SUPPORTED)` 关闭回滚，改由 `@BeforeEach` 自行清理数据（此时 `@BeforeEach` 基类设置的 `EnterpriseContextHolder` 与各类 id 缓存仍生效）。

14. **MyBatis-Plus 乐观锁冲突不抛异常、只返回 0 行**（2026-09-29 慢测 REQ-121 沉淀，补强第 12 条）：`OptimisticLockerInnerInterceptor`（已注册于 `MyBatisPlusConfig:24`）的机制是「把 `version` 条件塞进 WHERE，命中 0 行即视为冲突」，**不抛任何异常**。故 `assertThrows(...)` 形式的乐观锁测试是**永假的**（永远等不到异常），必须断言 `assertEquals(0, mapper.updateById(stale))` 并补一条「数据未被改动」的负向断言。

15. **自动生成逻辑常写在 Service 而非 Mapper（2026-09-29 慢测 REQ-121 沉淀）**：`PeriodServiceImpl.save()` **覆写了 MyBatis-Plus 的 `save`**，在其中生成 `periodCode`/`startDate`/`endDate`。测试若直接 `periodMapper.insert(entity)` 就**绕过了全部生成逻辑**，断言必然失败。**教训**：断言「自动生成」类行为前，先确认该逻辑挂在哪一层（Service 覆写 / 拦截器 / MetaObjectHandler），用对入口再写断言，别想当然调 Mapper。

16. **`selectCount(null)` 是全表计数 ⇒ 测试隔离越界（2026-09-29 慢测 REQ-121 沉淀）**：`selectCount(null)` 不带任何条件，会把种子数据和其它用例残留行一并计入 —— 慢测全量跑时得到 11 而非用例自己的 4 条。**修法**：每个用例造数时带唯一前缀（如 `"9999.E2E.KW.DOC."`），断言时用 `likeRight`/`eq` 收敛到自己的数据。**同类高危写法**：`SELECT count(*)` 裸查 + 断言固定总数（`SystemClearControllerIntegrationTest` 即栽在这），断言前必须先取 baseline 再做增量比较。
    - 🔴 **全表维护操作返回的行数天然含种子数据**（REQ-122 定位）：`SystemClearController.clearBusinessDocs()` 是 6 个无 `WHERE` 的 `DELETE` + 2 个全表解绑 `UPDATE`，其 `deleted` 必然包含库中**原有行** —— 实测 `t_business_doc` 有 **7 条 Flyway 种子行**，故单据删除数是 8 而非用例造的 1 条。**这类接口的返回行数本质上无法断言绝对值**，必须「先取基线、再断言增量」（`baseline + 本用例新增行数`）。**注意区分**：`assertEquals(0, 清空后 count(*))` 是**成立**的（清空即全表语义），要改的只是操作前的行数断言；而「保留型」断言（如「银行日记账应保留」）要从 `assertEquals(1, 总数)` 改成 `baseline + 1` —— 原写法往往只是**侥幸**通过（基线恰为 0）。

17. **测试可能针对「尚未建模」的功能（2026-09-29 慢测 REQ-121 沉淀）**：`BudgetFlowE2ETest` 断言部门/项目维度、逐条目使用额、控制方式（BLOCK/WARN），但 `t_budget_entry` 实际只有 10 列，`deptId`/`projectId`/`periodMonth`/`controlType`/`usedAmount` **全是幽灵字段**；于是 `checkBudget` 读到 `controlType=null` → `switch(null)` **NPE**，`addUsedAmount` 的 UPDATE 引用不存在的 `used_amount` 列。**这类不是「测试数据填错」，而是功能未实现** —— 继续改测试只会掩盖缺口。**识别信号**：Entity 里成片 `exist=false` + 生产代码直接读这些字段。**正确处置**：判为功能缺口走 SPEC 立项，测试保持失败待实现，**禁止把断言改弱来「做绿」**。

### 4.4 文档治理类
12. **SPEC 漂移检测**：架构变更后（如 P33→P34），SPEC 仍描述旧架构。每次大变更后必做 SPEC vs 代码一致性审计
13. **DIR 提交**：开发中发现需求模糊/规范缺失/流程不准确时，提交 DIR 到 `docs/dir/`，迭代结束时统一回写
14. **文档版本联动**：SPEC 版本号必须与 DESIGN.md 联动，修改代码后同步更新文档版本历史
15. **设计文档冲突**：DESIGN.md 变更后，检查 `docs/development/` 下所有文档是否冲突，冲突文件移入 `archive/`

### 4.5 工具链类
16. **OpenCode 委派失败**：worker 异常退出 → Hermes 直写 + 留任务书
17. **`git add <dir>` 陷阱**：add 目录后必查 `git status --short`，防止卷进 IDE 自动生成文件
18. **`execute_code` 工具返回空**：沙箱内 `terminal()`/`read_file()` 可能返回空，改用 `subprocess.run()` 直接调系统命令
19. **Maven JDK 21 编译**：`maven-compiler-plugin` 必须 ≥ 3.12.0，否则 `--release 21` 报错

### 4.6 工作流执行类
20. **起步跳过三步闭环**：收到"开发/继续开发/写代码"指令时，Hermes 必须先走 SPEC→审核门→再执行，禁止直接写 SPEC 文档或代码。**三次纠正沉淀：** 2026-07-09 ai-evolution-v2 起步时直接写计划文档+commit，跳过老丁审核（违反铁律 #10）。修正：收到任何开发指令，第一条输出必须是 SPEC 草案或要求确认需求，不是代码/计划文档。
21. **`git add -A` 导致 doc 漂移**：写完文档后用了 `git add -A` 而非指定文件路径，导致关联 issue 修复。修正：commit 前先 `git status --short` 确认只有目标文件被跟踪。

---

## §5 命名约定（强制执行）

- 表名前缀 `t_`（如 `t_voucher`、`t_business_doc`）
- 主键：`id BIGINT GENERATED ALWAYS AS IDENTITY`
- 金额列：`NUMERIC(18,2)`
- 扩展数据：`JSONB` 列（OCR 结果、AI 负载、辅助核算）
- AI 向量：`VECTOR(768)` + IVFFlat 索引
- 表/列名：**snake_case**（PostgreSQL 约定）
- Java 字段：camelCase，用 `@TableField(value = "snake_case_name")` 映射

---

## §6 常用命令速查

| 操作 | 命令 |
|------|------|
| 启动后端 | `cd backend && mvn spring-boot:run` |
| 启动前端 | `cd frontend && npm run dev` |
| 运行全部测试 | `cd backend && mvn test` |
| 快测试（无 Docker） | `cd backend && mvn test` |
| 完整测试（含真实 DB） | `cd backend && mvn test -DexcludedGroups=` |
| 单个测试类 | `cd backend && mvn test -Dtest=XxxTest` |
| 编译检查 | `cd backend && mvn compile` |
| 打包 | `cd backend && mvn clean package -DskipTests` |
| 启动 Docker 环境 | `docker compose up -d` |
| 查看 Flyway 状态 | `cd backend && mvn flyway:info` |
| 查看 Git 日志 | `git log --oneline -10` |

---

## §7 风险边界（操作前需人工确认）

1. **禁止 `git push -f`**：任何强制推送都会覆盖 Git 历史，必须经老丁确认
2. **禁止直接修改生产配置**：`application-prod.yml` 等生产环境配置文件不可修改
3. **禁止硬编码敏感信息**：数据库密码、API Key、密钥等必须通过环境变量或配置中心注入
4. **DDL 必须走 Flyway**：禁止直接修改数据库表结构，所有 schema 变更必须生成 Flyway migration
5. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端
6. **Git add 禁止通配**：`git add` 必须指定具体文件路径，禁止根目录 `git add -A`
7. **破坏性操作确认**：DROP、DELETE 无 WHERE、truncate 等操作必须经老丁确认

---

## §8 技术栈（已落地）

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.x, Spring Security + JWT |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL 16, pgvector, pg_trgm |
| 缓存/分布式锁 | Redis 7 |
| 消息队列 | RabbitMQ |
| 文件存储 | MinIO |
| AI 服务 | Python 3.11 + FastAPI, Hugging Face / PyTorch |
| 前端 | Vue 3 + Element Plus + ECharts |
| 报表 | EasyExcel |
| API 文档 | Knife4j (Swagger) |
| 监控 | Prometheus + Grafana（待接入） |
| 部署 | Docker Compose |