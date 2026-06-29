# P34 SPEC — 应收/应付单据恢复为业务单据类型

> **状态**: 待审核 | **优先级**: 高（P34）
> **依据**: 老丁决策 — "恢复成业务单据的应收单据，而取消现有的应收单据。应收单据、应付单据就是业务单据中的一种，不需要再单独生成一个"
> **目标**: 撤销 P33 简化方案，让应收/应付走回业务单据体系；将 `t_receivable`/`t_payable` 数据合并到 `t_business_doc`，删除独立应收应付表
> **核心原则**: 业务单据是应收/应付的唯一载体，核销金额直接记录在业务单据上，不再有独立的应收/应付实体

---

## 0. 改动清单总览

| # | 改动 | 影响文件 | 风险 | 状态 |
|---|------|---------|------|------|
| 1 | DB: t_business_doc 增加结算字段 | V68 migration | 🟡 中 | ⏳ 待实现 |
| 2 | DB: 数据迁移 t_receivable → t_business_doc | V69 migration | 🟡 中 | ⏳ 待实现 |
| 3 | DB: 删除 t_receivable/t_payable 表 | V70 migration | 🔴 高 | ⏳ 待实现 |
| 4 | 修改 OutputInvoiceStateMachineServiceImpl — 改回创建 BusinessDocEntity | 1 Java 文件 | 🟡 中 | ⏳ 待实现 |
| 5 | 修改 BusinessDocServiceImpl — 放开 INVOICE_OUT 限制 | 1 Java 文件 | ✅ 低 | ⏳ 待实现 |
| 6 | 修改 ArapSettlementServiceImpl — 改为更新 BusinessDocEntity | 1 Java 文件 | 🟡 中 | ⏳ 待实现 |
| 7 | 修改 ReconciliationServiceImpl — 改为操作 BusinessDocEntity | 1 Java 文件 | 🟡 中 | ⏳ 待实现 |
| 8 | 修改 ArapSettlementEntryEntity — receivableId/payableId → businessDocId | 1 Java 文件 + DB | 🟡 中 | ⏳ 待实现 |
| 9 | 修改 ReconciliationLogEntity — targetDocId 指向业务单据 | 1 Java 文件 | ✅ 低 | ⏳ 待实现 |
| 10 | 修改 ReceivableServiceImpl/PayableServiceImpl — 迁移能力到 BusinessDocService | 2 Java 文件 | 🟡 中 | ⏳ 待实现 |
| 11 | 修改 InputInvoiceImportService — 停止创建 PayableEntity | 1 Java 文件 | ✅ 低 | ⏳ 待实现 |
| 12 | 修改 BusinessDocList.vue — 展示 INVOICE_OUT 标签 | 1 Vue 文件 | ✅ 低 | ⏳ 待实现 |
| 13 | 修改 BusinessDocDetail.vue — 放开 INVOICE_OUT 凭证生成 | 1 Vue 文件 | ✅ 低 | ⏳ 待实现 |
| 14 | 修改 ReceivableList.vue — 改为业务单据列表视图 | 1 Vue 文件 | ✅ 低 | ⏳ 待实现 |
| 15 | 清理: 删除 10+ 个引用 ReceivableMapper/PayableMapper 的文件中的相关代码 | 多个 Java 文件 | 🟡 中 | ⏳ 待实现 |
| 16 | 更新测试 | 多个测试文件 | 🟡 中 | ⏳ 待实现 |

---

## 1. 背景与问题

### 1.1 P33 简化后的流程（当前现状）

```
销售发票审核(confirm)
  → OutputInvoiceStateMachineServiceImpl.confirm()
    → createReceivableFromInvoiceDirect()  // 直接创建 ReceivableEntity (t_receivable)
    → generateVoucherFromInvoiceDirect()   // 直接生成凭证
  → BusinessDocEntity (INVOICE_OUT) 完全被跳过
```

**P33 同时产生了两个问题：**

1. **数据割裂**：应收数据在 `t_receivable` 表，业务单据在 `t_business_doc` 表，核销结算跨表操作
2. **流程不统一**：其他所有单据（收款/付款/费用报销/采购应付）都走业务单据体系，唯独销售应收走独立表

### 1.2 采购发票流程（类似问题）

```
进项发票导入(confirm)
  → InputInvoiceImportService
    → 创建 BusinessDocEntity (INVOICE_IN, DRAFT)  ← 走业务单据
    → 创建 PayableEntity (t_payable, DRAFT)        ← 又额外创建独立应付
  → 同一个业务同时写了两个表
```

### 1.3 目标流程

```
销售发票审核(confirm)
  → OutputInvoiceStateMachineServiceImpl.confirm()
    → 创建 BusinessDocEntity (INVOICE_OUT, APPROVED)
    → 生成凭证（DRAFT，待人工审核）
  → 核销时直接更新 BusinessDocEntity.settledAmount/unsettledAmount
```

```
进项发票导入(confirm)
  → InputInvoiceImportService
    → 创建 BusinessDocEntity (INVOICE_IN, DRAFT)  ← 仅业务单据，不再创建 PayableEntity
```

### 1.4 简化对比

| 维度 | P33（当前） | P34（目标） |
|------|------------|------------|
| 销售应收载体 | `t_receivable` 独立表 | `t_business_doc` docType=INVOICE_OUT |
| 采购应付载体 | `t_business_doc` + `t_payable` 双写 | `t_business_doc` docType=INVOICE_IN |
| 结算金额存储 | `ReceivableEntity.settledAmount` | `BusinessDocEntity.settledAmount` |
| 前端应收列表 | 独立 `ReceivableList.vue` | 合并到 `BusinessDocList.vue` |
| 表数量 | 2 个独立表 + 1 个业务单据表 | 仅 1 个业务单据表 |

