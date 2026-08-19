# P49 SPEC — Agent 反馈闭环

> **编号**：HUICAI-SPC-049
> **test_ref**：AiFeedbackLogServiceImplTest
> **版本**：V1.0 | **修改日期**：2026-07-09
> **修改人**：Hermes
> **依据**：AI 辅助能力开发计划 V2.0 §阶段二
> **范围**：收集用户对 AI 结果的反馈，用于 Agent 效果评估和持续优化

> **关联需求**: REQ-2026-061

## 1. 输入契约
→ 见本文 §1.2 前端反馈按钮（👍/👎/✏️）、§4 YAML 契约 endpoints（POST /api/v1/ai/feedback）

## 2. 输出契约
→ 见本文 §2 验证清单、§4 YAML 契约 acceptance_tests

## 3. 状态流转
→ 见本文 §1.1 核心流程（AI 结果展示 → 用户反馈 → 写入 t_ai_feedback_log → 统计仪表盘）

## 4. 异常处理
→ 见本文 §1.3 Java 端 AiFeedbackLogService 统计 API 错误处理

---

## §0 当前状态

| 维度 | 状态 | 说明 |
|------|------|------|
| t_ai_feedback_log 表 | ✅ 已有 | task_id, feedback_type(ACCEPT/REJECT/MODIFY), user_id |
| AiFeedbackLogService | ✅ 已有 | CRUD 操作 |
| AiFeedbackLogController | ✅ 已有 | 反馈 API |
| 前端反馈按钮 | ❌ 缺失 | 每个 AI 结果旁无反馈 UI |
| 统计仪表盘 | ❌ 缺失 | 各 Agent 接受率/拒绝率无展示 |
| 分析端点 | ❌ 缺失 | Python 端无反馈分析 |

---

## §1 实现方案

### 1.1 核心流程

```
每个 AI 结果展示 → 用户点击 👍/👎/✏️
  → 写入 t_ai_feedback_log → 统计 API 聚合
  → 仪表盘展示各 Agent 效果指标
  → Python 端定期输出 Agent 效果报告
```

### 1.2 前端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| AI 结果组件（通用） | 每个 AI 结果旁嵌入反馈按钮（👍 接受 / 👎 拒绝 / ✏️ 修改） | 🟢 低 |
| 仪表盘页 | 展示各 Agent 接受率、拒绝率、平均响应时间 | 🟢 低 |

### 1.3 Java 端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| `AiFeedbackLogService.java` | 新增统计 API（按 Agent 类型聚合接受率/拒绝率） | 🟢 低 |
| `AiFeedbackLogController.java` | 新增统计端点 | 🟢 低 |

### 1.4 Python 端改动

| 文件 | 改动 | 风险 |
|------|------|------|
| `api/agent.py` | 新增反馈分析端点 | 🟢 低 |

---

## §2 验证清单

- [ ] 每个 AI 结果展示区有反馈按钮（👍/👎/✏️）
- [ ] 反馈数据写入 t_ai_feedback_log
- [ ] 统计 API 返回各 Agent 接受率/拒绝率
- [ ] 仪表盘展示效果指标
- [ ] Python 端反馈分析端点可用

---

## §3 排期

| 子任务 | 工时 | 依赖 |
|--------|------|------|
| 前端反馈按钮组件 | 0.5d | 无 |
| Java 统计 API | 0.5d | 无 |
| 仪表盘页面 | 0.5d | 统计 API 完成 |
| Python 分析端点 | 0.5d | 无 |

**总工期：2 人日**

---

## §4 YAML 契约

```yaml
contract_version: "1.0"
entity: AiFeedbackLog
module: ai
table: t_ai_feedback_log

endpoints:
  - method: POST
    path: /api/v1/ai/feedback
    request: "{ task_id, feedback_type, user_id, comment }"
    response: "{ id, status }"
  - method: GET
    path: /api/v1/ai/feedback/stats
    response: "{ agents: [{agent_type, accept_rate, reject_rate, total_count}] }"
  - method: GET
    path: /api/v1/agent/feedback-stats
    description: "Python 端反馈分析"

acceptance_tests:
  - id: AT-001
    description: "反馈写入 t_ai_feedback_log"
    assertion: "POST 反馈后表中有记录"
  - id: AT-002
    description: "统计 API 返回正确接受率"
    assertion: "10 条反馈中 7 条 ACCEPT → 接受率 70%"
```

> **文档结束**

---

## §5 BDD 验收标准

### 场景 1：用户反馈写入数据库
**Given** AI 结果展示区有反馈按钮  
**When** 用户点击 👍（接受）  
**Then** 反馈数据写入 t_ai_feedback_log  
**And** 记录包含 task_id、feedback_type、user_id

### 场景 2：统计 API 正确聚合
**Given** 10 条反馈记录，其中 7 条 ACCEPT、3 条 REJECT  
**When** 调用统计 API  
**Then** 返回各 Agent 接受率 70%、拒绝率 30%

### 场景 3：仪表盘展示效果指标
**Given** 统计 API 数据已就绪  
**When** 访问仪表盘页面  
**Then** 展示各 Agent 接受率、拒绝率、总反馈数