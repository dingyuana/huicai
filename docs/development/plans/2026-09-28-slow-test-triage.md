# 慢测全量 128 项失败 — 分诊清单

> **创建日期**：2026-09-28
> **状态**：🟡 分诊完成；**A / D / D5 / 悬空外键 / 银行流水 / P3全链路 已修复并验证**（96 项），剩余 32 项待修
> **数据来源**：`mvn test -DexcludedGroups=`（含 slow 组）全量运行
> **基线**：`main @ 3ab1a606`｜快测 1733/0 failures 正常，慢测 1980 中 **16 Failures + 112 Errors**
> **A+D 修复后**：慢测 **1984 中 1 Failure + 91 Errors = 92 项**
> **D5 修复后**：慢测 **1984 中 7 Failures + 48 Errors = 55 项 / 33 类**
> **悬空外键修复后**：慢测 **1984 中 7 Failures + 37 Errors = 44 项 / 31 类**
> **银行流水修复后**：慢测 **1988 中 7 Failures + 28 Errors = 35 项**
> **P3 全链路修复后**：慢测 **1988 中 7 Failures + 25 Errors = 32 项**
> **重要前提**：本清单**不含任何代码修改**。这些缺陷此前被 Testcontainers 连接错误完全掩盖
> （`AbstractMapperTest` 容器按类重建导致 `Connection refused`），REQ-108 修复后首次真实执行暴露。
> **性质**：全部为**既有测试数据/断言缺陷**，非生产代码缺陷
> （**唯一例外：REQ-2026-115 `ColumnMappingResolver` 缺真实银行表头别名，属真实生产缺陷**）

---

## 〇、A+D 类修复结果（2026-09-29 已完成）

| 分类 | 原项数 | 现状 | 关联 REQ |
|------|-------|------|---------|
| A 编号/索引断言过期 | 15 | ✅ 0 | REQ-2026-113 |
| D 显式 setId 违反 IDENTITY | 7 | ✅ 0 | REQ-2026-114 |
| C 种子数据准备不足 | 94 | 92 项待修 | — |
| B 与迁移种子撞码 | 11 | 待修 | — |
| E 断言与共享库状态不符 | 1 | 待修 | — |

**已转绿（40 tests / 0 failures）**

| 测试类 | 结果 | 处置 |
|--------|------|------|
| `NumberingAssociationIndexesTest` | 16/16 ✅ | 断言新模型 10 个真实索引 + 7 个关联列；加负向断言锁死 14 个废弃索引与 `t_receivable`/`t_payable` 不得复活 |
| `NumberingAssociationFieldsTest` | 11/11 ✅ | 改用 `business_doc_id` / `invoice_no` / `voucher_id`+`voucher_no`；负向断言发票侧 `doc_no`/`voucher_no` 与凭证侧 `source_doc_*` 不得存在；正向断言发票侧 `doc_id`/`voucher_id` 仍有效 |
| `NumberingAssociationE2ETest` | 6/6 ✅ | 端到端「发票→单据→凭证」改走 `business_doc_id`；断言凭证读回 `sourceDocNo`/`sourceDocType` 恒 null |
| `InvoicePaymentReconcileMapperRealDBTest` | 4/4 ✅ | 去 `setId`，改用 DB 分配 id 传给单据 JOIN；新增 `ensureVendor` 助手造真实 `t_vendor`；修正 `paidAmount` 断言（SQL 语义为 `settled_amount`，无单据时为 0，原断言 1000.00 与自身注释矛盾） |
| `BankStatementRealDataImportTest` | 1/1 ✅ | 去 `setId` 改 DB 分配；补 `EnterpriseContextHolder` + `SecurityContextHolder` 登录态；修正收入/支出分布 5/10 → **6/9**（CSV 实为 6 收 9 付） |
| `InvoiceConfirmAuditPathIntegrationTest` | 2/2 ✅ | 排序字段 `getCreatedAt`（`exist=false`）→ `getId`；断言改为真实持久化列 |

### ⚠️ 分诊归因纠正（重要）

`InvoiceConfirmAuditPathIntegrationTest` 的 2 项原归入 **D 类（IDENTITY）**，**实为 C 类**：

```
can not find lambda cache for this property [createdAt] of entity [AuditLogEntity]
```

`AuditLogEntity.createdAt` 标注 `@TableField(exist = false)`（DB 实际列是 `operation_time`，
且实体**根本没有** `operationTime` 字段），故不能作 lambda 排序字段。与 IDENTITY 无关。

### 🔍 连带发现（3 个隐性根因）

1. **`LoginUser` 构造器参数顺序**：第 3 参是 `enterpriseId`、第 4 参是 `agencyId`。
   按示例误传 `null, 1L` 会使 `getCurrentEnterpriseId()` 返回 null →
   规则引擎按 `t_classification_rule.tenant_id = null` 过滤 → 规则全落空 →
   退化为方向兜底（`out` → `business_payment`），工资被误判。
2. **`importFromCsv` 的 catch 吞异常**：`classifySingle` 因「未登录」抛错被
   `log.warn` 吞掉，`classification` 留 null 且**测试只能看到一个断言失败**，
   真实原因藏在 WARN 日志里 —— 排障须 grep `分类失败`。
3. **CSV 编号列宽**：`t_business_doc.doc_no` / `voucher_no` / `t_voucher.voucher_no`
   均为 `varchar(32)`（`invoice_no` 是 `varchar(64)`）。测试唯一后缀不能直接用
   `System.nanoTime()`（19 位），须用短 base36 标记，否则
   `value too long for type character varying(32)`。

### 🐛 真实生产缺陷（REQ-2026-115）

`ColumnMappingResolver.Field.COUNTER_ACCOUNT` 别名缺 **`对方户名`** ——
工行/建行/招行导出格式的常用表头，导致导入后 `counterAccount` 恒 null、
流水分类与自动生单失准。已补 `对方户名/对方单位/对方姓名/交易对方/对方方名`。

---

## 一、总体分布

**128 项 / 44 个测试类**（修复后：**92 项 / 37 个测试类**）

| 分类 | 项数 | 涉及类数 | 根因性质 | 修复难度 | 风险 |
|------|------|---------|---------|---------|------|
| **A 编号/索引断言过期** | 15 | 1 + 1 | 测试守护**已废弃设计** | 中 | 🔴 **最高**（见下） |
| **B 与迁移种子撞码** | 11 | 8 | 测试数据编码与 seed 冲突 | 低 | 🟢 低 |
| **C 种子数据准备不足** | 94 | ~30 | NOT NULL/外键未赋值 | 中高 | 🟡 中 |
| **D 显式 setId 违反 IDENTITY** | 7 | 3 | 测试写法违反 AGENTS §4.2-4 | 低 | 🟢 低 |
| **E 断言与共享库状态不符** | 1 | 1 | 语义待确认 | 中 | 🟡 中 |

---

## 二、A 类：编号关联断言守护已废弃设计（🔴 优先处理）

### 事实

`NumberingAssociationIndexesTest` 断言 **14 个索引必须存在**，全部不存在：

```
idx_receivable_doc_no          idx_payable_doc_no
idx_receivable_invoice_no      idx_payable_invoice_no
idx_receivable_voucher_no      idx_payable_voucher_no
idx_settle_entry_receivable    idx_settle_entry_payable
idx_voucher_source_doc_no      idx_voucher_source_doc_type
idx_input_invoice_doc_no       idx_input_invoice_voucher_no
idx_business_doc_voucher_no    idx_arap_settlement_voucher_no
```

