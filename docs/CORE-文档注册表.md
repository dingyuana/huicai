# 慧财财务系统 — 文档注册表

> **版本**：V4.7 | **日期**：2026-09-23 | **维护人**：Hermes
> **说明**：本文档是所有项目文档的权威索引，编号格式 `HUICAI-{分类}-{序号}`
> **编号规则**：MAIN=主文档, DES=设计, ARC=架构, PRD=产品需求, SPC=规格, DEV=开发, TST=测试
> **V4.7 变更**：P84/P85 SPEC 落地（结账工作台 4 步向导 + 结转序列补全三子项）；翻转 PRD-017 R-136/R-137 状态；数量与实际对齐（prd 21、dsn 18、specs md 95）
> **V4.4 变更**：登记报表质量与体验增强 PRD-018 + DSN-018（三表缺陷 P88/显示穿透 P89/进阶 P90，SPEC 预留 P88~P90）；数量与实际对齐（prd 21、dsn 18、specs md 91）
> **V4.3 变更**：登记期末结账工作台 PRD-017 + DSN-017（结账向导/结转序列/前置检查扩展/期间锁全链路，SPEC 预留 P84~P87）；数量与实际对齐（prd 20、dsn 17、specs md 91）
> **V4.2 变更**：登记经营状况分析 PRD-016 + DSN-014（预算差异/指标预警/现金流静态预测/驾驶舱，SPEC 预留 P80~P83）；补登 DSN-015/016、P73~P79 SPEC；数量与实际对齐（prd 19、dsn 16、specs md 91）
> **V4.1 变更**：SPEC 编号冲突修复（P57-enterprise→P71、P58-opening→P72）；登记 P66~P69、S-27 及 development/testing 新增文档；数量与实际对齐（specs 81 份、需求 83 条、audit 2 份）；TST-007 编号冲突修复（test-methodology→TST-014）；归档 testing 4 份一次性产物至 archive/test-old；登记 dir/ DIR 机制（DEV-020）
> **V4.0 变更**：PRD/DSN/SPEC 三层体系正式确立；所有路径/版本/数量与实际对齐

---

## 〇、目录结构概览

```
docs/
├── CORE-项目说明.md                    ← 核心①：产品定位/设计理念/项目范围（V6.1）
├── CORE-技术方案.md                    ← 核心②：架构/技术栈/模块清单/设计决策（V5.1）
├── CORE-需求分析.md                    ← 核心③：需求全景/功能矩阵/版本规划（V5.0）
├── CORE-文档注册表.md                  ← 本文档，权威索引（V4.4）
│
├── prd/                        ← 产品需求文档（21份，含1份拆解计划）
├── design/                     ← 模块设计文档（18份 DSN + 2份分析 + archify 架构图产物）
├── specs/                      ← SPEC 规格契约（91份 md + 1份审计 JSON）
├── architecture/               ← 架构设计文档（7份）
│
├── development/                ← 开发文档
│   ├── coding-conduct.md        ← 编码规范 V2.0
│   ├── frontend-design-system.md ← 前端设计系统（FDS v0.1 草案）
│   ├── frontend-batch-ops-convention.md ← 前端批量操作约定
│   ├── MOCK_BLIND_SPOTS.md      ← Mock 测试盲区清单
│   ├── plans/                   ← 开发计划（10份）
│   ├── tasks/                   ← 任务书（30份）
│   ├── requirements/            ← 需求分析（8份 + 登记册）
│   ├── standards/               ← 开发规范（4份）
│   ├── lessons/                 ← 经验教训（4份）
│   ├── guides/                  ← 操作指南（2份）
│   ├── workflows/               ← 工作流（2份）
│   ├── incidents/               ← 事故报告（1份）
│   ├── audit/                   ← 审计报告（2份）
│   └── scripts/                 ← 开发脚本
│
├── testing/                    ← 测试文档（21份 = 17 md + 4 模板）
│   ├── TEST-STRATEGY.md         ← 测试策略（V1.1）
│   ├── plans/                   ← 测试计划（4份）
│   ├── standards/               ← 测试标准（3份）
│   ├── templates/               ← 测试模板（4份 Java）
│   └── 其他测试文档
│
├── reference/                  ← 外部参考文档
├── dir/                        ← DIR 文档改进请求机制
└── archive/                    ← 归档文档（历史，不参与日常开发）
```

