# P33 开发计划：销售发票流程简化

> **目标**：移除销售发票→应收单链路中的业务单中间环节，发票审核通过后直接生成应收单+凭证
> **编号关联**：发票↔应收单↔凭证 双向追溯必须保持
> **依据**：`docs/specs/P33-streamline-sales-invoice-flow.md`

---

## 开发批次

### 批次 1：DB Migration + Entity 字段（低风险）

**文件**：
- `db/migration/V65__streamline_sales_invoice_flow.sql`（新建）
- `backend/src/main/java/com/huicai/module/arap/entity/ReceivableEntity.java`（修改）

**改动**：
1. V65 migration：给 `t_receivable` 加 `invoice_id` 列 + 索引
2. `ReceivableEntity` 新增 `invoiceId` 字段
3. 确认 `t_receivable` 已有 `invoice_no` 列（V64 已添加）

**验证**：
- `mvn test -Dtest=ReceivableMapperTest` 通过
- psql 验证：`\d t_receivable` 确认新列存在

---

### 批次 2：核心逻辑修改 — OutputInvoiceStateMachineService（中风险）

**文件**：
- `backend/src/main/java/com/huicai/module/tax/service/impl/OutputInvoiceStateMachineServiceImpl.java`

**改动**：
1. `confirm()` 方法：移除 `createBusinessDocAndReceivableAfterConfirm()` 调用
2. 新增 `createReceivableFromInvoiceDirect()`：直接从发票创建应收单（`invoice_id` 关联）
3. 新增 `generateVoucherFromInvoiceDirect()`：调用 `taxService.generateVoucherFromInvoice()` 生成凭证
4. 删除 `createBusinessDocAndReceivableAfterConfirm()`、`createBusinessDocFromInvoice()`、`generateDocNo()` 方法
5. 保留 `createReceivableFromInvoice()`（可能被其他地方调用，先不动）
6. 保留 `generateReceivableNo()`（应收单编号生成仍需使用）

**关键逻辑**：
```java
private void createReceivableFromInvoiceDirect(OutputInvoiceEntity invoice, Long userId) {
    // 防重复：查 invoice_id 是否有应收单
    // 创建应收单：setInvoiceId(invoice.getId()), setInvoiceNo(invoice.getInvoiceNo())
    // 回写发票：invoice.setReceivableId(recv.getId()), invoice.setReceivableNo(receivableNo)
}
```

**验证**：
- 编译通过
- `mvn test -Dtest=OutputInvoiceStateMachineServiceImplTest` 通过
- 单元测试新增 `confirm_shouldCreateReceivableAndVoucherDirectly()`

---

### 批次 3：TaxService 适配（低风险）

**文件**：
- `backend/src/main/java/com/huicai/module/tax/service/impl/TaxServiceImpl.java`

**改动**：
1. `generateVoucherFromInvoice()` 确认 `sourceDocId = invoice.getId()`（当前可能未设置，需检查）
2. 确认 `sourceDocNo = invoice.getInvoiceNo()`（当前已是硬编码）
3. 确认 `sourceDocType = "OUTPUT_INVOICE"`（当前已是硬编码）

**验证**：
- 检查 `generateVoucherFromInvoice()` 代码，确认 `voucher.setSourceDocId(invoice.getId())` 存在
- 如果不存在，添加这一行

---

### 批次 4：测试更新（中风险）

**文件**：
- `backend/src/test/java/com/huicai/module/tax/service/impl/OutputInvoiceStateMachineServiceImplTest.java`
- `backend/src/test/java/com/huicai/module/finance/e2e/SalesFlowE2ETest.java`
- `backend/src/test/java/com/huicai/module/finance/e2e/NumberingFrontendApiTest.java`（如有）

**改动**：
1. 移除 `confirm` 后检查业务单创建的断言
2. 新增 `confirm` 后检查应收单+凭证创建的断言
3. 新增负断言：`verify(docMapper, never()).insert(any())`
4. E2E 测试更新链路：发票 → 应收单 → 凭证（移除业务单步骤）
5. 编号关联 E2E 测试：验证发票号→应收单号→凭证号双向追溯

**验证**：
- `mvn test` 全部通过（514+ 快测试）
- E2E 测试全部通过

---

### 批次 5：编号关联查询接口更新（低风险）

**文件**：
- `backend/src/main/java/com/huicai/module/finance/service/impl/NumberingTraceServiceImpl.java`
- `references/numbering-association-design.md`

**改动**：
1. 编号关联查询接口 `GET /api/v1/vouchers/trace?no={编号}` 中，销售发票链路不再经过 `BUSINESS_DOC`
2. 更新 `buildUpstreamChain()` 逻辑：
   - 输入 `OUTPUT_INVOICE` → 直接查 `t_receivable.invoice_id` → 查 `t_voucher.source_doc_id`
   - 不再查 `t_business_doc`
3. 更新下游链路：应收单 → 凭证（不变）

**验证**：
- curl 测试：`GET /api/v1/vouchers/trace?no=XS-2026-0001` 返回正确链路

---

### 批次 6：文档更新（低风险）

**文件**：
- `docs/linkage-map.md`（更新 L1 链路验证脚本）
- `references/numbering-association-design.md`（更新链路图）
- `docs/DESIGN.md`（已更新）
- `docs/tasks/P31-auto-flow-after-import_任务书.md`（已更新）
- `AGENTS.md` §0 项目状态更新

**改动**：
1. linkage-map.md L1 链路：移除业务单验证步骤，改为发票→应收单→凭证
2. numbering-association-design.md：更新链路图为简化版
3. AGENTS.md：更新 P31 状态和 commit 基准

---

## 整体时间估算

| 批次 | 预估时间 | 风险 |
|------|---------|------|
| 1. Migration + Entity | 30 分钟 | 低 |
| 2. 核心逻辑修改 | 1-2 小时 | 中 |
| 3. TaxService 适配 | 15 分钟 | 低 |
| 4. 测试更新 | 1-2 小时 | 中 |
| 5. 编号关联查询 | 30 分钟 | 低 |
| 6. 文档更新 | 15 分钟 | 低 |
| **总计** | **约 3-4 小时** | |

---

## 执行顺序

```
批次1 (Migration) → 批次2 (核心逻辑) → 批次3 (TaxService) → 批次4 (测试)
                                                        ↓
                                                   编译通过 & mvn test 全绿
                                                        ↓
                                               批次5 (编号关联查询) → 批次6 (文档)
```

每批次完成后 commit + push，跑 CI 确认全绿后再进行下一批。
