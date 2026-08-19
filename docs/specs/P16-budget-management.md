# P16 SPEC — 预算编制/控制/分析
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-016（11 方法全已实现, 8 测试覆盖 ✅）
> 目标：补 5 个单测 + 文档化预算状态机（✅ 已完成 2026-07-28）
> 工期：1 批

> **test_ref**：BudgetServiceImplTest, BudgetStateMachineServiceImplTest, BudgetFlowE2ETest
---

> **关联需求**: REQ-2026-031, REQ-2026-032, REQ-2026-033

## 1. 输入契约
→ 见本文 [现状摸底 / 预算编制与控制参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [预算状态机图 / 状态常量 / 状态转换方法] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_budget` (预算主表) | ✅ 实体 (P3) |
| `t_budget_entry` (预算科目明细) | ✅ 实体 (P3) |
| `t_budget_adjustment` (预算调整单) | ✅ 实体 (P3) |
| `BudgetServiceImpl` | ✅ 11 方法全实现 (P3+补全) |
| `BudgetServiceImplTest` | ✅ **8 测试覆盖 (P16)** |

**已实现方法**:
- `pageQuery/getById/create/approve/activate` — 预算 CRUD + 状态机
- `checkBudget(subjectId, period, amount)` — 预算控制检查 (STRICT/APPROVE/WARN 三种控制模式)
- `executionAnalysis(period)` — 期间执行汇总 (总预算/已用/执行率)
- `pageQueryAdjustment/createAdjustment/approveAdjustment` — 调整单 CRUD

---

## 2. P16-1 任务 (本批)

### 2.1 补 5 个单测

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `create_设置状态DRAFT_插入主表和明细` | create |
| 2 | `approve_DRAFT_变APPROVED` | approve |
| 3 | `checkBudget_在预算内_pass=true` | checkBudget (WARN 模式) |
| 4 | `checkBudget_超预算_action=REQUIRE_APPROVE` | checkBudget (APPROVE 模式) |
| 5 | `executionAnalysis_有预算和明细_返回汇总` | executionAnalysis |

### 2.2 状态机

```
DRAFT ──→ approve ──→ APPROVED ──→ activate ──→ ACTIVE
                                       ↓
                              createAdjustment (PENDING) ──→ approveAdjustment ──→ APPROVED
                                                              └─→ 调增/调减 totalAmount
```

### 2.3 控制模式

| controlType | 行为 |
|---|---|
| `STRICT` | 超过预算 → pass=false, action=BLOCK |
| `APPROVE` | 超过预算 → pass=true, action=REQUIRE_APPROVE (需主管审批) |
| `WARN` | 80% 触发 WARN, 100% 阻断 |

---

## 3. 不在 P16 范围

- 预算滚动预测 (rolling forecast)
- 多版本预算编制
- 预算与报销单自动联动
- 预算执行率预警 (P18 报表候选)

---

## 4. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-07-18 | 调整单初始状态 `DRAFT` → `PENDING` | Bug fix: DRAFT 无法通过审批检查 `isAdjustmentApprovable()` 需 `PENDING` |

---

## 5. 测试验收

**目标**: 295 → 300 (+5 测试) ✅ 已完成，实际 8 测试覆盖

**测试文件**: `BudgetServiceImplTest.java` (8 个 @Test 方法)

| 测试方法 | 覆盖场景 | 验收标准 |
|---------|---------|---------|
| `create_设置状态DRAFT_插入主表和明细` | 创建预算，自动设置 DRAFT 状态，汇总总金额 | AT-P16-1 |
| `approve_SUBMITTED_变APPROVED` | 提交后审批，状态变为 APPROVED | AT-P16-3 |
| `approve_非SUBMITTED_抛异常` | 非提交状态审批抛 BusinessException | 负向断言 |
| `checkBudget_在预算内_passTrue` | WARN 模式，预算内通过 | AT-P16-4 |
| `checkBudget_超预算_action_REQUIRE_APPROVE` | APPROVE 模式超预算需审批 | AT-P16-4 |
| `checkBudget_无预算配置_passTrue` | 无预算配置直接通过 | 边界场景 |
| `checkBudget_超预算BLOCK_passFalse` | BLOCK 模式超预算拦截 | AT-P16-5 |
| `executionAnalysis_有预算和明细_返回汇总` | 执行分析汇总计算 | 正向场景 |

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P16-1 | 预算创建后状态为DRAFT | `create() → status == 'DRAFT'` |
| AT-P16-2 | 提交后状态为SUBMITTED | `submit() → status == 'SUBMITTED'` |
| AT-P16-3 | 审批后状态为APPROVED | `approve() → status == 'APPROVED'` |
| AT-P16-4 | 预算检查返回余额 | `check(subjectId, amount) → remaining >= 0` |
| AT-P16-5 | 超预算BLOCK类型阻止提交 | `BLOCK type + over budget → BusinessException` |
