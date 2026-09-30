# P100 SPEC — 银行流水自动制证科目缺失防护 + 种子补全

> **版本**：V1.1（已实施） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P100 | 优先级：高（P100）
> **状态**：✅ 已开发完成
> **关联需求**：REQ-2026-127（新登记）
> **前置 SPEC**：P55（bank-statement-subject-and-batch-fix）—— P55 把利息/手续费科目改指 `6603` 并加了事后 null 守卫，但**从未把 `6603` 种进库**，本 SPEC 补完该链路的另一半。
> **test_ref**：AutoGenerationServiceTest（新增 2 条）、VoucherEntryMapperRealDBTest（夹具改 find-or-insert）

---

## 0. 缺陷背景（服务器手工测试实锤）

`http://localhost:3001/finance/bank-statement` 对 100/99/98/95/94 号 `business_receipt` 流水执行审核链路时，后端抛：

```
java.lang.NullPointerException: Cannot invoke "com.huicai.base.system.entity.Subject.getId()"
because "arAcct" is null
    at com.huicai.sme.arap.service.impl.AutoGenerationService.generateDocThenVoucher(AutoGenerationService.java:528)
```

**根因链（全部实测验证）**：

| # | 缺陷 | 证据 |
|---|---|---|
| 1 | `t_subject` 中 `2203`（预收账款）**完全不存在**（含已删行），`1122` 存在 | `SELECT code FROM t_subject` 全表无 2203 |
| 2 | `generateDocThenVoucher` 无未结清应收 → `findSubjectByCode("2203")` 返 null → `arAcct.getId()` NPE | 与前端报错逐字一致 |
| 3 | 审计全部 15 处 `findSubjectByCode` 调用点，另有 `1221`/`2211`/`6603` 三个代码同样缺失 | 逐一查库比对 |
| 4 | **既有单测把 2203 一并 stub 了**（`stubSubject("2203", 2203L)`），Mock 与 code 无关地回任何科目 | Mock 盲区，缺陷从未暴露 |
| 5 | 触发链：`POST /review`（PENDING→CONFIRMED）后 `POST /audit` 或 `/generate` → `doAutoGenerate` → `autoGenerateInNewTx`(REQUIRES_NEW) NPE → 内外事务回滚 | 5 条流水停在 CONFIRMED 且无单据，与回滚一致 |

**金额/审计影响**：未产生任何半截凭证（REQUIRES_NEW 回滚），但提交审核链路完全不可用，且违反铁律 #5（审计字段）的精神 —— 失败原因对用户不可读。

---

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| `stmt.classification` | String | A/B 类分类，硬编码降级路径按其选科目 |
| `stmt` 关联 `doc.customerId` / `doc.supplierId` | Long | 可空；空则按「无未结清」走 2203/1123 分支 |
| 科目表 `t_subject(code, enterprise_id)` | DB | **运行时依赖**，缺失不得导致 NPE |
| 测试夹具造数 | Test | 不得与 Flyway 种子撞 `uq_subject_code_ent` |

## 2. 输出契约

| 输出 | 约束 |
|---|---|
| 科目存在 | 正常生单+制证，凭证借贷科目来自 `t_subject` |
| 科目缺失 | 抛 `BusinessException(500, "缺少科目 <code>, 无法生成凭证, 请先在基础数据-会计科目中维护该科目")` —— **必须含科目代码**，禁止 NPE（铁律 #14） |
| 整体原子性 | 失败时不留半截凭证/分录（由 `@Transactional(rollbackFor=Exception.class)` + REQUIRES_NEW 保证） |
| Flyway 种子 | `V159` 为全部已建基础科目企业补齐 `1221`/`2203`/`2211`/`6603`，幂等可重跑 |

## 3. 状态流转

