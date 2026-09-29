# AGENTS.md — 慧财财务 (Huicai Financial Software)

---

## §0 项目状态（硬数字，每次 commit 后更新）

> **更新基准**：commit `815cb7e6` + 后续提交 (2026-09-28) — fix: P99 修复8项构建与可运行性阻断缺陷（REQ-103~111）；A/D 类修复 (2026-09-29) — 慢测 A+D 36 项转绿并修 1 个真实生产缺陷（REQ-113~115）；D5 基建修复 (2026-09-29) — 基类统一企业上下文，慢测再消解 37 项（REQ-116）；悬空外键修复 (2026-09-29) — 补 ensureCustomer 助手，慢测再消解 11 项（REQ-117）；银行流水批次修复 (2026-09-29) — 补 ensureSubject 助手，修 REQUIRES_NEW 隔离，慢测再消解 9 项（REQ-118）；P3 全链路修复 (2026-09-29) — 单据状态与幽灵列，慢测再消解 3 项（REQ-119）；B 类种子撞码修复 (2026-09-29) — 通用 identity 序列对齐 + RBAC 硬编码关联组合，慢测再消解 11 项（REQ-120）；C 类测试数据约束修复 (2026-09-29) — 补 3 个 Entity 缺失的 NOT NULL 字段 + CHECK 取值/幽灵列断言/隔离越界，慢测再消解 16 项（REQ-121）
> **当前分支**：`main`（本地领先 origin，**未 push**）
> **关联文档**：[项目说明](docs/CORE-项目说明.md)、[技术方案](docs/CORE-技术方案.md)、[需求分析](docs/CORE-需求分析.md)、[需求登记册](docs/development/requirements/REQUIREMENTS_REGISTRY.md)、[文档注册表](docs/CORE-文档注册表.md)、[测试策略](docs/testing/TEST-STRATEGY.md)、[Flyway治理规范](docs/development/flyway-governance.md)

| 维度 | 数据 |
|------|------|
| 后端代码 | 492 个 Java 主代码文件（另 237 个测试文件）|
| 测试用例 | 1994 个 `@Test` 方法 / 229 个后端测试类 + 26 个前端测试文件 265 用例（**快测实测 1733 通过，0 Failures, 0 Errors, 5 Skipped**；含 slow 组全量 1988，其中 **5 项待修**，A/D/C/B 类 124 项已于 2026-09-29 修复，见 REQ-2026-113~121）|
| 数据库 | PostgreSQL 16 / **73 个 migration，最新 V157**（注意：版本号非连续，实际为 V1-V5 + V63 + V92-V157，缺 V6-V62 与 V64-V91 共 85 个号；因 `out-of-order: true` + `validate-on-migrate: false` 不影响运行，但「V1 baseline merged V1-V146」的旧表述已失效）|
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

### 4.3 测试类
6. **测试假阳性**：测试通过 ≠ 功能完成。跨实体链路必须真实贯通，不能只测单个模块 CRUD。E2E 测试必须模拟真实用户操作路径
7. **Mock 测试盲区**：Mock 测试发现不了 DB 约束（NOT NULL、CHECK、UNIQUE）、Flyway 不匹配、SQL 语法错误。核心 Mapper 必须跑真实 DB 测试（Testcontainers）
8. **Service 签名变更同步**：扩展现有方法签名时，所有调用点（Controller、Service 实现、所有测试文件）必须同步更新

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