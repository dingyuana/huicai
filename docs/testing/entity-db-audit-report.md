# 完整事故分析与总结报告

> **日期**：2026-07-07 | **分析人**：Hermes

---

## 1. 发现的全部错误

本次排查共发现 **4 类 8 处** Entity-DB 不匹配错误

### 类型 A：字段存在但 DB 无列（缺 @TableField(exist = false)）

| # | Entity | 字段 | DB 表 | 触发场景 | 修复 |
|---|--------|------|-------|---------|------|
| 1 | OutputInvoiceEntity | auditedBy, auditedAt | t_output_invoice (无) | 任何 MyBatis SELECT（pageQuery、getDetail 等）| 补 `@TableField(exist = false)` |
| 2 | InputInvoiceEntity | auditedBy, auditedAt | t_input_invoice (无) | 同类型查询 | ✅ 已有 `exist=false`（未触发） |

### 类型 B：String 映射 JSONB（缺 typeHandler = JsonbTypeHandler.class）

| # | Entity | 字段 | DB 类型 | 触发场景 | 修复 |
|---|--------|------|---------|---------|------|
| 3 | OutputInvoiceEntity | aiMappingResult | JSONB | UPDATE（提交审核/审批/作废） | ✅ 已修 |
| 4 | InputInvoiceEntity | aiMappingResult | JSONB | UPDATE（确认/退回等） | ✅ 已修 |
| 5 | AccountMappingRuleEntity | auxDimension | JSONB | UPDATE（规则更新） | ✅ 已修 |
| 6 | BusinessDocEntity | ocrData | JSONB | UPDATE（业务单更新） | ✅ 已修 |
| 7 | VoucherEntryEntity | assistJson | JSONB | UPDATE（分录更新） | ✅ 已修 |

### 类型 C：业务逻辑缺失

| # | Entity/Service | 问题 | 影响 | 修复 |
|---|---------------|------|------|------|
| 8 | BusinessDocServiceImpl.create() | 未设 unsettledAmount | 新单据 unsettled=0，工作台不显示 | ✅ `unsettledAmount = amount` |
| 9 | SalesInvoiceImportService | 未设 unsettledAmount | 同上（导入路径） | ✅ `unsettledAmount = totalAmount` |

### 类型 D：测试覆盖空白

| # | 空白 | 影响 |
|---|------|------|
| 10 | pageQuery() 零测试 | 未捕获 #1-7 全部 SQL 错误 |
| 11 | 前端 ReconciliationWorkbench 零组件测试 | 未捕获 frontend total 不匹配 |
| 12 | E2E 核销链路缺失 | 未验证 INVOICE_OUT→工作台可见性 |

---

## 2. Root Cause 统一分析

所有这些错误的共同根因：

1. **Entity 变更不验证 DB**：开发者在 Entity 中添加字段时，不检查 DB schema（migration SQL）
2. **Migration 注释不可信**：注释声称的"V63 已添加列"从未发生在 `t_output_invoice` 上
3. **同类字段不同步更新**：`InputInvoiceEntity` 正确标注了 `exist=false`，但 `OutputInvoiceEntity` 没同步
4. **String vs JSONB 类型映射无标准化**: 项目已有 `JsonbTypeHandler`（`AuditLogEntity` 在用），但新增 JSONB 字段时没人参考
5. **测试不覆盖全字段**：Mock 测试无法验证 SQL 编译错误

---

## 3. 修复清单（已全部修复 + 部署）

| Commit | 内容 |
|--------|------|
| 2c180d8 | auditedBy/auditedAt exist=false + unsettledAmount 逻辑 |
| fc7faa5 | 5 个 Entity JSONB typeHandler |

---

## 4. 同类问题排查结论

- ✅ 15 个 JSONB 列全部排查完毕
- ✅ 57 个 Entity 全部扫描过 `exist=false` 和 typeHandler 情况
- ✅ 仅有上述 5 个 Entity 存在 String→JSONB 缺失 typeHandler（已全部修复）
- ✅ 仅有 OutputInvoiceEntity 存在字段缺失 `exist=false`（已修复）
- 其他 JSONB 字段（t_audit_log 4 字段）已正确使用 typeHandler
- `VoucherEntity.auditedBy/auditedAt` 对应的 DB 列存在，✅ 正确

---

## 5. 防错机制输出

| 文档 | 说明 |
|------|------|
| docs/testing/test-coverage-matrix.md | 测试覆盖矩阵（标识空白区域）|
| docs/testing/test-prevention-mechanism.md | 防错机制（影响面清单+测试门禁）|
| docs/incidents/sales-invoice-audited-by-column-error.md | 事故报告 #1 |
| AGENTS.md §4.2 | 新增 3 条 Entity-DB 陷阱经验 |

**核心防错流程**（已嵌入 AGENTS.md 三步闭环）：
```
Entity 字段新增 → 同时检查：DB 有列？→ 有则 @TableField(value=)
                                   无则 @TableField(exist = false)
                                   是 JSONB → 加 typeHandler = JsonbTypeHandler.class
```