# ai-evolution-v2：AI 演进第二阶段开发计划

> **分支**：`ai-evolution-v2`（基于 main）
> **日期**：2026-07-09
> **关联文档**：[AI辅助能力开发计划](../plans/慧财财务系统 AI 辅助能力开发计划.md) V2.0、[P0-P3-roadmap](../plans/P0-P3-roadmap.md) V4.0、[10-ai-orchestration-design.md](../../design/10-AI编排层.md)

---

## 一、前情回顾

`ai-evolution` 分支（2026-06-30 ~ 2026-07-09）的原始目标是在 P40 基础之上推进 AI 演进（P41），但实际交付集中于 P42-P45 的 Bug 修复和基础设施稳定化。**原始 AI 演进功能从未进入真正的开发阶段。**

### 已交付的（来自 ai-evolution）：

| 内容 | 状态 |
|------|------|
| P34 应收应付合并到业务单据 | ✅ main |
| P40 AI 服务骨架增强（CORS/日志/Swagger） | ✅ main |
| P42 核销增强（Timeline/FIFO） | ✅ main |
| P43-P45 核销日志/列表/上游追溯 | ✅ main |
| 测试体系修复（942 测试 0 失败） | ✅ main |
| 文档编号 + 双向闭环治理 | ✅ main |

### 未交付的（本轮目标）：

| 编号 | 任务 | 原始来源 | 预估工时 |
|------|------|---------|---------|
| P3-5 | 流水分类 Agent | AI 演进计划 阶段二 | 2人日 |
| P3-6 | 审核建议 Agent | AI 演进计划 阶段二 | 3人日 |
| P3-7 | 核销匹配 Agent | AI 演进计划 阶段二 | 2人日 |
| P3-12 | AI 服务测试补齐 | P0-P3 路线图 | 3人日 |
| — | Agent 反馈闭环 | AI 演进计划 阶段二 | 2人日 |

---

## 二、当前 AI 体系状态

### 已就绪的 AI 基础设施

| 组件 | 状态 | 说明 |
|------|------|------|
| Python FastAPI 服务（5 端点） | ✅ | health/match/anomaly/embedding/ocr |
| RouterAgent（LangGraph） | ✅ | 意图识别 + 子 Agent 路由 |
| MatchAgent（科目映射） | ✅ | 规则→pgvector→LLM 三阶段 |
| AnomalyAgent（异常检测） | ✅ | 品名背离/时间/金额/对方重复 |
| 前端 AI 按钮 | ✅ | 发票/凭证页嵌入 |
| Java 后端 AiTask 状态机 | ✅ | PENDING→PROCESSING→COMPLETED/FAILED→REVIEWED |
| RabbitMQ 异步通信 | ✅ | `huicai.ai.task.queue` / `huicai.ai.result.queue` |
| 三大数据表 | ✅ | t_ai_task / t_ai_anomaly_tag / t_ai_feedback_log |

### 待实现的缺口

```
当前 Agent 体系：
  RouterAgent ──→ MatchAgent (科目映射) ✅
                └→ AnomalyAgent (异常检测) ✅
                └→ (空白) 流水分类 Agent ❌
                └→ (空白) 审核建议 Agent ❌
                └→ (空白) 核销匹配 Agent ❌
                └→ (空白) Agent 反馈闭环 ❌
```

---

## 三、任务详情

### T1: 流水分类 Agent（P3-5, 2人日）

**目标：** 银行流水导入后，当规则引擎（8类分类）无法确定分类时，由 AI 做语义补充分类。

**现状：** 规则引擎（`ClassificationRuleService` / `FallbackHeuristicService`）已覆盖 8 类分类（business_receipt/payment、bank_interest_fee、tax_withholding、internal_transfer 等）。但非标流水（摘要模糊、对方户名不标准）会 fallback 到未知分类。

**实现方案：**
1. Python 端：新增 `ClassificationAgent`，基于流水摘要+金额+对方户名做语义分类
2. Java 端：`FallbackHeuristicService` 中 fallback 到未知时，创建 AiTask 走 MQ 触发 AI 分类
3. 前端：导入预览页展示 AI 推荐分类标签 + 人工确认按钮
4. 链路：规则引擎不确定 → MQ → Python AI 分类 → 结果回写 → 前端展示

**涉及文件：** `ai-service/core/agent.py`（新增 ClassificationAgent）、`ai-service/api/agent.py`（路由注册）、`FallbackHeuristicService.java`（MQ fallback）、前端导入预览页

### T2: 审核建议 Agent（P3-6, 3人日）

**目标：** 费用报销提交时，AI 初审发票合规性、金额合理性、预算匹配度。

**现状：** 费用报销模块（`arap`）已有完整状态机（DRAFT→SUBMITTED→APPROVED→PAID→REJECTED），但审核环节完全依赖人工判断。