---

## 一、核心文档（3份）

> 项目文档顶层只有三份核心文档，其余均为支撑性子文档。

| 编号 | 文件名 | 版本 | 最后修改 | 作者 | 说明 |
|------|--------|------|---------|------|------|
| HUICAI-MAIN-001 | [CORE-项目说明.md](./CORE-项目说明.md) | V6.1 | 2026-09-13 | Hermes | 产品定位/设计理念/项目范围/11种docType/1776测试 |
| HUICAI-MAIN-002 | [CORE-技术方案.md](./CORE-技术方案.md) | V5.1 | 2026-09-13 | Hermes | 系统架构/技术栈/模块清单/关键设计决策 |
| HUICAI-MAIN-003 | [CORE-需求分析.md](./CORE-需求分析.md) | V5.0 | 2026-08-19 | Hermes | 需求全景/功能矩阵/版本规划/验收标准（R-001~R-305）⚠️ 版本规划未反映 P54+ 进展，待更新 |

---

## 二、PRD ↔ DSN ↔ SPEC 三层体系

> **顺序**：PRD（需求层）→ DSN（设计层）→ SPEC（契约层）→ 代码 + @Test
> **PRD**：回答 What，对应 16 个模块需求
> **DSN**：回答 Architecture，13 份模块级设计
> **SPEC**：回答 How，77 份功能点级契约

### 2.1 PRD 产品需求文档（prd/ — 18份，含拆解计划）

| 编号 | 文件名 | 关联 DSN | 关联 SPEC | 状态 |
|------|--------|---------|----------|------|
| HUICAI-PRD-000 | [业务单据管理-PRD-V1.0.md](./prd/业务单据管理-PRD-V1.0.md) | DSN-应收应付管理 | P-BUSINESSDOC-LIST、P-SALARY、P-TRANSFER | ✅ |
| HUICAI-PRD-000b | [业务单据通用引擎-PRD-V1.0.md](./prd/业务单据通用引擎-PRD-V1.0.md) | DSN-应收应付状态机设计 | P-BUSINESSDOC-LIST、P-SALARY、P-TRANSFER | ✅ |
| HUICAI-PRD-001 | [基础数据管理-PRD-V1.0.md](./prd/基础数据管理-PRD-V1.0.md) | DSN-基础数据管理 | S-04、S-05 | ✅ |
| HUICAI-PRD-002 | [凭证管理-PRD-V1.0.md](./prd/凭证管理-PRD-V1.0.md) | DSN-总账管理 | P22、P37、S-17 | ✅ |
| HUICAI-PRD-003 | [总账结账-PRD-V1.0.md](./prd/总账结账-PRD-V1.0.md) | DSN-总账管理 | S-17、S-18 | ✅ |
| HUICAI-PRD-004 | [报表中心-PRD-V1.0.md](./prd/报表中心-PRD-V1.0.md) | DSN-报表分析 | P17-report-center | ✅ |
| HUICAI-PRD-005 | [发票税务管理-PRD-V1.0.md](./prd/发票税务管理-PRD-V1.0.md) | DSN-发票税务管理 | P40、P41、P13 | ✅ |
| HUICAI-PRD-006 | [应收应付核销-PRD-V1.0.md](./prd/应收应付核销-PRD-V1.0.md) | DSN-应收应付管理 | P30、P36、P42、P43、P51、P52、P53 | ✅ |
| HUICAI-PRD-007 | [资金管理-PRD-V1.0.md](./prd/资金管理-PRD-V1.0.md) | DSN-资金管理 | P1、P14、P23 | ✅ |
| HUICAI-PRD-008 | [费用报销-PRD-V1.0.md](./prd/费用报销-PRD-V1.0.md) | DSN-费用报销管理 | P11 | ✅ |
| HUICAI-PRD-009 | [固定资产-PRD-V1.0.md](./prd/固定资产-PRD-V1.0.md) | DSN-固定资产管理 | S-23 | ✅ |
| HUICAI-PRD-010 | [预算管理-PRD-V1.0.md](./prd/预算管理-PRD-V1.0.md) | DSN-预算管理 | P16 | ✅ |
| HUICAI-PRD-011 | [工资薪酬-PRD-V1.0.md](./prd/工资薪酬-PRD-V1.0.md) | DSN-工资薪酬管理 | S-14（待建） | ✅（模块待建） |
| HUICAI-PRD-012 | [Agency分支-PRD-V1.0.md](./prd/Agency分支-PRD-V1.0.md) | DSN-应收应付管理 | S-26 | ✅ |
| HUICAI-PRD-013 | [公共参数-PRD-V1.0.md](./prd/公共参数-PRD-V1.0.md) | DSN-基础数据管理 | 待建 | ⚠️ 部分实现 |
| HUICAI-PRD-014 | [权限安全审计-PRD-V1.0.md](./prd/权限安全审计-PRD-V1.0.md) | DSN-基础数据管理 | S-01、S-02 | ✅ |
| HUICAI-PRD-015 | [凭证模板引擎-PRD-V1.0.md](./prd/凭证模板引擎-PRD-V1.0.md) | DSN-总账管理 | P22、P37、S-17 | ✅ |
| HUICAI-PRD-016 | [经营状况分析-PRD-V1.0.md](./prd/经营状况分析-PRD-V1.0.md) | DSN-经营状况分析 | P80~P83（预留） | ⚠️ 需求已定，待开发（R-132~135） |
| HUICAI-PRD-017 | [期末结账工作台-PRD-V1.0.md](./prd/期末结账工作台-PRD-V1.0.md) | DSN-期末结账工作台 | P84 ✅ / P85 ✅ / P87 ✅（P86 预留） | ✅ P84/P85/P87 已实现（R-136/137/139），P86 待开发（R-138） |
| HUICAI-PRD-018 | [报表质量与体验增强-PRD-V1.0.md](./prd/报表质量与体验增强-PRD-V1.0.md) | DSN-报表质量与体验增强 | P88 ✅（已建 d74c7c6）/ P89~P90（预留） | ✅ P88 信任级缺陷已修复（R-140） |
| HUICAI-PRD-PLAN | [PRD-拆解计划.md](./prd/PRD-拆解计划.md) | — | — | ✅ |

