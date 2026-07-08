# 慧财财务系统 — 文档注册表

> 版本：V1.0 | 日期：2026-07-07
> 维护人：Hermes
> 说明：本文档是所有项目文档的权威索引，每份文档分配唯一编号。
> 编号格式：`HUICAI-{分类}-{序号}`（MAIN=主文档, DES=设计, ARC=架构, SPC=规格, DEV=开发, TST=测试, REF=参考）

---

## 一、主文档

| 编号 | 文件名 | 版本 | 最后修改 | 修改人 | 说明 |
|------|--------|------|---------|--------|------|
| HUICAI-MAIN-001 | DESIGN.md | V4.3 | 2026-07-08 | Hermes | 综合设计主文档，含模块索引和全局决策 |

## 二、设计文档（design/）

| 编号 | 文件名 | 版本 | 最后修改 | 修改人 | 说明 |
|------|--------|------|---------|--------|------|
| HUICAI-DES-001 | 00-system-design.md | V1.0 | 2026-07-07 | Hermes | 基础数据管理模块设计 |
| HUICAI-DES-002 | 01-gl-design.md | V1.0 | 2026-07-07 | Hermes | 总账管理模块设计 |
| HUICAI-DES-003 | 02-arap-design.md | V1.0 | 2026-07-07 | Hermes | 应收应付管理模块设计 |
| HUICAI-DES-004 | 03-cash-design.md | V1.0 | 2026-07-07 | Hermes | 现金与资金管理模块设计 |
| HUICAI-DES-005 | 04-asset-design.md | V1.0 | 2026-07-07 | Hermes | 固定资产管理模块设计 |
| HUICAI-DES-006 | 05-expense-design.md | V1.0 | 2026-07-07 | Hermes | 费用报销管理模块设计 |
| HUICAI-DES-007 | 06-tax-design.md | V1.0 | 2026-07-07 | Hermes | 发票与税务管理模块设计 |
| HUICAI-DES-008 | 07-salary-design.md | V1.0 | 2026-07-07 | Hermes | 工资薪酬管理（待建蓝图） |
| HUICAI-DES-009 | 08-budget-design.md | V1.0 | 2026-07-07 | Hermes | 预算管理模块设计 |
| HUICAI-DES-010 | 09-report-design.md | V1.0 | 2026-07-07 | Hermes | 财务报表与分析模块设计 |
| HUICAI-DES-011 | 10-ai-orchestration-design.md | V1.0 | 2026-07-07 | Hermes | AI 智能体编排层设计 |

## 三、架构文档（architecture/）

| 编号 | 文件名 | 版本 | 最后修改 | 修改人 | 说明 |
|------|--------|------|---------|--------|------|
| HUICAI-ARC-001 | AI_ARCHITECTURE_EVOLUTION.md | V1.0 | 2026-07-07 | Hermes | AI 架构演进设计 |
| HUICAI-ARC-002 | NUMBERING_ASSOCIATION.md | v1.8 | 2026-07-07 | Hermes | 编号关联体系 |
| HUICAI-ARC-003 | STATE_TRANSITION_RED_LINE.md | v1.1 | 2026-07-07 | Hermes | 状态机红线规范 |
| HUICAI-ARC-004 | layer-architecture.md | V1.0 | 2026-07-07 | Hermes | 强制分层架构规范 |
| HUICAI-ARC-005 | linkage-map.md | V1.0 | 2026-07-07 | Hermes | 核心链路映射 |

## 四、规格文档（specs/）

规格文档已使用 P 系列编号（P0-P41），保持现有编号不变。共 38 份 SPEC 文件。

| 编号范围 | 说明 |
|----------|------|
| P0-P42 | 功能规格文档，详见 specs/ 目录 |
| P42 | 核销前端增强 — Timeline 视图、穿透点击、FIFO 自动核销 |

## 五、开发文档（development/）

