# P33 代码与SPEC差距分析

> **编号**：HUICAI-SPC-033A
> 依据: `docs/specs/P33-streamline-sales-invoice-flow.md` + `docs/tasks/P33-streamline-sales-invoice-flow_开发计划.md`
> 对比范围: 实际代码 vs SPEC 设计

---

> **关联需求**: REQ-2026-007, REQ-2026-008
## 一、已完成项 ✅

### 1. 核心流程简化

| SPEC 要求 | 实际代码 | 状态 |
|-----------|----------|------|
| `confirm()` 移除业务单创建，直连应收单 | `OutputInvoiceStateMachineServiceImpl.confirm()` 调用 `createReceivableFromInvoiceDirect()` | ✅ 已实现 |
| 新增 `createReceivableFromInvoiceDirect()` 方法 | L175-217，直接创建应收单，通过 `invoice_id` 关联 | ✅ 已实现 |
| 移除 `createBusinessDocAndReceivableAfterConfirm()` | 该方法已不存在 | ✅ 已实现 |
| 移除 `createBusinessDocFromInvoice()` | 该方法已不存在 | ✅ 已实现 |
| 移除 `generateDocNo()` | 该方法已不存在 | ✅ 已实现 |

### 2. 编号关联体系

| SPEC 要求 | 实际代码 | 状态 |
|-----------|----------|------|
| 应收单 `invoice_id` 字段 | `ReceivableEntity.invoiceId` (L25) | ✅ 已实现 |
| 应收单 `receivableNo` 字段 | `ReceivableEntity.receivableNo` (L48-49) | ✅ 已实现 |
| 发票 `receivable_id` 字段 | `OutputInvoiceEntity.receivableId` | ✅ 已实现 |
| 发票 `receivable_no` 字段 | `OutputInvoiceEntity.receivableNo` | ✅ 已实现 |
| 凭证 `sourceDocId = invoice.getId()` | `TaxServiceImpl` L390, L452 | ✅ 已实现 |
| 凭证 `sourceDocType = OUTPUT_INVOICE` | `TaxServiceImpl` L390 | ✅ 已实现 |

### 3. 编号关联查询

| SPEC 要求 | 实际代码 | 状态 |
|-----------|----------|------|
| 销售发票链路不再经过 BUSINESS_DOC | `NumberingTraceServiceImpl` L236-238 下游链路: OUTPUT_INVOICE → RECEIVABLE | ✅ 已实现 |
| 应收单上游追溯到销售发票 | `buildInvoiceUpstream()` L421-434 通过 `invoiceNo` 查找 | ✅ 已实现 |
| 凭证上游追溯到销售发票 | `buildNextUpstreamForVoucher()` L414 `OUTPUT_INVOICE` 分支 | ✅ 已实现 |

### 4. 数据库迁移

| SPEC 要求 | 实际文件 | 状态 |
|-----------|----------|------|
| V65: 应收单加 `receivable_id` + `receivable_no` | `V65__add_receivable_no_to_output_invoice.sql` | ✅ 已创建 |
| V66: 应收单加 `invoice_id` + `invoice_no` | `V66__add_invoice_id_to_receivable.sql` | ✅ 已创建 |

### 5. 前端展示

| SPEC 要求 | 实际代码 | 状态 |
|-----------|----------|------|
| 发票详情页显示业务流程链路 | `OutputInvoiceList.vue` L294-330 合并展示 | ✅ 已实现 |
| 应收核销页面优化 | `ReceivableList.vue` 核销进度条 | ✅ 已实现 |

---

## 二、差距项 ⚠️

### 差距 1: 凭证生成时机

**SPEC 要求**（P33 SPEC §2.3）:
```java
// confirm() 中应同时调用:
createReceivableFromInvoiceDirect(entity, userId);
generateVoucherFromInvoiceDirect(entity, userId);  // ← 审核后自动生成凭证
```

**实际代码**（`OutputInvoiceStateMachineServiceImpl.confirm()` L82-86）:
```java
// P33: 审核后直接创建应收单（不再经过业务单）
createReceivableFromInvoiceDirect(invoiceId, userId);
// ❌ 缺少 generateVoucherFromInvoiceDirect() 调用
```

**影响**: 发票审核通过后只创建了应收单，凭证仍需用户手动点击"生成凭证"按钮。

**建议**: 根据 P31 任务书"人工仅需最后一步凭证审核"的目标，应在 `confirm()` 中同时调用 `generateVoucherFromInvoiceDirect()`。

---

### 差距 2: V66 迁移缺少 `invoice_id` 字段

**SPEC 要求**（P33 SPEC §2.4）:
```sql
ALTER TABLE t_receivable ADD COLUMN invoice_id BIGINT REFERENCES t_output_invoice(id);
```

**实际文件**（`V66__add_invoice_id_to_receivable.sql`）:
```sql
-- 需要检查是否包含 invoice_id 字段
```

**建议**: 确认 V66 迁移脚本包含 `invoice_id` 列和索引。

---

### 差距 3: 应收单 `invoice_id` 回填

**SPEC 要求**: `fillOutputInvoiceDetails()` 应通过 `receivable_id` 查询应收单并回填 `receivableNo`。

**实际代码**（`TaxServiceImpl.fillOutputInvoiceDetails()` L297-302）:
```java
if (inv.getReceivableId() != null) {
    var recv = receivableMapper.selectById(inv.getReceivableId());
    if (recv != null && recv.getReceivableNo() != null) {
        inv.setReceivableNo(recv.getReceivableNo());
    }
}
```

**状态**: ✅ 已实现，但需要注意 `ReceivableEntity` 的 `receivableNo` 字段需要通过 V66 migration 添加到数据库。

---

### 差距 4: 历史数据迁移

**SPEC 要求**（P33 SPEC §2.4 V65 Migration）:
```sql
-- 历史数据补全
UPDATE t_receivable r
SET invoice_id = (SELECT id FROM t_output_invoice i WHERE i.invoice_no = r.invoice_no)
WHERE r.invoice_id IS NULL AND r.invoice_no IS NOT NULL;
```

**实际文件**: `V66__add_invoice_id_to_receivable.sql` 应包含历史数据补全脚本。

**建议**: 在 V66 migration 末尾添加历史数据补全 SQL。

---

### 差距 5: 测试用例更新

**SPEC 要求**（P33 SPEC §3.1）:
- 移除 `confirm` 后检查业务单创建的断言
- 新增 `confirm` 后检查应收单+凭证创建的断言
- 新增负断言：`verify(docMapper, never()).insert(any())`

**实际状态**: 需要检查测试文件是否已更新。

---

## 三、总结

| 类别 | 数量 | 状态 |
|------|------|------|
| 已完成项 | 15 | ✅ |
| 差距项 | 5 | ⚠️ |
| 其中高优先级 | 2 (凭证生成时机, V66 migration) | 🔴 |
| 中优先级 | 2 (历史数据迁移, 测试更新) | 🟡 |
| 低优先级 | 1 (invoice_id 回填) | 🟢 |

**建议优先处理**:
1. 🔴 `confirm()` 中补充 `generateVoucherFromInvoiceDirect()` 调用（实现审核后自动生成凭证）
2. 🔴 确认 V66 migration 包含 `invoice_id` 字段和历史数据补全
3. 🟡 更新测试用例
