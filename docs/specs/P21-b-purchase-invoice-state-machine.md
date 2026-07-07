# P21-b 采购发票状态机分析报告

> **编号**：HUICAI-SPC-021B
> 替代文档：本报告替代原"P21-b SPEC — 采购发票状态机实现规格书"（2026-06-22 标 [已废弃]）
> 结论：**现状方案够用，不实施新状态机**
> 依据：实测 schema + 业务代码 + PG 数据三重验证

---

> **关联需求**: REQ-2026-026
## 1. 背景

原 P21-b SPEC（2026-06-21 起草）假设：
- `t_input_invoice` 有 `status` 字段（类似销售发票的 7/8 态）
- 旧 4 状态 CHECK 约束需要扩展到 8 状态
- 需要 V40 migration + InputInvoiceStateMachineService + 单测

**2026-06-22 实施前置实测发现**：以上假设全部错误。

---

## 2. 现状实测（schema + 业务 + 数据）

### 2.1 Schema 现状

```sql
-- 实测 t_input_invoice 字段（psql \d t_input_invoice）
id, invoice_no, invoice_date, period, vendor_id, vendor_name,
amount, tax_rate, tax_amount, total_amount, invoice_type,
certification_status, certified_date, deduction_period, deduction_amount,
doc_id, voucher_id, remark, created_by, created_at, updated_at, deleted
-- ❌ 无 status 字段
```

**CHECK 约束**（V8 已建）：

| 约束名 | 字段 | 枚举值 |
|---|---|---|
| `chk_cert_status` | certification_status | UNCERTIFIED / CERTIFIED / INVALID / CANCELLED |
| `chk_invoice_type` | invoice_type | SPECIAL / PLAIN / CUSTOMS / TRANSPORT |

### 2.2 业务代码现状

| 文件:行 | 写法 | 含义 |
|---|---|---|
| `InputInvoiceImportService:404` | `setCertificationStatus("PENDING")` | **❌ 违反 V8 CHECK 约束**（"PENDING" 不在 4 态枚举）|
| `InputInvoiceImportService:370/406/421` | `setVoucherId(...)` | 已生成凭证的关联 |
| `TaxServiceImpl:124` | `setCertificationStatus("UNCERTIFIED")` | ✅ 合法 |
| `TaxServiceImpl:145` | `setCertificationStatus("CERTIFIED")` | ✅ 合法 |

### 2.3 数据现状

```sql
SELECT COUNT(*) FROM t_input_invoice;  -- 返回 0
```

**没有真实采购发票数据**——所以 `InputInvoiceImportService:404` 的 PENDING 写入**未爆**（没数据走这条路径）。

---

## 3. "已生成凭证"业务的现状实现

采购发票的"已生成凭证"通过 **`voucher_id` 字段** 表达（不为空 = 已关联凭证），不需要新 status 字段：

| 业务场景 | 现状实现 |
|---|---|
| 采购发票导入 | 创建 t_input_invoice + 自动生成 t_payable + t_voucher，`voucher_id` 写入 |
| 凭证查询 | 关联 `t_voucher.voucher_no` 通过 `voucher_id` JOIN |
| 红冲 | 通过 `t_voucher.reversed_from` 字段关联（已存在，V8 加的）|
| 核销 | 走 t_payable 的核销工作台（P12 已实现）|

**销售 vs 采购发票的字段差异**（这是设计差异，不是 bug）：

| 字段 | t_output_invoice（销售）| t_input_invoice（采购）|
|---|---|---|
| 业务状态字段 | `status` (8 态: P21-a V46) | `certification_status` (4 态: 认证) |
| "已生成凭证"标识 | `status=VOUCHERED` 或 `voucher_id` 非空 | `voucher_id` 非空 |
| 业务含义 | 销售方开票给客户，状态 = 客户/财务确认流程 | 采购方收票，状态 = 进项税抵扣认证流程 |

---

## 4. 结论

**采购发票不需要独立的 8 态状态机**：

1. `certification_status` (4 态) 已覆盖"进项税抵扣认证"业务
2. `voucher_id` 字段已覆盖"已生成凭证"业务
3. `reversed_from` / `t_voucher.reversed_from` 已覆盖"红冲"业务
4. `t_payable` 核销工作台（P12）已覆盖"应付核销"业务

**如果未来业务扩展**（如 "采购发票审批流"），再独立开 P21-c 工单：
1. 先实测当前业务需求
2. 再决定加字段（voucherStatus？）或扩展 certification_status
3. 起草 SPEC 时必须用 R5 铁律查 schema 现状

---

## 5. 实施清单

| # | 改动 | 文件 | 风险 | commit |
|---|---|---|---|---|
| 1 | 修 P0 bug：`InputInvoiceImportService:404` `PENDING` → `UNCERTIFIED` | `InputInvoiceImportService.java` | 🟡 中（修业务代码）| (下个 commit) |
| 2 | 改造本 SPEC（"已废弃" → "分析报告"）| `docs/specs/P21-b-purchase-invoice-state-machine.md` | ✅ 低 | (本 commit) |

---

## 6. 后续工单

| 编号 | 名称 | 优先级 | 前置条件 |
|---|---|:---:|---|
| P21-c | 采购发票状态机 V2（仅当业务需要时）| P1 | 业务需求文档化 + R5 4 步实测 |
| P25-b | `InputInvoiceImportService` 全量测试（覆盖 P0 bug 修复）| P0 | 修 P0 bug 后 |

---

## Changelog

- 2026-06-22 重构 by Hermes：原 P21-b SPEC 整个假设错（t_input_invoice 无 status 字段），改写为"分析报告"
- 2026-06-22 标 [已废弃] by Hermes：实施前置实测发现 SPEC 错位
- 2026-06-21 创建原 P21-b SPEC（已废弃）
