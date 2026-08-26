# P57 SPEC — 进项发票"认证/申报"状态拆分（增值税计算口径修正）

> **版本**：V1.0 | **日期**：2026-08-27 | **作者**：Hermes
> **状态**：📝 待审核
> **编号**：HUICAI-SPC-057
> **关联PRD**：../prd/发票税务管理-PRD-V1.0.md
> **关联SPEC**：P40-input-invoice-state-machine.md
> **test_ref**：InputInvoiceDeclareTest（新建）、TaxServiceImplVatDeclareTest（新建）

---

## 业务背景

当前 `calculateVat()` 的进项抵扣额取 `certification_status='CERTIFIED'` 的
`deduction_amount`——**认证即抵扣**。但税务规则是"认证≠抵扣"：
进项发票需勾选并**申报**后才允许抵扣。当前口径会在"已认证但跨期申报/未申报"时
虚增进项抵扣，导致增值税应纳税额计算不准（这是真实风险，非理论）。

本 SPEC 将"认证"与"申报抵扣"拆成两个独立状态，增值税计算以"已申报抵扣"为准。

---

## 1. 输入契约

### 1.1 进项发票新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| declaredStatus | String | 申报状态：UNDECLARED(已认证未申报) / DECLARED(已申报抵扣)，默认 UNDECLARED |
| declaredPeriod | String | 申报所属期(yyyyMM)，勾选申报时写入 |
| declaredDate | LocalDate | 申报日期 |

### 1.2 新增接口

```java
// TaxService
/** 进项发票勾选申报抵扣（认证后→已申报） */
InputInvoiceEntity declareDeduction(Long id, String declaredPeriod, Long userId);
```

| 参数 | 必填 | 说明 |
|------|------|------|
| id | 是 | 进项发票 ID |
| declaredPeriod | 是 | 申报所属期 yyyyMM |
| userId | 是 | 操作人 |

约束：仅 `certificationStatus='CERTIFIED'` 且 `declaredStatus='UNDECLARED'` 可申报。

---

## 2. 输出契约

### 2.1 declareDeduction 输出

| 对象 | 字段 | 值 |
|------|------|----|
| InputInvoiceEntity | declaredStatus | DECLARED |
| | declaredPeriod | 传入的 declaredPeriod |
| | declaredDate | 当天 |

### 2.2 calculateVat 进项口径变更

| 维度 | 变更前 | 变更后 |
|------|--------|--------|
| 进项抵扣来源 | `certification_status='CERTIFIED'` | `declared_status='DECLARED'` |
| 取数列 | `deduction_amount` | `deduction_amount` |

> 销项税额口径不变（销项开票即产生纳税义务）。

---

## 3. 状态流转

```
进项发票:
 UNCERTIFIED ──certify()──▶ CERTIFIED (declaredStatus=UNDECLARED)
                                   │
                          declareDeduction()
                                   ▼
                            CERTIFIED (declaredStatus=DECLARED, declaredPeriod=xxx)
```

**非法转换（阻断）：**
| 场景 | 阻断 |
|------|------|
| 未认证即申报 | BusinessException: 仅已认证发票可申报抵扣 |
| 已申报重复申报 | BusinessException: 该发票已申报抵扣 |
| 红冲依赖 | 红冲时快照 declaredStatus（已在 P36.1 实现 originalCertificationStatus） |

---

## 4. 异常处理

| 错误码 | 场景 | 消息 |
|:------:|------|------|
| 400 | 未认证申报 | 仅已认证(CERTIFIED)发票可申报抵扣 |
| 400 | 重复申报 | 该发票已申报抵扣(DECLARED) |

---

## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| DEC-01 | Given 已认证发票(UNDECLARED) When declareDeduction(202608) Then declaredStatus=DECLARED + declaredPeriod=202608 |
| DEC-02 | Given 未认证发票(UNCERTIFIED) When declareDeduction Then 抛异常 |
| DEC-03 | Given 已申报发票(DECLARED) When declareDeduction Then 抛异常(重复) |
| VAT-01 | Given 认证未申报1000 + 已申报500 + 销项2000 When calculateVat Then 进项抵扣=500(已申报)，应纳税额=1500 |
| VAT-02 | Given 全部认证未申报 When calculateVat Then 进项抵扣=0 |

---

## 影响文件

| 文件 | 变更 |
|------|------|
| InputInvoiceEntity.java | +declaredStatus/declaredPeriod/declaredDate |
| V139 migration | +三列 |
| InputInvoiceMapper.summaryByPeriod | deductible 改为按 declared_status='DECLARED' 汇总 |
| TaxServiceImpl.java | +declareDeduction()；calculateVat 取数列条件变更 |
| TaxService.java | +declareDeduction 接口 |

## 不做

| 不做 | 理由 |
|------|------|
| 自动申报 | 铁律：申报需人审 |
| 申报表导入 | 外部数据，先手工勾选 |
| 跨期自动结转 | 留抵逻辑已在 calculateVat 体现 |
