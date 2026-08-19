# P43 SPEC — 坏账准备计提模块

> **编号**：HUICAI-SPC-043
> **test_ref**：BadDebtProvisionStateMachineServiceImplTest
> **版本**：V1.0 | **日期**：2026-07-11
> **状态**：📝 草案（待审核）
> **关联需求**：REQ-2026-015（坏账计提）
> **关联文档**：[项目说明](../CORE-项目说明.md), [技术方案](../CORE-技术方案.md), [需求分析](../CORE-需求分析.md), [02-arap-design.md](../design/02-应收应付管理.md), [P34-receivable-payable-to-businessdoc.md](P34-receivable-payable-to-businessdoc.md)

## 0. SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 三、详细设计 — 数据库 Migration / API 端点 / 科目种子数据](#三详细设计)

### 2. 输出契约
→ 见本文 [## 一、需求概述 验收标准项 / ## 四 YAML 契约](#一需求概述)

### 3. 状态流转
→ 见本文 [## 3.9 状态机 — DRAFT→VOUCHERED 状态转换](#39-状态机)

### 4. 异常处理
→ 见本文各 BusinessException 抛出点（如凭证生成失败、科目余额查询异常）

> **版本历史**：
> - V1.0 (2026-07-11): 初始版本

---

## 一、需求概述

### 业务背景

企业应对应收账款、预付款项、其他应收款、应收票据等应收款项计提坏账准备。根据我国会计准则（《企业会计准则第22号》），应采用**备抵法**，先预估损失后设置备抵科目，实际发生坏账时再冲销。

**关键决策（老丁 2026-07-11 明确）**：
- 覆盖类型：应收账款、预付款项、其他应收款、应收票据 **全部覆盖**
- 计提方法：**账龄分析法**（最常用，作为一期实现方法）
- 计提方案：**系统预制，允许用户调整** — 在计提弹窗中直接修改比例，无需独立管理页面
- **计提时间：年底一次性计提**
- **确认流程：confirm 时自动生成凭证**（不再分两步）

### 数据源说明

系统当前已有以下数据载体：

| 应收类型 | 数据表 | 匹配条件 | 状态 |
|---------|--------|---------|------|
| 应收账款（销项发票） | `t_business_doc` | `doc_type='INVOICE_OUT'` | ✅ 已有 |
| 其他应收款 | `t_business_doc` | `doc_type='OTHER_RECEIVABLE'` | ✅ 已有 |
| 预付账款 | `t_prepayment` | `unsettled_amount > 0` | ✅ 已有 |
| 应收票据 | `t_business_doc` | 需新增 `doc_type='NOTE_RECEIVABLE'` | ⚠️ 本期新增 CHECK 约束 |

> **说明**：`OTHER_RECEIVABLE` 已在 V5 migration 的 `chk_doc_type` CHECK 约束中。`NOTE_RECEIVABLE` 尚不存在，本期需加 V81 migration 扩展 CHECK 约束 + 凭证模板。

### 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | 账龄分析引擎能按配置区间自动划分未清应收，计算各区间应计提金额 | 单元测试 + 手工验证 |
| 2 | 计提结果能正确对比"坏账准备"科目当前余额，计算补提/冲回额 | 单元测试 |
| 3 | 确认后可自动生成凭证（DRAFT），分录方向正确 | E2E 测试 |
| 4 | 计提方案支持增删改查，修改即时生效 | MockMvc 测试 |
| 5 | 前端可查看账龄分析预览、计提明细、执行计提 | 手工验证 |

---

## 二、当前状态 vs 目标状态

### 已存在（无需重建）

| 组件 | 状态 | 说明 |
|------|------|------|
| `t_bad_debt_provision` 表 | ✅ | V7 已有，含 period/method/total_amount/voucher_id/status |
| `BadDebtProvisionEntity` | ✅ | Entity 映射已存在 |
| `BadDebtProvisionMapper` | ✅ | CRUD Mapper |
| `BadDebtService` 接口 + 实现 | ✅ | pageQuery/getById/provisionByAging/confirm/delete |
| `BadDebtProvisionStateMachineService` | ✅ | 状态机 DRAFT→CONFIRMED→VOUCHERED |
| `BadDebtController` | ✅ | CRUD + aging/percentage 端点 |
| 前端 `BadDebtList.vue` | ✅ | 列表 + 计提弹窗 |
| 前端 API 模块 + 路由 + 菜单 | ✅ | 已在 arap.ts + base.ts + sidebar |
| V12 预置比例 | ✅ | `bad_debt.aging_ratios` 含 6 个区间 |
| 测试文件 | ✅ | Controller/Mapper/StateMachine 测试存在 |
| 相关科目 | ✅ | 1122(应收账款)、1221(其他应收款)、1123(预付账款) 已在 V60 |

### 待改造/新增（本 SPEC 范围）

| 组件 | 差距 | 优先级 |
|------|------|--------|
| **科目**：1231 坏账准备, 6701 信用减值损失 | ❌ 不存在 | P0 |
| **计提方案**：可持久化的区间/比例配置 | ❌ 现用参数传递，无持久化 | P0 |
| **计提明细**：每笔应收的账龄归属与计提额 | ❌ 只存总额，无明细 | P0 |
| **补提/冲回逻辑**：对比科目余额 | ❌ 代码只算应有余额，不查已有余额 | P0 |
| **凭证生成**：confirm 时自动完成 | ❌ confirm 只改状态，不生成凭证 | P0 |
| **凭证模板**：计提补提/冲回 2 种 | ❌ 不存在 | P0 |
| **数据源扩展**：覆盖全部 4 类应收 | ⚠️ 当前只查 INVOICE_OUT | P0 |
| **NOTE_RECEIVABLE doc_type** | ❌ 需扩充 CHECK 约束 | P0 |
| **前端账龄预览**：执行前预览账龄分布 | ❌ 无预览，直接执行 | P1 |
| **坏账核销(Write-Off)**：实际发生坏账后的核销操作 | ❌ 不存在 | P1 |
| **已核销收回(Recovery)**：已核销坏账又收回 | ❌ 不存在 | P2 |

---

## 三、详细设计

### 3.1 科目种子数据

```sql
-- V81__add_bad_debt_subjects.sql
BEGIN;

-- 1231 坏账准备（资产类备抵科目，贷方余额）
INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, is_active)
SELECT '1231', '坏账准备', NULL, 1, 'credit', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '1231');

-- 6701 信用减值损失（损益类，借方余额）
INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, is_active)
SELECT '6701', '信用减值损失', NULL, 1, 'debit', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6701');

COMMIT;
```

### 3.2 计提方案配置表

**需求**：系统预置一套默认账龄区间+比例，用户可在前端调整，调整即时生效。年底计提时按当前方案计算。

```sql
-- V82__add_bad_debt_provision_scheme.sql
CREATE TABLE IF NOT EXISTS t_bad_debt_provision_scheme (
    id              BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name            VARCHAR(100)  NOT NULL,
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_bad_debt_provision_scheme IS '坏账计提方案';
COMMENT ON COLUMN t_bad_debt_provision_scheme.name IS '方案名称';
COMMENT ON COLUMN t_bad_debt_provision_scheme.is_default IS '是否默认方案';

CREATE TABLE IF NOT EXISTS t_bad_debt_provision_scheme_item (
    id              BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    scheme_id       BIGINT        NOT NULL REFERENCES t_bad_debt_provision_scheme(id),
    aging_from      INTEGER,       -- 账龄起始天数（含），NULL 表示无下限
    aging_to        INTEGER,       -- 账龄结束天数（不含），NULL 表示无上限
    label           VARCHAR(50)   NOT NULL,  -- 展示标签，如"1年以内"
    ratio           NUMERIC(5,4)  NOT NULL,  -- 计提比例，如 0.05 表示 5%
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_bad_debt_provision_scheme_item IS '计提方案区间明细';
COMMENT ON COLUMN t_bad_debt_provision_scheme_item.aging_from IS '起始天数（含），如 0';
COMMENT ON COLUMN t_bad_debt_provision_scheme_item.aging_to IS '结束天数（不含），如 365';
COMMENT ON COLUMN t_bad_debt_provision_scheme_item.ratio IS '计提比例 0-1';
```

**系统预置方案**（V82 中插入）：

| label | aging_from | aging_to | ratio |
|-------|-----------|---------|-------|
| 信用期内（当前） | 0 | 0 | 0% |
| 1-30天 | 1 | 31 | 5% |
| 31-60天 | 31 | 61 | 20% |
| 61-90天 | 61 | 91 | 50% |
| 91-180天 | 91 | 181 | 80% |
| 181-365天 | 181 | 366 | 100% |
| 365天以上 | 366 | NULL | 100% |

> **注**：原有 V12 的 `bad_debt.aging_ratios` JSON 配置保留兼容，新代码优先读取 `t_bad_debt_provision_scheme` 中 is_default 的方案。

### 3.3 计提明细表

```sql
-- V83__add_bad_debt_provision_detail.sql
CREATE TABLE IF NOT EXISTS t_bad_debt_provision_detail (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    provision_id        BIGINT        NOT NULL REFERENCES t_bad_debt_provision(id),
    source_type         VARCHAR(30)   NOT NULL,  -- INVOICE_OUT / PREPAYMENT / OTHER_RECEIVABLE / NOTE_RECEIVABLE
    source_id           BIGINT,                  -- 源单据 ID（t_business_doc.id / t_prepayment.id 等）
    source_no           VARCHAR(64),             -- 源单据编号
    customer_name       VARCHAR(200),            -- 客户/往来单位名称
    due_date            DATE,                     -- 到期日
    aging_days          INTEGER       NOT NULL,  -- 账龄天数
    bucket_label        VARCHAR(50)   NOT NULL,  -- 所属区间标签，"1-30天"
    unsettled_amount    NUMERIC(18,2) NOT NULL,   -- 未清金额
    provision_amount    NUMERIC(18,2) NOT NULL,   -- 本次计提金额
    ratio               NUMERIC(5,4)  NOT NULL,   -- 应用的比例
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bad_debt_detail_provision ON t_bad_debt_provision_detail(provision_id);
CREATE INDEX idx_bad_debt_detail_source    ON t_bad_debt_provision_detail(source_type, source_id);

COMMENT ON TABLE  t_bad_debt_provision_detail IS '坏账计提明细';
COMMENT ON COLUMN t_bad_debt_provision_detail.source_type IS '源单据类型';
COMMENT ON COLUMN t_bad_debt_provision_detail.aging_days IS '截至计提日的逾期天数';
```

### 3.4 BadDebtProvisionEntity 扩展

当前 `t_bad_debt_provision` 表需增加字段：

```sql
-- V83 (同上)
ALTER TABLE t_bad_debt_provision
  ADD COLUMN IF NOT EXISTS expected_balance     NUMERIC(18,2),  -- 应有余额（按账龄计算出的总额）
  ADD COLUMN IF NOT EXISTS existing_balance     NUMERIC(18,2),  -- 科目已有余额
  ADD COLUMN IF NOT EXISTS adjustment_amount    NUMERIC(18,2),  -- 补提/冲回金额（正=补提，负=冲回）
  ADD COLUMN IF NOT EXISTS adjustment_type      VARCHAR(10),    -- 'PROVISION'（补提）/ 'REVERSAL'（冲回）
  ADD COLUMN IF NOT EXISTS scheme_id            BIGINT;         -- 使用的计提方案 ID
```

**Entity 字段**：
```java
/** 应有余额（账龄计算出的总额）*/
private BigDecimal expectedBalance;

/** 科目已有余额（t_subject_balance 中 1231 科目的当前余额）*/
private BigDecimal existingBalance;

/** 补提/冲回金额：正=补提，负=冲回 */
private BigDecimal adjustmentAmount;

/** 调整类型：PROVISION-补提, REVERSAL-冲回 */
private String adjustmentType;

/** 使用的计提方案 ID */
private Long schemeId;
```

### 3.5 账龄分析引擎 — 核心逻辑改造

**数据源（全部覆盖）**：

| source_type | 数据表 | 查询条件 |
|------------|--------|---------|
| INVOICE_OUT | t_business_doc | doc_type='INVOICE_OUT', unsettled_amount > 0, 期间 ≤ 计提期间 |
| PREPAYMENT | t_prepayment | unsettled_amount > 0, 期间 ≤ 计提期间 |
| OTHER_RECEIVABLE | t_business_doc | doc_type='OTHER_RECEIVABLE', unsettled_amount > 0, 期间 ≤ 计提期间 |
| NOTE_RECEIVABLE | t_business_doc | doc_type='NOTE_RECEIVABLE', unsettled_amount > 0, 期间 ≤ 计提期间 |

> **NOTE_RECEIVABLE 新增**：V81 migration 需扩展 `chk_doc_type` CHECK 约束添加 `'NOTE_RECEIVABLE'`，并新增对应凭证模板（借 应收票据 / 贷 银行存款）。

**账龄区间匹配逻辑**（改造 `computeAgingBucket`）：
```java
private String computeAgingBucket(LocalDate refDate, LocalDate dueDate) {
    if (dueDate == null) return "current";     // 无到期日视为当前
    long days = refDate.toEpochDay() - dueDate.toEpochDay();
    if (days <= 0) return "current";           // 未到期
    
    // 从 t_bad_debt_provision_scheme_item 中按 days 匹配第一个区间
    // 使用当前激活的默认方案
}
```

**建议改造点**：当前 `computeAgingBucket` 硬编码 6 个区间，改为从方案配置动态读取。但为保持简单，一期可保留硬编码（与方案配置同步），后续再解耦。

### 3.6 补提/冲回逻辑

```java
// 核心计算逻辑（新增方法或改造 provisionByAging）
// 步骤1: 按账龄计算应有余额 expectedBalance
// 步骤2: 查询科目 1231 当前余额 existingBalance
//         SQL: SELECT balance FROM t_subject_balance 
//              WHERE subject_code='1231' AND period=...
//         （或从凭证科目余额汇总计算）
// 步骤3: 计算差额
BigDecimal adjustment = expectedBalance.subtract(existingBalance);

// 步骤4: 确定方向
if (adjustment.compareTo(BigDecimal.ZERO) > 0) {
    // 补提：expected > existing
    entity.setAdjustmentType("PROVISION");
} else if (adjustment.compareTo(BigDecimal.ZERO) < 0) {
    // 冲回：expected < existing
    entity.setAdjustmentType("REVERSAL");
    adjustment = adjustment.abs();  // 冲回金额为正数
} else {
    // 无需调整
    entity.setAdjustmentType(null);
    adjustment = BigDecimal.ZERO;
}

entity.setExpectedBalance(expectedBalance);
entity.setExistingBalance(existingBalance);
entity.setAdjustmentAmount(adjustment);
```

### 3.7 凭证生成（confirm 自动完成）

**老丁明确：confirm 时自动生成凭证**，不再分两步。

**凭证模板（硬编码，不配模板）**：

| 场景 | 借 | 贷 | 摘要 |
|------|----|----|------|
| 补提 | 6701 信用减值损失（计提金额） | 1231 坏账准备（计提金额） | "YYYY年度坏账准备计提" |
| 冲回 | 1231 坏账准备（冲回金额） | 6701 信用减值损失（冲回金额） | "YYYY年度坏账准备冲回" |

**状态机改造**：

```java
// confirm() 两步合一：
// 1. 校验状态（仅 DRAFT 可确认）
// 2. 计算应有余额 - 已有余额 = 补提/冲回金额
// 3. 生成凭证（DRAFT，等待人工审核过账）
// 4. 回写 voucher_id/voucher_no 到 t_bad_debt_provision
// 5. 状态 → VOUCHERED
```

**设计决策**：
- ⚠️ 按"人是唯一审核主体"铁律，凭证状态设为 **DRAFT**，等待人工审核后过账
- 凭证摘要写"YYYY年度坏账准备计提/冲回"

### 3.8 API 端点

#### 新增端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/v1/aging-analysis/summary?period=YYYYMM` | 账龄分析预览（AgingAnalysisController 已实现，返回各区间分布 + 预计金额） |
| GET | `/api/v1/bad-debts/scheme` | 获取当前生效的计提方案 |
| PUT | `/api/v1/bad-debts/scheme` | 更新方案区间比例 |
| POST | `/api/v1/bad-debts/write-off` | 坏账核销（应收确实无法收回） |
| POST | `/api/v1/bad-debts/recovery` | 已核销坏账收回 |

#### 改造现有端点

| 端点 | 改动 |
|------|------|
| `POST /provision/aging` | 增加返回 expected/existing/adjustment 字段 |
| `POST /{id}/confirm` | **改为自动生成凭证**，不再仅改状态 |
| `GET /page` | 返回字段增加 expectedBalance/existingBalance/adjustmentAmount/adjustmentType |

### 3.9 状态机

```
DRAFT ──provisionByAging──→ DRAFT（首次计算，可反复重算）
DRAFT ──confirm──→ VOUCHERED（确认 + 自动生成凭证，不可逆）

DRAFT ──delete──→ (删除)
```

> **状态简化**：用户要求 confirm 时自动生成凭证，因此中间状态 CONFIRMED 不再需要。但为保持现有 DB CHECK 约束兼容，`t_bad_debt_provision.status` 的 CHECK 约束保持 `'DRAFT', 'CONFIRMED', 'VOUCHERED'` 不变，confirm 后直接设为 VOUCHERED。

### 3.10 前端增强

#### BadDebtList.vue 改造

| 功能 | 当前 | 目标 |
|------|------|------|
| 列表列 | period/method/金额/状态 | +应有余额/已有余额/补提金额/调整类型 |
| 计提弹窗 | 手动输入各区间比例 | **从默认方案加载比例**，可直接在弹窗中修改 |
| 确认按钮 | 仅改状态 | **确认后自动生成凭证**，状态变为 VOUCHERED |
| 账龄预览 | ✅ 已实现 | AgingAnalysisController 提供 /api/v1/aging-analysis/* 套件（summary/by-customer/due-receivables 等） |
| 方案管理 | ❌ 不存在 | **在弹窗中直接修改比例**，无需独立页面 |

---

## 四、YAML 契约

```yaml
# ===== 坏账计提 API 契约 =====

aging-preview:
  method: GET
  path: /api/v1/aging-analysis/summary  # 已迁移到 AgingAnalysisController
  note: 通过 AgingAnalysisController 实现，非 BadDebtController
  params:
    period: { type: string, required: true, desc: "期间 YYYYMM" }
    schemeId: { type: integer, required: false, desc: "方案ID，缺省用默认" }
  response:
    buckets:
      - label: "信用期内"
        count: 5
        totalUnsettled: 100000.00
        ratio: 0
        provisionAmount: 0
      - label: "1-30天"
        count: 3
        totalUnsettled: 50000.00
        ratio: 0.05
        provisionAmount: 2500.00
    # ... 其他区间
    summary:
      totalExpectedBalance: 38500.00
      existingBalance: 12000.00
      adjustmentAmount: 26500.00
      adjustmentType: PROVISION

provision-by-aging:
  method: POST
  path: /api/v1/bad-debts/provision/aging
  params:
    period: { type: string, required: true }
    schemeId: { type: integer, required: false }
  body:
    ratios: { type: object, required: false, desc: "覆盖方案的手动比例" }
  response:
    id: 1
    period: "202612"
    method: AGING_RATIO
    expectedBalance: 38500.00
    existingBalance: 12000.00
    adjustmentAmount: 26500.00
    adjustmentType: PROVISION
    status: DRAFT

confirm:
  method: POST
  path: /api/v1/bad-debts/{id}/confirm
  response:
    id: 1
    voucherId: 42
    voucherNo: "PZ2026120001"
    status: VOUCHERED
    adjustmentAmount: 26500.00
    adjustmentType: PROVISION

write-off:
  method: POST
  path: /api/v1/bad-debts/write-off
  body:
    sourceType: INVOICE_OUT
    sourceId: 123
    writeOffAmount: 5000.00
    reason: "客户破产"
  response:
    voucherId: 43
    status: COMPLETED
```

---

## 五、实施计划

### M1: 数据库层（V81-V82）
| 任务 | 工时 |
|------|------|
| V81: 新增科目 1231/6701 + 扩展 chk_doc_type 加 NOTE_RECEIVABLE + 新增 NOTE_RECEIVABLE 凭证模板 | 1h |
| V82: 计提方案表 + 预置数据 + 计提明细表 + provision 表扩展字段 | 1.5h |

### M2: 后端核心逻辑
| 任务 | 工时 |
|------|------|
| BadDebtProvisionEntity + Scheme/Detail Entity + Mapper 扩展 | 0.5h |
| 账龄分析引擎改造（多数据源：INVOICE_OUT + PREPAYMENT + OTHER_RECEIVABLE + NOTE_RECEIVABLE） | 2h |
| 补提/冲回逻辑 + 科目余额查询 | 1.5h |
| 确认时自动生成凭证（confirm 改造） | 1.5h |
| 方案查询/更新 API + 账龄预览 API | 1h |
| 坏账核销/收回 API | 1.5h |

### M3: 前端
| 任务 | 工时 |
|------|------|
| 计提弹窗改造（从方案加载默认比例、弹窗内直接修改） | 2h |
| 列表列扩展（应有/已有/补提金额/调整类型） | 1h |
| 账龄预览（计提前展示分布） | 1.5h |
| 确认按钮改为自动生成凭证 + 显示凭证号 | 1h |
| 坏账核销/收回弹窗 | 1.5h |

### M4: 测试
| 任务 | 测项 | 工时 |
|------|------|------|
| 账龄计算逻辑测试 | 4 数据源 + 多场景 | 1.5h |
| 补提/冲回计算测试 | 满提/不足提/恰好平 | 1h |
| 凭证生成 E2E 测试 | 计提→确认→凭证→状态 | 1.5h |
| Controller 测试增强 | 现有测试补充新端点 | 1h |

---

## 六、未纳入一期范围

| 功能 | 原因 | 后续 |
|------|------|------|
| 余额百分比法/销货百分比法 | 一期聚焦账龄分析法，代码已有骨架 | P2 |
| 个别认定法(INDIVIDUAL) | 需要额外数据（客户信用评级等） | P2 |
| 收到票据(应收票据)作为独立类型 | 项目暂无 t_note_receivable 表 | P3 |
| 红字计提凭证（反审核） | 计提凭证的红冲 | P3 |
| 账龄分析 AI 建议 | AI 建议坏账比例 | P3 |
| 自动计提定时任务 | 年底需人工触发，不自动 | P3 |

---

## 七、关联文档

| 文档 | 说明 |
|------|------|
| [P51-aging-analysis.md](P51-aging-analysis.md) | 账龄分析与逾期预警（独立模块规格） |
| [P52-customer-reconciliation.md](P52-customer-reconciliation.md) | 客户对账与差异处理（独立模块规格） |

---

## 八、BDD 验收标准

### 场景 1：账龄分析引擎按配置区间正确划分应收并计算计提金额
**Given** 存在多笔未清应收（INVOICE_OUT / PREPAYMENT / OTHER_RECEIVABLE / NOTE_RECEIVABLE），系统预置了默认计提方案（含 7 个账龄区间）
**When** 用户调用 aging-preview API，传入期间参数
**Then** 返回每个区间的应收笔数、未清总额、计提比例和计提金额，且各区间计提金额之和等于 expectedBalance

### 场景 2：确认计提后自动生成坏账准备凭证
**Given** 账龄分析已完成，provisionByAging 计算出了补提金额（expectedBalance > existingBalance）
**When** 用户调用 confirm 确认计提
**Then** 系统自动生成一张凭证（DRAFT），借方为 6701 信用减值损失，贷方为 1231 坏账准备，金额等于 adjustmentAmount，且 BadDebtProvision 状态变为 VOUCHERED

### 场景 3：计提方案修改后即时生效
**Given** 默认计提方案已存在，某一区间比例为 5%
**When** 用户通过 PUT /api/v1/bad-debts/scheme 将该区间比例修改为 10%
**Then** 修改后立即查询 aging-preview，该区间的 provisionAmount 按新比例 10% 重新计算

### 场景 4：冲回场景 — 已有余额大于应有余额
**Given** 科目 1231 坏账准备已有余额 50000.00，账龄计算应有余额仅为 30000.00
**When** 用户调用 provisionByAging 并确认
**Then** adjustmentType 为 REVERSAL，adjustmentAmount 为 20000.00，凭证借方为 1231 坏账准备，贷方为 6701 信用减值损失

### 场景 5：无需调整 — 应有余额等于已有余额
**Given** 科目 1231 坏账准备已有余额等于账龄计算出的应有余额
**When** 用户调用 provisionByAging 并确认
**Then** adjustmentAmount 为 0，adjustmentType 为 null，凭证不生成或生成金额为 0 的凭证

### 场景 6：DRAFT 状态反复重算
**Given** 存在一笔 DRAFT 状态的计提记录
**When** 用户反复调用 provisionByAging 传入不同比例
**Then** 每次调用后预期金额、补提金额和明细数据均更新，状态保持 DRAFT

### 场景 7：确认后不可重复确认
**Given** 一笔计提记录状态为 VOUCHERED
**When** 用户再次调用 confirm
**Then** 返回业务异常，提示状态不允许重复确认

### 场景 8：DRAFT 可删除，VOUCHERED 不可删除
**Given** 一笔 DRAFT 状态的计提记录
**When** 用户调用 delete
**Then** 记录被逻辑删除

**Given** 一笔 VOUCHERED 状态的计提记录
**When** 用户调用 delete
**Then** 返回业务异常，提示已凭证化不可删除

### 场景 9：坏账核销并生成凭证
**Given** 存在一笔已确认的应收账款（INVOICE_OUT）确实无法收回
**When** 用户调用 POST /api/v1/bad-debts/write-off 传入 sourceId、writeOffAmount 和 reason
**Then** 系统生成凭证（DRAFT），借方为 1231 坏账准备，贷方为 1122 应收账款，核销金额正确

### 场景 10：已核销坏账收回
**Given** 某笔应收账款已坏账核销
**When** 用户调用 POST /api/v1/bad-debts/recovery 传入回收金额
**Then** 系统生成凭证（DRAFT），借方为 1122 应收账款（或银行存款），贷方为 1231 坏账准备，冲回已核销金额

### 场景 11：计提方案增删改查
**Given** 系统存在默认计提方案
**When** 用户通过 GET /api/v1/bad-debts/scheme 查询
**Then** 返回当前生效方案及其所有区间明细和比例

**Given** 用户通过 PUT /api/v1/bad-debts/scheme 提交新的区间比例列表
**When** 方案更新成功
**Then** 下次 provisionByAging 按新比例计算

---

## 九、状态机 YAML 契约

```yaml
# ===== 坏账准备计提 状态机契约 =====
contract_version: "1.0"
entity:
  name: BadDebtProvision
  table: t_bad_debt_provision
  description: 坏账准备计提记录，采用备抵法按账龄分析法计提坏账准备
  fields:
    - name: id
      type: BIGINT
      desc: 主键
    - name: period
      type: VARCHAR(6)
      desc: 计提期间 YYYYMM
    - name: method
      type: VARCHAR(30)
      desc: 计提方法 (AGING_RATIO / PERCENTAGE / INDIVIDUAL)
    - name: total_amount
      type: NUMERIC(18,2)
      desc: 本次计提总额
    - name: expected_balance
      type: NUMERIC(18,2)
      desc: 应有余额（按账龄计算出的总额）
    - name: existing_balance
      type: NUMERIC(18,2)
      desc: 科目已有余额（1231 坏账准备当前余额）
    - name: adjustment_amount
      type: NUMERIC(18,2)
      desc: 补提(+) / 冲回(-) 金额
    - name: adjustment_type
      type: VARCHAR(10)
      desc: 调整类型 — PROVISION（补提）/ REVERSAL（冲回）
    - name: scheme_id
      type: BIGINT
      desc: 使用的计提方案 ID
    - name: voucher_id
      type: BIGINT
      desc: 生成的凭证 ID（confirm 后回写）
    - name: voucher_no
      type: VARCHAR(64)
      desc: 生成的凭证编号
    - name: status
      type: VARCHAR(20)
      desc: 状态 — DRAFT / VOUCHERED
    - name: deleted
      type: INTEGER
      desc: 逻辑删除标记 (0=正常, 1=删除)

states:
  - name: DRAFT
    description: 草稿状态 — 计提计算完成，可反复重算、可删除
    initial: true
    allowed_actions:
      - provisionByAging
      - confirm
      - delete
  - name: VOUCHERED
    description: 已凭证化 — 确认并生成凭证，不可逆、不可删除
    initial: false
    allowed_actions: []

transitions:
  - from: DRAFT
    to: DRAFT
    action: provisionByAging
    description: 重新计算计提金额，更新明细
    conditions:
      - "记录状态为 DRAFT"
    effects:
      - "重新计算 expected_balance / existing_balance / adjustment_amount"
      - "重新生成或更新 t_bad_debt_provision_detail 明细"
      - "状态保持 DRAFT"

  - from: DRAFT
    to: VOUCHERED
    action: confirm
    description: 确认计提并自动生成凭证
    conditions:
      - "记录状态为 DRAFT"
      - "adjustment_amount 已计算"
    effects:
      - "生成凭证（DRAFT 状态），等待人工审核"
      - "补提场景：借 6701 信用减值损失 / 贷 1231 坏账准备"
      - "冲回场景：借 1231 坏账准备 / 贷 6701 信用减值损失"
      - "回写 voucher_id / voucher_no 到 t_bad_debt_provision"
      - "状态变为 VOUCHERED"

  - from: DRAFT
    to: DELETED
    action: delete
    description: 逻辑删除草稿
    conditions:
      - "记录状态为 DRAFT"
    effects:
      - "deleted 标记设为 1"
      - "记录不可再操作"

acceptance_tests:
  - scenario: "账龄分析引擎正确划分区间并计算计提金额"
    gherkin: |
      Given 存在多笔未清应收（INVOICE_OUT / PREPAYMENT / OTHER_RECEIVABLE / NOTE_RECEIVABLE）
      And 系统预置了默认计提方案（含 7 个账龄区间）
      When 用户调用 aging-preview API，传入期间参数
      Then 返回每个区间的应收笔数、未清总额、计提比例和计提金额
      And 各区间计提金额之和等于 expectedBalance

  - scenario: "确认计提自动生成补提凭证"
    gherkin: |
      Given 账龄分析已完成，expectedBalance > existingBalance
      When 用户调用 confirm 确认计提
      Then 系统自动生成一张 DRAFT 凭证
      And 借方为 6701 信用减值损失，贷方为 1231 坏账准备
      And 金额等于 adjustmentAmount
      And BadDebtProvision 状态变为 VOUCHERED

  - scenario: "确认计提自动生成冲回凭证"
    gherkin: |
      Given 账龄分析已完成，expectedBalance < existingBalance
      When 用户调用 confirm 确认计提
      Then 系统自动生成一张 DRAFT 凭证
      And 借方为 1231 坏账准备，贷方为 6701 信用减值损失
      And 金额等于 adjustmentAmount 绝对值
      And adjustmentType 为 REVERSAL
      And BadDebtProvision 状态变为 VOUCHERED

  - scenario: "计提方案修改即时生效"
    gherkin: |
      Given 默认计提方案某一区间比例为 5%
      When 用户通过 PUT /api/v1/bad-debts/scheme 将该比例修改为 10%
      Then 修改后立即查询 aging-preview，该区间的 provisionAmount 按 10% 重新计算

  - scenario: "DRAFT 状态可反复重算"
    gherkin: |
      Given 存在一笔 DRAFT 状态的计提记录
      When 用户反复调用 provisionByAging 传入不同比例
      Then 每次调用后预期金额、补提金额和明细数据均更新
      And 状态保持 DRAFT

  - scenario: "VOUCHERED 不可重复确认"
    gherkin: |
      Given 一笔计提记录状态为 VOUCHERED
      When 用户再次调用 confirm
      Then 返回业务异常，提示状态不允许重复确认

  - scenario: "DRAFT 可删除，VOUCHERED 不可删除"
    gherkin: |
      Given 一笔 DRAFT 状态的计提记录
      When 用户调用 delete
      Then 记录被逻辑删除

      Given 一笔 VOUCHERED 状态的计提记录
      When 用户调用 delete
      Then 返回业务异常，提示已凭证化不可删除

  - scenario: "坏账核销"
    gherkin: |
      Given 存在一笔已确认的应收账款确实无法收回
      When 用户调用 POST /api/v1/bad-debts/write-off 传入 sourceId、writeOffAmount 和 reason
      Then 系统生成 DRAFT 凭证，借方为 1231 坏账准备，贷方为 1122 应收账款
      And 核销金额正确

  - scenario: "已核销坏账收回"
    gherkin: |
      Given 某笔应收账款已坏账核销
      When 用户调用 POST /api/v1/bad-debts/recovery 传入回收金额
      Then 系统生成 DRAFT 凭证，借方为 1122 应收账款（或银行存款），贷方为 1231 坏账准备
      And 冲回已核销金额
```