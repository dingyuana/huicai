# AGENTS.md — 慧财财务 (Huicai Financial Software)

## Current State

**Greenfield.** No code exists yet. This repository contains only the Chinese-language project specification:
- `基于Web财务软件的项目说明书.md` — the single source of truth; read this first before any implementation.

## Intended Architecture

```
前端 (Vue 3 + Element Plus + ECharts)
  ↓ HTTP
API Gateway (Spring Cloud Gateway, optional)
  ↓
业务应用层 (Spring Boot 3.x)
  ├── 系统管理 / 基础数据
  ├── 用户权限 (RBAC, button-level)
  ├── 单据管理 (发票/收付)
  ├── 财务核心 (凭证/账簿/结账)
  ├── 出纳管理 (日记账/对账)
  ├── 固定资产 (卡片/折旧/盘点)
  ├── 往来管理 (应收应付/核销)
  ├── 税务管理 (进销项/申报)
  ├── 预算管理 (编制/控制/分析)
  ├── 报表中心 (三大报表/自定义报表)
  ├── 财务分析 (指标/杜邦/趋势)
  └── AI 任务调度与数据服务中间件
  ↓                     ↓
RabbitMQ            PostgreSQL 16 + pgvector + pg_trgm
  ↓                  Redis 7 (缓存/锁)
AI 服务层            MinIO (文件存储)
(Python/FastAPI)
```

## Tech Stack (Intended, Not Yet Set Up)

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.x, Spring Security + JWT/OAuth2 |
| ORM | MyBatis-Plus or JPA (Hibernate) |
| Database | PostgreSQL 16, pgvector, pg_trgm |
| Cache/Distributed Lock | Redis 7 |
| Message Queue | RabbitMQ |
| File Storage | MinIO |
| AI Service | Python 3.11 + FastAPI, Hugging Face / PyTorch |
| Frontend | Vue 3 + Element Plus + ECharts |
| Reporting | JasperReports / EasyExcel |
| API Docs | Knife4j (Swagger) |
| Monitoring | Prometheus + Grafana + ELK |
| Deployment | Docker Compose / Kubernetes, Patroni for PG HA |

## Key Design Decisions from the Spec (Honor These)

- **Currency**: 人民币 (RMB) only. No multi-currency. All amounts `NUMERIC(18,2)`.
- **AI in the loop**: All AI outputs are *suggestions* requiring human confirmation. Never auto-apply AI results to financial data.
- **Immutability**: Audited/posted vouchers are immutable — only corrected via red-ink reversal (红冲), never edited.
- **Status machines**: All core entities (voucher, document) have strict state transitions. No illegal transitions.
- **Sequencing**: Voucher numbers use Redis atomic INCR, format `type+period+serial`.
- **Audit trail**: All critical writes logged via AOP + `jsonb` snapshots.
- **Data permissions**: MyBatis interceptor injects SQL conditions for org-level data isolation.
- **Period close**: Strict sequencing — depreciation → payroll → tax → cost carry → P&L carry → close.

## Existing Files

- `基于Web财务软件的项目说明书.md` — 469-line Chinese spec covering architecture, modules, DB schema, AI integration, deployment. The only file in the repo.

## Naming Conventions (From Spec)

- Tables prefixed `t_` (e.g., `t_voucher`, `t_subject`, `t_business_doc`)
- Primary keys: `id BIGINT GENERATED ALWAYS AS IDENTITY`
- Amount columns: `NUMERIC(18,2)`
- Extensible data: `JSONB` columns for OCR results, AI payloads, auxiliary accounting
- AI vectors: `VECTOR(768)` with IVFFlat index
- Table/column names in **snake_case** (PostgreSQL convention)

## What's Not Yet Set Up

No build system, no dependency management (Maven/Gradle), no frontend scaffolding, no Docker config, no CI. All of this needs to be created from scratch following the spec.