# SPEC: 大表列表页 scope 分区与已完成视图条件显示（批量改造）

## 背景
> **test_ref**：TaxServiceImplTest, TaxControllerTest, TaxApiContractTest

`InputInvoiceList`、`VoucherList`、`ReceivableList`、`PayableList`、`ExpenseList`、`TicketList`、`PrepaymentView`、`InvoiceReconcileView`、`AuditLogList` 共 9 个累积型大表页面，当前无条件全量分页展示历史数据。依据 `docs/development/frontend-design-system.md` §4.5（R1-R6），对齐 `BusinessDocList` / `OutputInvoiceList` 已落地的待处理/已完成分区模式。

> **Backend 基线**：`BusinessDocServiceImpl.pageQuery` 已内置 `scope`/`startDate`/`endDate` 支持 + 日期优先 period 退让（见 `docs/specs/P-OUTPUTINVOICE-LIST.md`）。AR/AP 的 `ArapController.pageReceivable/pagePayable` 仅需透传参数。

## 变更内容

| # | 页面 | 模块 | 实体状态全集 | 终态集 (completed) | 非终态 (pending) | 日期字段 | 后端改动量 |
|---|------|------|-------------|-------------------|----------------|---------|-----------|
| 1 | InputInvoiceList | tax | PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/CERTIFIED/VOUCHED/PARTIALLY_RECONCILED/FULLY_RECONCILED/VOIDED/REVERSED | VOUCHED/PARTIALLY_RECONCILED/FULLY_RECONCILED/VOIDED/REVERSED | PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/CERTIFIED | invoiceDate (LocalDate) | Service + Controller + 前端 + 测试 |
| 2 | VoucherList | finance/voucher | DRAFT/SUBMITTED/AUDITED/POSTED | POSTED | DRAFT/SUBMITTED/AUDITED | submittedAt (LocalDateTime) | Service + DTO + Controller + 前端 + 测试 |
| 3 | ReceivableList | arap | 见 BusinessDocStatus | VOUCHED/PARTIALLY_RECONCILED/FULLY_RECONCILED/VOIDED/CLOSED | DRAFT/SUBMITTED/APPROVED | docDate (LocalDate) | Controller 透传 + 前端 |
| 4 | PayableList | arap | 同 Receivable | 同 Receivable | 同 Receivable | docDate | Controller 透传 + 前端 |
| 5 | ExpenseList | arap | DRAFT/SUBMITTED/APPROVED/REJECTED/VOUCHERED | VOUCHERED | DRAFT/SUBMITTED/APPROVED/REJECTED | 需确认 | Service + Controller + 前端 + 测试 |
| 6 | TicketList | cash | IN_STOCK/ISSUED/ENDORSED/CASHED/VOIDED | CASHED/VOIDED | IN_STOCK/ISSUED/ENDORSED | 需确认 | Service + Controller + 前端 + 测试 |
| 7 | PrepaymentView | arap | DRAFT/APPLIED/CONFIRMED | CONFIRMED | DRAFT/APPLIED | 需确认 | Service + Controller + 前端 + 测试 |
| 8 | InvoiceReconcileView | tax | UNPAID/PARTIAL/PAID | PAID/PARTIAL | UNPAID | invoiceDate (客户端过滤) | 前端守卫 |
| 9 | AuditLogList | system | N/A（审计日志无生命周期） | N/A | N/A | operationTime (已有 startDate/endDate) | 前端 R1 守卫 |

## 后端接口契约

### 共用规则（对齐 BusinessDoc 模式）
- **period 退让**：`startDate/endDate` 均不为空时，period 条件不生效（日期优先）
- **scope 语义**：`scope=pending` → `status NOT IN (终态集)`；`scope=completed` → `status IN (终态集)`；scope 为空 → 不附加状态过滤（保持原行为）
- **日期守卫**：`startDate != null → field >= startDate`；`endDate != null → field <= endDate`

### Batch 1: TaxService.pageQueryInput
```java
IPage<InputInvoiceEntity> pageQueryInput(String vendorName, String period, String certStatus, String scope, LocalDate startDate, LocalDate endDate, Integer current, Integer size);
```
Controller: `TaxController` `/tax/input-invoice/page` 加 scope/startDate/endDate 透传。

