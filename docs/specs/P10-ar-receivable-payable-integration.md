# P10 SPEC — 发票+银行流水 端到端应收/应付/核销自动接入
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效
> **关联PRD**：../prd/应收应付核销-PRD-V1.0.md

> **编号**：HUICAI-SPC-010 — V74 已删除 t_receivable/t_payable，本 SPEC 描述的独立应收/应付表架构已不适用
> **当前架构**：统一使用 t_business_doc（INVOICE_OUT/INVOICE_IN），详见 P34 SPEC
> **历史价值**：P10 的 4 批 commit 实现了发票→应收/应付自动写入，为后续 P34 业务单据统一化提供了前提
> mvn test: 211 → 235 (+24 测试, 0 fail, 0 error)

---

> **关联需求**: REQ-2026-012, REQ-2026-013, REQ-2026-014

## SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 2. 各工单详细方案（API 端点/数据模型）](#2-各工单详细方案)

### 2. 输出契约
→ 见本文 [## 3. 测试与验收（测试矩阵/端到端冒烟）](#3-测试与验收)

### 3. 状态流转
→ 见本文 [各工单详细方案中描述的流程状态流转](#2-各工单详细方案)

### 4. 异常处理
→ 见本文各工单中的 BusinessException 抛出点/错误处理说明

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
| 销售发票 Excel 导入 (`SalesInvoiceImportService.confirmImport`) | 写 `t_output_invoice`（PENDING_CONFIRM）+ `t_business_doc`(INVOICE_OUT, DRAFT) + 应收单(DRAFT) + 凭证(DRAFT) — 全部在 `confirm()` 审核通过时一次性生成 | ✅ **写（P31）** |
| 采购发票 Excel 导入 | **整个 ImportService 不存在** | ❌ **完全不接** |
| 银行流水 B 类确认 (`AutoGenerationService.generateDocThenVoucher`) | 写 `t_business_doc`(RECEIPT/PAYMENT) + 凭证 | ❌ **不写也不核销** |

**好消息**：`ReceivableService.create` / `PayableService.create` 接口完整（P9 已测），`ReconciliationService.recommendReceipt/recommendPayment` 算法可用（P5+ P7+ P9 全测）。**所有底层能力都在，只缺上层接通**。

### 0.3 成功标准

跑通以下 4 个端到端测试场景：
- **S1** 销售发票导入 → t_business_doc + t_output_invoice + t_receivable 都有记录
- **S2** 采购发票导入（新建）→ t_business_doc + t_input_invoice + t_payable 都有记录
- **S3** 银行收款流水 B 类确认 → t_business_doc(RECEIPT) + DRAFT 凭证，**不做自动核销**，用户在核销工作台手工匹配
- **S4** 银行付款流水 B 类确认 → t_business_doc(PAYMENT) + DRAFT 凭证，**不做自动核销**，同上

---

## 1. 工单分批（4 批，独立可回滚）

| 批 | 工单 | 文件改动 | 风险 | 价值 |
|---|---|---|---|---|
| **P10-1** | 销售发票导入补写应收单 | `SalesInvoiceImportService` + 1 Mapper 注入 | ✅ 低 | ✅ 高 |
| **P10-2** | 采购发票导入新建（仿销售版） | 新建 `InputInvoiceImportService` + Controller | ✅ 中 | ✅ 高 |
| **P10-3** | 银行流水 B 类补写应收/应付单（不自动核销） | `AutoGenerationService` + 4 Mapper 注入 | ✅ 中 | ✅ 中 |
| **P10-4** | ~~银行流水 B 类确认后自动触发核销~~ **已移除**（P34 铁律：系统不允许自动核销） | ~~`AutoGenerationService` 嵌入 `ReconciliationService.execute`~~ | ✅ ~~高~~ | ~~高~~ |\n| **P10-4** 已被移除，核销改为人工通过核销工作台操作。|\n\n**推荐顺序**：1 → 2 → 3，每批 1 commit + 跑通测试 + 你过目后再进下批。

---

## 2. 各工单详细方案

### P10-1：销售发票导入补写应收单

**P31 修正（2026-06-26）**：销售发票导入的流程已从"导入时直接生成全部"改为"导入→人工审核→自动生成"。
应收单和凭证不再在导入时生成，而是在 `confirm()` 审核通过后的 `postProcessAfterInvoiceConfirm()` 中统一生成。

**当前流程**：
```
导入 → PENDING_CONFIRM → 提交审核 → PENDING_REVIEW → 人工审核(confirm) 
  → 自动生成业务单(DRAFT) + 应收单(DRAFT) + 凭证(DRAFT) 
  → 发票状态=VOUCHERED → 凭证人工审核(凭证管理页面)
```

**改动位置**：`OutputInvoiceStateMachineServiceImpl.postProcessAfterInvoiceConfirm()`，统一管理
- `createBusinessDocFromInvoice()` — 创建业务单 + 分录
- `createReceivableFromInvoice()` — 创建应收单（关联 docId）
- `taxService.generateVoucherFromInvoice()` — 创建凭证
- 最后同步 `voucherId` 到业务单和应收单

**单测**（P31 新增）：
- `confirm_应当自动生成业务单_应收单_凭证`：mock 各 mapper，断言 insert 和 update 次数正确
- `confirm_模板匹配失败_走硬编码降级`：验证降级路径

**Commit**：`P31: 销售发票确认后自动生成应收单+凭证`

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

### ~~P10-4：银行流水 B 类确认后自动触发核销~~ **已移除**\n\n> **2026-07-03 移除原因**：P34 铁律规定系统不允许自动核销。核销全部通过核销工作台人工操作。\n> `autoReconcileFifo` 调用已从 `AutoGenerationService.createReceivableOrPayableFromBankDoc` 中删除。\n\n此批工单不再需要。银行流水 B 类确认后：
- **只生成 DRAFT 业务单据（收款单/付款单）**
- **不做自动核销**，用户通过核销工作台手工匹配执行核销
- P12-3 预收/预付款路径保持不变（创建 DRAFT prepayment）

**相关代码已修改**：`createReceivableOrPayableFromBankDoc` 中 `autoReconcileFifo` 调用已删除，替换为 log 提示用户去核销工作台操作。

**受影响测试**：如果有测试用例验证 P10-4 自动核销行为，需同步更新。

**P10-4 旧内容已全部移除**（自动核销逻辑不再使用）。

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

一、**工单分批顺序**（1→2→3）接受？  
二、**P10-4 已移除**（P34 铁律：系统不允许自动核销，核销改由核销工作台人工操作）  
三、**P10-3 只生成单据不自动核销**（已执行）接受？  
四、**P10-2 采购凭证硬编码科目**（不动模板）接受？  
五、**先开工 P10-1**（最高 ROI，最低风险）？  

我倾向**全部接受 + 先 P10-1**。等你逐项回。

---

## BDD 验收标准

### 场景 1：销项发票导入后自动生成应收单
**Given** 用户导入一张销售发票 Excel 并执行审核确认
**When** confirm() 操作完成
**Then** 应收单 `t_receivable` 中应有一条记录，其 `invoice_id` 关联该发票，且 `status = CONFIRMED`

### 场景 2：银行收款流水确认后仅生成业务单据，不做自动核销
**Given** 一条银行收款流水（B 类）被出纳确认
**When** 审核流程执行完毕
**Then** 系统生成 `t_business_doc`(RECEIPT, DRAFT) 和凭证(DRAFT)，但 `t_reconciliation_log` 中无对应核销记录

### 场景 3：采购发票导入生成应付单
**Given** 用户导入一张采购发票 Excel
**When** 导入服务执行成功
**Then** `t_payable` 表中应生成一条应付单记录，`vendor_id` 正确关联供应商，`status = CONFIRMED`
