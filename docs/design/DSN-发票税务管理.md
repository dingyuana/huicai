# 06-发票与税务管理设计

> **关联PRD**：../prd/发票税务管理-PRD-V1.0.md
> **关联SPEC**：P13-tax-declaration.md, P36-invoice-reverse-chain.md, P36-1-red-flush-voucher.md, P40-input-invoice-state-machine.md, P41-invoice-driven-finance.md, P57-declare-status-split.md, P58-invoice-payment-reconcile.md, P61-vat-appendix-and-burden.md
> **编号**：HUICAI-DES-007
> **版本**：V2.0 | **修改日期**：2026-08-27 | **修改人**：Hermes | **修改内容**：对齐实现(进销项字段/P57认证申报/P58勾稽/P61附表/P36.1红冲凭证)
> 代码包：`com.huicai.module.tax`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：税务合规与发票管理。进销项发票手工录入/税控盘导入，增值税计算，申报表生成。

**超越传统之处：**
- 传统：手工录入或税控盘导入 → 当前：**Excel自动化导入 + 价税分离 + 容错（匿名客户回退）**
- 传统：简单状态 → 当前：**进销项均实现8态状态机 + 进项独立认证/申报双态**（P40/P57）
- 当前新增：**AI 科目映射**（规则→pgvector→LLM三阶段）+ **异常检测**（品名背离/时间异常）+ **红冲三情况账务处理**（P36.1）+ **发票-付款勾稽视图**（P58）+ **增值税进销项附表 + 税负分析**（P61）

## 2. 核心组件

| 组件 | 说明 | 关联SPEC |
|------|------|---------|
| OutputInvoiceService | 销项发票CRUD | P41 |
| InputInvoiceService | 进项发票CRUD | P41 |
| OutputInvoiceStateMachineService | 销项发票8态状态机 | P41 |
| InputInvoiceStateMachineService | 进项发票8态状态机 | P40 |
| SalesInvoiceImportService | 销项发票Excel导入（列名映射+价税分离+防重） | P41 |
| InputInvoiceImportService | 进项发票导入（对称逻辑） | P41 |
| InvoiceDedupUtil | 跨表发票号查重 | P41 |
| ColumnMappingResolver | Excel 列名别名映射 | P41 |
| TaxService | 税金计算、申报、附表、税负分析 | P13/P57/P61 |
| InvoicePaymentReconcileService | 发票-付款勾稽只读视图 | P58 |
| RedFlushVoucherGenerator | 红冲发票三种情况凭证生成 | P36-1 |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_output_invoice | 销项发票 | invoice_no, period, customer_id, customer_name, amount, tax_rate, tax_amount, total_amount, amount_ex_tax, status, invoice_type, ai_risk_tag, ai_mapping_result, process_status, doc_id, voucher_id, doc_no, voucher_no, reversed_from, reversed_by_invoice_id, original_invoice_no, reverse_reason, original_voucher_id, original_certification_status, original_invoice_id, reversed_by_invoice_no, remark |
| t_input_invoice | 进项发票 | invoice_no, period, vendor_id, vendor_name, amount, tax_rate, tax_amount, total_amount, amount_ex_tax, status, invoice_type, certification_status, deduction_period, deduction_amount, ai_risk_tag, ai_mapping_result, process_status, doc_id, doc_no, voucher_id, voucher_no, reverse_reason, original_voucher_id, original_certification_status, declared_status, declared_period, reversed_from, original_invoice_no, remark |
| t_tax_type | 税种 | code, name, rate |
| t_tax_declaration | 纳税申报 | period, declaration_no, status, period_type, declared_amount, paid_amount |
| t_arap_settlement | 核销单(P1统一) | settlement_no, source_doc_type, source_doc_id, target info, status(DRAFT/SUBMITTED/CONFIRMED/CANCELLED) |

**P36.1 进项红冲新增字段 (V138):**
- t_input_invoice: reverse_reason(INVOICE_ERROR/RETURN/DISCOUNT/OTHER), original_voucher_id, original_certification_status
- 进项转出科目由 TaxService.calculateVat 基于 reverse_reason 计算（RETURN → 10702进项转出）

**P57 进项认证/申报拆分新增字段 (V139):**
- t_input_invoice: declared_status(UNDECLARED/DECLARED/EXPIRED), declared_period(YYYYMM)
- calculateVat 抵扣口径: CERTIFIED + DECLARED 的 deduction_amount 合计