---

## 2. 详细设计

### 2.1 DB 迁移（V68 ~ V70）

#### V68：t_business_doc 增加结算字段

```sql
-- V68__add_settlement_columns_to_business_doc.sql

-- 1. 增加已核销金额（默认 0）
ALTER TABLE t_business_doc
ADD COLUMN settled_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
COMMENT ON COLUMN t_business_doc.settled_amount IS '已核销金额';

-- 2. 增加未核销金额（默认 = amount）
ALTER TABLE t_business_doc
ADD COLUMN unsettled_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
COMMENT ON COLUMN t_business_doc.unsettled_amount IS '未核销金额';

-- 3. 增加到期日
ALTER TABLE t_business_doc
ADD COLUMN due_date DATE;
COMMENT ON COLUMN t_business_doc.due_date IS '到期日';
```

**BusinessDocEntity.java 新增字段：**

```java
/** 已核销金额 */
private BigDecimal settledAmount;

/** 未核销金额 */
private BigDecimal unsettledAmount;

/** 到期日 */
private LocalDate dueDate;
```

**BusinessDocVO.fromEntity() 同步读取新字段：**

```java
vo.setSettledAmount(e.getSettledAmount());
vo.setUnsettledAmount(e.getUnsettledAmount());
```

#### V69：数据迁移 t_receivable → t_business_doc

```sql
-- V69__migrate_receivable_to_business_doc.sql
-- 说明：将现有 t_receivable 数据迁移到 t_business_doc（INVOICE_OUT 类型）
-- 迁移前请确认已执行 V68

-- 1. 迁移应收数据到业务单据
INSERT INTO t_business_doc (
    doc_no, doc_type, doc_date, period, amount, status,
    customer_id, summary, invoice_no, source,
    voucher_id, voucher_no,
    settled_amount, unsettled_amount, due_date,
    created_by, created_at, updated_at, version
)
SELECT
    COALESCE(r.receivable_no, 'YS' || r.period || LPAD(CAST(r.id AS TEXT), 4, '0')),
    'INVOICE_OUT',
    r.tx_date,
    r.period,
    r.amount,
    CASE
        WHEN r.status = 'REVERSED' THEN 'REVERSED'
        WHEN r.settled_amount >= r.amount AND r.amount > 0 THEN 'FULLY_RECONCILED'
        WHEN r.settled_amount > 0 THEN 'PARTIALLY_RECONCILED'
        WHEN r.voucher_id IS NOT NULL THEN 'VOUCHERED'
        WHEN r.status = 'CONFIRMED' THEN 'APPROVED'
        ELSE 'DRAFT'
    END,
    r.customer_id,
    r.summary,
    r.invoice_no,
    'IMPORTED',
    r.voucher_id,
    r.voucher_no,
    COALESCE(r.settled_amount, 0),
    COALESCE(r.unsettled_amount, r.amount),
    r.due_date,
    1, r.created_at, r.updated_at, 1
FROM t_receivable r
WHERE NOT EXISTS (
    SELECT 1 FROM t_business_doc d
    WHERE d.doc_type = 'INVOICE_OUT' AND d.invoice_no = r.invoice_no
);

-- 2. 迁移应付数据到业务单据
INSERT INTO t_business_doc (
    doc_no, doc_type, doc_date, period, amount, status,
    supplier_id, summary, invoice_no, source,
    voucher_id, voucher_no,
    settled_amount, unsettled_amount, due_date,
    created_by, created_at, updated_at, version
)
SELECT
    COALESCE(p.payable_no, 'YF' || p.period || LPAD(CAST(p.id AS TEXT), 4, '0')),
    'INVOICE_IN',
    p.tx_date,
    p.period,
    p.amount,
    CASE
        WHEN p.status = 'REVERSED' THEN 'REVERSED'
        WHEN p.settled_amount >= p.amount AND p.amount > 0 THEN 'FULLY_RECONCILED'
        WHEN p.settled_amount > 0 THEN 'PARTIALLY_RECONCILED'
        WHEN p.voucher_id IS NOT NULL THEN 'VOUCHERED'
        WHEN p.status = 'CONFIRMED' THEN 'APPROVED'
        ELSE 'DRAFT'
    END,
    p.vendor_id,
    p.summary,
    p.invoice_no,
    'IMPORTED',
    p.voucher_id,
    p.voucher_no,
    COALESCE(p.settled_amount, 0),
    COALESCE(p.unsettled_amount, p.amount),
    p.due_date,
    1, p.created_at, p.updated_at, 1
FROM t_payable p
WHERE NOT EXISTS (
    SELECT 1 FROM t_business_doc d
    WHERE d.doc_type = 'INVOICE_IN' AND d.invoice_no = p.invoice_no
);

-- 3. 更新核销关联：ArapSettlementEntry 增加 business_doc_id 列
ALTER TABLE t_arap_settlement_entry
ADD COLUMN business_doc_id BIGINT REFERENCES t_business_doc(id);

-- 回填 business_doc_id（应收侧）
UPDATE t_arap_settlement_entry e
SET business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_receivable r ON d.invoice_no = r.invoice_no
    WHERE e.receivable_id = r.id
    AND d.doc_type = 'INVOICE_OUT'
    LIMIT 1
)
WHERE e.receivable_id IS NOT NULL;

-- 回填 business_doc_id（应付侧）
UPDATE t_arap_settlement_entry e
SET business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_payable p ON d.invoice_no = p.invoice_no
    WHERE e.payable_id = p.id
    AND d.doc_type = 'INVOICE_IN'
    LIMIT 1
)
WHERE e.payable_id IS NOT NULL;

-- 4. 更新 reconciliation_log 的 target_doc_id 为业务单据ID
-- （当前 target_doc_id 指向 receivable/payable ID）
-- reconciliation_log 已有 target_doc_type=INVOICE_OUT/INVOICE_IN
-- 我们需要加一列或重新解释 target_doc_id
-- 由于重建逻辑复杂且 reconciliation_log 少量数据，
-- 新增 target_business_doc_id 列，允许 NULL
ALTER TABLE t_reconciliation_log
ADD COLUMN target_business_doc_id BIGINT REFERENCES t_business_doc(id);

-- 回填 target_business_doc_id（应收侧）
UPDATE t_reconciliation_log l
SET target_business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_receivable r ON d.invoice_no = r.invoice_no
    WHERE l.target_doc_id = r.id
    AND l.target_doc_type = 'INVOICE_OUT'
    LIMIT 1
)
WHERE l.target_doc_type = 'INVOICE_OUT';

-- 回填 target_business_doc_id（应付侧）
UPDATE t_reconciliation_log l
SET target_business_doc_id = (
    SELECT d.id FROM t_business_doc d
    INNER JOIN t_payable p ON d.invoice_no = p.invoice_no
    WHERE l.target_doc_id = p.id
    AND l.target_doc_type = 'INVOICE_IN'
    LIMIT 1
)
WHERE l.target_doc_type = 'INVOICE_IN';
```

