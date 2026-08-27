---
标题: P61 增值税进销项附表 + 税负分析
编号: P61
版本: v1.0 (2026-08-27)
关联PRD: 发票税务管理-PRD-V1.0.md
关联SPEC: P57-declare-status-split.md, P13-tax-declaration.md
状态: 设计中
前置条件: P57(进项认证/申报拆分已落地)

## 背景

现状：TaxVatView 仅有 calculateVat(period) 返回单期 5 个数字（销项税/进项税/应交/附加/合计），**无附表一/附表二，无税负率**，
纳税人无法直接对照税务局的申报表填列，也看不到税负异常信号。
本 SPEC 基于现有 TaxController 的 6 个明细端点（outputSummary/outputByTaxRate/inputSummary/inputByTaxRate）
组装为申报附表视图，并叠加税负率分析。

## 1. 输入契约

### 1.1 API

| 端点 | 方法 | 参数 | 返回 |
|------|------|------|------|
| /api/sme/tax/v1/tax/vat/appendix-i | GET | period, customerId?(附表一:销项分客户/分税率) | AppendixIOpenAPIResponse |
| /api/sme/tax/v1/tax/vat/appendix-ii | GET | period, vendorId?(附表二:进项分供应商/分税率) | AppendixIIOpenAPIResponse |
| /api/sme/tax/v1/tax/vat/tax-burden | GET | period, type=(CURRENT/YOY) | TaxBurdenResponse |

### 1.2 DTO 定义

```java
// 附表一(销项) 一行
record AppendixIRow(Long period, Long customerId, String customerName,
                    BigDecimal salesAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                    BigDecimal rate) {}

// 附表二(进项) 一行 — 分认证态
record AppendixIIRow(Long period, Long vendorId, String vendorName,
                     BigDecimal amountExTax, BigDecimal taxAmount, BigDecimal totalAmount,
                     BigDecimal rate, String declareStatus) {
    // declareStatus: CERTIFIED_DECLARED / CERTIFIED_UNDECLARED / NOT_CERTIFIED
    boolean isDeductible() { return "CERTIFIED_DECLARED".equals(declareStatus); }
}

// 税负分析
record TaxBurdenResponse(BigDecimal revenue,         // 含税销售收入（附表一合计）
                         BigDecimal outputTax,       // 销项税额
                         BigDecimal inputDeduction,  // 进项可抵扣税额（附表二 declareStatus=CERTIFIED_DECLARED 的税合计）
                         BigDecimal payableTax,      // 应纳增值税 = outputTax - inputDeduction（负数为留抵）
                         BigDecimal taxBurdenRate,   // 税负率 = payableTax / revenue（收入为0时返回 null）
                         BigDecimal yoyRate,         // 去年同期税负率（type=YOY 时返回）
                         BigDecimal yoyChange) {}    // 同比变动 = taxBurdenRate - yoyRate

// 附表响应
record AppendixIResponse(BigDecimal totalSalesAmount, BigDecimal totalTaxAmount,
                         BigDecimal totalAmount, List<AppendixIRow> rows) {}
record AppendixIIResponse(BigDecimal totalAmountExTax, BigDecimal totalTaxAmount,
                          BigDecimal deductibleTax, List<AppendixIIRow> rows) {}
```

## 2. 输出契约

- 附表一 rows 按 totalAmount 降序，同一 period 下聚合 customerId + rate
- 附表二 rows 按 declareStatus + vendorId + rate 分组，deductibleTax 仅统计 CERTIFIED_DECLARED
- 应交增值税 = outputTax − inputDeduction（与 P57 declareDeduction 口径一致）
- 税负率 = payableTax / revenue（含税收入，revenue = 附表一 totalAmount）；revenue = 0 返回 null
- 附表响应 total* 与 rows 汇总一致（前后端对账依据）

## 3. 状态流转

- 附表一/二为只读视图，无写操作，不改变任何实体状态
- 税负分析基于附表汇总计算，无副作用

## 4. 异常处理

| 场景 | 异常 | 消息 |
|------|------|------|
| period 格式非法（非 YYYYMM） | BusinessException | 期间格式非法，应为 YYYYMM |
| type 非法（非 CURRENT/YOY） | BusinessException | 分析类型非法 |
| 同 period 已有过账凭证未结账，仍正常返回（不阻塞） | — | — |

## 5. BDD

### 场景 A: 附表一按客户+税率聚合
- Given 202607 有 3 张销项发票(客户A: 2张 rate=13%, 客户B: 1张 rate=6%)
- When GET /vat/appendix-i?period=202607
- Then rows 长度 = 3（客户A/客户B 分别按税率拆行）
- And totalTaxAmount = 3 张发票 taxAmount 之和

### 场景 B: 附表二仅统计 CERTIFIED_DECLARED 为可抵扣
- Given 202607 有进项发票 A(CERTIFIED_DECLARED, 税额100) + B(CERTIFIED_UNDECLARED, 税额50) + C(NOT_CERTIFIED, 税额20)
- When GET /vat/appendix-ii?period=202607
- Then totalTaxAmount = 170
- And deductibleTax = 100

### 场景 C: 税负率计算
- Given 附表一 totalAmount=1130, 附表一 totalTax=130, 附表二 deductibleTax=50
- When GET /vat/tax-burden?period=202607
- Then payableTax = 80
- And taxBurdenRate = 80/1130 ≈ 0.0708

### 场景 D: 零收入防除零
- Given 附表一 totalAmount=0
- When GET /vat/tax-burden?period=202607
- Then taxBurdenRate = null

### 场景 E: 同比
- Given 202607 税负率 0.07, 202507 税负率 0.05
- When GET /vat/tax-burden?period=202607&type=YOY
- Then yoyRate = 0.05, yoyChange = 0.02

### 场景 F: 附表二未认证发票不进入可抵扣
- Given 进项发票 certificationStatus=UNCONFIRMED, declaredStatus=UNDECLARED
- When GET /vat/appendix-ii?period=202607
- Then deductibleTax 不含该发票税额

## 6. test_ref

| 层级 | 测试类 | 数量 |
|------|--------|:----:|
| Service | TaxServiceImpl.appendixI/appendixII/taxBurden 单元测试 | 待补 |
| Controller | TaxController.vat.* 端点 4 个 | 待补 |
| 数据对齐 | TaxMapper 附表 SQL 与 P57 declaredStatus 口径一致 | 待补 |

## 7. 实施顺序

1. TaxServiceImpl.appendixI(period, customerId) — 复用 outputMapper.summaryByPeriod + 新增 byCustomerAndRate
2. TaxServiceImpl.appendixII(period, vendorId) — 复用 inputMapper.summaryByPeriod + 新增 byVendorAndRate 带 declaredStatus
3. TaxServiceImpl.taxBurden(period, type) — 组合 appendixI + appendixII 计算
4. TaxController 3 个端点
5. 前端 TaxVatView 追加 2 个 Tab（附表一/附表二）+ 税负率卡片