# P109 核销异常池可用性修复

> **状态**：🆕 **待审核**（SPEC V1.0，2026-10-06）—— **本 SPEC 只提交取证结论与待决策项，未动任何生产代码**
> **需求**：REQ-2026-138（新登记）| **编号**：HUICAI-SPC-P109 | **来源**：P106 SPEC §9「不在本 SPEC 范围」登记项 + P102 待办
> **铁律约束**：三步闭环 SPEC→Plan→**审核**→执行。**§2 的 D-109-1 未裁定前不得动 DDL**（改错方向 = 白做且要回滚）
> **关联**：[P106 多账套](P106-multi-book-account-set.md)（批次 1a-2 删除了本表的幽灵字段死代码）| [P102 安全基线](P102-security-permission-baseline.md)

---

## 0. 取证结论（全部真库实测，非读 migration 推断）

**取证载体**：`ReconciliationExceptionEntityDbProbeTest`（4 例，L2 真库 Testcontainers，全绿 —— 每例都是「断言缺陷存在」，故全绿即缺陷坐实）。

### 0.1 四方不一致（这是本 SPEC 的核心，不是「字段对不齐」）

| 层 | 数据模型 | 关键字段 |
|---|---|---|
| **DB 表** `t_reconciliation_exception` | **银行账户-centric**（V1 baseline） | `account_id`(NOT NULL, FK→`t_bank_account`)、`description`、`period`(NOT NULL) |
| **Entity** `ReconciliationExceptionEntity` | **往来单位-centric** | `sourceDocType/sourceDocId`、`targetDocType/targetDocId`、`partyId/partyType`、`matchSuggestion` |
| **Service** `ReconciliationServiceImpl` | 往来单位-centric | 与 Entity 一致 |
| **前端** `ReconciliationExceptionList.vue` | 往来单位-centric + **第三套枚举** | 读 `row.sourceDocType`/`sourceDocId`/`retryCount`；`exceptionType` 用 `PARTY_MISMATCH`/`AMOUNT_MISMATCH`/`INVOICE_NOT_FOUND`/`MATCH_FAILED`/`APPROVAL_REQUIRED` |

⚠️ **前端枚举与 DB CHECK 完全不交集**：`chk_exception_type CHECK (exception_type IN ('AMOUNT_DIFF','DATE_DIFF','UNMATCHED','DUPLICATE'))`（V1 baseline:1297）。前端下拉的 5 个值**没有一个能通过 CHECK**。

### 0.2 四条实测事实

| # | 断言 | 实测结果 |
|---|---|---|
| F1 | `createException()` 能插入成功？ | ❌ **不能**。真实报错：`ERROR: null value in column "account_id" of relation "t_reconciliation_exception" violates not-null constraint` |
| F2 | `retryException()` 能重试？ | ❌ **恒抛**。`ReconciliationServiceImpl:972` 的 `if (targetDocType == null \|\| targetDocId == null) throw` —— 两字段 `exist=false` ⇒ 读回恒 null ⇒ **对任何记录都抛「异常记录缺少目标单据信息」** |
| F3 | 该表有真实数据吗？ | ❌ **`count(*) = 0`** ⇒ 缺陷从未被真实写入触发过 |
| F4 | `tenant_id` 列存在吗？ | ❌ 不存在 ⇒ 证实批次 1a-2 删死代码是对的（该字段是真幽灵） |

### 0.3 「为什么两年没人发现」——三层遮蔽同时成立

1. **唯一测试是 Mock**：`ReconciliationServiceImplTest:720` 用 `@MockBean` 的 mapper，**结构上看不见 NOT NULL / FK / CHECK**（AGENTS §4.3 第 7 条）。
2. **表是空的**（F3）⇒ 即使真库跑，没有用例去调 `createException`，自然不报错。
3. **无生产调用方**：`createException` 全仓**只有接口声明 + 实现 + 那个 Mock 测试**，没有任何 Controller 或 Service 调它 ⇒ 它是一条**从未接上线的死路径**。

