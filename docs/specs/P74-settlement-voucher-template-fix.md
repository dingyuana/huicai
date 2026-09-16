# P74 SPEC — 核销单凭证模板科目修正（预收冲应收）

> **版本**：V1.0 | **最后修改**：2026-09-15 | **作者**：Sisyphus
> **状态**：✅ 已实现（V130 源文件修正 + V140 生产数据修正）
> **编号**：HUICAI-SPC-074 | 优先级：高（P74）
> 依据：核销链路端到端验证发现 `TPL_SETTLEMENT_RECEIVABLE`/`TPL_SETTLEMENT_PAYMENT` 模板科目错误，导致收款单已贷2203预收后核销再借1002银行存款 = 双重记账
> 目标：修正核销模板科目，使核算轨会计周期闭合
> 工期：1 天（修复 + 验证 + 迁移）

> **关联需求**: REQ-2026-079（银行流水核销体验优化，P73 批1验收延伸）、REQ-2026-007（自动制证）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-15 | 初版。根因：V130 种子模板科目为 借1002/贷1122（应收核销），与 P12-3 逻辑（无未结清应收→贷2203预收）冲突。修复：模板改为 借2203/贷1122（应收）、借2202/贷1123（应付）。新增 V140 修正迁移 + 端到端验证测试 |
| V1.1 | 2026-09-16 | 遗留#1 闭环：端到端测试断言方向反转——mock 修正后模板（借2203/贷1122），断言预收冲应收正确行为 + 负向断言（不得再借1002）。测试改名 `e2e_settlementOffsetsPrepaymentAgainstReceivable`，31 测试绿 |

---

## 0. 背景与问题

### 0.1 问题发现过程

在 P73 批1验收后，主动验证核销链路是否闭环。通过端到端测试发现：

1. **收款单 P12-3 逻辑正确**：当客户无未结清应收时，收款单凭证贷 `2203 预收账款`（而非硬编码的 `1122 应收账款`）。存量凭证 `SK2024120032` 确认：借1002/贷2203
2. **核销模板科目错误**：`TPL_SETTLEMENT_RECEIVABLE` 模板分录为 借1002(银行存款)/贷1122(应收账款)，核销时再次借记银行存款
3. **双重记账**：预收模式下，银行存款被借记两次（收款+核销），预收2203未冲销

### 0.2 会计影响

```
错误周期：
  收款单→凭证 [借1002/贷2203]  ← 正确
  核销→凭证   [借1002/贷1122]  ← 错误！银行存款重复借记
  净效果：银行存款 2×1002，2203 未冲销 ← 资产虚增、负债未清

正确周期：
  收款单→凭证 [借1002/贷2203]  ← 正确
  核销→凭证   [借2203/贷1122]  ← 正确！预收冲应收
  净效果：银行存款1002，收入确认 ← 账实相符
```

---

## 1. 竞品对标

| 竞品 | 核销模板科目 | 差异点 | 结论 |
|------|-------------|--------|------|
| **用友 T+** | 预收冲应收：借2203/贷1122 | 科目完全一致 | ✅ 最佳实践 |
| **金蝶** | 预收冲应收：借2203(客户辅助)/贷1122(客户辅助) | 科目一致，金蝶额外支持辅助核算维度 | ✅ 最佳实践 |
| **SAP** | 类似预收清账逻辑 | 使用特殊总账标识区分预收/应收 | 架构不同，结论一致 |

> **金蝶官方文档**（tdxsoft.com）："财务科目上设有预收科目，预收业务计入预收账款科目。应收核销单：**借：预收账款**，贷：应收账款"
> **用友 T+**（畅捷通社区）："使用预收后，系统自动生成预收冲应收冲销单，生成凭证体现预收账款"

**结论**：修复方案完全符合国内主流财务软件的最佳实践。慧财使用 2203/1123 独立科目，与用友/金蝶一致。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | V130 源文件修正：应收模板 1002→2203，应付模板 1002→1123 | `V130__add_settlement_voucher_templates.sql` | ✅ 低 | ✅ 已实现 |
| 2 | P0 | V140 修正迁移：全企业 UPDATE template_line + JSONB entries | `V140__fix_settlement_template_subjects.sql` | ⚠️ 中 | ✅ 已实现 |
| 3 | P0 | 端到端验证测试：`e2e_settlementDoubleCountsBankDeposit` | `ArapSettlementServiceImplTest.java` | ✅ 低 | ✅ 已实现 |
| 4 | P1 | 新增 `matchByClassification` 使用 `entries -> 0 ->>` 路径的正确语法说明 | 迁移文档 | ✅ 低 | ✅ 已实现 |

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 输入契约

- **触发条件**：`ArapSettlementServiceImpl.generateVoucher(settlementId)` 被调用
- **前置条件**：核销单状态 = CONFIRMED，无已有 voucher_id
- **模板查询**：`voucherTemplateService.matchByClassification("settlement_receivable"|"settlement_payment")`

### 3.2 输出契约

- **核销凭证**：借方科目为 2203（预收）/2202（应付），贷方科目为 1122（应收）/1123（预付）
- **核销单状态**：CONFIRMED → VOUCHERED
- **凭证状态**：DRAFT

### 3.3 状态流转

```
CONFIRMED → VOUCHERED（generateVoucher）
  └─ 前提：template_line.subject_id 正确映射到 2203/1123（而非 1002）
  └─ 核销单 → 凭证双向溯源：settlement.voucher_id + voucher.source_doc_no = settlement.settlementNo
```

### 3.4 异常处理

- 模板未配置 → 抛 `BusinessException("核销单凭证模板")`（沿用现有逻辑）
- 已生成凭证 → 抛 `BusinessException("已生成凭证")`（沿用现有逻辑）
- 科目映射失败 → V140 迁移的验证 DO 块 `RAISE WARNING` 报警

