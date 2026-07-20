# P33 SPEC — 销售发票流程简化（已归档）

> **编号**：HUICAI-SPC-033B | 优先级: 已废弃
> **归档日期**: 2026-07-01
> **取代者**: `docs/specs/P34-receivable-payable-to-businessdoc.md`
> **原因**: 老丁决策恢复业务单据体系，P33 的"去除业务单中间环节"方向已被反转。
> 实际代码已按 P34 实现：发票审核后创建 INVOICE_OUT 业务单据 + 凭证，不再创建独立应收单。
>
> **历史摘要**: P33 曾试图简化销售发票→应收单流程，去除 BusinessDoc 中间环节。但 P34 决定应收/应付走回业务单据体系，P33 方案被撤销。
>
> **以下为原始内容，仅供参考，不再维护。**

---

> **关联需求**: REQ-2026-029
> **说明**：本 SPEC 已被 P34 取代，归档仅作参考

## 1. 输入契约
→ 见本文 [## 2. 详细设计 — 数据模型与 API 变更](#2-详细设计)

## 2. 输出契约
→ 见本文 [## 6. 验收标准 — 验收检查清单](#6-验收标准)

## 3. 状态流转
→ 见本文 [## 2.1 状态机变更 — OutputInvoice 状态转换](#21-状态机变更)

## 4. 异常处理
→ 见本文各 BusinessException 抛出点 / ## 4. 风险评估

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 | 状态 |
|---|------|------|------|------|
| 1 | 修改 `OutputInvoiceStateMachineServiceImpl.confirm()` — 审核后直接创建应收单 + 凭证 | `OutputInvoiceStateMachineServiceImpl.java` | 🟡 中 | ✅ 已实现（凭证生成待补充） |
| 2 | 移除 `createBusinessDocAndReceivableAfterConfirm()` 及 `createBusinessDocFromInvoice()` | `OutputInvoiceStateMachineServiceImpl.java` | ✅ 低 | ✅ 已移除 |
| 3 | 新增 `createReceivableFromInvoiceDirect()` — 直接从发票创建应收单 | `OutputInvoiceStateMachineServiceImpl.java` | ✅ 低 | ✅ 已实现 |
| 4 | 新增 `generateVoucherFromInvoiceDirect()` — 调用 `taxService` 生成凭证 | `OutputInvoiceStateMachineServiceImpl.java` | 🟡 中 | ⚠️ **待实现** |
| 5 | 更新 `TaxServiceImpl.generateVoucherFromInvoice()` — 确认 `sourceDocId = invoice.getId()` | `TaxServiceImpl.java` | ✅ 低 | ✅ 已实现 |
| 6 | 更新编号关联体系 — 发票→应收→凭证链路不再经过业务单 | `NumberingTraceServiceImpl.java` | ✅ 低 | ✅ 已实现 |
| 7 | 更新测试 — 移除业务单相关的断言，新增发票→应收→凭证直连测试 | 多个测试文件 | 🟡 中 | ⚠️ **待更新** |
| 8 | 更新文档 | 文档文件 | ✅ 低 | ✅ 已更新 |

---

## 1. 背景与问题

### 1.1 当前流程（过度复杂）

```
销售发票导入 → PENDING_CONFIRM
  → 提交审核 → PENDING_REVIEW
  → 人工审核(confirm) → CONFIRMED
    → 自动生成：
       1. BusinessDoc (INVOICE_OUT, DRAFT) + BusinessDocEntry (DRAFT)
       2. Receivable (DRAFT)
    → 用户手动调用 TaxService.generateVoucherFromInvoice()
       → Voucher (DRAFT)
    → 用户手动调用 markVouchered()
       → Invoice VOUCHERED
```

**问题**:
1. 业务单（`BusinessDoc`）在这个链路中是多余的中间环节，没有实际业务价值
2. 凭证生成需要用户手动调用，不符合 P31 任务书中"人工仅需最后一步凭证审核"的目标

### 1.2 简化后流程（目标）

```
销售发票导入 → PENDING_CONFIRM
  → 提交审核 → PENDING_REVIEW
  → 人工审核(confirm) → CONFIRMED
    → 自动生成：
       1. Receivable (DRAFT) — 应收单
       2. Voucher (DRAFT) — 凭证（待人工审核）
    → 用户手动调用 markVouchered()（或自动调用）
       → Invoice VOUCHERED
```

**简化点**:
- 移除 `BusinessDoc` + `BusinessDocEntry` 的创建
- 发票审核通过后，直接生成应收单 + 凭证
- 凭证状态为 `DRAFT`，等待人工审核（符合"人是唯一审核主体"铁律）

---

## 2. 详细设计

### 2.1 状态机变更

**`OutputInvoiceStateMachineServiceImpl.confirm()` 修改**:

```java
@Override
@Transactional
public void confirm(Long invoiceId, Long userId) {
    OutputInvoiceEntity entity = getEntity(invoiceId);
    if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
        throw BusinessException.badRequest("仅待审核状态可确认，当前: " + entity.getStatus());
    }
    
    entity.setStatus(InvoiceStatus.CONFIRMED);
    entity.setAuditedBy(userId);
    entity.setAuditedAt(LocalDateTime.now());
    entity.setUpdatedBy(userId);
    invoiceMapper.updateById(entity);
    log.info("销售发票审核通过: id={}, userId={}", invoiceId, userId);

    // P33 简化：审核后直接生成应收单 + 凭证（不再经过业务单）
    createReceivableFromInvoiceDirect(invoiceId, userId);
    generateVoucherFromInvoiceDirect(invoiceId, userId);  // ← 新增
}
```

**⚠️ 当前差距**: `confirm()` 中缺少 `generateVoucherFromInvoiceDirect()` 调用。

**移除的方法**:
- `createBusinessDocAndReceivableAfterConfirm()` — ✅ 已删除
- `createBusinessDocFromInvoice()` — ✅ 已删除
- `generateDocNo()` — ✅ 已删除

### 2.2 应收单直连生成（✅ 已实现）

```java
/**
 * P33 简化：发票审核后直接创建应收单，不经过业务单。
 * 应收单的 summary 直接使用发票的客户名称和发票号。
 */
private void createReceivableFromInvoiceDirect(Long invoiceId, Long userId) {
    OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
    if (invoice == null) {
        log.warn("发票不存在: invoiceId={}", invoiceId);
        return;
    }

    // 防重复创建：查 invoice_id 是否有应收单
    long existingCount = receivableMapper.selectCount(
            new LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getInvoiceId, invoiceId)
                    .eq(ReceivableEntity::getCustomerId, invoice.getCustomerId()));
    if (existingCount > 0) {
        log.info("发票已有应收单: invoiceId={}, count={}, skip", invoiceId, existingCount);
        return;
    }

    ReceivableEntity recv = new ReceivableEntity();
    recv.setCustomerId(invoice.getCustomerId());
    recv.setInvoiceId(invoice.getId());       // P33: 直接关联发票ID
    recv.setInvoiceNo(invoice.getInvoiceNo()); // 发票编号冗余
    recv.setVoucherId(null);                   // 凭证尚未生成
    recv.setPeriod(invoice.getPeriod());
    recv.setTxDate(invoice.getInvoiceDate());
    recv.setAmount(invoice.getTotalAmount());
    recv.setSettledAmount(BigDecimal.ZERO);
    recv.setUnsettledAmount(invoice.getTotalAmount());
    recv.setSummary(invoice.getCustomerName());
    recv.setStatus(ArapStatus.DRAFT);

    String receivableNo = generateReceivableNo(invoice.getPeriod());
    recv.setReceivableNo(receivableNo);

    receivableMapper.insert(recv);

    // 回写发票：应收单 ID 和编号（双向追溯）
    invoice.setReceivableId(recv.getId());
    invoice.setReceivableNo(receivableNo);
    invoiceMapper.updateById(invoice);

    log.info("P33 销售发票应收单直连生成: invoiceId={}, receivableNo={}, amount={}",
            invoice.getId(), receivableNo, invoice.getTotalAmount());
}
```

### 2.3 凭证直连生成（⚠️ 待实现）

```java
/**
 * P33 简化：发票审核后直接创建凭证（DRAFT 状态，等待人工审核）。
 * 凭证生成逻辑复用 TaxService 的模板匹配 + 硬编码降级。
 */
private void generateVoucherFromInvoiceDirect(Long invoiceId, Long userId) {
    try {
        taxService.generateVoucherFromInvoice(invoiceId, userId);
        log.info("P33 销售发票凭证直连生成: invoiceId={}", invoiceId);
    } catch (Exception e) {
        log.error("P33 销售发票凭证生成失败: invoiceId={}, error={}", invoiceId, e.getMessage());
        // 凭证生成失败不影响发票审核通过，记录日志供人工处理
    }
}
```

### 2.4 编号关联体系变更（**必须保持发票↔应收单↔凭证双向追溯**）

**简化后的链路**:
```
t_output_invoice ──invoice_id──→ t_receivable ──voucher_id──→ t_voucher
```

**三张单据编号关联矩阵**:

| 源表 → 目标表 | 发票 (t_output_invoice) | 应收单 (t_receivable) | 凭证 (t_voucher) |
|--------------|------------------------|----------------------|------------------|
| 发票 | - | `invoice_id` + `invoice_no` | `source_doc_id` + `source_doc_no` + `source_doc_type='OUTPUT_INVOICE'` |
| 应收单 | `receivable_id` + `receivable_no` | - | `voucher_id` + `voucher_no` |
| 凭证 | `voucher_id` + `voucher_no` | `voucher_id` + `voucher_no` | - |

**Entity 字段变更**:

1. `ReceivableEntity`：
   - ✅ **新增** `invoiceId` 字段（直接关联发票 ID，替代原来通过 `docId` 间接关联）
   - ✅ **保留** `invoiceNo`（发票编号冗余，已有）
   - ✅ **保留** `receivableNo`（应收单编号，已有）
   - ✅ **保留** `voucherId`/`voucherNo`（凭证编号冗余，已有）
   - ⚠️ **保留** `docId`/`docNo` 用于历史数据兼容（销售发票链路不再写入）

2. `OutputInvoiceEntity`：
   - ✅ **保留** `invoiceNo`（发票编号，已有）
   - ✅ **保留** `receivableId`/`receivableNo`（应收单关联，已有）
   - ✅ **保留** `voucherId`/`voucherNo`（凭证关联，已有）
   - ⚠️ **保留** `docId`/`docNo` 用于历史数据兼容（销售发票链路不再写入）

3. `VoucherEntity`：
   - ✅ **确认** `sourceDocType='OUTPUT_INVOICE'`（当前已是硬编码）
   - ✅ **确认** `sourceDocNo=发票号`（当前已是硬编码）
   - ✅ **确认** `sourceDocId=发票ID`（当前已是硬编码，L390, L452）

**V65 Migration**（应收单编号关联）:
```sql
-- 1. 给 t_receivable 增加 invoice_id 列（直接关联发票）
ALTER TABLE t_receivable ADD COLUMN invoice_id BIGINT REFERENCES t_output_invoice(id);
COMMENT ON COLUMN t_receivable.invoice_id IS '关联销售发票ID（P33 简化：直接关联，不经过业务单）';
CREATE INDEX idx_t_receivable_invoice_id ON t_receivable(invoice_id);

-- 2. 给 t_receivable 增加 receivable_no 列（应收单编号）
ALTER TABLE t_receivable ADD COLUMN receivable_no VARCHAR(64);
COMMENT ON COLUMN t_receivable.receivable_no IS '应收单编号';
CREATE INDEX idx_t_receivable_no ON t_receivable(receivable_no);

-- 3. 历史数据补全
UPDATE t_receivable r
SET invoice_id = (SELECT id FROM t_output_invoice i WHERE i.invoice_no = r.invoice_no)
WHERE r.invoice_id IS NULL AND r.invoice_no IS NOT NULL;
```

### 2.5 TaxServiceImpl.generateVoucherFromInvoice() 适配

当前 `generateVoucherFromInvoice()` 已经有完整的模板匹配 + 硬编码降级逻辑，**无需大幅改动**。

需要确认：
1. ✅ 生成的凭证 `sourceDocType` 设为 `OUTPUT_INVOICE`（当前已是硬编码）
2. ✅ 生成的凭证 `sourceDocNo` 设为发票号（当前已是硬编码）
3. ✅ 生成的凭证 `sourceDocId` 设为发票ID（当前已是硬编码，L390, L452）
4. ✅ 凭证状态为 `DRAFT`，等待人工审核（当前已是如此）

### 2.6 对业务单模块的影响

**不需要删除业务单模块**，只需要：
1. 销售发票链路不再创建业务单
2. 采购发票链路（如有）保留业务单
3. 银行流水 B 类确认生成的业务单（RECEIPT/PAYMENT）保留
4. `BusinessDocEntity` 和 `BusinessDocEntryEntity` 保留，但销售发票场景不再使用

**影响范围**:
- `BusinessDocServiceImpl` — 不受影响（其他场景仍使用）
- `BusinessDocController` — 不受影响
- 编号关联查询接口 — 已更新，销售发票链路不再经过 `BUSINESS_DOC`

---

## 3. 测试变更

### 3.1 需要修改的测试

| 测试文件 | 变更内容 | 状态 |
|---------|---------|------|
| `OutputInvoiceStateMachineServiceImplTest.java` | 移除 `confirm` 后检查业务单的断言；新增应收单+凭证直连断言 | ⚠️ **待更新** |
| `SalesFlowE2ETest.java` | 移除业务单相关步骤；更新验证链路 | ⚠️ **待更新** |
| `NumberingFrontendApiTest.java` | 更新编号关联链路（发票→应收→凭证） | ⚠️ **待更新** |
| `linkage-map.md` 验证脚本 | 移除业务单验证步骤 | ✅ **已更新** |

### 3.2 新增测试

```java
@Test
void confirm_shouldCreateReceivableAndVoucherDirectly() {
    // 1. 创建并审核发票
    OutputInvoiceEntity invoice = createTestInvoice();
    stateMachine.submitForReview(invoice.getId(), 1L);
    stateMachine.confirm(invoice.getId(), 1L);
    
    // 2. 验证发票状态 = CONFIRMED
    OutputInvoiceEntity confirmed = invoiceMapper.selectById(invoice.getId());
    assertEquals(InvoiceStatus.CONFIRMED, confirmed.getStatus());
    
    // 3. 验证应收单已创建（直接关联发票）
    ReceivableEntity recv = receivableMapper.selectOne(
        new LambdaQueryWrapper<ReceivableEntity>()
            .eq(ReceivableEntity::getInvoiceId, invoice.getId()));
    assertNotNull(recv);
    assertEquals(invoice.getTotalAmount(), recv.getAmount());
    
    // 4. 验证凭证已创建（DRAFT 状态）
    VoucherEntity voucher = voucherMapper.selectOne(
        new LambdaQueryWrapper<VoucherEntity>()
            .eq(VoucherEntity::getSourceDocType, "OUTPUT_INVOICE")
            .eq(VoucherEntity::getSourceDocNo, invoice.getInvoiceNo()));
    assertNotNull(voucher);
    assertEquals("DRAFT", voucher.getStatus());
    
    // 5. 验证没有创建业务单
    long docCount = docMapper.selectCount(
        new LambdaQueryWrapper<BusinessDocEntity>()
            .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo()));
    assertEquals(0, docCount);
}
```

---

## 4. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 编号关联体系断裂 | 中 | V65/V66 migration 增加 `invoice_id` 字段，保持双向追溯 |
| 采购发票链路受影响 | 低 | 采购发票不使用 `OutputInvoiceStateMachineService`，仅影响销售发票 |
| 业务单模块废弃部分代码 | 低 | 保留 `BusinessDoc` 模块，仅销售发票链路不再创建 |
| 测试大规模变更 | 中 | 分阶段修改测试，先改状态机测试，再改 E2E 测试 |
| 前端接口不兼容 | 低 | 前端不直接操作业务单（INVOICE_OUT 类型），主要影响后端 API |

---

## 5. 回滚方案

如果简化后发现问题，可以一键回滚：
1. 恢复 `confirm()` 中对 `createBusinessDocAndReceivableAfterConfirm()` 的调用
2. 回滚 V65/V66 migration（删除 `invoice_id` 列）
3. 恢复测试代码

---

## 6. 验收标准

- [ ] 销售发票审核后，应收单自动创建（DRAFT 状态） ✅
- [ ] 销售发票审核后，凭证自动创建（DRAFT 状态，等待人工审核） ⚠️ **待实现**
- [ ] 不再创建 `BusinessDoc`（INVOICE_OUT 类型） ✅
- [ ] 编号关联链路完整：发票 → 应收单 → 凭证（双向追溯正常） ✅
- [ ] 所有测试通过（包括 E2E 全流程测试） ⚠️ **待更新**
- [ ] `GET /api/v1/vouchers/trace?no={发票号}` 能正确追溯到应收单 ✅
- [ ] 人工审核凭证流程不变（凭证审核仍由人完成） ✅

---

## 7. 与 P31 的关系

**P31 任务书**（`docs/tasks/P31-auto-flow-after-import_任务书.md`）定义的目标是：
> 销售发票批量导入后，系统自动完成：发票审核 → 生成应收/应付单 → 审核业务单 → 生成凭证

**P33 修正**：移除"审核业务单"环节，改为：
> 销售发票批量导入后，系统自动完成：发票审核 → 生成应收单 + 凭证

**人工仍需操作的唯一环节**：凭证审核（P31 的硬约束保持不变）。

---

## 8. 相关文件

| 文件 | 变更 |
|------|------|
| `OutputInvoiceStateMachineServiceImpl.java` | 核心改动：移除业务单创建，直连应收+凭证 |
| `TaxServiceImpl.java` | 微调：确保 sourceDocType=OUTPUT_INVOICE |
| `ReceivableEntity.java` | 新增 `invoiceId` 字段 |
| `VoucherEntity.java` | 确认 `sourceDocType` 枚举值 |
| `NumberingTraceServiceImpl.java` | 更新链路图 |
| `docs/linkage-map.md` | 更新 L1 链路验证脚本 |
| `docs/技术方案.md` | 更新架构流程图 |
| `docs/tasks/P31-auto-flow-after-import_任务书.md` | 更新为简化流程 |

---

## 7. BDD 验收标准

### 场景 1：销售发票审核后自动生成应收单和凭证
**Given** 一张销售发票处于 PENDING_REVIEW 状态，尚未关联任何业务单、应收单或凭证
**When** 用户调用 confirm(invoiceId, userId)
**Then** 发票状态变为 CONFIRMED，应收单（Receivable, DRAFT）和凭证（Voucher, DRAFT）自动创建，且应收单的 invoiceId 直接指向该发票

### 场景 2：应收单防重复创建
**Given** 一张销售发票已有对应的应收单记录（invoiceId 已存在）
**When** 用户再次调用 confirm(invoiceId, userId)
**Then** 系统跳过应收单创建，日志记录"发票已有应收单: invoiceId=...，skip"，凭证正常生成

### 场景 3：编号关联链路完整追溯
**Given** 一张销售发票已完成审核，生成了应收单和凭证
**When** 通过 trace API 查询发票编号
**Then** 返回的链路包含发票→应收单→凭证（不经过 BusinessDoc），且双向追溯字段（invoiceId、voucherId、sourceDocId）非空
