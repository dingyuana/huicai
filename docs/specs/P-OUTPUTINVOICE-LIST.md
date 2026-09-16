# SPEC: 销项发票列表 scope 分区与已完成视图条件显示

## 背景
> **test_ref**：TaxServiceImplTest, TaxControllerTest, TaxApiContractTest

`OutputInvoiceList.vue` 当前无条件全量分页展示全部销项发票（含流程已终结的历史数据），数据累积会导致列表过大、网络阻塞。依据设计规范 `frontend-design-system.md` §4.5（大表条件显示 R1-R6），对齐 `/finance/business-doc` 已落地的待处理/已完成分区模式改造。

## 变更内容

| # | 变更 | 类型 | 涉及文件 |
|---|------|------|----------|
| 1 | pageQueryOutput 新增 scope 过滤（pending/completed） | 后端 | TaxService + TaxServiceImpl |
| 2 | pageQueryOutput 新增 startDate/endDate 按 invoiceDate 区间过滤 | 后端 | TaxService + TaxServiceImpl |
| 3 | Controller /output-invoices/page 参数透传 | 后端 | TaxController |
| 4 | 前端 scope 根 Tab（待处理/已完成），默认待处理 | 前端 | OutputInvoiceList.vue |
| 5 | 已完成视图条件显示：快捷时段/自定义日期必填/空态提示 | 前端 | OutputInvoiceList.vue |
| 6 | 已完成视图统计栏与列表同受日期约束 | 前端 | OutputInvoiceList.vue |

## 后端接口契约

### TaxService.pageQueryOutput 新增参数

```java
IPage<OutputInvoiceEntity> pageQueryOutput(String customerName, String period, String status, String invoiceType,
                                           String scope, LocalDate startDate, LocalDate endDate,
                                           Integer current, Integer size);
```

### 查询条件语义

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scope | String | 否 | pending=流程未终结（默认）；completed=流程终态（VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED） |
| startDate | LocalDate | 否 | invoiceDate >= startDate |
| endDate | LocalDate | 否 | invoiceDate <= endDate |

- scope 为空：不附加状态过滤（保持原行为）
- scope=pending：`status NOT IN (VOUCHERED, FULLY_RECONCILED, PARTIALLY_RECONCILED, VOIDED, REVERSED)`
- scope=completed：`status IN` 同上集合
- startDate/endDate 均不为空时与既有 customerName/period/status/invoiceType 条件 AND 叠加

## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| OIL-01 | Given scope=completed When 查询 Then SQL 含 status IN 终态集合（VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED）且不含 status NOT IN |
| OIL-02 | Given scope=pending When 查询 Then SQL 含 status NOT IN 终态集合且不含 status IN |
| OIL-03 | Given startDate/endDate When 查询 Then SQL 含 invoice_date >= 下界 且 invoice_date <= 上界 |
| OIL-04 | Given scope=completed 且未选日期范围 When 查询 Then 前端不发请求，列表空态提示"请先选择日期范围（快捷时段或自定义）查询已完成单据"，统计栏清空 |
| OIL-05 | Given scope=completed 且选择快捷时段 When 查询 Then 请求携带对应 startDate/endDate 且清空自定义日期 |
| OIL-06 | Given scope=completed 且选择自定义日期 When 查询 Then 请求携带日期且清空快捷时段选中 |
| OIL-07 | Given 切换 scope When 查询 Then 重置页码与日期条件，pending 默认全量，completed 恢复日期必填 |

## 前端交互

- scope 分区：顶部 tabs（待处理/已完成），默认待处理（对齐 BusinessDocList.scope-root-tabs）
- 已完成视图条件显示（frontend-design-system §4.5 R1-R6）
  - 快捷时段：本月 / 近3个月 / 近6个月 / 近12个月（el-radio-button），选中即查询
  - 自定义日期：`el-date-picker` type="daterange"，格式 `YYYY-MM-DD`；选择后清空快捷时段
  - 未选日期：不发请求，表格 `#empty` 展示空态提示，统计栏清空
- 类型筛选（全部/专用/普通/红字/已红冲）与 scope 分区正交并存，互不影响

## 不做的

- 不新增后端统计接口按 scope 计数（统计栏 outputSummary 保持全量语义）
- 不改动发票状态机（scope 仅是查询视图分层，不改变状态流转）