#### V70：删除旧表（⚠️ 确认迁移成功后执行）

```sql
-- V70__drop_receivable_payable_tables.sql
-- ⚠️ 在确认 V69 迁移正确且所有业务验证通过后执行

DROP TABLE IF EXISTS t_receivable CASCADE;
DROP TABLE IF EXISTS t_payable CASCADE;
```

### 2.2 业务单据状态扩展

当前业务单据状态（在 BusinessDocServiceImpl 中使用硬编码字符串）：

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED → CLOSED
                    ↘ REJECTED
```

对于 INVOICE_OUT / INVOICE_IN 类型，状态流需要扩展核销阶段：

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED → PARTIALLY_RECONCILED → FULLY_RECONCILED
                    ↘ REJECTED                                   ↘ REVERSED
```

**新增状态常量（建议加到 `BusinessDocStatus` 常量类或复用 `ArapStatus`）：**

```java
// 新增业务单据核销状态（仅 INVOICE_OUT/INVOICE_IN 使用）
public static final String PARTIALLY_RECONCILED = "PARTIALLY_RECONCILED";
public static final String FULLY_RECONCILED = "FULLY_RECONCILED";
```

**前端 DOC_STATUS_LABELS 同步新增：**

```typescript
export const DOC_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已审批',
  VOUCHERED: '已生成凭证',
  PARTIALLY_RECONCILED: '部分核销',
  FULLY_RECONCILED: '已核销',
  REVERSED: '已冲销',
  CLOSED: '已关闭',
  REJECTED: '已驳回',
}
```

### 2.3 销售发票审核流修改（撤销 P33）

**`OutputInvoiceStateMachineServiceImpl.confirm()`** — 改为创建 BusinessDocEntity 而非 ReceivableEntity：

```java
@Override
@Transactional
public void confirm(Long invoiceId, Long userId) {
    // ... 状态校验和更新 (保留) ...

    // P34: 改为创建业务单据（INVOICE_OUT），不再创建独立应收单
    BusinessDocEntity doc = createBusinessDocFromInvoice(invoiceId, userId);
    
    // 生成凭证（DRAFT 状态，等待人工审核）
    generateVoucherFromBusinessDoc(doc, userId);
}

private BusinessDocEntity createBusinessDocFromInvoice(Long invoiceId, Long userId) {
    OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
    
    // 防重复创建
    long existing = docMapper.selectCount(
        new LambdaQueryWrapper<BusinessDocEntity>()
            .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
            .eq(BusinessDocEntity::getDocType, "INVOICE_OUT"));
    if (existing > 0) return docMapper.selectOne(...);
    
    BusinessDocEntity doc = new BusinessDocEntity();
    doc.setDocNo(generateDocNo("INVOICE_OUT", invoice.getPeriod()));
    doc.setDocType("INVOICE_OUT");
    doc.setDocDate(invoice.getInvoiceDate());
    doc.setPeriod(invoice.getPeriod());
    doc.setAmount(invoice.getTotalAmount());
    doc.setCustomerId(invoice.getCustomerId());
    doc.setSummary(invoice.getCustomerName() + " " + invoice.getInvoiceNo());
    doc.setSource("IMPORTED");
    doc.setInvoiceNo(invoice.getInvoiceNo());
    doc.setSettledAmount(BigDecimal.ZERO);
    doc.setUnsettledAmount(invoice.getTotalAmount());
    doc.setStatus("APPROVED");  // 发票已审批，业务单据直接处于 APPROVED 状态
    doc.setCreatedBy(userId);
    docMapper.insert(doc);
    
    // 更新发票回写：业务单据ID
    invoice.setDocId(doc.getId());
    invoice.setDocNo(doc.getDocNo());
    invoiceMapper.updateById(invoice);
    
    return doc;
}

private void generateVoucherFromBusinessDoc(BusinessDocEntity doc, Long userId) {
    // 复用 BusinessDocServiceImpl 的凭证生成逻辑
    // 改为调用 businessDocService.generateVoucher(doc.getId(), userId);
    // 或者直接调 taxService.generateVoucherFromInvoice(invoiceId, userId);
}
```