### 关键证据

| 项 | 事实 |
|---|---|
| 技术方案声明 | `docs/CORE-技术方案.md:168`「t_receivable/t_payable 已删除，统一使用 t_business_doc 表」 |
| 实际 DROP migration | **不存在**（`grep 'DROP TABLE.*t_receivable' ` 无命中） |
| 实际表状态 | `t_receivable` / `t_payable` **均不存在**；`t_business_doc` 存在 |
| 结论 | 两表在当前 schema 中**从未创建**，合并发生在 V1 baseline 之前 |
| 现存索引命名 | `idx_voucher_business_doc_id`、`idx_business_doc_invoice_id`、`idx_business_doc_due_date`、`idx_t_business_doc_enterprise` … |

`NumberingAssociationFieldsTest`（8 项失败，编号追溯字段 `docNo`/`voucherNo`/`sourceDocId`/`sourceDocNo`，编号形如 `9999.DOC.INPUT.001`）属同一设计族。

### 为什么最危险

这批测试**不是数据准备不足，而是在守护一套已被废弃的数据模型**。若有人为让测试转绿而去建这些索引：
- 会在**不存在的表**上建索引 → 必然失败，诱导误判
- 或在现存表上按旧设计补 `source_doc_no`/`source_doc_type` 等列 → **复活已废弃的双向编号结构**，与 `t_business_doc` 单一模型冲突

### 建议处置（需老丁拍板）

1. **确认旧编号关联设计确已废弃**（技术方案为准）
2. 若确认废弃 → **删除/改写这 2 个测试类**（15 + 8 = 23 项），改按 `t_business_doc` 现有字段与索引编写断言
3. 若仍有保留价值 → 改写为对**现存**索引的断言（`idx_voucher_business_doc_id` 等）
4. **不建议**为迁就测试而改生产 schema

---

## 三、B 类：与迁移种子数据撞码（11 项 / 8 类）

| 测试类 | 项数 |
|---|---|
| `RoleMapperRealDBTest` | 2 |
| `RoleMenuMapperTest` | 2 |
| `VoucherEntryMapperRealDBTest` | 2 |
| `RoleMenuMapperRealDBTest` / `UserRoleMapperRealDBTest` / `UserRoleMapperTest` / `SysConfigMapperRealDBTest` / `VoucherIntegrationTest` | 各 1 |

**根因**：迁移已 seed 5 角色 / 43 菜单 / 4 用户 / 18 凭证模板，测试用固定编码（如 roleCode=`TEST_ROLE` 或复用 `ADMIN`）插入 → 撞唯一约束。日志中 `duplicate key value violates unique constraint` 出现 55 次（Spring 将 DuplicateKey 包成 `DataIntegrityViolation`，故多数被计入 C 类）。

**建议**：测试统一改用带随机后缀的独立编码（如 `TEST_ROLE_<UUID8>`），并保留一条**显式断言撞码**的用例（部分类已有，勿删）。

---

## 四、C 类：种子数据准备不足（94 项 / ~30 类，最大宗）

### 高频具体原因

| 约束 | 次数 |
|---|---|
| `null value in column "dept_code" of relation "t_dept"` | 7 |
| `null value in column "category_id" of relation "t_asset_card"` | 7 |
| 唯一约束撞码（Spring 包装为 DataIntegrityViolation） | 55 |
| 其他 NOT NULL / 外键 | 其余 |

### 涉及类（按项数）

`NumberingAssociationFieldsTest` 8、`AssetCardMapperTest` 8、`OutputInvoiceMapperTest` 7、`ExpenseFlowE2ETest` 6、`SalesFlowE2ETest` 6、`InputFlowE2ETest` 5、`VoucherMapperTest` 4、`BankFlowE2ETest` 4、`ReconciliationWorkbenchE2ETest` 4、`ReconciliationIntegrationTest` 4、`InvoicePaymentReconcileMapperRealDBTest`(见 D 类) 4、`NumberingFullChainE2ETest` 3、`NumberingAssociationE2ETest$SalesChainTest` 3、`NumberingAssociationE2ETest$ProcurementChainTest` 3、`InputInvoiceMapperTest` 3、`BusinessDocMapperTest` 3、`BankStatementAuditIntegrationTest` 3 …（完整 44 类见 `mvn` 输出）

### 建议

- 本批**已为 P99 提供可复用范式**：`AbstractMapperTest.ensureBankAccount(enterpriseId)` 与 `ensureAssetCategory(enterpriseId)`，解决 `t_bank_account`/`t_asset_category` 迁移后为空表导致的外键问题
- 建议按同样方式补 `ensureDept`（`dept_code`）、并对 `t_asset_card` 统一走 `ensureAssetCategory`
- E2E 类（`SalesFlowE2ETest` 等）需各自补齐完整业务前置数据，工作量最大，建议单独排期

---

## 五、D 类：显式 setId 违反 IDENTITY（7 项 / 3 类）

**根因（单一）**：`ERROR: cannot insert a non-DEFAULT value into column "id"`

测试显式 `entity.setId(...)` 后插入，而对应列是 `BIGINT GENERATED ALWAYS AS IDENTITY`（AGENTS.md §4.2-4 / §4.6 的一致性铁律：Entity 的 `@TableId(type = IdType.AUTO)` 必须对应 DB `GENERATED ALWAYS AS IDENTITY`）。

| 测试类 | 失败方法 |
|---|---|
| `InvoicePaymentReconcileMapperRealDBTest` | `reconcile_fullyPaid_marksPaid:81`、`reconcile_partialPayment_marksPartial:70`、`reconcile_unpaid_noDoc_marksUnpaid:92`、`reconcile_nullFilters_doNotFail:102` |
| `BankStatementRealDataImportTest` | `setUp:74` |
| `InvoiceConfirmAuditPathIntegrationTest` | 2 项（`MyBatisSystemException`，待复核是否同因） |

**建议**：让 DB 分配主键（插入后回读 `getId()`），或按 §4.4-11 的双保险范式处理；**不得**把列改为 `GENERATED BY DEFAULT` 来迁就测试。

---

## 六、E 类：断言与共享库状态不符（1 项）

`SystemClearControllerIntegrationTest.clearBusinessDocs_withSettlementEntry_shouldSucceed:139`
→ `应清理 1 条核销明细 + 1 条核销单 + 1 条业务单 ==> expected: <3> but was: <10>`

**待确认语义**：该测试断言清理接口返回受影响行数。实际得 10，说明清理范围比断言预期大（可能包含级联/幂等重复清理）。**这可能是真实行为差异而非测试过期**，需先读实现再判定。

---

## 七、建议执行顺序

```
第 1 步  A 类 15+8 项 —— 先确认「编号关联旧设计是否确已废弃」，再改写/删除测试   🔴
第 2 步  D 类 7 项   —— 去 setId，改 DB 分配主键；改动小、收益明确                🟢
第 3 步  B 类 11 项  —— 测试改用随机独立编码                                    🟢
第 4 步  C 类 94 项  —— 按表补种子工厂（复用 ensureBankAccount/ensureAssetCategory 范式）
         ├ 4a 高频单点：t_dept.dept_code(7)、t_asset_card.category_id(7)
         └ 4b 30 个 E2E/Integration 类：逐类补业务前置数据，工作量最大
第 5 步  E 类 1 项   —— 先读实现确认语义，再定测试或改码
第 6 步  重跑慢测全量，目标 BUILD SUCCESS
```

