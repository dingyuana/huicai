# P38 SPEC — 三流程溯源缺口修复

> **状态**: 待实施 | **优先级**: 高（P0-P2）
> **依据**: 三流程设计对比与代码审核报告（2026-07-04）
> **目标**: 修复发票流、银行流水流、核销流三个流程中发现的 10 个数据一致性与追溯缺口
> **核心原则**: 人是唯一审核主体，系统只允许创建草稿（已逐项核验）

---

## 0. 问题清单总览

| # | 优先级 | 流程 | 问题 | 文件 | 行号 |
|---|--------|------|------|------|------|
| F1 | **P0** | 发票流 | BusinessDoc 生成凭证后状态设为 DRAFT 而非 VOUCHERED | `OutputInvoiceStateMachineServiceImpl.java` | L356 |
| F2 | **P0** | 发票流 | auditedBy/auditedAt 因 @TableField(exist=false) 无法持久化 | `OutputInvoiceEntity.java` | L90-L95 |
| F3 | **P0** | 银行流 | Voucher 缺失 sourceDocType/sourceDocId/sourceDocNo 追溯 | `AutoGenerationService.java` | L535-L555 |
| F4 | **P0** | 核销 | 核销后发票状态未同步（onReconciliationUpdate 未调用） | `ReconciliationServiceImpl.java` | L271-L361 |
| F5 | **P0** | 核销 | Settlement.confirm() receivableId/payableId 旧路径静默跳过 | `ArapSettlementServiceImpl.java` | L138-L143 |
| F6 | **P1** | 银行流 | BusinessDoc 无 bankStatementId 反向追溯 | `AutoGenerationService` + `BusinessDocEntity` | — |
| F7 | **P1** | 核销 | 核销自动制证缺失 sourceDocType/sourceDocId/sourceDocNo | `ReconciliationServiceImpl.java` | L397-L406 |
| F8 | **P1** | 核销 | Settlement.generateVoucher() 不回写 BusinessDoc 状态为 VOUCHERED | `ArapSettlementServiceImpl.java` | L252-L268 |
| F9 | **P2** | 银行流 | audit() 与 generateVoucher() 代码重复 | `BankStatementServiceImpl.java` | L425-L500 |
| F10 | **P2** | 发票流 | fillOutputInvoiceDetails 查询失败时保留冗余值（隐式行为） | `TaxServiceImpl.java` | L289-L302 |

## 1. 当前流程与问题

### 1.1 发票流（F1, F2, F10）

```
发票 confirm()  [人触发]
  → createBusinessDocFromInvoice()
     → BusinessDoc(status=DRAFT) ← 创建草稿 ✅ 符合原则
  → generateVoucherFromInvoiceDirect()
     → taxService.generateVoucherFromInvoice()
        → Voucher(DRAFT)  ← 创建草稿 ✅ 符合原则
        → markVouchered() → invoice=VOUCHERED
     → 回写 BusinessDoc: voucherId/voucherNo/status=DRAFT ← ❌ F1
```

**F1** (严重): 此时 BusinessDoc 已有凭证，状态应为 VOUCHERED。人的触发（confirm）已包含对生成单据的认可，不违反"人是唯一审核主体"原则。

**F2** (严重): `confirm()` 设置 `auditedBy`/`auditedAt`，但 entity 标记 `@TableField(exist = false)`。V63 migration 已在 `t_output_invoice` 添加 `audited_by`/`audited_at` 列，是 Entity 声明错误。

### 1.2 银行流水流（F3, F6, F9）

```
BankStatement CONFIRMED → audit() [人触发]
  → autoGenerate()
     → generateDocThenVoucher()
        → createBusinessDoc(RECEIPT/PAYMENT, DRAFT)
        → createVoucher(stmt, period, amount, userId)
           → Voucher(DRAFT)
             ❌ sourceDocType 未设置 ← F3
             ❌ sourceDocId 未设置    ← F3
             ❌ sourceDocNo 未设置    ← F3
```

**F3** (严重): `AutoGenerationService.createVoucher()` 创建的凭证无法追溯到源头银行流水。对比：发票流的 `TaxServiceImpl.generateVoucherFromInvoice()` 正确设置了 `sourceDocType=OUTPUT_INVOICE`。

