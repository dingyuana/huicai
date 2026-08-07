# P56 — 销项发票批量操作（增量化改造）

> 日期：2026-08-07 | 触发：用户反馈 `/tax/output-invoice` 模块单条操作效率低
> 关联 SPEC：docs/specs/P21-sales-invoice-state-machine.md（V1.5，状态机基础）
> 工期：2 个微循环，2 commit 估算（后端 1 + 前端 1）
> 风险等级：P1（不修改状态机内部，仅做包装层）

---

## §0 任务来源

### 0.1 用户原话

> http://129.211.7.254:3001/tax/output-invoice 对这个界面模块的增加操作

### 0.2 现状调研

| 维度 | 现状 | 缺口 |
|---|---|---|
| 后端端点 | TaxController 已实现 7 个单笔状态机端点（行 204-260） | 缺批量端点 |
| 状态机 | OutputInvoiceStateMachineServiceImpl 实现 7 个状态方法（行 73-226） | 不修改，仅在 Service 层包装 |
| Service 接口 | TaxService 57 行，未声明 batch 方法 | 新增 7 个 batch 接口方法 |
| DTO 体系 | 已有 VoucherStatusDTO 模板（凭证批量用） | 复用/扩展出带 reason 的批量 DTO |
| 前端表格 | OutputInvoiceList.vue 行 114，缺 `type="selection"` 列 | 增选择列 + 批量按钮组 |
| 前端 API | frontend/src/api/modules/tax.ts 143 行，无 batch 函数 | 增 7 个 batch API |

### 0.3 设计原则

1. **不修改状态机内部**：OutputInvoiceStateMachineServiceImpl 行 73-226 保持原样，batch 入口仅在 TaxService 包装层
2. **best-effort 模式**：单条失败不影响其他，前端拿到明细显示
3. **复用现有 DTO 模式**：参考 VoucherStatusDTO（凭证批量）
4. **审计自动生效**：P24 StatusChangeAspect 自动拦截 updateById 写 t_audit_log

---

## §1 任务范围

### 1.1 必须做（本次 P56）

| # | 模块 | 内容 | 文件 |
|:--:|:--:|:---|:---|
| 1 | 后端 DTO | 新增 OutputInvoiceBatchDTO { ids, reason? } | 新建 `tax/dto/OutputInvoiceBatchDTO.java` |
| 2 | 后端 DTO | 新增 BatchOperationResult { success, failure } | 新建 `tax/dto/BatchOperationResult.java` |
| 3 | 后端 Service 接口 | TaxService 新增 7 个 batch 方法签名 | `tax/service/TaxService.java` |
| 4 | 后端 Service 实现 | TaxServiceImpl 7 个 batch 方法实现（循环 + 收集） | `tax/service/impl/TaxServiceImpl.java` |
| 5 | 后端 Controller | TaxController 新增 7 个 batch 端点 | `tax/controller/TaxController.java` |
| 6 | 前端 API | tax.ts 新增 7 个 batch 函数 | `frontend/src/api/modules/tax.ts` |
| 7 | 前端页面 | OutputInvoiceList.vue 加选择列 + 动态批量按钮 + 失败明细弹窗 | `frontend/src/views/tax/output-invoice/OutputInvoiceList.vue` |
| 8 | 测试 | TaxServiceImplTest 新增 batch 单元测试 | `tax/service/impl/TaxServiceImplTest.java` |

### 1.2 不做（明确范围外）

- 不改 OutputInvoiceStateMachineServiceImpl 任何内部逻辑（保持 P21 状态机原状）
- 不做跨模块批量（不进项 + 销项混批，仅销项）
- 不做异步批量（同步返回结果，规模 ≤100 条/批，前端分页限制）
- 不做断点续传（失败重试由用户在前端重新发起）
- 不改前端单笔操作按钮（行 150-164 保持原样）
- 不新增数据库表/字段（零 schema 变更，零 Flyway migration）
- 不批量生成凭证（已有的 `batch-generate-voucher` 端点 TaxController 行 313-323 已覆盖）

---

## §2 后端实现（微循环 1）

### 2.1 新建 DTO：OutputInvoiceBatchDTO

文件：`backend/src/main/java/com/huicai/sme/tax/dto/OutputInvoiceBatchDTO.java`

