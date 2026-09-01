# P65 SPEC — 账簿查询前端接入（D-C 延后项）

> **编号**：HUICAI-SPC-065
> **关联需求**：REQ-2026-079（账簿查询增强）
> **关联 SPEC**：[P63](./P63-ledger-p1-alignment.md)（T4-T8 后端契约）、[P64](./P64-ledger-perf-and-vo.md)（T9-T10）
> **版本**：V1.0 | **日期**：2026-09-01
> **状态**：📝 草案
> **范围声明**：本 SPEC 收编 P63 决策 D-C 延后的前端任务——LedgerView 新列展示 + 凭证联查跳转。后端 T1-T10 已完成（1555 测试 0 失败），前端仅消费既有/已就绪的 API。

---

## 1. 背景

后端账簿查询增强已全部落地（T1-T10, commit `3439649` 基准）：
- 余额表：`yearBeginBalance/yearDebitTotal/yearCreditTotal`（T4）+ `includeZero/includeNoMovement/subjectCodePrefix` 过滤参数（T5）
- 总账/明细账：行携带 `voucherNo/voucherDate`（T6）+ `type`（OPENING/ENTRY/CLOSING/YEAR_TOTAL）+ `running` 滚动余额（T7）+ `includeUnposted` 参数（T8）
- 返回值已 VO 化（T10），JSON 结构 `R<List<...>>` 不变，前端类型需同步更新

现行 `frontend/src/api/modules/ledger.ts` 接口类型是 P1 阶段前的旧版：`SubjectBalanceRow` 缺年月累计字段、`LedgerRow` 缺 voucherNo/voucherDate/type/running、查询函数缺过滤参数。

## 2. 改动清单

### 2.1 `frontend/src/api/modules/ledger.ts`

| 项 | 现状 | 目标 |
|----|------|------|
| `SubjectBalanceRow` | begin/debit/credit/end | + `yearBeginBalance`/`yearDebitTotal`/`yearCreditTotal` |
| `LedgerRow` | type(OPENING/ENTRY/CLOSING)/summary/debit/credit/running/assistJson | type 增 `YEAR_TOTAL`；+ `voucherNo`/`voucherDate`/`voucherId`/`subjectCode`/`subjectName`；assistJson 已废弃可移除 |
| `getSubjectBalance` | `(period)` | + `params: { includeZero?, includeNoMovement?, subjectCodePrefix? }` |
| `getGeneralLedger` | `(subjectId, period)` | + `params: { includeUnposted? }` |
| `getSubsidiaryLedger` | `(subjectId, period)` | + `params: { startDate?, endDate?, includeUnposted? }` |
| `TrialBalance` | 有 balanced/etc | + `empty?`/`emptyMessage?`（T3） |

**类型约束**：`number`（后端 BigDecimal 序列化为 number/string，项目内统一 number 约定）

### 2.2 `frontend/src/views/finance/ledger/LedgerView.vue`

| Tab | 改动 |
|-----|------|
| 科目余额表 | 行新增「年初余额」「本年累计借/贷」列；可选过滤控件（含零/无发生额/编码前缀）——**过滤控件本期可不做交互，仅展示**（范围收敛，见 §3） |
| 总分类账 | 凭证号列：`#{{voucherId}}` → 可点击 `voucherNo`（有则链接，无则回退 `#id`）；跳转 `/finance/voucher/detail?voucherId=` 打开新窗口 |
| 明细账 | 期初行（OPENING）渲染「期初余额」；分录行凭证号可点击跳详情；新增「余额」(running) 列 |

**跳转目标**：`VoucherDetail` 路由已存在（`/finance/voucher/detail`），通过 `route.query.id` 读取凭证 ID（源码 80 行 `Number(route.query.id)`）——**跳转 query 参数名必须是 `id`**（非 voucherId）。

**试算平衡弹窗**：`empty=true` 时展示 `emptyMessage` 提示文案（替代「平衡」误导）。

## 3. 范围收敛（本期不做）

- 余额表过滤控件完整交互（includeZero/includeNoMovement/subjectCodePrefix 的 UI 输入）——**仅 API 层支持 + 类型补充**，UI 控件延后
- 辅助核算账前端 Tab（T1 后端已有 `/auxiliary`，前端接入另立任务）
- 明细账 startDate/endDate 日期范围控件（T2 后端已有，UI 延后）

## 4. 依赖与验收

**依赖**：
- `VoucherDetail` 已通过 `route.query.id` 读取凭证 ID（无需改造）
- 后端 `GET /ledgers/general|subsidiary` 已带 `voucherNo`（T6 已验证）

**验收（BDD）**：

| 验收 | Given-When-Then |
|------|-----------------|
| AT-001 新列展示 | Given 余额表有数据 → When 加载 → Then 年初余额/本年累计列正常显示 |
| AT-002 总账联查 | Given 总账 ENTRY 行有 voucherNo → When 点击凭证号 → Then 跳转 VoucherDetail（`?id=<voucherId>`） |
| AT-003 明细账余额列 | Given 明细账有分录 → When 加载 → Then 显示期初行+滚动余额列（running） |
| AT-004 试算平衡空提示 | Given trialBalance.empty=true → When 打开弹窗 → Then 展示 emptyMessage 而非「平衡」 |
| AT-005 回归 | 前端 `npm run build` / vue-tsc 通过；后端不变 1555 测试 0 失败 |

---

## 版本历史

- V1.0 (2026-09-01): 初始版本，收编 D-C 延后的前端接入

<!-- === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
module: frontend (Vue3 + Element Plus + TS)
states: {}                     # 纯展示，无状态机
transitions: []
constraints:
  - id: F-01
    rule: "API 参数/字段与后端 VO (SubjectBalanceRowVO/LedgerRowVO) 完全对齐"
  - id: F-02
    rule: "凭证号点击跳转 /finance/voucher/detail?voucherId=<id>"
  - id: F-03
    rule: "trialBalance.empty=true 时展示 emptyMessage"
  - id: F-04
    rule: "后端零改动；前端 build 与 vue-tsc 通过为验收门槛"
acceptance_tests:
  - id: AT-001..005 (见 §4)
    status: draft
out_of_scope:
  - "余额表过滤 UI 控件（仅 API+类型）"
  - "辅助核算账前端 Tab"
  - "明细账日期范围 UI（仅 API+类型）"
dependencies:
  - spec: P63 / P64
    relation: "消费其后端契约；D-C 前端部分"
-->