⚠️ 但前端**有页面**（`/arap/reconciliation-exception`，路由已注册）且**调的是真实端点** ⇒ 用户点开页面会看到空列表，且**即使有数据也会显示不出来源单据**（那 4 个字段恒 null）。

### 0.4 修正一处此前的错误定性

P106 SPEC §9 把本项登记为「`ReconciliationExceptionEntity` 15 处 `exist = false`（含 `updatedAt` 反向缺口）」——**这个定性偏轻**。实测表明它不是「Entity 注解写错」，而是**Entity 被整体重构过、但配套的 migration 从未写过**（AGENTS §4.5 第 5 条同型：「注释说 Vxx 列已添加但 migration 实际没写」）。15 处 `exist=false` 是**症状**，不是病因；只把注解删掉（不改表）会让写入从「撞 NOT NULL」变成「撞 Unknown column」，**问题原地不动**。

---

## 1. 根因

`ReconciliationExceptionEntity` 按「往来单位核销异常」重写时，**没有同步写 Flyway migration 把 `t_reconciliation_exception` 从账户-centric 迁到往来单位-centric**。于是：

- Entity 里 12 个字段被标 `exist=false`（作者自己知道列不存在，用标注掩盖）
- 2 个字段是**反向缺口**（列真实存在却标不存在）：`updatedAt`（V127:34）、`description`（V1 baseline）
- 2 个**必填列在 Entity 里压根没有字段**：`account_id`、`period` ⇒ 写入必挂
- `enterprise_id`（V103:43 已加 `NOT NULL DEFAULT 1`）Entity 无字段 ⇒ 静默落 DB 默认值 **1**（与 P106 批次 1a-1 修掉的 `PrepaymentEntity` 同型，AGENTS §4.5 第 34 条）

---

## 2. 待决策项 —— ✅ **V1.1 已裁定（2026-10-06，老丁）**

| # | 裁定 | 关键含义 |
|---|---|---|
| **D-109-1** | ✅ **方案 A：表迁就 Entity** | 写 Flyway 加往来单位-centric 的列；`account_id`/`period` 降为可空（银行流水时代遗留）；Entity 删 12 处 `exist=false`、补 `enterpriseId`；`tenantId` 保持幽灵（表确无该列） |
| **D-109-2** | ✅ **方案 B：对齐到 DB 现有 4 值** | 前端下拉与文案改为 `AMOUNT_DIFF`/`DATE_DIFF`/`UNMATCHED`/`DUPLICATE`；**后端与 CHECK 零改动、零迁移风险** |
| **D-109-3** | ✅ 保留 `retryCount`/`remark`/`createdBy` 三列（前端 `{{ row.retryCount ?? 0 }}` 已在用） | 已含在方案 A 的列清单内 |

**裁定依据补充（V1.1 实测校正）**：D-109-2 裁定前查清了枚举不交集的**真实影响面** —— 前端对异常池**只有读接口**（`pageReconciliationExceptions`/`resolve`/`ignore`/`retry`，**没有 create**），且 `ignoreException` 后端端点存在、无 404 缺口。故枚举不交集的**当前**后果只是「筛选下拉永远查不到」，而非写入即报错；但**一旦本批打开写入路径且有人用前端词表插数据，即变成 CHECK 硬错** ⇒ 仍需同期处理，选了代价最低的 B（改前端）。

**词表映射（裁定后落档，防后人误以为是随手改的）**：

| DB CHECK 4 值（保留） | 前端原 5 值（废弃） | 处置 |
|---|---|---|
| `AMOUNT_DIFF` 金额不符 | `AMOUNT_MISMATCH` | 合并到 `AMOUNT_DIFF` |
| `DATE_DIFF` 账期不符 | —— | 保留（前端原本无入口，补上） |
| `UNMATCHED` 未匹配到对手方 | `INVOICE_NOT_FOUND` | 合并到 `UNMATCHED` |
| `DUPLICATE` 重复匹配 | —— | 保留（前端原本无入口，补上） |
| —— | `PARTY_MISMATCH` | ⚠️ **DB 无对应值**。按 B 方案废弃；业务语义（往来单位不符）由 `UNMATCHED` 承载 |
| —— | `MATCH_FAILED` | ⚠️ **DB 无对应值**。废弃 |
| —— | `APPROVAL_REQUIRED` | ⚠️ **DB 无对应值**。废弃 |