### 1.3 核销流（F4, F5, F7, F8）

```
核销 execute() [人触发]
  → 更新 BusinessDoc.settledAmount/unsettledAmount/status
  → 创建 ReconciliationLog
  → createReconciliationVoucher()
     → Voucher(DRAFT) ❌ sourceDocType 未设置 ← F7
  ❌ 不调用 OutputInvoiceStateMachineService.onReconciliationUpdate() ← F4
     → 发票状态停留在 VOUCHERED，不反映核销结果
```

```
Settlement confirm() [人触发]
  → 遍历 entries
     → businessDocId 路径: 更新 BusinessDoc ✅
     → receivableId/payableId 路径: 只打 DEBUG log ❌ 静默跳过 ← F5
  → status=CONFIRMED  ← 即使什么都没做也确认
```

**F4** (严重): 核销后 BusinessDoc 状态变为 FULLY_RECONCILED/PARTIALLY_RECONCILED，但关联的发票状态不动。`OutputInvoiceStateMachineService.onReconciliationUpdate()` 方法已定义未调用。

**F5** (严重): Settlement 旧格式（receivableId/payableId）数据静默跳过，用户以为核销成功，实际未更新任何数据。

---

## 2. 详细修改设计

### 2.1 F1 — BusinessDoc 状态设为 VOUCHERED (P0)

**文件**: `OutputInvoiceStateMachineServiceImpl.java` L356

**修改**:
```java
// 改前:
doc.setStatus("DRAFT");

// 改后:
doc.setStatus("VOUCHERED");
```

**理由**: 
- 人在发票级已确认（confirm 触发生成）
- 凭证已创建（`voucherId`/`voucherNo` 已回写）
- DRAFT 意味着"尚未生效"，与已有凭证矛盾
- 系统无独立 BusinessDoc 审核 UI 环节，设为 VOUCHERED 是执行结果而非系统自动审批

**原则核验**: 符合。人触发 `confirm()` → 系统执行创建单据+凭证 → 设 VOUCHERED 是执行结果。

### 2.2 F2 — auditedBy/auditedAt 去 exist=false (P0)

**文件**: `OutputInvoiceEntity.java` L90, L94

**修改**:
```java
// 改前:
@TableField(exist = false)
private Long auditedBy;

@TableField(exist = false)
private LocalDateTime auditedAt;

// 改后:
/** 审核人ID（V63 已添加列） */
private Long auditedBy;

/** 审核时间（V63 已添加列） */
private LocalDateTime auditedAt;
```

**影响**: V63 migration 已在 `t_output_invoice` 添加两列，去注释后 MyBatis-Plus 正常写入。

**原则核验**: 符合。此修复让《审计追踪》铁律（§1.5 红线）真正生效。

### 2.3 F3 — 银行流水 Voucher 添加来源追溯 (P0)

**文件**: `AutoGenerationService.java` L535-L555（`createVoucher()` 方法）

**修改**: 在插入 Voucher 前添加三个字段
```java
// 新增：来源追溯字段（凭证 → 银行流水）
voucher.setSourceDocType("BANK_STMT");
voucher.setSourceDocId(stmt.getId());
voucher.setSourceDocNo(stmt.getExternalNo());
```

**原则核验**: 符合。元数据增强，不涉及状态变更。

### 2.4 F4 — 核销后同步发票状态 (P0)

**文件**: `ReconciliationServiceImpl.java`（`execute()` 方法，约 L330）

**修改**: 在更新 BusinessDoc 状态后，查找关联的发票并调用状态机

```java
// 新增：核销后同步发票状态
if (request.targetDocType() != null && request.targetDocId() != null) {
    BusinessDocEntity bizDoc = businessDocMapper.selectById(request.targetDocId());
    if (bizDoc != null && bizDoc.getInvoiceId() != null) {
        try {
            outputInvoiceStateMachineService.onReconciliationUpdate(
                bizDoc.getInvoiceId(),
                bizDoc.getUnsettledAmount(),
                DEFAULT_USER_ID);
            log.info("核销同步发票状态: invoiceId={}, targetDocId={}, unsettled={}",
                bizDoc.getInvoiceId(), request.targetDocId(), bizDoc.getUnsettledAmount());
        } catch (Exception e) {
            log.warn("核销同步发票状态失败(不影响核销): {}", e.getMessage());
        }
    }
}
```