### Batch 2: VoucherService.pageQuery
```java
IPage<VoucherVO> pageQuery(VoucherQueryDTO queryDTO); // DTO 加 scope, startDate, endDate
```
Controller: `VoucherController` `/voucher/page` (`@PostMapping`) 加 scope/startDate/endDate 透传。

### Batch 3: ArapController.pageReceivable/pagePayable
```java
R<IPage<BusinessDocVO>> pageReceivable(@RequestParam Integer current, @RequestParam Integer size, @RequestParam Long customerId, @RequestParam String period, @RequestParam String status, @RequestParam String scope, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate);
R<IPage<BusinessDocVO>> pagePayable(...同上...);
```
Backend: 仅 Controller 参数透传至 `BusinessDocQueryDTO`（service 已有 scope/日期逻辑）。

### Batch 4: ExpenseReimbursementService + TicketService
```java
IPage<ExpenseReimbursementVO> pageQuery(Long employeeId, String status, String scope, LocalDate startDate, LocalDate endDate, Integer current, Integer size);
IPage<TicketEntity> pageQuery(String ticketType, String status, String scope, LocalDate startDate, LocalDate endDate, Integer current, Integer size);
```

### Batch 5: PrepaymentService + 客户端守卫
```java
IPage<PrepaymentEntity> pageQuery(Long vendorId, Long customerId, String status, String scope, LocalDate startDate, LocalDate endDate, Integer current, Integer size);
```
InvoiceReconcileView / AuditLogList：仅前端守卫，无后端改动（AuditLogService.pageLog 已有 startDate/endDate；InvoiceReconcile 客户端过滤）。

## BDD 验收标准（每模块一组）

| ID | Given-When-Then |
|----|----------------|
| LT-01 | Given scope=completed When 查询 Then SQL 含 status IN 终态集合 且不含 status NOT IN |
| LT-02 | Given scope=pending When 查询 Then SQL 含 status NOT IN 终态集合 且不含 status IN |
| LT-03 | Given startDate/endDate When 查询 Then SQL 含日期区间条件（field >= 下界 且 field <= 上界） |
| LT-04 | Given 同时传 period 与日期范围 When 查询 Then period 条件退让不生效（仅日期生效） |
| LT-05 | Given scope=completed 且未选日期范围 When 查询 Then 前端不发请求，列表空态提示"请先选择日期范围…"，统计栏清空 |
| LT-06 | Given scope=completed 且选择快捷时段 When 查询 Then 请求携带对应 startDate/endDate 且清空自定义日期 |
| LT-07 | Given 切换 scope When 查询 Then 重置页码与日期条件，pending 默认全量，completed 恢复日期必填 |

各模块具体测试断言见对应测试文件（TaxServiceImplTest、VoucherServiceImplTest、ArapSettlementServiceImplTest、ExpenseReimbursementServiceImplTest、TicketServiceImplTest、PrepaymentServiceImplTest）。

## 前端交互（所有页面统一）

- scope 分区：顶部 tabs（待处理/已完成），默认待处理（对齐 BusinessDocList.scope-root-tabs）
- 已完成视图条件显示（frontend-design-system §4.5 R1-R6）
  - 快捷时段：本月 / 近3个月 / 近6个月 / 近12个月（el-radio-button），选中即查询
  - 自定义日期：`el-date-picker type="daterange"`，格式 `YYYY-MM-DD`
  - 未选日期：不发请求，表格 `#empty` 展示空态提示，统计栏清空
  - 日期与快捷互斥（R4）：先选快捷、后改自定义以自定义为准
  - 统计数同步受控（R5）
  - 重置恢复默认（R6）
- 原有类型/状态筛选与 scope 分区正交并存，互不影响

## 不做的

- 不新增后端按 scope 计数的统计接口（统计栏保持全量语义）
- 不改动各实体状态机（scope 仅是查询视图分层，不改变状态流转）
- 不改变已有 query 参数的语义（customerId/period/status/keyword 等保持原样）
