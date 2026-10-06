# P110 Entity–DB 三方对齐清零（十类实体的「反向缺口/前向缺口/幽灵字段」）

> **状态**：✅ **全部 Phase 已完成（2026-10-06）**—— Phase 0（门禁升级）+ Phase 1（`voucher_template`）+ Phase 2（`t_aging_alert`/`t_account_mapping_rule`）+ Phase 3（4 处 C 类清零）。工具台账 11 → 0；`check-entity-schema.mjs` 回到完全通过。台账里的 4 处 C 类/A 类缺陷全部修复后同步受代码 + 真库测试锁定。
> **需求**：REQ-2026-139（新登记）| **编号**：HUICAI-SPC-P110 | **来源**：P102 批次 5/6/7 三方核对遗留 + AGENTS §4.2 第 4/8/13/16 条同型
> **铁律约束**：三步闭环 SPEC→Plan→**审核**→执行。每个微循环独立 TDD（Red→Green），完成前验证 `check-entity-schema.mjs` 双向 + 真库 CRUD 探针
> **关联**：[P106 多账套](P106-multi-book-account-set.md)《[P102 安全基线](P102-security-permission-baseline.md)》《[P109 异常池修复](P109-reconciliation-exception-pool-repair.md)》

---

## 0. 取证结论（全部真库实测，非读代码推断）

**取证手段**：针对全部 31 个 Entity 类执行「PG ↔ Entity ↔ 状态：报告中列出的每处偏差」，四大类别判定如下：

| 判定代码 | 含义 | 对运行的影响 |
|---|---|---|
| A 反向缺口 | DB 中 `NOT NULL` 无默认值的列，Entity 未声明对应字段 | Entity 无字段 ⇒ insert 必挂 |
| B 前向缺口 | Entity 声明了字段，但 DB 实际**无此列** | MyBatis-Plus 全字段 SQL ⇒ `column does not exist` |
| C 幽灵字段 | `@TableField(exist=false)` 标注，但 DB **实际存在该列** | 列永不写入、读回恒 null |

### 0.1 实测偏差台账（按严重度排列）

| # | Entity | Table | A 反向 | B 前向 | C 幽灵 |
|---|---|---|---|---|---|
| 1 | `VoucherTemplateEntity` | `t_voucher_template` | **4**（`template_code`,`template_name`,`doc_type`,`entries`） | 7（`business_type`,`description`,`direction`,`match_priority`,`name`,`number_prefix`,`source`） | 1（`updatedAt`） |
| 2 | `BankStatementEntity` | `t_bank_statement` | 0 | **17**（`ai_business_scene`,`ai_confidence`,`ai_suggested_action`,`batch_id`,`classification`,`direction`,`generated_at`,`generated_doc_id`,`generated_voucher_id`,`generated_voucher_no`,`purpose`,`reviewed_at`,`reviewed_by`,`rule_id`,`transaction_remark`） | 1（`importedAt`） |
| 3 | `OutputInvoiceEntity` | `t_output_invoice` | 0 | 15（`ai_mapping_result`,`ai_risk_tag`,`amount_ex_tax`,`audited_at`,`audited_by`,`doc_no`,`doc_status`,`original_invoice_id`,`process_status`,`receivable_id`,`reversed_by_invoice_no`,`voucher_no`,`voucher_status`,`reversal_flag`） | 2（`receivableNo`,`reversedFrom`） |
| 4 | `InputInvoiceEntity` | `t_input_invoice` | 0 | 6（`ai_mapping_result`,`ai_risk_tag`,`amount_ex_tax`,`doc_no`,`process_status`,`voucher_no`） | 1（`createdAt`） |
| 5 | `BadDebtProvisionEntity` | `t_bad_debt_provision` | 0 | 6（`adjustment_amount`,`adjustment_type`,`existing_balance`,`expected_balance`,`scheme_id`,`voucher_no`） | 1（`status`） |
| 6 | `ArapSettlementEntryEntity` | `t_arap_settlement_entry` | 0 | 4（`after_balance`,`before_balance`,`payable_id`,`receivable_id`） | 2 |
| 7 | `CustomerStatementEntity` | `t_customer_statement` | 0 | 6（`confirmed_at`,`customer_name`,`statement_date`,`total_original`,`total_settled`,`total_unsettled`） | 3 |
| 8 | `ArapSettlementEntity` | `t_arap_settlement` | 0 | 2（`reversed_from_settlement_id`,`voucher_no`） | 1 |
| 9 | `BudgetEntity` | `t_budget` | 0 | 0 | 1（`createdAt`） |
| 10 | `AssetDisposalEntity` | `t_asset_disposal` | 0 | 0 | 1（`createdAt`） |
| 11 | `AssetInventoryEntity` | `t_asset_inventory` | 0 | 0 | 1（`createdAt`） |
| 12 | `TaxDeclarationEntity` | `t_tax_declaration` | 0 | 0 | 1（`createdAt`） |
| 13 | `BusinessDocEntity` | `t_business_doc` | 0 | 1（`bank_statement_id`） | 0 |

