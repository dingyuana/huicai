# 慧财前端设计规范（Design System）

- 编号：FDS-v0.1（草案待审核）
- 日期：2026-09-13
- 适用范围：`frontend/`（Vue 3 + Element Plus + ECharts + Vite + TS）全部业务页面
- 关联：P67《批量操作交互统一》、REQ-2026-081
- 状态：📝 草案，待老丁审核后执行 A+B 类页面批量改造

---

## 0. 现状问题（本规范要解决的事）

经全项目盘点，当前前端**没有真正生效的设计体系**：

1. `src/styles/index.scss` 仅 28 行，且**未被 `main.ts` 引入**，等于不存在全局样式基线。
2. 无设计 token：主色/成功/警告/危险色以硬编码散落在各页面（`#409eff`×18、`#67c23a`×34、`#e6a23c`×17、`#f56c6c`×23，另有 `#333/#666/#999` 与 Element 的 `#303133/#606266/#909399` 两套灰阶混用）。
3. 47 个列表各自实现"操作列"，行为/样式/位置不一致；部分页面点击行进入详情、部分只能点操作列按钮。
4. 列表页头有 69 处 `page-header` / 64 处 `page-title` 命名但样式各写。

**目标：建立一套可复用、可约束、可回归的设计基线，新增页面必须复用，存量页面分批收敛。**

---

## 1. 设计原则

| 原则 | 含义 |
|------|------|
| 一致性优先 | 同类元素在所有页面外观与交互一致，用户在一个页面学会，处处可用 |
| 直接可达 | 查看/进入详情是最高频动作，点击单据本身即可直达，不依赖操作列 |
| 动作内聚 | 状态推进类动作（提交/审核/记账/删除等）收敛到详情页或详情弹窗，不在列表挤一列按钮 |
| 危险可控 | 不可逆动作必须二次确认；批量动作遵守 P67 规范 |
| 少即是多 | 列表只承载"浏览、筛选、选择、进入"，不承载全部业务操作 |
| 复用组件 | 列表骨架、批量条、结果弹窗、详情容器统一组件化，禁止页内自建重复实现 |

---

## 2. 设计 Token（色彩 / 字体 / 间距 / 圆角 / 阴影）

以 Element Plus 的 CSS 变量为底座，不另造一套色板，通过覆盖 `--el-*` 实现品牌统一。计划沉淀到 `src/styles/tokens.scss` 并在 `main.ts` 引入（当前缺失，见 §8 落地任务）。

### 2.1 品牌与语义色（沿用 Element 默认色板，统一引用，禁止再写裸 hex）

| Token | 值 | 用途 |
|-------|-----|------|
| `--el-color-primary` | `#409eff` | 主操作、链接、选中 |
| `--el-color-success` | `#67c23a` | 成功、已核销、收款方向 |
| `--el-color-warning` | `#e6a23c` | 警告、待处理、部分核销 |
| `--el-color-danger` | `#f56c6c` | 危险、删除、红冲、未核销金额 |
| `--el-color-info` | `#909399` | 次要信息、草稿态 |

### 2.2 文字与边框灰阶（统一到 Element 一套，淘汰 `#333/#666/#999`）

| Token | 值 | 用途 |
|-------|-----|------|
| `--el-text-color-primary` | `#303133` | 主文字 |
| `--el-text-color-regular` | `#606266` | 常规文字 |
| `--el-text-color-secondary` | `#909399` | 次要/占位 |
| `--el-border-color` | `#dcdfe6` | 常规边框 |
| `--el-border-color-lighter` | `#e8e8e8` | 分隔线（现 AppHeader 用） |

### 2.3 背景与表面

| Token | 值 | 用途 |
|-------|-----|------|
| 页面底色 | `#f5f7fa` | AppLayout 内容区背景 |
| 表面/卡片 | `#ffffff` | el-card、表头、弹窗 |
| 危险浅底 | `#fef0f0` | 危险提示背景 |

### 2.4 间距 / 圆角 / 字号

