# P16 SPEC — 预算编制/控制/分析

> 状态：补单测中（11 方法全已实现, 0 测试覆盖）
> 目标：补 5 个单测 + 文档化预算状态机
> 工期：1 批

---

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_budget` (预算主表) | ✅ 实体 (P3) |
| `t_budget_entry` (预算科目明细) | ✅ 实体 (P3) |
| `t_budget_adjustment` (预算调整单) | ✅ 实体 (P3) |
| `BudgetServiceImpl` | ✅ 11 方法全实现 (P3+补全) |
| `BudgetServiceImplTest` | ❌ **零测试** — 本批补 5 |

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
                              createAdjustment (DRAFT) ──→ approveAdjustment ──→ APPROVED
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

## 4. 测试验收

**目标**: 295 → 300 (+5 测试)
