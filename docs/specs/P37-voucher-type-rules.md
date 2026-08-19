# P37 SPEC — 自动制证凭证类型映射规则
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-037 | 优先级：中（P37）
> 依据：`docs/design.md` §6.4 凭证类型与模板解耦
> 目标：为所有自动生成凭证的路径定义明确的凭证类型映射规则，消除硬编码 `DEFAULT_VOUCHER_TYPE_ID = 1L`
> 工期：单批交付，1 个 commit

> **test_ref**：VoucherTypeServiceImplTest
---

> **关联需求**: REQ-2026-007

## 1. 输入契约
→ 见本文 [## 1. 凭证类型表 — VoucherType 常量类与数据库映射](#1-凭证类型表)

## 2. 输出契约
→ 见本文 [## 6. 验收标准 — AT-01 至 AT-08 验收场景表](#6-验收标准)

## 3. 状态流转
→ 见本文 [## 2. 映射规则总表 — 各制证路径的凭证类型映射规则](#2-映射规则总表)

## 4. 异常处理
→ 见本文 [## 4. 边界情况 — 分类为空/无 settlement_type 的降级与异常处理](#4-边界情况)

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `VoucherType` 常量类 | `backend/.../finance/constant/VoucherType.java` | ✅ 低 |
| 2 | `AutoGenerationService` 根据流水分类动态选凭证类型 | Service 文件 | ⚠️ 中 |
| 3 | `SalesInvoiceImportService` 销项发票 → SK | Service 文件 | ✅ 低 |
| 4 | `InputInvoiceImportService` 进项发票 → FK | Service 文件 | ✅ 低 |
| 5 | `ArapSettlementServiceImpl` 核销结算 → SK / FK | Service 文件 | ✅ 低 |
| 6 | `PrepaymentServiceImpl` 预收/预付 → SK / FK | Service 文件 | ✅ 低 |
| 7 | `ReconciliationServiceImpl` 对账制证 → SK / FK | Service 文件 | ✅ 低 |

---

## 1. 凭证类型表

数据库 `t_voucher_type` 定义了 4 种凭证类型：

| ID | Code | 名称 | 语义 |
|----|------|------|------|
| 1 | JZ | 记账凭证 | 通用/兜底，无特殊分类时使用 |
| 2 | SK | 收款凭证 | 收款类业务（客户回款、销售收款） |
| 3 | FK | 付款凭证 | 付款类业务（供应商付款、费用支出） |
| 4 | ZZ | 转账凭证 | 内部转账、资金调拨 |

```java
// com.huicai.module.finance.constant.VoucherType.java
public final class VoucherType {
    public static final long JZ = 1L;
    public static final long SK = 2L;
    public static final long FK = 3L;
    public static final long ZZ = 4L;
}
```

---

## 2. 映射规则总表

| 制证路径 | 业务分类 / 来源 | 凭证类型 |
|----------|----------------|----------|
| 银行流水自动制证 | `business_receipt` | SK |
| 银行流水自动制证 | `business_payment` | FK |
| 银行流水自动制证 | `internal_transfer` | ZZ |
| 银行流水自动制证 | `salary_social` / `bank_interest_fee` / `tax_withholding` / 其他 | JZ |
| 销项发票导入 | 销售发票（应收类） | SK |
| 进项发票导入 | 采购发票（应付类） | FK |
| 核销结算 | `settlement_receivable`（应收核销） | SK |
| 核销结算 | `settlement_payment`（应付核销） | FK |
| 预付冲应付 | 供应商预付款核销 | FK |
| 预收冲应收 | 客户预收款核销 | SK |
| 对账制证 | `reconciliation_receipt`（发票-流水应收方向） | SK |
| 对账制证 | `reconciliation_payment`（发票-流水应付方向） | FK |

### 2.1 映射原则

1. **收款类**（资金流入：客户汇款、销售回款、预收冲抵）→ **SK (收款凭证)**
   - 借方科目通常是银行存款/应收账款（科目 1002/1122）
2. **付款类**（资金流出：供应商付款、费用支出、预付冲抵）→ **FK (付款凭证)**
   - 贷方科目通常是银行存款/应付账款（科目 1002/2202）
3. **转账类**（资金在内部账户间划转）→ **ZZ (转账凭证)**
   - 不涉及外部收付方
4. **其他**（税费计提、工资发放、利息收入等无明确收付方向）→ **JZ (记账凭证)**
   - 兜底，保持原有行为

---

## 3. 各路径实现细节

### 3.1 银行流水（AutoGenerationService）

`resolveVoucherType()` 方法根据 `BankStatementEntity.classification` 选择凭证类型：

```java
private static long resolveVoucherType(String classification) {
    if (classification == null) return VoucherType.JZ;
    return switch (classification) {
        case BankClassification.BUSINESS_RECEIPT -> VoucherType.SK;
        case BankClassification.BUSINESS_PAYMENT -> VoucherType.FK;
        case BankClassification.INTERNAL_TRANSFER -> VoucherType.ZZ;
        default -> VoucherType.JZ;
    };
}
```

**匹配优先级**：模板制证路径优先（模板中有 classification 匹配），模板不存在时回退到硬编码路径 + 上述映射。

### 3.2 销项发票导入（SalesInvoiceImportService）

销项发票产生的是应收账款（客户欠款），属于收款类业务，使用 `VoucherType.SK`。

凭证分录：
```
借: 1122 应收账款
贷: 5001 主营业务收入
贷: 2221.01 应交税费—销项税
```

### 3.3 进项发票导入（InputInvoiceImportService）

进项发票产生的是应付账款（欠供应商款），属于付款类业务，使用 `VoucherType.FK`。

凭证分录：
```
借: 5001 采购成本
借: 2221.01 应交税费—进项税
贷: 2202 应付账款
```

### 3.4 核销结算（ArapSettlementServiceImpl）

核销方向由 `settlement_type` 字段决定：
- `receive` / `receivable` → 应收核销，对应 **SK**
- `pay` / `payment` → 应付核销，对应 **FK**

### 3.5 预收/预付（PrepaymentServiceImpl）

| 方法 | 业务场景 | 分录 | 凭证类型 |
|------|----------|------|----------|
| `applyToPayable()` | 预付冲应付 | 借: 2202 应付账款 / 贷: 1123 预付账款 | FK |
| `applyToReceivable()` | 预收冲应收 | 借: 2203 预收账款 / 贷: 1122 应收账款 | SK |

### 3.6 对账制证（ReconciliationServiceImpl）

对账方向由目标单据类型决定：
- `INVOICE_OUT` → `reconciliation_receipt` → **SK**
- `INVOICE_IN` → `reconciliation_payment` → **FK**

---

## 4. 边界情况

| 场景 | 处理方式 |
|------|----------|
| 银行流水分类为空 | 降级为 JZ（记账凭证） |
| 核销结算无 `settlement_type` | 默认为 receivable，使用 SK |
| 新增银行流水分类 | 未匹配到明确分类的均走 default → JZ |
| 手动创建凭证 | 用户在前端 `VoucherEdit.vue` 自行选择凭证类型 + 模板，不受此规则影响 |

---

## 5. YAML 契约

```yaml
# MACHINE-READABLE CONTRACT
contract_version: '1.0'
entity: Voucher
description: 自动制证凭证类型映射规则

rules:
  - id: R-BANK-STMT
    source: bank_statement
    description: 银行流水自动制证
    mapping:
      business_receipt: SK
      business_payment: FK
      internal_transfer: ZZ
      default: JZ
    implementation: AutoGenerationService.resolveVoucherType()

  - id: R-SALES-INVOICE
    source: sales_invoice_import
    description: 销项发票导入制证
    mapping:
      default: SK
    implementation: SalesInvoiceImportService

  - id: R-PURCHASE-INVOICE
    source: input_invoice_import
    description: 进项发票导入制证
    mapping:
      default: FK
    implementation: InputInvoiceImportService

  - id: R-SETTLEMENT
    source: arap_settlement
    description: 核销结算制证
    mapping:
      receivable: SK
      payment: FK
    implementation: ArapSettlementServiceImpl

  - id: R-PREPAYMENT
    source: prepayment
    description: 预收预付制证
    mapping:
      prepay_to_payable: FK
      prepaid_receipt_to_receivable: SK
    implementation: PrepaymentServiceImpl

  - id: R-RECONCILIATION
    source: reconciliation
    description: 对账制证
    mapping:
      reconciliation_receipt: SK
      reconciliation_payment: FK
    implementation: ReconciliationServiceImpl

acceptance_tests:
  - id: AT-1
    scenario: 银行流水 business_receipt 生成凭证为 SK
    status: missing
  - id: AT-2
    scenario: 银行流水 business_payment 生成凭证为 FK
    status: missing
  - id: AT-3
    scenario: 销项发票导入生成凭证为 SK
    status: missing
  - id: AT-4
    scenario: 进项发票导入生成凭证为 FK
    status: missing
```

---

## 6. 验收标准

| 编号 | 场景 | 前置条件 | 操作步骤 | 预期结果 |
|------|------|---------|---------|---------|
| AT-01 | 银行流水 business_receipt 自动制证 | 银行流水记录存在，classification='business_receipt'，未审核 | ① 选择该笔银行流水 ② 触发自动制证流程 | 生成凭证的 voucher_type_id = 2（SK 收款凭证） |
| AT-02 | 银行流水 business_payment 自动制证 | 银行流水记录存在，classification='business_payment'，未审核 | ① 选择该笔银行流水 ② 触发自动制证流程 | 生成凭证的 voucher_type_id = 3（FK 付款凭证） |
| AT-03 | 银行流水 internal_transfer 自动制证 | 银行流水记录存在，classification='internal_transfer'，未审核 | ① 选择该笔银行流水 ② 触发自动制证流程 | 生成凭证的 voucher_type_id = 4（ZZ 转账凭证） |
| AT-04 | 银行流水其他/空分类降级制证 | 银行流水记录存在，classification='salary_social' 或 null | ① 选择该笔银行流水 ② 触发自动制证流程 | 生成凭证的 voucher_type_id = 1（JZ 记账凭证，兜底） |
| AT-05 | 销项发票导入生成 SK 凭证 | 销项发票导入完成，处于待制证状态 | ① 执行销项发票导入制证 | 生成凭证的 voucher_type_id = 2（SK 收款凭证） |
| AT-06 | 进项发票导入生成 FK 凭证 | 进项发票导入完成，处于待制证状态 | ① 执行进项发票导入制证 | 生成凭证的 voucher_type_id = 3（FK 付款凭证） |
| AT-07 | 核销结算应收方向制证 SK | settlement_type='receivable' 的核销结算记录，BusinessDoc 已创建 | ① 执行核销结算制证 | 生成凭证的 voucher_type_id = 2（SK 收款凭证） |
| AT-08 | 对账制证应付方向生成 FK 凭证 | 对账方向为 INVOICE_IN（应付方向），BusinessDoc 已到位 | ① 执行对账制证 | 生成凭证的 voucher_type_id = 3（FK 付款凭证） |

---

## 7. BDD 验收标准

### 场景 1：银行流水 business_receipt 自动制证为收款凭证
**Given** 银行流水记录存在，classification='business_receipt'，未审核
**When** 用户触发自动制证流程
**Then** 生成凭证的 voucher_type_id = 2（SK 收款凭证），且 resolveVoucherType() 方法返回 VoucherType.SK

### 场景 2：销项发票导入自动制证为收款凭证
**Given** 销项发票导入完成，处于待制证状态
**When** 执行销项发票导入制证
**Then** 生成凭证的 voucher_type_id = 2（SK 收款凭证），借方科目为 1122 应收账款

### 场景 3：银行流水分类为空降级为 JZ 记账凭证
**Given** 银行流水记录存在，classification 为 null 或未识别分类（如 salary_social）
**When** 用户触发自动制证流程
**Then** resolveVoucherType() 走 default 分支，生成凭证的 voucher_type_id = 1（JZ 记账凭证，兜底）