**依赖**: `ReconciliationServiceImpl` 需注入 `OutputInvoiceStateMachineService`。

**原则核验**: 符合。人触发核销 → 系统执行更新发票状态作为执行结果。状态转换受 `onReconciliationUpdate()` 状态机方法约束：
- VOUCHERED + unsettledAmount==0 → FULLY_RECONCILED
- VOUCHERED + unsettledAmount>0 → PARTIALLY_RECONCILED

### 2.5 F5 — Settlement 旧路径抛出异常 (P0)

**文件**: `ArapSettlementServiceImpl.java` L138-L143

**修改**:
```java
// 改前:
} else if (entry.getReceivableId() != null) {
    log.debug("核销明细关联应收单但未迁移至 BusinessDoc，跳过更新: receivableId={}", entry.getReceivableId());
} else if (entry.getPayableId() != null) {
    log.debug("核销明细关联应付单但未迁移至 BusinessDoc，跳过更新: payableId={}", entry.getPayableId());
}

// 改后:
} else if (entry.getReceivableId() != null) {
    throw new BusinessException("核销明细仍使用旧格式(receivableId)，请迁移至 businessDocId: id=" + entry.getReceivableId());
} else if (entry.getPayableId() != null) {
    throw new BusinessException("核销明细仍使用旧格式(payableId)，请迁移至 businessDocId: id=" + entry.getPayableId());
}
```

**原则核验**: 符合。主动报错防静默数据不一致。P34 过渡期已结束（V73 已迁移数据），应强制使用新路径。

### 2.6 F6 — BusinessDoc 添加 bankStatementId 反向追溯 (P1)

**文件**: 
- `BusinessDocEntity.java` — 新增字段
- `AutoGenerationService.java` — 回写 bankStatementId

**BusinessDocEntity 修改**:
```java
/** 来源银行流水ID（反向追溯银行流水 → 单据） */
private Long bankStatementId;
```

**DB**: V78 migration
```sql
ALTER TABLE t_business_doc ADD COLUMN bank_stmt_id BIGINT REFERENCES t_bank_statement(id);
COMMENT ON COLUMN t_business_doc.bank_stmt_id IS '来源银行流水ID（反向追溯）';
CREATE INDEX idx_t_business_doc_bank_stmt_id ON t_business_doc(bank_stmt_id);
```

**AutoGenerationService 修改**: 在 `generateDocThenVoucher()` 创建 BusinessDoc 后回写：
```java
doc.setBankStatementId(stmt.getId());
```

**原则核验**: 符合。元数据增强。

### 2.7 F7 — 核销自动制证添加来源追溯 (P1)

**文件**: `ReconciliationServiceImpl.java` L397-L406 (`createReconciliationVoucher()`)

**修改**:
```java
// 新增：来源追溯字段（凭证 → 核销来源）
voucher.setSourceDocType("RECONCILIATION");
voucher.setSourceDocId(request.sourceDocId());
voucher.setSourceDocNo(request.remark() != null ? request.remark() : "");
```

**原则核验**: 符合。元数据增强。

### 2.8 F8 — Settlement generateVoucher 回写 BusinessDoc 状态 (P1)

**文件**: `ArapSettlementServiceImpl.java` L252-L268

**修改**: 在回写 voucherId/voucherNo 后，将 BusinessDoc 状态设为 VOUCHERED

```java
if (doc != null && doc.getVoucherNo() == null) {
    doc.setVoucherNo(voucherNo);
    doc.setVoucherId(voucher.getId());
    doc.setStatus("VOUCHERED");  // ← 新增
    ...
}
```

**理由**: BusinessDoc 已有凭证，状态应从 PARTIALLY_RECONCILED/FULLY_RECONCILED 推进到 VOUCHERED。

**原则核验**: 符合。人在核销单级已确认（confirm → generateVoucher 是后续执行）。

### 2.9 F9 — audit() / generateVoucher() 去重 (P2)

**文件**: `BankStatementServiceImpl.java`

**修改**: 提取公共方法 `doAutoGenerate()`，两个入口各自调用。

**原则核验**: 代码质量，不涉及业务状态。

### 2.10 F10 — fillOutputInvoiceDetails 回退增强 (P2)