⚠️ **遗留业务疑点（不阻塞本批，但需业务方后续确认）**：`PARTY_MISMATCH`（往来单位不符）在会计上与 `UNMATCHED`（未匹配到对手方）**并非严格同义**。本批按 B 方案统一到 4 值以解除 CHECK 硬错，但若业务上确实需要区分「单位不符」与「找不到对手方」，需**另行立项**给 DB 增补第 5 个合法值并同步前端。已登记，不在本 SPEC 范围。

---

## 3. 输入/输出契约与状态流转

**输入契约**（`createException`，签名不变）：
```
sourceDocType:String, sourceDocId:Long, targetDocType:String, targetDocId:Long,
partyId:Long, partyType:String, amount:BigDecimal, unsettledAmount:BigDecimal,
exceptionType:String, exceptionReason:String, matchSuggestion:String
```
⚠️ **注意 `exceptionType` 的合法值集合当前是三方不一致的**（见 D-109-2），本 SPEC 不擅自裁定。

**输出契约**：变更后 `R<ReconciliationExceptionEntity>` 将**新增** `sourceDocType`/`sourceDocId`/`targetDocType`/`targetDocId`/`partyId`/`partyType`/`matchSuggestion`/`retryCount`/`remark`/`description`/`updatedAt` 等字段的**实际值**（此前恒 null）。**这是加字段，不是删字段** ⇒ 对前端**向后兼容**（现有 `?? 0` 兜底不受影响）。

**状态流转**（实测 `status` 列**无 CHECK 约束**，故以下为代码事实而非 DB 约束）：

```
（无记录）
   │ createException → status='OPEN'   ← 当前 100% 因 F1 失败
   ▼
 OPEN ──resolveException──► RESOLVED
   │
   └──retryException──► （重新 execute 核销）+ retryCount+1 + resolvedBy/At
                          ← 当前 100% 因 F2 抛错
```
⚠️ 前端下拉含 `IGNORED`（已忽略），但**后端无任何路径写入该值** ⇒ 又一处前后端词汇不对齐，登记入 D-109-2 一并裁定。

## 4. 异常处理

| 场景 | 现状 | 修后要求 |
|---|---|---|
| `createException` 缺必填的业务上下文 | ❌ 撞 DB `NOT NULL(account_id)`，报错信息**不含业务语义**，用户无从得知缺什么 | 按铁律 #14：缺 `sourceDocType`/`targetDocType`/`exceptionType` 时抛 `BusinessException` 并**指明缺哪个**；`account_id` 若保留则由 Service 显式赋值或列改可空 |
| `retryException` 目标信息缺失 | ❌ 恒抛「缺少目标单据信息」（因字段恒 null，而非真的缺） | 修后该分支只在**真的**缺信息时触发 |
| `exceptionType` 非法 | ⚠️ 撞 DB CHECK，报错不含允许集 | 按 AGENTS §4.2 第 14 条：引用前查证允许集；建议 Service 侧前置校验并**在报错里列出合法值** |
| 表 0 行 | F3 | 无历史数据 ⇒ **不需要回填 migration**（这是方案 A 成本低的关键） |

---

## 5. BDD 验收标准（每个场景对应一个 `@Test`）