---

## 八、需老丁确认事项

| 编号 | 议题 | 我的推荐 |
|---|---|---|
| ~~D1~~ | A 类旧编号关联设计是否确已废弃？据此决定改写还是补索引 | ✅ **已确认并执行**：确已废弃（技术方案 §4.2 明文，两表无 DROP migration 说明合并早于 baseline）→ 改写测试，**未补任何索引/列**。新增负向断言锁死废弃结构（REQ-2026-113） |
| D2 | B/C 类是否接受「测试改用随机编码 + 补种子工厂」作为统一修法 | ⭐ 接受，已在 P99 验证该范式可行 |
| D3 | 4b 的 30 个 E2E/Integration 类是否本轮一并修 | ⭐ **单独排期**，不与 A~D 混做（E2E 前置数据量大，易掩盖其它问题） |
| D4 | E 类 `SystemClearControllerIntegrationTest` 若确为行为差异，是否升级为新缺陷 | ⭐ 先读实现；若接口语义与断言不符，另立需求 |
| **D5** | ~~剩余 92 项 C 类是否在 `AbstractMapperTest` 统一设 `EnterpriseContextHolder.set(1L)`？~~ | ✅ **已拍板并实施**（REQ-2026-116）：92 → **55 项**，`enterprise_id` 根因消失。两处副作用已处理（详见 §〇 D5 小节） |

---

## 〇之二、D5 实施结果（2026-09-29 已完成，REQ-2026-116）

**做法**：`AbstractMapperTest` 加 `@BeforeEach setDefaultEnterpriseContext()`（设 `DEFAULT_ENTERPRISE_ID=1`）
与 `@AfterEach clearEnterpriseContext()`，并提供 `useEnterprise(Long)` 供子类切换。

**为什么单点修复能消解 90+ 项**：`enterprise_id` 自 V102~V105 起 `NOT NULL` 且**无 DB 默认值**，
`MyMetaObjectHandler.insertFill` 仅在 `EnterpriseContextHolder.get() != null` 时才回填该列。
测试无登录态 → 上下文为 null → 不回填 → 整片 `null value in column "enterprise_id"`。

**两处必须处理的副作用**（若忽略会直接造成回归）

| 副作用 | 现象 | 处置 |
|--------|------|------|
| 拦截器给所有慢测 SELECT 注入 `enterprise_id = 1` | 7 个依赖「无上下文→放行全部」的用例转红 | `DataIsolationAuditTest` 6 个「漏洞确认」用例 + `BankStatementDataIsolationTest` 1 个「超级管理员」用例，方法体内显式 `EnterpriseContextHolder.clear()`，并留注释说明与基类默认值相反 |
| 使用独立 `ENT_ID` 造数的报表/余额类数据被过滤 | 4 个类查询返回 0 行 | `IncomeStatementCaliberRealDBTest`(9904)、`AuxiliaryDetailRealDBTest`(9905)、`CashSubjectBalanceRealDBTest`(9902)、`OpeningContinuityRealDBTest`(9903) 改调 `useEnterprise(ENT_ID)` |

**结果**：慢测 **92 → 55 项 / 33 类**；`enterprise_id` 从根因 Top 榜**彻底消失**；
全量慢测**无新增红项**；快测 1733 / 0 Failures 回归通过。

### 剩余 55 项的三大根因（可批量处理）

| 根因 | 次数 | 涉及 |
|------|------|------|
| 硬编码 `vendorId=1` / `customerId=1` 悬空外键（`fk_input_invoice_vendor` / `fk_output_invoice_customer`） | 45 | `SalesFlowE2ETest`(6)、`InputFlowE2ETest`(5) 等 → 改用 `ensureVendor()` / 补 `ensureCustomer()` |
| `t_bank_statement.tx_type` NOT NULL 未赋值 + `fk_statement_account` 悬空 | 35 | `BankStatementAuditIntegrationTest`(3) 等 → 补 `txType` + `ensureBankAccount()` |
| `t_business_doc` 用发票状态（`PENDING_CONFIRM`/`CONFIRMED`）违反 `chk_doc_status` | 15 | 合法值：DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED |
| 与 Flyway 种子撞码（`uq_role_menu`/`t_subject_pkey`/`t_role_pkey`/`uq_user_role`…） | 40 | B 类 11 项，已有 `alignIdentitySequences()` 范式可复用 |

---

## 〇之三、悬空外键批次修复结果（2026-09-29 已完成，REQ-2026-117）

慢测 **55 → 44 项 / 31 类**，`fk_input_invoice_vendor` / `fk_output_invoice_customer` 两个根因彻底消失。

**实际范围修正**：分诊表里的「45」是**日志行计数**（同一次 FK 违约在异常栈里
打印多行），真实受影响的是 **2 个类 11 项**。分诊统计应按「类 × 用例」而非日志行。

**做法**

1. 基类新增 `ensureCustomer()`，与 `ensureVendor()` 完全对称
   （`t_customer` 唯一约束 `UNIQUE(code, enterprise_id)`，同企业造一行并缓存）。
2. 两个 E2E 类的硬编码 `customerId/vendorId = 1 | 99` 全部改用上述助手。

**连带修掉的 2 类同源缺陷**（比 FK 本身更隐蔽）

| 缺陷 | 现象 | 修法 |
|------|------|------|
| `t_business_doc.doc_no` NOT NULL 无默认值，原代码**只读不写** | `null value in column "doc_no"` | 造单时显式赋唯一 `docNo` |
| 单据状态写 `"CONFIRMED"` / `"SETTLED"` | `violates check constraint "chk_doc_status"` | 改 `APPROVED`（已审核待结算）/ `FULLY_RECONCILED`（已结清）。`CONFIRMED` 是**发票侧**状态，`SETTLED` 在允许集里**根本不存在** |

**顺带纠正一批不可能成立的断言**

原代码断言 `invoice.docNo` / `invoice.voucherNo` / `voucher.sourceDocNo` /
`voucher.sourceDocType`，但这四个字段都标注 `@TableField(exist = false)`
（实体注释明写「DB 无此列」）—— 经 DB 往返读回**必然为 null**，断言永远不成立。
已改为断言真实 id 列落库（`invoice.doc_id` / `invoice.voucher_id` /
`voucher.business_doc_id`），并补上 `voucher.businessDocId` 建立凭证→单据溯源。

**注**：`t_input_invoice.audited_by/audited_at` **是**真实列，可断言；
但 `OutputInvoiceEntity.auditedBy/auditedAt` 是 `exist=false`，不可断言。

---

## 〇之四、银行流水批次修复结果（2026-09-29 已完成，REQ-2026-118）

慢测 **44 → 35 项**。5 个类 9 项表层错误（`tx_type` NOT NULL、`account_id` 空值、
`fk_statement_account`、`t_asset_card.category_id`/`useful_life`）修完后，
暴露出**4 层更深的缺陷** —— 这些比表层错误更值得关注：

### 🔴 1. 约束用例「因错误的原因通过」

`BankStatementMapperTest` 有 5 个 `assertThrows` 用例
（NOT NULL / CHECK 约束校验）。在 `accountId=1` 悬空 FK 存在期间，
**每一次插入都先撞 FK 异常**，而 FK 异常同样满足 `assertThrows` ——
于是这些用例**从未真正验证过目标约束**（`chk_stmt_type`、
`chk_stmt_match_status`、`chk_stmt_review_status`）。

