# P47 SPEC — 审核建议 Agent

> **编号**：HUICAI-SPC-047
> **版本**：V1.0 | **修改日期**：2026-07-09
> **修改人**：Hermes
> **依据**：AI 辅助能力开发计划 V2.0 §阶段二 + 10-ai-orchestration-design.md §5
> **范围**：费用报销提交时，AI 初审发票合规性、金额合理性、预算匹配度

> **关联需求**: REQ-2026-059

## 1. 输入契约
→ 见本文 §1.2 ReviewAgent 输入输出定义、§4 YAML 契约 endpoints

## 2. 输出契约
→ 见本文 §2 验证清单、§4 YAML 契约 acceptance_tests

## 3. 状态流转
→ 见本文 §1.1 核心流程（SUBMITTED → MQ → ReviewAgent → 结果写入 t_ai_anomaly_tag）

## 4. 异常处理
→ 见本文 §1.5 安全机制（AI 建议不影响状态机流转、不自动审批/拒绝）

---

## §0 当前状态

| 维度 | 状态 | 说明 |
|------|------|------|
| 费用报销模块 | ✅ 已有 | 完整状态机 DRAFT→SUBMITTED→APPROVED→PAID→REJECTED |
| 审核环节 | ✅ 已有 | 完全依赖人工判断 |
| AI 异常检测 | ✅ 已有 | AnomalyAgent 已注册（品名背离/时间异常/金额波动/对方重复） |
| ReviewAgent | ❌ 待实现 | 新增审核建议 Agent |
| 前端审核 AI 面板 | ❌ 待实现 | 审核页展示 AI 建议 |

---

## §1 实现方案

### 1.1 核心流程

```
费用报销提交 (SUBMITTED) → MQ 触发 ReviewAgent
  ├── 发票合规检查（抬头/金额/税率一致性）
  ├── 费用合理性检查（vs 历史报销均值）
  ├── 预算占用校验（预算余额是否充足）
  └── 结果写入 t_ai_anomaly_tag → 前端审核页展示 AI 建议面板
```

### 1.2 Python 端 — ReviewAgent

| 项 | 说明 |
|----|------|
| intent | `"review"` |
| 输入 | `{ expense_id, invoice_data, amount, department, budget_code }` |
| 输出 | `{ verdict: "PASS"/"WARN"/"REJECT", checks: [{type, status, detail}], confidence }` |
| 维度 | 发票合规、费用合理性、预算匹配度 |

### 1.3 Java 端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| `ExpenseServiceImpl.java` | SUBMITTED 状态转换时触发 AiTask | 🟡 中 |
| `ExpenseController.java` | 新增 AI 建议查询端点 | 🟢 低 |

### 1.4 前端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| 费用报销审核页 | 增加 AI 建议面板（绿色通过/黄色警告/红色拒绝） | 🟢 低 |

### 1.5 安全机制

- AI 建议不影响状态机流转，审核员仍可自由决定
- AI 建议仅作为参考信息展示，不自动审批/拒绝
- 复用 `t_ai_anomaly_tag` 表存储检测结果

---

## §2 验证清单

- [ ] Python 端 ReviewAgent 注册到 RouterAgent
- [ ] 报销提交时触发 AiTask，结果写入 t_ai_anomaly_tag
- [ ] 前端审核页展示 AI 建议面板（3 色状态）
- [ ] AI 建议不影响状态机流转
- [ ] 3+ 维度检测覆盖

---

## §3 排期

| 子任务 | 工时 | 依赖 |
|--------|------|------|
| ReviewAgent 实现（3 维度检测） | 1.0d | 无 |
| Java 端触发 + 回调 | 0.5d | 无 |
| 前端 AI 建议面板 | 0.5d | Python 端完成 |
| 集成测试 + 异常 case | 1.0d | 以上完成 |

**总工期：3 人日**

---

## §4 YAML 契约

```yaml
contract_version: "1.0"
entity: null
module: arap

endpoints:
  - method: POST
    path: /api/v1/agent/route
    intent: "review"
    request: "{ expense_id, invoice_data, amount, department, budget_code }"
    response: "{ verdict, checks: [{type, status, detail}], confidence }"
  - method: GET
    path: /api/v1/expense/{id}/ai-review
    description: "获取报销单的 AI 审核建议"

acceptance_tests:
  - id: AT-001
    description: "报销提交时触发 AI 审核"
    assertion: "SUBMITTED 状态转换 → AiTask 创建"
  - id: AT-002
    description: "合规发票返回 PASS"
    assertion: "reviewAgent verdict = PASS"
  - id: AT-003
    description: "异常发票返回 WARN/REJECT"
    assertion: "reviewAgent.checks 包含异常维度"
  - id: AT-004
    description: "AI 建议不影响状态机"
    assertion: "即使 AI 返回 REJECT，状态仍为 SUBMITTED"
```

> **文档结束**

---

## §5 BDD 验收标准

### 场景 1：合规发票审核通过
**Given** 费用报销提交，发票抬头/金额/税率一致  
**When** ReviewAgent 完成审核  
**Then** verdict = "PASS"  
**And** 前端审核页展示绿色通过面板

### 场景 2：异常发票触发警告
**Given** 费用报销提交，发票金额超出历史均值 50%  
**When** ReviewAgent 完成审核  
**Then** checks 中包含异常维度  
**And** verdict = "WARN" 或 "REJECT"

### 场景 3：AI 建议不影响状态机
**Given** ReviewAgent 返回 REJECT  
**When** 状态机流转  
**Then** 报销单状态仍为 SUBMITTED  
**And** 审核员可自由决定审批结果