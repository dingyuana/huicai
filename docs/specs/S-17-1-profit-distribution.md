# S-17.1 - 利润分配（自动提取盈余公积）

> **版本**：V1.0
> **编号**：HUICAI-SPC-109
> **日期**：2026-08-09
> **状态**：✅ 完整实现
> **层级**：期末与报表层
> **关联需求**：REQ-期末结转
> **关联 SPEC**：S-17 期末自动化结转

> **test_ref**：PeriodCloseServiceImplTest
---

## 概述

利润分配是损益结转后的下一步：从本年利润（4103）的期末余额按比例提取盈余公积（4101），生成自动结转凭证。

**与 S-17 的关系**：S-17 实现了损益结转（收入/费用→本年利润），本 SPEC 扩展其后的利润分配环节（本年利润→盈余公积）。

**使用前提**：损益结转凭证（`generateProfitCarryOver`）已过账，本年利润科目余额已反映净利润。

---

## 1. 输入契约

### 1.1 生成利润分配凭证

```
POST /api/v1/period-close/profit-distribution?period={periodCode}
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| period | String | 是 | 会计期间编码，格式 YYYYMM |

**前置条件**：
- 期间在 `t_period` 表中存在
- 当前用户已登录
- 该期间无已存在的利润分配凭证（幂等保护）
- 本年利润科目(4103)在 t_subject 中存在
- 盈余公积(4101)和利润分配(4104)科目在 t_subject 中存在
- 本年利润科目在 t_subject_balance 中有余额记录

---

## 2. 输出契约

### 2.1 成功响应

```json
{
  "code": 200,
  "message": "ok",
  "data": 42
}
```

`data` 为生成的凭证 ID（`VoucherEntity.id`）。生成的凭证：
- 状态：`DRAFT`（草稿）
- 来源：`GENERATED`
- 摘要："自动利润分配: {period}"
- 凭证号格式："DISTRIB-{period}-{流水号}"
- 分录：借 利润分配(4104) / 贷 盈余公积(4101)，金额 = 净利润 × 10%

### 2.2 错误响应

| 场景 | HTTP 状态码 | 错误信息 |
|------|-------------|----------|
| 期间不存在 | 404 | "会计期间不存在: {period}" |
| 已存在利润分配凭证 | 400 | "期间 {period} 已存在 {N} 张利润分配凭证" |
| 本年利润无余额数据 | 400 | "期间 {period} 本年利润无余额数据，请先完成损益结转过账" |
| 净利润为负或零 | 400 | "期间 {period} 净利润为 {X}，亏损无需分配" |
| 科目未配置 | 400 | "未配置盈余公积(4101)或利润分配(4104)科目" |

---

## 3. 核心逻辑

### 3.1 净利润计算

```
净利润 = t_subject_balance 中 4103 科目的 endBalance
```

4103 科目方向为 credit，其 `endBalance = beginBalance + creditTotal - debitTotal`。
- endBalance > 0 → 盈利（净利润为正）
- endBalance ≤ 0 → 亏损（不分配）

### 3.2 盈余公积计算

```
提取额 = 净利润 × 10%
金额保留2位小数（HALF_UP 四舍五入）
```

### 3.3 凭证分录

```
借：利润分配(4104)   提取额
贷：盈余公积(4101)   提取额
```

---

## 4. 异常处理

| 场景 | 处理方式 |
|------|---------|
| 幂等保护 | 检查凭证号前缀 "DISTRIB-{period}"，已存在则拒绝 |
| 科目不存在 | 分别查 4103/4101/4104，任一缺失则抛异常 |
| 余额不存在 | 未过账损益结转时本年利润无余额，抛明确提示 |
| 净利润为负 | 抛异常告知亏损金额，不生成凭证 |
| 事务回滚 | 标注 `@Transactional(rollbackFor = Exception.class)` |

---

## 验收标准（BDD）

### 场景 1：利润分配成功

- **Given** 期间 202607，损益结转凭证已过账，本年利润(4103) 期末余额 84,050.00
- **When** 调用 `POST /api/v1/period-close/profit-distribution?period=202607`
- **Then** 返回新凭证 ID
- **And** 生成 DRAFT 凭证，包含两条分录：借 利润分配(4104) 8,405.00 / 贷 盈余公积(4101) 8,405.00

### 场景 2：幂等保护 — 重复调用

- **Given** 期间 202607 已存在利润分配凭证
- **When** 再次调用 `POST /api/v1/period-close/profit-distribution?period=202607`
- **Then** 抛出 BusinessException(400, "已存在 N 张利润分配凭证")

### 场景 3：亏损 — 不分配

- **Given** 期间 202607 本年利润(4103) 期末余额为 -15,950.00（亏损）
- **When** 调用 `POST /api/v1/period-close/profit-distribution?period=202607`
- **Then** 抛出 BusinessException(400, "亏损无需分配")

### 场景 4：余额不存在

- **Given** 期间 202607 本年利润科目在 t_subject_balance 中无记录
- **When** 调用 `POST /api/v1/period-close/profit-distribution?period=202607`
- **Then** 抛出 BusinessException(400, "本年利润无余额数据")

---

```yaml
# === MACHINE-READABLE CONTRACT (V1.0) ===

contract_version: "1.0"
entity: PeriodClose
module: finance
extension_of: S-17

endpoints:
  - method: POST
    path: /api/v1/period-close/profit-distribution
    params:
      period: YYYYMM
    response: R<Long>  # 凭证 ID

logic:
  - step: "幂等检查"
    guard: "SELECT COUNT(*) FROM t_voucher WHERE voucher_no LIKE 'DISTRIB-{period}%' AND deleted=0 AND reversed_from IS NULL"
    fail: "BusinessException(400, '已存在利润分配凭证')"
  - step: "读本年利润余额"
    source: "t_subject_balance WHERE subject_id = 4103.id AND period = {period}"
    field: endBalance
  - step: "判断盈亏"
    guard: "endBalance > 0"
    fail: "BusinessException(400, '亏损无需分配')"
  - step: "计算提取额"
    formula: "endBalance × 0.10, scale=2, HALF_UP"
  - step: "生成凭证"
    status: DRAFT
    source: GENERATED
    voucher_no: "DISTRIB-{period}-{流水}"
    entries:
      - debit: 4104 (利润分配)
        credit: 4101 (盈余公积)
        amount: 提取额

acceptance_tests:
  - scenario: "利润分配成功 - 盈利 84,050 → 提取 8,405"
    given: "4103 endBalance=84,050.00"
    when: "POST /api/v1/period-close/profit-distribution"
    then: "凭证 DRAFT, 借 4104 8,405 / 贷 4101 8,405"
  - scenario: "幂等保护 - 已有凭证拒绝"
    given: "已存在 DISTRIB-202607 凭证"
    when: "POST /api/v1/period-close/profit-distribution"
    then: "400 已存在"
  - scenario: "亏损 - 不分配"
    given: "4103 endBalance=-15,950.00"
    when: "POST /api/v1/period-close/profit-distribution"
    then: "400 亏损无需分配"
```