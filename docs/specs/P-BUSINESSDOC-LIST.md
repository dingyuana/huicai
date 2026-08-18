# SPEC: 业务单据列表筛选与展示增强

## 背景

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