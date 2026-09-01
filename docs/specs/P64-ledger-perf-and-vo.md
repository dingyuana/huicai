# P64 SPEC — 账簿查询性能与类型治理（T9 N+1 消除 + T10 VO 化）

> **编号**：HUICAI-SPC-064
> **test_ref**：LedgerServiceImplTest / SubjectBalanceServiceImplTest / LedgerControllerTest
> **版本**：V1.1 | **日期**：2026-09-01
> **状态**：✅ 已实现（2026-09-01 验证：T9 N+1 消除 + T10 VO 化完成，4 个验收场景全过）
> **关联需求**：REQ-2026-079（账簿查询增强）
> **评估依据**：[账簿查询功能评估报告](../development/audit/2026-08-31-账簿查询功能评估报告.md)（缺陷 D5 N+1、D6 弱类型返回）
> **关联计划**：[账簿查询完善计划](../development/plans/2026-08-31-账簿查询完善计划.md)（T9、T10，P2 工程质量）

---

## T9 N+1 消除（缺陷 D5）

### 现状

- `LedgerServiceImpl.subjectBalance()`：逐行 `subjectService.getById(b.getSubjectId())` 组装科目信息 → 451 个末级科目 = 451 次 DB 查询
- `SubjectBalanceServiceImpl.checkTrialBalance()`：同样逐行 `getById`（同构问题，一并修复）

### 方案

| 位置 | 改动 |
|------|------|
| `subjectBalance` | 先收集 subjectIds → `subjectService.listByIds(subjectIds)` 一次批量查 → `Map<Long, Subject>` 索引 → 逐行取索引 |
| `checkTrialBalance` | 同上：批量查科目后按 direction 汇总 |

**只读优化，SQL 不变，返回结构不变**（行为等价，仅减少 DB 往返）。

### 验收（BDD）

- **场景 1**：`subjectBalance` 对 N 行余额只调用 1 次 `listByIds`（而非 N 次 getById），结果与优化前等价（科目编码/名称/方向一致）
- **场景 2**：`checkTrialBalance` 同样批量查科目，汇总借贷方向结果不变

---

## T10 VO 化（缺陷 D6，铁律 #13）

### 现状

- 三个账簿查询（subjectBalance/generalLedger/subsidiaryLedger）返回 `List<Map<String, Object>>`，违反铁律 #13「禁止将数据库 Entity 直接暴露，出参必须定义 VO」
- 前端无类型保障，序列化字段名靠约定

### 方案

| 新 VO | 用途 | 字段 |
|-------|------|------|
| `SubjectBalanceRowVO` | 科目余额表行 | subjectId/subjectCode/subjectName/direction、beginBalance/debitTotal/creditTotal/endBalance、yearBeginBalance/yearDebitTotal/yearCreditTotal |
| `LedgerRowVO` | 总账/明细账行 | type/summary/debit/credit/running、voucherId/voucherNo/voucherDate、subjectId/subjectCode/subjectName |

**兼容性**：
- VO 字段名与现有 Map key 完全一致（camelCase）→ **JSON 序列化结构不变**，前端零改动
- 总账行特有字段（voucherNo 等）在明细账场景复用同一 VO，多余字段置 null（Lombok 自动）
- `trialBalance` 返回的是聚合 Map（含 balanced/empty 等多口径布尔），保持 `Map<String, Object>` 不变（不属于行类型，避免过度设计；如需 VO 化可留待后续）

### 验收（BDD）

- **场景 1**：`subjectBalance` 返回 `List<SubjectBalanceRowVO>`，行字段与优化前 Map 完全一致
- **场景 2**：`generalLedger`/`subsidiaryLedger` 返回 `List<LedgerRowVO>`，行结构（type/summary/debit/credit/running/voucherNo/voucherDate）与优化前一致
- **场景 3**：Controller 泛型同步 VO（`R<List<SubjectBalanceRowVO>>`），HTTP 响应 JSON 结构不变

---

## 范围外

- 前端类型定义（TS interface）迁移 — 后续前端任务
- `checkTrialBalance` 的 Map 聚合返回 VO 化 — 过度设计，不做
- 分页（账簿行数受科目/分录数约束，友商同）— 远期

---

## 版本历史

- V1.1 (2026-09-01): 实现完成，T9/T10 落地，4 个验收场景全过
- V1.0 (2026-09-01): 初始版本，覆盖 T9（N+1）+ T10（VO 化）

<!-- === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
module: base.voucher / base.balance
table: t_voucher_entry / t_subject_balance
states: {}                     # 纯查询，无状态机
transitions: []
constraints:
  - id: C-01
    type: performance
    rule: "subjectBalance/checkTrialBalance replace per-row getById with single listByIds batch query"
  - id: C-02
    type: business
    rule: "VO field names identical to previous Map keys; JSON response structure unchanged"
  - id: C-03
    type: business
    rule: "trialBalance aggregate map stays Map (out of scope for row VO)"
acceptance_tests:
  - id: AT-001 (T9): subjectBalance batch-fetches subjects via listByIds once; results equivalent
    status: implemented
  - id: AT-002 (T9): checkTrialBalance batch-fetches subjects; direction totals unchanged
    status: implemented
  - id: AT-003 (T10): LedgerService returns SubjectBalanceRowVO/LedgerRowVO; fields match legacy Map
    status: implemented
  - id: AT-004 (T10): Controller generics updated to R<List<VO>>; JSON unchanged
    status: implemented
out_of_scope:
  - "前端 TS 类型迁移"
  - "trialBalance Map → VO（过度设计）"
dependencies:
  - spec: P63
    relation: "P1 阶段 T4-T8 已落地，本 SPEC 承接 P2 质量治理"
-->