### 2.2 DSN 模块设计文档（design/ — 18份）

| 编号 | 文件名 | 关联 PRD | 说明 |
|------|--------|---------|------|
| HUICAI-DES-001 | [DSN-基础数据管理.md](./design/DSN-基础数据管理.md) | 基础数据管理 PRD | 科目/期间/权限/主数据 |
| HUICAI-DES-002 | [DSN-总账管理.md](./design/DSN-总账管理.md) | 凭证管理 PRD + 总账结账 PRD | 凭证/账簿/结账/红冲 |
| HUICAI-DES-003 | [DSN-应收应付管理.md](./design/DSN-应收应付管理.md) | 业务单据管理 + 应收应付核销 PRD | 业务单据/核销/预收预付/坏账 |
| HUICAI-DES-004 | [DSN-应收应付状态机设计.md](./design/DSN-应收应付状态机设计.md) | 业务单据管理/通用引擎 PRD | 统一状态机规范 |
| HUICAI-DES-005 | [DSN-发票税务管理.md](./design/DSN-发票税务管理.md) | 发票税务管理 PRD | 进销项/以票定账/增值税 |
| HUICAI-DES-006 | [DSN-资金管理.md](./design/DSN-资金管理.md) | 资金管理 PRD | 银行流水/对账/日记账/票据 |
| HUICAI-DES-007 | [DSN-固定资产管理.md](./design/DSN-固定资产管理.md) | 固定资产 PRD | 卡片/折旧/处置/盘点 |
| HUICAI-DES-008 | [DSN-费用报销管理.md](./design/DSN-费用报销管理.md) | 费用报销 PRD | 报销/审批/凭证 |
| HUICAI-DES-009 | [DSN-预算管理.md](./design/DSN-预算管理.md) | 预算管理 PRD | 编制/控制/调整/结转 |
| HUICAI-DES-010 | [DSN-报表分析.md](./design/DSN-报表分析.md) | 报表中心 PRD | 三大报表/杜邦/趋势 |
| HUICAI-DES-011 | [DSN-工资薪酬管理.md](./design/DSN-工资薪酬管理.md) | 工资薪酬 PRD（待建） | 工资表/个税/凭证 |
| HUICAI-DES-012 | [DSN-银行流水智能分类.md](./design/DSN-银行流水智能分类.md) | 资金管理 PRD | 竞品分析+智能分类 |
| HUICAI-DES-013 | [DSN-前端简化方案.md](./design/DSN-前端简化方案.md) | Agency分支 PRD + 基础数据 PRD | 前端菜单精简方案 |
| HUICAI-DES-014 | [DSN-经营状况分析.md](./design/DSN-经营状况分析.md) | 经营状况分析 PRD（HUICAI-PRD-016） | 预算差异/指标预警/现金流静态预测/驾驶舱 |
| HUICAI-DES-015 | [DSN-代理公司场景设计.md](./design/DSN-代理公司场景设计.md) | Agency分支 PRD | 代理公司（多租户）场景设计 |
| HUICAI-DES-016 | [DSN-前端查询条件区风格统一.md](./design/DSN-前端查询条件区风格统一.md) | — | 前端查询条件区风格统一规范 |
| HUICAI-DES-017 | [DSN-期末结账工作台.md](./design/DSN-期末结账工作台.md) | 期末结账工作台 PRD（HUICAI-PRD-017） | 结账向导/结转序列/前置检查扩展/期间锁全链路 |
| HUICAI-DES-018 | [DSN-报表质量与体验增强.md](./design/DSN-报表质量与体验增强.md) | 报表质量与体验增强 PRD（HUICAI-PRD-018） | 三表缺陷修复/显示穿透/重分类与异常高亮 |