**关键变化**：
1. `createReceivableFromInvoiceDirect()` → `createBusinessDocFromInvoice()`
2. 业务单据直接设为 `APPROVED`（发票已通过人工审核）
3. 无需再走业务单据的提交/审批流程
4. `ReceivableMapper` → `BusinessDocMapper`

### 2.4 BusinessDocServiceImpl 修改

**2.4.1 放开 `generateVoucher()` 中的 INVOICE_OUT 限制**

```java
// 删除此段 (line 308-311):
// if ("INVOICE_OUT".equals(entity.getDocType())) {
//     throw BusinessException.badRequest("销售发票应收单不能独立生成凭证，请从发票模块操作");
// }
```

改为：对于 INVOICE_OUT，允许从业务单据生成凭证。
但注意：P33 的凭证生成从发票直连，P34 改回从业务单据生成后，需要保证不重复生成。

```java
// INVOICE_OUT 类型不再特殊拦截
// 所有 APPROVED 且无 voucherId 的业务单据均可生成凭证
if (!"APPROVED".equals(entity.getStatus())) {
    throw BusinessException.badRequest("仅已审批状态可生成凭证");
}
```

**2.4.2 修改 `populateSettlementAmounts()`**

当前实现查询 `t_receivable`/`t_payable` 取结算金额。改为直接从 `t_business_doc` 自身字段读取：

```java
private void populateSettlementAmounts(BusinessDocVO vo, BusinessDocEntity entity) {
    // P34: 结算金额直接来自业务单据自身字段
    vo.setSettledAmount(entity.getSettledAmount());
    vo.setUnsettledAmount(entity.getUnsettledAmount());
}
```

**2.4.3 移除 `ReceivableMapper`/`PayableMapper` 注入**

```java
// 删除以下字段 (line 100-101):
// private final ReceivableMapper receivableMapper;
// private final PayableMapper payableMapper;
```

### 2.5 核销结算系统修改

#### 2.5.1 ArapSettlementServiceImpl.confirm()

当前：更新 `ReceivableEntity.settledAmount` / `PayableEntity.settledAmount`
改为：更新 `BusinessDocEntity.settledAmount`

```java
@Override
@Transactional
public ArapSettlementEntity confirm(Long id) {
    ArapSettlementEntity entity = getById(id);
    // ... 状态校验 ...
    
    List<ArapSettlementEntryEntity> entries = entryMapper.selectList(...);
    for (ArapSettlementEntryEntity entry : entries) {
        if (entry.getBusinessDocId() != null) {
            BusinessDocEntity doc = businessDocMapper.selectById(entry.getBusinessDocId());
            if (doc != null) {
                BigDecimal newSettled = doc.getSettledAmount().add(entry.getSettledAmount());
                doc.setSettledAmount(newSettled);
                doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
                
                // 自动更新核销状态
                if (doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
                    doc.setStatus("FULLY_RECONCILED");
                } else {
                    doc.setStatus("PARTIALLY_RECONCILED");
                }
                
                businessDocMapper.updateById(doc);
            }
        }
    }
    entity.setStatus(ArapStatus.CONFIRMED);
    mapper.updateById(entity);
    return entity;
}
```

#### 2.5.2 ReconciliationServiceImpl.recommend()

当前：查询 `ReceivableEntity`/`PayableEntity` 进行匹配推荐
改为：查询 `BusinessDocEntity`（docType=INVOICE_OUT/INVOICE_IN）

```java
private RecommendResult recommend(...) {
    List<BusinessDocEntity> invoices;
    if (isReceipt) {
        invoices = businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                .eq(BusinessDocEntity::getCustomerId, customerId)
                .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED")));
    } else {
        invoices = businessDocMapper.selectList(
            new LambdaQueryWrapper<BusinessDocEntity>()
                .eq(BusinessDocEntity::getDocType, "INVOICE_IN")
                .eq(BusinessDocEntity::getSupplierId, vendorId)
                .in(BusinessDocEntity::getStatus, List.of("APPROVED", "VOUCHERED", "PARTIALLY_RECONCILED")));
    }
    // ... 后续匹配逻辑使用 BusinessDocEntity 的字段 ...
}
```

#### 2.5.3 ReconciliationServiceImpl.execute()

当前：直接更新 `ReceivableEntity.settledAmount`
改为：直接更新 `BusinessDocEntity.settledAmount`

```java
if ("INVOICE_OUT".equals(request.targetDocType())) {
    BusinessDocEntity doc = businessDocMapper.selectById(request.targetDocId());
    // ... 校验 ...
    BigDecimal newSettled = doc.getSettledAmount().add(request.amount());
    doc.setSettledAmount(newSettled);
    doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
    // 更新状态
    if (doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
        // 还要通知来源单据（发票）更新状态
        doc.setStatus("FULLY_RECONCILED");
    } else {
        doc.setStatus("PARTIALLY_RECONCILED");
    }
    businessDocMapper.updateById(doc);
    
    // 通知发票状态
    if (doc.getInvoiceNo() != null) {
        // 找到对应发票更新核销状态
        OutputInvoiceEntity invoice = invoiceMapper.selectByInvoiceNo(doc.getInvoiceNo());
        if (invoice != null) {
            invoiceStateMachine.onReconciliationUpdate(invoice.getId(), doc.getUnsettledAmount(), userId);
        }
    }
}
```