**实现方案：**
1. Python 端：新增 `ReviewAgent`，检查：发票合规（抬头/金额/税率）、费用合理性（ vs 历史报销）、预算占用校验
2. Java 端：报销提交（SUBMITTED）时触发 AiTask，结果写入 `t_ai_anomaly_tag`，前端审核页展示 AI 建议
3. 前端：审核页增加 AI 建议面板（绿色通过/黄色警告/红色拒绝）
4. 安全：AI 建议不影响状态机流转，审核员仍可自由决定

**涉及文件：** `ai-service/core/agent.py`（新增 ReviewAgent）、`ai-service/api/agent.py`（路由注册）、`ExpenseServiceImpl.java`（提交时触发）、前端审核页

### T3: 核销匹配 Agent（P3-7, 2人日）

**目标：** 核销工作台自动推荐匹配对（基于金额+客户/供应商相似度）。

**现状：** 核销工作台已支持手动匹配（精准/组合/模糊策略），但缺乏智能推荐。用户需自行筛选应收/应付单据并匹配。

**实现方案：**
1. Python 端：新增 `MatchAgent` 增强版（原 MatchAgent 只做科目映射，新增强核销匹配意图）
2. 匹配逻辑：同一客户/供应商下，金额相近的应收/应付单据对 → 计算匹配分数 → top-3 推荐
3. Java 端：`ReconciliationServiceImpl` 新增推荐 API，前端工作台嵌入"AI 推荐匹配"按钮
4. 与现有核销逻辑集成：推荐结果直接填入匹配表单，人工确认后执行

**涉及文件：** `ai-service/core/agent.py`（增强 MatchAgent）、`ai-service/api/agent.py`（新增 intent 路由）、`ReconciliationServiceImpl.java`（推荐 API）、前端工作台

### T4: Agent 反馈闭环（2人日）

**目标：** 收集用户对 AI 结果的反馈，用于 Agent 效果评估和持续优化。

**现状：** `t_ai_feedback_log` 表已创建，`AiFeedbackLogService` 和 `AiFeedbackLogController` 已实现，但前端未嵌入反馈按钮，缺乏数据驱动优化。

**实现方案：**
1. 前端：在每个 AI 结果旁嵌入反馈按钮（👍 接受 / 👎 拒绝 / ✏️ 修改）
2. 后端：反馈数据写入 `t_ai_feedback_log`，新增统计 API（各 Agent 接受率/拒绝率）
3. Python：新增反馈分析端点，定期输出 Agent 效果报告
4. 仪表盘：展示各 Agent 效果指标

**涉及文件：** 前端组件（AI 反馈按钮）、`AiFeedbackLogService.java`（统计 API）、`ai-service/api/agent.py`（分析端点）

### T5: AI 服务测试补齐（P3-12, 3人日）

**目标：** AI 服务从 0 个测试到 10+ 个测试。

**现状：** `ai-service/` 零测试。Python FastAPI 端点缺乏基础的质量保障。

**实现方案：**
1. 单元测试：各 Agent 核心逻辑（MatchAgent 的三阶段管道、AnomalyAgent 的各维度检测）
2. API 测试：各端点 HTTP 响应测试（健康检查、路由、匹配、异常检测）
3. Mock 测试：使用 pytest + httpx 模拟外部依赖

**涉及文件：** `ai-service/tests/`（新建目录）

---

## 四、执行顺序

```
第1周           第2周           第3周
┌────────┐     ┌────────┐     ┌────────┐
│ T1: 流水   │     │ T2: 审核   │     │ T4: 反馈   │
│ 分类Agent │ ──→ │ 建议Agent │ ──→ │ 闭环     │
└────────┘     └────────┘     └────────┘
     │              │              │
     ▼              ▼              ▼
┌────────┐     ┌────────┐     ┌────────┐
│ T3: 核销   │     │ T5: AI   │     │ 收尾验证 │
│ 匹配Agent │     │ 测试补齐  │     │ 合并main │
└────────┘     └────────┘     └────────┘
```

推荐从 **T1（流水分类Agent）** 开始，与现有规则引擎衔接最紧密，变更范围最小，风险最低。

---

## 五、验收标准

| 任务 | 验收标准 |
|------|---------|
| T1 | 规则引擎 fallback 到未知时，AI 返回 ≥3 个分类候选，接受率 ≥70% |
| T2 | 报销审核页展示 AI 建议面板，异常检测覆盖 3+ 维度 |
| T3 | 核销工作台"AI 推荐匹配"按钮返回 top-3 匹配对，准确率 ≥60% |
| T4 | 每个 AI 结果旁有反馈按钮，仪表盘展示各 Agent 接受率 |
| T5 | 10+ 测试，CI 可运行，覆盖率 ≥50% |

---

> **文档结束。本文档定义了 ai-evolution-v2 分支的开发范围。**