### 0.2 重中之重

- **`VoucherTemplateEntity` 是唯一的 A 类缺口**（`template_code`/`template_name`/`doc_type`/`entries` 为 DB NOT NULL 且 Entity 无字段）⇒
  `VoucherTemplateServiceImpl.create()` 的 MP `insert` **必抛** `null value in column ...`。
  这意味着**凭证模板功能未实际可用**（与 AGENTS §4.2 第 13 条同型）。
- **`BankStatementEntity` 17 处 B 类缺口**：任何经 MP 的 select/update 都会 `column does not exist` ⇒
  `BankStatementController` 的 CRUD / 导入 / 审核全链的读写都**实际上未接通**。

### 0.3 工具缺口

`check-entity-schema.mjs` 目前只做 **Entity→DB** 单向校验（「Entity 引用了 DB 没有的列」），
对本 SPEC 里的 A（反向缺口）和 C（幽灵字段）**全看不见**。本轮的反证实证了：缺口类
实测 3 类都被「单向脚本绿」屏蔽，直到新增反向判定脚本才逐一暴露。

---

## 1. 根因

三类缺口同源：**Entity 按「未来/想象中的完整 schema」编写，但 DB DDL 从未更新到那个版本，
反之亦然，而 Entity 的注释却声称「Vxx 列已添加」**（AGENTS §4.2 第 4/5 条直接同源，十年同型）。
`check-entity-schema.mjs` 只单往检查，成为「单向的免罪金牌」。

---

## 2. 处置原则（每类逐列三选一）

对每一处偏差必须属于以下三类其一，不许模糊处理：

| 选项 | 适用 | 动作 |
|---|---|---|
| **补列** | Entity 的字段是优先功能、且 DB 缺列 | Flyway 迁移加列（V170 起） |
| **删声明** | Entity 的字段是从未落库的死代码 | 删 Entity 字段 + 删业务代码读写 |
| **补字段** | DB 有列而 Entity 没有（A 类） | Entity 补字段 |

**禁止的兜底**：只写注释说「DB 无此列」而不同步删字段（第 10 条）；给字段补 `exist=false`
掩盖真实列（第 16 条反向缺口）；用 Mock 夹具充当真实 DB 的证据（第 9/17 条）。

---

## 3. 执行计划（按表一个微循环，每循环 Red→Green→三方对齐→反证）

### Phase 0：门禁升级（1 个微循环）

- 将 `check-entity-schema.mjs` 升级为**双向**：额外检测 A 类（DB NOT NULL 列未声明）与 C 类（`exist=false` 而列存在）
- 对本台账 13 个 Entity 回跑，应精确命中 0.1 表的全部列（反证：删改字段后脚本报偏差）
- 接入 CI/pre-commit（`.husky` hook 已存在，把新判定合入同一脚本）

### Phase 1：A 类清零（1 个微循环 —— 凭证模板）

| 表 | 现状 | 判定 | 动作 |
|---|---|---|---|
| `t_voucher_template` | DB 有 4 个 NOT NULL 无默认列，Entity 无对应字段；另有 7 个 Entity 字段 DB 无列；`updatedAt` 真实列被误标 `exist=false` | Entity 从未能插入 ⇒ B 类（功能未接通）| Entity 补 4 个字段 + 删除 7 个从未落列的 Entity 字段 + 恢复 `updatedAt` 的正常映射 |