```
银行流水 review_status:
  PENDING ──review──▶ CONFIRMED ──audit/doAutoGenerate──▶ payment_created | voucher_generated
                                        │
                                        ├─ 科目齐全 → 生单(Vouchered 单据) + 凭证 → 状态前进
                                        └─ 科目缺失 → BusinessException → 内外事务回滚 → 停留 CONFIRMED（可修复后重试）
```
无非法跳转；失败不推进状态（`generateVoucher` 显式允许 CONFIRMED 重试）。

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| `findSubjectByCode` 返 null（调用点需必有科目） | `requireSubject(code)` 抛 `BusinessException` 并指明代码 |
| `1012` 缺失、`1221` 存在（内部转账回退） | 保留回退；两者皆缺 → `requireSubject("1221")` 抛错 |
| A 类路径 | 沿用 P55 的事后 null 守卫（`debitAcct == null || creditAcct == null` → `BusinessException`），本 SPEC 不改其行为 |
| 种子已存在又遇夹具插入 | 测试侧 `insertProfitSubject` 改 find-or-insert，撞唯一约束即复用 |

## 5. BDD 行为契约（Given-When-Then）

| # | Given | When | Then | @Test |
|---|---|---|---|---|
| AT-127-1 | 库中无 `2203`，收款流水且无未结清应收 | `autoGenerate` 走硬编码降级 | 抛 `BusinessException` 且消息含 `2203`，**不是 NPE** | `testAutoGenerate_receipt_缺2203科目_抛业务异常而非空指针` |
| AT-127-2 | 同上 | `autoGenerate` | 不写入任何凭证分录（负向断言） | `testAutoGenerate_receipt_缺2203科目_不得生成任何凭证分录` |
| AT-127-3 | Testcontainers 全新库跑全部 Flyway | 迁移到 V159 | `1221`/`2203`/`2211`/`6603` 在每个已有 1122 的企业下各 1 行 | 全量慢测（VoucherEntryMapperRealDBTest 等真实 DB 类） |
| AT-127-4 | 夹具需 6603 而 V159 已种入 | `insertProfitSubject("6603",…)` | 复用既有行，不撞 `uq_subject_code_ent` | `VoucherEntryMapperRealDBTest`（10/10 绿） |
| AT-127-5 | 开发库已迁 V159，流水 1（business_receipt, CONFIRMED） | `POST /{id}/audit` | HTTP 200；业务单 `RECEIPT…` VOUCHERED；分录 `1002 借 / 2203 贷` | 手工 E2E（脚本 `326-audit.sh`，已实测通过） |

## 6. 竞品对标（铁律 #15）

| 竞品 | 同类设计 | 差异/借鉴 |
|---|---|---|
| 用友 U8/NC | 凭证模板须绑定「科目对照关系」，缺科目时制证前校验并**列出缺失科目清单**，不产生半张凭证 | 借鉴：异常必须**指明科目代码**，本 SPEC 的 `requireSubject` 消息即按此设计 |
| 金蝶 K/3·星空 | 基础资料「会计科目」随账套初始化模板**全量建账**，业务模块引用科目时只做存在性校验 | 借鉴：种子完整性是基础，V159 即按标准科目体系（1221/2203/2211/6603）补齐 |
| SAP | 科目确定（account determination）配置缺失时以**明确错误码**中止过账 | 一致：宁可显式失败，不得静默或 NPE |
| QuickBooks | 银行流水匹配分类后自动入账，依赖内置 Chart of Accounts 预置 | 一致：预置科目表是自动入账前提 |

**结论**：四家竞品均以「初始化全量科目 + 制证前存在性校验 + 指明缺失项」为共同实践；本改动（种子补全 + `requireSubject`）与之对齐，不引入新的自动决策（仍守铁律 #1/#2 人工审核边界）。

## 7. 不在范围

- 不改 Mapper 的 `findSubjectByCode` 查询语义与数据权限过滤
- 不改 A 类路径的既有 null 守卫行为（P55 已定型）
- 不做「缺科目时自动用备用科目兜底」—— 那是无人工确认的自动记账决策，违反铁律 #1/#2
- `t_bank_statement.generated_doc_id`/`generated_voucher_id` 幽灵字段（AGENTS §4.2-6 既有记录）不在本 SPEC 修

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.1 | 2026-09-29 | opencode | 已实施：`requireSubject` + V159 种子 + 2 条单测（TDD 红→绿）+ 夹具 find-or-insert；全量 1998/0/0/5 全绿；开发库 E2E 实测 `/audit` 200 且分录正确 |
| V1.0 | 2026-09-29 | opencode | 初稿（根因链 + 四段契约 + BDD + 竞品对标） |