```java
package com.huicai.sme.tax.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 销项发票批量操作 DTO（P56）
 *
 * 用途：批量提交审核/审核通过/驳回/回退/作废/生成凭证/红冲
 */
@Data
public class OutputInvoiceBatchDTO {

    @NotEmpty(message = "发票ID列表不能为空")
    @Size(max = 100, message = "单次批量最多 100 张发票")
    private List<Long> ids;

    /** 驳回/作废/红冲原因（其它操作可为空） */
    private String reason;
}
```

### 2.2 新建 DTO：BatchOperationResult

文件：`backend/src/main/java/com/huicai/sme/tax/dto/BatchOperationResult.java`

```java
package com.huicai.sme.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 批量操作结果（best-effort 模式，P56）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchOperationResult {

    /** 成功 ID 列表 */
    private List<Long> success;

    /** 失败明细 */
    private List<FailureDetail> failure;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FailureDetail {
        private Long id;
        private String reason;
    }
}
```

### 2.3 TaxService 接口扩展

文件：`backend/src/main/java/com/huicai/sme/tax/service/TaxService.java`，末尾追加：

```java
// ==================== 销项发票批量操作（P56）====================

BatchOperationResult batchSubmitForReview(List<Long> ids, Long userId);
BatchOperationResult batchConfirm(List<Long> ids, Long userId);
BatchOperationResult batchReject(List<Long> ids, Long userId, String reason);
BatchOperationResult batchRevert(List<Long> ids, Long userId);
BatchOperationResult batchMarkVouchered(List<Long> ids, Long userId);
BatchOperationResult batchVoid(List<Long> ids, Long userId, String reason);
BatchOperationResult batchReverse(List<Long> ids, Long userId, String reason);
```

### 2.4 TaxServiceImpl 实现

实现模板（以 `batchSubmitForReview` 为例，6 个 batch 方法同构）：

```java
@Override
@Transactional(propagation = Propagation.NEVER)  // 不开外层事务，让每条单笔事务独立
public BatchOperationResult batchSubmitForReview(List<Long> ids, Long userId) {
    List<Long> success = new ArrayList<>();
    List<BatchOperationResult.FailureDetail> failure = new ArrayList<>();
    for (Long id : ids) {
        try {
            stateMachineService.submitForReview(id, userId);  // 内部 @Transactional
            success.add(id);
        } catch (BusinessException e) {
            failure.add(new BatchOperationResult.FailureDetail(id, e.getMessage()));
            log.warn("批量提交审核失败: id={}, reason={}", id, e.getMessage());
        } catch (Exception e) {
            failure.add(new BatchOperationResult.FailureDetail(id, "系统异常: " + e.getMessage()));
            log.error("批量提交审核系统异常: id={}", id, e);
        }
    }
    return new BatchOperationResult(success, failure);
}
```

关键点：
- `Propagation.NEVER`：外层 Service 方法**不**开事务，让每次状态机调用独立提交（best-effort 模式）
- 单笔状态机方法本身已有 `@Transactional`（OutputInvoiceStateMachineServiceImpl 行 71/84/105/122/135/150/167）
- 异常捕获只针对 `BusinessException`（业务错误）和通用 `Exception`（系统错误），不捕获 `Error`
- 失败原因直接透传状态机抛出的 message（已含"仅待确认状态可提交审核，当前: X"等业务文案）

### 2.5 TaxController 端点

文件：`backend/src/main/java/com/huicai/sme/tax/controller/TaxController.java`，在第 323 行 `batchGenerateVoucher` 之后追加：

