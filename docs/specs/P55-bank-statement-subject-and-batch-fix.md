# P55 SPEC — 银行流水科目配置修复 + 批量操作失败可见性

> **版本**：V1.0 | **最后修改**：2026-08-05 | **作者**：Sisyphus
> **状态**：⏳ 待审核

> **编号**：HUICAI-SPC-055 | 优先级：高（P55）
> 依据：生产环境银行对账单页面 `bank_interest_fee` 分类流水生成凭证报「缺少科目配置」，批量确认/审核静默漏执行
> 目标：修复 A 类银行流水硬编码科目引用错误，并让批量操作失败对用户可见
> 工期：单批交付，1 个 commit

---

> **关联需求**: REQ-2026-007（自动制证）、REQ-2026-072（批量凭证审核）

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | `bank_interest_fee` 硬编码科目 `6602.01`→`6603`、`6602.02`→`6603` | `AutoGenerationService.java` | ✅ 低 |
| 2 | 同步修正 `previewDraft()` 预览分录科目 | `BankStatementServiceImpl.java` | ✅ 低 |
| 3 | 批量确认/审核/制证返回失败明细 | `BankStatementServiceImpl.java` + Controller | ⚠️ 中 |
| 4 | 前端批量结果对话框展示失败列表 | `BankStatementView.vue` | ⚠️ 中 |

---

## 1. 输入契约

### 1.1 触发场景

| # | 场景 | 前置条件 |
|---|------|---------|
| S1 | 银行对账单导入含「手续费/利息」流水 | 流水被分类为 `bank_interest_fee`，reviewStatus=CONFIRMED |
| S2 | 用户点「批量审核」或「批量制证」 | 选中多条 CONFIRMED 流水，其中含 `bank_interest_fee` |
| S3 | 用户在详情/预览中查看分录 | 流水分类为 `bank_interest_fee` |

### 1.2 科目现状（生产库 t_subject）

| code | name | level | 是否存在 |
|------|------|-------|---------|
| 1002 | 银行存款 | 1 | ✅ |
| 6602 | 管理费用 | 1 | ✅ |
| 6603 | 财务费用 | 1 | ✅ |
| 6602.01 | （代码引用的手续费科目） | - | ❌ 不存在 |
| 6602.02 | （代码引用的利息收入科目） | - | ❌ 不存在 |

---

## 2. 输出契约

### 2.1 修复后分录（bank_interest_fee）

| 方向 | 借方 | 贷方 |
|------|------|------|
| in（利息收入） | 1002 银行存款 | **6603 财务费用**（利息收入红字/贷方） |
| out（手续费） | **6603 财务费用**（手续费） | 1002 银行存款 |

> 说明：`6602` 语义为管理费用，手续费/利息属于财务费用（`6603`）。因科目表无 `6603.01`/`6603.02` 二级科目，采用一级科目 `6603`，与 `TAX_WITHHOLDING`（2221）、`SALARY_SOCIAL`（2211）等硬编码路径一致。

### 2.2 批量操作返回契约

`batchReview` / `batchAudit` / `batchGenerateVouchers` 返回类型从 `int` 改为：

```json
{
  "total": 10,
  "success": 8,
  "failed": [
    {"id": 11, "reason": "缺少科目配置, 无法生成凭证. classification=bank_interest_fee"}
  ]
}
```

---

## 3. 状态流转

- 修复后：CONFIRMED → 批量审核 → 成功 `voucher_generated` / `payment_created`；失败条目**保持原状态**，前端明确提示失败原因
- 不改变既有单条审核/确认状态机

---

## 4. 异常处理

| 场景 | 行为 |
|------|------|
| 批量中单条失败 | 捕获异常 → 记录 `id + reason` → 继续处理其余条目（不整体回滚） |
| 全部成功 | failed 为空数组 |
| 全部失败 | success=0，failed 含全部条目 |

---

## 5. 验收标准（BDD）

### AT-01 手续费流水可生成凭证
**Given** 一条 `bank_interest_fee`/EXPENSE 流水处于 CONFIRMED
**When** 调用批量审核
**Then** 返回 success=1，流水状态变为 `voucher_generated`，凭证存在且借方科目=6603、贷方=1002

### AT-02 利息流水可生成凭证
**Given** 一条 `bank_interest_fee`/INCOME 流水处于 CONFIRMED
**When** 调用批量审核
**Then** 凭证借方=1002、贷方=6603

### AT-03 批量失败可见
**Given** 选中 10 条流水批量审核，其中 2 条无法制证
**When** 调用批量审核
**Then** 返回 success=8、failed 含 2 条及失败原因，前端弹出结果对话框展示失败列表

### AT-04 失败条目不误标成功
**Given** AT-03 场景
**Then** 失败 2 条 reviewStatus 保持 CONFIRMED（未被错误推进）

### AT-05 预览分录与制证一致
**Given** `bank_interest_fee` 流水
**When** 调用 previewDraft
**Then** 返回分录科目为 1002 与 6603，与制证路径一致

---

## 6. 回归影响

| 影响面 | 说明 |
|--------|------|
| 既有批量接口调用方 | Controller 返回类型变更 → 前端同步适配 |
| 现有测试 | `BankStatementServiceImpl` / `AutoGenerationService` 相关测试需适配返回类型与科目断言 |
| 其他分类 | `business_receipt/payment`、`internal_transfer`、`salary_social`、`tax_withholding` 硬编码科目不受影响 |
