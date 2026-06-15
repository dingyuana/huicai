# P10 SPEC — 发票+银行流水 端到端应收/应付/核销自动接入

> 状态：开发方案（待老丁审核 → 委 Hermes/OpenCode 执行）
> 目标：把"t_receivable/t_payable 是空表+可写"的状态，打通到"发票导入/流水确认后自动写入"
> 工期：分 4 批工单（每批 1 个 commit、可独立回滚）

---

## 0. 范围与目标

### 0.1 业务目标

财务双记账原则下，**两个最常见的"债权债务产生"入口**必须分别落地：

1. **销项发票** → 客户欠我钱（应收 `t_receivable`）
2. **进项发票** → 我欠供应商钱（应付 `t_payable`）
3. **银行收款流水** → 客户给我打钱（核销应收 `t_receivable`）
4. **银行付款流水** → 我给供应商打钱（核销应付 `t_payable`）

### 0.2 现状（已 100% 验证）

| 入口 | 现状 | 是否写 t_receivable/payable |
|---|---|---|
| 销售发票 Excel 导入 (`SalesInvoiceImportService.confirmImport`) | 写 `t_business_doc`(INVOICE_OUT) + 凭证 + `t_output_invoice` | ❌ **不写** |
| 采购发票 Excel 导入 | **整个 ImportService 不存在** | ❌ **完全不接** |
| 银行流水 B 类确认 (`AutoGenerationService.generateDocThenVoucher`) | 写 `t_business_doc`(RECEIPT/PAYMENT) + 凭证 | ❌ **不写也不核销** |

**好消息**：`ReceivableService.create` / `PayableService.create` 接口完整（P9 已测），`ReconciliationService.recommendReceipt/recommendPayment` 算法可用（P5+ P7+ P9 全测）。**所有底层能力都在，只缺上层接通**。

### 0.3 成功标准

跑通以下 4 个端到端测试场景：
- **S1** 销售发票导入 → t_business_doc + t_output_invoice + t_receivable 都有记录
- **S2** 采购发票导入（新建）→ t_business_doc + t_input_invoice + t_payable 都有记录
- **S3** 银行收款流水 B 类确认 → t_business_doc(RECEIPT) + 凭证 + 应收核销记录
- **S4** 银行付款流水 B 类确认 → t_business_doc(PAYMENT) + 凭证 + 应付核销记录

---

## 1. 工单分批（4 批，独立可回滚）

| 批 | 工单 | 文件改动 | 风险 | 价值 |
|---|---|---|---|---|
| **P10-1** | 销售发票导入补写应收单 | `SalesInvoiceImportService` + 1 Mapper 注入 | ✅ 低 | ✅ 高 |
| **P10-2** | 采购发票导入新建（仿销售版） | 新建 `InputInvoiceImportService` + Controller | ✅ 中 | ✅ 高 |
| **P10-3** | 银行流水 B 类补写应收/应付单（不自动核销） | `AutoGenerationService` + 4 Mapper 注入 | ✅ 中 | ✅ 中 |
| **P10-4** | 银行流水 B 类确认后自动触发核销 | `AutoGenerationService` 嵌入 `ReconciliationService.execute` | ✅ **高** | ✅ 高 |

**推荐顺序**：1 → 2 → 3 → 4，每批 1 commit + 跑通测试 + 你过目后再进下批。

---

## 2. 各工单详细方案

### P10-1：销售发票导入补写应收单

**改动文件**：
- `SalesInvoiceImportService.java` — 注入 `ReceivableService` + `ReceivableMapper`
- 新建 `t_receivable` 写入逻辑

**改动点**（行 309-311 增加 1 步）：

```java
// 现有 3 步
BusinessDocEntity doc = createBusinessDoc(row, customerId, period, batchId);  // 309
createVoucher(doc, row, customerId, period);                                  // 310
insertOutputInvoice(row, customerId, period, doc);                           // 311
// 新增第 4 步
createReceivableFromInvoice(doc, row, customerId, period);                   // 312
```