---

## 4. BDD 验收场景

### 场景 1：应收核销模板科目正确

```gherkin
Given 核销单状态为 CONFIRMED
When generateVoucher 被调用
Then 核销凭证借记科目为 2203（预收账款）
And 核销凭证贷记科目为 1122（应收账款）
And 银行存款 1002 未被核销凭证借记
```

**测试验证**：`ArapSettlementServiceImplTest#e2e_settlementDoubleCountsBankDeposit`
- Mock 模板分录为 借731(1002)/贷6(1122)
- 断言 `assertFalse(hasPrepaymentDebit)`：核销凭证未出现 2203
- 该测试在修复前 PASS（确认缺陷存在），修复后需更新为断言 `assertTrue(hasPrepaymentDebit)`

### 场景 2：应付核销模板科目正确

```gherkin
Given 应付核销单状态为 CONFIRMED
When generateVoucher 被调用
Then 核销凭证贷记科目为 1123（预付账款）
And 银行存款 1002 未被核销凭证贷记
```

### 场景 3：全企业模板一致性

```gherkin
Given 所有企业的核销模板
When 查询 TPL_SETTLEMENT_RECEIVABLE 和 TPL_SETTLEMENT_PAYMENT 的 template_line
Then 所有借方科目均为 2203（应收核销）或 2202（应付核销）
And 所有贷方科目均为 1122（应收核销）或 1123（应付核销）
And 无 1002 科目出现在核销模板分录中
```

**验证 SQL**：V140 迁移的验证 DO 块

---

## 5. 修复详情

### 5.1 V130 源文件修正

**文件**：`backend/src/main/resources/db/migration/V130__add_settlement_voucher_templates.sql`

```diff
- -- 借：银行存款(1002) / 贷：应收账款(1122)
+ -- 借：预收账款(2203) / 贷：应收账款(1122)
  INSERT ... VALUES (8, 'TPL_SETTLEMENT_RECEIVABLE', ..., 
-  '{"debitSubjectCode": "1002", "creditSubjectCode": "1122"}', TRUE, 1)
+  '{"debitSubjectCode": "2203", "creditSubjectCode": "1122"}', TRUE, 1)
- (SELECT id FROM t_subject WHERE code = '1002' AND enterprise_id = 1)  ← debit
+ (SELECT id FROM t_subject WHERE code = '2203' AND enterprise_id = 1)  ← debit

- -- 借：应付账款(2202) / 贷：银行存款(1002)
+ -- 借：应付账款(2202) / 贷：预付账款(1123)
  INSERT ... VALUES (9, 'TPL_SETTLEMENT_PAYMENT', ...,
-  '{"debitSubjectCode": "2202", "creditSubjectCode": "1002"}', TRUE, 1)
+  '{"debitSubjectCode": "2202", "creditSubjectCode": "1123"}', TRUE, 1)
- (SELECT id FROM t_subject WHERE code = '1002' AND enterprise_id = 1)  ← credit
+ (SELECT id FROM t_subject WHERE code = '1123' AND enterprise_id = 1)  ← credit
```

### 5.2 V140 修正迁移

**文件**：`backend/src/main/resources/db/migration/V140__fix_settlement_template_subjects.sql`

```sql
-- 应收核销：借方科目从1002改为2203（按企业映射）
UPDATE t_voucher_template_line tpl
SET subject_id = (SELECT s2203.id FROM ... WHERE s2203.code = '2203' ...)
WHERE ... AND direction = 'debit' AND subject_id IN (SELECT id FROM t_subject WHERE code = '1002');

-- 应付核销：贷方科目从1002改为1123（按企业映射）
UPDATE t_voucher_template_line tpl
SET subject_id = (SELECT s1123.id FROM ... WHERE s1123.code = '1123' ...)
WHERE ... AND direction = 'credit' AND subject_id IN (SELECT id FROM t_subject WHERE code = '1002');

-- JSONB entries 同步修正
UPDATE t_voucher_template SET entries = jsonb_set(entries, '{0, debitSubjectCode}', '"2203"')
WHERE template_code = 'TPL_SETTLEMENT_RECEIVABLE' AND entries -> 0 ->> 'debitSubjectCode' = '1002';

UPDATE t_voucher_template SET entries = jsonb_set(entries, '{0, creditSubjectCode}', '"1123"')
WHERE template_code = 'TPL_SETTLEMENT_PAYMENT' AND entries -> 0 ->> 'creditSubjectCode' = '1002';
```

### 5.3 测试文件

**文件**：`backend/src/test/java/com/huicai/sme/arap/service/impl/ArapSettlementServiceImplTest.java`

新增方法 `e2e_settlementDoubleCountsBankDeposit`，验证：
1. 核销凭证借记了银行存款 1002（缺陷确认）
2. 核销凭证未出现 2203 预收科目（缺陷确认）
3. 修复后应反转断言

---

## 6. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | 8 张模板分录行（4 企业 × 2 类型）subject_id 更新 |
| 会计逻辑 | 核销凭证从双重记账 → 正确的预收冲应收 |
| 前端 | 无影响（核销工作台显示不变） |
| API | 无影响（generateVoucher 接口签名不变） |
| 测试 | +1 新测试（e2e_settlementDoubleCountsBankDeposit）|

---

## 7. 遗留事项

- ~~测试断言方向反转~~（V1.1 已闭环：`e2e_settlementOffsetsPrepaymentAgainstReceivable` 断言预收冲应收正确行为）
- P73 批2（小额直制证阈值 + 仪表盘待核销提醒）待启动