> **教训**：断言「应当失败」时，必须先确认**失败原因**就是目标约束。
> 否则插入层的任意异常都会顶替它，形成假绿。

### 🔴 2. 守护了一条不存在的约束

`insert_shouldEnforceChkDirection` 断言非法 `direction` 会插入失败。
但 `BankStatementEntity.direction` 是 `@TableField(exist = false)`
（DB 无此列，仅内存缓存），**根本不存在 `chk_direction` 约束** ——
该用例**永远无法通过**。已改为断言该字段确实不落库（读回为 null）。

### 🟡 3. `@Version` 乐观锁的两个陷阱

原断言「初始版本号应为 0」与 DB 默认值 **1** 矛盾。修正过程中又暴露两点，
已沉淀进 `AGENTS §4.4` 第 12 条：

- MyBatis-Plus **不把 `@Version` 列的 DB 默认值回填到插入时的内存对象**。
  拿插入时的原对象去 `updateById`，`version` 仍为 null，
  `OptimisticLockerInnerInterceptor` 遇 null 会**同时跳过版本条件与递增**
  —— 既不校验并发，也不 bump。正确用法：插入后**重新 `selectById`** 再更新。
- **同一 SqlSession 内两次 `selectById` 返回同一个对象实例**（一级缓存），
  且更新成功后新 version 会回写进该对象。故「拿两个实体分别代表新旧 version」
  的乐观锁测试是**假的**——两个其实是同一个、且 version 已被刷新。
  必须**显式构造**一个带过期 version 的实体。

### 🔴 4. `REQUIRES_NEW` 看不到外层未提交数据

`BankStatementAuditIntegrationTest` 的 `audit()` 内部走
`AutoGenerationService.autoGenerateInNewTx`（**`REQUIRES_NEW`**）。
基类 `AbstractMapperTest` 类上带 `@Transactional`，夹具数据处于**未提交**状态，
新事务根本看不到它 → 必抛「银行流水不存在 5」这类看似无关的错误。

修法：该类显式 `@Transactional(propagation = NOT_SUPPORTED)` 关闭回滚，
改由 `@BeforeEach` 自行清理；并补登录态（自动制证走 `SecurityUtils`）与
1002/2203/1122 三个科目。已沉淀进 `AGENTS §4.4` 第 13 条。

### 另修正：跨测试方法的隐式依赖

`BankFlowE2ETest.step2` 通过 `System.getProperty` 读取 step1 写入的流水 id。
但基类每个方法结束即回滚，**该行早已消失**；且 JVM 级 `System` property
会跨方法泄漏，单独跑时又会静默 `return`（假绿）。已改为自建数据。

### 状态机边界：未改业务规则

`service.review()` 的可复审白名单为
`null / PENDING / classified / manual_pending / reclassified`，**不含 `UNCONFIRMED`**；
而测试先写 `UNCONFIRMED` 再调 `review()` 必然抛「无法复审」。
本次**按已实现的状态机**修正断言，**未改动业务规则** ——
`UNCONFIRMED`（待确认）是否应可复审属产品语义问题，
按铁律 #1（人是唯一审核主体）/#4（状态机严格转换）需老丁单独拍板。

---

## 〇之五、P3 全链路修复结果（2026-09-29 已完成，REQ-2026-119）

慢测 **35 → 32 项**，`NumberingFullChainE2ETest` **3/3 全绿**。

**根因 4 类**（除表层的 `chk_doc_status` 外，其余 3 类与 A/D 类同源）

| # | 缺陷 | 说明 |
|---|---|---|
| 1 | 单据状态误用发票状态 | `doc.setStatus("CONFIRMED")`，`t_business_doc.chk_doc_status` **不含** CONFIRMED |
| 2 | 断言幽灵编号列 | `invoice.docNo` / `invoice.voucherNo` / `settlement.voucherNo` 均 `exist=false`，DB 往返必为 null |
| 3 | 凭证溯源用已废弃字段 | `voucher.sourceDocId/No/Type` 亦 `exist=false`；真实列是 `t_voucher.business_doc_id` |
| 4 | 幽灵字段赋值属误导性代码 | `invoice.setDocNo/setVoucherNo` 对 INSERT/UPDATE 完全无效 |

**⚠️ 本次最值得记住的坑：同名字段，两表允许集不同**

| 表 | 约束 | 是否含 `CONFIRMED` |
|---|---|---|
| `t_business_doc` | `chk_doc_status` | ❌ **不含**（用 `APPROVED`/`VOUCHERED`） |
| `t_arap_settlement` | `chk_settlement_status` | ✅ **含** |
| `t_input_invoice`/`t_output_invoice` | `chk_input_invoice_status`/`chk_output_invoice_status` | ✅ **含** |

本测试**同时**操作三张表的 `status`，前两处单据写 `CONFIRMED` 报错、
核销单写 `DRAFT` 却合法 —— 极易误判成「CHECK 约束有 bug」。
已沉淀为 `AGENTS §4.2` 第 9 条。

**顺带补齐**：原代码只设 `doc.invoiceNo` 而未设 `doc.invoiceId`，
双向关联实际是半通的，已补齐并加入断言。

---

## 〇之六、B 类种子撞码修复结果（2026-09-29 已完成，REQ-2026-120）

**结果：慢测 32 → 21 项，消解 11 项。** 8 个类 24 个用例全绿。

### 根因一：identity 序列落后于种子数据（7 项主键撞码）

**先量化**（REQ-120 定位过程）：

| 表 | MAX(id) | 当前 seq | 落后 |
|---|---|---|---|
| `t_menu` | 200 | 1 | **199** |
| `t_subject` | 102 | 18 | **84** |
| `t_role` | 5 | 1 | 4 |
| `t_sys_config` | 5 | 1 | 4 |
| `t_user` | 4 | 4 | 0 |
| `t_dept` | 0 | 1 | 0 |

种子 migration 用**显式 id** 插基础数据却**从未 `nextval`**，
故序列停在起始值；测试首次让 DB 自行分配 id 时拿到的正是 `nextval(seq)=1`，正撞种子行：

```
duplicate key value violates unique constraint "t_menu_pkey"
duplicate key value violates unique constraint "t_subject_pkey"
duplicate key value violates unique constraint "t_role_pkey"
duplicate key value violates unique constraint "t_sys_config_pkey"
```

**根治**（而非逐类 `align()`）：在 `AbstractMapperTest` 用目录表通用查出**全部 82 个**
identity 序列并统一推进：

```java
// 每 JVM 只跑一次
SELECT s.relname, t.relname
FROM pg_class s
JOIN pg_namespace n ON n.oid = s.relnamespace
JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'i'
JOIN pg_class t ON t.oid = d.refobjid
JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
WHERE n.nspname = 'public' AND a.attname = 'id' AND s.relkind = 'S'
-- → setval(seq, GREATEST(COALESCE(MAX(id),1), 1))
```

**为何一次就够**：PostgreSQL **序列不参与事务回滚**。测试方法因基类
`@Transactional` 回滚后，行消失但序列不倒退，且只增不减 ⇒ 一次对齐永久有效。

### 根因二：RBAC 测试硬编码关联组合（5 项业务唯一键撞码）

序列对齐后，这 5 项**换了个错误**继续失败 —— 暴露第二层根因。