#### 2.5.4 ArapSettlementEntryEntity 修改

```java
// 新增字段（替换 receivableId/payableId）
private Long businessDocId;

// 保留 receivableId/payableId 做历史兼容（迁移后可为 null）
private Long receivableId;
private Long payableId;
```

#### 2.5.5 ReconciliationLogEntity 修改

当前有 `targetDocId`（指向 receivable/payable ID）+ `targetDocType`
新增 `targetBusinessDocId`，迁移后逐步切换。

### 2.6 InputInvoiceImportService 修改（进项发票）

当前流程：
```
进项发票确认 → 创建 BusinessDocEntity (INVOICE_IN) + 创建 PayableEntity
```

改为：
```
进项发票确认 → 创建 BusinessDocEntity (INVOICE_IN)   // 不再创建 PayableEntity
```

**核心改动**：
```java
// 移除 createPayableFromInvoice() 的调用 (line 306)
// 移除 createPayableFromInvoice() 方法 (line 436-452)

// 在创建 INVOICE_IN 业务单据时同步设置结算字段
doc.setSettledAmount(BigDecimal.ZERO);
doc.setUnsettledAmount(row.getAmount());
```

### 2.7 ReceivableServiceImpl / PayableServiceImpl 迁移

**现状**：这两个 Service 提供以下能力：
- `pageQuery()` — 分页查询应收/应付
- `create()` — 创建应收/应付
- `confirm()` — 确认状态
- `markSettled()` — 标记结清
- `reverse()` — 冲销
- `overdueList()` — 逾期列表
- `agingAnalysis()` — 账龄分析
- `overallAging()` — 总体账龄

**迁移方案**：
1. `pageQuery()` → 前端直接查业务单据列表（BusinessDocList.vue），通过 docType=INVOICE_OUT/INVOICE_IN 过滤
2. `create()` → 创建业务单据
3. `confirm()` → 已被业务单据的 submit/approve 替代
4. `markSettled()` → 核销完成后自动更新状态，无需手动标记
5. `reverse()` → 复用 BusinessDocServiceImpl.reverse() 的红冲逻辑
6. `agingAnalysis()` / `overdueList()` → 迁移到 BusinessDocService，基于 `t_business_doc` 查询
7. `overallAging()` → 同上

**账龄分析迁移 SQL**：

```sql
-- 基于 t_business_doc 的账龄分析
SELECT
    CASE
        WHEN CURRENT_DATE - due_date <= 0 THEN 'current'
        WHEN CURRENT_DATE - due_date <= 30 THEN 'days_0_30'
        WHEN CURRENT_DATE - due_date <= 60 THEN 'days_31_60'
        WHEN CURRENT_DATE - due_date <= 90 THEN 'days_61_90'
        WHEN CURRENT_DATE - due_date <= 180 THEN 'days_91_180'
        WHEN CURRENT_DATE - due_date <= 365 THEN 'days_181_365'
        ELSE 'over_365'
    END AS aging_bucket,
    COUNT(*) AS count,
    SUM(unsettled_amount) AS amount
FROM t_business_doc
WHERE doc_type IN ('INVOICE_OUT', 'INVOICE_IN')
  AND status NOT IN ('REVERSED', 'DRAFT', 'REJECTED')
  AND unsettled_amount > 0
  AND customer_id = ?
GROUP BY aging_bucket
ORDER BY aging_bucket;
```

### 2.8 清理引用

以下 18 个 Java 文件引用 `ReceivableMapper`，需要逐文件评估：

| # | 文件 | 引用方式 | 处理方式 |
|---|------|---------|---------|
| 1 | `OutputInvoiceStateMachineServiceImpl.java` | 创建 ReceivableEntity | ✅ 改为 BusinessDocMapper |
| 2 | `BusinessDocServiceImpl.java` | 查询 ReceivableEntity 获取结算金额 | ✅ 改为读自身字段 |
| 3 | `ArapSettlementServiceImpl.java` | 确认核销时更新 ReceivableEntity | ✅ 改为 BusinessDocMapper |
| 4 | `ReconciliationServiceImpl.java` | 推荐+执行核销 | ✅ 改为 BusinessDocMapper |
| 5 | `AutoGenerationService.java` | 银行流水 → 应收 | ✅ 改为 BusinessDocMapper |
| 6 | `ReceivableServiceImpl.java` | CRUD + 账龄 | ✅ 迁移到 BusinessDocService |
| 7 | `ReceivableStateMachineServiceImpl.java` | 状态机 | ✅ 合并到 BusinessDocService |
| 8 | `TaxServiceImpl.java` | 取发票关联 | 需评估 |
| 9 | `CustomerServiceImpl.java` | 客户余额 | ✅ 改为查询 t_business_doc |
| 10 | `BadDebtServiceImpl.java` | 坏账准备 | ✅ 改为查询 t_business_doc |
| 11 | `ClearDataService.java` | 数据清理 | ✅ 移除 |
| 12 | `BankStatementServiceImpl.java` | 银行对账 | ✅ 改为查询 t_business_doc |
| 13 | `NumberingConsistencyCheckJob.java` | 编号一致性 | ✅ 改为检查业务单据 |
| 14 | `NumberingTraceServiceImpl.java` | 编号追溯 | ✅ 改为业务单据追溯 |
| 15 | `PrepaymentServiceImpl.java` | 预付款核销 | ✅ 改为关联 BusinessDocEntity |
| 16 | `ReconciliationReportController.java` | 核销报表 | ✅ 改为查询 t_business_doc |
| 17 | `SalesInvoiceImportService.java` | 销售发票导入 | ✅ 已移除业务单创建（P33），保留现状 |
| 18 | `InputInvoiceImportService.java` | 创建 PayableEntity | ✅ 移除 PayableEntity 创建 |