```gherkin
Scenario: 异常记录可被真实写入（当前撞 account_id NOT NULL）
  Given 企业上下文为 E，t_bank_account 存在合法账户
  When  调用 createException(sourceDocType='RECEIPT', targetDocType='PAYMENT',
        targetDocId=T, partyType='CUSTOMER', exceptionType=<合法值>)
  Then  不抛异常，且落库行 enterprise_id = E（负向：≠ DB 默认值 1）
  And   落库行 source_doc_type / target_doc_type / party_id 与入参一致（负向：不得为 null）

Scenario: 异常记录可被重试（当前恒抛「缺少目标单据信息」）
  Given 上一步写入的一条 status='OPEN' 的异常记录
  When  调用 retryException(id, userId)
  Then  不抛「缺少目标单据信息」，且产生一条核销日志
  And   retry_count 落库为 1（负向：不得因字段是幽灵而恒 null / NPE）

Scenario: 来源单据在读接口里可见（当前恒 null）
  Given 一条含 sourceDocType='RECEIPT' / sourceDocId=S 的异常记录
  When  调用 pageExceptions(...)
  Then  返回行中 sourceDocType='RECEIPT'、sourceDocId=S
  And   负向断言：不得为 null（前端页面依赖这两列渲染「来源」列）

Scenario: 备注与操作人真正落库（当前被静默丢弃）
  Given 一条 OPEN 异常
  When  调用 resolveException(id, userId, remark='人工已核')
  Then  落库行 remark='人工已核'、resolved_by=userId、resolved_at 非空

Scenario: 缺失业务上下文时抛可读异常而非 DB 约束错误
  When  调用 createException 但 targetDocType 为空
  Then  抛 BusinessException，且消息指明缺失字段名
  And   负向：不得抛 PSQLException / 不得出现 "violates not-null constraint"

Scenario: 守卫：Entity 声明的每个字段在真实表里都有列（防再次漂移）
  When  用反射枚举 ReconciliationExceptionEntity 的全部字段
  Then  每个非 exist=false 的字段都能在 information_schema 中找到同名列
  And   反向自检：把某个字段误标 exist=false 时本守卫必须转红
```

---

## 6. 实施分批（✅ 已按 §2 V1.1 裁定实施完毕）

| 批次 | 内容 | 门禁 |
|---|---|---|
| **1** | 按 D-109-1 结论补 Flyway（加列 / 改可空）+ 删 Entity 幽灵标注 + 补 `enterpriseId` | 三方对照审计（PG ↔ Entity ↔ 业务代码）；`node backend/scripts/check-entity-schema.mjs`；新 migration 号（**须先 `rg -l "V16" backend/src/main/resources/db/migration` 确认可用，当前最新 V168**） |
| **2** | BDD 场景 1~5 的真库用例（先 RED 后 Green） | L2 全绿；`All coverage checks have been met` |
| **3** | D-109-2 枚举对齐（依赖业务方确认词表映射） | 需老丁单独确认 |

⚠️ **批次 1 与 2 不可合并**：DDL 先行才能让 RED 用例以「真实 NOT NULL 报错」转红；若先改 Entity，RED 会变成 `column "source_doc_type" does not exist`，**症状不同、归因困难**（AGENTS §4.5 第 20 条同型的「归因」教训）。

⚠️ **覆盖率注意**：本批**不新增 DTO/VO**（只改 Entity 字段映射），故**不触发 DTO 覆盖率税**（AGENTS §4.5 第 30 条）；但删掉 12 处 `exist=false` 会让这 12 个字段**新增 Lombok getter/setter 计入分母**（此前 `@Data` 已生成，只是未被 SQL 触及）⇒ 需实测确认 METHOD 缓冲（当前 +2.17 点）足够。

---

## 7. 风险与明确不做

**不做**：
- ❌ 不改 `t_reconciliation_exception` 的既有行数据（F3 已证 0 行，无回填对象）
- ❌ 不动 `t_reconciliation_log`（那是另一张表，批次 1a-2 已收口）
- ✅ D-109-1 / D-109-2 已由老丁裁定（见 §2 V1.1），并已按裁定实施
- ✅ 前端只改了 `exceptionType` 词表三处（下拉/标签/配色），**未改任何页面结构或交互**

**风险**：

