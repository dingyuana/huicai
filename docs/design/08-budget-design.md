# 08-预算管理设计

> **编号**：HUICAI-DES-009
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.budget`
> 设计文档：[主文档](../DESIGN.md)

---

## 1. 模块定位

传统定位：预算编制与执行控制模块。

**对比传统：**
- 传统：事后统计 → 当前：**事前控制**（报销提交时实时校验预算余额，预占预算，驳回时释放）
- 传统：简单超支警告 → 当前：**三级控制**（WARN/APPROVE_REQUIRED/BLOCK）

> **⚠️ 代码-设计差异说明：** 设计稿原计划 5 态（DRAFT→SUBMITTED→APPROVED→CLOSED + REJECTED），
> 代码实际实现了 7 态状态机，中间增加了 ACTIVE（执行中/已激活）和 FROZEN（已冻结）状态。
> CLOSED 和 REJECTED 常量已声明，暂未接入完整流转逻辑。

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| BudgetService | 预算编制、预算检查、执行分析 |
| BudgetStateMachineService | 预算状态机（5态） |
| BudgetAdjustmentService | 预算调整、调整审批 |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_budget | 预算 | budget_no, period, budget_type, total_amount, status |
| t_budget_entry | 预算明细 | budget_id, subject_id, amount, used_amount, control_type(WARN/APPROVE/BLOCK) |
| t_budget_adjustment | 预算调整 | budget_id, adjust_amount, new_amount, reason, status |

## 4. 状态机

```
DRAFT ──submit──→ SUBMITTED ──approve──→ APPROVED ──activate──→ ACTIVE ──close──→ CLOSED
  ↕                              ↕                            ↕
  edit                        reject(→REJECTED)            freeze(→FROZEN)
```

> **说明：** ACTIVE(执行中) 和 FROZEN(已冻结) 为代码实际新增状态。
> CLOSED 和 REJECTED 常量已声明，接入流转逻辑待补充。

## 5. 三级控制策略

| control_type | 行为 |
|-------------|------|
| WARN | 使用率>80%时提示 |
| APPROVE_REQUIRED | 超预算时需额外审批 |
| BLOCK | 超预算时阻止提交 |

## 6. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/budgets/page | GET | 分页 |
| /api/v1/budgets/{id} | GET | 详情 |
| /api/v1/budgets | POST | 创建 |
| /api/v1/budgets/{id}/submit | POST | 提交 |
| /api/v1/budgets/{id}/approve | POST | 审批 |
| /api/v1/budgets/check | GET | 预算检查 |
| /api/v1/budgets/execution | GET | 执行分析 |
| /api/v1/budgets/adjustments/** | CRUD | 预算调整 |

## 7. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 预算预测 | 基于历史数据预测下期预算 | 🟢 P3 |

## 8. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 含状态机+三级控制+调整 |
| 前端 | ✅ 已完善 | P1-C 完成 BudgetList/Edit + AdjustmentList |
| 测试 | ❌ 缺 | 仅有 2 个 Mock 测试，无 Mapper 真实 DB 测试 |
| 对传统覆盖 | ✅ | 编制+控制+调整+执行分析 |

> **文档结束**