```java
// ========== 销项发票批量操作 (P56) ==========
@Operation(summary = "批量提交审核 (PENDING_CONFIRM → PENDING_REVIEW)")
@PostMapping("/output-invoices/batch/submit-review")
public R<BatchOperationResult> batchSubmitReview(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    return R.ok(service.batchSubmitForReview(dto.getIds(), orDefault(null)));
}

@Operation(summary = "批量审核通过 (PENDING_REVIEW → CONFIRMED + 业务单+凭证)")
@PostMapping("/output-invoices/batch/confirm")
public R<BatchOperationResult> batchConfirm(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    return R.ok(service.batchConfirm(dto.getIds(), orDefault(null)));
}

@Operation(summary = "批量驳回 (PENDING_REVIEW → PENDING_CONFIRM)")
@PostMapping("/output-invoices/batch/reject")
public R<BatchOperationResult> batchReject(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    if (dto.getReason() == null || dto.getReason().isBlank()) {
        return R.fail("驳回必须填写原因");
    }
    return R.ok(service.batchReject(dto.getIds(), orDefault(null), dto.getReason()));
}

@Operation(summary = "批量回退 (CONFIRMED → PENDING_REVIEW)")
@PostMapping("/output-invoices/batch/revert")
public R<BatchOperationResult> batchRevert(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    return R.ok(service.batchRevert(dto.getIds(), orDefault(null)));
}

@Operation(summary = "批量生成凭证 (CONFIRMED → VOUCHERED)")
@PostMapping("/output-invoices/batch/mark-vouchered")
public R<BatchOperationResult> batchMarkVouchered(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    return R.ok(service.batchMarkVouchered(dto.getIds(), orDefault(null)));
}

@Operation(summary = "批量作废 (任意非终态 → VOIDED)")
@PostMapping("/output-invoices/batch/void")
public R<BatchOperationResult> batchVoid(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    if (dto.getReason() == null || dto.getReason().isBlank()) {
        return R.fail("作废必须填写原因");
    }
    return R.ok(service.batchVoid(dto.getIds(), orDefault(null), dto.getReason()));
}

@Operation(summary = "批量红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → REVERSED + 红字发票)")
@PostMapping("/output-invoices/batch/reverse")
public R<BatchOperationResult> batchReverse(@Valid @RequestBody OutputInvoiceBatchDTO dto) {
    if (dto.getReason() == null || dto.getReason().isBlank()) {
        return R.fail("红冲必须填写原因");
    }
    return R.ok(service.batchReverse(dto.getIds(), orDefault(null), dto.getReason()));
}
```

注意：userId 通过 `orDefault(null)` 走 `SecurityUtils.getCurrentUserId()` fallback（行 264 一致）。

### 2.6 后端单元测试

如已存在 `TaxServiceImplTest.java` 则追加，否则新建 `backend/src/test/java/com/huicai/sme/tax/service/impl/TaxServiceImplTest.java`：

```java
@Test
void testBatchSubmitForReview_allSuccess() {
    when(invoiceMapper.selectById(1L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.selectById(2L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.selectById(3L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.updateById(any())).thenReturn(1);

    BatchOperationResult result = service.batchSubmitForReview(List.of(1L, 2L, 3L), 100L);

    assertEquals(3, result.getSuccess().size());
    assertEquals(0, result.getFailure().size());
}

@Test
void testBatchSubmitForReview_partialFailure() {
    when(invoiceMapper.selectById(1L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.selectById(2L)).thenReturn(buildInvoice("VOUCHERED"));  // 状态不符
    when(invoiceMapper.selectById(3L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.updateById(any())).thenReturn(1);

    BatchOperationResult result = service.batchSubmitForReview(List.of(1L, 2L, 3L), 100L);

    assertEquals(2, result.getSuccess().size());
    assertEquals(1, result.getFailure().size());
    assertEquals(2L, result.getFailure().get(0).getId());
    assertTrue(result.getFailure().get(0).getReason().contains("仅待确认状态"));
}

@Test
void testBatchSubmitForReview_systemException() {
    when(invoiceMapper.selectById(1L)).thenThrow(new RuntimeException("DB connection lost"));
    when(invoiceMapper.selectById(2L)).thenReturn(buildInvoice("PENDING_CONFIRM"));
    when(invoiceMapper.updateById(any())).thenReturn(1);

    BatchOperationResult result = service.batchSubmitForReview(List.of(1L, 2L), 100L);

    assertEquals(1, result.getSuccess().size());
    assertEquals(1, result.getFailure().size());
    assertTrue(result.getFailure().get(0).getReason().contains("系统异常"));
}
```

### 2.7 后端验证清单

- [ ] `mvn compile` 通过
- [ ] `mvn test -Dtest=TaxServiceImplTest` 全过
- [ ] `mvn test` 全过（必须 0 failures, 0 errors）
- [ ] pre-commit hook `check-entity-schema.mjs` 通过（无 Entity 变更）
- [ ] 启动后端 `mvn spring-boot:run`，curl 测试 7 个端点

