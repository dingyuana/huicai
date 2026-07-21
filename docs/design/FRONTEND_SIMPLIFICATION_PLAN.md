# 前端精简设计方案

> **版本**：V1.0 | **日期**：2026-07-21 | **状态**：📋 设计评审中
> **目标**：将 67 个菜单项精简至约 35 个，剥离 Agency 分支与过度设计模块，聚焦 SME MVP 交付

---

## 1. 现状诊断

| 维度 | 现状 | 问题 |
|------|------|------|
| 菜单总数 | 67 项 | SME 用户面对过多入口，认知负荷高 |
| 一级目录 | 10 个 | `AI中心`、`财务分析`、`预算管理` 等超出 SME 核心范围 |
| Agency 模块 | 0 实现 | `agency.ts` 空路由占位，菜单却有残留 |
| 重复/相似 | 4 对 | 账龄分析×2、费用报销×2、核销相关×5 |
| 后端未实现 | 12 项 | 工资薪酬、合并报表、AI Agent、批量操作等 |

---

## 2. 设计原则

| 原则 | 说明 |
|------|------|
| **SME 优先** | 只保留 SME 分支 P0/P1 需求对应的菜单 |
| **Agency 彻底剥离** | 代账公司功能另建分支，不污染 SME 导航 |
| **单一入口** | 同一业务域只保留一个核心入口，子功能内嵌 Tab/抽屉 |
| **AI 内嵌不露面** | AI 能力作为辅助内嵌在业务页，不单独做顶级菜单 |
| **渐进增强** | Phase 2+ 功能（预算、分析、工资）移至「实验室」或后期开关 |

---

## 3. 精简后菜单架构（目标：35 项）

### 3.1 一级导航（10 个 → 7 个）✅ 已确认

| 顺序 | 目标一级菜单 | 来源 | 说明 |
|------|-------------|------|------|
| 1 | **首页** | Dashboard | 保留 |
| 2 | **基础数据** | 系统管理 + 基础数据 | 合并：科目/期间/摘要/商户/配置 |
| 3 | **财务核心** | 财务核心 | 凭证/账簿/结账/期初 |
| 4 | **业务单据** | 财务核心/往来/资金 | 业务单据、银行、现金、票据、**核销工作台** |
| 5 | **税务发票** | 税务管理 | 进项/销项/增值税 |
| 6 | **固定资产** | 固定资产 | 类别/卡片/折旧/处置/盘点 |
| 7 | **报表中心** | 报表中心 | 三大报表 + 科目余额表 |
| — | ❌ 费用报销 | → 业务单据 | 内嵌 Tab |
| — | ❌ 应收应付/核销 | → 业务单据 | 核销工作台作为 Tab |
| — | ❌ 预算管理 | Phase 2 | 移入「实验室」 |
| — | ❌ 财务分析 | Phase 2 | 移入「实验室」 |
| — | ❌ AI 中心 | 内嵌 | 不再顶级展示 |
| — | ❌ 系统管理 | → 基础数据/设置 | 权限相关移至用户菜单「设置」 |

---

### 3.2 二级菜单详细映射

#### 🏠 首页
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 仪表盘 | `/dashboard` | — | 保留，精简为关键指标卡片 |

---

#### 📁 基础数据（原 系统管理+基础数据，14→7 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 会计科目 | `/basis/subject` | `subjects:manage` | 树形 CRUD、一键导入标准科目 |
| 会计期间 | `/basis/period` | `periods:manage` | 开/关/锁期间 |
| 常用摘要 | `/basis/summary-lib` | `summary:lib:list` | 15 条种子数据 |
| 客商档案 | `/basis/party` | `party:list` | **合并**客户/供应商/员工/部门为单页 Tab |
| 分类规则 | `/system/classification-rule` | — | 银行流水分类规则（系统级） |
| 系统参数 | `/basis/config` | `sys:config:list` | 全局配置 |
| 凭证类型 | `/finance/voucher-setup?tab=type` | `voucher:type:list` | JZ/SK/FK/ZZ、编号规则 |

> ❌ 移除：用户/角色/菜单/部门/审计日志/数据维护 → 迁移至「设置」抽屉（仅超管可见）

---

#### 💰 财务核心（原 财务核心，10→6 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 凭证管理 | `/finance/voucher` | `voucher:list` | 列表+编辑+详情一体页（Tab） |
| 凭证模板 | `/finance/voucher-setup?tab=template` | `voucher:template:list` | 7 条种子模板 |
| 账簿查询 | `/finance/ledger` | `ledger:list` | 总账/明细账/多栏账 |
| 期末结账 | `/finance/period-close` | `period:close` | 结账/反结账/损益结转向导 |
| 期初建账 | `/finance/beginning-balance` | `beginning:balance:init` | 期初余额录入 |
| 结转向导 | `/finance/carryover-guide` | `period:carryover:guide` | 期末结转分步向导 |

---

