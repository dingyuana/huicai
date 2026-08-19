# 财务软件-业务单据通用引擎产品需求文档（PRD）

> **编号**：HUICAI-PRD-000b
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD**：`../CORE-需求分析.md`
> **关联设计**：DSN-应收应付状态机设计.md / DSN-应收应付管理.md
> **关联SPEC**：P-BUSINESSDOC-LIST.md、P-SALARY.md、P-TRANSFER.md
> **对应包**：com.huicai.sme.arap / com.huicai.base.business

---

## 1. 模块定位

业务单据是凭证生成的中间层，统一 11 种业务场景的单据录入和凭证生成规则，是"以票定账"和"以单定账"的核心枢纽。

**做什么**：通过 docType 抽象不同类型单据的凭证生成规则，实现统一引擎。

**不做什么**：
- 不做独立销货单/进货单/退货单类型（发票即业务单据）
- 不做元数据驱动的动态表单
- 不做多级审批流
- 不做可视化凭证打印设计器

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| DOC-G-01 | 11 种 docType 统一引擎 | P0 | ✅ | 11 种 docType 共享同一 Service |
| DOC-G-02 | DOC_TYPE_CODE → 凭证科目映射 | P0 | ✅ | 每种 docType 映射固定借贷科目 |
| DOC-G-03 | DOC_VOUCHER_SUBJECTS 常量管理 | P0 | ✅ | 集中管理所有 docType 的科目映射 |
| DOC-G-04 | AutoGenerationService 凭证自动生成 | P0 | ✅ | 审核后自动调用，凭证=DRAFT |
| DOC-G-05 | SUPPLIER_DOC_TYPES / NO_COUNTERPARTY_DOC_TYPES | P1 | ✅ | 区分有/无对手方单据类型 |
| DOC-G-06 | 结算账户互斥（客户/供应商 vs 结算账户） | P1 | ✅ | 前端 v-if 控制字段显隐 |
| DOC-G-07 | 转账单 TRANSFER 借/贷方向选择器 | P1 | ✅ | 费用类别列改为方向选择器 |
| DOC-G-08 | 工资单 SALARY 独立类型 | P1 | ✅ | SALARY docType + GR 编号前缀 |

---

## 3. 状态流转

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED
     ↕            ↕
   (编辑)      REJECTED(→DRAFT)

VOUCHERED → REVERSED（红冲，不可恢复）
```

（同业务单据管理 PRD，统一状态机）

---

## 4. 核心映射表

| docType | 编号前缀 | 借方科目 | 贷方科目 | 有对手方 |
|---------|---------|---------|---------|:------:|
| RECEIPT | SK | 1122/2211 | — | ✅ 客户 |
| PAYMENT | FK | 2202/6602 | — | ✅ 供应商 |
| EXPENSE | FY | 6602 | 1122 | ❌ |
| INVOICE_IN | FP | — | — | ✅ 供应商 |
| INVOICE_OUT | FP | — | — | ✅ 客户 |
| TRANSFER | ZC | 分录指定 | 分录指定 | ❌ |
| SALARY | GR | 2211 | 1002 | ❌ |

---

## 5. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sme/arap/business-doc/page` | 列表分页 |
| POST | `/api/sme/arap/business-doc` | 创建 |
| POST | `/api/sme/arap/business-doc/{id}/approve` | 审核 |
| POST | `/api/sme/arap/business-doc/{id}/reverse` | 红冲 |
| GET | `/api/sme/arap/business-doc/doc-types` | 获取所有 docType |

---

## 6. 验收标准

| ID | BDD 场景 | 关联 SPEC |
|----|---------|----------|
| AT-01 | Given RECEIPT When 提交 Then 生成单据 + 科目映射正确 | P-BUSINESSDOC-LIST.md |
| AT-02 | Given TRANSFER When 提交 Then 借贷科目从分录读取 | P-TRANSFER.md |
| AT-03 | Given SALARY When 提交 Then 凭证科目=2211/1002 | P-SALARY.md |
| AT-04 | Given 审核后 When 自动生成凭证 Then 凭证状态=DRAFT | P-BUSINESSDOC-LIST.md |
| AT-05 | Given 红冲 When 原单 isReversed=true Then 红冲单创建成功 | P-BUSINESSDOC-LIST.md |