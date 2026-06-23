# P27 — BusinessDoc 客户/供应商名称字段补齐（修复 P26 P1-1 回归）

> 日期：2026-06-23 | 基于：docs/specs/P27-businessdoc-customer-vendor-name-fix.md
> 触发：mvn test 4 fail（BusinessDocServiceImplTest.generateVoucher_*）+ BUILD FAILURE
> 工期：3 批，4 commit 估算

---

## 总览

| 批次 | 内容 | commit | 预估 |
|:---:|:---|:---:|:---:|
| **P0-1** | V50 migration + BusinessDocEntity 加字段 | 1 | 0.5h |
| **P0-2** | 4 个写入点反查填名 + generateVoucher 编译修复 | 2 | 1.5h |
| **P0-3** | 单测补齐（4 fail + 新增 3 个字段持久化测试） + mvn test 390/0/0 | 1 | 1h |
| **合计** | | **4 commit** | **3h** |

---

## 第一批 P0-1 — Schema + Entity

### 改动

| 项 | 内容 |
|:---|:------|
| **改动文件** | 新建 `db/migration/V50__add_business_doc_customer_supplier_name.sql` |
| | 修改 `finance/entity/BusinessDocEntity.java`（+customerName/supplierName） |

### V50 SQL

```sql
ALTER TABLE t_business_doc
  ADD COLUMN customer_name VARCHAR(200),
  ADD COLUMN supplier_name VARCHAR(200);
```

### Entity 改动

```java
@TableField("customer_name")
private String customerName;

@TableField("supplier_name")
private String supplierName;
```

### 验证

- [ ] `mvn compile` 通过（Entity 加字段后编译不会出错）
- [ ] `psql -c "\d t_business_doc"` 看到 2 个新列
- [ ] `mvn test -Dtest=VoucherServiceImplTest` 等无关测试仍绿（不引入回归）

---

## 第二批 P0-2 — 4 个写入点 + generateVoucher 编译修复

### 改动文件

| # | 文件 | 改动点 |
|:---|:------|:------|
| 1 | `BusinessDocServiceImpl.java` create / update | customerId/vendorId 非空时反查 Customer/Vendor Service 取名称 |
| 2 | `BusinessDocServiceImpl.java` generateVoucher 317-318 | **编译失败根因**：因 Entity 已加字段，这行能编译过。逻辑保持 |
| 3 | `SalesInvoiceImportService.java` | 写入 t_business_doc 时填 customerName（从 row.buyerName）|
| 4 | `InputInvoiceImportService.java` | 写入 t_business_doc 时填 supplierName（从 row.sellerName）|

### 反查逻辑样板

```java
// BusinessDocServiceImpl.create()
if (entity.getCustomerId() != null) {
    CustomerVO cust = customerService.getById(entity.getCustomerId());
    entity.setCustomerName(cust != null ? cust.getName() : null);
}
if (entity.getSupplierId() != null) {
    VendorVO vendor = vendorService.getById(entity.getSupplierId());
    entity.setSupplierName(vendor != null ? vendor.getName() : null);
}
```

### 验证

- [ ] `mvn compile` 0 错（317-318 行能编译）
- [ ] `mvn test -Dtest=BusinessDocServiceImplTest` 4 个 fail 测试**仍 fail**（因为测试没设值，下批修）

---

## 第三批 P0-3 — 单测补齐

### 改动

| # | 测试方法 | 改动 |
|:---|:------|:------|
| 1 | `generateVoucher_非APPROVED状态_throwBadRequest` (line 467) | mock entity 时加 `setCustomerName(...)` + `setSupplierName(...)` |
| 2 | `generateVoucher_科目不存在_抛BusinessException_不标记VOUCHERED` (line 450) | 同上 |
| 3 | `generateVoucher_已生成凭证_不重复生成` (line 479) | 同上 |
| 4 | `generateVoucher_正常路径_APPROVED且科目齐备_成功生成` (line 509) | 同上 + verify |
| 5 | **新增** `create_带customerId_应同时填customerName` | 验证反查逻辑 |
| 6 | **新增** `create_带supplierId_应同时填supplierName` | 验证反查逻辑 |
| 7 | **新增** `generateVoucher_customerName_应进入模板上下文` | 验证 P26 集成 |

### 验证

- [ ] `mvn test -Dtest=BusinessDocServiceImplTest` 19+3=**22 个 @Test 全绿**
- [ ] `mvn test` 全量 **390/0/0** BUILD SUCCESS
- [ ] 端到端：`SalesInvoiceImportServiceTest` 等集成测试无回归

---

## 不做的事

- ❌ 不改 `TemplateContext` / `BusinessDocVO`（已有字段）
- ❌ 不补老 P0 缺口（AssetCard / OutputInvoiceStateMachine 等）
- ❌ 不开 P26 P2 批（独立工单）

---

## 执行方式

按你工作流："先 commit 验证通过 → push → 再下一步"。

P27 全部走 Hermes 直写（按 2026-06-22 §15 OpenCode 编程委托铁律破例降级路径触发条件）：
- ✅ 任务非视觉（纯代码 + 测试）
- ✅ 已有明确 SPEC 入库（P27）
- ✅ 老丁显式授权（"A" 选项）
- ✅ P26 P0-P3 6 批全部 Hermes 直写已稳态化

---

## Commit message 规范（§29）

- 首行 < 50 字符
- body 只描述 WHAT + WHY
- **不**加 TODO/⚠️/🚨（那些进任务书跟踪）
- 4 commit 串行 push