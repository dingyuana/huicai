# E2E 测试生成计划

## 最终交付成果

**从 4 个文件 / 10 个测试 → 16 个文件 / 51 个测试**

| 阶段 | 新增测试 | 覆盖模块 | 文件数 |
|:----:|:-------:|---------|:-----:|
| **已有** | 10 | 登录(3) + 销项发票(2) + 银行对账单(2) + 核销工作台(3) | 4 |
| **阶段1 (P0)** | 17 | 凭证全流程(5) + 科目摘要(3) + 进项发票(5) + 期末结账(4) | 4 |
| **阶段2 (P1)** | 12 | 资产类别(3) + 资产卡片(3) + 科目余额表(2) + 三大报表(3) + 增值税计算(3) | 5 |
| **阶段3 (P1)** | 12 | 业务单据(3) + 代理客户管理(3) + 异常页面(4) | 3 |
| **合计** | **51** | **覆盖 16 个页面/模块** | **16** |

## 文件清单

### 已有（4 文件 / 10 测试）
| 文件 | 路径 | 测试数 |
|------|------|:------:|
| login.spec.ts | `/finance/login` | 3 |
| sales-invoice-import.spec.ts | `/tax/output-invoice` | 2 |
| bank-statement-import.spec.ts | `/finance/bank-statement` | 2 |
| reconciliation-workbench.spec.ts | `/arap/reconciliation-workbench` | 3 |

### 阶段1（4 文件 / 17 测试）
| 文件 | 路径 | 测试数 | 说明 |
|------|------|:------:|------|
| voucher-workflow.spec.ts | `/finance/voucher` | 5 | 列表/新增/详情/DRAFT→SUBMITTED→AUDITED→POSTED |
| basis-account-and-summary.spec.ts | `/basis/account-and-summary` | 3 | 科目树/摘要列表 Tab 切换 |
| input-invoice.spec.ts | `/tax/input-invoice` | 5 | 列表/新增弹窗/提交审核/审核通过 |
| period-close.spec.ts | `/finance/period-close` | 4 | 检查通过/未通过/试算平衡/损益结转 |

### 阶段2（5 文件 / 12 测试）
| 文件 | 路径 | 测试数 | 说明 |
|------|------|:------:|------|
| asset-category.spec.ts | `/asset/category` | 3 | 类别列表/新增弹窗 |
| asset-card.spec.ts | `/asset/card` | 3 | 卡片列表/新增弹窗 |
| report-subject-balance.spec.ts | `/report/subject-balance` | 2 | 余额表查询/合计行 |
| report-financial.spec.ts | `/report/balance-sheet` + `/report/income-statement` + `/report/cash-flow` | 3 | 三大报表加载 |
| tax-vat.spec.ts | `/tax/vat` | 3 | 汇总卡片/按税率明细 |

### 阶段3（3 文件 / 12 测试）
| 文件 | 路径 | 测试数 | 说明 |
|------|------|:------:|------|
| business-doc-list.spec.ts | `/finance/business-doc` | 3 | 单据列表/类型筛选 |
| agency-enterprise.spec.ts | `/agency/enterprise-list` | 3 | 客户列表/状态标签/新增企业 |
| error-pages.spec.ts | `/403` + `/404` + 未登录 | 4 | 403/404/路由兜底/权限跳转 |

## 执行状态（2026-07-28）

**33/51 通过（65%）** — 16 个文件，2.8 分钟全量运行

### ✅ 通过（33 个）

| 文件 | 通过/总数 | 状态 |
|------|:---------:|:----:|
| login.spec.ts | 3/3 | ✅ |
| bank-statement-import.spec.ts | 2/2 | ✅ |
| reconciliation-workbench.spec.ts | 3/3 | ✅ |
| sales-invoice-import.spec.ts | 2/2 | ✅ |
| voucher-workflow.spec.ts | 3/5 | ⚠️ 2个失败 |
| basis-account-and-summary.spec.ts | 2/3 | ⚠️ 1个失败 |
| period-close.spec.ts | 4/4 | ✅ |
| report-financial.spec.ts | 3/3 | ✅ |
| report-subject-balance.spec.ts | 0/2 | ❌ |
| business-doc-list.spec.ts | 3/3 | ✅ |
| error-pages.spec.ts | 4/4 | ✅ |
| asset-category.spec.ts | 0/3 | ❌ |
| asset-card.spec.ts | 2/3 | ⚠️ 1个失败 |
| input-invoice.spec.ts | 1/5 | ⚠️ 4个失败 |
| tax-vat.spec.ts | 0/3 | ❌ |
| agency-enterprise.spec.ts | 0/3 | ❌ |

### ❌ 失败原因分析（18 个）

| 类型 | 数量 | 典型问题 | 涉及文件 |
|------|:----:|---------|---------|
| 页面未加载 | 5 | 路由守卫拦截，`page-title` 未找到 | asset-category(3), basis-account(1), voucher-workflow(1) |
| 数据未显示 | 5 | mock 数据格式与视图 computed 不匹配 | report-subject-balance(2), tax-vat(3) |
| 严格模式冲突 | 4 | `getByText` 匹配到多个元素 | voucher-workflow(1), asset-card(1), input-invoice(2) |
| 对话框交互 | 4 | 弹窗表单填写/提交失败 | agency-enterprise(3), input-invoice(1) |

### 未覆盖模块（待后续扩展）

- 折旧计提视图（`/asset/depreciation`） — 占位符
- 资产处置/盘点页面
- 代理会计管理/分配/仪表盘
- 票据管理、现金日记账
- 工资薪酬

## 设计原则
- **Mock 策略**：mockAuth 注入 localStorage + 拦截 userinfo API，按页面 mock 特定 API
- **页面标题**：`.page-title` 类选择器定位
- **断言规范**：每个测试至少 2-3 个断言（标题 + 按钮 + 表格列头或数据）
- **状态流转**：用状态变量工厂函数 + reload 模拟后端状态变更

## 注意事项

- 折旧计提视图 (`/asset/depreciation`) 为占位符，跳过
- 资产处置/盘点页面未覆盖（待后续扩展）
- 代理会计管理/分配/仪表盘未覆盖（待后续扩展）
- 票据管理/现金日记账等未覆盖（待后续扩展）