# SPEC-P138：空数据账套无法结账（前端 `canClose` 误拦）

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已实施并验证**（2026-10-20；企业 4 的 202407 已由后端接口结账为 `closed`） |
| REQ | REQ-2026-154 |
| 前序 | SPEC-P137（结转序列允许「无数据 ⇒ SKIPPED」）、SPEC-P84（结账工作台四步流程） |
| 关联 | AGENTS §1 铁律 #1（人是唯一审核主体）、#10（SPEC→审核→执行）、§4.5 第 40 条（断言/门禁对目标改动不敏感） |

---

## §0 取证（全部实测，2026-10-20）

### 0.1 用户报障

企业 4（山东C）202407，结账工作台第 2 步三步全部「已跳过」，进入第 4 步点击「执行结账」：

```
仍有未记账凭证或结账检查未通过, 无法结账
```

### 0.2 后端事实（`shandong_c` 实测，`GET /api/base/voucher/v1/period-close/check?period=202407`）

| 检查项 | 实测值 |
|---|---|
| 后端 `passed` | **`true`** |
| `issues` | `[]` |
| 未记账凭证（`status != POSTED`） | **0**（45 张全部 `POSTED`） |
| 试算平衡 | 借 608,889.39 = 贷 608,889.39，`balanced=true` |
| 期初连续性 | 上期无数据 ⇒ `checked=false` 跳过，不阻断 |
| 草稿红冲凭证 | 0 |

⇒ **后端从未拒绝结账**，请求根本没发出。

### 0.3 根因（前端守卫与 P137 设计互斥）

`frontend/src/views/finance/period-close/PeriodCloseView.vue:322`：

```ts
const canClose = computed(() =>
  checkResult.value?.passed === true &&
  allVouchers.value.length > 0 &&          // ← 根因
  allVouchers.value.every(v => v.status === 'POSTED'),
)
```

- `allVouchers` = 第 2 步「已生成」的结转凭证；三步全 `SKIPPED` 时为空数组；
- `length > 0` 恒 `false` ⇒ `canClose=false`；
- `onClose()`（:434）在发请求前直接 `ElMessage.warning(...)` 并 `return`。

结论：**没有任何损益/资产数据的账套，永远无法结账**。这与 P137 的既定语义（无数据属正常业务跳过，不视为失败）直接矛盾。

### 0.4 旁证：企业 4 的另一个期间是**真实**阻断（本 SPEC 不修）

`t_period` 中企业 4 还有 202610，`check` 返回：

```
passed=false, issues=["上一会计期间 202609 不存在, 请先初始化期间"]
```

这是**期间断档**，与未记账凭证无关；登记为独立数据/产品问题（见 §7.3）。

---

## §1 需求

### 1.1 行为契约（输入 → 输出）

结账按钮可用的充要条件：

| # | 条件 | 说明 |
|---|---|---|
| 1 | `checkResult.passed === true` | 后端结账检查已通过（唯一权威） |
| 2 | 本次会话**已执行**结转序列（`sequence.length > 0`） | 保留原意图：不允许跳过第 2 步直接结账 |
| 3 | 本次序列**无 `FAILED` 步** | 部分失败时必须人工处理 |
| 4 | 已生成凭证**全部 `POSTED`** | 空集合视为满足（vacuous truth）⇒ 全跳过可结账 |

### 1.2 输入契约

```ts
canClosePeriod(
  checkPassed: boolean | undefined,
  sequenceRan: boolean,
  vouchers: ReadonlyArray<{ status: string }>,
): boolean
```

组件侧：`sequenceRan = sequence.length > 0 && !sequence.some(r => r.status === 'FAILED')`。

### 1.3 输出契约

- `true`：允许点击「执行结账」（仍需用户二次确认，结账动作本身不变）；
- `false`：按钮提示拦截，文案区分三种原因：
  - 检查未通过 / 存在未记账凭证 → `仍有未记账凭证或检查未通过, 结账将被拦截`
  - 检查通过但本期无需生成凭证 → `本期无结转凭证需生成, 结账检查已通过, 可执行结账`
  - 检查通过且已生成并记账 → `全部结转凭证已记账, 可执行结账`