> **设计分析报告**（design/analysis/，非 DSN 设计文档）：
> - [DSN-PRD合理性评估.md](./design/analysis/DSN-PRD合理性评估.md)
> - [DSN-PRD差距分析.md](./design/analysis/DSN-PRD差距分析.md)

### 2.3 SPEC 规格契约文档（specs/ — 91份 md + 1份 JSON）

| 编号范围 | 说明 | 数量 |
|----------|------|------|
| P0~P72 | 功能规格文档（P 系列，含 P-BUSINESSDOC-LIST/P-SALARY/P-TRANSFER），按开发顺序增量编号 | 64份 |
| P73~P79 | 后补批次：P73 银行流水核销 UX、P74 核销单凭证模板、P75 应收应付余额汇总（草案）、P76 费用汇总（已实现）、P77 折旧统计（已实现）、P78 预收预付汇总（已实现）、P79 代理进度（已实现 P0+P1） | 7份 |
| S-00~S-29 | 系统级规范文档（S 系列，S-17 含 S-17-1 子文档），按业务域分层编号 | 14份 |
| 其他 | SPEC-CONTRACT-SCHEMA.md（契约规范）、T1-BankStatement数据隔离测试方案.md、timestamp-precision.md、P-LARGETABLE-BATCH、P-OUTPUTINVOICE-LIST | 6份 |

> **编号冲突修复记录（2026-09-13）**：原 P57/P58 编号各有两个文件。保留已实现并登记的 P57-declare-status-split、P58-invoice-payment-reconcile；将草案阶段的 P57-enterprise-start-period 重编号为 **P71**、P58-opening-balance-entry-audit 重编号为 **P72**，需求回链（REQ-2026-077/078）已同步。P69-balance-sheet-equality 由并行会话于同日新建。

**全部 SPEC 按模块归类**：