**文件**: `TaxServiceImpl.java`

**修改**: 当 doc/voucher 查询失败时，显式从 DB 读取原值保持，而非隐式使用 entity 中已有的冗余值。

**原则核验**: 数据准确性增强。

---

## 3. 修改清单（实施顺序）

| # | 优先级 | 文件 | 变更类型 | 涉及修改 |
|---|--------|------|---------|---------|
| 1 | P0 | `OutputInvoiceStateMachineServiceImpl.java` | L356: `DRAFT`→`VOUCHERED` | 1 行 |
| 2 | P0 | `OutputInvoiceEntity.java` | L90, L94: 去除 `@TableField(exist=false)` | 2 行 |
| 3 | P0 | `AutoGenerationService.java` | L535-555: createVoucher() 添加 3 行 sourceDoc 字段 | 3 行 |
| 4 | P0 | `ReconciliationServiceImpl.java` | execute() 新增注入 + 调用 onReconciliationUpdate | ~20 行 |
| 5 | P0 | `ArapSettlementServiceImpl.java` | L138-143: log→throw BusinessException | 4 行 |
| 6 | P1 | `BusinessDocEntity.java` + V78 migration | 新增 bankStatementId 字段 | ~10 行 |
| 7 | P1 | `AutoGenerationService.java` | generateDocThenVoucher() 回写 bankStatementId | 1 行 |
| 8 | P1 | `ReconciliationServiceImpl.java` | createReconciliationVoucher() 添加 3 行 sourceDoc 字段 | 3 行 |
| 9 | P1 | `ArapSettlementServiceImpl.java` | generateVoucher() 回写 BusinessDoc VOUCHERED 状态 | 1 行 |
| 10 | P2 | `BankStatementServiceImpl.java` | 提取公共方法 audit/generateVoucher | ~15 行 |
| 11 | P2 | `TaxServiceImpl.java` | fillOutputInvoiceDetails 增强 | ~5 行 |

---

## 4. 状态机变更

### OutputInvoice 状态机新增转换

```yaml
# 本次新增: VOUCHERED → FULLY_RECONCILED (已有实现, 现在被调用)
# 本次新增: VOUCHERED → PARTIALLY_RECONCILED (已有实现, 现在被调用)
```

### BusinessDoc 状态机说明

本次修改涉及 BusinessDoc 状态转换：
- `DRAFT → VOUCHERED`（F1: 发票 confirm 后直接设为 VOUCHERED）
- `PARTIALLY_RECONCILED → VOUCHERED`（F8: 核销单生成凭证后推进状态）
- `FULLY_RECONCILED → VOUCHERED`（F8: 同上）

> 注意：VOUCHERED 是"已有凭证"状态，不代表已审核。凭证审核在 VoucherEntity 级别进行。BusinessDoc 的 VOUCHERED 仅表示该单据的会计凭证已生成。

---

## 5. 测试计划

### 5.1 新增加测试用例

| 测试方法 | 关联修复 | 断言 |
|---------|---------|------|
| `testBusinessDocStatusVoucheredAfterConfirm` | F1 | confirm → BusinessDoc.status==VOUCHERED |
| `testAuditedByAuditedAtPersisted` | F2 | confirm → DB audited_by/audited_at 非空 |
| `testBankTxnVoucherHasSourceDoc` | F3 | 银行流制证 → Voucher.sourceDocType==BANK_STMT |
| `testReconciliationSyncsInvoiceStatus` | F4 | 核销后 → Invoice.status==FULLY_RECONCILED/PARTIALLY_RECONCILED |
| `testSettlementOldPathThrows` | F5 | confirm(receivableId) → BusinessException |
| `testBusinessDocHasBankStatementId` | F6 | B类制证 → BusinessDoc.bankStatementId 非空 |
| `testReconciliationVoucherHasSourceDoc` | F7 | 核销制证 → Voucher.sourceDocType==RECONCILIATION |
| `testSettlementVoucherUpdatesDocStatus` | F8 | generateVoucher → BusinessDoc.status==VOUCHERED |

### 5.2 需修改的测试