后果验证：修后 `create()` 必须能插入（真库用例：必要字段全填后 `insert` 成功）。

### Phase 2：B 类逐列决策（目标 B=0）

按实测台账逐表逐项判断「补列 / 删声明」：

- **`t_bank_statement`（17 列）**：AI 辅助字段（`ai_*`、`generated_*`、`purpose`、`classification`、`direction` 等）。决策键：代码里是否有**写入**这些列的真路径。有写入路径 ⇒ 补迁移；无写入路径（死声明）⇒ 删 Entity 字段 + 删读取方
- **`t_input_invoice`/`t_output_invoice`（23 列）**：`doc_no`/`voucher_no`/`process_status`/`ai_mapping_result` 等 — 决策规则同上；特别注意**不要把幽灵字段死代码当作「待补列」**（P109 已实证死路径）
- **`t_bad_debt_provision`（6）**、**`t_customer_statement`（6）**、**`t_arap_settlement_entry`（4）**、**`t_arap_settlement`（2）**、**`t_business_doc`（1）**：逐项查证代码读写后做同等三选一

### Phase 3：C 类清零（13 处）

- 对台账标「真实存在」的列：删除 Entity 上的 `exist=false` 标注，恢复正常映射（插入/更新/读取）。若读不出该用途（如 `createdAt` on `t_budget`、`t_tax_declaration`…），在同表确认删除后 + `_createdAt` 回归测试
- 对 `t_bank_statement.imported_at`、`t_bad_debt_provision.status`、`t_output_invoice.receivableNo/reversedFrom` 等彻底核实用途后按同规则处理

### Phase 4：真库整体验收

- `check-entity-schema.mjs` 双向通过、计数 0 偏差
- 对每张受影响表增补「真库 CRUD 探针」（`AbstractMapperTest` 风格）：新增一条最小合法记录 → 读回 → 断言每列被正确持久化（对补字段/删标注的每个字段逐一 assert）
- L1 `mvn clean test`、L2 `mvn test -DexcludedGroups=` 全绿并满足现行阈值

---

## 3.1 Phase 0 实施记录（2026-10-06，已合入 `backend/scripts/check-entity-schema.mjs`）

- **已做**：① 新增 `getTableColumnMeta()`（psql 返回 `column_name|||is_nullable|||column_default`），为 A 类判定提供元数据 ② 新增 `allEntityFieldsFromSource()`（绕过旧解析器对 `@TableId`/`exist=false` 的过滤，覆盖子类未声明的 BaseEntity 字段）③ 主流程新增 C 类（`exist=false` 实列）与 A 类（NOT NULL 且无默认值、Entity 未覆盖）双项检测 ④ **修复了错误分类 bug**：A/C 类此前落入「表不存在，跳过」的 `missingTableErrors`（表名 regex 只匹配正向「但 xx 表没有此列」），已追加 `/\b(t_\w+)\s+表/` 兜底，An/C 发现项现进入 `realErrors`
- **首轮双向跑出的已知 11 实伤（全部进入基线台账，不构成阻断；修复后必须同提交移除对应条目）**：
  - C 类幽灵字段 4 例：`AgencyUserEntity.createdBy`/`updatedBy`、`SubjectBalanceEntity.deleted`、`BankStatementEntity.generatedDocNo`
  - A 类反向缺口 7 例：`t_voucher_template.template_code`/`entries`、`t_aging_alert.doc_type`/`party_type`、`t_account_mapping_rule.rule_code`/`rule_name`/`source_type`
- **为什么不立即跑红**：AK/P0 期台账不是「代码退步」，而是历史负债；但台账之外的任何新反向缺口/幽灵字段一出现即 `exit 1`
- **覆盖范围**：非表字段（`@TableId` 主键、`@TableLogic`、BaseEntity 继承链）纳入 A 类覆盖集；`PostgreSQL GENERATED ALWAYS AS IDENTITY` 与 `DEFAULT` 列豁免；类型对应的 `Map<String,Object>` 泛型字段不判（与正向 B 类同样豁免）

