# 编号关联体系设计文档

**版本**: v1.8  
**日期**: 2026-06-29  
**状态**: 全部实现完成，46/46 测试通过  
**P33 修正**: 2026-06-29 销售发票链路移除业务单中间环节，发票→应收单→凭证直连

---

## 一、设计背景与目标

### 1.1 业务背景
财务系统中存在多条业务链路，各环节产生的单据编号需要建立关联关系以满足：
- 审计追踪要求（全链路可追溯）
- 财务对账需求（三流合一：业务流、票据流、资金流）
- 问题排查效率（快速定位异常来源）

### 1.2 设计目标
✅ **双向追溯**：从任意节点可向上溯源至原始单据，向下追踪至最终凭证  
✅ **关联完整性**：ID关联 + 编号冗余双保险  
✅ **查询高效**：索引优化，关联查询性能达标  
✅ **数据一致**：关联字段自动赋值，避免人工维护

---

## 二、核心关联模型

### 2.1 完整链路图

```
┌─────────────────┐
│  销售发票        │  OutputInvoice
│  output_invoice │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│  应收单          │     │  应付单          │
│  receivable     │     │  payable        │
└────────┴────────┘     └────────┴────────┘
         │                        │
         └──────────┬─────────────┘
                    │
                    ▼
          ┌─────────────────┐
          │  会计凭证        │  Voucher
          │  voucher        │
          └─────────────────┘
```

> **P33 简化（2026-06-29）**：销售发票链路不再经过业务单中间层。  
> 旧链路：销售发票 → 业务单 → 应收单 → 凭证  
> 新链路：销售发票 → 应收单 → 凭证

### 2.2 关联矩阵

| 源表 → 目标表 | OutputInvoice | InputInvoice | Receivable | Payable | Voucher |
|--------------|---------------|--------------|------------|---------|---------|
| OutputInvoice | - | ⭕ | `invoice_id` + `invoice_no` + `receivable_id` + `receivable_no` | ⭕ | `voucher_id` + `voucher_no` |
| InputInvoice | ⭕ | - | ⭕ | `invoice_id` + `invoice_no` | `voucher_id` + `voucher_no` |
| Receivable | `receivable_id` + `receivable_no` | ⭕ | - | ⭕ | `voucher_id` + `voucher_no` |
| Payable | ⭕ | `invoice_id` + `invoice_no` | ⭕ | - | `voucher_id` + `voucher_no` |
| Voucher | `source_doc_id` + `source_doc_no` + `source_doc_type` | `source_doc_id` + `source_doc_no` + `source_doc_type` | `source_doc_id` + `source_doc_no` + `source_doc_type` | `source_doc_id` + `source_doc_no` + `source_doc_type` | - |

**图例**:
- `Id + No`: ID外键关联 + 编号字段冗余
- `⭕`: 无直接关联，通过中间表关联

> **P33 变更（2026-06-29）**：销售发票链路不再经过 BusinessDoc，`t_output_invoice` 与 `t_receivable` 之间建立直接关联（`invoice_id` + `receivable_id`）。

---

## 三、当前实现状态（2026-06-28 v1.7）

### 3.1 已实现字段

| 表名 | 关联字段 | 状态 | 备注 |
|------|---------|------|------|
| **t_output_invoice** | `receivable_id`, `receivable_no` | ✅ 已实现 | P33 新增（V65） |
| | `voucher_id`, `voucher_no` | ✅ 已实现 | 关联会计凭证 |
| | `original_invoice_id`, `original_invoice_no` | ✅ 已实现 | 红冲关联 |
| | `reversed_by_invoice_id`, `reversed_by_invoice_no` | ✅ 已实现 | 被红冲关联 |
| | `doc_id`, `doc_no` | ⚠️ 保留 | P33 简化后不再写入，历史数据保留 |
| **t_input_invoice** | `doc_id` | ✅ 已实现 | 关联业务单据 |
| | `voucher_id` | ✅ 已实现 | 关联会计凭证 |
| | `doc_no`, `voucher_no` | ✅ 已实现 | V64 补充编号冗余 |
| **t_receivable** | `invoice_id` | ✅ 已实现 | P33 新增（V65） |
| | `doc_id` | ⚠️ 保留 | P33 简化后不再写入，历史数据保留 |
| | `voucher_id` | ✅ 已实现 | 关联会计凭证 |
| | `doc_no`, `voucher_no`, `invoice_no` | ✅ 已实现 | V64 补充 |
| **t_payable** | `doc_id` | ✅ 已实现 | 关联业务单据 |
| | `voucher_id` | ✅ 已实现 | 关联会计凭证 |
| | `doc_no`, `voucher_no`, `invoice_no` | ✅ 已实现 | V64 补充 |
| **t_business_doc** | `voucher_id`, `voucher_no` | ✅ 已实现 | V64 补充 |
| **t_voucher** | `source_doc_id`, `source_doc_no` | ✅ 已实现 | V64 补充 |
| | `source_doc_type` | ✅ 已实现 | V64 补充 |