---

## §3 前端实现（微循环 2）

### 3.1 API 模块（tax.ts）

文件：`frontend/src/api/modules/tax.ts`，末尾追加：

```typescript
// ====== P56 销项发票批量操作 ======
export interface BatchFailure {
  id: number
  reason: string
}
export interface BatchResult {
  success: number[]
  failure: BatchFailure[]
}

export function batchSubmitForReview(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/submit-review', { ids })
}
export function batchConfirmOutputInvoice(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/confirm', { ids })
}
export function batchRejectOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/reject', { ids, reason })
}
export function batchRevertOutputInvoice(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/revert', { ids })
}
export function batchMarkVouchered(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/mark-vouchered', { ids })
}
export function batchVoidOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/void', { ids, reason })
}
export function batchReverseOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/reverse', { ids, reason })
}
```

### 3.2 OutputInvoiceList.vue 改造

#### 3.2.1 增 el-table 选择列

行 114 `<el-table :data="list" v-loading="loading" border>` 内首列加：

```vue
<el-table-column type="selection" width="50" :selectable="rowSelectable" />
```

`rowSelectable` 函数：仅非终态发票可被选择（VOIDED/REVERSED/FULLY_RECONCILED 不可选）：

```typescript
const TERMINAL_STATUSES = ['VOIDED', 'REVERSED', 'FULLY_RECONCILED']
const rowSelectable = (row: any) => !TERMINAL_STATUSES.includes(row.status)
```

#### 3.2.2 增工具栏批量按钮组

行 6-9 `<el-space>` 内追加（在「导入发票」「新增发票」按钮之后）：

```vue
<template v-if="selectedRows.length > 0">
  <el-tag type="info" effect="plain">已选 {{ selectedRows.length }} 条</el-tag>
  <el-button
    v-if="canBatch('submitReview')"
    @click="onBatchAction('submitReview')" type="primary" plain size="small">
    批量提交审核
  </el-button>
  <el-button
    v-if="canBatch('confirm')"
    @click="onBatchAction('confirm')" type="primary" plain size="small">
    批量审核通过
  </el-button>
  <el-button
    v-if="canBatch('reject')"
    @click="onBatchAction('reject')" type="warning" plain size="small">
    批量驳回
  </el-button>
  <el-button
    v-if="canBatch('revert')"
    @click="onBatchAction('revert')" type="warning" plain size="small">
    批量回退
  </el-button>
  <el-button
    v-if="canBatch('markVouchered')"
    @click="onBatchAction('markVouchered')" type="primary" plain size="small">
    批量生成凭证
  </el-button>
  <el-button
    v-if="canBatch('void')"
    @click="onBatchAction('void')" type="danger" plain size="small">
    批量作废
  </el-button>
  <el-button
    v-if="canBatch('reverse')"
    @click="onBatchAction('reverse')" type="danger" plain size="small">
    批量红冲
  </el-button>
</template>
```

#### 3.2.3 增失败明细弹窗

在 `importVisible` 对话框之后追加：

```vue
<!-- 批量操作失败明细 -->
<el-dialog v-model="batchResultVisible" title="批量操作结果" width="640px">
  <el-alert
    v-if="batchResult"
    :type="(batchResult.failure?.length || 0) > 0 ? 'warning' : 'success'"
    :closable="false"
    style="margin-bottom:12px"
  >
    成功 {{ batchResult.success?.length || 0 }} 条，失败 {{ batchResult.failure?.length || 0 }} 条
  </el-alert>
  <el-table v-if="(batchResult?.failure?.length || 0) > 0" :data="batchResult.failure" border size="small">
    <el-table-column prop="id" label="发票ID" width="120" />
    <el-table-column prop="reason" label="失败原因" />
  </el-table>
  <template #footer>
    <el-button @click="batchResultVisible = false">关闭</el-button>
  </template>
</el-dialog>
```

#### 3.2.4 script 段增状态/方法

