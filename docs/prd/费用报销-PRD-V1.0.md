# 费用报销 PRD

> **编号**：HUICAI-PRD-008
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD：`(../CORE-需求分析.md)` 
> **关联设计**：DSN-费用报销管理.md
> **关联SPEC**：P11-employee-expense-reimbursement.md
> **对应包**：com.huicai.sme.arap（ExpenseReimbursementServiceImpl）

---

## 1. 模块定位

管理员工费用报销单提交、审批、凭证生成全流程。

**做什么**：报销单录入+审批+自动生成凭证。

**不做什么**：
- 不做多级审批流（仅直接上级审批）
- 不做移动端报销（V2.0 移动端审批）
- 不做预算预占（由预算管理模块单独处理）

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| EXP-01 | 报销单录入 | P0 | ✅ 已完成 | 部门/员工/报销类型/金额/附件 |
| EXP-02 | 报销审批 | P0 | ✅ 已完成 | 审批通过后生成凭证(DRAFT) |
| EXP-03 | 凭证自动生成 | P0 | ✅ 已完成 | 审批通过后自动生成凭证(DRAFT) |
| EXP-04 | Excel 导出 | P1 | ✅ 已完成 | 含报销明细+审批状态 |

---

## 3. 状态流转

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED
               ↕
           REJECTED(→DRAFT)
```

---

## 4. 验收标准

| ID | BDD 场景 |
|----|---------|
| AT-01 | Given 员工录入报销单 When 提交 Then 状态=SUBMITTED |
| AT-02 | Given SUBMITTED报销单 When 审批通过 Then 状态=APPROVED + 自动生成凭证 |
| AT-03 | Given SUBMITTED报销单 When 驳回 Then 状态=DRAFT + 记录原因 |
| AT-04 | Given APPROVED报销单 When 过账 Then 状态=VOUCHERED |

---

## 5. 不做的事

| 不做 | 理由 |
|------|------|
| 多级审批 | 非当前需求 |
| 移动端 | V2.0 |
| 预算预占 | 预算管理模块 |

---

## 6. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sme/arap/expense/page` | 报销单分页 |
| POST | `/api/sme/arap/expense` | 创建报销单 |
| POST | `/api/sme/arap/expense/{id}/submit` | 提交 |
| POST | `/api/sme/arap/expense/{id}/approve` | 审批通过 |
| POST | `/api/sme/arap/expense/{id}/reject` | 驳回 |
| GET | `/api/sme/arap/expense/export` | Excel 导出 |

---

> **文档结束。**