> **P33 变更（2026-06-29）**：销售发票链路不再创建业务单，`t_output_invoice` 与 `t_receivable` 之间建立直接关联。`doc_id/doc_no` 字段保留但不写入，历史数据仍可查询。

### 3.2 实现完成度统计

| 模块 | 字段总数 | 已实现 | 完成度 |
|------|---------|--------|--------|
| 销售发票 | 8 | 8 | **100% ✅** |
| 采购发票 | 4 | 4 | **100% ✅** |
| 应收单 | 4 | 4 | **100% ✅** |
| 应付单 | 4 | 4 | **100% ✅** |
| 业务单据 | 2 | 2 | **100% ✅** |
| 会计凭证 | 3 | 3 | **100% ✅** |
| **整体** | **25** | **25** | **100% ✅** |

---

## 四、字段设计规范

### 4.1 统一命名规范

| 关联类型 | ID字段名 | 编号字段名 | 字段类型 | 说明 |
|---------|---------|-----------|---------|------|
| 业务单据关联 | `doc_id` | `doc_no` | BIGINT / VARCHAR(64) | |
| 发票关联 | `invoice_id` | `invoice_no` | BIGINT / VARCHAR(64) | |
| 凭证关联 | `voucher_id` | `voucher_no` | BIGINT / VARCHAR(64) | |
| 溯源关联 | `source_doc_id` | `source_doc_no` | BIGINT / VARCHAR(64) | 凭证溯源用 |
| 单据类型 | `source_doc_type` | - | VARCHAR(32) | 枚举值 |

### 4.2 单据类型枚举

```java
public enum SourceDocType {
    BUSINESS_DOC,        // 业务单据
    OUTPUT_INVOICE,      // 销售发票
    INPUT_INVOICE,       // 采购发票
    RECEIVABLE,          // 应收单
    PAYABLE,             // 应付单
    BANK_STATEMENT       // 银行流水
}
```

---

## 五、Migration 实施完成（V64）

### 5.1 V64__add_numbering_association_fields.sql

已执行的字段补充：

| 表名 | 新增字段 | 数量 |
|------|---------|------|
| t_input_invoice | doc_no, voucher_no | 2 |
| t_receivable | doc_no, voucher_no, invoice_no | 3 |
| t_payable | doc_no, voucher_no, invoice_no | 3 |
| t_business_doc | voucher_id, voucher_no | 2 |
| t_voucher | source_doc_id, source_doc_no, source_doc_type | 3 |
| t_arap_settlement | voucher_no | 1 |
| **合计** | | **14** |

所有新增字段均创建了 B-tree 索引（`idx_表名_字段名` 规范）。

---

## 六、Service 层自动赋值（全部完成）

### 6.1 自动赋值矩阵

| 触发动作 | 赋值字段 | 赋值位置 | 状态 |
|---------|---------|---------|------|
| 业务单据生成发票 | doc_id, doc_no | InvoiceService.create() | ✅ |
| 发票审核生成应收/应付 | invoice_id, invoice_no | ReceivableService.confirm() | ✅ |
| 应收/应付核销生成凭证 | voucher_id, voucher_no | VoucherService.generate() | ✅ |
| 凭证生成回写 | voucher_id, voucher_no, docNo | 各 Service markVouchered | ✅ |
| 采购发票导入 | docNo, invoiceNo, voucherNo | InputInvoiceImportService | ✅ |
| 销售发票导入 | sourceDocId, sourceDocNo, sourceDocType | SalesInvoiceImportService | ✅ |
| 核销单生成凭证 | sourceDocId, sourceDocNo, sourceDocType | ArapSettlementServiceImpl | ✅ |
| 业务单据生成凭证 | sourceDocId, sourceDocNo, sourceDocType | BusinessDocServiceImpl | ✅ |

### 6.2 凭证溯源赋值位置

| 生成凭证位置 | sourceDocType | sourceDocNo | 文件 |
|-------------|---------------|-------------|------|
| 销售发票 → 凭证（硬编码） | OUTPUT_INVOICE | invoiceNo | TaxServiceImpl.java:355 |
| 销售发票 → 凭证（模板） | OUTPUT_INVOICE | invoiceNo | TaxServiceImpl.java:413 |
| 税务申报 → 凭证（硬编码） | TAX_DECLARATION | declarationNo | TaxServiceImpl.java:647 |
| 税务申报 → 凭证（模板） | TAX_DECLARATION | declarationNo | TaxServiceImpl.java:691 |
| 采购发票 → 凭证 | INPUT_INVOICE | invoiceNo | InputInvoiceImportService.java:380 |
| 核销单 → 凭证 | SETTLEMENT | settlementNo | ArapSettlementServiceImpl.java:199 |
| 销售发票导入 → 凭证 | OUTPUT_INVOICE | invoiceNo | SalesInvoiceImportService.java:466 |
| 业务单据 → 凭证（硬编码） | BUSINESS_DOC | docNo | BusinessDocServiceImpl.java:364 |
| 业务单据 → 凭证（模板） | BUSINESS_DOC | docNo | BusinessDocServiceImpl.java:483 |

