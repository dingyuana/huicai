# P41 SPEC — 发票驱动业财一体化（以票定账）

> **编号**：HUICAI-SPC-041
> **版本**：V2.0 | **修改日期**：2026-07-07
> **修改人**：Hermes | **修改内容**：V2.0 更新真实状态——3/5 任务已实现，新增映射表+批量合并+ai_mapping_result
> **依据**：发票驱动业财一体化专项设计
> **范围**：打通"外部发票数据"与"内部业财系统"的壁垒，以票定账
> **总工期**：剩余 2 人日（原 5 人日，3 人日已实现）

---

> **关联需求**: REQ-2026-029
## §0 当前状态（2026-07-07 审计）

| 任务 | 内容 | 原估工时 | 当前状态 | 说明 |
|------|------|---------|---------|------|
| T-2.1 | 发票导入增强 + 价税分离 + 防重 | 1.5人日 | ✅ **已完成** | `amount_ex_tax`字段, `InvoiceDedupUtil`, `ColumnMappingResolver` 均已实现 |
| T-2.2 | 发票→业务单据 自动生单 | 1.5人日 | ⚠️ **已有基础** | P34 confirm() 已创建 BusinessDoc(INVOICE_OUT, DRAFT) + Voucher(DRAFT)，需补充 `process_status` 回写和 `ar_ap_doc_id` |
| T-2.3 | AI 智能科目映射引擎 | 1人日 | ✅ **已完成** | P2-1 三阶段（规则→pgvector→LLM），`match.py` 已上线 |
| T-2.4 | 凭证生成前置校验 + 批量合并制单 | 0.5人日 | ⚠️ **部分完成** | 借贷校验已有，**批量合并制单未实现** |
| T-2.5 | 异常发票风控模型 | 0.5人日 | ✅ **已完成** | P2-2 四维度检测（品名背离/时间异常/金额波动/对方重复） |

### 新增需求（参考设计对比后采纳）

| 新增项 | 参考设计来源 | 说明 |
|--------|------------|------|
| `t_account_mapping_rule` 表 | 商品名→科目专用映射，替代分散在分类规则+凭证模板的方式 | 独立表，支持方向(INPUT/OUTPUT/BOTH)区分 |
| `ai_mapping_result` 字段 | 存储AI科目映射推荐结果（JSONB） | 追加到 OutputInvoiceEntity + InputInvoiceEntity |

### 实际剩余工作（2 人日）

| 子任务 | 工时 | 文件 |
|--------|------|------|
| **R-1** 创建 t_account_mapping_rule 表 + ai_mapping_result 字段 | 0.5d | V80 migration, Entity, Mapper |
| **R-2** 批量合并制单（发票→凭证） | 1.0d | VoucherController, VoucherServiceImpl, TaxService |
| **R-3** 更新 P41 SPEC + YAML 契约 | 0.5d | 本文档 |

---

## §1 现状摸底（详细）

### 1.1 已实现功能

| 模块 | 能力 | 文件 |
|------|------|------|
| 发票导入 | 销项/进项 Excel 导入+价税分离+防重 | `SalesInvoiceImportService.java`, `InputInvoiceImportService.java`, `InvoiceDedupUtil.java` |
| 发票模型 | 销项/进项 Entity + 状态机 + `process_status` | `OutputInvoiceEntity`, `InputInvoiceEntity`, `OutputInvoiceStateMachineServiceImpl` |
| 业务单据 | BusinessDoc 统一替代应收/应付（P34） | `BusinessDocEntity`, `BusinessDocServiceImpl` |
| 凭证模块 | 完整 CRUD + 6态状态机 + 模板 | `VoucherEntity`, `VoucherEntryEntity`, `VoucherServiceImpl` |
| AI 科目映射 | 规则→pgvector→LLM 三阶段 | `ai-service/app/api/match.py`, `subject_mapping_agent` |
| AI 异常检测 | 4维度（品名背离/时间/金额/对方） | `ai-service/app/api/anomaly.py`, `AnomalyAgent` |
| 科目余额校验 | 凭证借贷平衡校验 | `VoucherServiceImpl` 已有 |
| 编号关联溯源 | 全链路双向追溯 | `NumberingTraceService` |

### 1.2 缺失能力（本次开发）