**Payable 侧类似清理**（`PayableMapper` 引用的 5 个文件）：
- `InputInvoiceImportService.java` — 移除 `createPayableFromInvoice()`
- `AutoGenerationService.java` — 改为 BusinessDocMapper
- `ClearDataService.java` — 移除 payable 清理
- `NumberingTraceServiceImpl.java` — 改为查询 t_business_doc
- `BankStatementServiceImpl.java` — 改为查询 t_business_doc

### 2.9 前端修改

#### 2.9.1 BusinessDocList.vue

**移除 INVOICE_OUT 过滤（line 37）**：

```vue
<!-- 改前 -->
v-for="(label, value) in Object.entries(DOC_TYPE_LABELS).filter(([k]) => k !== 'INVOICE_OUT')"

<!-- 改后 -->
v-for="(label, value) in Object.entries(DOC_TYPE_LABELS)"
```

**移除 P33 警告条（line 12-14）**：

```vue
<!-- 删除以下 alert -->
<el-alert title="销售发票流程已简化：..." type="warning" ... />
```

**操作列放开 INVOICE_OUT 的凭证生成（line 83）**：

```vue
<!-- 改前 -->
v-if="row.status === 'APPROVED' && !row.voucherId && row.docType !== 'INVOICE_OUT'"

<!-- 改后 -->
v-if="row.status === 'APPROVED' && !row.voucherId"
```

**新增核销列：已核销金额、未核销金额、到期日**（在金额列后插入）：

```vue
<el-table-column label="已核销" width="120" align="right">
  <template #default="{ row }">
    <span style="color:#67c23a">{{ fmtAmount(row.settledAmount) }}</span>
  </template>
</el-table-column>
<el-table-column label="未核销" width="120" align="right">
  <template #default="{ row }">
    <span :style="{ color: Number(row.unsettledAmount) > 0 ? '#f56c6c' : '#67c23a' }">
      {{ fmtAmount(row.unsettledAmount) }}
    </span>
  </template>
</el-table-column>
```

**标签颜色扩展**：

```typescript
function statusType(s: string) {
  switch (s) {
    case 'DRAFT': return 'info'
    case 'SUBMITTED': return 'primary'
    case 'APPROVED': return 'warning'
    case 'VOUCHERED': return ''
    case 'PARTIALLY_RECONCILED': return 'warning'
    case 'FULLY_RECONCILED': return 'success'
    case 'REVERSED': return 'danger'
    case 'REJECTED': return 'danger'
    case 'CLOSED': return 'info'
    default: return 'info'
  }
}
```

#### 2.9.2 BusinessDocDetail.vue

**放开 INVOICE_OUT 的凭证生成按钮（line 12）**：

```vue
<!-- 改前 -->
v-if="doc?.status === 'APPROVED' && !doc?.voucherId && doc?.docType !== 'INVOICE_OUT'"

<!-- 改后 -->
v-if="doc?.status === 'APPROVED' && !doc?.voucherId"
```

#### 2.9.3 ReceivableList.vue / ReceivableController

**方案 A（推荐）**：将 ReceivableList.vue 改为业务单据的应收视图（过滤 INVOICE_OUT + OTHER_RECEIVABLE），数据源改为 `/business-docs/page?docType=INVOICE_OUT`

**方案 B**：保留 ReceivableList.vue 路由，但内部重定向到 BusinessDocList（带 docType=INVOICE_OUT 参数）

**推荐方案 A**，因为应收核销视图有其独特价值（核销进度条、逾期高亮），应该在业务单据列表中增强，而非删除。

修改后 ReceivableList.vue：
```vue
<!-- 数据源改为 BusinessDocMapper，过滤 INVOICE_OUT -->
const res = await getBusinessDocPage({ docType: 'INVOICE_OUT', ...query.value })
```

#### 2.9.4 routes/base.ts

如果移除独立的 ReceivableList 路由：
```typescript
// 删除或注释以下路由
// { path: '/arap/receivable', component: () => import('@/views/arap/receivable/ReceivableList.vue') }
// { path: '/arap/payable', component: () => import('@/views/arap/payable/PayableList.vue') }
```

或者在业务单据下增加 tab 分类视图。

### 2.10 乐观锁处理

`t_business_doc` 已有 `version` 字段（`@Version` 注解）。核销时对 `settledAmount` 的更新会通过 MyBatis-Plus 的乐观锁机制自动做 CAS 校验。

**核销并发安全**：
```java
// ArapSettlementServiceImpl.confirm() 或 ReconciliationServiceImpl.execute()
// 使用 BusinessDocMapper.updateById(doc) 时，@Version 自动增加
// 如果并发更新，version 不匹配会抛出 OptimisticLockException

// 建议使用 @Retryable 或重试机制处理乐观锁冲突
@Retryable(value = OptimisticLockException.class, maxAttempts = 3)
public void updateSettlement(...) { ... }
```

---

## 3. 测试变更

### 3.1 需要修改的测试