| 测试 | 原硬编码 | 撞码约束 | 种子已有 |
|---|---|---|---|
| `RoleMenuMapperTest`(2) | `(1,1)` / `(2,5)` | `uq_role_menu` | role1×menu1~5 |
| `RoleMenuMapperRealDBTest`(1) | `(1,1)` | `uq_role_menu` | 同上 |
| `UserRoleMapperTest`(1) | `(1,1)` | `uq_user_role` | (1,1) |
| `UserRoleMapperRealDBTest`(1) | `(1,1)` | `uq_user_role` | 同上 |

这类撞码与序列**无关** —— 即便序列对齐，硬编码的种子 id 组合依然会撞。
`RoleMenuMapperTest.selectById_shouldReturnRelation` 用的 `(2,5)` 也已存在。

修法：基类新增 `createRole()` / `createMenu()` / `createSysUser()` 现造行。
**三者刻意不做缓存** —— 唯一键要求每次拿到全新 id，缓存会重新引入撞码。
（对比：`ensureVendor`/`ensureCustomer` 可缓存，因为无唯一键约束。）

### 🔴 顺带发现真实 Entity-DB 缺口（待修）

`t_menu.menu_code` 是 `NOT NULL` 且**无默认值**，但 `MenuEntity` **没有 `menuCode` 字段**。
故**任何经 MyBatis-Plus 插入菜单的代码路径都会因缺列失败**：

```
ERROR: null value in column "menu_code" of relation "t_menu" violates not-null constraint
```

这是 AGENTS §4.2 第 8 条的**镜像方向**（不是 Entity 指向不存在的列，
而是 DB 必填列在 Entity 里缺失）。`createMenu()` 暂用 `JdbcTemplate` 绕开，
已登记待补 Entity 字段。

另确认 `t_menu` **无 `enterprise_id` 列**（首次插入时误加该列报
`column "enterprise_id" does not exist`），别想当然按多租户表处理。

### 剩余 21 项的根因分组（下一批候选）

| 组 | 数量 | 根因 |
|---|---|---|
| ① NOT NULL 未填 | 8 | `dept_code`/`period_code`/`menu_code`/`budget_name`/`doc_date`/`category_id`/`account_id`/`tx_type` |
| ② CHECK 允许集 | 3 | `chk_user_status`、`chk_output_invoice_type`、`chk_disposal_status` |
| ③ 状态机语义 | 3 | 核销单期望 `CONFIRMED` 实得 `SUBMITTED`（`review()` 白名单不含 `UNCONFIRMED`，属**产品语义待拍板**） |
| ④ 断言/数据隔离口径 | 4 | 清理类断言 3 vs 10、关键词搜索 4 vs 15、`t_output_invoice` JSONB 读回 null |
| ⑤ 幽灵列断言 | 2 | `input/output_invoice.voucherNo` —— 又是 `exist=false` 幽灵列 |
| ⑥ 乐观锁 | 1 | `updateById` 期望抛异常但未抛（版本号未变） |

---

## 〇之七、C 类测试数据约束修复结果（2026-09-29 已完成，REQ-2026-121）

**结果：慢测 21 → 5 项，本轮修 16 项（① 8 + ② 3 + ④ 3 + ⑤ 2... 实为 16），目标类 44 项中 43 绿；快测 1733/0 无回归。**

### 🔴 根因一：`NOT NULL` 必填列在 Entity 里**完全缺失** ⇒ 生产写入路径必挂（3 项，含 2 个真实生产缺陷）

`information_schema.columns WHERE is_nullable='NO' AND column_default IS NULL` 扫出三个缺口：

| 表.列 | Entity 现状 | 后果 |
|---|---|---|
| `t_dept.dept_code` | `DeptEntity` **无 `deptCode` 字段** | `DeptServiceImpl.create:37` `deptMapper.insert()` 报 `null value in column "dept_code"` |
| `t_menu.menu_code` | `MenuEntity` **无 `menuCode` 字段** | `MenuServiceImpl.create:49` `menuMapper.insert()` 同上 |
| `t_budget.budget_name` | `BudgetEntity` **无 `budgetName` 字段** | 任何预算插入必失败 |

**这不是测试数据问题，是生产缺陷**（承接 REQ-2026-120 的遗留缺口）。已补 3 个 `@TableField` 字段；基类 `createMenu()` 同步从 `JdbcTemplate` 绕开改回 Mapper 路径 —— **测试侧的绕开只会掩盖生产缺陷，不能当长期方案**。

### 🔴 根因二：CHECK 允许集**大小写敏感 + 同名跨表不同**（6 项）

本轮 6 项失败**全部**是猜错允许集。对照表（均经 `pg_get_constraintdef` 查证）：

| 约束 | 测试原值（违约） | 实际允许值 |
|---|---|---|
| `chk_user_status` | `enabled` | `ACTIVE`/`INACTIVE`/`LOCKED` |
| `chk_user_type` | `employee` | `SUPER_ADMIN`/`AGENCY`/`ENTERPRISE` |
| `chk_menu_type` | `menu`（小写） | **大写** `MENU`/`BUTTON`/`DIR` |
| `chk_disposal_status` | `PENDING_APPROVAL` | `DRAFT`/`APPROVED`/`VOUCHERED` |
| `chk_budget_type` | `OPERATION` | `DEPARTMENT`/`PROJECT`/`SUBJECT`/`OVERALL` |
| `chk_direction` | `DEBIT`（大写） | **小写** `debit`/`credit` |

另 `chk_output_invoice_type` **不含** `RED`（红字是查询层由 `amount < 0` 派生的伪值，非入库枚举）。

### 🟡 根因三：幽灵字段当真实列断言（4 项，**永不可能通过**）

- `InputInvoiceEntity.processStatus` —— `t_input_invoice` 连 `process_status` 列都没有（真实列是 `status`）
- `OutputInvoiceEntity.aiMappingResult` —— `t_output_invoice` **无任何 jsonb 列**；`auditedBy`/`auditedAt` 同样是幽灵

原用例期望「DB 往返等于写入值」（如 `assertEquals("{...json...}", found.getAiMappingResult())`）。改为**正向断言真实列 + `assertNull` 负向锁死幽灵列**。

> `@TableField` 注解只代表「作者以为」。写测试前必须用 `information_schema.columns` 确认列真实存在。

### 🟡 根因四：测试隔离越界 + 口径错（3 项）

- `NumberingFrontendApiTest` 用 `selectCount(null)` 断言**全表** 4 条 → 慢测全量跑得 11（含种子 + 其它用例残留）。改用 `docNo` 前缀 `likeRight` 收敛到自己的数据。
- `OutputInvoiceMapperTest.summaryByFilter` 的 `pending` 口径期望由 3 改 **2**（发票 B 是 `VOUCHERED`，属 completed 而非 pending；`pending` = status NOT IN VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED）。
- `NumberingSettlementE2ETest` 两处补 `doc_date`、`settlement_type`（**RECEIVE/PAY**，不是 `RECEIVABLE`/`PAYABLE`）；`t_voucher_type.code` 限 `varchar(20)`，原值 23 字符超限。

### 🟡 根因五：方法性缺陷（2 项）

- **`PeriodMapperTest` 绕过 Service**：自动生成 `periodCode`/`startDate`/`endDate` 写在 `PeriodServiceImpl.save()`（覆写 MP 的 save），直插 Mapper 全部绕过。断言「自动生成」前先确认逻辑挂在哪一层。
- **`BusinessDocMapperTest` 乐观锁断言永假**：MP 的 `OptimisticLockerInnerInterceptor` 冲突时**不抛异常**，只把 version 塞进 WHERE 并返回 **0 行**（该拦截器确已注册于 `MyBatisPlusConfig:24`）。改为「显式构造过期 version + `assertEquals(0, mapper.updateById(stale))` + 数据未变负向断言」。