| 类别 | 规范 |
|------|------|
| 间距基数 | 4px 栅格；常用 `4 / 8 / 12 / 16 / 20 / 24` |
| 页面内边距 | 内容区 `16px`（卡片之间 `16px`）；卡片内边距默认 Element |
| 圆角 | 控件 `4px`（Element 默认），卡片 `4px`，弹窗沿用默认 |
| 阴影 | 仅弹窗/抽屉/悬浮用 Element 默认 shadow；普通卡片 `shadow="never"` + 边框 |
| 字号 | 正文 14px；页头标题 16px/600；区块标题 14px/600；辅助 12px |
| 字体栈 | `system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif` |
| 金额 | 等宽对齐、右对齐、保留 2 位小数；正数常规、负/未核销用 danger 色 |

---

## 3. 布局与页面骨架

- 顶层：`AppLayout`（左侧 `AppSidebar` + 顶部 `AppHeader` + 内容区），内容区底色 `#f5f7fa`。
- 标准列表页骨架统一为：

```
el-card(shadow="never")
├─ .page-header        左：页面标题(16/600)   右：主操作按钮组
├─ .filter-form        筛选表单（el-form inline）
├─ 类型 Tabs（可选，含计数）
├─ BatchActionBar      批量操作条（P67，勾选后出现）
├─ el-table            数据表格（点击行进入详情）
└─ .page-pagination    右对齐分页
```

- 页头类名统一使用 `page-header` / `page-title`，样式由全局类提供，页面不再各写。

---

## 4. 列表交互统一（本规范核心，回应"点击单据直接进入"）

### 4.1 页面分类与适用规则

| 类别 | 定义 | 列表交互 | 操作列 |
|------|------|----------|--------|
| **A 业务列表** | 单据/流水/发票/日记账/票据/报销/往来/资产卡片/预算/AI 任务等"有详情概念"的对象 | **点击行 → 进入详情**；主单号列渲染为 `el-link` | **删除操作列**，动作迁到详情 |
| **B 基础资料/系统配置** | 科目/用户/角色/菜单/部门/客户/供应商/员工/银行账户/资产分类/凭证模板/摘要库等 | **点击行 → 打开编辑弹窗/编辑页** | **删除操作列**，编辑/删除迁到行编辑弹窗 |
| **C 工作台/审批面板** | 核销工作台、核销单、核销审批、异常处理、代理工作台、客户对账单、账龄告警、期末结账等"以动作为中心"的面板 | 保留行内动作 | **保留操作列**（无独立详情可进） |
| **D 嵌套子表** | 凭证分录、单据明细、导入预览等表格内子表 | 不适用 | 保留行内增删 |

> 判断口径：**"点进去有没有一个值得看的详情/编辑对象"** —— 有则 A/B（去操作列、点行进），无则 C（保留）。

### 4.2 A 类：点击行进入详情

1. `el-table` 绑定 `@row-click="onRowClick"`，整行可点，行 `cursor: pointer`。
2. 主单号列（如单据号/流水号）渲染为 `el-link type="primary" :underline="false"`，显式表达"可点击进入"。
3. `onRowClick(row, column, event)` **必须排除可交互元素**，避免点按钮/链接/勾选框时误跳转：

```ts
function onRowClick(row: T, _column: unknown, event: Event) {
  const t = event.target as HTMLElement
  if (t.closest('.el-button, .el-link, .el-popconfirm, .el-checkbox, .el-switch, .el-select')) return
  goDetail(row)
}
```

4. 详情形式选择（优先级从高到低）：
   - 已有独立详情**路由/页面**的 → 整页路由跳转（如业务单据 `BusinessDocDetail?id=`）。
   - 已有**详情弹窗/抽屉**且动作较多的 → 保留弹窗（如银行流水）。
   - 新建详情：信息复杂、需要整页布局/再跳转的用**路由页面**；只需快速查看+少量动作的用 **el-drawer/el-dialog**。
5. 原操作列里的状态动作（编辑/提交/审核/记账/删除等）**全部迁入详情页/弹窗**；迁入后逐个核对函数仍被引用，删除列表侧死代码。

### 4.3 B 类：点击行打开编辑