#### 📋 业务单据（新建一级，聚合原 财务核心/往来/资金，18→10 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 业务单据 | `/finance/business-doc` | `doc:list` | 销售/采购/收付/其它单据统一列表 |
| 银行账户 | `/finance/bank-account` | `bank:account:list` | 账户档案 |
| 银行日记账 | `/finance/bank-journal` | `bank:journal:list` | 手工记账 |
| 银行对账单 | `/finance/bank-statement` | `bank:statement:list` | 导入/预览/确认 |
| 待处理流水 | `/finance/pending-pool` | `bank:statement:list` | **内嵌 Tab** 在银行对账单页 |
| 银行对账 | `/finance/bank-reconciliation` | `bank:reconciliation:list` | 余额调节表 |
| 现金日记账 | `/finance/cash-journal` | `cash:journal:list` | 现金收支 |
| 票据管理 | `/finance/ticket` | `ticket:list` | 承兑/支票 |
| 核销工作台 | `/arap/reconciliation-workbench` | `arap:reconciliation:workbench` | **作为 Tab** 在业务单据页，含推荐匹配/执行核销/异常池 |
| 费用报销 | `/finance/business-doc?tab=expense` | `arap:expense:list` | **内嵌 Tab** 在业务单据页 |

> ❌ 移除/合并：核销单/核销审批/核销异常池/客户对账/付款计划/采购退货/预收预付/坏账准备 → 全部内嵌核销工作台 Tab 或业务单据 Tab

---

#### 🧾 税务发票（保留 3 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 进项发票 | `/tax/input-invoice` | `tax:input:list` | 导入/验真/勾选/认证/状态机 |
| 销项发票 | `/tax/output-invoice` | `tax:output:list` | 开具/红冲/状态机 |
| 增值税计算 | `/tax/vat` | `tax:vat:view` | 进销项抵扣/申报表预填 |

---

#### 🏭 固定资产（保留 5 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 资产类别 | `/asset/category` | `asset:category:list` | 分类树 |
| 资产卡片 | `/asset/card` | `asset:card:list` | 增减变/折旧预览 |
| 折旧计提 | `/asset/depreciation` | `asset:depreciation:run` | 月度批量计提 |
| 资产处置 | `/asset/disposal` | `asset:disposal:list` | 处置审批/凭证 |
| 资产盘点 | `/asset/inventory` | `asset:inventory:list` | 盘点单/差异处理 |

---

#### 📊 报表中心（保留 4 项）
| 菜单 | 路由 | 权限 | 说明 |
|------|------|------|------|
| 科目余额表 | `/report/subject-balance` | `report:subject:list` | 多维筛选/导出 |
| 资产负债表 | `/report/balance-sheet` | `report:balance:view` | 标准模板 |
| 利润表 | `/report/income-statement` | `report:income:view` | 标准模板 |
| 现金流量表 | `/report/cash-flow` | `report:cashflow:view` | 标准模板 |

---

#### ⚙️ 设置（用户菜单下拉，非侧边栏）
| 入口 | 说明 |
|------|------|
| 用户管理 | 超管可见 |
| 角色权限 | 超管可见 |
| 菜单管理 | 超管可见 |
| 部门管理 | 超管可见 |
| 审计日志 | 超管可见 |
| 数据维护 | 超管可见 |
| **实验室功能** | **开关**：预算/分析/工资/AI 等 Phase 2+ 功能 |

---

## 4. 路由文件重构计划

| 文件 | 处理方式 |
|------|----------|
| `routes/base.ts` | **重命名** `routes/sme-base.ts`，只保留 SME 通用骨架 |
| `routes/sme.ts` | **拆分** 为 `routes/sme-business.ts` `routes/sme-tax.ts` `routes/sme-asset.ts` `routes/sme-report.ts` |
| `routes/agency.ts` | **保留空文件**，Phase 2 实现 |
| `routes/index.ts` | 动态加载：`base + (isAgency ? agency : sme-*)` |

### 新增：实验室路由（Phase 2+ 功能开关）
```ts
// routes/lab.ts - 受 feature flag 控制
const labRoutes = [
  { path: 'budget', ... },           // 预算管理
  { path: 'budget/adjustment', ... }, // 预算调整
  { path: 'analysis/key-metrics', ... }, // 关键指标
  { path: 'analysis/dupont', ... },     // 杜邦分析
  { path: 'salary', ... },              // 工资薪酬（待建）
  { path: 'ai/task', ... },             // AI 任务
  { path: 'ai/anomaly', ... },          // AI 异常
]
```

---

## 5. 组件清理清单

