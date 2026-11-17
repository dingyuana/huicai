# 慧财财务 (Huicai Financial)

基于 Web 的企业财务核算系统，面向中小企业（SME）与财务代理公司提供总账核算、应收应付、现金管理、固定资产、费用报销、发票税务、预算与财务报表等财务管理能力。当前完善重点是代理记账业务；AI 功能不属于本阶段交付范围。

> 🔴 **关于 AI 能力的如实说明**：当前 AI 层**不包含模型推理**。
> `ai-service/` 的 5 个端点中，`embedding` 用 `md5` 哈希生成伪向量（`SentenceTransformer` 调用在源码中**已被注释**），
> `anomaly` / `match` / `ocr` 为规则与桩实现。
> 换言之，现阶段是**「AI 接入位点 + 人工确认工作流」**，而非「内置推理能力」。
> 按项目铁律，AI 输出仅为建议，所有财务数据变更必须经人工确认——该约束**当前是有效的**，因为根本没有自动写入路径。

## 项目状态

| 维度 | 数据 |
|------|------|
| 后端代码 | 637 个 Java 主代码文件、346 个 Java 测试源码文件 |
| 测试用例 | L1 `cd backend && mvn test`：3856 通过 / 0 Failures / 0 Errors / 10 Skipped；P162 L2 PostgreSQL 租户隔离：1/1；前端：39 个测试文件、374 通过 |
| API 端点 | 510+ 个后端端点 |
| 数据库 | PostgreSQL 16 / **119 个 migration，最新 V203** |
| 业务单据类型 | 11 种（RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE/TRANSFER/SALARY/PRE_RECEIVE/PRE_PAY）|
| 阶段进度 | 代理记账主线持续完善；P162 已补齐批量导入、审核、结账工作台。基座与内控仍需持续验证；AI 功能暂不纳入本阶段 |

> ⚠️ **不写「100%」的理由**：功能模块可用 ≠ 系统可交付。内控与基座（租户隔离、权限、审计）在
> 2026-10 期间仍有多轮修复，且**已知遗留项**（如「AI 模块 RLS 读路径缺陷根因未定位」）尚未收口。
> 详见 `docs/development/requirements/REQUIREMENTS_REGISTRY.md` 的状态码列与 `AGENTS.md` §0。

## 技术栈

- 后端：Spring Boot 3.x + Spring Security (JWT) + MyBatis-Plus
- 前端：Vue 3 + Element Plus + ECharts
- 数据库：PostgreSQL 16（pgvector / pg_trgm）
- 中间件：Redis 7 + RabbitMQ + MinIO
- AI 服务：Python 3.11 + FastAPI（5 端点：health/anomaly/embedding/match/ocr）—— **规则与桩实现，无模型推理**
- 部署：Docker Compose

## 快速开始

```bash
# 启动基础设施（PostgreSQL/Redis/RabbitMQ/MinIO）
docker compose up -d

# 启动后端
cd backend && mvn spring-boot:run

# 启动前端
cd frontend && npm run dev
```

## 项目结构

```
backend/      — Spring Boot 后端
  └─ com.huicai.base      — 基础模块（凭证/科目/报表/存储/系统/AI 接入）
  └─ com.huicai.sme       — 业务模块（应收应付/固定资产/预算/现金/发票税务）
frontend/     — Vue 3 前端
ai-service/   — Python FastAPI AI 微服务
docs/         — 项目文档（PRD/SPEC/设计/测试/开发规范）
docker/       — Docker 配置
e2e/          — 端到端测试
scripts/      — 脚本工具
```

## 核心模块

基础数据、总账、应收应付、现金管理、固定资产、费用报销、发票税务、预算、财务报表、存储管理

## 常用命令

| 操作 | 命令 |
|------|------|
| 运行全部测试 | `cd backend && mvn test` |
| 编译检查 | `cd backend && mvn compile` |
| 查看 Flyway 状态 | `cd backend && mvn flyway:info` |

## 核心设计原则

- **人是唯一审核主体**：所有审核/结转/状态变更由人主动触发，系统不自动调整业务状态
- **AI 输出 = 建议**：必须人工确认后才能写入财务数据
- **凭证不可变**：已审核/已过账凭证只能红冲修正
- **审计追踪**：关键写操作通过 AOP + jsonb 快照记录
- **数据权限**：组织级数据隔离自动注入

完整规范见 [AGENTS.md](AGENTS.md) 与 [docs/CORE-项目说明.md](docs/CORE-项目说明.md)。
