# P48 SPEC — 核销匹配 Agent

> **编号**：HUICAI-SPC-048
> **版本**：V1.0 | **修改日期**：2026-07-09
> **修改人**：Hermes
> **依据**：AI 辅助能力开发计划 V2.0 §阶段二 + 10-ai-orchestration-design.md §5
> **范围**：核销工作台智能推荐匹配对（基于金额+客户/供应商相似度）

> **关联需求**: REQ-2026-060

---

## §0 当前状态

| 维度 | 状态 | 说明 |
|------|------|------|
| 核销工作台 | ✅ 已有 | 支持手动匹配（精准/组合/模糊策略） |
| 核销推荐 | ❌ 缺乏 | 用户需自行筛选单据并匹配 |
| MatchAgent（科目映射） | ✅ 已有 | 规则→pgvector→LLM 三阶段 |
| 核销匹配增强 | ❌ 待实现 | 在 MatchAgent 中新增核销匹配意图 |

---

## §1 实现方案

### 1.1 核心流程

```
核销工作台 → 点击"AI 推荐匹配" → 当前未匹配单据列表
  → RouterAgent (intent="reconciliation_match")
  → 同一客户/供应商下，金额相近的应收/应付单据对 → 匹配分数计算 → top-3 推荐
  → 前端展示匹配对 + 置信度 → 人工确认 → 填入匹配表单
```

### 1.2 Python 端 — MatchAgent 增强

| 项 | 说明 |
|----|------|
| 新增 intent | `"reconciliation_match"` |
| 输入 | `{ receivable_docs: [{id, amount, customer_id}], payable_docs: [{id, amount, vendor_id}] }` |
| 输出 | `{ matches: [{receivable_id, payable_id, score, reasoning}], top_n: 3 }` |
| 匹配逻辑 | 同客户/供应商下，金额差最小 → 匹配分数最高；辅助基于历史匹配向量检索 |

### 1.3 Java 端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| `ReconciliationServiceImpl.java` | 新增推荐 API（接收 AI 结果 + 匹配建议） | 🟡 中 |
| `ReconciliationController.java` | 新增 AI 推荐匹配端点 | 🟢 低 |

### 1.4 前端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| 核销工作台 | 嵌入"AI 推荐匹配"按钮，展示推荐结果 | 🟢 低 |

### 1.5 安全机制

- AI 推荐结果直接填入匹配表单，人工确认后执行
- 匹配分数 < 0.5 不展示
- 最终核销执行仍由用户触发

---

## §2 验证清单

- [ ] MatchAgent 新增 `reconciliation_match` 意图路由
- [ ] Python 端返回 top-3 匹配对 + 分数
- [ ] Java 端推荐 API 返回结果
- [ ] 前端工作台展示推荐结果
- [ ] 匹配分数 < 0.5 不展示
- [ ] 准确率 ≥ 60%（基于历史数据验证）

---

## §3 排期

| 子任务 | 工时 | 依赖 |
|--------|------|------|
| MatchAgent 增强（新意图） | 0.5d | 无 |
| Java 推荐 API | 0.5d | 无 |
| 前端推荐展示 | 0.5d | Python 端完成 |
| 集成测试 | 0.5d | 以上完成 |

**总工期：2 人日**

---

## §4 YAML 契约

```yaml
contract_version: "1.0"
entity: null
module: arap

endpoints:
  - method: POST
    path: /api/v1/agent/route
    intent: "reconciliation_match"
    request: "{ receivable_docs, payable_docs }"
    response: "{ matches: [{receivable_id, payable_id, score, reasoning}] }"
  - method: GET
    path: /api/v1/reconciliation/ai-recommend
    description: "获取 AI 核销匹配推荐"

acceptance_tests:
  - id: AT-001
    description: "同一客户应收应付匹配"
    assertion: "匹配分数 ≥ 0.6"
  - id: AT-002
    description: "不同客户不匹配"
    assertion: "匹配分数 < 0.3"
  - id: AT-003
    description: "分数 < 0.5 不展示"
    assertion: "前端不显示低分匹配"
```

> **文档结束**