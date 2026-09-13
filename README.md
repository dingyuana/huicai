# 慧财财务 (Huicai Financial)

基于 Web 的企业财务核算系统，面向中小企业（SME）提供总账核算、应收应付、现金管理、固定资产、费用报销、发票税务、预算与财务报表等全流程财务管理能力，并内置 AI 辅助能力（异常检测、智能匹配、OCR）。

> AI 输出仅为建议，所有财务数据变更必须经人工确认。

## 项目状态

| 维度 | 数据 |
|------|------|
| 后端代码 | 461+ Java 文件 |
| 测试用例 | 1776 个 @Test 方法 / 210 个测试类（0 Failures）|
| API 端点 | 510+ 个 |
| 数据库 | PostgreSQL 16 / V1 baseline |
| 业务单据类型 | 11 种（RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE/TRANSFER/SALARY/PRE_RECEIVE/PRE_PAY）|
| 阶段进度 | P0-P2 ✅ 100%（基础体系 + 缺陷修复 + AI 辅助）；P3 ⏳ 0%（经营分析/预算预测/风控/工资薪酬）|

## 技术栈

- 后端：Spring Boot 3.x + Spring Security (JWT) + MyBatis-Plus
- 前端：Vue 3 + Element Plus + ECharts
- 数据库：PostgreSQL 16（pgvector / pg_trgm）
- 中间件：Redis 7 + RabbitMQ + MinIO
- AI 服务：Python 3.11 + FastAPI + Hugging Face（5 端点：health/anomaly/embedding/match/ocr）
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
