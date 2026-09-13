---
标题: P68 期末结账按期间顺序约束（防止跨期跳结/跳反）
编号: P68
版本: v0.1 (2026-09-13)
关联PRD: REQ-2026-083
状态: 📝 草案待审核
关联SPEC: P57（企业建账期间通用化）、REQ-2026-009（期间结账）
预估工时: 4h（后端校验 1h + 测试 2h + 前端提示 1h）

## 背景

期末结账没有期间顺序约束，`PeriodCloseServiceImpl.closePeriod` 只校验：
- 期间本身状态（非 closed/locked）
- 无未记账凭证
- 试算平衡
- 无草稿红冲凭证

**但不校验"紧邻上一会计期间是否已结账"**，导致前期未结转、后期却能先结。当前生产数据已出现错乱（enterprise_id=1）：

```
202401 open | 202402 open | 202403 closed | 202404 closed ... 202411 closed | 202412 open
```

即 202404–202411 已结账，而 202401/202402 仍 open——跨期跳结。这会造成：余额链期初/期末衔接错乱、损益结转期间不连续、资产负债表取数不可信。

## 目标

1. 结账必须严格按会计期间升序：某期间结账时，其紧邻上一期间必须已 `closed`（企业建账起始期 `start_period` 除外，它无上一期）。
2. 反结账必须严格降序：某期间反结账时，其后一期间必须未结账（防止前期反结后，后期仍挂在已断的链上）。
3. 不自动改任何期间状态（铁律 #1：人是唯一审核主体），只做拦截与提示。

## 输入契约

- 入口不变：`POST /api/base/voucher/v1/period-close/close?period=`、`/reopen?period=`。
- 依赖：`t_period.period_code`(YYYYMM)、`t_period.status`(open/closed/locked)、`t_enterprise.start_period`（enterprise 1 = 202401）。
- 仅后端 + 前端错误提示，不改表结构、不加 Flyway。

## 输出契约

- `closePeriod(period, userId)`：若上一期间存在且 `status != 'closed'`，抛 `BusinessException.badRequest("上一会计期间 {prev} 尚未结账，请先完成上期结账后再结 {period}")`。
- `reopenPeriod(period, userId)`：若下一期间存在且 `status = 'closed'`，抛 `BusinessException.badRequest("下一会计期间 {next} 已结账，请先反结账 {next} 后再反结 {period}")`。
- `checkBeforeClose(period)`：在 issues 中追加一条顺序未满足的提示（前端结账检查页可见），与现有 issues 机制一致。

## 边界与期间推算

- 紧邻上一期 = 月历上一个月（跨年回绕：`202401` 的上一期为 `202312`）。
- 若推算出的上一期在 `t_period` 中**不存在**，则以企业 `start_period` 为界：`period <= start_period` 视为首个可结期间，放行；`period > start_period` 但上一期记录缺失，抛错"上一会计期间不存在，请先初始化期间"（不放任跳结）。
- `locked` 是期初建账锁定态（opening_status），与期间结账 `closed` 是两个维度；顺序判断只看 `status='closed'`。
- 首个可结期间：`period.equals(enterprise.start_period)` 时跳过"上一期必须 closed"校验。

## 状态流转

- 不新增实体状态；仅约束 open→closed、closed→open 的合法前置条件。

## 异常处理

| 场景 | 处理 |
|------|------|
| 上期未结账就结本期 | 400，提示先结上一期（含上期编码） |
| 下期已结账就反结本期 | 400，提示先反结下一期 |
| 上一期记录缺失且本期晚于 start_period | 400，提示先初始化期间 |
| 本期是 start_period | 放行（无上一期） |
| 期间不存在 | 既有 notFound |
| 期间已 closed/locked | 既有 badRequest |

## BDD（Given-When-Then，每场景一个 @Test）

- **场景 1 首个期间可直接结账**：Given enterprise.start_period=202401 且 202401 检查全过 When 结 202401 Then 成功置 closed。
- **场景 2 上期未结禁止结本期**：Given 202401=open When 结 202402 Then 抛错含"202401 尚未结账"，202402 仍 open。
- **场景 3 上期已结允许结本期**：Given 202401=closed 且 202402 检查全过 When 结 202402 Then 成功。
- **场景 4 跨年上推**：Given start_period=202401、202312 不存在 When 结 202401 Then 作为起始期放行（不要求 202312）。
- **场景 5 反结账降序**：Given 202401=closed、202402=closed When 反结 202401 Then 抛错含"202402 已结账"。
- **场景 6 反结账最末已结期**：Given 202401=closed、202402=open When 反结 202401 Then 成功置 open。
- **负向断言**：被拦截时期间 status 必须保持原值、无任何 UPDATE 落库。

## 存量脏数据处理（不在本 SPEC 自动修复）

当前 enterprise 1 已存在 202403–202411 先结、202401/202402 未结的断链。上线新校验后：
- 该数据不影响系统运行（只影响"能否继续结账/反结账"）。
- 提供**人工**处置建议（另议，不自动改状态）：要么补结 202401/202402 使其合规，要么从 202411 起逐期反结到 202403 再重走顺序。涉及资金/结账状态，必须人工确认，不在代码里批量改。

## 非目标

- 不自动结账、不批量结账、不自动修复存量断链。
- 不改结账的既有检查项（未记账/试算平衡/红冲草稿）。
- 不做多企业批量顺序（Agency REQ-073 另议）。

## 版本历史

- v0.1 (2026-09-13)：草案，基于现场断链数据与 PeriodCloseServiceImpl 现状。待老丁审核。