| 风险 | 缓解 |
|---|---|
| ~~若裁定 D-109-1 选 B，前端已投入的页面需重做~~ | ✅ **已裁定为 A**，该风险不成立 |
| ~~批次 1 放开写入后枚举不一致变成必现错误~~ | ✅ **已同期裁定并改前端**，另加 `ReconciliationExceptionPoolRepairRealDBTest#allLegacyFrontendValuesAreRejected` 锁死该边界 |
| 新加的列若含 `party_id`/`source_doc_id`，是否需要外键或索引未知 | 本批**不加外键**（与项目既有做法一致：`t_prepayment.vendor_id` 等均无 FK）；索引需求待有真实数据后再评估 |
| `enterprise_id` 写入路径依赖 `insertFill` 是否触发（AGENTS §4.5 第 34 条） | ✅ **已验证**：本 Entity **继承 `BaseEntity`**，其 `enterpriseId` 带 `@TableField(fill = INSERT)` ⇒ `withInsertFill = true` ⇒ 会触发。由 `enterpriseIdFollowsContextNotDbDefault` 用非默认企业号实测（落库值 = 上下文，≠ DB 默认 1） |
| 新增的 13 列与 Entity 的三方一致性 | ✅ 已由 `node backend/scripts/check-entity-schema.mjs` 复核 `✅ 通过`（注意该脚本查的是**开发库**，本地需先 `docker start huicai-postgres` 且确保它已应用 V169） |

---