**P61 附表/税负:**
- 附表一: output_summary(period, customer_id, rate)
- 附表二: input_summary(period, vendor_id, rate, declared_status)
- 税负率: payable_tax / revenue(含税)

## 4. 核心流程（以票定账，人工审核驱动）

### 4.1 采购（进项）

```
① 发票导入/手动创建 → PENDING_CONFIRM
② 人工提交审核 → PENDING_REVIEW
③ 人工审核通过 → CONFIRMED → 自动创建 INVOICE_IN 业务单 + 凭证(DRAFT)
④ 人工审核凭证 → 过账 → POSTED
⑤ 税务认证 → CERTIFIED（独立于审核状态机，可跨期）
⑥ 申报抵扣 → declared_status=DECLARED（计入当期 VAT 抵扣）
⑦ 核销 → FULLY/PARTIALLY_RECONCILED
```

### 4.2 销售（销项）

```
① 发票导入/手动创建 → PENDING_CONFIRM
② 人工提交审核 → PENDING_REVIEW
③ 人工审核通过 → CONFIRMED → 自动创建 INVOICE_OUT 业务单 + 凭证(DRAFT)
④ 人工审核业务单 → APPROVED
⑤ 人工审核凭证 → 过账 → POSTED
⑥ 核销 → FULLY/PARTIALLY_RECONCILED
```

### 4.3 红冲（P36/P36.1）

```
① 蓝字发票 reverse() → 创建红字发票(RED, status=PENDING_CONFIRM)
② 红字发票审核通过后 generateRedFlushVoucher（三种情况）：
   a) 原蓝字已入账已抵扣：冲销原分录 + 进项转出（借成本/贷进项转出）
   b) 原蓝字已入账未抵扣：仅冲销原分录
   c) 原蓝字未入账：红字发票单独入账
```

### 4.4 月末税金（P13/P61）

```
① calculateVat(period) → 销项税合计 - 进项已抵扣税合计 = 应纳增值税
② 生附税申报表 → TaxDeclaration(DRAFT)
③ 人工提交 → SUBMITTED → 人工审批 → APPROVED
④ 附表一/二只读视图（P61）→ 对照税务局附表填报
⑤ 税负率分析 → 同比 + 预警（≥5%阈值）
```

### 4.5 发票-付款勾稽（P58）

```
GET /api/sme/tax/v1/invoice-reconcile/{input,output} → 只读视图
reconcile_status 算法（SQL CASE）：UNPAID / PARTIAL / PAID
不触发核销动作，核销仍走 ReconciliationService(P1统一路径)
```

## 5. 状态机

### 5.1 销项（INVOICE_OUT）

```
PENDING_CONFIRM ──submitReview──→ PENDING_REVIEW ──confirm──→ CONFIRMED ──markVouchered──→ VOUCHERED
      ↕                       ↕              ↕                    ↕
    void(→VOIDED)          reject(→回退)     revert(→回退)      reconcile(→FULLY/PARTIALLY_RECONCILED)
                                                                        ↕
                                                                     reverse(→REVERSED)
```

**凭证科目方向（销项）：** 借 1122(应收账款) / 贷 5001(主营收入) + 2221.01(销项税)

### 5.2 进项（INVOICE_IN）(P40)

```
PENDING_CONFIRM ──submitReview──→ PENDING_REVIEW ──confirm──→ CONFIRMED ──auto──→ VOUCHERED
      ↕                       ↕              ↕                    ↕
    void(→VOIDED)          reject(→回退)     revert(→回退)      reconcile(→FULLY/PARTIALLY_RECONCILED)
```

**凭证科目方向（进项）：** 借 1601(原材料) + 2221.01(进项税) / 贷 2202(应付账款)

### 5.3 进项认证/申报（P57 双态）

```
审核态（独立）:         税务认证态（独立）:
PENDING_CONFIRM                    UNCONFIRMED
  ↕                                 ↕
PENDING_REVIEW                  CERTIFIED
  ↕                                 ↕
CONFIRMED                     CERTIFIED_DECLARED
  ↕                                 ↕
VOUCHERED                     CERTIFIED_EXPIRED
```

- certify(id, deductionPeriod): 进项认证，仅 CONFIRMED/VOUCHERED 发票可认证
- declareDeduction(id, declaredPeriod): 申报抵扣，仅 CERTIFIED + UNDECLARED 可申报
- 抵扣口径：declared_status=DECLARED 的 deduction_amount 合计