| 缺失项 | 说明 | 文件名 |
|--------|------|--------|
| `t_account_mapping_rule` 表 | 发票品名→会计科目的专用映射规则表，支持方向区分 | 新增 V80 migration |
| `ai_mapping_result` 字段 | AI 科目映射结果存储（JSONB）到发票表 | OutputInvoiceEntity + InputInvoiceEntity |
| 批量合并制单 | 多张同客户发票→合并生成一张凭证 | VoucherController + VoucherServiceImpl |

---

## §2 执行计划

```
R-1 映射表+字段 (0.5d)
  └─ V80 migration: t_account_mapping_rule + ai_mapping_result
  └─ Entity/Mapper: AccountMappingRuleEntity + 发票表字段补齐
  └─ 种子数据: 常用商品→科目映射规则

R-2 批量合并制单 (1.0d)
  ├─ TaxController: POST /api/v1/tax/invoice/batch/generate-voucher
  ├─ TaxServiceImpl: batchGenerateVoucher(invoiceIds)
  │    ├─ 校验: 同一客户、同一方向
  │    ├─ 价税分离汇总
  │    └─ 合并分录: 多条借方+多条贷方
  └─ 前端: 发票列表增加"批量生成凭证"按钮

R-3 更新 P41 SPEC + YAML 契约
  └─ 状态改为 V2.0
  └─ YAML 契约块追加
```

---

## §3 数据库变更

```sql
-- 科目映射规则表（新增）
CREATE TABLE IF NOT EXISTS t_account_mapping_rule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_keyword VARCHAR(255) NOT NULL,             -- 商品/费用关键字
    account_code VARCHAR(32) NOT NULL,              -- 目标会计科目编码
    account_name VARCHAR(128),                     -- 科目名称（冗余）
    direction VARCHAR(10) DEFAULT 'BOTH',          -- INPUT/OUTPUT/BOTH
    aux_dimension JSONB DEFAULT '{}',              -- 辅助核算维度
    priority INT DEFAULT 0,                        -- 匹配优先级
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_account_mapping_rule IS '发票品名→会计科目映射规则表';
COMMENT ON COLUMN t_account_mapping_rule.item_keyword IS '商品/费用名称关键字（支持LIKE匹配）';
COMMENT ON COLUMN t_account_mapping_rule.account_code IS '目标会计科目编码';

-- 发票表补充 ai_mapping_result
ALTER TABLE t_output_invoice ADD COLUMN ai_mapping_result JSONB DEFAULT '{}';
ALTER TABLE t_input_invoice ADD COLUMN ai_mapping_result JSONB DEFAULT '{}';
```

---

## §4 API 契约

### 4.1 批量合并制单

```
POST /api/v1/tax/invoice/batch/generate-voucher
Request: {
  "invoice_ids": [1, 2, 3, 4, 5],
  "same_customer": true    // 同一客户多张发票合并
}
Response: {
  "voucher_id": 67890,
  "voucher_no": "PZ-202607-00001",
  "invoice_count": 5,
  "total_debit": 50000.00,
  "total_credit": 50000.00,
  "entry_count": 3,
  "status": "DRAFT"
}
```

### 4.2 科目映射规则 CRUD

```
GET/POST/PUT/DELETE /api/v1/tax/account-mapping-rules/**
```

---

## §5 YAML 契约

```yaml
contract_version: "2.0"
tasks:
  - id: R-1
    name: 创建科目映射规则表 + 字段补充
    effort: "0.5 人日"
    acceptance:
      - id: AT-R1-1
        description: "t_account_mapping_rule 表存在且有 CHECK 约束"
        assertion: "SELECT EXISTS(...) → true"
      - id: AT-R1-2
        description: "OutputInvoiceEntity 有 ai_mapping_result 字段"
        assertion: "entity.ai_mapping_result != null"
      - id: AT-R1-3
        description: "种子数据可查询"
        assertion: "SELECT count(*) FROM t_account_mapping_rule → > 0"
  - id: R-2
    name: 批量合并制单
    effort: "1.0 人日"
    acceptance:
      - id: AT-R2-1
        description: "5张同客户发票合并生成1张凭证"
        assertion: "5 invoices → 1 voucher, entry_count > 1"
      - id: AT-R2-2
        description: "借贷平衡"
        assertion: "total_debit == total_credit"
      - id: AT-R2-3
        description: "不同客户发票批量生成失败"
        assertion: "mixed customer_ids → BusinessException"
```