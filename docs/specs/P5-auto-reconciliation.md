# SPEC: Phase 5 — 智能辅助核销

> 版本：v1.0 | 日期：2026-06-14 | 状态：草稿待审
> 上游：`docs/需求分析/03-自动核销.md`、`docs/需求分析/00-总览与架构.md`
> 关联：FR-BANK-05/06（银行流水出纳确认→生单）、FR-BANK-03（智能分类）

---

## 一、现状与问题

### 1.1 已实现的手动核销骨架

核销模块目前具备基础 CRUD 能力，但**无任何智能推荐能力**：

| 层 | 已存在 | 说明 |
|---|--------|------|
| 数据表 | `t_arap_settlement` / `t_arap_settlement_entry` | 核销单主表+明细表 |
| 实体 | `ReceivableEntity` / `PayableEntity` | 应收/应付账款表 |
| 实体 | `CustomerEntity` / `VendorEntity` | 客户/供应商档案 |
| 后端 | `ArapSettlementController` | 核销单 CRUD + 确认（更新应收应付已核销金额） |
| 后端 | `ReceivableController` / `PayableController` | 应收/应付分页查询 |
| 后端 | `CustomerController` / `VendorController` | 客户/供应商 CRUD |
| 前端 | `SettlementList.vue` | 核销列表 + 新增草稿 + 确认/删除 |
| 前端 | `ReceivableList.vue` / `PayableList.vue` | 应收/应付列表 |
| 前端 | `CustomerList.vue` / `VendorList.vue` | 客户/供应商管理 |

### 1.2 缺失的自动核销能力

依据 `docs/需求分析/03-自动核销.md`，以下模块完全未实现：

| 需求 | 说明 | 优先级 |
|------|------|--------|
| FR-RECON-01 | 收款核销推荐引擎 | P0 |
| FR-RECON-02 | 付款核销推荐引擎 | P0 |
| FR-RECON-03 | 银行流水→自动匹配发票（business_receipt/payment 联动） | P1 |
| FR-RECON-04 | 5种匹配策略（精确/容差/部分/多对一/一对多/溢收款/尾差） | P0 |
| FR-RECON-05 | 核销工作台前端（推荐展示+确认+批量） | P0 |
| FR-RECON-06 | 核销记录追溯（`t_reconciliation_log` 表 + 审计） | P1 |
| DB | `t_reconciliation_log` Flyway 迁移 | P1 |
| API | `/api/v1/reconciliation/*` 6个端点 | P0 |

---

## 二、功能规格

### FR-RECON-01: 收款核销推荐

**触发时机**：用户进入核销工作台，选择一笔待核销的收款单或银行流水。

**输入**：`收款单ID / 银行流水ID / 客户ID`

**流程**：

```
1. 确定对方方（客户ID）→ 查询该客户所有已确认/部分核销的销售发票
2. 执行匹配度评分算法:
   匹配度 = 金额匹配(0.4) + 摘要相似度(0.4) + 对方名匹配(0.2)
3. 筛选匹配度 ≥ 0.7 的候选发票
4. 按匹配度降序返回推荐列表
```

**匹配度评分算法**：

```
金额匹配(0.4):
  ├─ 收款金额 == 发票未核销余额 → +0.4
  ├─ |收款金额 - 未核销余额| ≤ 容差阈值(5元) → +0.3
  └─ 否则 → +0.0

摘要相似度(0.4):
  └─ Jaccard(收款单摘要分词, 发票摘要分词) × 0.4
  └─ 中文二元分词（2-gram），英文按单词切分

对方名匹配(0.2):
  ├─ 收款单对方户名 == 发票客户名称 → +0.2
  └─ 模糊匹配（Levenshtein ≥ 80%）→ +0.1
  └─ 否则 → +0.0
```

**匹配度分级**：

| 匹配度 | 颜色 | 操作建议 |
|:-----:|:----:|:--------|
| ≥ 0.95 | 🟢 绿色 | 一键核销 |
| 0.7 - 0.94 | 🟡 黄色 | 核对后核销 |
| < 0.7 | ⚪ 灰色 | 不展示 |

### FR-RECON-02: 付款核销推荐

与 FR-RECON-01 对称，供应商替换客户，采购发票替换销售发票：

- 按供应商筛选未核销采购发票
- 金额匹配 + 摘要相似度 + 对方名匹配（算法同 FR-RECON-01）
- 匹配度 ≥ 0.7 的候选发票展示供会计确认

### FR-RECON-03: 银行流水自动匹配核销

**目标**：银行流水 `business_receipt` → 自动创建收款单 → 推荐匹配发票；`business_payment` → 自动创建付款单 → 推荐匹配发票。

**流程**（集成到现有出纳确认流程 `review()`）：

```
银行流水 → 出纳确认 review()
  ↓
若 classification == 'business_receipt':
  → 自动创建收款单（DRAFT, FROM_BANK_TXN）
  → 按 counter_account 匹配 Customer
  → 调用收款核销推荐（FR-RECON-01）
  → 如有匹配度 ≥ 0.95 的发票 → 自动执行核销
  → 否则 → 标记"待核销推荐"

若 classification == 'business_payment':
  → 自动创建付款单（DRAFT, FROM_BANK_TXN）
  → 按 counter_account 匹配 Vendor
  → 调用付款核销推荐（FR-RECON-02）
  → 匹配度 ≥ 0.95 → 自动核销
  → 否则 → 标记"待核销推荐"
```

