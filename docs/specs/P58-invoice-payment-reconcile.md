# P58 SPEC — 发票-收付款勾稽 Tab（三流合一视图）

> **版本**：V1.0 | **日期**：2026-08-27 | **作者**：Hermes
> **状态**：📝 待审核
> **编号**：HUICAI-SPC-058
> **关联PRD**：../prd/发票税务管理-PRD-V1.0.md、../prd/应收应付核销-PRD-V1.0.md
> **关联SPEC**：P30-reconciliation-workbench-enhance.md、P57-declare-status-split.md
> **test_ref**：InvoicePaymentReconcileServiceTest（新建）

---

## 业务背景

进项发票通过"以票定账"生成业务单（docType=INVOICE_IN），业务单经核销工作台
与付款单（docType=PAYMENT）核销后更新 settledAmount/unsettledAmount。
但当前**发票本身没有一个"已付款"维度的标记**，无法快速回答：
"这张进项发票认证了没？申报了没？钱付了没？"

本 SPEC 在核销工作台新增"发票勾稽"Tab，提供**只读聚合视图**
（票流=认证/申报、资金流=付款核销），不新增核销动作（复用 P30 现有核销）。

---

## 1. 输入契约

### 1.1 查询接口

```java
// InvoicePaymentReconcileService
/** 按供应商聚合发票勾稽状态（进项） */
List<InvoiceReconcileVO> queryInputReconcile(String period, Long vendorId);
/** 按客户聚合发票勾稽状态（销项） */
List<InvoiceReconcileVO> queryOutputReconcile(String period, Long customerId);
```

### 1.2 InvoiceReconcileVO

| 字段 | 类型 | 说明 |
|------|------|------|
| invoiceId | Long | 发票 ID |
| invoiceNo | String | 发票号 |
| invoiceDate | LocalDate | 开票日 |
| vendorName/customerName | String | 往来单位 |
| amount | BigDecimal | 金额（含税） |
| taxAmount | BigDecimal | 税额 |
| certificationStatus | String | UNCERTIFIED/CERTIFIED |
| declaredStatus | String | UNDECLARED/DECLARED（P57） |
| paidAmount | BigDecimal | 已付款金额（来自关联业务单 settledAmount） |
| unpaidAmount | BigDecimal | 未付款金额 = amount - paidAmount |
| reconcileStatus | String | UNPAID(未付)/PARTIAL(部分)/PAID(已付) |
| hasRedFlushed | Boolean | 是否已红冲 |

---

## 2. 输出契约

### 2.1 聚合逻辑

```
发票.金额 = totalAmount
已付款 = 关联业务单(BusinessDoc.docId = 发票.id AND docType=INVOICE_IN).settledAmount
未付款 = totalAmount - 已付款
勾稽状态:
  paidAmount == 0        → UNPAID
  0 < paidAmount < total → PARTIAL
  paidAmount >= total    → PAID
```

### 2.2 reconcileStatus 用途

前端标记：
- 已认证 + 已申报 + 已付款（三流合一）→ 绿色"可抵扣/已闭环"
- 已认证 + 未付款 → 黄色"票到款未到"（虚开风险预警）

---

## 3. 状态流转

无状态变更（只读视图）。数据来源：
```
InputInvoiceEntity → docId → BusinessDoc(settledAmount)
                      ↑ certificationStatus / declaredStatus (P57)
```

---

## 4. 异常处理

| 场景 | 处理 |
|------|------|
| 发票无关联业务单 | paidAmount=0, reconcileStatus=UNPAID（正常） |
| 业务单 settledAmount 为 null | 按 0 处理 |

---

## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| REC-01 | Given 进项发票(1000)关联业务单settled=600 When queryInputReconcile Then reconcileStatus=PARTIAL, unpaid=400 |
| REC-02 | Given 进项发票(1000)关联业务单settled=1000 When 查询 Then reconcileStatus=PAID |
| REC-03 | Given 进项发票无业务单 When 查询 Then paidAmount=0, reconcileStatus=UNPAID |
| REC-04 | Given 进项发票certified+declared+paid When 查询 Then 三流合一标记完整 |

---

## 影响文件

| 文件 | 变更 |
|------|------|
| InvoicePaymentReconcileService.java（新建） | queryInputReconcile / queryOutputReconcile |
| InvoiceReconcileVO.java（新建 DTO） | 聚合视图 |
| InvoicePaymentReconcileController.java（新建） | REST 端点 |
| InvoicePaymentReconcileMapper.java（新建） | 聚合 SQL |

## 不做

| 不做 | 理由 |
|------|------|
| 自动核销 | 复用 P30 工作台，本 SPEC 仅视图 |
| 销项回款自动标记 | 销项已关联 receivableId，同样只读聚合 |
| 预警推送 | 前端标记即可，不做消息 |