| 目录 | 动作 | 说明 |
|------|------|------|
| `views/arap/aging/` | ❌ 删除 | 重复，合并入 `views/arap/aging-analysis/` |
| `views/arap/aging-analysis/` | ✅ 保留 | 重命名为 `views/arap/aging/` |
| `views/arap/ExpenseList.vue` + `ExpenseEdit.vue` | 🔄 迁移 | 移至 `views/finance/business-doc/ExpenseTab.vue` |
| `views/arap/reconciliation-*` (5个) | 🔄 合并 | 合并为 `views/arap/ReconciliationWorkbench.vue` 单页 Tab |
| `views/arap/customer-statement/` | 🔄 内嵌 | 作为核销工作台 Tab |
| `views/arap/payment-plan/` | 🔄 内嵌 | 作为业务单据 Tab |
| `views/arap/purchase-return/` | 🔄 内嵌 | 作为业务单据 Tab |
| `views/arap/bad-debt/` | 🔄 内嵌 | 作为核销工作台 Tab |
| `views/arap/prepayment/` | 🔄 内嵌 | 作为业务单据 Tab |
| `views/budget/` (3个) | 📦 移入 lab/ | Phase 2 受 flag 控制 |
| `views/analysis/` (2个) | 📦 移入 lab/ | Phase 2 受 flag 控制 |
| `views/ai/` (2个) | 📦 移入 lab/ | AI 能力内嵌业务页，不再独立入口 |
| `views/system/user|role|menu|dept|audit-log|clear-data/` | 📦 移入 settings/ | 仅超管可见 |

---

## 6. 权限码精简

| 原权限码 | 新权限码 | 映射说明 |
|---------|---------|----------|
| `customer:list` `vendor:list` `employee:list` `dept:list` | `party:list` | 合并为客商档案 |
| `arap:aging:view` `aging:analysis:list` | `arap:aging:view` | 合并账龄分析 |
| `arap:settlement:list` `arap:reconciliation:workbench` `arap:reconciliation:approve` `arap:reconciliation:exception` `customer:statement:list` `arap:payment:plan` `arap:purchase:return:list` `prepayment:list` `bad:debt:list` | `arap:reconciliation:workbench` | 统一核销工作台权限 |
| `budget:list` `budget:create` `budget:adjustment` | `lab:budget` | 实验室开关 |
| `analysis:key:view` `analysis:dupont:view` | `lab:analysis` | 实验室开关 |
| `ai:task:list` `ai:anomaly:list` | 内嵌 | 无独立权限 |

---

## 7. 实施步骤

| 阶段 | 任务 | 产出 | 验收 |
|------|------|------|------|
| **P0-1** | 备份现有 routes/、views/ | Git tag `pre-frontend-simplify` | 可回滚 |
| **P0-2** | 重写 `routes/sme-base.ts` + 新建 4 个 sme-* 路由文件 | 35 个路由定义 | `npm run build` 通过 |
| **P0-3** | 调整菜单种子数据 | V96 migration | 菜单树对齐设计 |
| **P0-4** | 合并/迁移 Vue 组件 | 组件数 -40% | 核心流程 E2E 通过 |
| **P0-5** | 权限码迁移脚本 | SQL 更新 `t_menu` + `t_role_menu` | 现有角色权限不丢失 |
| **P1** | 侧边栏组件适配分支（SME/Agency） | `AppLayout.vue` 动态加载 | Agency 空态友好 |
| **P2** | 实验室 Feature Flag 接入 | `useLabStore` + 后端配置 | 开关生效即时 |

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 旧书签/收藏失效 | 用户抱怨 | 路由别名重定向：`/arap/aging` → `/arap/aging-analysis` |
| 权限码变更导致无权访问 | 运营事故 | 发布前跑迁移脚本，灰度 1 小时 |
| 核销工作台过重 | 性能/复杂度 | 分 Tab 懒加载，虚拟滚动列表 |
| Agency 分支空白影响演示 | 销售阻塞 | Phase 1 交付 SME，Agency 单独里程碑 |

---

## 9. 遗留功能清单（Phase 2+ 接管）

| 功能 | 所属设计文档 | 优先级 |
|------|-------------|--------|
| 预算编制/控制/调整 | `08-预算管理.md` | P1 |
| 杜邦/关键指标/趋势分析 | `09-报表分析.md` | P1 |
| 工资薪酬计算/个税/社保 | `07-工资薪酬管理.md` | P1 |
| 多客户账套/批量操作/CRM | Agency 分支设计 | P0 (Agency) |
| AI 任务/异常/分类/审核/核销 Agent | `10-AI编排层.md` | P2 |
| 合并报表/内部交易抵消 | 归档 S-24 | P3 |

---

## 10. 决策需求

> 请老丁在以下项上确认或调整：

1. **一级菜单数量**：目标 7 个（首页+基础数据+财务核心+业务单据+税务发票+固定资产+报表中心），是否接受？或合并「固定资产」入「业务单据」？
2. **核销工作台**是否作为「业务单据」的 Tab 而非一级菜单？（当前设计已调整为 Tab）
3. **费用报销**内嵌「业务单据」Tab，确认无异议
4. **实验室**入口放在用户菜单下「实验室功能」开关，确认
5. **分类规则**归属「基础数据」，确认
6. **客商档案**合并客户/供应商/员工/部门为单页 Tab，确认无异议

---

**文档结束** — 评审通过后，我将按实施步骤逐步执行重构。