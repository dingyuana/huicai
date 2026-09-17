# 05-费用报销管理设计

> **关联PRD**：../prd/费用报销-PRD-V1.0.md
> **关联SPEC**：P11-员工费用报销.md
> **编号**：HUICAI-DES-006
> **版本**：V1.1 | **修改日期**：2026-09-17 | **修改人**：Hermes | **修改内容**：新增 §8 费用汇总报表设计（对齐竞品多维度费用报表能力，填补 G-1 缺口）
> 代码包：`com.huicai.module.arap`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：全员报销入口与费用管控中心。员工填写报销单→审批→打款→生成凭证。

**对比传统：**
- 传统：PC端+实体发票，当前：**电子流程+MinIO附件**
- 传统：多级审批流，当前：**状态机控制**（DRAFT→SUBMITTED→APPROVED/REJECTED→VOUCHERED）
- 当前新增：AI 辅助审核（预览）

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| ExpenseReimbursementService | 报销单CRUD + 状态流转 + 凭证生成 |
| ExpenseTypeService | 费用类型管理 |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_expense_reimbursement | 报销单 | employee_id, expense_type, amount, summary, status, bank_stmt_id, voucher_id |

## 4. 状态机

```
DRAFT ──submit──→ SUBMITTED ──approve──→ APPROVED ──generateVoucher──→ VOUCHERED
  ↕                              ↕
  edit                        reject(→REJECTED)
```

## 5. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/expense-reimbursements/page | GET | 分页 |
| /api/v1/expense-reimbursements/{id} | GET | 详情 |
| /api/v1/expense-reimbursements | POST | 创建草稿 |
| /api/v1/expense-reimbursements/{id} | PUT | 修改草稿 |
| /api/v1/expense-reimbursements/{id}/submit | POST | 提交 |
| /api/v1/expense-reimbursements/{id}/approve | POST | 审核通过 |
| /api/v1/expense-reimbursements/{id}/reject | POST | 驳回 |
| /api/v1/expense-reimbursements/{id}/auto-voucher | POST | 自动生成凭证 |

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| AI 初审 | 扫描附件自动校验合规性 | 🟡 P2 |
| 科目推荐 | 基于费用类型+摘要推荐科目 | 🟡 P2 |

## 7. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 9 个端点+完整状态机 |
| 前端 | ✅ 刚完成 | P1-D 新建 ExpenseList/ExpenseEdit |
| 测试 | ❌ 缺 | ExpenseReimbursementService 测试未写 |
| 对传统覆盖 | ✅ | 报销/审批/凭证全流程 |

---

## 8. 费用汇总报表（V1.1 新增，G-1）

**定位**：管理型聚合报表。竞品基线（金蝶云报销"多维度费用报表"、易代账费用统计）的核心能力，
现有端点只有单笔 CRUD，缺"按什么维度汇总花了多少钱"的管理视图。

### 8.1 报表口径

| 项 | 定义 |
|----|------|
| 数据源 | t_expense_reimbursement + t_employee（部门） + 费用类型字典 |
| 统计范围 | 期间区间内的报账单，`status ∈ (APPROVED, VOUCHERED)`（已生效单据，不含 DRAFT/SUBMITTED/REJECTED） |
| 汇总维度 | 默认**部门 × 费用类型**交叉汇总；支持切换单维度（仅按部门 / 仅按费用类型 / 仅按员工） |
| 金额口径 | 报销金额合计 + 单据数 + 人均（金额/部门员工数，部门员工数取统计期末在职人数） |
| 对比口径 | 支持同比（去年同期同维度）、环比（上一期间同维度）列 |
| 导出 | EasyExcel 导出（与其他报表中心导出规范一致） |

### 8.2 API 端点

| 端点 | 方法 | 说明 | SPEC |
|------|------|------|------|
| /api/sme/arap/v1/expense-reimbursements/summary | GET | 费用汇总报表（参数：period_from, period_to, group_by=DEPT\|EXPENSE_TYPE\|EMPLOYEE, include_yoy, include_mom） | P76 |
| /api/sme/arap/v1/expense-reimbursements/summary/export | GET | 导出 Excel | P76 |

**响应结构（summary）：**

```json
{
  "periodFrom": "202601",
  "periodTo": "202606",
  "groupBy": "DEPT",
  "rows": [
    {"dimId": 1, "dimName": "研发部", "count": 42, "amount": 152000.00,
     "amountYoy": 138000.00, "amountMom": 28000.00, "perCapita": 15200.00}
  ],
  "total": {"count": 180, "amount": 620000.00}
}
```

### 8.3 异常与边界

| 场景 | 处理 |
|------|------|
| 期间区间无有效单据 | 返回空 rows + total 全 0，不报错 |
| group_by 参数非法 | 400 bad_request |
| 同比无上年同期数据 | amountYoy 返回 null（前端显示 "—"），不影响环比 |
| 数据权限 | 由 EnterpriseDataPermissionInterceptor 注入 enterprise_id，服务层不做手工过滤（与 P75 一致） |

### 8.4 与 P75 的关系

P75 是应收应付余额（会计恒等式推导），本节是费用发生额（期间流量）汇总，
两者属于不同口径，互不依赖，但共用报表中心的 Excel 导出组件与期间校验工具类。

## 9. 成熟度与待办（更新）

| 维度 | 状态 | 备注 |
|------|------|------|
| 费用汇总报表 | ❌ 待开发 | §8 已设计，SPEC P76 待建；含同比/环比/人均/Excel 导出 |

> **文档结束**