| 测试文件 | 变更内容 |
|---------|---------|
| `OutputInvoiceStateMachineServiceImplTest` | 确认后检查 doc.status==VOUCHERED；确认后检查 auditedBy/auditedAt 非空 |
| `ReconciliationServiceTest` | 核销后检查发票状态同步；核销制证检查 Voucher.sourceDocType |
| `ArapSettlementServiceTest` | confirm(old path) 应抛异常；generateVoucher 后检查 BusinessDoc.status==VOUCHERED |
| `AutoGenerationServiceTest` | 银行流制证后检查 Voucher.sourceDoc/BusinessDoc.bankStatementId |

### 5.3 负向断言

| 测试方法 | 级别 | 断言 |
|---------|------|------|
| `testConfirmShouldNotSetDocStatusDraft` | F1 | confirm 后 doc.status !== DRAFT |
| `testConfirmShouldPersistAuditFieldsToDb` | F2 | 直接查 DB 确认 audited_by/audited_at 有值 |

---

## 6. Flyway 迁移

### V78__add_bank_stmt_id_to_business_doc.sql

```sql
-- V78: 为 BusinessDoc 添加银行流水反向追溯字段
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS bank_stmt_id BIGINT REFERENCES t_bank_statement(id);
COMMENT ON COLUMN t_business_doc.bank_stmt_id IS '来源银行流水ID（反向追溯）';
CREATE INDEX IF NOT EXISTS idx_t_business_doc_bank_stmt_id ON t_business_doc(bank_stmt_id);

-- 补全历史数据：从 AutoGenerationService 生成的 BusinessDoc 追溯 bank_stmt_id
UPDATE t_business_doc d
SET bank_stmt_id = s.id
FROM t_bank_statement s
WHERE s.generated_doc_id = d.id
  AND d.bank_stmt_id IS NULL;
```

---

## 7. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| F1 修改后 BusinessDoc 从 DRAFT 直接跳 VOUCHERED，前端未适配 | 低 | 前端已展示 VOUCHERED（invoice 同状态，未单独过滤 DRAFT） |
| F5 抛出异常可能导致已有 settlement 旧数据失败 | 🟡 中 | 确认为 P34 过渡期遗留数据，V73 后应已清理。发布前需确认 DB 无 receivableId/payableId 引用 |
| F4 同步发票状态在核销事务内可能回滚 | 低 | catch 异常只打 log 不抛，不影响主核销 |
| F8 将 PARTIALLY_RECONCILED→VOUCHERED 后核销状态丢失 | 低 | settledAmount/unsettledAmount 仍保留，可用于统计；状态语义从"核销进展"变为"凭证状态" |

---

## 8. 验收标准

- [ ] F1: 发票 confirm 后 BusinessDoc.status==VOUCHERED ✅ 测试验证
- [ ] F2: `auditedBy`/`auditedAt` 正确写入 `t_output_invoice` ✅ 测试验证
- [ ] F3: 银行流水制证后 Voucher.sourceDocType==BANK_STMT ✅ 测试验证
- [ ] F4: 核销后发票状态同步为 FULLY_RECONCILED/PARTIALLY_RECONCILED ✅ 测试验证
- [ ] F5: Settlement.confirm() 旧路径抛 BusinessException ✅ 测试验证
- [ ] F6: BusinessDoc.bankStatementId 正确回写 ✅ 测试验证
- [ ] F7: 核销制证 Voucher.sourceDocType==RECONCILIATION ✅ 测试验证
- [ ] F8: Settlement.generateVoucher() 后 BusinessDoc.status==VOUCHERED ✅ 测试验证
- [ ] F9: audit()/generateVoucher() 去重无回归 ✅ mvn test 0 fail
- [ ] F10: fillOutputInvoiceDetails 降级路径正确 ✅ 测试验证
- [ ] 全部 10 项修改通过 mvn test（容忍 16 个 H2 历史 errors）

---

## 9. 依赖关系

| 依赖 | 关系 |
|------|------|
| P34 (应收/应付→BusinessDoc 迁移) | F5 依赖 P34 V73 数据迁移完成 |
| V78 migration | F6 需要在 DB 层面添加 bank_stmt_id 列 |

---

## 10. 回滚方案

本次修改不涉及 schema 破坏性变更（F6 除外），可逐项回滚：