- 基础资料无"只读详情"价值时，点击行直接打开**编辑弹窗**（与"新增/编辑"复用同一弹窗）。
- 删除等危险动作放入编辑弹窗底部或弹窗内，带 `el-popconfirm`。

### 4.4 操作列去留红线

- A/B 类列表**禁止**再出现右侧 fixed 的"操作"列（查看/编辑/删除按钮）。
- C 类保留操作列时，按钮遵守 §5 按钮规范；危险动作（删除/红冲/反核销）必须 `type="danger"` + 二次确认。
- 表格中的"源单号/凭证号"等**关联跳转**链接保留（这是导航，不是操作列）。

### 4.5 大表条件显示规范（防全量加载）

> **原则**：累积型数据（单据/流水/凭证/发票等）**禁止无条件全量展示**历史数据。随业务增长，已完成/已终态记录只增不减，无条件分页仍会全量扫描，导致查询慢、网络阻塞、前端渲染卡顿。

| 规则 | 要求 |
|------|------|
| R1 历史视图必须带日期条件 | `completed`/`vouchered` 等历史归档视图，**必须**提供日期范围（快捷时段或自定义）后才允许查询；无日期条件时**不发起请求**，展示空态提示（`el-empty description="请先选择日期范围…"`） |
| R2 待处理视图不受限 | `pending` 等处理中视图数据量有限（流程未终结），允许默认全量分页 |
| R3 快捷时段 | 历史视图提供"本月 / 近3个月 / 近6个月 / 近12个月"`el-radio-button` 快捷时段，选择即触发查询 |
| R4 日期与快捷互斥 | 手动选择日期范围时清除快捷时段选中态，二者互斥（先选快捷、后改自定义以自定义为准） |
| R5 统计数同步受控 | 页签计数（docType counts / totalCount）与列表同受日期条件约束，无日期时不查询 |
| R6 重置恢复默认 | 重置操作恢复默认时区（待处理）并清空日期条件 |

> 落地基线：BankStatementView（已制证）、BusinessDocList（已完成）已按此规范实现；后续同类大表改造（InvoiceList 历史、VoucherList 历史等）必须套用 R1-R6。

---

## 5. 组件与控件规范

| 控件 | 规范 |
|------|------|
| 按钮 | 主操作 `type="primary"`；批量条按钮用 plain（P67）；列表内动作按钮用 `text size="small"`；危险 `type="danger"`；禁止满屏 solid 彩色按钮 |
| 状态 | 统一用 `el-tag size="small"`，颜色语义固定：草稿 info / 进行中 primary / 待处理 warning / 完成 success / 作废驳回 danger |
| 金额 | 右对齐、2 位小数、千分位；负数或风险金额 danger 色 |
| 表格 | `border stripe`；日期/期间/状态居中，金额右对齐，长文本 `show-overflow-tooltip` |
| 弹窗 | 详情/编辑用 el-dialog，复杂侧滑用 el-drawer；`destroy-on-close` |
| 反馈 | 即时结果 `ElMessage`；批量结果用 P67 `BatchResultDialog`；危险操作 `el-popconfirm`/`el-messagebox` |
| 空状态/加载 | 列表统一 `v-loading`；空数据用 Element 默认空态，不自定义花哨图 |
| 分页 | 右对齐，layout `total, prev, pager, next, jumper` |
| 表单 | 筛选区 `el-form inline`；编辑表单 label 右对齐、必填星号、校验前置 |

---

## 6. 批量操作（引用 P67，不在此重复）

一切含勾选批量操作的列表，必须复用 `components/batch/BatchActionBar.vue` + `composables/useBatchOperation.ts` + `components/batch/BatchResultDialog.vue`，启用语义统一 `every()`、上限 100、危险动作原因必填 + 二次确认。详见 P67 SPEC 与《前端批量操作规范》（待补 `frontend-batch-ops-convention.md`）。

---

## 7. 本期落地范围（A+B 类，C/D 不动）

