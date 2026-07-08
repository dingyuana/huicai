# 慧财财务系统 — 综合设计文档（主文档）

> 版本：V4.3
> 日期：2026-07-08
> 范围：纯财务流程（不含销售/采购等业务流程）
> 版本历史：
> - V4.3 (2026-07-08): 核销前端增强 — Timeline 视图、穿透点击、FIFO 自动核销按钮
> - V4.2 (2026-07-08): 核销模块增强 — 全链路追溯API、余额快照、容差配置化
> - V4.1 (2026-07-07): 新增 §九 需求映射，关联 REQUIREMENTS_REGISTRY.md
> - V4.0 (2026-07-07): 模块设计文档拆分，主文档精简
> 设计文档索引：
> - [00-基础数据管理](design/00-system-design.md)
> - [01-总账管理](design/01-gl-design.md)
> - [02-应收应付管理](design/02-arap-design.md)
> - [03-现金与资金管理](design/03-cash-design.md)
> - [04-固定资产管理](design/04-asset-design.md)
> - [05-费用报销管理](design/05-expense-design.md)
> - [06-发票与税务管理](design/06-tax-design.md)
> - [07-工资薪酬管理](design/07-salary-design.md)（待建）
> - [08-预算管理](design/08-budget-design.md)
> - [09-财务报表与分析](design/09-report-design.md)
> - [10-AI智能体编排层](design/10-ai-orchestration-design.md)
> 关联架构文档：
> - [数据库与存储架构](architecture/DATABASE_ARCHITECTURE.md)
> - [编号关联体系](architecture/NUMBERING_ASSOCIATION.md)
> - [全局治理与可观测性](architecture/GOVERNANCE_OBSERVABILITY.md)

---

## 一、项目概述

### 1.1 项目定位

慧财财务系统是一款面向中小微企业的纯财务流程管理系统。对比传统财务软件的 8 大功能模块，当前系统覆盖 7/8，缺工资薪酬模块。

### 1.2 核心设计铁律

| 铁律 | 说明 |
|------|------|
| 人是唯一审核主体 | 所有审核/状态变更必须由人主动触发 |
| AI 输出 = 建议 | 永远不自动应用到财务数据 |
| 凭证不可变性 | 已审核/已过账凭证只能红冲修正 |
| 状态机严格转换 | 所有核心实体禁止非法状态跳转 |
| 审计追踪 | AOP + jsonb 快照 |
| 数据权限 | MyBatis 拦截器实现组织级隔离 |
| 金额精度 | BigDecimal + NUMERIC(18,2) |
| 核销架构 | 银行流水不直接核销，通过核销工作台 |
| 编号关联溯源 | 全链路双向追溯 |

### 1.3 架构原则

确定性业务（Java）与 AI 推理（Python）分离，两层通过 REST + RabbitMQ 通信，不共享事务边界。

---

## 二、技术架构

### 2.1 分层

| 层级 | 名称 | 核心职责 |
|------|------|---------|
| L1 | 基础设施层 | PostgreSQL 16/pgvector/Redis 7/MinIO/RabbitMQ |
| L2 | 后端业务底座 | Spring Boot 3.x + MyBatis-Plus + 状态机引擎 |
| L3 | AI 智能体编排层 | Python FastAPI + LangChain/LangGraph（横切辅助） |
| L4 | 前端交互层 | Vue 3 + Element Plus + ECharts |
| L5 | 全局治理层 | 安全/审计/可观测性 |

### 2.2 实际技术栈（不含未使用的技术）

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | 后端框架 |
| MyBatis-Plus | 3.5.x | ORM |
| PostgreSQL | 16 | OLTP + pgvector |
| Redis | 7 | 缓存/锁 |
| RabbitMQ | 3.x | AI 异步任务 |
| MinIO | latest | 对象存储 |
| FastAPI | 0.110.x | AI 服务 |
| LangChain/LangGraph | latest | AI 编排 |
| Vue 3 + Element Plus | 3.x | 前端 |
| ECharts | latest | 可视化 |

**排除的技术**：Seata、Nacos、Sentinel、ClickHouse、NebulaGraph、Kafka、K8s。

---

## 三、模块清单

| 序号 | 模块 | 代码包 | 设计文档 | 成熟度 | 备注 |
|------|------|--------|---------|--------|------|
| 00 | 基础数据管理 | `system` | [00-system-design.md](design/00-system-design.md) | 良好 | 科目/期间/权限/主数据 |
| 01 | 总账管理 | `finance` | [01-gl-design.md](design/01-gl-design.md) | 良好 | 凭证/账簿/结账/红冲/溯源 |
| 02 | 应收应付管理 | `arap` | [02-arap-design.md](design/02-arap-design.md) | 良好 | 核销工作台/预收预付/坏账/全链路追溯/容差配置 |
| 03 | 现金与资金管理 | `finance` | [03-cash-design.md](design/03-cash-design.md) | 良好 | 银行流水/对账/分类 |
| 04 | 固定资产管理 | `asset` | [04-asset-design.md](design/04-asset-design.md) | 及格 | 卡片/折旧/处置/盘点 |
| 05 | 费用报销管理 | `arap` | [05-expense-design.md](design/05-expense-design.md) | 较差 | 报销/审批/打款 |
| 06 | 发票与税务管理 | `tax` | [06-tax-design.md](design/06-tax-design.md) | 良好 | 进销项/税务申报 |
| 07 | 工资薪酬管理 | — | [07-salary-design.md](design/07-salary-design.md) | ❌待建 | 传统8模块唯一缺口 |
| 08 | 预算管理 | `budget` | [08-budget-design.md](design/08-budget-design.md) | 及格 | 编制/控制/调整 |
| 09 | 财务报表与分析 | `report` | [09-report-design.md](design/09-report-design.md) | 及格 | 三大报表/杜邦分析 |
| 10 | AI 智能体编排层 | `ai` | [10-ai-orchestration-design.md](design/10-ai-orchestration-design.md) | 初始 | 横切能力 |
| — | 存储管理 | `storage` | 附属模块 | 良好 | MinIO 附件 |

