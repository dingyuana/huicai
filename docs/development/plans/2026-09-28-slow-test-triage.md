# 慢测全量 128 项失败 — 分诊清单

> **创建日期**：2026-09-28
> **状态**：🟡 分诊完成；**A / D / D5(C类主体) 已修复并验证**（73 项），剩余 55 项待修
> **数据来源**：`mvn test -DexcludedGroups=`（含 slow 组）全量运行
> **基线**：`main @ 3ab1a606`｜快测 1733/0 failures 正常，慢测 1980 中 **16 Failures + 112 Errors**
> **A+D 修复后**：慢测 **1984 中 1 Failure + 91 Errors = 92 项**
> **D5 修复后**：慢测 **1984 中 7 Failures + 48 Errors = 55 项 / 33 类**
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

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.1 | 2026-09-29 | opencode | **A 类 + D 类实施完成**：36 项转绿，全量慢测 128 → 92 项。改写 3 个 `NumberingAssociation*` 类为新模型断言（含废弃结构负向断言）；修 3 个 D 类类的 IDENTITY/悬空外键/非法枚举/无登录态问题。**纠正分诊归因**：`InvoiceConfirmAuditPathIntegrationTest` 2 项实为 C 类（`exist=false` 属性做 lambda 排序）而非 D 类。**发现并修复 1 个真实生产缺陷**（REQ-2026-115 CSV 表头「对方户名」未识别）。新增 D5 待拍板事项 |
| V1.0 | 2026-09-28 | opencode | 初稿：128 项 / 44 类分诊完成，按 A~E 五类归档，每类附实测证据与处置建议；D1-D4 待老丁确认。**未修改任何代码** |