### 🔴 顺带发现 3 个「疑似」生产缺陷（**REQ-2026-123 逐条查证**）

1. **`MenuServiceImpl:84` 用户路由恒空** —— 比较小写 `"menu"`，而种子数据与 `chk_menu_type` 均为大写 `MENU`。✅ **已确认为真实缺陷并修复**。
2. **`PrepaymentServiceImpl:190,328` 预收预付流程运行时必失败** —— 写 `settlementType="PAYABLE"/"RECEIVABLE"`，而 `chk_settlement_type` 只允许 `RECEIVE`/`PAY`。✅ **已确认为真实缺陷并修复**。
3. ~~`ReportDataMapper` 的 `@Select` 运行时必报错 —— 引用 `assist_json::text`，但 `t_voucher_entry`/`t_voucher`/`t_subject` **均无该列**。~~ **【⚠️ 经 REQ-2026-123 更正为误报**：`t_voucher_entry.assist_json` 实为真实 jsonb 列，SQL 在真实 PG 上执行成功（退出码 0）；根因是 `check-entity-schema.mjs` 未剥离 `::type` 转型，脚本已修**】**

> **教训**：工具告警 ≠ 缺陷。第 3 条就是「看到静态检查报缺列就直接下结论」的样本 —— 动手前必须先在真实 DB 上把 SQL 跑一遍。

### 🔴 `BudgetFlowE2ETest` 判定为**功能未实现**（非测试缺陷，已延后）

`t_budget_entry` 实际只有 10 列，**无** `dept_id`/`project_id`/`period_month`/`control_type`/`used_amount`；而 `BudgetEntryEntity` 把这 5 个全标成幽灵字段，生产代码 `BudgetServiceImpl.checkBudget` 却直接读 `entry.get("controlType")`：

- `findBySubjectAndPeriod` 依赖 `t_budget.period` 关联（条目自身无 period 映射）→ 匹配不到
- `controlType` 恒为 null → `switch(null)` **NPE**
- `addUsedAmount` 的 UPDATE 引用不存在的 `used_amount` 列 → **SQL 报错**

该测试针对的是尚未建模的预算模型（部门/项目维度 + 逐条目使用额 + 控制方式）。**判为功能缺口，走 SPEC 立项；测试保持失败待实现，禁止把断言改弱来「做绿」。**

### 剩余 5 项的根因分组（下一批候选）

| 类 | 项 | 根因 | 处置建议 |
|---|---|---|---|
| `ReconciliationIntegrationTest` | 2 | 核销单状态语义：期望 `CONFIRMED` 实得 `SUBMITTED`；`reverse()` 只接受「已确认/已执行」 | 🟠 **需老丁拍板**：`review()` 是否应把 `UNCONFIRMED` 纳入白名单 |
| `ReconciliationWorkbenchE2ETest` | 1 | 同上 | 🟠 同上 |
| `SystemClearControllerIntegrationTest` | 1 | 断言全表固定总数（3 vs 10），未取 baseline | 🟢 **已于 REQ-2026-122 修复**（见 §〇之八）|
| `BudgetFlowE2ETest` | 1 | 功能未实现 | 🔴 走 SPEC 立项 |

---

## 〇之八、清理类测试隔离越界修复结果（2026-09-29 已完成，REQ-2026-122）

**结果：慢测 5 → 4 项；`SystemClearControllerIntegrationTest` 3/3 全绿；快测 1733/0 无回归。**

### 🔴 根因：全表维护操作的返回行数**天然含种子数据**

`SystemClearController.clearBusinessDocs()` 由 8 个 DML 组成：

| # | 语句 | 语义 |
|---|---|---|
| d1 | `DELETE FROM t_arap_settlement_entry` | 删 |
| d2 | `DELETE FROM t_arap_settlement` | 删 |
| d3 | `DELETE FROM t_reconciliation_log` | 删 |
| d4 | `DELETE FROM t_aging_alert` | 删 |
| d5 | `UPDATE t_bank_journal SET business_doc_id=NULL WHERE ... IS NOT NULL` | **解绑，保留行** |
| d6 | `UPDATE t_voucher SET business_doc_id=NULL WHERE ... IS NOT NULL` | **解绑，保留行** |
| d7 | `DELETE FROM t_business_doc_entry` | 删 |
| d8 | `DELETE FROM t_business_doc` | 删 |

返回的 `deleted` = 上表 8 项受影响行数之和。**6 个 DELETE 均无 `WHERE`**，所以这个和里**必然含有库中原本就存在的行**。

实测定位真凶（单跑该类时的日志）：

```
清空业务单据: settlement_entry=1, settlement=1, recon_log=0, aging_alert=0,
              bank_journal_unlink=0, voucher_unlink=0, doc_entry=0, doc=8
```

`doc=8` —— 而本用例只造了 **1 条**业务单据。差额 7 来自 **Flyway 在 `t_business_doc` 里种了 7 行**。故原断言 `assertEquals(3, deleted)`（只算自己的 1 核销明细 + 1 核销单 + 1 单据）必然失败。

### 修法：先取基线，再断言增量

类内新增两个助手（与被测方法的 8 个 DML **一一对应**，避免漂移）：

- `baselineClearOps()` —— 调用前 8 项各自的行数合计（UPDATE 项统计「`business_doc_id IS NOT NULL`」的行数，与 DML 的 WHERE 口径一致）
- `count(String sql)` —— 单表行数

3 个用例全部改为增量断言：

| 用例 | `deleted` 期望 | 保留型断言 |
|---|---|---|
| `..._withSettlementEntry_shouldSucceed` | `baseline + 3`（1 核销明细 + 1 核销单 + 1 单据）| — |
| `..._shouldUnlinkBankJournalNotDelete` | `baseline + 2`（1 单据删除 + 1 journal 解绑）| `t_bank_journal` 总数 = `journalBaseline + 1` |
| `..._shouldUnlinkVoucherNotDelete` | `baseline + 2`（1 单据删除 + 1 凭证解绑）| `t_voucher` 总数 = `voucherBaseline + 1` |

### 沉淀（AGENTS §4.4 第 16 条）

- **操作前的行数断言**必须增量；**操作后「应为空」的断言**仍可保留绝对值 0 —— 清空本就是全表语义，绝对断言成立。
- 后两例原有的 `assertEquals(1, t_bank_journal/t_voucher 总数)` 此前**只是侥幸通过**（基线恰为 0），已一并改为 `baseline + 1`。
- 这类接口的返回行数**本质上无法断言绝对值**：它会随库中任何历史数据变化而变化。

### 剩余 4 项（最终状态）

| 类 | 项 | 根因 | 处置建议 |
|---|---|---|---|
| `ReconciliationIntegrationTest` | 2 | 核销单状态语义（`execute`/`reverse` 依赖 `CONFIRMED`，实测 `SUBMITTED`）| 🟠 **需老丁拍板**：`review()` 是否应把 `UNCONFIRMED` 纳入白名单 |
| `ReconciliationWorkbenchE2ETest` | 1 | 同上 | 🟠 同上 |
| `BudgetFlowE2ETest` | 1 | 功能未实现（`t_budget_entry` 缺 4 列，`checkBudget` 会 NPE）| 🔴 走 SPEC 立项 |

