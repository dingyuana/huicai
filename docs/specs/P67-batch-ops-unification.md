---
标题: P67 批量操作交互统一（三页面对齐 + 可复用组件沉淀）
编号: P67
版本: v1.0 (2026-09-13)
关联PRD: 通用前端规范（无既有 PRD，登记 REQ-2026-081）
状态: 📝 草案待审核
关联SPEC: P56（销项批量操作，最完整参照）、P55（银行流水批量）、P57-declare-status-split（凭证状态拆分）
test_ref: 待实施（前端 npm run build + 手工验证清单；不改后端）
预估工时: 10h（组件/composable 3h + 三页面改造 5h + 规范文档与验证 2h）

## 背景

三个高频页面的批量操作各自实现，风格与行为不一致：

| 维度 | 凭证 VoucherList | 银行流水 BankStatementView | 销项发票 OutputInvoiceList（P56，最新最完整） |
|------|-----------------|--------------------------|---------------------------------------------|
| 位置 | 筛选表单下方独立 batch-bar | 顶部工具栏 el-space | 页头 el-space + "已选 N 条" tag |
| 按钮样式 | text 纯文本按钮 | solid success/warning | plain small + 语义 type 配置 |
| 启用语义 | `some()` 命中即启用，客户端静默过滤子集执行（用户以为提交 10 张实际 3 张）| 仅判非空，无状态门槛 | `every()` 全部满足才启用 + tooltip 说明原因 |
| 状态矩阵 | isBatchable 勾选拦截 | 无 | BATCH_AVAILABLE_BY_STATUS 显式矩阵 |
| 危险确认 | 无 | 无 | reject/void/reverse 原因必填 + void/reverse 二次确认 |
| 结果反馈 | 仅成功 toast，无失败明细 | 结果弹窗 `{total,success,failed:[{id,reason}]}` | 结果弹窗 `{success:[id],failure:[{id,reason}]}` + toast |
| API 契约 | `Promise<void>` 全有全无 | BatchResult(计数版) | BatchResult(数组版) |
| 数量上限 | 无 | 无 | 100 条 |

核心问题：凭证页 `some()` 静默子集执行是**行为缺陷**（违反可预期性）；三套结果契约导致每个页面重复造弹窗；危险操作（批量审核/记账）无二次确认。

## 方案总览（以 P56 模式为基准提炼，组件化沉淀）

### 1. 新增共享组件 `src/components/batch/BatchActionBar.vue`

- Props：
  - `selectedCount: number`
  - `actions: BatchActionDef[]`：`{ key, label, type?: 'primary'|'warning'|'danger'（默认 primary plain）、needReason?: boolean、needConfirm?: boolean、statusMatrix: Record<string, string[]>、maxCount?: number（默认 100）}`
  - `rows: T[]`（选中行，用于状态判断）
- 行为：已选 0 时整体禁用并显示"请先勾选"；启用语义统一 `every()`（全部选中行支持该操作），不满足时 tooltip 显示"当前选中记录不支持「xx」"；右侧固定"已选 N 条"tag + "清空"按钮
- Emits：`action(key: string)`（仅做门槛判断，业务调用交给 composable）

### 2. 新增共享 composable `src/composables/useBatchOperation.ts`

- 入参：`{ refresh: () => Promise<void> | void }`
- 返回：`{ selectedRows, onSelectionChange, run(def, executor), result, resultVisible, clearSelection }`
- `run(def, executor)` 统一编排：空选校验 → 数量上限校验 → `needReason` 弹原因输入（必填）→ `needConfirm` 弹确认（danger 文案含数量与"不可撤销"）→ 调 `executor(ids, reason)` → 结果归一化 → 弹统一结果弹窗 → 成功后清空选择 + refresh

### 3. 新增共享组件 `src/components/batch/BatchResultDialog.vue`

- 统一结果模型（前端适配器归一化）：`{ total, successCount, failures: Array<{ id, no?, reason }> }`
- 弹窗：el-result 图标（全成功 success / 有失败 warning）+ "成功 N / 总 M 条" + 失败明细表（编号/原因）
- 适配器 `normalizeBatchResult(raw)`：兼容两种现存后端契约（银行流水计数版 / 销项数组版）；凭证 `Promise<void>` 视为"全部成功"

