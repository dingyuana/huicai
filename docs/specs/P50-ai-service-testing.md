# P50 SPEC — AI 服务测试补齐

> **编号**：HUICAI-SPC-050
> **版本**：V1.0 | **修改日期**：2026-07-09
> **修改人**：Hermes
> **依据**：P0-P3 路线图 V4.0 §P3-12
> **范围**：Python AI 服务从 0 个测试到 10+ 个测试

> **关联需求**: REQ-2026-062

## 1. 输入契约
→ 见本文 §1.1 测试架构（test_*.py 测试文件定义）、§1.2 测试类型

## 2. 输出契约
→ 见本文 §2 验收标准（AT-001 至 AT-008）、§4 YAML 契约 acceptance_tests

## 3. 状态流转
→ 见本文 §1.1 测试架构层级（单元测试 → API 测试 → CI 集成）

## 4. 异常处理
→ 见本文 §1.3 Mock 策略（mock 外部依赖、不启动真实 PG/MQ/NVIDIA）

---

## §0 当前状态

| 维度 | 状态 | 说明 |
|------|------|------|
| `ai-service/` 测试 | ❌ 0 个 | 零测试，无 pytest 配置 |
| Python FastAPI 端点 | ✅ 5 个 | health/match/anomaly/embedding/ocr |
| Agent 核心逻辑 | ✅ 已实现 | MatchAgent 三阶段管道、AnomalyAgent 四维度检测 |
| 外部依赖 | ✅ 复杂 | pgvector、RabbitMQ、NVIDIA API |

---

## §1 测试方案

### 1.1 测试架构

```
ai-service/tests/
├── conftest.py              # pytest 配置 + mock 夹具
├── test_health.py           # 健康检查端点
├── test_match.py            # 科目映射核心逻辑（三阶段管道）
├── test_anomaly.py          # 异常检测核心逻辑（四维度）
├── test_embedding.py        # 语义检索端点
├── test_agent_route.py      # Agent 路由
├── test_agents.py           # 各 Agent 独立测试
└── test_agent.py            # 统一 Agent 路由端到端
```

### 1.2 测试类型

| 测试类型 | 数量 | 说明 |
|---------|------|------|
| 单元测试 | 5+ | 各 Agent 核心逻辑（不含外部依赖） |
| API 测试 | 5+ | 各端点 HTTP 响应测试 |
| **合计** | **10+** | |

### 1.3 Mock 策略

| 外部依赖 | Mock 方案 |
|---------|----------|
| pgvector | pytest-httpx mock HTTP 请求 |
| RabbitMQ | mock 消息发布，不启动真实 MQ |
| NVIDIA API | mock HTTP 响应 |
| 数据库 | mock 连接，不启动真实 PG |

### 1.4 测试工具

| 工具 | 用途 |
|------|------|
| pytest | 测试框架 |
| pytest-httpx | HTTP 请求 mock |
| pytest-asyncio | 异步测试支持 |
| httpx | TestClient 模拟 FastAPI 请求 |

---

## §2 验收标准

| 编号 | 场景 | 前置条件 | 操作步骤 | 预期结果 |
|------|------|---------|---------|---------|
| AT-001 | 单元测试数量达标 | pytest 已安装，ai-service/tests/ 目录存在 | 运行 `pytest --collect-only | grep 'test_' \| wc -l` | 收集到至少 5 个单元测试用例（test_ 前缀） |
| AT-002 | API 测试数量达标 | pytest 已安装，API 测试文件存在 | 运行 `pytest --collect-only | grep 'test_' \| wc -l` | 收集到至少 5 个 API 端点测试用例 |
| AT-003 | 全部测试通过 | 所有测试已编写，mock 环境就绪 | 运行 `pytest ai-service/tests/ -v` | exit code = 0，所有测试绿色通过 |
| AT-004 | 不使用真实外部依赖 | 测试代码已提交 | 审查所有 `tests/` 目录下的测试代码 | 无真实 pgvector / RabbitMQ / NVIDIA API 连接，全部使用 mock |
| AT-005 | MatchAgent 三阶段管道覆盖率 | MatchAgent 实现完成，test_match.py 存在 | 查看 test_match.py 测试内容 | 覆盖清洗(cleaning)→检索(retrieval)→重排(reranking) 三阶段 |
| AT-006 | AnomalyAgent 四维度检测覆盖率 | AnomalyAgent 实现完成，test_anomaly.py 存在 | 查看 test_anomaly.py 测试内容 | 覆盖金额异常 / 科目异常 / 频次异常 / 对手方异常四维度 |
| AT-007 | 健康检查端点响应正常 | ai-service 测试环境已启动 | 发送 GET /health 请求 | 返回 HTTP 200，包含服务状态信息 |
| AT-008 | CI 集成 pytest 命令 | CI 配置文件（.gitlab-ci.yml 等）已更新 | 查看 CI 配置中的 test job | pytest ai-service/tests/ 命令已加入 CI 流程 |

---

## §3 排期

| 子任务 | 工时 | 依赖 |
|--------|------|------|
| test 目录 + conftest.py | 0.5d | 无 |
| 单元测试（Agent 核心逻辑） | 1.0d | 无 |
| API 测试（端点） | 1.0d | 无 |
| CI 集成（pytest 命令） | 0.5d | 以上完成 |

**总工期：3 人日**

---

## §4 YAML 契约

```yaml
contract_version: "1.0"
entity: null
module: ai

acceptance_tests:
  - id: AT-001
    description: "单元测试 ≥ 5 个"
    assertion: "pytest --collect-only | grep 'test_' | wc -l ≥ 5"
  - id: AT-002
    description: "API 测试 ≥ 5 个"
    assertion: "pytest --collect-only | grep 'test_api' | wc -l ≥ 5"
  - id: AT-003
    description: "全部测试通过"
    assertion: "pytest exit code = 0"
  - id: AT-004
    description: "不使用真实外部依赖"
    assertion: "测试代码中无真实数据库/MQ/NVIDIA 连接"
```

> **文档结束**

---

## §5 BDD 验收标准

### 场景 1：单元测试数量达标
**Given** pytest 已安装，ai-service/tests/ 目录存在  
**When** 运行 `pytest --collect-only`  
**Then** 收集到至少 5 个单元测试用例（test_ 前缀）

### 场景 2：全部测试通过
**Given** 所有测试已编写，mock 环境就绪  
**When** 运行 `pytest ai-service/tests/ -v`  
**Then** exit code = 0  
**And** 所有测试绿色通过

### 场景 3：MockAgent 三阶段管道被覆盖
**Given** test_match.py 存在  
**When** 审查测试代码  
**Then** 覆盖清洗→检索→重排三阶段  
**And** 不使用真实外部依赖