| 对应模块 | SPEC 文件 |
|---------|---------|
| 凭证管理 | P22-voucher-state-machine、P37-voucher-type-rules、S-17-期末自动化结转、S-17-1-profit-distribution、S-18-结账控制、P59-opening-balance-single-entry、P60-auxiliary-ledger-query、P62-subsidiary-date-range-and-trial-empty、P63-ledger-p1-alignment、P64-ledger-perf-and-vo、P65-ledger-frontend、P68-period-close-ordering |
| 应收应付核销 | P30、P30-P1-unified-settlement-path、P36、P42、P43-bad-debt、P43-reconciliation-log-settlement-state-machine、P44-settlement-list-display-fix、P45-reconciliation-upstream-trace-fix、P51、P52、P53、S-28-反核销制证凭证联动作废、P10、P73-bank-statement-settlement-ux、P74-settlement-voucher-template-fix |
| 发票税务 | P40-input-invoice、P41-invoice-driven-finance、P13-tax-declaration、P36-1-red-flush-voucher、P57-declare-status-split、P57-enterprise-start-period→**已重编号 P71**、P58-invoice-payment-reconcile、P58-opening-balance-entry-audit→**已重编号 P72**、P61-vat-appendix-and-burden |
| 资金管理 | P1-bank-import、P14-bank-reconciliation、P23-bank-statement、P55-bank-statement-subject-and-batch-fix、S-29-数据导入模块详细规格 |
| 固定资产 | S-23 |
| 工资薪酬 | S-14（待建） |
| 基础数据 | S-04、S-05、P71-enterprise-start-period（原 P57，企业级建账期间）、P72-opening-balance-entry-audit（原 P58，期初建账审计） |
| 费用报销 | P11、P66-expense-reimbursement-frontend-polish |
| 预算管理 | P16 |
| 报表中心 | P17-report-center、P69-balance-sheet-equality（资产负债表平衡根治）、P75-arap-balance-summary（草案）、P76-expense-summary-report、P77-asset-depreciation-statistics、P78-prepayment-balance-summary |
| Agency | S-26、P79-agency-service-progress-workload |
| AI Agent | P40-ai-service-skeleton-enhancement、P46~P50（R-301~305，暂不拆 PRD） |
| 业务单据增强 | P-BUSINESSDOC-LIST、P-SALARY、P-TRANSFER、P67-batch-ops-unification（前端批量操作统一） |
| 其他/横切 | P0-project-skeleton、P4-counterparty-direction、P5-auto-reconciliation、P7-test-coverage、P8-h2-compatibility、P12-reconciliation-business-closure、P15-attachment-ocr、P18-declaration-state-machine、P19-financial-document-content-standards、P24-audit-tracking、P27-businessdoc-customer-vendor-name-fix、P32-financial-data-integrity、P33-code-vs-spec-gap-analysis、P38-fix-three-flow-traceability-gaps、P54-code-restructure-base-sme-agency、S-00-base-architecture、S-27-import-export-framework |

---

### 2.4 架构文档（architecture/ — 7份）

| 编号 | 文件名 | 版本 | 说明 |
|------|--------|------|------|
| HUICAI-ARC-001 | [ARC-AI架构演进.md](./architecture/ARC-AI架构演进.md) | V1.0 | AI 架构演进设计 |
| HUICAI-ARC-002 | [ARC-AI编排层.md](./architecture/ARC-AI编排层.md) | V1.0 | AI 编排层架构 |
| HUICAI-ARC-003 | [ARC-编号关联体系.md](./architecture/ARC-编号关联体系.md) | V1.8 | 6种实体编号关联 |
| HUICAI-ARC-004 | [ARC-状态机红线规范.md](./architecture/ARC-状态机红线规范.md) | V1.1 | 状态机红线规范 |
| HUICAI-ARC-005 | [ARC-分层架构规范.md](./architecture/ARC-分层架构规范.md) | V1.0 | 强制分层架构规范 |
| HUICAI-ARC-006 | [ARC-核心链路映射.md](./architecture/ARC-核心链路映射.md) | V1.0 | 核心链路映射 |
| HUICAI-ARC-007 | [ARC-多租户架构设计.md](./architecture/ARC-多租户架构设计.md) | V1.0 | 多租户架构设计 |

---

### 2.5 开发文档（development/）