## 三页面改造

1. **VoucherList**：text 按钮条 → BatchActionBar；`some()` 静默过滤 → `every()` 门槛（状态矩阵：DRAFT→提交；SUBMITTED→审核；AUDITED→记账）；批量记账加 needConfirm；接入结果弹窗（void 契约暂为全有全无，归一化为全成功/整批报错）
2. **BankStatementView**：solid 按钮 → BatchActionBar；补状态矩阵（PENDING/classified→批量确认；CONFIRMED→批量审核，实现时以实际 reviewStatus 枚举对证）；接入统一 composable 与结果弹窗（替换现私有弹窗）
3. **OutputInvoiceList**：逻辑不变，迁入 BatchActionBar + useBatchOperation（删除页内重复实现），保留其状态矩阵/原因/确认/100 上限语义

## 输入契约

- 仅前端改动；不改任何后端端点与响应结构（两种 BatchResult 由适配器消化，后端契约统一列为非目标）
- 新增文件：`src/components/batch/BatchActionBar.vue`、`src/components/batch/BatchResultDialog.vue`、`src/composables/useBatchOperation.ts`

## 输出契约

- 三个页面批量操作交互完全一致：位置（页头右侧）、按钮样式（plain 语义色）、启用语义（every+tooltip）、危险确认（原因必填+二次确认）、结果反馈（统一弹窗+toast）、上限（100）
- 新增规范文档 `docs/development/frontend-batch-ops-convention.md`：后续一切含批量操作的列表页（含 Agency batch、费用报销等）必须复用 BatchActionBar + useBatchOperation，禁止页内自建
- `npm run build` 通过；三页面手工验证清单全过

## 状态流转

不涉及业务实体状态机变更（批量操作仍是各既有端点，人工触发，符合铁律 #1）。

## 异常处理

| 场景 | 处理 |
|------|------|
| 未勾选点击批量按钮 | 按钮禁用 + tooltip"请先勾选" |
| 选中行状态混合（部分支持部分不支持） | 按钮禁用 + tooltip 说明，不静默过滤 |
| 超过 100 条 | toast 警告"单次最多批量操作 100 条"，不发请求 |
| 原因为空确认取消 | 中止操作，无请求 |
| executor 抛错 | toast 后端 msg，结果弹窗不弹（全有全无契约） |
| 部分失败 | 结果弹窗 warning 态 + 失败明细，刷新列表保留成功结果 |

## BDD（手工验证清单，前端无单测基建）

### 场景 1: 混合选中不静默执行
- Given 勾选 1 张 DRAFT + 1 张 SUBMITTED 凭证
- When 查看批量提交按钮
- Then 按钮禁用，tooltip 说明含 SUBMITTED 记录不支持
- And 不发出任何请求

### 场景 2: 凭证批量记账二次确认
- Given 勾选 3 张 AUDITED 凭证
- When 点击批量记账
- Then 弹确认框"确认对 3 张凭证执行【批量记账】？"
- And 确认后执行并弹结果"成功 3 / 总 3 条"

### 场景 3: 销项批量作废原因必填
- Given 勾选 2 张 PENDING_CONFIRM 发票
- When 批量作废，原因为空点确定
- Then 提示"原因不能为空"，不执行

### 场景 4: 银行流水部分失败明细
- Given 勾选 3 条流水，其中 1 条状态已变化
- When 批量确认
- Then 结果弹窗显示"成功 2 / 总 3 条"及失败行原因
- And 列表刷新，勾选清空

### 场景 5: 超上限拦截
- Given 勾选 101 条（跨页累加场景若不支持则跳过）
- When 执行批量操作
- Then toast 警告上限，无请求

### 场景 6: 风格一致性
- Given 打开三个页面
- When 勾选记录
- Then 批量按钮位置/样式/"已选 N 条"/清空/结果弹窗视觉与行为一致

## 非目标

- 后端 BatchResult 契约统一（另立后端任务）
- 跨页勾选/全选全部数据（当前仅当前页）
- 业务实体状态机变更
- BusinessDocList.vue 既有未提交改动的处理（工作区已有，与本任务无关）

## 版本历史

- v1.0 (2026-09-13): 草案创建，待老丁审核
