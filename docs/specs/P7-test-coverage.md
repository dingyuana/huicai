# P7 SPEC — 核心 Service 单测覆盖补齐

> 状态：草稿（待老丁审核 → 委 OpenCode 执行）
> 范围：3 个 service 的 Mockito 纯单测
> 不修 IntegrationTest（16 errors = H2 缺表，独立工单）

---

## 1. 背景与动机

2026-06-15 工作树清理中入库的 P1-P5 集成代码（commit cef3a6f），其中 3 个核心 service 新增了关键业务逻辑但**完全无单测**：

- `BankReconciliationServiceImpl` — 银企对账 5 维评分 + Redis 锁
- `ReconciliationServiceImpl` — 核销推荐 + 执行 + 反核销
- `SalesInvoiceImportService` — 销售发票导入去重 + 客户匹配

`BankStatementServiceImpl` 已有 `BankStatementServiceTest`（P1 期已补），**不在本工单范围**。

**MEMORY 数字修正**：
- 旧记 110/1/18（6-14 P1 bug 修后快照）→ 实测 **110/0/16**
- 16 errors 全是 `*IntegrationTest` 跑 H2 + PG 迁移脚本的兼容问题（`Table "T_OUTPUT_INVOICE" not found`），与 P7 无关
- 目标：P7 完成后 **0 fail** + 新增 ~25-30 个测试用例

---

## 2. 测试策略

| 维度 | 决策 |
|---|---|
| 框架 | JUnit 5 + Mockito（沿用 `BankStatementServiceTest` 模板） |
| 范围 | **纯单测**，不启动 Spring 上下文，不依赖数据库 |
| Mock 范围 | 所有 `*Mapper` + 跨 service 依赖 + `RedisTemplate` |
| 命名 | `xxxTest` 后缀，方法名 `方法名_场景_预期` 三段式 |
| 路径 | `backend/src/test/java/com/huicai/module/{finance,arap}/service/impl/` |
| 工具断言 | AssertJ 不引入；用 `org.junit.jupiter.api.Assertions` + `org.mockito.Mockito` |

---

## 3. 测试用例清单

### 3.1 `BankReconciliationServiceImplTest`（finance）

**文件**：`backend/src/test/java/com/huicai/module/finance/service/impl/BankReconciliationServiceImplTest.java`

**Mock 依赖**（4 mapper + 1 redis）：
- `BankAccountMapper accountMapper`
- `BankJournalMapper journalMapper`
- `BankStatementMapper statementMapper`
- `RedisTemplate<String, Object> redisTemplate`

| # | 方法名 | 测试场景 | 关键断言 |
|---|---|---|---|
| 1 | `generateAdjustment_账户不存在_throwIAE` | accountMapper 返回 null | `IllegalArgumentException` |
| 2 | `generateAdjustment_企业大于银行_diff为正_balancedFalse` | enterprise=1000, bank=800 | diff=200, balanced=false |
| 3 | `generateAdjustment_企业等于银行_balancedTrue` | 两边相等 | balanced=true |
| 4 | `summarize_混合状态_各类计数正确` | journals 2 matched / 1 unmatched, stmts 1 MATCHED + 1 PENDING_CONFIRM + 1 IGNORED | 各字段数值 |
| 5 | `unmatchedItems_4方向分类_返回4类` | 各方向 1 条数据 | rows.size() == 4，type 字段各取期望值 |
| 6 | `calculateScore_完全匹配_返回100` | stmt 和 journal 全字段一致 | total=100, amount=50, date=20, name=15, desc=10, ref=5 |
| 7 | `calculateScore_金额差0.5%_线性衰减` | amount 1000 vs 995（0.5%） | amount=25, total=70（desc/date/name/ref 需另算） |
| 8 | `calculateScore_日期差2天_10pt` | 间隔 2 天 | dateScore=10 |
| 9 | `calculateScore_名称Levenshtein80%_10pt` | 相似度 80% | nameScore=10 |
| 10 | `calculateScore_statement不存在_返回0+备注` | statementMapper 返回 null | total=0, remark 含"不存在" |
| 11 | `lockReconciliation_首次锁定_true` | redis.opsForValue().setIfAbsent 返回 true | 返回 true |
| 12 | `lockReconciliation_已被锁定_false` | setIfAbsent 返回 false | 返回 false |
| 13 | `unlockReconciliation_操作者匹配_delete` | redis.get 返回相同 operator | redis.delete 被调用 |
| 14 | `unlockReconciliation_操作者不匹配_不delete` | redis.get 返回其他 operator | redis.delete **0 次** |
| 15 | `runMatching_分数>=85_自动MATCHED` | stmt+journal 完美匹配 | results[0].matchStatus="MATCHED" |
| 16 | `runMatching_分数60-84_PENDING_CONFIRM` | 中等匹配 | matchStatus="PENDING_CONFIRM" |
| 17 | `runMatching_分数<60_UNMATCHED` | 错配 | matchStatus="UNMATCHED" |
| 18 | `runMatching_无journal_全UNMATCHED` | journalMapper.selectUnreconciled 空 | 所有 stmt 都 UNMATCHED |