| 编号 | 文件名 | 说明 |
|------|--------|------|
| HUICAI-DEV-001 | [coding-conduct.md](./development/coding-conduct.md) | 编码规范 V2.0 |
| HUICAI-DEV-002 | [文档管理规范.md](./development/standards/文档管理规范.md) | 文档编号/头部/生命周期 |
| HUICAI-DEV-003 | [flyway-governance.md](./development/standards/flyway-governance.md) | Flyway 迁移规范 |
| HUICAI-DEV-004 | [差距检测规范.md](./development/standards/差距检测与设计-实施同步规范.md) | 差距检测规范 |
| HUICAI-DEV-005 | [银行流水导入后操作流程.md](./development/guides/银行流水导入后操作流程.md) | 操作指南 |
| HUICAI-DEV-006 | [CORE-Hermes技能手册.md](./development/guides/CORE-Hermes技能手册.md) | Hermes 技能手册 |
| HUICAI-DEV-007 | [P0-P3-roadmap.md](./development/plans/P0-P3-roadmap.md) | P0-P3 开发路线图 |
| HUICAI-DEV-008 | [REQUIREMENTS_REGISTRY.md](./development/requirements/REQUIREMENTS_REGISTRY.md) | 需求登记册（83条） |
| HUICAI-DEV-009 | [THREE-PHASE-LOOP.md](./development/workflows/THREE-PHASE-LOOP.md) | 三步闭环工作流 |
| HUICAI-DEV-010 | [AI-EVOLUTION-CLOSED-LOOP.md](./development/workflows/AI-EVOLUTION-CLOSED-LOOP.md) | AI 演进闭环工作流 |
| HUICAI-DEV-011 | tasks/（30份） | 任务书/日报/验证报告 |
| HUICAI-DEV-012 | lessons/（4份） | 经验教训 |
| HUICAI-DEV-013 | incidents/（1份） | 事故报告（销项发票 auditedBy 列缺失） |
| HUICAI-DEV-014 | audit/（2份） | 项目设计文档综合审核报告（2026-07-23）、账簿查询功能评估报告（2026-08-31） |
| HUICAI-DEV-015 | scripts/check-spec-compliance.sh | SPEC 合规性检查脚本 |
| HUICAI-DEV-016 | scripts/check-spec-drift.sh | SPEC 漂移检测脚本 |
| HUICAI-DEV-017 | [frontend-design-system.md](./development/frontend-design-system.md) | 前端设计系统（FDS v0.1 草案，REQ-2026-081） |
| HUICAI-DEV-018 | [frontend-batch-ops-convention.md](./development/frontend-batch-ops-convention.md) | 前端批量操作约定 |
| HUICAI-DEV-019 | [MOCK_BLIND_SPOTS.md](./development/MOCK_BLIND_SPOTS.md) | Mock 测试盲区清单 |
| HUICAI-DEV-020 | [dir/README.md](./dir/README.md) | DIR 文档改进请求机制（⚠️ 2026-07-20 起无条目，机制待复盘） |

### 2.6 测试文档（testing/ — 21份 = 17 md + 4 模板）

| 编号 | 文件名 | 说明 |
|------|--------|------|
| HUICAI-TST-001 | [TEST-STRATEGY.md](./testing/TEST-STRATEGY.md) | 测试策略（V1.1） |
| HUICAI-TST-002 | plans/full-stack-test-methodology.md | 全链路测试方法论 |
| HUICAI-TST-003 | plans/state-machine-test-checklist.md | 状态机检查清单 |
| HUICAI-TST-004 | plans/frontend-test-plan.md | 前端测试方案 |
| HUICAI-TST-005 | plans/ai-service-test-plan.md | AI 服务测试方案 |
| HUICAI-TST-006 | standards/TESTING_QUICKSTART.md | 测试快速入门 |
| HUICAI-TST-007 | standards/TESTING_STANDARD.md | 测试标准规范 |
| HUICAI-TST-008 | standards/mapper-testcontainers-plan.md | Mapper 真实 DB 测试方案 |
| HUICAI-TST-009 | templates/（4份 Java） | 测试模板 |
| HUICAI-TST-010 | test-coverage-matrix.md | 测试覆盖矩阵 |
| HUICAI-TST-011 | test-prevention-mechanism.md | 防错机制 |
| HUICAI-TST-012 | entity-db-audit-report.md | Entity-DB 审计报告 |
| HUICAI-TST-013 | NUMBERING_ASSOCIATION_TEST_PLAN.md | 编号关联测试计划 |
| HUICAI-TST-014 | [test-methodology.md](./testing/test-methodology.md) | 测试方法总结与检查清单（原 TST-007，2026-09-13 重编号解决冲突） |
| HUICAI-TST-015 | [TDD-GUIDE.md](./testing/TDD-GUIDE.md) | TDD 指南 |
| HUICAI-TST-019 | [LESSONS-LEARNED.md](./testing/LESSONS-LEARNED.md) | 测试经验教训 |
| HUICAI-TST-021 | [performance-baseline.md](./testing/performance-baseline.md) | 性能基线快照（⚠️ 需定期刷新） |
| HUICAI-TST-022 | [S-26-agency-test-plan.md](./testing/S-26-agency-test-plan.md) | Agency 分支测试计划 |

