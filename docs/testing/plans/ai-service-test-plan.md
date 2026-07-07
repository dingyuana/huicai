# 慧财财务 — AI 服务测试方案

> **编号**：HUICAI-TST-009
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **修改内容**：初始创建——ai-service Python 端测试从零搭建方案
> **关联文档**：[10-ai-orchestration-design.md](../design/10-ai-orchestration-design.md)、[AI_ARCHITECTURE_EVOLUTION.md](../architecture/AI_ARCHITECTURE_EVOLUTION.md)

---

## 一、现状评估

| 维度 | 现状 | 目标 |
|------|------|------|
| 测试文件数 | 0个 | 10+ |
| 测试框架 | 无 | pytest + httpx |
| 覆盖范围 | 完全缺失 | 5个API端点 + 2个Agent + 核心模型 |
| CI集成 | 无 | GitHub Actions 自动执行 |

---

## 二、技术选型

| 工具 | 用途 |
|------|------|
| pytest | 测试框架 |
| httpx | FastAPI 异步测试客户端 |
| pytest-asyncio | 异步测试支持 |
| pytest-cov | 覆盖率统计 |
| unittest.mock | Mock pgvector/LLM 调用 |

---

## 三、测试分层

### L1: API端点测试（优先）

测试 `ai-service/app/api/` 下的 5 个端点模块。

```
ai-service/tests/
├── test_health.py          → 健康检查
├── test_match.py           → 科目映射API
├── test_anomaly.py         → 异常检测API
├── test_agent.py           → Agent路由API
├── test_embedding.py       → 嵌入API
├── test_ocr.py             → OCR API
└── conftest.py             → 公共fixture
```

**测试模式**：
```python
import pytest
from httpx import AsyncClient
from app.main import app

@pytest.mark.asyncio
async def test_health():
    async with AsyncClient(app=app, base_url="http://test") as client:
        resp = await client.get("/api/v1/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"

@pytest.mark.asyncio
async def test_subject_mapping():
    async with AsyncClient(app=app, base_url="http://test") as client:
        resp = await client.post("/api/v1/match/agent/subject-mapping", json={
            "item_name": "电脑",
            "amount": 5000
        })
        assert resp.status_code == 200
        data = resp.json()
        assert "best" in data or "candidates" in data
```

### L2: Agent 逻辑测试

测试 `ai-service/app/core/agent.py` 中的 RouterAgent、MatchAgent、AnomalyAgent。

```python
import pytest
from app.core.agent import RouterAgent, MatchAgent, AnomalyAgent

@pytest.mark.asyncio
async def test_router_match_intent():
    router = RouterAgent()
    router.register_agent(MatchAgent())
    result = await router.route("match", {"item_name": "电脑"})
    assert "best" in result or "candidates" in result

@pytest.mark.asyncio
async def test_router_unknown_intent():
    router = RouterAgent()
    result = await router.route("unknown", {})
    assert "error" in result

@pytest.mark.asyncio
async def test_anomaly_voucher_unbalanced():
    from app.api.anomaly import VoucherCheck, check_voucher
    req = VoucherCheck(voucher_id=1, total_debit=100, total_credit=99, entries=[])
    result = await check_voucher(req)
    assert result.risk_score > 0
    assert any(a.type == "UNBALANCED" for a in result.anomalies)
```

### L3: 异常检测逻辑测试

测试 `ai-service/app/api/anomaly.py` 中的 4 个维度检测函数。

```python
def test_item_mismatch_construction_to_food():
    from app.api.anomaly import _check_item_mismatch
    result = _check_item_mismatch("餐饮", "OUTPUT", [
        {"item_name": "水泥", "type": "INPUT"}
    ])
    assert result is not None
    assert result.severity == "HIGH"

def test_weekend_invoice():
    from app.api.anomaly import _check_time_anomaly
    result = _check_time_anomaly("2026-07-05")  # 周六
    assert result is not None
    assert "周末" in result.description

def test_amount_spike():
    from app.api.anomaly import _check_amount_volatility
    result = _check_amount_volatility(100000, [
        {"amount": 1000}, {"amount": 2000}, {"amount": 1500}
    ])
    assert result is not None
    assert "5 倍" in result.description
```

---

## 四、实施计划

| 阶段 | 内容 | 工时 | 优先级 |
|------|------|------|--------|
| 阶段1 | pytest环境 + conftest.py | 0.5d | P0 |
| 阶段2 | 6个API端点测试 | 1d | P0 |
| 阶段3 | Agent逻辑测试（Router/Match/Anomaly） | 0.5d | P0 |
| 阶段4 | 异常检测4维度单元测试 | 0.5d | P1 |
| 阶段5 | CI集成 | 0.5d | P1 |

---

## 五、CI配置

```yaml
# .github/workflows/ai-service-test.yml
name: AI Service Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: cd ai-service && pip install -r requirements.txt pytest pytest-asyncio httpx
      - run: cd ai-service && python -m pytest tests/ -v --cov=app
```

---

## 六、验收标准

| 指标 | 目标 |
|------|------|
| API端点测试覆盖 | 6/6 端点 |
| Agent测试覆盖 | 3/3 Agent（Router/Match/Anomaly） |
| 异常检测维度覆盖 | 4/4 维度 |
| 测试用例总数 | 30+ |
| CI执行时间 | < 15s |

---

> **文档结束。**