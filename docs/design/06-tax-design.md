# 06-发票与税务管理设计

> **编号**：HUICAI-DES-007
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.tax`
> 设计文档：[主文档](../DESIGN.md)

---

## 1. 模块定位

传统定位：税务合规与发票管理。进销项发票手工录入/税控盘导入，增值税计算，申报表生成。

**超越传统之处：**
- 传统：手工录入或税控盘导入 → 当前：**Excel自动化导入 + 价税分离 + 容错（匿名客户回退）**
- 传统：简单状态 → 当前：**6态状态机**（PENDING_CONFIRM→CONFIRMED→VOUCHERED→核销→REVERSED）
- 当前新增：**AI 科目映射**（规则→pgvector→LLM三阶段）+ **异常检测**（品名背离/时间异常）

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| OutputInvoiceService | 销项发票管理 |
| InputInvoiceService | 进项发票管理 |
| OutputInvoiceStateMachineService | 销项发票6态状态机 |
| TaxService | 税金计算、凭证生成 |
| SalesInvoiceImportService | 销项发票Excel导入（列名映射+价税分离+防重） |
| InputInvoiceImportService | 进项发票导入（对称逻辑） |
| InvoiceDedupUtil | 跨表发票号查重 |
| ColumnMappingResolver | Excel 列名别名映射 |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_output_invoice | 销项发票 | invoice_no, customer_id, amount, tax_rate, tax_amount, total_amount, amount_ex_tax, status, ai_risk_tag, doc_no, voucher_no |
| t_input_invoice | 进项发票 | invoice_no, vendor_id, amount, tax_rate, tax_amount, total_amount, amount_ex_tax, status, doc_no, voucher_no |
| t_tax_type | 税种 | code, name, rate |
| t_tax_declaration | 纳税申报 | period, declaration_no, status |

## 4. 核心流程（以票定账，人工审核驱动）

```
① 发票导入 → PENDING_CONFIRM（仅创建发票，不自动生单）
② 人工提交审核 → 人工审核通过 → CONFIRMED
③ 人工点击"生成业务单据" → BusinessDoc DRAFT
④ 人工审核业务单据 → APPROVED
⑤ 人工点击"生成凭证" → Voucher DRAFT
⑥ 人工审核凭证 → 过账 → POSTED
```

## 5. 状态机（销项）

```
PENDING_CONFIRM ──submitReview──→ PENDING_REVIEW ──confirm──→ CONFIRMED ──markVouchered──→ VOUCHERED
      ↕                      ↕              ↕                    ↕
    void(→VOIDED)       reject(→reject)   revert(→回退)     reconcile(→FULLY/PARTIALLY_RECONCILED)
                                                              ↕
                                                           reverse(→REVERSED)
```

## 6. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/tax/output-invoices/** | CRUD | 销项发票 |
| /api/v1/tax/input-invoices/** | CRUD | 进项发票 |
| /api/v1/tax/output-invoices/{id}/confirm | POST | 审核通过 |
| /api/v1/tax/output-invoices/{id}/mark-vouchered | POST | 生成凭证 |
| /api/v1/tax/output-invoices/{id}/reverse | POST | 红冲 |
| /api/v1/tax/vat/calculate | GET | 增值税计算 |
| /api/v1/tax/declarations/** | CRUD | 纳税申报 |

## 7. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 科目映射 | 规则→pgvector→LLM 三阶段 | ✅ 已实现（P2-1） |
| 异常检测 | 品名背离/金额/时间异常 | ✅ 已实现（P2-2） |
| 审核建议 | 发票合规性 AI 初审 | 🟡 P2 |

## 8. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 含完整状态机+导入流程 |
| 前端 | ✅ 完整 | OutputInvoiceList/InputInvoiceList |
| 测试 | ✅ 良好 | OutputInvoiceMapperTest + 42个状态机测试 |
| 对传统超越 | ✅ AI科目映射+异常检测+自动导入+价税分离 | 远超传统 |

> **文档结束**