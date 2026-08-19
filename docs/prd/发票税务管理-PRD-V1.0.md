# 发票税务管理 PRD

> **编号**：HUICAI-PRD-005
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD：`(../CORE-需求分析.md)` 
> **关联设计**：DSN-发票税务管理.md
> **关联SPEC**：P40-input-invoice-state-machine.md、P41-invoice-driven-finance.md
> **对应包**：com.huicai.sme.tax

---

## 1. 模块定位

管理进销项发票的导入、状态流转、以票定账（审核通过自动生成业务单+凭证）、AI 辅助映射、增值税计算。

**做什么**：进销项发票导入+状态机、AI 科目映射、发票异常检测、增值税计算。

**不做什么**：
- 不做电子发票查验接口对接（通过 Excel 导入）
- 不做税务申报（V2.0 批量报税）
- 不做发票 OCR（通过 Excel 录入）

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| TAX-01 | 销项发票导入（Excel 批量） | P0 | ✅ 已完成 | 含预览+确认；单张/多行独立导入 |
| TAX-02 | 销项发票状态机 | P0 | ✅ 已完成 | PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED→VOUCHERED |
| TAX-03 | 进项发票导入（Excel 批量） | P0 | ✅ 已完成 | 同上 |
| TAX-04 | 进项发票状态机 | P0 | ✅ 已完成 | 8态：PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED→PAID→VOUCHERED+红冲/作废 |
| TAX-05 | 以票定账 | P0 | ✅ 已完成 | 审核通过自动：业务单(DRAFT)+应收/应付单+凭证(DRAFT) |
| TAX-06 | AI 科目映射 | P1 | ✅ 已完成 | 规则→向量→LLM 三阶段；置信度<0.6 转人工 |
| TAX-07 | 发票异常检测 | P1 | ✅ 已完成 | 品名背离/时间异常/金额异常；输出风险标签 |
| TAX-08 | 增值税计算 | P0 | ✅ 已完成 | 销项-进项=应纳税额；按税率分组计算 |

---

## 3. 状态流转

### 3.1 销项发票

```
PENDING_CONFIRM → PENDING_REVIEW → CONFIRMED → VOUCHERED
         ↑                                ↕
         └── reject(→PENDING_CONFIRM)     reverse(→REVERSED)
```

### 3.2 进项发票

```
PENDING_CONFIRM → PENDING_REVIEW → CONFIRMED → PAID → VOUCHERED
         ↑                                ↕              ↕
         └── reject                     cancel           REVERSED
```

**注意**：进项发票 `confirm()` 跳两跳：`CONFIRMED` 立即调用 `generateVoucherFromInvoiceDirect()` 推进到 `VOUCHERED`。

### 3.3 以票定账流程

```
发票审核通过(CONFIRMED)
  → 生成业务单(DRAFT) — 人工确认后提交
    → 生成应收/应付单(DRAFT)
      → 自动生成凭证(DRAFT) — 人工审核过账
```

---

## 4. 核心业务规则

### 4.1 AI 科目映射

| 阶段 | 说明 | 优先级 |
|------|------|--------|
| 规则匹配 | 精确匹配发票品名→科目 | 第一优先 |
| 向量检索 | pgvector 近邻检索相似科目 | 第二优先 |
| LLM 推理 | 规则+向量都不匹配时 LLM 推断 | 第三优先 |

置信度 < 0.6 时标记为"需人工确认"。

### 4.2 发票异常检测

| 类型 | 规则 |
|------|------|
| 品名背离 | 发票品名与历史同类业务品名不一致 |
| 时间异常 | 开票日期与录入日期差>30天 |
| 金额异常 | 单张发票金额 > 同品类历史均值×3 |

### 4.3 增值税计算

| 项目 | 公式 |
|------|------|
| 销项税额 | 销项发票金额 × 税率 |
| 进项税额 | 进项发票金额 × 税率（按类别分组） |
| 应纳税额 | 销项税额 - 进项税额 |

---

## 5. 验收标准

| ID | BDD 场景 |
|----|---------|
| AT-01 | Given 销项发票 When 导入(单行) Then 状态=PENDING_CONFIRM |
| AT-02 | Given 销项发票 When 导入(多行,同 invoiceNo) Then 每行独立插入 |
| AT-03 | Given PENDING_REVIEW 发票 When confirm Then 状态=CONFIRMED + 自动创建业务单 |
| AT-04 | Given CONFIRMED 发票 When generateVoucher Then 状态=VOUCHERED + 创建凭证 |
| AT-05 | Given AI映射 When 置信度<0.6 Then 标记需人工确认 |
| AT-06 | Given 异常发票(品名背离) When 导入 Then 风险标签=异常 |
| AT-07 | Given 增值税计算 When 查询(当期) Then 返回销项/进项/应纳税额 |

---

## 6. 不做的事

| 不做 | 理由 |
|------|------|
| 税务申报 | 非当前版本范围 |
| 发票 OCR | Excel 录入已满足需求 |
| 发票查验接口 | 外部接口不稳定，暂不做 |
| 电子发票接收 | 通过 Excel 导入 |

---

## 7. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sme/tax/invoices/output/page` | 销项发票分页 |
| POST | `/api/sme/tax/invoices/output/import` | 销项发票导入 |
| POST | `/api/sme/tax/invoices/output/{id}/confirm` | 销项审核 |
| GET | `/api/sme/tax/invoices/input/page` | 进项发票分页 |
| POST | `/api/sme/tax/invoices/input/import` | 进项发票导入 |
| POST | `/api/sme/tax/invoices/input/{id}/confirm` | 进项确认（跳两跳到 VOUCHERED） |
| GET | `/api/sme/tax/vat` | 增值税计算 |
| POST | `/api/sme/tax/ai/mapping` | AI 科目映射 |

---

> **文档结束。**