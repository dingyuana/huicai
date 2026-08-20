# SPEC: 业务单据列表筛选与展示增强

## 背景
> **test_ref**：BusinessDocServiceImplTest, BusinessDocRestContractTest, BusinessDocControllerTest

`BusinessDocList.vue` 当前筛选和展示体验不足，影响代账会计日常查账效率。

## 变更内容

| # | 变更 | 类型 | 涉及文件 |
|---|------|------|----------|
| 1 | 期间筛选 → 日期范围选择器（docDate BETWEEN） | 前后端 | DTO + Service + 前端 |
| 2 | 新增金额区间筛选（amountMin / amountMax） | 前后端 | DTO + Service + 前端 |
| 3 | 已核销列徽章化（绿/黄/红三态） | 纯前端 | BusinessDocList.vue |
| 4 | 新增源单号列，可点击跳转关联单据 | 前端 | BusinessDocList.vue |

## 后端接口契约

### BusinessDocQueryDTO 新增字段

```java
private String startDate;   // yyyy-MM-dd
private String endDate;     // yyyy-MM-dd
private BigDecimal amountMin;
private BigDecimal amountMax;
```

### BusinessDocServiceImpl.pageQuery 查询条件

```java
.ge(StrUtil.isNotBlank(q.getStartDate()), BusinessDocEntity::getDocDate, LocalDate.parse(q.getStartDate()))
.le(StrUtil.isNotBlank(q.getEndDate()), BusinessDocEntity::getDocDate, LocalDate.parse(q.getEndDate()))
.ge(q.getAmountMin() != null, BusinessDocEntity::getAmount, q.getAmountMin())
.le(q.getAmountMax() != null, BusinessDocEntity::getAmount, q.getAmountMax())
```

`period`（YYYYMM）查询条件保留，与日期范围并存，前端同时传两个时后端 OR 关系——两个条件都有效时后端只保留第一个有效条件（优先日期范围）。

---

## 1. 输入契约

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startDate | String(yyyy-MM-dd) | 否 | 起始日期 |
| endDate | String(yyyy-MM-dd) | 否 | 结束日期 |
| amountMin | BigDecimal | 否 | 最小金额 |
| amountMax | BigDecimal | 否 | 最大金额 |
| docDateStart | String | 否 | 单据起始日期 |
| docDateEnd | String | 否 | 单据结束日期 |

## 2. 输出契约

| 字段 | 类型 | 说明 |
|------|------|------|
| docNo | String | 单据编号 |
| docType | String | 单据类型 |
| amount | BigDecimal | 金额 |
| docDate | LocalDate | 单据日期 |
| status | String | 状态 |
| settledAmount | BigDecimal | 已核销金额 |
| unsettledAmount | BigDecimal | 未核销金额 |

## 3. 状态流转

| 转换 | 前置条件 | 副作用 |
|------|---------|--------|
| DRAFT→SUBMITTED | 提交 | 记录提交人/时间 |
| SUBMITTED→AUDITED | 审核 | 记录审核人/时间 |
| AUDITED→VOUCHERED | 制证 | 写入 voucherNo |
| AUDITED→REVERSED | 红冲 | 设置 isReversed，生成红冲单 |

## 4. 异常处理

| 场景 | 异常 |
|------|------|
| 日期范围非法 | BusinessException: 起始日期不得晚于结束日期 |
| 金额范围非法 | BusinessException: 最小金额不得大于最大金额 |

## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| LIST-01 | Given 日期范围筛选 When 查询 Then 仅返回期间内单据 |
| LIST-02 | Given 金额区间筛选 When 查询 Then 仅返回区间内单据 |
| LIST-03 | Given 核销列展示 When 未核销 Then 显示红色徽章 |
| LIST-04 | Given 源单号列 When 点击编号 Then 跳转关联单据详情 |

## 前端交互

- 日期范围：`el-date-picker` type="daterange"，格式 `yyyy-MM-dd`
- 金额区间：两个 `el-input-number`，min=0，precision=2
- 已核销徽章：
  - 已核销 == 金额 且 > 0 → 绿色 `el-tag success` "已核销"
  - 已核销 > 0 且 < 金额 → 黄色 `el-tag warning` "部分核销"
  - 已核销 == 0 且 金额 > 0 → 红色 `el-tag danger` "未核销"
  - 金额为 0 → 灰色 `-`
- 源单号列：显示 `sourceDocNo`，点击打开详情弹窗

## 不做的

- 不实现"上一个季度"快捷按钮（后续需求）
- 不实现源单号列表（多个关联单据时只显示第一个）