## 8. 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| **V1.1** | 2026-10-06 | opencode | **D-109-1 / D-109-2 / D-109-3 已裁定并实施完毕（批次 1 + 2）**。**①裁定落档**：D-109-1 = **表迁就 Entity**（方案 A）；D-109-2 = **对齐到 DB 现有 4 值**（改前端，后端零改动）；D-109-3 = 保留 `retryCount`/`remark`/`createdBy` 三列（前端 `{{ row.retryCount ?? 0 }}` 已在用）。**②裁定前补测一处事实，修正了 V1.0 的影响面判断**：查清前端对异常池**只有读接口**（`pageReconciliationExceptions`/`resolve`/`ignore`/`retry`，**没有 create**），且 `ignoreException` 后端端点存在、无 404 缺口 ⇒ 枚举不交集的**当前**后果只是「筛选下拉永远查不到」，**不是** V1.0 写的「写入即报错」；但一旦打开写入路径且有人用前端词表插数据即变成 CHECK 硬错，故仍需同期处理。**③批次 1（DDL + Entity + 前端）**：新增 **`V169__p109_reconciliation_exception_party_columns.sql`** —— 补 13 列（`source_doc_type`/`source_doc_id`/`target_doc_type`/`target_doc_id`/`party_id`/`party_type`/`unsettled_amount`/`exception_reason`/`match_suggestion`/`retry_count`/`assigned_to`/`remark`/`created_by`）+ `account_id`/`period` 降为可空 + 2 个索引（异常池查询形状、来源回溯），全幂等（**重跑输出 15 条 skipping，已实测**）；Entity 删 12 处失效标注、`tenantId` 保持幽灵；前端下拉/标签/配色三处同步换成 DB 4 值。**④顺带修掉一处「字段遮蔽」造成的反向缺口**：该 Entity 原先**重复声明** `createdAt`/`updatedAt`/`createdBy`，遮蔽了 `BaseEntity` 里**可写入**的 `updatedAt`（`V127:34` 已加真实列）⇒ 表现为真实列永不写入（AGENTS §4.2 第 16 条）。现删掉重复声明改由基类提供；`createdBy` 例外 —— `V169` 补了真实列，故保留声明但去掉 `exist=false`，让 Service 里既有的 `setCreatedBy` 真正落库。**⑤Service 侧加前置校验**：`createException` 现在对缺 `sourceDocType`、非法 `exceptionType` 抛 `BusinessException` 并**列出合法值**（铁律 #14 + §4.2 第 9/14 条）；`retryException` 的 `getRetryCount() + 1` 补 null 兜底（原为幽灵字段 ⇒ 读回 null ⇒ 必然 NPE）。**⑥批次 2（回归锁）**：`ReconciliationExceptionEntityDbProbeTest`（取证，断言缺陷存在）**删除并由** `ReconciliationExceptionPoolRepairRealDBTest`（**12 例**，SPEC §5 场景 1~5 逐条改写为正向断言）取代；另同步修正既有 Mock 用例 `ReconciliationServiceImplTest#createException_创建异常记录` —— 它断言的 `PARTY_MISMATCH` **是永远不可能落库的值**（与 DB CHECK 不相交），属 Mock 结构上看不见约束的典型（§4.3 第 7 条）。**⑦反证矩阵**：给 `sourceDocType` 加回 `@TableField(exist=false)` ⇒ 打红 2 例；V169 去掉 `source_doc_type` 列 ⇒ 12 例全 error；**V169 不做 `account_id DROP NOT NULL` ⇒ 8 例 error 且复现出原缺陷的原文** `null value in column "account_id" ... violates not-null constraint`。**⑧一处「工具告警 ≠ 缺陷」的复核**：`check-entity-schema.mjs` 一度报 13 个「列不存在」，追查发现是**开发库停在 Flyway V99**（V169 已手工补上、重跑确认幂等），重跑该脚本即 `✅ 通过` ⇒ 不是 migration 缺陷（AGENTS §4.5 第 23 条）。**验证**：L1 `1651/0/0/5`、L2 `2074/0/0/6`，两次 `All coverage checks have been met`（L1 覆盖率 46.30/36.83/65.56 对阈值 45/36/65），`check_tenant_fixture.py` 与 `check_entity_status_massassignment.py` exit 0。**遗留业务疑点**：`PARTY_MISMATCH`（往来单位不符）与 `UNMATCHED` 会计上并非严格同义，本批按裁定统一到 4 值以解除 CHECK 硬错，若业务确需区分须另行立项给 DB 增补第 5 个合法值 |
| **V1.0** | 2026-10-06 | opencode | **首版，只提交取证结论与待决策项，未动任何生产代码。**取证载体 `ReconciliationExceptionEntityDbProbeTest`（4 例真库全绿）。**①实测定性推翻原登记**：本项不是「Entity 15 处注解写错」，而是**Entity 整体重构后配套 migration 从从未写过**（§0.4）—— 只删注解会让写入从「撞 NOT NULL」变成「撞 Unknown column」，问题原地不动。**②实测四条事实**：F1 `createException` 真实报错 `null value in column "account_id" ... violates not-null constraint`；F2 `retryException` 因 `targetDocType`/`targetDocId` 读回恒 null 而**对任何记录都抛**「缺少目标单据信息」；F3 表 `count(*)=0` ⇒ 缺陷从未被真实写入触发；F4 `tenant_id` 确认不存在 ⇒ 批次 1a-2 删死代码正确。**③发现四方不一致**（DB 账户-centric ↔ Entity/Service 往来单位-centric ↔ 前端第三套 `exceptionType` 枚举，且前端 5 值与 `chk_exception_type` 的 4 值**完全不交集**；前端还有 `IGNORED` 而后端无路径写入）。**④三层遮蔽解释「为何两年没人发现」**：唯一测试是 Mock（看不见 NOT NULL/FK/CHECK）+ 表是空的 + `createException` 无生产调用方（死路径），而前端页面已接真实端点。**⑤新增 §2 三项待决策**（D-109-1 模型取舍**阻塞 DDL**、D-109-2 枚举对齐、D-109-3 三字段落库），**⑥§6 标注批次 1/2 不可合并的原因**（先改 Entity 会让 RED 症状变成 Unknown column，归因困难），**⑦§7 风险表新增一条前置验证项**（`enterpriseId` 补字段后须确认 `insertFill` 是否触发，否则重蹈 §4.5 第 34 条覆辙） |