| 编号 | 路径 | 版本 | 最后修改 | 修改人 | 说明 |
|------|------|------|---------|--------|------|
| HUICAI-DEV-001 | plans/慧财财务总体开发计划_2026-06-15.md | V1.0 | 2026-06-15 | — | 总体开发计划 |
| HUICAI-DEV-002 | plans/慧财财务系统 AI 辅助能力开发计划.md | V2.0 | 2026-07-06 | Hermes | AI 辅助能力开发计划 |
| HUICAI-DEV-003 | plans/Sprint-数据导入模块修复.md | V1.0 | 2026-06-13 | — | Sprint 计划 |
| HUICAI-DEV-004 | plans/开发计划书.md | — | — | — | 初始开发计划书 |
| HUICAI-DEV-005~027 | tasks/*.md | — | — | — | 历史任务书/验证报告（23份） |
| HUICAI-DEV-028 | workflows/THREE-PHASE-LOOP.md | — | — | — | 三步闭环工作流 |
| HUICAI-DEV-029 | workflows/AI-EVOLUTION-CLOSED-LOOP.md | — | — | — | AI 演进闭环工作流 |
| HUICAI-DEV-030 | standards/差距检测与设计-实施同步规范.md | — | — | — | 差距检测规范 |
| HUICAI-DEV-031 | guides/银行流水导入后操作流程.md | — | — | — | 银行流水操作指南 |
| HUICAI-DEV-032 | lessons/001-test-false-positive.md | — | — | — | 测试假阳性教训 |
| **HUICAI-DEV-033** | **lessons/002-period-constraint-testing-gap.md** | **V1.0** | **2026-07-07** | **Hermes** | **period_code NOT NULL 约束违反 — 测试盲区复盘** |
| HUICAI-DEV-033 | coding-conduct.md | — | — | — | 编码规范 |
| HUICAI-DEV-034~040 | requirements/*.md | — | — | — | 需求分析（7份） |
| **HUICAI-DEV-035** | **standards/文档管理规范.md** | **V1.0** | **2026-07-07** | **Hermes** | **文档编号/头部/生命周期规范** |
|| HUICAI-DEV-036 | plans/P0-P3-roadmap.md | V4.0 | 2026-07-07 | Hermes | P0-P3开发路线图（P0-P2已完成，P3远期） |
|| HUICAI-DEV-037 | requirements/REQUIREMENTS_REGISTRY.md | V1.1 | 2026-07-08 | Hermes | 需求登记册（REQ-2026-001~057，57 条需求） |
|| HUICAI-DEV-038 | lessons/002-period-constraint-testing-gap.md | V1.0 | 2026-07-07 | Hermes | 期间约束测试盲区复盘 |
|| HUICAI-DEV-039 | lessons/003-cross-module-integration-gap.md | — | — | — | 跨模块集成空白复盘 |
|| HUICAI-DEV-040 | lessons/004-api-contract-gap-after-architecture-migration.md | — | — | — | 架构迁移API契约空白复盘 |

## 六、测试文档（testing/）

| 编号 | 文件名 | 版本 | 最后修改 | 修改人 | 说明 |
|------|--------|------|---------|--------|------|
| HUICAI-TST-001 | NUMBERING_ASSOCIATION_TEST_PLAN.md | — | — | — | 编号关联测试计划 |
| HUICAI-TST-002 | plans/full-stack-test-methodology.md | — | — | — | 全链路测试方法论 |
| HUICAI-TST-003 | plans/state-machine-test-checklist.md | — | — | — | 状态机检查清单 |
| HUICAI-TST-004 | standards/TESTING_QUICKSTART.md | — | — | — | 测试快速入门 |
| HUICAI-TST-005 | standards/TESTING_STANDARD.md | — | — | — | 测试标准规范 |
| HUICAI-TST-006 | standards/mapper-testcontainers-plan.md | — | — | — | Mapper 真实 DB 测试方案 |
| HUICAI-TST-007 | test-methodology.md | V1.0 | 2026-07-07 | Hermes | 测试方法总结与检查清单 |
| HUICAI-TST-008 | plans/frontend-test-plan.md | V1.0 | 2026-07-07 | Hermes | 前端测试方案（Vitest+Vue Test Utils） |
| HUICAI-TST-009 | plans/ai-service-test-plan.md | V1.0 | 2026-07-07 | Hermes | AI服务测试方案（pytest+httpx） |
| HUICAI-TST-010 | test-coverage-matrix.md | V1.0 | 2026-07-07 | Hermes | 测试覆盖矩阵（标识空白区域） |
| HUICAI-TST-011 | test-prevention-mechanism.md | V1.0 | 2026-07-07 | Hermes | 防错机制（影响面清单+测试门禁） |
| HUICAI-TST-012 | entity-db-audit-report.md | V1.0 | 2026-07-07 | Hermes | Entity-DB 审计报告 |

## 七、事故报告（incidents/）

| 编号 | 文件名 | 日期 | 说明 |
|------|--------|------|------|
| HUICAI-INC-001 | sales-invoice-audited-by-column-error.md | 2026-07-07 | 销项发票auditedBy列缺失故障报告 |

## 八、参考文档（reference/）

| 编号 | 文件名 | 说明 |
|------|--------|------|
| HUICAI-REF-001 | 2026-06-25-综合审计报告.md | 专家审计报告 |
| HUICAI-REF-002 | MCP协议_从买彩票到买定离手_AI工具协议开发指南.md | MCP 技术笔记 |
| HUICAI-REF-003 | MCP实战_Git操作前后对比.md | MCP 实操对比 |
| HUICAI-REF-004 | 项目说明书影响分析_银行流水导入分类.md | 需求影响分析 |

## 八、文档更新规范

1. **新增文档**：分配 HUICAI-{CAT}-{NEXT} 编号，在注册表登记
2. **修改文档**：更新文件头部的版本号 + 修改日期，注册表对应行更新
3. **废弃文档**：移入 archive/，注册表标记为「已归档」
4. **编号不可复用**：已分配编号即使文档废弃也不可重新使用

---

> **文档结束。所有项目文档的编号和状态以此注册表为准。**