# P13 SPEC — 税务申报 (进销项归集 + 申报表自动生成)

> **编号**：HUICAI-SPC-013（骨架完整, 6 归集方法 + calculateVat 已在 main）
> 目标：进项/销项按 period 自动归集 → 增值税应纳税额计算 → 申报表生成 → 申报状态机
> 工期：1 批（骨架已有, 主要补单测 + 申报表详情表）

---

> **关联需求**: REQ-2026-030
## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_input_invoice` (进项发票) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `t_output_invoice` (销项发票) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `t_tax_declaration` (申报主表) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `TaxServiceImpl` | ✅ 18 个接口方法全部实现 (certify + 5 个归集 + calculateVat + 申报 CRUD) |
| `TaxServiceImplTest` | ❌ **零测试覆盖** — 本批补 |

**已实现的核心方法** (main 分支 commit `fa35a0e` 之前已落):
- `certify(Long id, String deductionPeriod)` — 进项发票勾选认证
- `inputSummary(String period)` / `inputByTaxRate(String period)` — 进项按 period 汇总
- `outputSummary(String period)` / `outputByTaxRate(String period)` — 销项按 period 汇总
- `calculateVat(String period)` — 应纳税额 = 销项 - 进项, 含附加税 12%

---

## 2. P13-1 任务 (本批)

### 2.1 补单测 (8 个)

| # | 测试 | 覆盖方法 |
|---|---|---|
| 1 | `certify_未认证_状态变CERTIFIED_扣除金额=税额` | `certify` |
| 2 | `certify_已认证_throw` | `certify` |
| 3 | `certify_发票不存在_throw` | `certify` |
| 4 | `calculateVat_正数_应纳税额=销项-进项+附加税12%` | `calculateVat` |
| 5 | `calculateVat_留抵_应纳税额=0+note提示` | `calculateVat` |
| 6 | `inputSummary_有数据_返回map` | `inputSummary` |
| 7 | `outputByTaxRate_有数据_返回列表` | `outputByTaxRate` |
| 8 | `submitDeclaration_DRAFT_变SUBMITTED` | `submitDeclaration` |

### 2.2 SPEC 文档

本文件就是。

### 2.3 不在 P13 范围

- 电子税务局对接 (需要外部 API, P14 候选)
- OCR 发票识别 (P15 票据管理)
- 多税种支持 (城建税/教育费附加已硬编码 12%, 后续可配置化)
- 申报撤销/作废流程 (P16 候选)

---

## 3. 关键设计

### 3.1 进项发票认证 (certify)

```
UNCERTIFIED ──→ certify(period) ──→ CERTIFIED
                                    deductionAmount = taxAmount
                                    certifiedDate = today
                                    deductionPeriod = period(默认当月)
```

### 3.2 增值税计算 (calculateVat)

```java
outputTax = SUM(t_output_invoice.taxAmount WHERE period=?)
inputTax  = SUM(t_input_invoice.deductionAmount WHERE period=? AND certification_status=CERTIFIED)
payable   = outputTax - inputTax
surcharge = payable * 0.12 (12% = 城建7% + 教育3% + 地方教育2%)
totalPayable = payable + surcharge
```

**留抵场景**: payable < 0 → surcharge=0, totalPayable=0, note="留抵税额(下期继续抵扣)"

### 3.3 申报状态机 (P13 后续)

```
DRAFT ──→ submitDeclaration ──→ SUBMITTED ──→ (人工) ──→ APPROVED
                                            └─→ (人工) ──→ REJECTED → DRAFT
```

当前 `submitDeclaration` 只到 SUBMITTED, 后续 P13-2 补 approve/reject。

---

## 4. 测试验收

**目标**: 281 → 289 (+8 测试, 0 fail, 0 error)
**commit**: `TBD`

---

## 5. P14+ 路线图

| 批 | 内容 | 优先级 |
|---|---|---|
| P14 | 银企对账完善 (BankReconciliation E2E) | 高 |
| P15 | 票据管理 + 附件上传 + OCR | 中 |
| P16 | 预算编制 + 控制 + 分析 | 中 |
| P17 | 报表中心 (三大报表) | 中 |
| P18 | 申报状态机扩展 (approve/reject) | 低 |

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P13-1 | 进项归集按period汇总 | `collectInput(period) → sum(amount) matches DB` |
| AT-P13-2 | 销项归集按period汇总 | `collectOutput(period) → sum(amount) matches DB` |
| AT-P13-3 | 增值税计算正确 | `calculateVat(period) → output_tax - input_tax == payable` |
| AT-P13-4 | 申报表生成 | `generateDeclaration(period) → declaration exists` |