---

## 四、关键设计决策

### 4.1 红冲机制
已审核/已过账凭证不可修改，只能通过反向单据修正。支持前向级联（发票/单据同步 REVERSED）和反向级联（凭证红冲回写源单据）。

### 4.2 核销架构
银行流水 → B类路由 → 收款单/付款单(DRAFT) → 核销工作台（唯一入口）→ 核销单 → 凭证。t_receivable/t_payable 已删除。

### 4.3 编号关联体系
所有核心实体通过编号双向关联，`GET /api/v1/vouchers/trace?no={编号}` 支持 6 种实体类型全链路追溯。

### 4.4 AI 定位
AI 智能体层是横切辅助能力，不替代 Java 确定性模块。AI 输出必须人工确认后落库。

### 4.5 以票定账（人工审核驱动）
发票导入→PENDING_CONFIRM→人工审核→CONFIRMED→人工生单→BusinessDoc DRAFT→人工审核→凭证 DRAFT→人工审核过账。**每个环节都是人主动触发。**

### 4.6 预算控制
事前控制：报销提交时实时校验预算余额，预占预算，驳回时释放。

---

## 五、跨模块数据模型

核心跨模块表（完整表结构见各模块设计文档）：

| 表名 | 模块 | 功能 |
|------|------|------|
| t_voucher | 总账 | 凭证主表 |
| t_voucher_entry | 总账 | 凭证分录 |
| t_subject_balance | 总账 | 科目余额 |
| t_business_doc | 应收应付/费用 | 业务单据（替代 t_receivable/t_payable） |
| t_bank_statement | 现金 | 银行流水 |
| t_arap_settlement | 应收应付 | 核销单 |
| t_audit_log | 全系统 | 审计日志 |

编号关联字段命名规范：`xxx_id`（外键）+ `xxx_no`（编号冗余）。

---

## 六、全局状态机摘要

| 实体 | 状态机路径 |
|------|-----------|
| 凭证 | DRAFT→SUBMITTED→AUDITED→POSTED→CLOSED→REVERSED |
| 业务单据 | DRAFT→SUBMITTED→AUDITED→VOUCHERED→REVERSED |
| 销项发票 | PENDING_CONFIRM→CONFIRMED→VOUCHERED→FULLY_RECONCILED/PARTIALLY_RECONCILED→REVERSED |
| 进项发票 | PENDING_CONFIRM→CONFIRMED→VOUCHERED→REVERSED |
| 银行流水 | PENDING→CLASSIFIED→CONFIRMED→VOUCHER_GENERATED |
| 资产卡片 | IN_USE→IDLE→DISPOSED→SCRAPPED |
| 预算 | DRAFT→SUBMITTED→AUDITED/REJECTED→CLOSED |
| 核销单 | DRAFT→CONFIRMED→VOUCHERED→REVERSED |

完整状态机定义见各模块设计文档。

---

## 七、文档一致性规则

1. 主文档维护全局共识和跨模块索引，模块细节在各设计文档中。
2. 每次模块变更必须更新对应的设计文档版本号。
3. 每季度做一次主文档 vs 代码差异审计。
4. 术语统一：审核=audit，驳回=reject，红冲=reverse，过账=post，结账=close，凭证=voucher。
5. 新增/修改需求必须在 REQUIREMENTS_REGISTRY.md 中登记并分配 REQ 编号。

---

## 八、需求映射

见 [需求登记册](development/requirements/REQUIREMENTS_REGISTRY.md)。

### 8.1 模块 ↔ 需求编号对照

| 模块 | 需求编号范围 | 条数 |
|------|-------------|------|
| 基础数据管理 | REQ-2026-001 ~ 006 | 6 |
| 总账管理 | REQ-2026-007 ~ 011 | 5 |
| 应收应付管理 | REQ-2026-012 ~ 016 | 5 |
| 现金与资金管理 | REQ-2026-017 ~ 021 | 5 |
| 固定资产管理 | REQ-2026-022 ~ 025 | 4 |
| 发票与税务管理 | REQ-2026-026 ~ 030 | 5 |
| 预算管理 | REQ-2026-031 ~ 033 | 3 |
| 财务报表与分析 | REQ-2026-034 ~ 037 | 4 |
| AI 智能体层 | REQ-2026-038 ~ 045 | 8 |
| 存储管理 | REQ-2026-046 | 1 |
| 架构设计约束 | REQ-2026-047 ~ 054 | 8 |

---

> **主文档结束。各模块详细设计见 [docs/design/](design/) 目录。**