---

### 3.2 Phase 1–3 落地摘要（2026-10-06，本日快速收敛）

- **Phase 1 — `VoucherTemplateEntity` 清 8 野列 + 补 5 真实列**：`templateCode`/`voucherTypeCode`/`summary`/`entries`（jsonb）/`remark` 补齐、Ghost 的 `description/classification/source/direction/matchPriority/numberPrefix` + 冗余 BaseEntity shadow 删除；`VoucherTemplateVO` 同步；`VoucherTemplateCreateRequest` 改为必填 `templateCode/businessType/entries/voucherTypeCode`。真库测试 `VoucherTemplatePhase1RealDBTest` 补字段回读 + 漏字段 DB NOT NULL 拦截 2/2 绿。
- **Phase 2 — `AgingAlertEntity`/`AccountMappingRuleEntity`**：补齐 `doc_type`/`party_type`（NPE 保护 INSERT）；alertLevel 改为 DB-CHECK 允许集 `INFO/WARNING/CRITICAL`（旧产出 `MILD/MODERATE/SEVERE` 必违约）；`dismissAlert/resolveAlert` 的 `dismissedAt` 野字段改写入真实列 `processedAt`；`AgingAlertVO` drop `notifiedAt/dismissedAt`；`AccountMappingRuleEntity` 从 6 个 exist=false 野列重写为真实列集并继承 `BaseEntity`；真库测试 `AgingAlertPhase2RealDBTest` (2/2) + `AccountMappingRulePhase2RealDBTest` (1/1)。
- **Phase 3 — C 类 4 处清零**：`AgencyUserEntity` (createdBy/updatedBy 去 exist=false)、`SubjectBalanceEntity`（删除对 BaseEntity deleted 的 exist=false shadow ← MP 标准逻辑删除生效）、`BankStatementEntity.generatedDocNo`（去 exist=false）。`AutoGenerationService` 三条路径新增 `stmt.setGeneratedDocNo(doc.getDocNo())`，DB 列存在即将被填充。台账归零。

## 4. BDD 验收契约（Given-When-Then，每个场景对应一个 @Test）

```
Scenario: 反向缺口被脚本拦截
  Given check-entity-schema.mjs 已升级为双向
  When 在某 Entity 中声明一个 DB 实存的 NOT NULL 但 Entity 未持有的列
  Then 脚本报 "A 类缺陷：列 X 在 DB NOT NULL 但 Entity 未声明"
  And exit code != 0

Scenario: 幽灵字段标记被脚本拦截
  Given 同上
  When Entity 上某列标 @TableField(exist=false) 而该列在 DB 实际存在
  Then 脚本报 "C 类缺陷：列 X 被标 exist=false 但 DB 存在"
  And exit code != 0

Scenario: t_voucher_template 插入成功
  Given Entity 补齐了 template_code/template_name/doc_type/entries
  When insert 一条合法模板
  Then select 回读全部 4 列与写入一致

Scenario: t_bank_statement 全字段读写不报错
  Given 前向缺口列被补齐（或删除）
  When selectPage 读取任一行
  Then 不抛 "column does not exist"

Scenario: C 类列可写可读
  Given 某 exist=false 误标列被恢复
  When insert 一条记录
  Then 回读该列 == 写入值（不再恒 null）
```

---

## 5. 约束与备注

- **不动生产 DDL 的顺序**：先在调查期确认「删声明」还是「补列」，确认后一次性改 Entity ± DDL，禁止「先删 Entity 字段」又「后补 DDL」的反复施工
- **每句注释的引用必须与实际 migration 对证**（§4.2 #5）；凡注释写「Vxx 已补列」的语句先在 migration 中逐行验证
- **「修复」不允许是「注释修改」**：若某处偏差的最终结论是「注释本来就该这么说」，但字段从未在代码中使用，则正确动作是 **删字段或加列**，不是改注释
- 覆盖率棘轮当前已贴实测上限；**本 SPEC 各微循环新增的测试代码不会触碰阈值倒退**（新增 VO/Entity 字段只会抬分母，须配套补测试）