**应收单创建逻辑**（新增方法）：
```java
private void createReceivableFromInvoice(BusinessDocEntity doc, ParsedInvoiceRow row,
                                          Long customerId, String period) {
    ReceivableEntity recv = new ReceivableEntity();
    recv.setCustomerId(customerId);
    recv.setDocId(doc.getId());              // 关联 t_business_doc
    recv.setVoucherId(doc.getVoucherId());   // 关联凭证
    recv.setPeriod(period);
    recv.setTxDate(row.invoiceDate);
    recv.setAmount(row.totalAmount);
    recv.setSettledAmount(BigDecimal.ZERO);
    recv.setUnsettledAmount(row.totalAmount);  // 关键：未结清 = 全额
    recv.setSummary(row.goodsName);
    receivableMapper.insert(recv);
}
```

**单测**（在 P7 已有 `SalesInvoiceImportServiceTest` 上加）：
- `confirmImport_应收单已生成`：mock 应收 mapper，断言 insert 1 次
- `confirmImport_部分重复_应收单只生成新增部分`：复用 P7 已有去重测试

**Commit**：`feat(finance): 销售发票导入自动生成应收单`

---

### P10-2：新建采购发票导入服务

**新建文件**：
- `InputInvoiceImportService.java`（仿 `SalesInvoiceImportService`）
- `InputInvoiceController.java`
- `InputInvoiceImportServiceTest.java`

**差异点**（vs 销售版）：
1. 实体类：`InputInvoiceEntity` 而非 `OutputInvoiceEntity`
2. 客户/供应商：`VendorMapper` 而非 `CustomerMapper`
3. 写 `t_payable` 而非 `t_receivable`
4. 凭证方向相反：借 5001 收入 / 贷 1122 应收账款 → 销售；**采购**：借 1122 应收账款 / 借 2221.01 进项税 / 贷 2202 应付账款

```
借：应收账款-客户 (1122) — 不含税
借：应交税费-进项税 (2221.01) — 税额
   贷：应付账款-供应商 (2202) — 价税合计
```

（注：详细科目映射参考现有 `VoucherTemplateService.matchByClassification`，**P10-2 不动模板**，按硬编码实现。）

5. 应付单字段：`vendorId` 而非 `customerId`

**导入预览 API**：
```
POST /api/v1/input-invoices/import (MultipartFile)
POST /api/v1/input-invoices/preview (MultipartFile)
```

**单测**（同 P7 模板，13 个用例）。

**Commit**：`feat(tax+finance): 采购发票 Excel 导入 + 自动生成应付单`

---

### P10-3：银行流水 B 类补写应收/应付单

**改动文件**：
- `AutoGenerationService.java` — 注入 `ReceivableService` + `PayableService`（或直接 `ReceivableMapper`/`PayableMapper`）

**改动点**（`generateDocThenVoucher` 行 281-292 之后增加）：

```java
// 现有：生成业务单据 + 凭证
// 新增：如果是 B 类且能识别对方，写应收/应付单
String party = stmt.getCounterName();
if (StrUtil.isNotBlank(party) && type.equals("B")) {
    if (stmt.getDirection().equals("IN")) {  // 收款
        Long customerId = findCustomerByName(party);
        if (customerId != null) {
            createReceivableFromBankDoc(doc, customerId, stmt, period, amount, userId);
        }
    } else {  // 付款
        Long vendorId = findVendorByName(party);
        if (vendorId != null) {
            createPayableFromBankDoc(doc, vendorId, stmt, period, amount, userId);
        }
    }
}
```

**核心问题**：`findCustomerByName` / `findVendorByName` 是新方法。**P10-3 工单里实现**（实际就是把现有 `CustomerMapper`/`VendorMapper` 的 selectByName 暴露出来，或新加 SQL）。

**单测**（在 P7 已有 `BankReconciliationServiceImplTest` 基础上——不对，这是别的 service。需要在 P10-3 单独写新 Test，或在已有 `AutoGenerationService` 写 Test 文件）：
- 新建 `AutoGenerationServiceTest.java`
- 测试 `generateDocThenVoucher_B类收款_有客户_生成应收单`
- 测试 `generateDocThenVoucher_B类付款_有供应商_生成应付单`
- 测试 `generateDocThenVoucher_B类收款_无客户_跳过应收单生成`

