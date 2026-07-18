# P46 SPEC — 流水分类 Agent

> **编号**：HUICAI-SPC-046
> **版本**：V1.0 | **修改日期**：2026-07-09
> **修改人**：Hermes
> **依据**：AI 辅助能力开发计划 V2.0 §阶段二 + 10-ai-orchestration-design.md §5
> **范围**：银行流水导入后，规则引擎无法确定分类时由 AI 语义补充分类

> **关联需求**: REQ-2026-058

---

## §0 当前状态

| 维度 | 状态 | 说明 |
|------|------|------|
| 规则引擎 | ✅ 已有 | 8 类分类（business_receipt/payment、bank_interest_fee、tax_withholding、internal_transfer 等） |
| Fallback 处理 | ✅ 已有 | FallbackHeuristicService 兜底，但非标流水 fallback 到未知 |
| Python AI 服务 | ✅ 已有 | FastAPI 5 端点，RouterAgent 已注册 |
| ClassificationAgent | ❌ 待实现 | 新增 Agent |
| 前端 AI 分类展示 | ❌ 待实现 | 导入预览页嵌入 AI 推荐标签 |

---

## §1 实现方案

### 1.1 核心流程

```
银行流水导入 → 规则引擎 8 类分类
  ├── 确定分类 (conf ≥ 0.8) → 自动分类，结束
  └── 不确定/未知 → MQ 触发 ClassificationAgent → AI 语义分类 → 结果回写 → 前端展示 top-3 候选
```

### 1.2 Python 端 — ClassificationAgent

| 项 | 说明 |
|----|------|
| intent | `"classify"` |
| 输入 | `{ summary, counterparty_name, amount, transaction_date, existing_rules }` |
| 输出 | `{ classification: string, confidence: float, candidates: [{classification, confidence, reasoning}] }` |
| 逻辑 | 基于流水摘要+金额+对方户名做语义分类，规则引擎结果作为强先验 |

### 1.3 Java 端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| `FallbackHeuristicService.java` | fallback 到未知时创建 AiTask，走 MQ 触发 AI 分类 | 🟡 中 |
| `AiTaskController.java` | 新增分类回调端点 | 🟢 低 |

### 1.4 前端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| 导入预览页 | 展示 AI 推荐分类标签 + 人工确认按钮 | 🟢 低 |

### 1.5 安全机制

- AI 推荐分类不影响单据状态，人工确认后才写入
- confidence < 0.5 时强制人工选择
- 规则引擎结果优先于 AI 推荐

---

## §2 验证清单

- [ ] Python 端 ClassificationAgent 注册到 RouterAgent
- [ ] `FallbackHeuristicService` 未知分类触发 MQ 任务
- [ ] 前端预览页展示 AI 推荐标签
- [ ] 人工确认后分类写入
- [ ] 规则引擎结果优先于 AI 推荐

---

## §3 排期

| 子任务 | 工时 | 依赖 |
|--------|------|------|
| ClassificationAgent 实现 | 0.5d | 无 |
| Java MQ 触发 + 回调 | 0.5d | 无 |
| 前端 AI 标签展示 | 0.5d | Python 端完成 |
| 集成测试 | 0.5d | 以上完成 |

**总工期：2 人日**

---

## §4 YAML 契约

```yaml
contract_version: "1.0"
entity: null
module: ai

endpoints:
  - method: POST
    path: /api/v1/agent/route
    intent: "classify"
    request: "{ summary, counterparty_name, amount, transaction_date, existing_rules }"
    response: "{ classification, confidence, candidates }"
  - method: POST
    path: /api/v1/agent/classify
    description: "直接调用分类端点（向后兼容）"

acceptance_tests:
  - id: AT-001
    description: "规则引擎确定分类时，AI 不介入"
    assertion: "FallbackHeuristicService 不触发 MQ"
  - id: AT-002
    description: "规则引擎未知分类时，AI 返回 top-3 候选"
    assertion: "MQ 消息发送 → ClassificationAgent 响应"
  - id: AT-003
    description: "confidence < 0.5 强制人工选择"
    assertion: "前端不自动填入，显示人工选择提示"
```

> **文档结束**