> **已归档（2026-09-13，原 TST-016/017/018/020）**：DEVELOPMENT-PARADIGM.md、TDD-REVERSE-INV-DEMO.md、E2E-test-generation-plan.md、STAGING-CONFIG.md → [archive/test-old/](./archive/test-old/)（一次性产物/阶段性计划/配置快照，编号保留不复用）

---

## 三、归档文档（archive/）

> 归档文档按类别分类，不再参与日常开发，恢复需重新评审。

| 类别 | 归档路径 | 内容 |
|------|---------|------|
| 过容设计 | archive/over-engineered/ | 旧版 PRD/技术架构/Spec（含微服务/Milvus/Drools 等排除技术） |
| 旧版架构 | archive/architecture/ | layer-architecture.md、linkage-map.md |
| 旧版标准 | archive/standards/ | 旧版编码规范/差距检测 |
| 旧版工作流 | archive/dev-workflow/ | 旧版 AI-EVOLUTION-CLOSED-LOOP、THREE-PHASE-LOOP |
| 旧版开发计划 | archive/plans/ | 开发计划书/Sprint计划/总体开发计划 |
| 旧版需求分析 | archive/requirements/ | 00~07（旧版 AI 需求分析） |
| 旧版测试文档 | archive/test-old/ | TESTING_QUICKSTART/STANDARD/模板 + 一次性产物（DEVELOPMENT-PARADIGM/TDD-REVERSE-INV-DEMO/E2E-test-generation-plan/STAGING-CONFIG，2026-09-13 归档） |
| 历史任务书 | archive/tasks/ | P1~P33 历史任务书/验证报告 |
| 分析报告 | archive/analysis/ | 银行流水导入分类影响分析 |
| 审计报告 | archive/audit/ | 2026-06-25 综合审计报告 |
| 技术笔记 | archive/tech/ | MCP 协议/实战（2份） |
| 数据库迁移 | archive/migrations/ | V1~V91（91份 SQL + 1份说明） |
| 旧版主文档 | archive/ | DESIGN.md.bak（V4.4） |

---

## 四、文档规范

### 4.1 PRD 头部规范

```markdown
> **编号**：HUICAI-PRD-xxx
> **版本**：V1.0 | **日期**：YYYY-MM-DD
> **关联总 PRD**：`../CORE-需求分析.md`
> **关联设计**：DSN-xxx.md
> **关联SPEC**：Pxx、S-xx（建立 PRD→SPEC 可追溯）
> **对应包**：com.huicai.xxx
```

### 4.2 DSN 头部规范

```markdown
> **编号**：HUICAI-DES-xxx
> **版本**：V1.0 | **日期**：YYYY-MM-DD
> **关联PRD**：../prd/xxx-PRD-V1.0.md
> **关联文档**：相关设计文档
```

### 4.3 SPEC 头部规范

```markdown
> **编号**：HUICAI-SPC-xxx
> **版本**：V1.0 | **日期**：YYYY-MM-DD
> **状态**：✅ 生效 / ⚠️ 待建 / ❌ 废弃
> **关联PRD**：../prd/xxx-PRD-V1.0.md
> **test_ref**：@Test 方法名（待补）
```

### 4.4 版本号规范

| 变更类型 | 版本号变化 | 示例 |
|---------|-----------|------|
| 初始创建 | V1.0 | — |
| 小修改 | V1.x | V1.0→V1.1 |
| 大修改 | Vx.0 | V1.3→V2.0 |

### 4.5 文档生命周期

```
创建 → 生效 ✅ → 修改 ↺ → 废弃 ❌ → 归档
```

### 4.6 SPEC 漂移防错

- 修改后端 `service/`、`controller/`、`entity/` 下的代码时，必须同步检查关联 SPEC
- 状态机变更 → SPEC 的 `state` 定义必须同步
- API 路径/参数变更 → SPEC 的 API 定义必须同步
- 使用 `backend/scripts/check-spec-drift.sh` 辅助检测
- 使用 `.git/hooks/pre-commit` + `check-entity-schema.mjs` 做提交前自动校验

---

> **文档结束。以本文档为权威索引，所有文档路径和状态以本文档为准。**