## 6. API 端点

### 6.1 销项发票

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/sme/tax/v1/sales-invoices/preview | POST | Excel 导入预览 |
| /api/sme/tax/v1/sales-invoices/confirm-import | POST | 确认导入 |
| /api/sme/tax/v1/sales-invoices/import | POST | 后台导入 |
| /api/sme/tax/v1/sales-invoices/page | GET | 分页列表 |
| /api/sme/tax/v1/sales-invoices/batch-link-red-flush | POST | 批量关联红冲 |
| /api/sme/tax/v1/tax/output-invoices/** | CRUD+审核 | 全生命周期 |
| /api/sme/tax/v1/tax/output-invoices/summary | GET | 期间汇总 |
| /api/sme/tax/v1/tax/output-invoices/by-tax-rate | GET | 分税率汇总 |

### 6.2 进项发票

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/sme/tax/v1/input-invoices/preview | POST | Excel 导入预览 |
| /api/sme/tax/v1/input-invoices/confirm-import | POST | 确认导入 |
| /api/sme/tax/v1/input-invoices/import | POST | 后台导入 |
| /api/sme/tax/v1/tax/input-invoices/** | CRUD+审核 | 全生命周期 |
| /api/sme/tax/v1/tax/input-invoices/{id}/certify | POST | 税务认证 |
| /api/sme/tax/v1/tax/input-invoices/summary | GET | 期间汇总 |
| /api/sme/tax/v1/tax/input-invoices/by-tax-rate | GET | 分税率汇总 |

### 6.3 勾稽/申报/税金（P58/P13/P61）

| 端点 | 方法 | 说明 | 关联 |
|------|------|------|------|
| /api/sme/tax/v1/invoice-reconcile/input | GET | 进项发票-付款勾稽视图 | P58 |
| /api/sme/tax/v1/invoice-reconcile/output | GET | 销项发票-收款勾稽视图 | P58 |
| /api/sme/tax/v1/tax/vat/calculate | GET | 增值税计算 | P13 |
| /api/sme/tax/v1/tax/vat/appendix-i | GET | 附表一(销项) | P61 |
| /api/sme/tax/v1/tax/vat/appendix-ii | GET | 附表二(进项) | P61 |
| /api/sme/tax/v1/tax/vat/tax-burden | GET | 税负率分析 | P61 |
| /api/sme/tax/v1/tax/declarations/** | CRUD+审批 | 纳税申报 | P13 |
| /api/sme/tax/v1/tax/types/** | CRUD | 税种管理 | P13 |

## 7. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 科目映射 | 规则→pgvector→LLM 三阶段 | ✅ 已实现（P2-1） |
| 异常检测 | 品名背离/金额/时间异常 | ✅ 已实现（P2-2） |
| 审核建议 | 发票合规性 AI 初审 | 🟡 P2 |

## 8. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端(进销项) | ✅ 完整 | 进销项均含完整状态机+导入流程，进项 8 态 + 认证/申报双态 |
| 后端(核销联动) | ✅ P1 统一 | execute→SUBMITTED→approve→CONFIRMED，发票/来源单据/银行流水同步 |
| 后端(红冲) | ✅ P36/P36.1 | 三情况凭证生成 + 进项转出 + RedFlushReason 枚举 |
| 后端(勾稽/申报) | ✅ P58/P13 | 只读勾稽视图 + 申报表全生命周期 |
| 后端(附表/税负) | ✅ P61 完成 | 附表一/二/税负率三端点 + 4-8 行聚合 SQL + CURR/YOY 同比 + 前端三 Tab |
| 前端(进销项/勾稽/增值税) | ✅ 基本完整 | OutputInvoiceList/InputInvoiceList/InvoiceReconcileView/TaxVatView |
| 前端(附表/税负可视化) | ✅ 完成 | TaxVatView 加附表一/附表二/税负率三 Tab |
| 测试 | ✅ 良好 | 全量 1512 tests, 0 fail；P61 新增 8 测(TaxService 40) + P57 32 + P58 7 + P1 7 |
| 缺失能力 | 🟡 | 海关进口增值税专用缴款书/完税凭证/附表三（跨期进项转出明细） |
| DSN 与实现一致性 | ✅ 本次对齐 | 数据模型/API/组件与实现一致 |