---

## 〇之九、生产缺陷修复 + 一处结论更正（2026-09-29 已完成，REQ-2026-123）

**结果：修 2 个真实生产缺陷 + 更正 1 处误报 + 修 1 个测试假阳性；慢测 1990 项仍 4 项待修（无回归），快测 1735/0/0/5。**

### 🔴 缺陷 1：`MenuServiceImpl:84` 用户登录后**路由恒空**

```java
// 修复前
.filter(m -> "menu".equals(m.getType()) && m.getIsActive())
```

`t_menu` 种子数据与 CHECK `chk_menu_type` 只允许**大写** `MENU`/`BUTTON`/`DIR`，实测库中取值仅 MENU(36)/DIR(7)。小写 `menu` 永远匹配不上 → `getRoutesByUserId` 恒返回空树。已改 `"MENU"`。

### 🔴 缺陷 2：`PrepaymentServiceImpl:190,328` 预收预付**运行时必失败**

| 行 | 场景 | 原值 | 改为 | 依据 |
|---|---|---|---|---|
| 190 | 预付冲应付（`partyType=VENDOR`）| `PAYABLE` | `PAY` | `chk_settlement_type` 仅 `RECEIVE`/`PAY` |
| 328 | 预收冲应收（`partyType=CUSTOMER`）| `RECEIVABLE` | `RECEIVE` | 同上 |

`ReconciliationServiceImpl:373` 与 `ArapSettlementServiceImpl:445` 本就正确，仅需对齐口径（后者已自带注释提醒该 CHECK）。

### ⚠️ 更正：`ReportDataMapper` 并非缺陷，是**检查脚本误报**

REQ-2026-121 曾依据 `node backend/scripts/check-entity-schema.mjs` 的告警判定：

> `ReportDataMapper` 引用了列 `assist_json::text`，但 `t_voucher_entry`/`t_voucher`/`t_subject` 表均无此列

**查证结论：误报。** 三步验证：

1. `information_schema.columns` → `t_voucher_entry.assist_json` **存在**（`jsonb`），`t_subject.aux_calc_type` **存在**；
2. 把 `auxiliaryMovement()` 的 SQL 原样在真实 PG 上执行 → **退出码 0，0 行**（0 行是因为该期间无辅助核算数据，不是语法/列错误）；
3. 定位脚本缺陷：`extractColumnRefs()` 的 `cleaned` 链**未剥离 PostgreSQL `::type` 转型**，于是 `cur.assist_json::text` 被整体当作列名 `assist_json::text` 去比对 `information_schema` → 必然「不存在」。

**已修脚本**：`cleaned` 链加 `.replace(/::\s*\w+/g, ' ')`，修后输出「✅ Entity-DB 列一致性检查通过」。

> **教训**：静态检查告警 ≠ 缺陷。看到「引用了不存在的列」必须先在真实 DB 上把 SQL 跑一遍再下结论 —— 否则会把工具缺陷写进缺陷台账，浪费一轮排查。

### 🔴 连带发现：这个缺陷为什么**从来没被测出来**（双重遮蔽）

1. **fixture 与缺陷互相印证**：`MenuServiceImplTest` 的 `stubEntity()` 用 `setType("menu")` —— 和生产代码里那个错误的小写字面量一模一样，于是「生产错 + 测试也错」互相掩盖；
2. **断言形同虚设**：`getRoutesByUserId` 用例只有 `assertNotNull(result)`，而错误实现返回的是**空列表**（非 null）→ 断言照样通过（AGENTS §4.3 第 6 条「测试假阳性」）。

**修法**：fixture 改大写；断言强化为 3 项 ——

| 用例 | 断言 |
|---|---|
| `getRoutesByUserId_调selectBatchIds` | `result.size() == 1` 且名称正确（**断言内容而非非 null**）|
| `getRoutesByUserId_只收MENU_排除BUTTON与DIR` | 3 个菜单 → 只剩 1 个 MENU（**负向断言**）|
| `getRoutesByUserId_小写menu_type不应被匹配` | 小写值 → 空列表（**锁死原缺陷不复发**）|

`MenuMapperTest` 另有 4 处小写 fixture 一并改正。

### 核销状态语义：方向更正 + 3 项修复（REQ-2026-124，慢测 4 → 1）

**⚠️ 上一轮判断有误，先更正：** 问题**不是**「`review()` 是否纳入 `UNCONFIRMED`」—— 全库**没有** `review()` 方法，`ArapStatus` 常量与 `chk_reconciliation_status` CHECK 也**都没有** `UNCONFIRMED` 取值。放宽白名单的建议是伪问题。

真实状态机（`execute()` 源码带 P1-fix 注释，引用铁律 #1）：

```
execute()                  → 核销单 SUBMITTED（只提报，金额不动）
  ↓ ArapSettlementService.approve()   （人工审批：金额在此扣减 + 发票同步）
settlement = CONFIRMED，并产出 operationType=APPROVE 的审批日志
  ↓ ReconciliationService.approve()   → EXECUTED
  ↓ ReconciliationService.reject()    → REJECTED（回滚金额）
  ↓ ArapSettlementService.reverse()   → REVERSED（红冲对冲，铁律 #3）
```

**结论：生产代码正确，3 个测试断言的是重构前的旧行为。** 判据是 `execute()` 里有显式注释 `// P1-fix: 统一核销写路径 …（人审铁律：核销需人工审批才生效）`。

| 类 | 修法 |
|---|---|
| `ReconciliationIntegrationTest.execute_shouldReduceUnsettledAmount` | 改名 `execute_thenApprove_...`，改走 `execute → approve` |
| `ReconciliationIntegrationTest.reverse_shouldRestoreUnsettledAmount` | 改名 `approve_thenReverse_...`，反核销走 `ArapSettlementServiceImpl.reverse()` |
| `ReconciliationWorkbenchE2ETest` | `execute` 后断言 `SUBMITTED`，审批后再断言 `CONFIRMED` |

**新增 5 条人审铁律负向断言**（此前完全缺失 —— 这正是重构把行为抽空却无人守门的原因）：

1. 审批前 `unsettled_amount` 不得变动
2. 审批前 `settled_amount` 不得变动
3. 审批前单据状态仍为 `APPROVED`
4. 审批前核销单为 `SUBMITTED`、`trace` 回读亦为 `SUBMITTED`；审批后为 `CONFIRMED`
5. 对 `SUBMITTED` 提报单反核销必须抛 `BusinessException`

**顺带发现并已修复 1 个真实生产缺陷（REQ-2026-125）：** `t_reconciliation_log` 承载两族日志，而 `reverse()`/`reject()` 的状态门槛与回滚口径错配 ——

| 日志族 | 写入方 | `targetDocId` | 状态 |
|---|---|---|---|
| (a) 核销提报 | `ReconciliationServiceImpl.execute()` | 真实业务单据 | 恒 `SUBMITTED` |
| (b) 核销单生命周期 | `ArapSettlementServiceImpl.logReconciliationLog()` | **硬编码 `null`** | `CONFIRMED`/`VOUCHERED`/`REVERSED` |

状态门槛（`CONFIRMED`/`EXECUTED`）只放行 (b)，回滚逻辑却按 (a) 用 `targetDocId` 回查 → `selectById(null)` 返回 null → `if (doc != null)` 整块跳过 → **金额与发票状态静默不回滚，日志却置 `CANCELLED`/`REJECTED` 并返回成功**。(a) 恒 `SUBMITTED` 永远进不了反核销，故该入口**在任何真实链路下都无法回滚**。