```typescript
// ====== P56 批量操作状态 ======
const selectedRows = ref<any[]>([])
const batchResultVisible = ref(false)
const batchResult = ref<BatchResult | null>(null)

const TERMINAL_STATUSES = ['VOIDED', 'REVERSED', 'FULLY_RECONCILED']
const rowSelectable = (row: any) => !TERMINAL_STATUSES.includes(row.status)

/** 单条状态可执行的批量操作（按状态过滤） */
const BATCH_AVAILABLE_BY_STATUS: Record<string, string[]> = {
  PENDING_CONFIRM: ['submitReview', 'void'],
  PENDING_REVIEW: ['confirm', 'reject', 'void'],
  CONFIRMED: ['markVouchered', 'revert', 'void'],
  VOUCHERED: ['reverse'],
  PARTIALLY_RECONCILED: ['reverse'],
  FULLY_RECONCILED: [],  // 不可操作
  VOIDED: [],
  REVERSED: [],
}

const canBatch = (action: string) => {
  if (selectedRows.value.length === 0) return false
  // 仅当所有选中行都支持该操作时才显示按钮
  return selectedRows.value.every(r => BATCH_AVAILABLE_BY_STATUS[r.status]?.includes(action))
}

const onSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
}

const onBatchAction = async (action: string) => {
  const ids = selectedRows.value.map(r => r.id).filter(Boolean)
  if (ids.length === 0) {
    ElMessage.warning('请先选择发票')
    return
  }
  if (ids.length > 100) {
    ElMessage.warning('单次最多批量操作 100 张')
    return
  }

  // 需填原因的操作
  let reason = ''
  if (['reject', 'void', 'reverse'].includes(action)) {
    const labelMap: any = { reject: '批量驳回', void: '批量作废', reverse: '批量红冲' }
    const { value } = await (await import('element-plus')).ElMessageBox.prompt(
      `请输入${labelMap[action]}原因（将应用于所有选中发票）`,
      labelMap[action],
      { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
    ).catch(() => ({ value: null }))
    if (!value) return
    reason = value
  }

  // 风险确认（红冲不可逆）
  if (action === 'reverse' || action === 'void') {
    const label = action === 'reverse' ? '批量红冲' : '批量作废'
    try {
      await (await import('element-plus')).ElMessageBox.confirm(
        `确认对 ${ids.length} 张发票执行【${label}】？此操作不可撤销。`,
        label,
        { type: 'warning' }
      )
    } catch { return }
  }

  const fnMap: any = {
    submitReview: () => batchSubmitForReview(ids),
    confirm: () => batchConfirmOutputInvoice(ids),
    reject: () => batchRejectOutputInvoice(ids, reason),
    revert: () => batchRevertOutputInvoice(ids),
    markVouchered: () => batchMarkVouchered(ids),
    void: () => batchVoidOutputInvoice(ids, reason),
    reverse: () => batchReverseOutputInvoice(ids, reason),
  }

  try {
    batchResult.value = await fnMap[action]()
    batchResultVisible.value = true
    const succ = batchResult.value.success.length
    const fail = batchResult.value.failure.length
    if (fail === 0) {
      ElMessage.success(`批量操作完成：成功 ${succ} 条`)
    } else {
      ElMessage.warning(`批量操作完成：成功 ${succ} 条，失败 ${fail} 条（详见弹窗）`)
    }
    selectedRows.value = []
    fetchData(); fetchStats()
  } catch {
    // backend handles error
  }
}
```

#### 3.2.5 el-table 加 selection-change 监听

行 114 的 `<el-table>` 标签上增加：

```vue
<el-table :data="list" v-loading="loading" border @selection-change="onSelectionChange">
```

### 3.3 前端验证清单

- [ ] `npm run typecheck`（如项目有）通过
- [ ] `npm run build` 通过
- [ ] 浏览器手动验证：
  - [ ] 列表加载，选择列显示且 VOIDED/REVERSED 行不可选
  - [ ] 选中 1 张 PENDING_CONFIRM 发票 → 仅显示「批量提交审核」「批量作废」按钮
  - [ ] 选中混合状态 → 所有批量按钮隐藏（避免误操作）
  - [ ] 点击「批量提交审核」→ 调后端 → 弹窗显示成功 N 条，失败 M 条（如有）
  - [ ] 点击「批量驳回」→ 弹原因输入框 → 提交后调后端
  - [ ] 点击「批量作废」→ 弹原因 + 风险确认 → 提交后调后端