**目标 18 用例**（含 5 维评分 + 阈值路由）。

---

### 3.2 `ReconciliationServiceImplTest`（arap）

**文件**：`backend/src/test/java/com/huicai/module/arap/service/impl/ReconciliationServiceImplTest.java`

**Mock 依赖**（5 mapper + 1 service）：
- `ReceivableMapper receivableMapper`
- `PayableMapper payableMapper`
- `CustomerMapper customerMapper`
- `VendorMapper vendorMapper`
- `ReconciliationLogMapper logMapper`
- `ArapSettlementService settlementService`

| # | 方法名 | 测试场景 | 关键断言 |
|---|---|---|---|
| 1 | `recommendReceipt_无客户ID_返回空items` | customerId=null | items 为空 list |
| 2 | `recommendReceipt_应收未结清_L1_优先级最高` | 1 张应收 unsettled=100, externalNo="100" | matchLevel="L1" |
| 3 | `recommendReceipt_2张应收_按L级别排序` | 1 张 L4 + 1 张 L5 | items[0]=L4, items[1]=L5 |
| 4 | `recommendPayment_无供应商ID_返回空items` | vendorId=null | items 为空 |
| 5 | `recommendPayment_应付已结清_跳过` | unsettled=0 | items 为空 |
| 6 | `recommendForStatement_收款方向_查客户表` | direction="in", counterparty="A" | customerMapper.selectList 被调用 |
| 7 | `recommendForStatement_付款方向_查供应商表` | direction="out", counterparty="B" | vendorMapper.selectList 被调用 |
| 8 | `execute_金额为null_throw` | request.amount()=null | BusinessException |
| 9 | `execute_INVOICE_OUT_更新应收并插入日志` | target=INVOICE_OUT | receivableMapper.updateById + logMapper.insert 被调用 |
| 10 | `execute_应收不存在_throw` | receivableMapper.selectById=null | BusinessException |
| 11 | `execute_有period_调用settlementService` | period="202606" | settlementService.create 被调用 |
| 12 | `execute_无period_跳过settlement` | period=null/blank | settlementService.create 0 次 |
| 13 | `preCheck_5项全过_allPassedTrue` | 全部条件满足 | allPassed=true, checks.size()=5 |
| 14 | `preCheck_sourceDocId为null_第1项失败` | sourceDocId=null | 第 1 项 passed=false |
| 15 | `preCheck_金额<=0_第5项失败` | amount=0 | 第 5 项 passed=false |
| 16 | `pageLogs_正常分页` | current=1, size=10 | logMapper.selectPage 被调用 |
| 17 | `reverse_正常_INVOICE_OUT恢复应收` | logId=1, status=CONFIRMED | receivableMapper.updateById + log.setStatus("CANCELLED") |
| 18 | `reverse_记录不存在_throw` | logMapper.selectById=null | BusinessException |
| 19 | `reverse_状态非CONFIRMED_throw` | status=REVERSED | BusinessException |
| 20 | `batchExecute_2个请求_调2次execute` | 2 个 request | execute 调 2 次 |

**目标 20 用例**。

---

### 3.3 `SalesInvoiceImportServiceTest`（finance）

**文件**：`backend/src/test/java/com/huicai/module/finance/service/impl/SalesInvoiceImportServiceTest.java`

**Mock 依赖**（5 mapper + 1 service + 1 resolver）：
- `BusinessDocMapper docMapper`
- `BusinessDocEntryMapper docEntryMapper`
- `VoucherMapper voucherMapper`
- `VoucherEntryMapper voucherEntryMapper`
- `VoucherNoService voucherNoService`
- `CustomerMapper customerMapper`
- `SubjectMapper subjectMapper`
- `OutputInvoiceMapper outputInvoiceMapper`
- `ColumnMappingResolver columnMappingResolver`

**关键发现（核对代码后修正）**：
- `findExistingInvoiceNos` / `ensureStandardSubjects` / `matchOrCreateCustomer` 全部是 `private`
- `ParsedInvoiceRow` 是 **package-private static class**（同包可访问，无需改产品代码）
- `ensureSubject` 内部调 4 次，覆盖 4 个标准科目：1122 / 5001 / 2221 / 2221.01
- `matchOrCreateCustomer` 内部 4 路匹配：税号 → 全名 → 短名（去括号）→ 创建