**Commit**：`feat(finance): 银行流水 B 类确认后自动生成应收/应付单`

**P10-3 不做自动核销**——只生成单据，不调用 `ReconciliationService`。这是 P10-4 的事。

---

### P10-4：银行流水 B 类确认后自动触发核销

**这是最难的一批**——跨模块事务 + 状态机。

**改动文件**：
- `AutoGenerationService.java` — 注入 `ReconciliationService`

**改动点**（P10-3 之后追加）：

```java
// 在生成应收/应付单之后，自动尝试核销
if (receivableCreated) {
    // L1-L5 匹配 + 自动执行
    ReconciliationResult result = reconciliationService.autoMatch(
        "bank_txn", stmt.getId(), amount, party, stmt.getTxDate()
    );
    // 自动 executed？还是 pending_review 等人审？
}
```

**关键决策点（你定）**：
- 核销后是 `executed`（系统自动核销完）还是 `pending_review`（生成草稿等人审）？
- 老丁硬约束："人是唯一审核主体"——**必须是 `pending_review`**，系统永远不自动 executed
- P5 设计：`matched → confirmed → pending_review → approved → executed` 5 段式

**P10-4 实现路径**：
- `createReceivableOrPayableFromBankDoc` 中，在 `receivableMapper.insert` 后调 `reconciliationService.execute`
- `execute` 内部状态设为 `CONFIRMED`（已确认、待审批执行），**不是 auto-executed**
- 人在核销待审池里点"批准"才到 `executed`

**单测**：3 个，验证 execute 被正确调用。

**Commit**：`feat(finance+arap): 银行流水 B 类确认后自动串联核销（停在 CONFIRMED 状态）`

**风险**：
- 跨模块 `@Transactional` 边界（finance 调 arap）— 测试事务回滚
- 状态机错乱（如果核销失败，流水已确认但没核销，要不要回滚流水？）— 选不回滚，留 warning 日志
- 性能（B 类批量确认时，N 条流水 × 5 级匹配 = O(N×5) SQL）— 接受，加批量接口

---

## 3. 测试与验收

### 3.1 测试矩阵

每批工单必须通过：

```
mvn test -q 
  → Tests run: 211 (P7 前) + P10-1 新增 3 + P10-2 新增 15 + P10-3 新增 3 + P10-4 新增 3
  → Tests run: 235, Failures: 0, Errors: 0
```

### 3.2 端到端冒烟（手动）

`docs/specs/P10-端到端冒烟脚本.md`（P10-1 落地时写）：
1. 用真实 PG 跑 P1 的银行流水 + 销售发票
2. 检查 4 张表（t_business_doc / t_output_invoice / t_receivable / t_payable / t_reconciliation_log）记录数

---

## 4. 不在 P10 范围

- **网银 API 导入**（P3 老坑）— 独立
- **AI 分类升级**（P3 节点四）— 独立
- **规则模型 v2**（P3 节点三）— 独立
- **采购发票 OCR**（P5 已部分实现）— 独立
- **费用报销单→银行付款**（你最初 5 分支图里"个人（员工）"分支）— 独立

P10 只解决"应收/应付自动生成 + 银行流水自动核销串联"一件事。

---

## 5. 工作流（与老丁已有铁律一致）

按"先 commit，再下一步"：

```
P10-1 → 委 Hermes 写测试 + 改 service → mvn test 通过 → 你过目 → commit → push
  ↓
P10-2 → 同上
  ↓
P10-3 → 同上
  ↓
P10-4 → 同上（最大风险批次，你重点审）
```

每批独立可回滚。如果 P10-3 失败，P10-1/P10-2 仍保留价值。

---

## 6. 决策点（你回我）

按"列表化决策模式"——以下每项单字回：

一、**工单分批顺序**（1→2→3→4）接受？  
二、**P10-4 自动核销停在 pending_review**（不自动 executed）接受？  
三、**P10-3 只生成单据不自动核销**（拆给 P10-4）接受？  
四、**P10-2 采购凭证硬编码科目**（不动模板）接受？  
五、**先开工 P10-1**（最高 ROI，最低风险）？  

我倾向**全部接受 + 先 P10-1**。等你逐项回。
