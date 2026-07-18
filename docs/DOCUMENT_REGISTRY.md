# 慧财财务系统 — 文档注册表

> 版本：V1.1 | 日期：2026-07-18
> 维护人：Hermes
> 说明：本文档是所有项目文档的权威索引，每份文档分配唯一编号。
> 编号格式：`HUICAI-{分类}-{序号}`（MAIN=主文档, DES=设计, ARC=架构, SPC=规格, DEV=开发, TST=测试, REF=参考）

---

## 一、主文档

| 编号 | 文件名 | 版本 | 最后修改 | 修改人 | 说明 |
|------|--------|------|---------|--------|------|
| HUICAI-MAIN-001 | DESIGN.md | V4.4 | 2026-07-08 | Hermes | 综合设计主文档，含模块索引和全局决策 |

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

规格文档包含两套编号体系（共 66 份）：

**P系列（P0~P53）**：共 49 份功能规格文档，按开发顺序增量编号，HUICAI-SPC 编号头部 100% 覆盖。多数已有 YAML 契约块，部分已有 BDD 验收标准。

**S系列（S-00~S-26）**：共 14 份系统级规格文档，按业务域分层编号。HUICAI-SPC-100~113。全部已含 SDD 四段结构 + BDD + YAML 契约，4/4 完全合规。

两套编号体系不强制迁移，新 SPEC 用 S 编号，旧 P 编号自然迭代替换。详见 development/workflows/s-p-spec-mapping.md。

| 编号范围 | 编号 | 说明 |
|----------|------|------|
| S-01~S-26 | HUICAI-SPC-100~113 | 系统级规范文档，详见 specs/ 目录（8份在册，6份已归档，全部 4/4 合规） |
| P0~P53 | HUICAI-SPC-000~053 | 功能规格文档，详见 specs/ 目录（49份） |
| SPEC-CONTRACT-SCHEMA.md | HUICAI-SPC-099 | S-00 全局契约规范 v2.0（4/4 合规） |

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
||| HUICAI-DEV-040 | lessons/004-api-contract-gap-after-architecture-migration.md | — | — | — | 架构迁移API契约空白复盘 |
||| HUICAI-DEV-041 | workflows/s-p-spec-mapping.md | V1.1 | 2026-07-18 | Hermes | S编号↔P编号映射关系（含代码实现度评估） |

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

## 九、归档文档（archive/over-engineered/）

> 以下文档因设计过容（使用排除技术栈/复杂度远超当前版本/面向远期场景），已移入归档。恢复需重新评审。

| 原编号 | 原路径 | 归档原因 | 归档日期 |
|--------|--------|---------|---------|
| HUICAI-PH2-001 | phase2/项目说明报告.md | 含微服务/Milvus/Drools等排除技术 | 2026-07-19 |
| HUICAI-PH2-002 | phase2/需求方案（PRD）.md | A2A多智能体、行业方案、CRM全生命周期等超当前版本 | 2026-07-19 |
| HUICAI-PH2-003 | phase2/技术架构.md | 含Spring Cloud Gateway/Milvus/Drools/K8s/Nacos | 2026-07-19 |
| HUICAI-PH2-004 | phase2/Spec 规范报告.md | 26 Spec七层架构含极高复杂度远期模块 | 2026-07-19 |
| HUICAI-SPC-104 | S-09-NL-经营分析Agent与NL2SQL.md | P3+ AI功能，当前不急需 | 2026-07-19 |
| HUICAI-SPC-106 | S-15-农民工工资专户与代发.md | 太垂直小众，S-14子集 | 2026-07-19 |
| HUICAI-SPC-107 | S-16b-资金计划与票据管理.md | 远超当前资金管理需求 | 2026-07-19 |
| HUICAI-SPC-111 | S-24-合并报表与内部交易抵消.md | 极高复杂度，集团客户场景 | 2026-07-19 |
| HUICAI-SPC-112 | S-25-财务共享任务调度.md | 代账机构远期场景 | 2026-07-19 |
| HUICAI-SPC-113 | S-26-电子会计档案与合规归档.md | 合规需求，非MVP必需 | 2026-07-19 |

## 十、文档更新规范

1. **新增文档**：分配 HUICAI-{CAT}-{NEXT} 编号，在注册表登记
2. **修改文档**：更新文件头部的版本号 + 修改日期，注册表对应行更新
3. **废弃文档**：移入 archive/，注册表标记为「已归档」
4. **编号不可复用**：已分配编号即使文档废弃也不可重新使用

---

> **文档结束。所有项目文档的编号和状态以此注册表为准。**