### 1.4 明确排除

- 不改后端 `checkBeforeClose` 规则（本缺陷在后端无问题）；
- 不自动执行结账、不自动记账；
- 不修 202610 期间断档（§7.3 另行处置）；
- 不新增「损益是否已结转」的后端强校验（§7.2 登记为已知局限）。

---

## §2 方案（TDD-First）

1. **新增纯函数** `frontend/src/utils/periodClose.ts`
   - `canClosePeriod(checkPassed, sequenceRan, vouchers)`
   - `closeHint(canClose, voucherCount)`（三种文案）
2. **组件接入** `PeriodCloseView.vue`
   - `canClose` 改为调用 `canClosePeriod(...)`；
   - 新增 `sequenceRan` computed；
   - `:title` 改为 `closeHint(canClose, allVouchers.length)`；
   - `onClose()` 的早退逻辑与文案保持不变（它只消费 `canClose`）。
3. **测试** `frontend/src/__tests__/period-close-guard.test.ts`（先红后绿）
   - 纯函数 5 例 + 组件防回退结构断言 1 例（读源码，禁止 `allVouchers.value.length > 0` 复活）。
4. **反证**
   - 把 `canClosePeriod` 回退为「`vouchers.length > 0`」⇒ AT-138-1 必须转红；还原即绿；
   - 把组件改回内联旧判断 ⇒ AT-138-5 转红。

---

## §3 状态流转

不新增状态机。结账流程仍为四步：检查 → 生成序列 → 审核记账 → 结账；本 SPEC 只修正第 4 步的**准入判断**。

---

## §4 异常处理

| 场景 | 处理 |
|---|---|
| `checkResult === null`（未做检查） | `false` |
| 结转序列未执行（`sequenceRan=false`） | `false`（保留原强制流程） |
| 任一步 `FAILED` | `false`，由用户查看明细后重试 |
| 已生成凭证存在非 `POSTED` | `false` |
| 跨会话遗留的 DRAFT 结转凭证 | 前端不可见 ⇒ 可能放行，但**后端 `check` 以「存在 N 张未记账凭证」拒绝**，不会错结（§7.2） |

---

## §5 BDD 验收（Given-When-Then）

| ID | Given | When | Then |
|---|---|---|---|
| AT-138-1 | 检查通过、序列已执行、0 张凭证（三步全 SKIPPED） | 计算 `canClose` | `true`（**本缺陷回归锁**） |
| AT-138-2 | 检查未通过 | 计算 | `false` |
| AT-138-3 | 序列未执行 | 计算 | `false` |
| AT-138-4 | 已生成 1 张 DRAFT + 1 张 POSTED | 计算 | `false`；两张都 POSTED 后 `true` |
| AT-138-5 | 源码 | 读取 `PeriodCloseView.vue` | `canClose` 必须经 `canClosePeriod`，且不存在 `allVouchers.value.length > 0` |
| AT-138-6 | 三种输入组合 | 调 `closeHint` | 文案分别为 §1.3 的三条，互不串台 |

---

## §6 反证（必须实际执行）

| # | 注入 | 期望 |
|---|---|---|
| F-1 | `canClosePeriod` 恢复 `vouchers.length > 0` | AT-138-1 **转红** |
| F-2 | 组件恢复内联旧判断 | AT-138-5 **转红** |

---

## §7 影响面 / 风险 / 已知局限

### 7.1 影响面
- 仅前端：`PeriodCloseView.vue` + 新增 `utils/periodClose.ts` + 新增 1 个测试文件；
- 后端、DB、Flyway 零改动；
- 已结账期间、有正常结转凭证的期间行为不变（有凭证时条件 4 与原逻辑等价）。

### 7.2 已知局限（如实登记）
1. 前端只看**本次会话生成**的凭证；跨会话遗留 DRAFT 由后端 `check` 拦截，前端无法提前提示；
2. 后端 `checkBeforeClose` **不校验「损益是否已结转」**（只看未记账/试算/资产负债表/期初连续性）⇒ 存在「本期有损益但未结转也能结账」的理论口子。本 SPEC 只修 UI 误拦，**不放宽**该口子（前端条件 2 仍强制本次执行序列）；是否要在后端补强校验，另立 REQ 评估（会影响既有账套结账习惯，需老丁裁定）。