### FR-RECON-04: 核销匹配策略

| 策略 | 说明 | 匹配条件 |
|------|------|---------|
| **精确匹配** | 金额完全相等，摘要含发票号 | 金额==未核销余额 AND 摘要contains发票号 |
| **容差匹配** | 金额差额在 ±5 元内（含手续费） | `|金额-未核销余额| ≤ 5` |
| **部分核销** | 本次只核销部分金额，剩余继续挂账 | 收款金额 < 未核销余额 |
| **多对一核销** | 多笔流水/收付款共同核销一笔发票 | 多笔金额之和 == 未核销余额（累加匹配） |
| **一对多核销** | 一笔流水核销多张发票 | 流水金额 == 多张发票未核销余额之和 |
| **溢收款/预收款** | 多出金额自动转为预收款 | 收款金额 > 未核销余额 |
| **尾差处理** | 小额尾差计入财务费用 | 差额 ≤ 0.50 元 |

### FR-RECON-05: 核销工作台

**位置**：前端新增页面 `/arap/reconciliation-workbench` 或扩展现有 `SettlementList.vue`。

**功能列表**：

| 需求 | 说明 |
|------|------|
| 05.1 | 统一工作台，展示所有待核销的收/付款单及推荐核销方案 |
| 05.2 | 每笔推荐展示：发票号/日期/原金额/未核销余额/匹配度评分 |
| 05.3 | 匹配度 ≥ 0.95 高亮绿色，可直接一键核销 |
| 05.4 | 支持勾选多笔高置信度推荐，批量执行核销 |
| 05.5 | 支持手工调整：修改核销金额、更换匹配发票、取消匹配 |
| 05.6 | 部分核销时显示：已核销金额 / 未核销金额 |
| 05.7 | 核销完成后自动更新发票状态为部分核销/已核销 |
| 05.8 | 核销操作记录审计日志 |

**界面示意**（纯文字描述）：

```
┌─────────────────────────────────────────────────┐
│ 核销工作台                                       │
│ ┌─────┬──────┬────────┬──────┬──────┬─────────┐ │
│ │ 方向 │ 金额 │ 对方名 │ 匹配 │ 操作 │ 推荐发票│ │
│ ├─────┼──────┼────────┼──────┼──────┼─────────┤ │
│ │ 收款 │10000 │ 客户A │ 0.98 │[核销]│ FP202606│ │
│ │      │      │        │  🟢  │      │ -0001   │ │
│ ├─────┼──────┼────────┼──────┼──────┼─────────┤ │
│ │ 付款 │ 5000 │ 供应商B│ 0.85 │[核对]│ FP202606│ │
│ │      │      │        │  🟡  │      │ -0002   │ │
│ └─────┴──────┴────────┴──────┴──────┴─────────┘ │
└─────────────────────────────────────────────────┘
```

### FR-RECON-06: 核销记录追溯

**数据表**：`t_reconciliation_log`（新建 Flyway 迁移）

| 字段 | 类型 | 说明 |
|:----|:----|:-----|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT NOT NULL | 租户 ID |
| source_doc_type | VARCHAR(32) | 来源单据类型（receipt/payment/bank_txn）|
| source_doc_id | BIGINT | 来源单据 ID |
| target_doc_type | VARCHAR(32) | 目标单据类型（INVOICE_OUT/INVOICE_IN）|
| target_doc_id | BIGINT | 目标发票 ID |
| allocated_amount | NUMERIC(18,2) | 核销金额 |
| discount_amount | NUMERIC(18,2) | 现金折扣金额 |
| match_score | NUMERIC(5,2) | 匹配度评分 |
| match_method | VARCHAR(20) | AUTO / MANUAL |
| status | VARCHAR(20) | PENDING / CONFIRMED / CANCELLED |
| remark | VARCHAR(500) | 备注 |
| created_by | BIGINT | 操作人 |
| created_at | TIMESTAMP | 创建时间 |

**前端**：发票详情页设「核销记录」选项卡，以时间轴展示所有核销操作。每条记录支持穿透至关联收/付款单号和银行流水。

---

## 三、接口设计

### 3.1 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/reconciliation/receipt/{id}/recommend` | 收款核销推荐 |
| POST | `/api/v1/reconciliation/payment/{id}/recommend` | 付款核销推荐 |
| POST | `/api/v1/reconciliation/auto-recommend/{statementId}` | 银行流水自动推荐（集成 FR-RECON-03） |
| POST | `/api/v1/reconciliation/execute` | 执行单笔核销 |
| POST | `/api/v1/reconciliation/batch-execute` | 批量核销 |
| GET | `/api/v1/reconciliation/{docType}/{docId}/records` | 核销记录查询 |
| POST | `/api/v1/reconciliation/{id}/reverse` | 反核销 |

### 3.2 请求/响应示例

**收款核销推荐**：

