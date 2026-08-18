# SPEC: 工资单（SALARY）手动录入

## 背景

目前 `SALARY_SOCIAL` 银行流水分类在自动生成流程中被映射为 `PAYMENT`（付款单），且模板匹配后直接制证（DRAFT→VOUCHERED），缺少人工确认环节，也没有手工录入入口。本 SPEC 将其拆分为独立的 `SALARY`（工资单）doc type。

## 变更范围

| 文件 | 变更 |
|------|------|
| `BusinessDocServiceImpl.java` | DOC_TYPE_CODE + DOC_VOUCHER_SUBJECTS + SUPPLIER_DOC_TYPES 增加 SALARY |
| `AutoGenerationService.java` | mapToDocType 改 SALARY_SOCIAL→SALARY，B类制证跳过 SALARY（保持 DRAFT） |
| `businessDoc.ts` | DOC_TYPE_LABELS 增加 SALARY |
| `BusinessDocEdit.vue` | 无需改动（SALARY 行为同 PAYMENT） |

## 凭证科目映射

```
SALARY: 借 2211 应付职工薪酬 → 贷 1002 银行存款
```

## 状态流转

```
银行流水 SALARY_SOCIAL → 生成 SALARY 单据(DRAFT) → 人工确认 → 凭证(借2211/贷1002)
                          手工录入 SALARY 单据(DRAFT) → 人工确认 → 同上
```

**关键**：银行流水自动生成的 SALARY 单据**不自动制证**，停在 DRAFT，等待人工审核确认后生成凭证。

## 编号

格式：`GR + 期间(yyyyMM) + 流水号4位`，例 `GR2026080001`

## BDD 验收标准

- **场景1**：手工录入工资单，选科目确认 → 生成凭证，借方=2211，贷方=1002
- **场景2**：银行流水分类=SALARY_SOCIAL → 自动生成 SALARY 单据，状态=DRAFT，不自动制证
- **场景3**：SALARY 单据确认后 → 凭证生成，单据状态=VOUCHERED
- **场景4**：SALARY 编号前缀=GR，格式正确

## 不做的

- 不新增字段（员工明细、社保明细等暂不扩展，后续需求另开）
- 不拆分工资/社保为两个独立类型（统一 SALARY 覆盖）