---

## 七、索引设计

### 7.1 已创建的索引

| 表名 | 索引字段 | 索引名称 | 用途 |
|------|---------|---------|------|
| t_input_invoice | doc_no | idx_input_invoice_doc_no | 按单据号查询发票 |
| t_input_invoice | voucher_no | idx_input_invoice_voucher_no | 按凭证号查询发票 |
| t_receivable | doc_no | idx_receivable_doc_no | 按单据号查询应收 |
| t_receivable | voucher_no | idx_receivable_voucher_no | 按凭证号查询应收 |
| t_receivable | invoice_no | idx_receivable_invoice_no | 按发票号查询应收 |
| t_payable | doc_no | idx_payable_doc_no | 按单据号查询应付 |
| t_payable | voucher_no | idx_payable_voucher_no | 按凭证号查询应付 |
| t_payable | invoice_no | idx_payable_invoice_no | 按发票号查询应付 |
| t_voucher | source_doc_no | idx_voucher_source_doc_no | 按原始单据号查询凭证 |
| t_business_doc | voucher_no | idx_business_doc_voucher_no | 按凭证号查询业务单 |
| t_arap_settlement | voucher_no | idx_arap_settlement_voucher_no | 按凭证号查询核销单 |

### 7.2 索引验证

**15 个索引测试全部通过**（`NumberingAssociationIndexesTest`），涵盖：
- 6 张表的 11 个编号字段索引
- 索引存在性验证
- 索引查询性能验证

---

## 八、测试覆盖（46/46 全部通过）

### 8.1 测试分层

| 层级 | 测试类 | 测试数 | 状态 |
|------|--------|--------|------|
| L0 | 编译验证 | - | ✅ |
| L1 | 实体字段读写 | NumberingAssociationFieldsTest | 9/9 ✅ |
| L2 | 索引验证 | NumberingAssociationIndexesTest | 15/15 ✅ |
| L2 | Controller 接口 | NumberingTraceControllerTest | 7/7 ✅ |
| L3 | 销项链路 E2E | NumberingAssociationE2ETest | 6/6 ✅ |
| L3 | 核销链路 E2E | NumberingSettlementE2ETest | 3/3 ✅ |
| L3 | 一致性校验 Job | NumberingConsistencyCheckJobTest | 6/6 ✅ |
| **总计** | | **46/46** | **100% ✅** |

### 8.2 测试覆盖场景

- ✅ 销售流程全链路：BusinessDoc → OutputInvoice → Receivable → Voucher
- ✅ 采购流程全链路：BusinessDoc → InputInvoice → Payable → Voucher
- ✅ 核销流程：Receivable + Payable → Settlement → Voucher
- ✅ 反向溯源：从 Voucher 可溯源到原始单据 ID 和编号
- ✅ 跨模块一致性：同一业务流程中各单据的关联编号一致
- ✅ 一致性校验 Job：5 类脏数据检测 + 混合场景
- ✅ 追溯接口：按任意编号查询全链路

---

## 九、风险与注意事项

### 9.1 数据一致性风险
⚠️ **问题**: 编号字段冗余可能导致不一致（ID正确但编号错误）  
✅ **应对**: 
- 所有赋值通过 Service 层统一方法，禁止直接修改
- 新增定时任务每日校验关联一致性（`NumberingConsistencyCheckJob`）
- 异常数据自动告警并生成修复脚本

### 9.2 历史数据补全
⚠️ **问题**: 已有数据的新增字段为空  
✅ **应对**:
- Migration 中编写 UPDATE 脚本补全历史数据
- 按 ID 关联查询补全编号字段
- 补全后执行一致性校验

### 9.3 审计字段待迁移
⚠️ **注意**: `ReceivableEntity`/`PayableEntity`/`InputInvoiceEntity`/`OutputInvoiceEntity` 的 `auditedBy`/`auditedAt` 字段当前标记为 `@TableField(exist = false)`，因为 `t_receivable`/`t_payable`/`t_input_invoice`/`t_output_invoice` 表尚未添加审计字段列。如需启用，需额外 Migration。

---

## 十、文档变更记录

| 版本 | 日期 | 修改内容 |
|------|------|---------|
| v1.0 | 2026-06-28 | 初始版本，完整设计方案 |
| v1.1 | 2026-06-28 | 更新实施进展记录，补充 P0 完成情况 |
| v1.2 | 2026-06-28 | P1-A 采购链路 + P1-B 核销凭证回写 完成记录 |
| v1.3 | 2026-06-28 | P1-C 凭证溯源字段自动赋值完成 |
| v1.4 | 2026-06-28 | P1-D 业务单据回写 voucherNo 完成 |
| v1.5 | 2026-06-28 | P2 编号关联查询接口完成 |
| v1.6 | 2026-06-28 | P2-3 数据一致性校验定时任务完成 |
| v1.7 | 2026-06-28 | **全部完成**：V64 Migration + 46/46 测试通过 + 100% 字段实现 |