| 测试文件 | 变更内容 | 状态 |
|---------|---------|------|
| `OutputInvoiceStateMachineServiceImplTest.java` | 改为断言 BusinessDocEntity 而非 ReceivableEntity | ⏳ 待修改 |
| `SalesFlowE2ETest.java` | 验证业务单据的结算字段 | ⏳ 待修改 |
| `ArapSettlementServiceImplTest.java` | 改为验证 BusinessDocEntity 更新 | ⏳ 待修改 |
| `ReconciliationServiceImplTest.java` | 推荐+执行核销的目标改为业务单据 | ⏳ 待修改 |
| `ReceivableServiceImplTest.java` | 迁移到 BusinessDocServiceTest | ⏳ 待修改 |
| `NumberingFrontendApiTest.java` | 更新编号关联链路 | ⏳ 待修改 |
| `BusinessDocServiceImplTest.java` | 新增 INVOICE_OUT 的测试（if exists） | ⏳ 待新增 |

### 3.2 负向断言要点

```java
// 1. 确认不再创建 ReceivableEntity
long recvCount = receivableMapper.selectCount(...);
assertEquals(0, recvCount);

// 2. 确认创建了业务单据
BusinessDocEntity doc = docMapper.selectOne(
    new LambdaQueryWrapper<BusinessDocEntity>()
        .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
        .eq(BusinessDocEntity::getDocType, "INVOICE_OUT"));
assertNotNull(doc);
assertEquals(invoice.getTotalAmount(), doc.getAmount());

// 3. 确认结算字段正确
assertEquals(BigDecimal.ZERO, doc.getSettledAmount());
assertEquals(invoice.getTotalAmount(), doc.getUnsettledAmount());

// 4. 确认核销更新业务单据的结算金额
// 核销后查询 doc.getSettledAmount() 应等于核销金额
```

---

## 4. 影响范围与风险

### 4.1 影响矩阵

| 模块 | 影响程度 | 说明 |
|------|---------|------|
| 销售发票审核（OutputInvoiceStateMachineService） | 🔴 高 | 核心改动：改回创建业务单据 |
| 核销结算（ArapSettlementService + ReconciliationService） | 🔴 高 | 目标实体从 receivable → business doc |
| 业务单据（BusinessDocService） | 🟡 中 | 放开 INVOICE_OUT 限制，增加结算字段 |
| 前端业务单据列表 | 🟡 中 | 显示 INVOICE_OUT + 核销列 |
| 前端应收列表 | 🟡 中 | 合并到业务单据列表 |
| 进项发票导入（InputInvoiceImportService） | ✅ 低 | 移除 PayableEntity 创建 |
| 编号关联（NumberingTraceService） | ✅ 低 | 链路从 receivable → business doc |
| 数据清理（ClearDataService） | ✅ 低 | 移除 receivable/payable 清理代码 |
| 报表（ReconciliationReportController） | ✅ 低 | 查询目标改为业务单据 |
| DB 迁移 | 🔴 高 | 涉及 3 个 migration，需验证数据完整性 |

### 4.2 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 数据迁移丢失 | 🔴 高 | V69 使用 `NOT EXISTS` 防重复；迁移后验证行数一致后再执行 V70 |
| 核销并发冲突 | 🟡 中 | `@Version` + `@Retryable` 乐观锁重试 |
| 前端 INVOICE_OUT 显示异常 | 🟡 中 | 按类型分别处理：列表中新增核销相关操作按钮 |
| 历史数据兼容 | 🟡 中 | 保留 `docId`/`docNo` 字段，历史 receivable 通过 mapping 指向已迁移的业务单据 |
| 回滚困难 | 🔴 高 | V70 drop 表不可逆；V68/V69 可回滚 |

### 4.3 回滚方案

**V68（加字段）**：
```sql
ALTER TABLE t_business_doc DROP COLUMN settled_amount;
ALTER TABLE t_business_doc DROP COLUMN unsettled_amount;
ALTER TABLE t_business_doc DROP COLUMN due_date;
```

**V69（迁移数据）**：
```sql
-- 删除迁移插入的业务单据（INVOICE_OUT/INVOICE_IN 类型）
DELETE FROM t_business_doc WHERE doc_type IN ('INVOICE_OUT', 'INVOICE_IN') AND source = 'IMPORTED';
-- 删除新增列
ALTER TABLE t_arap_settlement_entry DROP COLUMN business_doc_id;
ALTER TABLE t_reconciliation_log DROP COLUMN target_business_doc_id;
```

**V70（删表）**：不可回滚。执行前必须确认所有数据验证通过。

---

## 5. 分阶段实施计划

### 第一阶段：DB + 后端基础（M1）
| 步骤 | 内容 |
|------|------|
| 1.1 | 编写 V68 migration（加字段） |
| 1.2 | BusinessDocEntity + BusinessDocVO 新增字段 |
| 1.3 | BusinessDocServiceImpl.populateSettlementAmounts() 改为读自身字段 |
| 1.4 | 前端 DOC_STATUS_LABELS 新增核销状态 |
| 1.5 | 验证：mvn test 0 fail |

### 第二阶段：销售发票链路（M2）
| 步骤 | 内容 |
|------|------|
| 2.1 | 修改 OutputInvoiceStateMachineServiceImpl.confirm() |
| 2.2 | 修改 BusinessDocServiceImpl.generateVoucher() 放开限制 |
| 2.3 | 修改 BusinessDocList.vue 放开 INVOICE_OUT 过滤 |
| 2.4 | 验证：新建销售发票 → 审核 → 业务单据创建 → 凭证生成 |

### 第三阶段：核销结算系统（M3）
| 步骤 | 内容 |
|------|------|
| 3.1 | 修改 ArapSettlementServiceImpl.confirm() |
| 3.2 | 修改 ReconciliationServiceImpl.recommend() + execute() |
| 3.3 | 修改 ArapSettlementEntryEntity 增加 businessDocId |
| 3.4 | 新增 `@Retryable` 乐观锁冲突重试 |
| 3.5 | 验证：核销操作 → 业务单据 settledAmount 更新 |