1. **F1**: 改回 `doc.setStatus("DRAFT")`
2. **F2**: 加回 `@TableField(exist = false)`
3. **F3/F4/F7/F8**: 注释新增代码行
4. **F5**: 改回 log.debug
5. **F6**: `DROP COLUMN bank_stmt_id`（V78 回滚）

---

```
# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: OutputInvoiceEntity
module: tax
table: t_output_invoice

states:
  PENDING_CONFIRM:
    description: "导入后默认状态，待确认"
    initial: true
    terminal: false
  PENDING_REVIEW:
    description: "待审核"
    initial: false
    terminal: false
  CONFIRMED:
    description: "已确认（已开票）"
    initial: false
    terminal: false
  VOUCHERED:
    description: "已生成凭证"
    initial: false
    terminal: false
  FULLY_RECONCILED:
    description: "全额核销"
    initial: false
    terminal: true
  PARTIALLY_RECONCILED:
    description: "部分核销"
    initial: false
    terminal: false
  VOIDED:
    description: "已作废"
    initial: false
    terminal: true
  REVERSED:
    description: "已红冲"
    initial: false
    terminal: true

transitions:
  - id: T-06
    from: VOUCHERED
    to: FULLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "status == VOUCHERED && unsettledAmount == 0"
    postcondition: "status == FULLY_RECONCILED"
    side_effects: []
    test_ref: test_fully_reconciled_via_reconciliation
  - id: T-07
    from: VOUCHERED
    to: PARTIALLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "status == VOUCHERED && unsettledAmount > 0"
    postcondition: "status == PARTIALLY_RECONCILED"
    side_effects: []
    test_ref: test_partially_reconciled_via_reconciliation

constraints:
  - id: C-01
    type: audit
    rule: "confirm() must persist auditedBy + auditedAt to DB"
    enforcement: "OutputInvoiceEntity.auditedBy/auditedAt must NOT have exist=false"
    test_ref: test_audited_by_audited_at_persisted
  - id: C-02
    type: traceability
    rule: "All Voucher records must have sourceDocType/sourceDocId set"
    enforcement: "createVoucher() in AutoGenerationService sets BANK_STMT; createReconciliationVoucher() sets RECONCILIATION"
    test_ref: test_voucher_source_doc_set

acceptance_tests:
  - id: AT-F1
    description: "发票confirm后BusinessDoc状态为VOUCHERED（非DRAFT）"
    method: test_business_doc_status_vouchered_after_confirm
    assertion: "doc.status == VOUCHERED"
    status: missing
  - id: AT-F2
    description: "审核信息持久化到DB"
    method: test_audited_by_audited_at_persisted
    assertion: "confirm后 DB 查询 audited_by/audited_at 非空"
    status: missing
  - id: AT-F3
    description: "银行流水制证后Voucher可追溯到源头"
    method: test_bank_txn_voucher_has_source_doc
    assertion: "Voucher.sourceDocType == BANK_STMT && sourceDocId == stmt.id"
    status: missing
  - id: AT-F4
    description: "核销后发票状态同步"
    method: test_reconciliation_syncs_invoice_status
    assertion: "核销全额后 invoice.status == FULLY_RECONCILED"
    status: missing
  - id: AT-F5
    description: "Settlement旧路径抛BusinessException"
    method: test_settlement_old_path_throws
    assertion: "confirm(receivableId) throws BusinessException"
    status: missing
  - id: AT-F6
    description: "BusinessDoc可反向追溯银行流水"
    method: test_business_doc_has_bank_statement_id
    assertion: "B类制证后 BusinessDoc.bankStatementId == stmt.id"
    status: missing
  - id: AT-F7
    description: "核销凭证可溯源"
    method: test_reconciliation_voucher_has_source_doc
    assertion: "Voucher.sourceDocType == RECONCILIATION"
    status: missing
  - id: AT-F8
    description: "核销单生成凭证后BusinessDoc状态为VOUCHERED"
    method: test_settlement_voucher_updates_doc_status
    assertion: "generateVoucher后 doc.status == VOUCHERED"
    status: missing

out_of_scope:
  - "前端适配（如有）— 纯后端修复"
  - "V78 migration 历史数据补全不影响现有数据"
  - "采购发票(InputInvoice)状态机 — 类似问题独立处理"

dependencies:
  - spec: P34
    relation: "F5 依赖 P34 V73 数据迁移完成，旧格式数据已清理"
```