```
POST /api/v1/reconciliation/receipt/{receiptId}/recommend

Response:
{
  "receiptId": 1,
  "customerId": 1,
  "customerName": "客户A",
  "receiptAmount": 10000.00,
  "recommendations": [
    {
      "invoiceId": 1,
      "invoiceNo": "FP202606-0001",
      "invoiceDate": "2026-06-01",
      "originalAmount": 10000.00,
      "unsettledAmount": 10000.00,
      "matchScore": 0.98,
      "matchLevel": "GREEN",
      "suggestedAmount": 10000.00
    }
  ]
}
```

**执行核销**：

```
POST /api/v1/reconciliation/execute
Body:
{
  "sourceDocType": "bank_txn",
  "sourceDocId": 42,
  "allocations": [
    { "targetDocType": "INVOICE_OUT", "targetDocId": 1, "amount": 10000.00 }
  ]
}

Response:
{
  "settlementId": 10,
  "settlementNo": "JS-202606-ABCDEF",
  "status": "CONFIRMED",
  "allocatedAmount": 10000.00,
  "voucherId": 5
}
```

---

## 四、数据库变更

### 4.1 新建 `t_reconciliation_log`（Flyway V24）

```sql
CREATE TABLE t_reconciliation_log (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL DEFAULT 1,
    source_doc_type    VARCHAR(32) NOT NULL,    -- receipt/payment/bank_txn
    source_doc_id      BIGINT NOT NULL,
    target_doc_type    VARCHAR(32) NOT NULL,    -- INVOICE_OUT/INVOICE_IN
    target_doc_id      BIGINT NOT NULL,
    allocated_amount   NUMERIC(18,2) NOT NULL,
    discount_amount    NUMERIC(18,2) DEFAULT 0,
    match_score        NUMERIC(5,2),
    match_method       VARCHAR(20) DEFAULT 'MANUAL',  -- AUTO/MANUAL
    status             VARCHAR(20) DEFAULT 'CONFIRMED',
    remark             VARCHAR(500),
    created_by         BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_log_source ON t_reconciliation_log(source_doc_type, source_doc_id);
CREATE INDEX idx_recon_log_target ON t_reconciliation_log(target_doc_type, target_doc_id);
CREATE INDEX idx_recon_log_tenant  ON t_reconciliation_log(tenant_id);
```

### 4.2 不修改的表

- `t_arap_settlement` / `t_arap_settlement_entry` — 现有核销单结构不变
- `t_receivable` / `t_payable` — 现有应收应付表不变
- `t_bank_statement` — 不新增字段（`classification` 已有 `business_receipt/payment`）

---

## 五、验收标准

1. ✅ 收款核销推荐：选择收款单/银行流水 → 返回匹配度≥0.7的发票列表，含评分
2. ✅ 付款核销推荐：选择付款单/银行流水 → 返回匹配度≥0.7的发票列表
3. ✅ 一键核销：匹配度≥0.95 → 执行核销 → 更新发票已核销金额 → 写reconciliation_log
4. ✅ 批量核销：勾选多笔→批量执行→更新多张发票状态
5. ✅ 部分核销：核销金额 < 发票金额 → 发票状态变为部分核销
6. ✅ 银行流水联动：business_receipt 确认后 → 自动推荐核销（匹配度≥0.95自动执行）
7. ✅ 核销记录查询：按来源单据查询全部核销操作
8. ✅ t_reconciliation_log 表创建成功，索引正确
9. ✅ 反核销：回滚核销金额，恢复发票未核销余额，记录CANCELLED状态

---

## 六、实施计划

### Phase 5.1 — 匹配引擎 + t_reconciliation_log（P0，核心）

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | Flyway V24: 创建 t_reconciliation_log 表 | 10 min |
| 2 | 创建 ReconciliationService（匹配度评分算法 + 推荐逻辑） | 45 min |
| 3 | 实现 RECON-04 五种匹配策略（精确/容差/部分/多对一/一对多） | 30 min |
| 4 | 实现执行核销 + 反核销 API | 30 min |
| 5 | 单元测试（ReconciliationServiceTest） | 20 min |
| | **小计** | **~2h** |

### Phase 5.2 — 银行流水联动（P1）

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | 集成到 review() 流程：business_receipt/payment → auto-recommend | 20 min |
| 2 | 自动创建收款/付款单 + 调用推荐 | 20 min |
| 3 | E2E 验证：导入→分类→确认→自动推荐→核销 | 15 min |
| | **小计** | **~1h** |

### Phase 5.3 — 核销工作台前端（P0）

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | 前端工作台页面（待核销列表 + 推荐展示） | 40 min |
| 2 | 一键核销 + 批量核销交互 | 20 min |
| 3 | 核销记录追溯（发票详情页"核销记录"选项卡） | 20 min |
| | **小计** | **~1.5h** |

### 合计

| Phase | 内容 | 预估 |
|-------|------|------|
| 5.1 | 匹配引擎 + 核销 API | ~2h |
| 5.2 | 银行流水联动 | ~1h |
| 5.3 | 核销工作台前端 | ~1.5h |
| **总计** | | **~4.5h** |