**难点**：`previewInvoices` / `commit` 需 `MultipartFile`，本工单**只测可纯单测部分**。

| # | 方法名 | 测试场景 | 关键断言 |
|---|---|---|---|
| 1 | `findExistingInvoiceNos_3个发票号_2个已存在_返回2` | stub 3 row，mock outputInvoiceMapper 返回 2 条 | result.size()=2 |
| 2 | `findExistingInvoiceNos_空发票号_返回空集` | 所有 row.invoiceNo=null/blank | 返回空 Set，**不调 mapper** |
| 3 | `ensureStandardSubjects_科目全不存在_插入4次` | findSubjectByCode 全部返回 null | subjectMapper.insert 调用 4 次 |
| 4 | `ensureStandardSubjects_科目全存在_不插入` | 4 个 code 都已存在 | subjectMapper.insert **0 次** |
| 5 | `ensureStandardSubjects_2221.01父科目非叶子_更新父` | 2221 存在且 isLeaf=true | subjectMapper.updateById 被调用（父级非叶子化） |
| 6 | `matchOrCreateCustomer_税号匹配上_返回ID` | buyerTaxId 命中 | 返回 customerId，不调 insert |
| 7 | `matchOrCreateCustomer_名称匹配上_返回ID` | buyerTaxId 空，buyerName 命中 | 返回 customerId，不调 insert |
| 8 | `matchOrCreateCustomer_短名匹配上_返回ID` | 名称"ABC（北京）科技" → "ABC科技" ≥4 字 | 返回 customerId |
| 9 | `matchOrCreateCustomer_全无匹配_创建客户` | 3 路 selectList 全空 | customerMapper.insert 被调用，返回新 ID |
| 10 | `matchOrCreateCustomer_名称和税号全空_返回null` | 都 blank | 返回 null，不调任何 mapper |
| 11 | `parseInvoiceDate_yyyyMMdd_正确解析` | "20260615" | LocalDate.of(2026,6,15) |
| 12 | `parseInvoiceDate_ISO_正确解析` | "2026-06-15" | LocalDate.of(2026,6,15) |
| 13 | `parseInvoiceDate_空白_返回null` | "" | null |

> **可见性问题（OpenCode 落地决策点）**：
>
> `findExistingInvoiceNos` / `ensureStandardSubjects` / `matchOrCreateCustomer` 都是 `private`。`ParsedInvoiceRow` 是 `package-private static class`，同包（`com.huicai.module.finance.service.impl`）测试可访问。
>
> 三种方案：
> - **A：反射调用**（不改动生产代码，最脆弱）
> - **B：方法升 package-private**（最小可见性改动）
> - **C：新增 public 包装方法暴露给测试**（最重）
>
> **倾向 B**：把 3 个 private 方法改为 package-private（去掉 `private` 关键字），`ParsedInvoiceRow` 已经是 package-private。OpenCode 落地时确认这是唯一会改产品代码的点，且只改 3 个方法可见性，不改逻辑。

**目标 13 用例**（如选 A 反射方案失败可降到 8-10 个）。

---

## 4. 验收标准

1. `mvn test -pl backend` 跑通，**0 failure**
2. 16 errors 数字**不变**（与 P7 无关）
3. 新增测试总数 **≥ 40**（18+20+13 = 51 上限）
4. 所有新测试方法命名遵循三段式 `方法_场景_预期`
5. 无新增依赖（不引入 AssertJ、Testcontainers 等）

---

## 5. 执行流程

1. 老丁审核本 SPEC
2. 委 OpenCode 写 3 个 Test 文件
3. OpenCode 跑 `mvn test`，输出数字
4. Hermes 审核 diff + 测试结果
5. 单独 commit（不带其他改动）→ push
6. 跑全量 `mvn test` 验 0 fail

---

## 6. 风险与决策点

| 风险 | 应对 |
|---|---|
| private 方法测不了 | 选 B（升 package-private）— OpenCode 落地时确认 |
| 反射脆弱 | 优先 B，B 不可行才 A |
| RedisTemplate mock 链长 | 用 `Mockito.lenient()` 容忍未使用的 stub |
| IntegrationTest 干扰 | 不在 P7 范围，事后另开工单 |

---

## 7. 工单后续（不在 P7 内）

- 16 errors H2 兼容 → P8 工单
- arap 其它 service 零单测（Customer/Supplier/Receivable/Payable/ArapSettlement/BadDebt）→ P9 工单
- BankReconciliation 评分细节（levenshtein/jaccard）有边界 bug 风险 → 后续专项

---

_创建于 2026-06-15 14:xx_ · _作者: Hermes (待 OpenCode 执行)_