### 7.3 关联但不在本 SPEC 范围
- 企业 4 的 202610：`t_period` 只有 202407/202610，缺 202609 ⇒ 后端拒绝结账（文案准确）。处置建议：初始化 202609（或删除误建的 202610 后重建连续期间），属数据初始化问题，需另行确认。

---

## §8 实施与验证清单

- [x] `npm test` 新增用例先红（TDD RED：`Failed to resolve import "@/utils/periodClose"`）
- [x] 实现 helper + 组件接入（GREEN：6/6）
- [x] F-1 / F-2 反证转红后还原
- [x] `npm test` 全量：P138 6 例全绿；工作区另有 3 例红，全部属未提交的 P136 草稿（回退本次改动后仍红，已自证无关）
- [x] `npm run build`（含 `vue-tsc --noEmit` + validate-api）通过
- [x] 回写登记册 REQ-2026-154 → DONE，更新 AGENTS §0
- [x] 选项 2：`POST /period-close/close?period=202407`（`shandong_c`）→ HTTP 200；`t_period.status=closed`，`t_close_log` id=6 success/operator=9/duration=727ms

### 8.1 反证实测记录

| 注入 | 结果 |
|---|---|
| F-1：`canClosePeriod` 加 `if (vouchers.length === 0) return false` | **AT-138-1 转红**（仅此 1 例），还原后 6/6 绿 |
| F-2：组件 `canClose` 换回内联 `allVouchers.value.length > 0` | **AT-138-5 转红**，还原后 6/6 绿 |
| 无关失败自证：还原本次全部改动后跑 `batch-action-permission.test.ts` + `VoucherList.test.ts` | 仍 **3 红** ⇒ 与 P138 无关（P136 未提交工作区） |


---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: PeriodCloseView
module: finance.period-close
table: null

rules:
  - id: C-01
    source: "P138 §1.1"
    rule: "结账准入 = 后端检查通过 + 本次已执行结转序列 + 无 FAILED 步 + 已生成凭证全部 POSTED；0 张凭证视为满足"
    mapping: { empty_vouchers: allowed, sequence_required: true, failed: forbidden }
    implementation: "frontend/src/utils/periodClose.ts#canClosePeriod"
  - id: C-02
    source: "P138 §1.3"
    rule: "拦截文案必须区分「检查未通过」「本期无需结转凭证」「全部已记账」三种原因"
    mapping: { reason_branch: 3, default: blocked }
    implementation: "frontend/src/utils/periodClose.ts#closeHint"
  - id: C-03
    source: "P138 §7.2"
    rule: "后端 checkBeforeClose 仍是最终权威；前端放行不得绕过未记账凭证/试算平衡等校验"
    mapping: { authority: backend, frontend: advisory }
    implementation: "PeriodCloseServiceImpl#checkBeforeClose（既有）"
  - id: C-04
    source: "P138 §2/§6"
    rule: "回归锁：三步全 SKIPPED 时必须允许结账；回退为 vouchers.length > 0 或用例不敏感视为未修复"
    mapping: { guard: empty_sequence_allowed, falsification: reinstate_length_check }
    implementation: "period-close-guard.test.ts"

acceptance_tests:
  - id: AT-138-1
    ref: "period-close-guard.test.ts#allSkippedAllowsClose"
  - id: AT-138-2
    ref: "period-close-guard.test.ts#checkFailedBlocksClose"
  - id: AT-138-3
    ref: "period-close-guard.test.ts#sequenceNotRunBlocksClose"
  - id: AT-138-4
    ref: "period-close-guard.test.ts#unpostedVoucherBlocksClose"
  - id: AT-138-5
    ref: "period-close-guard.test.ts#componentUsesHelperGuard"
  - id: AT-138-6
    ref: "period-close-guard.test.ts#closeHintBranches"
