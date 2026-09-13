# 前端批量操作规范（Batch Operations Convention）

- 编号：FDS-BATCH-v0.1（草案待审核）
- 日期：2026-09-13
- 关联：[P67 批量操作交互统一 SPEC](../specs/P67-batch-ops-unification.md)、[前端设计规范 FDS](./frontend-design-system.md)、REQ-2026-081
- 适用：`frontend/` 一切含"勾选多行后批量执行"的列表页

---

## 1. 强制复用（禁止页内自建）

任何批量操作列表**必须**复用以下三件套，不得自行实现批量条、结果弹窗、启用判断：

| 资产 | 路径 | 职责 |
|------|------|------|
| `BatchActionBar` | `src/components/batch/BatchActionBar.vue` | 批量按钮渲染、启用门槛（every）、tooltip、已选 N 条、清空 |
| `useBatchOperation` | `src/composables/useBatchOperation.ts` | 编排：空选→上限→原因→二次确认→执行→归一化→结果弹窗→清空+刷新 |
| `BatchResultDialog` | `src/components/batch/BatchResultDialog.vue` | 统一结果反馈：成功 N/总 M + 失败明细表 |

## 2. 行为契约（不可违反）

1. **启用语义统一 `every()`**：所有选中行都支持该动作时按钮才启用。**严禁 `some()` 命中即启用再静默只执行子集**（凭证页历史缺陷：用户选 10 张以为提交 10 张，实际只提交 3 张）。
2. **混合选中**：按钮禁用 + tooltip 说明"当前选中记录不支持「xx」"，不发请求。
3. **空选**：按钮禁用 + tooltip"请先勾选"。
4. **数量上限**：单次 ≤ 100，超出 toast"单次最多批量操作 100 条"，不发请求。
5. **危险动作**（驳回/作废/红冲/反核销/删除）：
   - `needReason`：原因必填，空原因中止；
   - `needConfirm`：二次确认，文案含数量与"不可撤销"。
6. **结果反馈**：统一结果弹窗，部分失败时 warning 态 + 失败行（编号/原因）；成功后清空勾选并 refresh。
7. **作用域**：仅当前页勾选；跨页/全选全部数据为非目标。

## 3. 后端契约适配（适配器归一化）

三种现存后端响应由 `normalizeBatchResult(raw, total)` 消化，前端组件不感知差异：

| 后端契约 | 出现页面 | 归一化处理 |
|----------|----------|-----------|
| `Promise<void>` 全有全无 | 凭证 | 无异常→全部成功；抛错→整批失败 toast，不弹结果窗 |
| BatchResult 计数版 `{total,success,failed:[{id,reason}]}` | 银行流水 | 直接映射 |
| BatchResult 数组版 `{success:[id],failure:[{id,reason}]}` | 销项发票 P56 | 由数组推导计数 |

> 后端 BatchResult 契约统一另立后端任务，本规范不要求改后端。

## 4. 新增批量页面落地步骤

1. 表格加 `type="selection"` 列（需要状态门槛时用 `:selectable`）；
2. 定义 `BATCH_AVAILABLE_BY_STATUS` 状态矩阵：`Record<状态, 允许的 actionKey[]>`；
3. 用 `useBatchOperation({ refresh })` 拿 `selectedRows/onSelectionChange/run/result/resultVisible/clearSelection`；
4. 模板放 `<BatchActionBar :rows :actions @action="...">` 与 `<BatchResultDialog v-model="resultVisible" :result="result">`；
5. 每个 action 调 `run(def, (ids, reason) => api.batchXxx(ids, reason))`。

## 5. Code Review 检查清单

- [ ] 是否复用三件套，无页内自建批量按钮条/结果弹窗
- [ ] 启用判断是否 `every()`（grep 不得出现批量启用用 `.some(`）
- [ ] 危险动作是否 needReason + needConfirm
- [ ] 100 上限是否生效
- [ ] 部分失败是否展示失败明细并清空勾选、刷新
- [ ] 批量动作是否限定当前企业（enterprise 隔离）

## 版本历史

- v0.1 (2026-09-13)：随 P67 提炼，草案待审。