- [ ] 失败明细表格：列出每张失败发票的 ID + 原因

---

## §4 BDD 验收场景（4 段模板精简版）

### 4.1 输入契约

| 操作 | 入参 | 约束 |
|---|---|---|
| 7 个 batch 端点 | `OutputInvoiceBatchDTO { ids: List<Long>, reason?: String }` | ids 非空 + ≤100；reject/void/reverse 必须带 reason |
| 前端批量按钮 | `onSelectionChange` 回调 | 仅非终态行可选 |

### 4.2 输出契约

| 端点 | 成功 | 失败 |
|---|---|---|
| 7 个 batch 端点 | `R<BatchOperationResult>` 含 success + failure 列表 | 业务异常：单条失败计入 failure；系统异常：计入 failure 含"系统异常"前缀 |
| 前端弹窗 | 显示成功/失败条数 + 失败明细表 | ElMessage 错误提示 |

### 4.3 状态流转（包装层，不改状态机）

| Batch 操作 | 单条状态机方法 | 前置状态 → 目标状态 |
|---|---|---|
| batchSubmitForReview | submitForReview | PENDING_CONFIRM → PENDING_REVIEW |
| batchConfirm | confirm | PENDING_REVIEW → CONFIRMED + 创建业务单+凭证 |
| batchReject | reject | PENDING_REVIEW → PENDING_CONFIRM |
| batchRevert | revertToReview | CONFIRMED → PENDING_REVIEW |
| batchMarkVouchered | markVouchered | CONFIRMED → VOUCHERED |
| batchVoid | voidInvoice | 任意非终态 → VOIDED |
| batchReverse | reverseInvoice | CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → REVERSED + 红字发票 |

### 4.4 异常处理

| 场景 | 处理 | 错误码 |
|---|---|---|
| ids 为空 | `@NotEmpty` 校验失败 | 400 |
| ids 数量 > 100 | `@Size(max=100)` 校验失败 | 400 |
| reason 为空（reject/void/reverse） | Controller 层 `R.fail` | 400 |
| 单条状态不符 | 状态机抛 BusinessException → 计入 failure | 200（best-effort） |
| 单条系统异常 | catch Exception → 计入 failure 含"系统异常" | 200（best-effort） |
| 单条凭证生成失败 | 状态机内部 catch → 计入 failure | 200（best-effort） |

---

## §5 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| 大批量操作超时 | 中 | 限制 100 条/批；超过前端分页 |
| 业务凭证生成连锁失败 | 中 | 状态机内部已有 catch，失败计入 failure 不影响其他 |
| 用户误操作红冲 | 高 | 前端弹「风险确认」弹窗 + 必填原因 |
| 状态机被改导致 batch 行为变化 | 低 | batch 是包装层，状态机行为变更自动适配 |
| 测试覆盖不足 | 中 | 至少 3 个核心 batch 单元测试 + curl 7 个端点验证 |

---

## §6 实施时间表

| 微循环 | 内容 | 估算 | commit |
|:---:|:---|:---:|:---:|
| MC56-1 | 后端 DTO + Service 接口 + Service 实现 + Controller 端点 | 2h | commit 1: `feat: 销项发票批量操作后端 7 端点` |
| MC56-2 | 前端 API + 选择列 + 动态按钮 + 失败明细弹窗 | 2h | commit 2: `feat: 销项发票批量操作前端 UI` |
| 合计 | | **4h** | **2 commit** |

---

## §7 关联文档

- 主 SPEC：docs/specs/P21-sales-invoice-state-machine.md（V1.5，状态机基础）
- 业务 SPEC：docs/specs/P34-receivable-payable-to-businessdoc.md（P34 业务单据）
- 任务书模板：docs/development/tasks/P27-businessdoc-name-fix_任务书.md
- 凭证批量参考：backend/src/main/java/com/huicai/base/voucher/dto/VoucherStatusDTO.java
- 凭证批量端点参考：backend/src/main/java/com/huicai/base/voucher/controller/VoucherController.java 行 76-105
- 需求登记册：docs/development/requirements/REQUIREMENTS_REGISTRY.md 新增 REQ-2026-076