### 第四阶段：数据迁移 + 清理（M4）
| 步骤 | 内容 |
|------|------|
| 4.1 | 编写 V69 migration（迁移数据） |
| 4.2 | 清理 10+ 个引用 ReceivableMapper/PayableMapper 的文件 |
| 4.3 | 合并 ReceivableServiceImpl 能力到 BusinessDocService |
| 4.4 | 前端 ReceivableList.vue 改造 |
| 4.5 | 验收通过后执行 V70 drop 表 |

---

## 6. 验收标准

- [ ] 销售发票审核后创建 INVOICE_OUT 业务单据（APPROVED 状态），不再创建 ReceivableEntity
- [ ] 业务单据列表展示 INVOICE_OUT 类型，无警告条
- [ ] INVOICE_OUT 业务单据可从列表生成凭证
- [ ] INVOICE_OUT 详情页显示已核销/未核销金额
- [ ] 核销确认后更新业务单据的 settledAmount/unsettledAmount
- [ ] 核销后 unsettledAmount=0 时自动将状态改为 FULLY_RECONCILED
- [ ] 核销推荐（recommend）基于业务单据而不是 receivable/payable 表
- [ ] 进项发票不再创建 PayableEntity
- [ ] 账龄分析基于 t_business_doc 查询
- [ ] V69 迁移后 t_receivable 数据完整迁移到 t_business_doc
- [ ] V70 删除 t_receivable/t_payable 后系统运行正常
- [ ] `mvn test` Failures: 0（允许 16 个 H2 历史 Errors）
- [ ] 核销并发更新不丢失数据（乐观锁验证）

---

## 7. 与 P33 的关系

**P33 简化**（将被撤销）：
> 销售发票审核后直接生成应收单 + 凭证，不再经过业务单中间环节

**P34 恢复**：
> 销售发票审核后走回业务单据体系（INVOICE_OUT），核销金额直接记录在业务单据上

**核心差异**：

| 对比项 | P33 | P34 |
|--------|-----|-----|
| 应收载体 | `t_receivable` 独立表 | `t_business_doc` docType=INVOICE_OUT |
| 创建时机 | 发票审核时 | 发票审核时（同 P33） |
| 凭证生成 | 从发票直连 | 从业务单据生成 |
| 结算更新 | 更新 ReceivableEntity | 更新 BusinessDocEntity |
| 表数量 | 余 `t_receivable` + `t_payable` | 全部合并到 `t_business_doc` |

---

## 8. 相关文件

| 文件 | 变更类型 |
|------|---------|
| `backend/.../db/migration/V68__add_settlement_columns_to_business_doc.sql` | 新增 |
| `backend/.../db/migration/V69__migrate_receivable_to_business_doc.sql` | 新增 |
| `backend/.../db/migration/V70__drop_receivable_payable_tables.sql` | 新增 |
| `backend/.../entity/BusinessDocEntity.java` | 修改：加字段 |
| `backend/.../dto/BusinessDocVO.java` | 修改：fromEntity 同步读取新字段 |
| `backend/.../service/impl/BusinessDocServiceImpl.java` | 修改：放开 INVOICE_OUT + 改 populateSettlementAmounts |
| `backend/.../service/impl/OutputInvoiceStateMachineServiceImpl.java` | 修改：恢复创建 BusinessDocEntity |
| `backend/.../service/impl/ArapSettlementServiceImpl.java` | 修改：改为更新 BusinessDocEntity |
| `backend/.../service/impl/ReconciliationServiceImpl.java` | 修改：推荐+执行操作 BusinessDocEntity |
| `backend/.../entity/ArapSettlementEntryEntity.java` | 修改：加 businessDocId |
| `backend/.../service/impl/InputInvoiceImportService.java` | 修改：移除 PayableEntity 创建 |
| `backend/.../service/impl/ReceivableServiceImpl.java` | 废弃：能力迁移到 BusinessDocService |
| `backend/.../service/impl/PayableServiceImpl.java` | 废弃：能力迁移到 BusinessDocService |
| `backend/.../service/impl/ReceivableStateMachineServiceImpl.java` | 废弃 |
| `backend/.../entity/ReceivableEntity.java` | 废弃：V70 后删除 |
| `backend/.../entity/PayableEntity.java` | 废弃：V70 后删除 |
| `backend/.../service/impl/AutoGenerationService.java` | 修改：改为 BusinessDocMapper |
| `backend/.../service/impl/BankStatementServiceImpl.java` | 修改：改为查询业务单据 |
| `backend/.../service/impl/CustomerServiceImpl.java` | 修改：客户余额查询改业务单据 |
| `backend/.../service/impl/BadDebtServiceImpl.java` | 修改：坏账查询改业务单据 |
| `backend/.../service/impl/NumberingTraceServiceImpl.java` | 修改：链路改业务单据 |
| `frontend/src/views/finance/business-doc/BusinessDocList.vue` | 修改：放开 INVOICE_OUT 过滤 |
| `frontend/src/views/finance/business-doc/BusinessDocDetail.vue` | 修改：放开凭证生成 |
| `frontend/src/views/arap/receivable/ReceivableList.vue` | 修改：改为业务单据视图 |
| `frontend/src/api/modules/businessDoc.ts` | 修改：扩展 DOC_STATUS_LABELS |
| `frontend/src/router/routes/base.ts` | 修改：可选移除独立应收路由 |
