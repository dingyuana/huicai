# P40 SPEC — AI 服务骨架增强（Phase 1 Task 1）

> **编号**：HUICAI-SPC-040
> **优先级**：已完成
> 依据：AI 架构演进计划 Phase 1 - 任务 1
> 目标：完善现有 ai-service 骨架，添加 FastAPI 健康检查、Swagger 文档、CORS 配置，Docker 化就绪

---

> **关联需求**: REQ-2026-038, REQ-2026-043

## 1. 输入契约
→ 见本文 [## 2. API 契约 — 健康检查 / Swagger / 统一响应格式](#2-api-契约)

## 2. 输出契约
→ 见本文 [## 4. 验收标准 — Docker build / 健康检查 / Swagger / CORS 验收项](#4-验收标准)

## 3. 状态流转
→ 见本文 [## 1.2 具体改动 — 依赖注入与模块初始化顺序](#12-具体改动)

## 4. 异常处理
→ 见本文 [## 6. 风险与应对 — 降级策略与缺失依赖处理](#6-风险与应对)

## 0. 现状摸底

### 现有能力（ai-service）

| 能力 | 文件 | 状态 |
|------|------|------|
| FastAPI 骨架 | `ai-service/app/main.py` | ✅ 已有 |
| 健康检查 API | `ai-service/app/api/health.py` | ✅ 已有 |
| OCR API | `ai-service/app/api/ocr.py` | ✅ 已有 |
| embedding API | `ai-service/app/api/embedding.py` | ✅ 已有 |
| Dockerfile | `ai-service/Dockerfile` | ✅ 已有 |
| requirements.txt | `ai-service/requirements.txt` | ✅ 已有 |
| docker-compose 集成 | `docker-compose.yml` | ✅ 已有 |

### 缺失能力

| 缺失项 | 说明 |
|--------|------|
| Swagger 文档配置 | 需要确保 `/docs` 和 `/openapi.json` 可访问 |
| CORS 配置 | 需要允许前端 3000/3001 端口访问 |
| 统一的响应格式 | 需要定义标准的 Response 结构 |
| 结构化日志 | 需要配置 logger |

---

## 1. 方案

### 1.1 保持现有结构不变

保留所有现有文件，只做增强：

```
ai-service/
├── app/
│   ├── api/
│   │   ├── health.py          (保留)
│   │   ├── ocr.py             (保留)
│   │   ├── embedding.py       (保留)
│   │   └── __init__.py        (保留)
│   ├── core/
│   │   ├── config.py          (新增)
│   │   ├── logging.py         (新增)
│   │   └── __init__.py        (保留)
│   ├── workers/
│   │   ├── task_consumer.py   (保留)
│   │   └── __init__.py        (保留)
│   ├── __init__.py            (保留)
│   └── main.py                (修改)
├── Dockerfile                 (保留)
├── requirements.txt           (保留)
├── POSTMAN.md                 (保留)
└── README.md                  (保留)
```

### 1.2 具体改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 新增配置模块 | `app/core/config.py` | 使用 Pydantic Settings 管理环境变量 |
| 新增日志模块 | `app/core/logging.py` | 配置结构化日志 |
| 修改 main.py | `app/main.py` | 加 CORS、Swagger 配置、统一异常处理 |
| 更新 requirements.txt | `requirements.txt` | 确保依赖正确（pydantic-settings 等） |

---

## 2. API 契约

### 2.1 健康检查

```
GET /ai/v1/health
Response:
{
  "status": "ok",
  "service": "huicai-ai-service",
  "version": "1.0",
  "timestamp": "2026-07-06T15:00:00Z"
}
```

### 2.2 Swagger 文档

```
GET /docs      → Swagger UI
GET /openapi.json → OpenAPI Schema
GET /redoc     → ReDoc UI (可选)
```

---

## 3. 数据库（无需改动）

任务 1 不涉及数据库变更，后续任务 2 再添加 pgvector。

---

## 4. 验收标准

| 项 | 验收标准 |
|----|----------|
| Docker build | `docker build -t huicai-ai-service ./ai-service` 成功 |
| docker-compose up | `docker-compose up -d ai-service` 启动成功 |
| 健康检查 | `curl http://localhost:8000/ai/v1/health` 返回 `{"status":"ok"}` |
| Swagger | `http://localhost:8000/docs` 可访问 |
| CORS | 从 `http://localhost:3001` 可以正常调用 API |
| 日志 | 结构化日志正常输出 |

---

## 5. 任务分解

| 子任务 | 工时 |
|--------|------|
| 新增 config.py | 0.1 人日 |
| 新增 logging.py | 0.1 人日 |
| 修改 main.py | 0.2 人日 |
| 测试验证 | 0.1 人日 |
| **总计** | **0.5 人日** |

---

## 6. 风险与应对

| 风险 | 应对 |
|------|------|
| 现有代码有问题 | 保留现有代码逻辑不变，只做增强 |
| requirements.txt 依赖缺失 | 测试时检查并补充 |

---

## === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

# --- Entity metadata ---
entity: AiService
module: ai
table: n/a

# --- API endpoints contract ---
endpoints:
  - id: E-001
    method: GET
    path: /ai/v1/health
    description: "服务健康检查"
    request: n/a
    response: HealthResponse
    test_ref: test_health_check_positive

  - id: E-002
    method: GET
    path: /docs
    description: "Swagger UI 文档"
    request: n/a
    response: SwaggerHTML
    test_ref: test_swagger_ui_accessible

  - id: E-003
    method: GET
    path: /openapi.json
    description: "OpenAPI Schema"
    request: n/a
    response: OpenAPISchema
    test_ref: test_openapi_schema_accessible

# --- Acceptance Tests ---
acceptance_tests:
  - id: AT-001
    description: "Docker build 成功"
    method: test_docker_build_success
    assertion: "docker build returns exit code 0"
    status: pending

  - id: AT-002
    description: "健康检查返回 ok"
    method: test_health_endpoint_ok
    assertion: "response.status == 'ok'"
    status: pending

  - id: AT-003
    description: "Swagger UI 可访问"
    method: test_swagger_ui_accessible
    assertion: "HTTP 200 response from /docs"
    status: pending

  - id: AT-004
    description: "CORS 配置正确"
    method: test_cors_config_accepts_frontend
    assertion: "Access-Control-Allow-Origin header includes http://localhost:3001"
    status: pending

# --- Files to create/modify ---
files:
  - path: ai-service/app/core/config.py
    action: create
    description: "Pydantic Settings 配置模块"

  - path: ai-service/app/core/logging.py
    action: create
    description: "结构化日志配置"

  - path: ai-service/app/main.py
    action: modify
    description: "加 CORS、Swagger、异常处理"

  - path: ai-service/requirements.txt
    action: modify
    description: "确保依赖正确"

# --- Constraints ---
constraints:
  - id: C-01
    type: compatibility
    rule: "保持现有 API 端点路径不变"
    enforcement: "不修改现有 api/health.py、api/ocr.py、api/embedding.py 路径"

  - id: C-02
    type: compatibility
    rule: "保持现有 docker-compose.yml 配置不变"
    enforcement: "不修改 docker-compose.yml 中的 ai-service 配置"

---

## 7. BDD 验收标准

### 场景 1：Docker 构建成功后服务健康检查正常
**Given** ai-service 的 Dockerfile 和 requirements.txt 已就绪
**When** 执行 `docker build -t huicai-ai-service ./ai-service` 构建成功，然后执行 `docker-compose up -d ai-service` 启动服务
**Then** `curl http://localhost:8000/ai/v1/health` 返回 `{"status":"ok"}`，HTTP 状态码为 200

### 场景 2：Swagger 文档页面可访问
**Given** ai-service 已启动运行
**When** 通过浏览器访问 `http://localhost:8000/docs`
**Then** 页面返回 Swagger UI 界面，且 `http://localhost:8000/openapi.json` 返回有效的 OpenAPI Schema

### 场景 3：CORS 配置允许前端跨域访问
**Given** ai-service 已启动，前端运行在 `http://localhost:3001`
**When** 从前端浏览器发起跨域请求到 ai-service API
**Then** 响应头中包含 `Access-Control-Allow-Origin: http://localhost:3001`，且预检请求（OPTIONS）正常通过