修复：①`logReconciliationLog()` 按首条明细回填 target 维度；②`reverse()` 识别 `sourceDocType=SETTLEMENT` 即**委托红冲路径**；③缺 `targetDocId` 或单据不存在一律抛错；④`reject()` 对已生效核销单显式拒绝并指向反核销。新增 6 条回归测试（4 Mock + 2 真实 DB）。

> **教训**：`if (doc != null)` 包住回滚逻辑是**静默失败**的典型反模式 —— 回查不到实体就整块 skip，但方法继续返回成功，DB 里留下「已反核销」状态而金额纹丝不动，**比直接抛异常危险得多**。凡「回滚/同步」类逻辑，回查不到实体必须抛 `BusinessException`。另：`V144` 把 `target_doc_id` 放宽为可空只为让日志能插入，但「可空」不等于「该空」—— 放宽约束只解决「插不进去」，不解决「信息缺失导致下游查不到」。

### 剩余 1 项

| 类 | 项 | 根因 | 状态 |
|---|---|---|---|
| `BudgetFlowE2ETest` | 1 | 功能未实现（`t_budget_entry` 缺 4 列，`checkBudget` 会 `switch(null)` NPE） | 🔴 待走 SPEC 立项 |

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.7 | 2026-09-29 | opencode | 补记 **反核销/驳回静默回滚修复**（REQ-2026-125）：`t_reconciliation_log` 两族日志的「状态门槛」与「回滚口径」错配 —— 门槛只放行核销单生命周期日志（`targetDocId` 硬编码 `null`），回滚却按提报日志口径用 `targetDocId` 回查 → `selectById(null)` → `if (doc != null)` 整块跳过 → **金额与发票状态静默不回滚却返回成功**，且该入口在任何真实链路下都无法真正回滚。修复：`logReconciliationLog()` 回填 target 维度；`reverse()` 委托红冲路径；缺 `targetDocId`/单据不存在一律抛 `BusinessException`；`reject()` 禁已生效核销单并指向反核销。新增 6 条回归测试。慢测 1996 项仍 1 项待修，快测 1740/0/0/5 |
| V1.6 | 2026-09-29 | opencode | 补记 **核销人审链路测试对齐**（REQ-2026-124）：**方向更正** —— 无 `review()`、无 `UNCONFIRMED`，真实链路是 `execute`(SUBMITTED 只提报) → `ArapSettlementService.approve()`(金额在此扣减) → `CONFIRMED`/`EXECUTED`；`execute()` 带 P1-fix 注释引用铁律 #1，故生产正确、测试过期。3 项改走真实审批链并新增 5 条人审铁律负向断言。慢测 **4 → 1 项**，快测 1735/0/0/5。顺带发现 `logReconciliationLog()` 的 `setTargetDocId(null)` 导致 `reverse()`/`reject()` **静默不回滚却返回成功**（REQ-2026-125，待拍板） |
| V1.5 | 2026-09-29 | opencode | 补记 **生产缺陷修复 + 结论更正**（REQ-2026-123）：修 `MenuServiceImpl:84`（`menu`→`MENU`，用户路由恒空）、`PrepaymentServiceImpl:190,328`（`PAYABLE`/`RECEIVABLE`→`PAY`/`RECEIVE`，运行时违约）；**更正** `ReportDataMapper` 系**检查脚本误报**（`check-entity-schema.mjs` 未剥离 `::type` 转型，脚本已修）；连带修掉 `MenuServiceImplTest` 的**测试假阳性**（fixture 小写 + 断言只验非 null 双重遮蔽），断言强化为 3 项、测试 5 → 7。慢测无回归（1990 项仍 4 项待修），快测 1735/0/0/5 |
| V1.4 | 2026-09-29 | opencode | 补记 **清理类隔离越界** 5 → **4**（REQ-122，`SystemClearControllerIntegrationTest` 3/3 绿）。`clearBusinessDocs()` 是 6 个无 `WHERE` 的 DELETE + 2 个全表解绑 UPDATE，返回行数**天然含种子数据** —— 实测 `t_business_doc` 有 **7 条 Flyway 种子行**，故 `doc=8` 而非用例造的 1 条。改为「先取 `baselineClearOps()` 基线、再断言增量」；后两例的「保留型」断言由 `assertEquals(1, 总数)` 改为 `baseline + 1`（原写法只是侥幸通过）。慢测仅余 4 项：核销单状态语义 3（待拍板）+ `BudgetFlowE2ETest` 1（功能未实现待立项） |
| V1.3 | 2026-09-29 | opencode | 补记 **C 类测试数据约束** 21 → **5**（REQ-121，修 16 项，5 类根因）：补 3 个 Entity 缺失的 `NOT NULL` 字段（**2 个真实生产缺陷**：`dept_code`/`menu_code`/`budget_name`）、CHECK 允许集大小写敏感（6 项）、幽灵字段当真实列断言（4 项，`t_input_invoice` 无 `process_status`、`t_output_invoice` 无任何 jsonb 列）、隔离越界与口径错（3 项）、自动生成逻辑在 Service 覆写（1 项）、MP 乐观锁冲突返回 0 行不抛异常（1 项）。**顺带发现 3 个生产缺陷待拍板**：`MenuServiceImpl:84` 路由恒空、`PrepaymentServiceImpl:190,328` 结算类型违约、`ReportDataMapper` 引用不存在的 `assist_json` 列。`BudgetFlowE2ETest` 判定为**功能未实现**需走 SPEC |
|---|---|---|---|
| V1.2 | 2026-09-29 | opencode | 补记五批实施结果（详见 §〇之二~〇之六）：**D5 基建** 55 → 44（REQ-116，基类统一企业上下文，7 项「无上下文→放行」用例显式 `clear()`）；**悬空外键** 44 → 35（REQ-117，补 `ensureCustomer`，修 `doc_no` 只读不写、单据状态误用发票状态）；**银行流水** 35 → 32 前推（REQ-118，5 类 32 项，含 `@Version` 回填陷阱与 `REQUIRES_NEW` 互斥）；**P3 全链路**（REQ-119，3 项，同名 `status` 两表允许集不同 + 幽灵列断言）；**B 类种子撞码** 32 → **21**（REQ-120，通用 identity 序列对齐 + RBAC 硬编码关联组合，发现 `MenuEntity` 缺 `menuCode` 真实缺口）。全部批次均满足：目标类全绿 + 全量慢测无新增红项 + 快测 1733/0 |
| V1.1 | 2026-09-29 | opencode | **A 类 + D 类实施完成**：36 项转绿，全量慢测 128 → 92 项。改写 3 个 `NumberingAssociation*` 类为新模型断言（含废弃结构负向断言）；修 3 个 D 类类的 IDENTITY/悬空外键/非法枚举/无登录态问题。**纠正分诊归因**：`InvoiceConfirmAuditPathIntegrationTest` 2 项实为 C 类（`exist=false` 属性做 lambda 排序）而非 D 类。**发现并修复 1 个真实生产缺陷**（REQ-2026-115 CSV 表头「对方户名」未识别）。新增 D5 待拍板事项 |
| V1.0 | 2026-09-28 | opencode | 初稿：128 项 / 44 类分诊完成，按 A~E 五类归档，每类附实测证据与处置建议；D1-D4 待老丁确认。**未修改任何代码** |