### 已完成
- `finance/business-doc/BusinessDocList.vue`：去操作列，单据号链接 + 点击行整页进入详情。
- `finance/bank-statement/BankStatementView.vue`：去操作列，动作（含新迁入的删除）收敛进既有详情弹窗。
- `tax/output-invoice/OutputInvoiceList.vue`（Batch1）：发票号改 el-link；行点击加 onRowClick，排除 selection 列与按钮/链接/勾选框等交互元素后进入既有详情弹窗（P67 批量逻辑保持不变）。
- `finance/pending-pool/PendingPool.vue`（Batch1）：去 fixed 操作列；行点击进入「处理」弹窗（el-descriptions 详情 + 处理/预览/删除动作），预览与删除（el-popconfirm）迁入弹窗 footer。

### A 类待改造（约 16 个，分批 PR；本批审计后多数因动作无详情承载而暂缓）
`tax/input-invoice/InputInvoiceList`、`finance/bank-journal/BankJournalList`、`finance/cash-journal/CashJournalList`、`finance/ticket/TicketList`、`arap/ExpenseList`、`finance/prepayment/PrepaymentView`、`arap/prepayment/PrepaymentList`、`arap/bad-debt/BadDebtList`、`arap/purchase-return/PurchaseReturnList`、`arap/customer-statement/CustomerStatementList`、`asset/card/AssetCardList`、`asset/disposal/AssetDisposalList`、`budget/BudgetList`、`budget/AdjustmentList`、`ai/task/AiTaskList`、`ai/anomaly/AnomalyList`

> Batch1 审计结论：上述 16 个页面的操作列动作（submit/approve/reject/delete/genVoucher 等）当前既无详情路由、也无可承载的详情弹窗，仅有「新增/编辑」或动作专用弹窗；需先补详情承载再去操作列，不可硬删导致功能丢失。

> 注：每个页面改造前必须先确认它**是否已有详情页/弹窗**；没有的先补详情承载动作，再去操作列，禁止"先删列导致功能丢失"。

### B 类待改造（约 14 个）
`system/subject/SubjectList`、`system/user/UserList`、`system/role/RoleList`、`system/menu/MenuList`、`system/dept/DeptList`、`system/config/SysConfigList`、`system/voucher-type/VoucherTypeList`、`system/summary-lib/SummaryLibList`、`system/classification-rule/ClassificationRuleList`、`arap/customer/CustomerList`、`arap/vendor/VendorList`、`arap/employee/EmployeeList`、`finance/bank-account/BankAccountList`、`asset/category/AssetCategoryList`

### C 类保留操作列（不改）
核销三面板（Workbench/Settlement/ReconLog）、核销审批、核销异常、账龄告警、客户对账单动作、期末结账、代理机构工作台/分配、系统审计日志等。

---

## 8. 基础设施落地任务（支撑规范生效）

1. 新建 `src/styles/tokens.scss`（`--el-*` 覆盖 + 字体/间距），在 `main.ts` 中于 element-plus 样式之后引入。
2. 修正 `src/styles/index.scss` 并真正引入（或并入 tokens.scss）；提供全局 `.page-header/.page-title/.filter-form/.page-pagination`。
3. 抽取通用列表组合式（可选）：`useRowDetail()` 封装"行点击排除交互元素 + 跳转/弹窗"，消除各页重复的 onRowClick。
4. 新增/编辑页面 Code Review 清单加入：是否裸写 hex、是否自建操作列（A/B 类）、是否复用批量组件。
5. 裸 hex 颜色分批替换为 `var(--el-*)`（存量不强求一次性清完，新代码必须遵守）。

---

## 版本历史

- v0.1 (2026-09-13)：草案。现状盘点 + token + 列表交互统一规则（A/B/C/D 分类）+ 本期改造清单。
- v0.2 (2026-09-13)：老丁批准。Batch1 落地 OutputInvoiceList、PendingPool（去操作列/行点击进入详情承载）；其余 16 个 A 类页面经审计因动作无详情承载暂缓，需先补详情承载。
- v0.3 (2026-09-16)：新增 §4.5 大表条件显示规范（R1-R6）。业务单据列表已完成分区需带日期条件防全量加载，落地 BankStatementView/BusinessDocList。
