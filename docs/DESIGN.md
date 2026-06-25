# 慧财财务系统 — 综合设计文档

> 版本：V2.1 | 日期：2026-06-23
> 本文件综合了 `基于Web财务软件的项目说明书.md`、`docs/需求分析书_*` 系列、`docs/specs/P*` 系列以及实际代码的实现情况。
>
> **规范**：多模块单库，MySQL风格PostgreSQL，严格状态机驱动。

---

## 目录

1. [系统架构](#1-系统架构)
2. [模块划分](#2-模块划分)
3. [核心状态机构架总图](#3-核心状态机构架总图)
4. [凭证状态机](#4-凭证状态机p22-✅-已实现)
5. [销售发票状态机](#5-销售发票状态机p21-a-✅-已实现)
6. [AR/AP 统一状态机](#6-arap-统一状态机p20--p12)
7. [业务单据状态机](#7-业务单据状态机)
8. [税务申报状态机](#8-税务申报状态机p18)
9. [费用报销状态机](#9-费用报销状态机p11)
10. [银行流水状态机](#10-银行流水状态机)
11. [固定资产状态机](#11-固定资产状态机)
12. [其他状态机](#12-其他状态机)
13. [核心业务流程](#13-核心业务流程)
14. [审计追踪机制](#14-审计追踪机制p24)
15. [AI 集成架构](#15-ai-集成架构)
16. [安全架构](#16-安全架构)
17. [关键技术决策](#17-关键技术决策)
18. [前端状态机集成](#18-前端状态机集成)
19. [测试覆盖率](#19-测试覆盖率)
20. [凭证模板系统](#20-凭证模板系统)
21. [实现状态总览](#21-实现状态总览)

---

## 1. 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│              前端层 (Vue 3 + Element Plus + ECharts)          │
│                 12 个视图目录，50+ 路由页面                     │
└──────────────────────────────────────────────────────────────┘
                             ↓ HTTP/HTTPS (JWT)
┌──────────────────────────────────────────────────────────────┐
│            业务应用层 (Spring Boot 3.2.5 + Java 17)           │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ system   │ │ finance  │ │ arap     │ │ tax      │        │
│  │ 用户权限  │ │ 凭证/账簿 │ │ 应收/应付 │ │ 税务/发票 │        │
│  │ 科目/期间 │ │ 银行对账  │ │ 核销      │ │ 进销项    │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ asset    │ │ budget   │ │ report   │ │ storage  │        │
│  │ 固定资产  │ │ 预算管理  │ │ 报表中心  │ │ 文件存储  │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────────────────────────────────────────────┐        │
│  │          AI 模块 (ai) — MQ 异步集成               │        │
│  └──────────────────────────────────────────────────┘        │
│  ┌──────────────────────────────────────────────────┐        │
│  │  common (异常/响应/注解) + config (安全/MP/Redis) │        │
│  └──────────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────┘
            ↓                           ↓
┌──────────────────────┐   ┌──────────────────────────────────┐
│  RabbitMQ            │   │  数据层                           │
│  task/result/dlq     │   │  ┌─ PostgreSQL 16 ─────────────┐  │
│  AI 异步任务分发      │   │  │  + pgvector/pg_trgm         │  │
└──────────────────────┘   │  │  47 个 Flyway 迁移           │  │
            ↓              │  │  66+ Entity，50+ Mapper       │  │
┌──────────────────────┐   │  └─────────────────────────────┘  │
│ AI Service (Python)  │   │  ┌─ Redis 7 ───────────────────┐  │
│ OCR/Embedding/异常   │   │  │  JWT黑名单/序列号/缓存/锁    │  │
└──────────────────────┘   │  └─────────────────────────────┘  │
                           │  ┌─ MinIO ─────────────────────┐  │
                           │  │  附件存储 (发票/回单影像)     │  │
                           │  └─────────────────────────────┘  │
                           └──────────────────────────────────┘
```

### 1.1 技术栈（已实现）

| 领域 | 技术 | 状态 |
|------|------|------|
| 后端框架 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 | ✅ |
| 安全 | Spring Security + JWT (jjwt 0.12.5) + Redis 黑名单 | ✅ |
| ORM | MyBatis-Plus (AUTO ID, 逻辑删, 乐观锁, 自动填充) | ✅ |
| 数据库 | PostgreSQL 16 (localhost:5432/huicai) | ✅ |
| 缓存 | Redis 7 (localhost:6379, 序列号/锁/黑名单) | ✅ |
| 消息队列 | RabbitMQ 3 (ai.task/ai.result/dlq) | ✅ |
| 文件存储 | MinIO (localhost:9000, 附件存储) | ✅ |
| API 文档 | Knife4j (Swagger) | ✅ |
| 前端 | Vue 3 + Element Plus + ECharts + TypeScript | ✅ |

---

## 2. 模块划分

### 2.1 后端模块（10 个包）

| 模块 | 包路径 | 职责 | Controller 数 | Entity 数 |
|------|--------|------|:-------------:|:---------:|
| system | `module.system.*` | 用户/角色/菜单/权限/科目/期间/凭证类型 | 12 | 15 |
| finance | `module.finance.*` | 凭证/账簿/银行对账/业务单据/分类规则 | 16+ | 15 |
| arap | `module.arap.*` | 应收/应付/核销/结算/客户/供应商/员工 | 11 | 12 |
| tax | `module.tax.*` | 销售发票/进项发票/税种/申报 | 1+ | 4 |
| asset | `module.asset.*` | 固定资产卡片/折旧/处置/盘点 | 4 | 7 |
| budget | `module.budget.*` | 预算编制/调整/执行 | 1 | 3 |
| report | `module.report.*` | 科目余额表/三大报表 | 1 | 3 |
| storage | `module.storage.*` | MinIO 附件上传/下载 | 1 | 1 |
| ai | `module.ai.*` | AI 任务调度/MQ消费/反馈日志 | 2 | 3 |
| common | `common.*` | 统一响应/异常/注解/AOP 切面 | — | — |

### 2.2 前端模块（12 个视图目录）

| 视图 | 子页面 |
|------|--------|
| dashboard | 首页仪表盘 |
| system | 用户/角色/菜单/部门/审计日志/数据维护/分类规则/参数配置 |
| finance | 凭证/凭证模板/账簿/业务单据/银行账户/日记账/对账单/票据/期初/期末 |
| arap | 应收/应付/客户/供应商/核销/坏账/预收预付/往来结算 |
| tax | 销售发票/进项发票/申报/税务计算 |
| asset | 资产卡片/类别/处置 |
| budget | 预算列表/编辑 |
| report | 科目余额表/资产负债表/利润表/现金流量表 |
| analysis | 关键指标/杜邦分析 |
| ai | AI任务/AI异常检测 |
| login | 登录 |
| error | 403/404 |

---

## 3. 核心状态机构架总图

慧财系统共有 **19 个独立状态机**，分布在 10 个模块。以下是核心状态机的关系总图：

```
                    ┌──────────────┐
                    │ 审计追踪(P24) │  ← AOP 拦截所有 status 变更
                    │  t_audit_log │     写入 @StatusChangeable 标记的字段
                    └──────────────┘
                            ↑ (拦截 updateById)
                            │
  ┌──────────────────────────────────────────────────────────┐
  │                    核心业务状态机                          │
  │                                                          │
  │  ┌──────────┐    ┌──────────┐    ┌──────────┐           │
  │  │ 凭证(P22) │    │ 发票(P21)│    │ 单据     │           │
  │  │4主+2附属  │◄───│ 8状态    │───►│ 6状态    │           │
  │  │ DRAFT→    │    │ PENDING→ │    │ DRAFT→   │           │
  │  │ POSTED    │    │ VOIDED   │    │ CLOSED   │           │
  │  └────┬─────┘    └────┬─────┘    └──────────┘           │
  │       │               │                                  │
  │       │               ▼ (生成应收/应付)                   │
  │       │    ┌──────────────────────┐                      │
  │       │    │  AR/AP (P20)         │                      │
  │       └───►│  CONFIRMED→SETTLED   │                      │
  │            │  →REVERSED           │                      │
  │            └──────────┬───────────┘                      │
  │                       │                                  │
  │                       ▼ (核销扣减 unsettled_amount)       │
  │            ┌──────────────────────┐                      │
  │            │  核销(P12)           │                      │
  │            │  APPROVED→EXECUTED   │                      │
  │            │  →REVERSED/CANCELLED  │                      │
  │            └──────────────────────┘                      │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                    辅助状态机                              │
  │  税务申报(P18) → DRAFT→SUBMITTED→APPROVED                │
  │  费用报销(P11) → DRAFT→SUBMITTED→APPROVED→VOUCHERED      │
  │  银行流水     → PENDING→classified→voucher_generated     │
  │  固定资产     → IN_USE/DISPOSED/SCRAPPED                 │
  │  AI任务       → PENDING→PROCESSING→COMPLETED/FAILED      │
  │  ... 另含 8 个零散状态机                                  │
  └──────────────────────────────────────────────────────────┘
```

### 3.1 状态机实现状态汇总

| 状态机 | 状态数 | CHECK约束 | 常量类 | StateMachine服务 | @StatusChangeable | 状态 |
|:-------|:------:|:---------:|:------:|:----------------:|:----------------:|:----:|
| Voucher | 4+2 | ✅V4 | ✅VoucherStatus | ✅ | ✅ | ✅ 已实现 |
| OutputInvoice | 8 | ✅V46 | ✅InvoiceStatus | ⚠️ SPEC 有 | ✅ | ✅ 已实现 |
| AR Receivable | 4 | ❌ | ✅ArapStatus | ⚠️ Service方法 | ❌ | ⚠️ 部分 |
| AR Payable | 4 | ❌ | ✅ArapStatus | ⚠️ Service方法 | ❌ | ⚠️ 部分 |
| Settlement | 4 | ✅V7 | ✅ArapStatus | ⚠️ Service方法 | ❌ | ⚠️ 部分 |
| Prepayment | 3 | ❌ | ✅ArapStatus | ❌ | ❌ | ⚠️ 部分 |
| BusinessDoc | 6 | ✅V5 | ✅BusinessDocService | ✅AutoGenerationService | ❌ | ✅ 已实现 |
| TaxDeclaration | 4 | ✅V8 | ❌ | ⚠️ 部分P18 | ❌ | ⚠️ 部分 |
| ExpenseReimbursement | 5 | ✅V34 | ✅ExpenseReimbursementService | ✅AutoGenerationService | ❌ | ✅ 已实现 |
| BankStatement | 7 | ✅V27 | ✅BankStatementService | ✅AutoGenerationService | ❌ | ✅ 已实现 |
| AssetCard | 4 | ✅V6 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| AssetInventory | 4 | ✅V6 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| AssetDisposal | 3 | ✅V6 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| BadDebtProvision | 3 | ✅V7 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| Budget | 4 | ✅V9 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| BudgetAdjustment | 3 | ✅V9 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| AiTask | 5 | ✅V10 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| Ticket | 5 | ✅V15 | ❌ | ❌ | ❌ | ⚠️ 未封装 |
| InputInvoice(cert) | 4 | ✅V8 | ❌ | ❌ | ❌ | ⚠️ 未封装 |

---

## 4. 凭证状态机（P22 ✅ 已实现）

**文件**: `VoucherStatus.java` → 常量类 | `VoucherStateMachineService.java` → 接口 | `VoucherStateMachineServiceImpl.java` → 实现 | `VoucherServiceImpl.java` → 消费方

**Entity**: `com.huicai.module.finance.entity.VoucherEntity` → `t_voucher`

### 4.1 状态定义

| 枚举值 | 状态 | 含义 | 终态 |
|:-------|:-----|:-----|:----:|
| `DRAFT` | 草稿 | 手工录入或自动生成未提交 | ❌ |
| `SUBMITTED` | 已提交 | 已提交，等待审核人处理 | ❌ |
| `AUDITED` | 已审核 | 审核通过，可记账 | ❌ |
| `POSTED` | 已记账 | 已登记总账，科目余额已更新 | ✅ |

### 4.2 附属字段

用独立字段记录，非 status 值：

| 状态 | 判断逻辑 | 何时写入 |
|:-----|:---------|:---------|
| `REJECTED` 已驳回 | `status=DRAFT` AND `rejected_reason` 非空 | SUBMITTED 驳回时 |
| `REVERSED` 已冲销 | `status=POSTED` AND `reversed_from` 非空 | 生成红字凭证时 |

**Check 约束**（V4 迁移）:
```sql
CONSTRAINT chk_voucher_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED'))
```

V47 迁移新增字段：`rejected_reason VARCHAR(500)`、`reverse_reason VARCHAR(500)`

**@StatusChangeable**: ✅ 已标注 `(entity="VOUCHER", fieldName="status")`

### 4.3 有效转换（由 VoucherStateMachineService 守卫）

```
DRAFT ──submit()──→ SUBMITTED
SUBMITTED ──audit()──→ AUDITED
SUBMITTED ──reject(reason)──→ DRAFT         (记录 rejected_reason)
AUDITED ──post()──→ POSTED                   (更新科目余额)
AUDITED ──unpost()──→ SUBMITTED             (反过账，纠错用)
POSTED ──reverse()──→ [红字凭证]             (保持 POSTED，加 reversed_from)
```

### 4.4 业务铁律

- **POSTED 锁死**：禁止 `UPDATE` / `DELETE`，唯一修正路径 → 红字冲销
- **红字冲销**：借贷方向与原凭证相反，金额绝对值相等，`reversed_from` 双向绑定
- **记账触发**：`post()` 调 `SubjectBalanceService.updateBalanceOnPost()` 更新科目余额
- **账期保护**：所有状态变更前检查 `assertPeriodOpen()` — 期间必须 OPEN

### 4.5 API 端点

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| POST | `/api/v1/vouchers` | 创建凭证（DRAFT） |
| PUT | `/api/v1/vouchers/{id}` | 修改凭证（仅 DRAFT） |
| DELETE | `/api/v1/vouchers/{id}` | 删除凭证（逻辑删除） |
| POST | `/api/v1/vouchers/{id}/submit` | 提交 |
| POST | `/api/v1/vouchers/batch-submit` | 批量提交 |
| POST | `/api/v1/vouchers/{id}/audit` | 审核 |
| POST | `/api/v1/vouchers/batch-audit` | 批量审核 |
| POST | `/api/v1/vouchers/{id}/post` | 记账 |
| POST | `/api/v1/vouchers/batch-post` | 批量记账 |
| POST | `/api/v1/vouchers/{id}/reverse` | 红冲 |
| POST | `/api/v1/vouchers/page` | 分页查询 |
| GET | `/api/v1/vouchers/{id}` | 获取详情 |

---

## 5. 销售发票状态机（P21-a ✅ 已实现）

**文件**: `InvoiceStatus.java` → 常量类 | `OutputInvoiceEntity.java` → Entity（含 `@StatusChangeable`）

**Entity**: `com.huicai.module.tax.entity.OutputInvoiceEntity` → `t_output_invoice`

### 5.1 状态定义（8 状态）

| 枚举值 | 状态 | 含义 | 终态 |
|:-------|:-----|:-----|:----:|
| `PENDING_CONFIRM` | 待确认 | 导入后未匹配客户/科目或缺关键字段 | ❌ |
| `PENDING_REVIEW` | 待审核 | 信息完整，等待财务主管确认 | ❌ |
| `CONFIRMED` | 已确认 | 审核通过，等待生成凭证 | ❌ |
| `VOUCHERED` | 已生成凭证 | 已生成凭证，记录 voucher_id | ❌ |
| `FULLY_RECONCILED` | 已核销 | 挂账金额=0 | ✅ |
| `PARTIALLY_RECONCILED` | 部分核销 | 挂账金额>0 但已发生核销 | ❌ |
| `VOIDED` | 已作废 | 发现错误，整张作废 | ✅ |
| `REVERSED` | 已冲销 | 已生成凭证后被红字冲销（承接旧 RED_INK 数据） | ✅ |

**状态数说明**：需求文档 §3.1 设计 7 状态（缺 REVERSED），但实际 V8 旧数据有 RED_INK 记录需承接，实施妥协为 8 状态。待 RED_INK 记录归档后可移除 REVERSED。

**Check 约束**（V46 迁移）:
```sql
CONSTRAINT chk_output_invoice_status CHECK (status IN (
    'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
    'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED',
    'REVERSED'
))
```

**V46 迁移逻辑** (替换已删除的 V45):
```
DROP 旧 CHECK (4: DRAFT/ISSUED/VOID/RED_INK)
  → UPDATE 数据 (NULL→PENDING_CONFIRM, DRAFT→PENDING_CONFIRM, ISSUED→CONFIRMED,
                 VOID→VOIDED, RED_INK→REVERSED)
  → ADD 新 CHECK (8 状态)
  → CREATE INDEX idx_t_output_invoice_status
  → COMMENT ON COLUMN
  → DO $$ 迁移结果审计
```

**@StatusChangeable**: ✅ 已标注 `(entity="OUTPUT_INVOICE", fieldName="status")`

### 5.2 有效转换

```
PENDING_CONFIRM ──submitForReview()──→ PENDING_REVIEW
PENDING_REVIEW ──confirm()──→ CONFIRMED
PENDING_REVIEW ──reject(reason)──→ PENDING_CONFIRM
CONFIRMED ──revertToReview()──→ PENDING_REVIEW    (选错结算状态时回退)
CONFIRMED ──markVouchered(voucherId)──→ VOUCHERED  (生成凭证)
VOUCHERED ──onReconciliationUpdate(unsettled=0)──→ FULLY_RECONCILED
VOUCHERED ──onReconciliationUpdate(unsettled>0)──→ PARTIALLY_RECONCILED
[任意非终态] ──voidInvoice(reason)──→ VOIDED
```

### 5.3 终态判断方法

```java
isTerminal(status) = VOIDED | REVERSED | FULLY_RECONCILED
isVoidable(status) = !VOIDED && !REVERSED && !FULLY_RECONCILED
isModifiable(status) = PENDING_CONFIRM | PENDING_REVIEW
```

### 5.4 API 端点（SPEC 设计，部分未实现）

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| POST | `/api/v1/output-invoices/{id}/submit-review` | 提交审核 |
| POST | `/api/v1/output-invoices/{id}/confirm` | 审核通过 |
| POST | `/api/v1/output-invoices/{id}/reject` | 审核驳回 |
| POST | `/api/v1/output-invoices/{id}/revert` | 回退到待审核 |
| POST | `/api/v1/output-invoices/{id}/void` | 作废 |
| GET | `/api/v1/output-invoices?status=VOUCHERED` | 按状态过滤 |

### 5.5 采购发票备注

P21-b（采购发票状态机 SPEC）已 **废弃**（2026-06-22 老丁拍板）。

**废弃原因**：`t_input_invoice` 无 status 字段，实际用 `certification_status`（4 态: UNCERTIFIED/CERTIFIED/INVALID/CANCELLED），voucher_id 已能标识"已生成凭证"。后续如需扩展，请开 P21-c。

---

## 6. AR/AP 统一状态机（P20 + P12）

**文件**: `ArapStatus.java` → 常量类（10 状态通用于 4 个 Entity）

**Entity**:
- `ReceivableEntity` → `t_receivable`（应收单）
- `PayableEntity` → `t_payable`（应付单）
- `ArapSettlementEntity` → `t_arap_settlement`（核销单）
- `PrepaymentEntity` → `t_prepayment`（预收预付）

### 6.1 ArapStatus 常量

```java
// 通用 3 态
DRAFT          // 草稿（初始）
CONFIRMED      // 已确认（债权/债务已确认）
REVERSED       // 已冲销（反核销/红冲）

// Receivable/Payable 特有
SETTLED        // 已结清（unsettled_amount=0）

// Settlement 特有
VOUCHERED      // 已生成凭证

// ReconciliationLog 特有
EXECUTED       // 已执行核销
REJECTED       // 已拒绝
CANCELLED      // 已取消

// Prepayment 特有
APPLIED        // 已核销抵扣
```

### 6.2 各实体状态转换

#### ReceivableEntity / PayableEntity

```
DRAFT ──confirm()──→ CONFIRMED
CONFIRMED + unsettled=0 ──自动──→ SETTLED
CONFIRMED/SETTLED ──reverse()──→ REVERSED
REVERSED ──（终态，不可再变更）
```

V37 迁移新增 status 字段，默认 `CONFIRMED`（现有导入/流水生成的应收应付单直接已确认）。

#### ArapSettlementEntity（核销单）

```
DRAFT ──create()──→ CONFIRMED
CONFIRMED ──generateVoucher()──→ VOUCHERED
CONFIRMED ──reverse()──→ REVERSED
```

**Check 约束**（V7）:
```sql
CONSTRAINT chk_settlement_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOUCHERED', 'REVERSED'))
```

#### PrepaymentEntity（预收预付）

```
DRAFT ──confirm()──→ CONFIRMED
CONFIRMED ──核销抵扣──→ APPLIED
```

### 6.3 自动 SETTLED 逻辑

在 `ReconciliationServiceImpl.execute()` 核销执行**之后**，检查 unsettled_amount：

```java
if (entity.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
    && ArapStatus.isConfirmed(entity.getStatus())) {
    entity.setStatus(ArapStatus.SETTLED);  // 自动标记结清
}
```

---

## 7. 业务单据状态机

**Entity**: `com.huicai.module.finance.entity.BusinessDocEntity` → `t_business_doc`

### 7.1 状态定义（6 态）

| 枚举值 | 含义 | 终态 |
|:-------|:-----|:----:|
| `DRAFT` | 草稿 | ❌ |
| `SUBMITTED` | 已提交 | ❌ |
| `APPROVED` | 已审核 | ❌ |
| `VOUCHERED` | 已生成凭证 | ❌ |
| `CLOSED` | 已关闭 | ✅ |
| `REJECTED` | 已驳回 | ❌ |

**Check 约束**（V5）:
```sql
CONSTRAINT chk_doc_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'VOUCHERED', 'CLOSED', 'REJECTED'))
```

### 7.2 有效转换

```
DRAFT ──→ SUBMITTED ──→ APPROVED ──→ VOUCHERED ──→ CLOSED
                     ↘ REJECTED
```

### 7.3 单据类型与科目映射

| 单据类型 | 代码前缀 | 默认借方 | 默认贷方 |
|:---------|:--------:|:---------|:---------|
| RECEIPT（收款单） | SK | 1002 银行存款 | 1122 应收账款 |
| PAYMENT（付款单） | FK | 2202 应付账款 | 1002 银行存款 |
| EXPENSE（报销单） | BX | 6602 费用 | 1002 银行存款 |
| INVOICE_IN（采购发票） | FPR | 1403 库存商品 | 2202 应付账款 |
| INVOICE_OUT（销售发票） | FPS | 1122 应收账款 | 6001 主营业务收入 |
| OTHER_RECEIVABLE（其他应收） | QTY | 1221 其他应收款 | 1002 银行存款 |
| OTHER_PAYABLE（其他应付） | QTF | 1002 银行存款 | 2241 其他应付款 |

单据编号：Redis INCR `doc:no:{period}:{docType}`，格式 `{CODE}{PERIOD}{4位流水}`。

---

## 8. 税务申报状态机（P18）

**Entity**: `com.huicai.module.tax.entity.TaxDeclarationEntity` → `t_tax_declaration`

### 8.1 状态定义

```sql
CONSTRAINT chk_declaration_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PAID'))
```

### 8.2 有效转换

```
DRAFT ──submitDeclaration()──→ SUBMITTED
SUBMITTED ──approveDeclaration()──→ APPROVED
SUBMITTED ──rejectDeclaration(reason)──→ REJECTED
```

### 8.3 增值税计算逻辑

```
outputTax = SUM(t_output_invoice.taxAmount WHERE period=?)
inputTax = SUM(t_input_invoice.deductionAmount WHERE period=? AND CERTIFIED)
payableVAT = outputTax - inputTax
附加税 = payableVAT × 12% (城建7% + 教育3% + 地方教育2%)

留抵场景：payableVAT < 0 → 附加税=0，待抵扣税额=|payableVAT|
```

---

## 9. 费用报销状态机（P11）

**Entity**: `com.huicai.module.arap.entity.ExpenseReimbursementEntity` → `t_expense_reimbursement`

### 9.1 状态定义

V34 迁移：`status VARCHAR(20) DEFAULT 'DRAFT'`（无 CHECK 约束）

| 枚举值 | 含义 | 终态 |
|:-------|:-----|:----:|
| `DRAFT` | 草稿 | ❌ |
| `SUBMITTED` | 已提交（等待审批） | ❌ |
| `APPROVED` | 已审批（可生成凭证） | ❌ |
| `REJECTED` | 已驳回 | ❌ |
| `VOUCHERED` | 已生成凭证 | ✅ |

### 9.2 员工匹配逻辑（P11-3）

银行流水 out 方向 → `counterAccount` 匹配 `t_employee.name`
- 匹配成功 → 创建报销单 (DRAFT)
- 匹配失败 → 正常 A/B/C 分类处理

### 9.3 报销→凭证自动生成（P11-4）

```
流水确认 → 创建报销单(DRAFT) → 提交(SUBMITTED) → 审批(APPROVED) → 生成凭证(VOUCHERED)
```

凭证停在 DRAFT 等待总账会计过账。

---

## 10. 银行流水状态机

**Entity**: `com.huicai.module.finance.entity.BankStatementEntity` → `t_bank_statement`

### 10.1 review_status 定义（V27 迁移）

```sql
CONSTRAINT chk_stmt_review_status CHECK (review_status IN (
    'PENDING', 'CONFIRMED', 'RECLASSIFIED',
    'classified', 'voucher_generated', 'payment_created', 'manual_pending', 'approved'
))
```

### 10.2 有效转换

```
PENDING ──classify()──→ classified
classified ──A类路由──→ voucher_generated  (bank_fee/interest/tax/social_security/insurance_fee)
classified ──B类路由──→ payment_created    (business_receipt/payment/internal_transfer)
classified ──C类路由──→ manual_pending     (pending/不明确)
voucher_generated/payment_created ──review(confirm)──→ CONFIRMED
payment_created ──review(reclassify)──→ RECLASSIFIED
CONFIRMED ──→ approved (审核确认，可选)
```

### 10.3 A/B/C 分类路由（AutoGenerationService）

| 分类 | 类型 | 自动动作 |
|:-----|:-----|:---------|
| `bank_fee` | A | 直接生成凭证（借:6602.01 财务费用-手续费 / 贷:1002 银行存款） |
| `interest_income` | A | 直接生成凭证（借:1002 银行存款 / 贷:6602.02 财务费用-利息收入） |
| `tax_payment` | A | 直接生成凭证（借:2221 应交税费 / 贷:1002 银行存款） |
| `social_security` | A | 直接生成凭证（借:2211 应付职工薪酬-社保 / 贷:1002） |
| `insurance_fee` | A | 直接生成凭证（借:6602 管理费用-保险费 / 贷:1002） |
| `business_receipt` | B | 生成收款单 → 推荐核销发票 |
| `business_payment` | B | 生成付款单 → 推荐核销发票 |
| `internal_transfer` | B | 生成银行转账单 |
| `salary_payment` | B | 生成付款单（关联员工）→ 费用报销 |
| `pending` | C | 归入待处理池 → 人工指定类型 |

### 10.4 三层分类引擎

```
优先级:  规则引擎(高) > AI 语义(中) > 兜底启发式(低)
              ↓              ↓              ↓
           确定性高         灵活性强      100%有结果
           Java 本地       Python 微服务   Java 本地
```

- **规则引擎**：8 条种子规则（keyword/keyword_regex/counterparty_match），按 priority 排序，第一命中即停
- **AI 语义**：Python 文本嵌入服务，摘要向量化 → pgvector 余弦相似度匹配（需 RabbitMQ 异步）
- **兜底启发式**：10 级关键词分组（银行费用→利息→保险→税务→社保→工资→内部转账→收款→付款）

---

## 11. 固定资产状态机

### 11.1 资产卡片（AssetCard）

**Entity**: `t_asset_card`

```
IN_USE (使用中) ↔ IDLE (闲置)
IN_USE ──→ DISPOSED (已处置)
SCRAPPED (已报废，终态)
```

**Check 约束**: `CONSTRAINT chk_asset_status CHECK (status IN ('IN_USE', 'IDLE', 'DISPOSED', 'SCRAPPED'))`

### 11.2 资产盘点（AssetInventory）

```
DRAFT ──→ IN_PROGRESS ──→ COMPLETED ──→ VOUCHERED
```

**Check 约束**: `CONSTRAINT chk_inv_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'VOUCHERED'))`

### 11.3 资产处置（AssetDisposal）

```
DRAFT ──→ APPROVED ──→ VOUCHERED
```

**Check 约束**: `CONSTRAINT chk_disposal_status CHECK (status IN ('DRAFT', 'APPROVED', 'VOUCHERED'))`

### 11.4 折旧规则

- 当月新增 → **下月**开始折旧
- 当月减少 → **当月**照常计提（最后一个月）

---

## 12. 其他状态机

### 12.1 坏账准备

```
DRAFT ──→ CONFIRMED ──→ VOUCHERED
```

### 12.2 预算

```
DRAFT ──→ APPROVED ──→ ACTIVE ──→ CLOSED
```

### 12.3 预算调整

```
DRAFT ──→ APPROVED / REJECTED
```

### 12.4 AI 任务

```
PENDING ──→ PROCESSING ──→ COMPLETED / FAILED (可重试)
```

### 12.5 票据（支票/汇票）

```
IN_STOCK (在库) ──→ ISSUED (已发出) ──→ ENDORSED (已背书) / CASHED (已兑现)
                                                              ↘ VOIDED (已作废)
```

### 12.6 银行对账匹配建议

```
PENDING ──→ CONFIRMED / REJECTED
```

---

## 13. 核心业务流程

### 13.1 银行流水处理全流程

```
┌──────────────────────────────────────────────────────────────────┐
│ Phase 1: 文件导入                                                  │
│  上传 Excel/CSV → 列名智能识别(两阶段+级联回退) → 解析入库          │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│ Phase 2: 智能分类                                                  │
│  规则引擎(8种子规则,priority排序,第一命中即停)                      │
│    → 兜底启发式(10级关键词分组,100%有结果)                          │
│    → AI语义增强(可选,置信度<60%触发,异步MQ)                         │
│  分类结果: bank_fee/interest/business_receipt/business_payment/... │
│  置信度≥80%绿色/60-79%黄色/<60%灰色                                 │
└──────────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────────┐
│ Phase 3: 出纳审查工作台                                            │
│  查看统计面板(总数/已确认/待处理/异常) → 按分类筛选                  │
│  确认(批量) / 驳回 / 重分类                                         │
└──────────────────────────────────────────────────────────────────┘
                            ↓ 确认后
┌──────────────────────────────────────────────────────────────────┐
│ Phase 4: A/B/C 路由                                                │
│  A类 (bank_fee/interest/tax等)                                     │
│    → 直接生成凭证草稿(DRAFT)                                        │
│  B类 (business_receipt/payment)                                    │
│    → 匹配客商 → 创建业务单据(DRAFT)                                  │
│    → 核销推荐(金额一致+摘要相似度+对方名匹配)                        │
│    → 人工确认核销 → 执行核销                                        │
│    → 生成凭证(DRAFT)                                               │
│  C类 (pending)                                                    │
│    → 归入待处理池 → 人工处理工作台                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 13.2 销售发票完整流

```
┌──────────────────────────────────────────────────────┐
│ Phase 1: 发票导入 (纯数据写入)                          │
│  OCR/税局/Excel 导入                                   │
│  → 客户自动匹配(税号精确→名称模糊→自动创建)               │
│  → 写入 t_business_doc + t_output_invoice + t_receivable│
│  → 所有发票导入完成后自动触发批量红冲关联                      │
│   扫描全库金额为负的红字发票（非终态），按金额绝对值+客户名      │
│   匹配蓝字发票，标记 → REVERSED（与手动"批量红冲关联"同逻辑）   │
│  → 状态: PENDING_CONFIRM                                │
│  (红冲检测不在导入循环内执行，避免蓝字在后导致匹配失败)     │
└──────────────────────────────────────────────────────┘
                            ↓ 提交审核
┌──────────────────────────────────────────────────────┐
│ Phase 1.5: 审核确认（人工操作）                         │
│  PENDING_CONFIRM → PENDING_REVIEW → CONFIRMED          │
└──────────────────────────────────────────────────────┘
                            ↓ 审核通过
┌──────────────────────────────────────────────────────┐
│ Phase 2: 生成凭证                                       │
│  CONFIRMED → 选择结算状态:                              │
│    A未收款(赊销) → 借:应收账款 贷:收入+销项税            │
│    B已收款(现结) → 借:银行存款 贷:收入+销项税(强现金流)   │
│    C冲预收       → 借:预收账款 贷:收入+销项税            │
│  → 发票: VOUCHERED, 记录voucher_id                      │
│  → 凭证: DRAFT, 等待总账审核                             │
└──────────────────────────────────────────────────────┘
                            ↓ 核销
┌──────────────────────────────────────────────────────┐
│ Phase 3: 收款核销                                       │
│  出纳收到回单 → 录入收款单                                │
│  → 系统推荐未核销发票(金额+摘要+到期日多维评分)            │
│  → 选择核销方案 → 执行核销                                │
│  → 发票: FULLY_RECONCILED / PARTIALLY_RECONCILED        │
│  → 收款单: → 生成凭证                                     │
└──────────────────────────────────────────────────────┘
```

### 13.3 核销匹配策略（按优先级）

| 策略 | 条件 | 说明 |
|:-----|:-----|:------|
| L1 精确匹配 | 金额=未核销余额 ∧ 摘要含发票号 | 满分推荐 |
| L2 参考号匹配 | `externalNo = invoiceNo` | 银行流水参考号 |
| L3 精确金额+日期 | 金额一致 ∧ 日期±3天 | 高精度匹配 |
| L4 精确金额 | 金额完全一致 | 标准匹配 |
| L5 容差匹配 | \|金额-未核销余额\| ÷ 未核销余额 ≤ 10% | 尾差处理 |
| L6 同客商其他 | 同一客商的任意未结单据 | 后备候选 |

匹配度评分：金额一致(0.4) + 摘要相似度_Jaccard(0.4) + 对方户名匹配(0.2)

### 13.4 业务端到端数据流

```
销售发票导入:
  t_output_invoice (PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED→VOUCHERED)
    → 生成 t_receivable (CONFIRMED) + t_business_doc (VOUCHERED)
    → 生成 t_voucher (DRAFT→SUBMITTED→AUDITED→POSTED)
    → 核销: t_receivable (SETTLED) + t_output_invoice (FULLY_RECONCILED)
    → t_arap_settlement (CONFIRMED→VOUCHERED)
    → t_reconciliation_log (EXECUTED)

银行流水→付款:
  t_bank_statement (PENDING→classified→payment_created→CONFIRMED)
    → 匹配客商 → 生成 t_payable (CONFIRMED)
    → 匹配采购发票 → 核销: t_payable (SETTLED) + t_input_invoice
    → 生成 t_voucher (DRAFT)

费用报销:
  t_expense_reimbursement (DRAFT→SUBMITTED→APPROVED→VOUCHERED)
    → 生成 t_voucher (DRAFT)
```

---

## 14. 审计追踪机制（P24）

### 14.1 实现方式

| 组件 | 文件 | 说明 |
|:-----|:-----|:------|
| `@StatusChangeable` 注解 | `common/annotation/StatusChangeable.java` | 标记需要审计的 status 字段 |
| `StatusChangeAspect` AOP 切面 | `common/aspect/StatusChangeAspect.java` | 拦截 `BaseMapper.updateById` |
| `AuditLogService.recordStatusChange()` | `system/service/AuditLogService.java` | 写入 t_audit_log |

### 14.2 工作流程

```
调用方 Service 调 voucherMapper.updateById(entity)
  → StatusChangeAspect.aroundUpdateById() 拦截
  → 查找 @StatusChangeable 字段
  → 反射 selectById 查旧值
  → 对比新旧值
  → 值相同: 直接放过 (proceed)
  → 值不同: proceed() 执行 update
  → 写入 t_audit_log (同事务, MANDATORY propagation)
  → 写入失败: throw e → 业务事务回滚 (fail-fast)
```

### 14.3 已标注的 Entity

| Entity | 字段 | entity 值 |
|:-------|:-----|:----------|
| `OutputInvoiceEntity` | status | `OUTPUT_INVOICE` |
| `VoucherEntity` | status | `VOUCHER` |

### 14.4 审计日志表结构

| 字段 | 类型 | 说明 |
|:-----|:-----|:------|
| `module` | VARCHAR | Entity 类型 (OUTPUT_INVOICE/VOUCHER) |
| `operation` | VARCHAR | `STATUS_CHANGE` |
| `method` | VARCHAR | `${entityType}.updateStatus` |
| `requestParams` | jsonb | `entityId=X, field=status` |
| `responseResult` | jsonb | `newValue=CONFIRMED` |
| `oldSnapshot` | jsonb | `{"status":"PENDING_REVIEW"}` |
| `newSnapshot` | jsonb | `{"status":"CONFIRMED"}` |
| `status` | VARCHAR | `SUCCESS` |
| `userId` | BIGINT | 操作人（从 SecurityContext 获取） |

### 14.5 审计触发场景

| 场景 | 触发链 | 审计条数 |
|:-----|:-------|:--------:|
| 发票审核通过 | `confirm()` → updateById → AOP 拦截 | 1 |
| 凭证驳回 | `reject()` → updateById → AOP (含 rejected_reason) | 1 |
| 红字冲销 | `generateReversalVoucher()` → 原凭证 updateById + 红字凭证 insert | 1 (仅原凭证 status 变更) |

---

## 15. AI 集成架构

### 15.1 通信机制

```
Java 主系统                          Python AI 服务
    │                                     │
    │ 1. 创建 t_ai_task (PENDING)          │
    │ 2. 发送 RabbitMQ (ai.task.queue)     │
    │─────────────────────────────────→    │
    │                                     │ 3. 消费任务
    │                                     │ 4. 处理 (OCR/Embedding/异常检测)
    │                                     │ 5. 发送结果 (ai.result.queue)
    │  ←───────────────────────────────── │
    │                                     │
    │ 6. AiResultListener 消费结果          │
    │ 7. 更新 t_ai_task (COMPLETED/FAILED) │
    │ 8. 更新业务表 (ai_suggested_action)  │
```

### 15.2 RabbitMQ 配置

```
Exchange: huicai.ai.exchange (DirectExchange)
├── Queue: huicai.ai.task.queue
│   └── Routing key: ai.task
│   └── DLQ: huicai.ai.dlq (死信交换)
│
└── Queue: huicai.ai.result.queue
    └── Routing key: ai.result

DLQ Exchange: huicai.ai.dlq.exchange
└── Queue: huicai.ai.dlq
    └── Routing key: ai.dlq
```

### 15.3 AI 服务清单

| 服务 | 技术栈 | 功能 |
|:-----|:-------|:-----|
| OCR | PaddleOCR + Tesseract | 发票/回单图片识别 |
| 文本嵌入 | text2vec-base-chinese / BGE | 摘要向量化 → pgvector 存储 |
| 匹配服务 | Jaccard + 余弦相似度 | 核销匹配建议 |
| 异常检测 | Isolation Forest / LSTM | 凭证异常标记 |
| 预测服务 | Prophet / ARIMA | 现金流趋势预测 |
| 问答服务 | LangChain + LLM | NL2SQL / 财务制度问答 |

### 15.4 人工审核闭环

所有 AI 结果**必须有**"建议 → 人工确认 → 生效"三阶段：

```
[AI 产出]
   ↓ 置信度分级
[≤60% 灰] [60-79% 黄] [≥80% 绿]
   ↓            ↓           ↓
[人工判断]  [重点核对]  [一键确认]
   ↓
[用户点击确认]
   ↓
[生效：更新业务数据]
```

---

## 16. 安全架构

### 16.1 JWT 认证

| 组件 | 文件 |
|:-----|:-----|
| JwtProvider | `config/security/JwtProvider.java` |
| JwtAuthenticationFilter | `config/security/JwtAuthenticationFilter.java` |
| SecurityConfig | `config/security/SecurityConfig.java` |

**Token 生命周期**：
- Access Token: 24h (claims: userId, roles)
- Refresh Token: 7d (same claims)
- 登出时 Token 加入 Redis 黑名单 `token:blacklist:{token}`

**安全过滤链**：
```
Request → JwtAuthenticationFilter (OncePerRequestFilter)
  ├─ 提取 "Authorization: Bearer {token}"
  ├─ 检查 Redis 黑名单
  ├─ jwtProvider.validateToken(token)
  ├─ loadUserByUsername → authorities
  └─ SecurityContextHolder.setAuthentication(...)
```

**公开接口**（无需认证）：`/api/v1/auth/login`、`/api/v1/system/health`、`/doc.html`、`/swagger-ui/**`

### 16.2 数据权限

`DataPermissionInterceptor` 是 MyBatis-Plus `InnerInterceptor` 的 scaffolding 实现。

当前状态：仅认证检查 + 日志输出。**实际的数据权限 SQL 注入未实现**（计划中：按 dept_id 注入 SQL `WHERE` 条件）。

### 16.3 权限模型

RBAC 模型，权限格式：`module:resource:action`

```
用户 ← 多对多 → 角色 ← 多对多 → 菜单（含按钮权限码）
                              → 数据权限（部门/岗位维度）

前端: v-permission 指令控制按钮可见性
后端: @PreAuthorize 注解校验
```

---

## 17. 关键技术决策

### 17.1 状态机设计原则

| 原则 | 说明 |
|:-----|:------|
| **状态机先行** | 所有业务流转基于 status 字段判断，禁止使用"金额是否为空"等模糊条件 |
| **String 常量非 Enum** | 保持与数据库 VARCHAR 兼容、与 MyBatis-Plus 字段赋值兼容 |
| **不可修改铁律** | POSTED/终态数据禁止 UPDATE/DELETE，只能红冲/反核销 |
| **同事务审计** | 状态变更与审计日志同事务，审计失败则业务回滚 |
| **行级锁保护** | 重要状态变更操作使用 `SELECT ... FOR UPDATE` 确保并发安全 |

### 17.2 金额与精度

| 场景 | 精度 | Java 类型 | 规则 |
|:-----|:-----|:----------|:-----|
| 发票金额 | NUMERIC(18,2) | BigDecimal | HALF_UP |
| 凭证金额 | NUMERIC(18,2) | BigDecimal | HALF_UP |
| 核销金额 | NUMERIC(18,2) | BigDecimal | HALF_UP |
| 核销容差 | 5.00 元 | BigDecimal | ≤5 元记入财务费用 |

### 17.3 并发控制

| 机制 | 场景 | 实现 |
|:-----|:-----|:-----|
| 行级锁 | 状态变更 | `selectByIdForUpdate()` |
| 乐观锁 | 科目余额更新 | MyBatis-Plus `@Version` + `OptimisticLockerInnerInterceptor` |
| 分布式锁 | 银企对账 | Redis SETNX + TTL |
| 原子序列号 | 凭证/单据编号 | Redis INCR |

### 17.4 禁用模式

- ❌ 禁止使用 `double`/`float` 处理金额
- ❌ 禁止 `as any`/`@ts-ignore` 类型压制
- ❌ 禁止 `catch(e) {}` 空捕获
- ❌ 禁止删除已有测试来"通过"
- ❌ 禁止对 POSTED 凭证做 UPDATE/DELETE

---

## 18. 前端状态机集成

### 18.1 状态机集成模式

| 模块 | 子模块 | 关键文件 |
|:-----|:-------|:---------|
| 项目骨架 | 前端/后端/DB/CI | pom.xml, docker-compose, 47 个迁移 |
| 系统管理 | 科目/期间/凭证类型/摘要/配置 | system module 全 |
| RBAC | 用户/角色/菜单/权限/审计日志 | system module 全 |
| 凭证管理 | 创建/审核/记账/红冲/查询 | VoucherService + Controller + 12 单测 |
| 凭证状态机 | VoucherStateMachineService | ✅ 4态守卫, V47 迁移 |
| 银行流水 | 导入/列名映射/分类/工作台/A/B/C路由 | BankStatementService(744行) + AutoGenerationService(665行) |
| 分类规则 | 8种子规则/CRUD/优先级/匹配引擎 | ClassificationRuleService |
| 智能分类 | 规则引擎+兜底启发式+AI异步 | FallbackHeuristicService + AiRabbitMQ |
| 业务单据 | 7单据类型/凭证生成/编号 | BusinessDocService(520行) |
| 往来核销 | L1-L6匹配/FIFO/容差/预收预付 | ReconciliationService(1109行) |
| 销售发票基础设施 | InvoiceStatus 常量/V46迁移/Entity注解 | 已完成 |
| 费用报销 | 员工档案/报销单/自动匹配/凭证生成 | ExpenseReimbursementService |
| 前端框架 | 路由/布局/权限指令/API封装 | 12视图目录, 50+路由 |


---

> **引用文档索引**：
> - `基于Web财务软件的项目说明书.md` — 主项目说明书，高层架构
> - `docs/需求分析书_发票与凭证状态机_V1.0.md` — 发票/凭证状态机需求
> - `docs/需求分析书_银行流水导入分类_V1.0.md` — 银行流水导入需求
> - `docs/specs/P20-arap-state-machine-spec.md` — AR/AP 状态机 SPEC
> - `docs/specs/P21-sales-invoice-state-machine.md` — 销售发票状态机 SPEC
> - `docs/specs/P22-voucher-state-machine.md` — 凭证状态机 SPEC
> - `docs/specs/P24-audit-tracking.md` — 审计追踪 SPEC
> - `docs/开发计划书.md` — 开发计划
#### 按钮状态控制

```vue
<!-- VoucherList.vue -->
<el-button v-if="row.status === 'DRAFT'" @click="onSubmit(row)">提交</el-button>
<el-button v-if="row.status === 'SUBMITTED'" @click="onAudit(row)">审核</el-button>
<el-button v-if="row.status === 'AUDITED'" @click="onPost(row)">记账</el-button>
<el-button v-if="row.status === 'POSTED' || row.status === 'AUDITED'" @click="onReverse(row)">红冲</el-button>
```

#### 批量操作计算属性

```typescript
const canBatchSubmit = computed(() => selectedRows.value.some((r) => r.status === 'DRAFT'))
const canBatchAudit = computed(() => selectedRows.value.some((r) => r.status === 'SUBMITTED'))
const canBatchPost = computed(() => selectedRows.value.some((r) => r.status === 'AUDITED'))
```

### 18.3 银行流水状态机前端集成

**文件**: `frontend/src/api/modules/bankStatement.ts` + `frontend/src/views/finance/bank-statement/BankStatementView.vue`

#### 状态变更 API

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| POST | `/bank-statements/{id}/classify` | 分类 |
| POST | `/bank-statements/{id}/review` | 确认/驳回 |
| POST | `/bank-statements/batch-review` | 批量确认 |

### 18.4 销售发票状态机前端集成

**文件**: `frontend/src/api/modules/salesInvoice.ts` + `frontend/src/views/finance/sales-invoice/SalesInvoiceImportView.vue`

#### 状态变更 API

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| POST | `/output-invoices/{id}/submit-review` | 提交审核 |
| POST | `/output-invoices/{id}/confirm` | 审核通过 |
| POST | `/output-invoices/{id}/reject` | 审核驳回 |
| POST | `/output-invoices/{id}/void` | 作废 |

### 18.5 前端状态机集成原则

| 原则 | 说明 |
|:-----|:-----|
| **状态驱动 UI** | 所有按钮显示/禁用完全由后端返回的 status 字段控制 |
| **禁止前端判断** | 不允许前端猜测状态转换，必须调用后端 API |
| **统一状态映射** | 状态显示名称统一在 API 模块层定义，禁止在视图中硬编码 |
| **即时刷新** | 状态变更成功后立即刷新列表，确保 UI 状态与后端一致 |
| **批量操作校验** | 批量操作前计算属性检查是否有符合条件的记录 |

---

## 19. 测试覆盖率

### 19.1 单元测试统计

| 模块 | Service 文件 | 测试文件 | 测试用例数 | 代码行数 |
|:-----|:------------|:--------|:---------:|:-------:|
| finance | VoucherServiceImpl.java | VoucherServiceTest.java | 12 | ~300 |
| finance | BankStatementServiceImpl.java | - | - | 744 |
| finance | AutoGenerationService.java | - | - | 665 |
| finance | BusinessDocServiceImpl.java | - | - | 520 |
| arap | ReconciliationServiceImpl.java | - | - | 1109 |
| arap | ReceivableServiceImpl.java | - | - | ~200 |
| arap | PayableServiceImpl.java | - | - | ~200 |
| arap | ExpenseReimbursementServiceImpl.java | - | - | ~250 |
| system | UserServiceImpl.java | - | - | ~150 |

### 19.2 已覆盖场景

**凭证模块（12 单测）**：

| 测试场景 | 方法名 |
|:---------|:-------|
| 创建凭证 | `testCreateVoucher` |
| 修改凭证 | `testUpdateVoucher` |
| 提交凭证 | `testSubmitVoucher` |
| 审核凭证 | `testAuditVoucher` |
| 记账凭证 | `testPostVoucher` |
| 红冲凭证 | `testReverseVoucher` |
| 批量提交 | `testBatchSubmit` |
| 批量审核 | `testBatchAudit` |
| 批量记账 | `testBatchPost` |
| 凭证编号生成 | `testGenerateVoucherNo` |
| 科目余额更新 | `testUpdateSubjectBalance` |
| 期间校验 | `testPeriodValidation` |

### 19.3 测试技术栈

| 组件 | 技术 |
|:-----|:-----|
| 测试框架 | Spring Boot Test + JUnit 5 |
| Mock | Mockito |
| 数据库 | H2 (内存数据库) |
| 断言 | AssertJ |

### 19.4 测试覆盖率目标

| 模块 | 当前覆盖率 | 目标覆盖率 |
|:-----|:---------:|:---------:|
| 凭证管理 | ~80% | 90% |
| 银行流水 | 0% | 70% |
| 往来核销 | 0% | 70% |
| 销售发票 | 0% | 70% |
| 业务单据 | 0% | 60% |

---

## 20. 凭证模板系统

### 20.1 概述

凭证模板系统用于将凭证分录的科目映射从硬编码剥离为配置驱动，使新增业务场景无需改代码。目前**模板的"壳"已就绪，但核心引擎和大部分接入点未实现**。

### 20.2 现有基础（✅）

| 组件 | 文件 | 说明 |
|:-----|:-----|:------|
| `VoucherTemplateEntity` | `finance/entity/` | 主表：name, classification, numberPrefix, isActive |
| `VoucherTemplateLineEntity` | `finance/entity/` | 分录行：subjectId, dr/crAmountTemplate, summaryTemplate, direction |
| `VoucherTemplateService` | `finance/service/` | CRUD + `matchByClassification()` |
| `VoucherTemplateController` | `finance/controller/` | 完整 REST API |
| 前端模板管理页 | `views/finance/voucher-template/` | 模板列表 + CRUD 弹窗 |
| V23 迁移 | `db/migration/V23__...` | t_voucher_template + t_voucher_template_line + 5 条种子模板 |
| V40 迁移 | `db/migration/V40__...` | 核销场景模板（reconciliation_receipt/payment） |
| V42 迁移 | `db/migration/V42__...` | 结算场景模板（settlement_receivable/payment） |
| `AutoGenerationService` 接入 | `finance/service/impl/` | A 类制证先查模板，无匹配降级硬编码 |
| `ReconciliationServiceImpl` 接入 | `arap/service/impl/` | 核销制证使用模板 |
| `ArapSettlementServiceImpl` 接入 | `arap/service/impl/` | 结算制证使用模板 + `{{settlementNo}}` 变量 |

### 20.3 变量系统（现有）

| 变量 | 支持位置 | 来源 |
|:-----|:---------|:-----|
| `{{amount}}` | dr/crAmountTemplate, summaryTemplate | 交易金额 |
| `{{summary}}` | summaryTemplate | 流水摘要 |
| `{{counterAccount}}` | summaryTemplate | 对方户名 |
| `{{settlementNo}}` | summaryTemplate | 结算单号 |
| `{{taxAmount}}` | dr/crAmountTemplate | 税额（占位，返回 0）|

### 20.4 差距分析

| 需求 | 现状 | 状态 |
|:-----|:-----|:----:|
| **多维度匹配**（source+businessType+direction） | 仅按 classification 一对一匹配 | ❌ |
| **业务变量**（`{客户名称}` / `{供应商名称}` / `{月份}` 等）| 仅 `{{amount}}` / `{{summary}}` 等 4 个 | ❌ |
| **金额表达式**（`{{amount}} - {{taxAmount}}`） | 只有直接取数 | ❌ |
| **辅助核算挂载**（客户/供应商/部门/员工） | BusinessDocServiceImpl 手动复制 assistJson，模板引擎未支持 | ❌ |
| **辅助核算强校验** | 无 | ❌ |
| **BusinessDocServiceImpl 接入** | 7 种单据硬编码 `DOC_VOUCHER_SUBJECTS` | ❌ |
| **TaxService 生成凭证接入** | `generateVoucherFromInvoice` 硬编码 1122/5001/2221.01 | ❌ |
| **期末自动结转模板**（损益/增值税/汇兑） | 不存在 | ❌ |
| **种子模板覆盖** | 仅 9 条（A 类 5 + 核销 2 + 结算 2） | ⚠️ 部分 |

### 20.5 模板匹配引擎设计

#### 匹配维度（目标）

```
匹配优先级:
  1. source + businessType + direction  (精确匹配)
  2. source + businessType              (业务类型匹配)
  3. classification                      (银行流水分类，兼容现有)
  4. 兜底: 固定科目映射                  (种子模板)
```

#### TemplateContext

```java
class TemplateContext {
    String source;          // BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE
    String businessType;    // RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ...
    String direction;       // in / out
    String classification;  // bank_fee / interest_income / ...
    Long customerId;        // 客户 ID（辅助核算用）
    Long vendorId;          // 供应商 ID
    Long deptId;            // 部门 ID
    Long employeeId;        // 员工 ID
    BigDecimal amount;
    BigDecimal taxAmount;
    String period;          // YYYYMM
    String counterpartyName;
    String summary;
    Map<String, Object> variables;  // 扩展变量
}
```

#### Entity 扩展字段

```sql
-- t_voucher_template 新增
ALTER TABLE t_voucher_template ADD COLUMN source VARCHAR(30);
ALTER TABLE t_voucher_template ADD COLUMN business_type VARCHAR(30);
ALTER TABLE t_voucher_template ADD COLUMN direction VARCHAR(10);
ALTER TABLE t_voucher_template ADD COLUMN match_priority INT DEFAULT 0;

-- t_voucher_template_line 新增
ALTER TABLE t_voucher_template_line ADD COLUMN assist_type VARCHAR(30);
ALTER TABLE t_voucher_template_line ADD COLUMN assist_required BOOLEAN DEFAULT FALSE;
```

### 20.6 五类模板匹配规则

#### 20.6.1 资金与出纳类（source=BANK_STMT）

| 场景 | 匹配条件 | 借方科目 | 贷方科目 |
|:-----|:---------|:---------|:---------|
| 客户收款 | classification=business_receipt | 1002 银行存款 | 1122 应收账款 |
| 支付供应商 | classification=business_payment | 2202 应付账款 | 1002 银行存款 |
| 内部调拨 | classification=internal_transfer | 1002-目标户 | 1002-源户 |
| 银行手续费 | classification=bank_fee | 6602.01 财务费用 | 1002 银行存款 |
| 利息收入 | classification=interest_income | 1002 银行存款 | 6602.02 利息收入 |
| 缴税 | classification=tax_payment | 2221 应交税费 | 1002 银行存款 |
| 社保缴费 | classification=social_security | 2211 应付职工薪酬 | 1002 银行存款 |
| 保险费用 | classification=insurance_fee | 6602.06 保险费 | 1002 银行存款 |

#### 20.6.2 往来与结算类（source=BUSINESS_DOC）

| 场景 | businessType | 借方科目 | 贷方科目 |
|:-----|:-------------|:---------|:---------|
| 收款单 | RECEIPT | 1002 银行存款 | 1122 应收账款 |
| 付款单 | PAYMENT | 2202 应付账款 | 1002 银行存款 |
| 报销单 | EXPENSE | 6602 费用 | 1002 银行存款 |
| 采购发票 | INVOICE_IN | 1403 库存商品 | 2202 应付账款 |
| 销售发票 | INVOICE_OUT | 1122 应收账款 | 6001 主营业务收入 |
| 其他应收 | OTHER_RECEIVABLE | 1221 其他应收款 | 1002 银行存款 |
| 其他应付 | OTHER_PAYABLE | 1002 银行存款 | 2241 其他应付款 |

#### 20.6.3 费用与薪酬类（source=PERIOD_CLOSE + BUSINESS_DOC）

| 场景 | 触发 | 借方科目 | 贷方科目 |
|:-----|:-----|:---------|:---------|
| 费用报销 | businessType=EXPENSE + 员工匹配 | 管理/销售费用 | 其他应付款/银行存款 |
| 计提工资 | 结账触发 | 管理/销售费用-工资 | 应付职工薪酬-工资 |
| 计提社保 | 结账触发 | 管理/销售费用-社保/公积金 | 应付职工薪酬-社保/公积金 |
| 缴纳税金 | 结账触发 | 应交税费-未交增值税等 | 银行存款 |

#### 20.6.4 资产与摊销类（source=PERIOD_CLOSE）

| 场景 | 触发 | 借方科目 | 贷方科目 |
|:-----|:-----|:---------|:---------|
| 固定资产折旧 | 结账→折旧步骤 | 管理/销售费用-折旧费 | 累计折旧 |
| 无形资产摊销 | 结账→摊销步骤 | 管理费用-无形资产摊销 | 累计摊销 |
| 长期待摊摊销 | 结账→摊销步骤 | 管理/销售费用 | 长期待摊费用 |

#### 20.6.5 期末自动化结转（source=PERIOD_CLOSE）

| 场景 | businessType | 逻辑 |
|:-----|:-------------|:-----|
| 损益结转 | PROFIT_LOSS_CLOSE | 取各收入/费用科目余额，反向结转至本年利润 |
| 增值税结转 | VAT_CLOSE | 销项-进项-已交，正数结转至未交增值税 |
| 汇兑损益 | FX_CLOSE | 按期末汇率重估外币余额，差额入财务费用 |

### 20.7 架构调用关系

```
BankStatementService
  └─autoGenerate()
      └─AutoGenerationService.autoGenerate()
          └─ TemplateMatcher.match(context)
              └─ VoucherTemplateService.matchByClassification()
                  └─ generateVoucherFromTemplate(template, lines, context)
                      ├─ TemplateEngine.resolveSummary("付{供应商名称}货款", context)
                      ├─ TemplateEngine.resolveAmount("{{amount}} - {{taxAmount}}", context)
                      └─ 分录插入 + 辅助核算挂载

BusinessDocServiceImpl
  └─generateVoucher()
      └─ TemplateMatcher.match(source=BUSINESS_DOC, businessType=RECEIPT)
          └─ (同上模板引擎)            [待实现]

TaxService
  └─generateVoucherFromInvoice()
      └─ TemplateMatcher.match(source=INVOICE, businessType=INVOICE_OUT)
          └─ (同上模板引擎)            [待实现]

PeriodCloseService
  └─close()
      └─ TemplateMatcher.match(source=PERIOD_CLOSE, businessType=PROFIT_LOSS_CLOSE)
          └─ (同上模板引擎)            [待实现]
```

### 20.8 实施路径

| 批次 | 任务 | 工时 | 优先级 |
|:-----|:-----|:----:|:------:|
| **P0-1** | `TemplateEngine` 变量替换引擎 + `AmountExpressionResolver` 四则运算 | 6h | P0 |
| **P0-2** | `TemplateMatcher` 多维匹配引擎 | 4h | P0 |
| **P0-3** | Entity 扩展字段 + V48 迁移 | 2h | P0 |
| **P0-4** | `AutoGenerationService.resolveAmount/resolveSummary` 改用新引擎 | 2h | P0 |
| **P1-1** | `BusinessDocServiceImpl.generateVoucher()` 改为查模板 | 4h | P1 |
| **P1-2** | `TaxService.generateVoucherFromInvoice()` 改为查模板 | 2h | P1 |
| **P1-3** | 前端模板编辑支持 source/businessType/assistType | 3h | P1 |
| **P1-4** | 辅助核算写入 + 强校验拦截 | 3h | P1 |
| **P2-1** | 新增 15+ 种子模板（V49 迁移） | 2h | P2 |
| **P2-2** | 期末结账触发模板制证 | 3h | P2 |
| **P2-3** | 删除所有残留硬编码 | 1h | P2 |

## 21. 实现状态总览

### 21.1 已实现（✅）

| 模块 | 子模块 | 关键文件 |
|:-----|:-------|:---------|
| 项目骨架 | 前端/后端/DB/CI | pom.xml, docker-compose, 47 个迁移 |
| 系统管理 | 科目/期间/凭证类型/摘要/配置 | system module 全 |
| RBAC | 用户/角色/菜单/权限/审计日志 | system module 全 |
| 凭证管理 | 创建/审核/记账/红冲/查询 | VoucherService + Controller + 12 单测 |
| 凭证状态机 | VoucherStateMachineService | ✅ 4态守卫, V47 迁移 |
| 银行流水 | 导入/列名映射/分类/工作台/A/B/C路由 | BankStatementService(744行) + AutoGenerationService(665行) |
| 分类规则 | 8种子规则/CRUD/优先级/匹配引擎 | ClassificationRuleService |
| 智能分类 | 规则引擎+兜底启发式+AI异步 | FallbackHeuristicService + AiRabbitMQ |
| 业务单据 | 7单据类型/凭证生成/编号 | BusinessDocService(520行) |
| 往来核销 | L1-L6匹配/FIFO/容差/预收预付 | ReconciliationService(1109行) |
| 销售发票基础设施 | InvoiceStatus 常量/V46迁移/Entity注解 | 已完成 |
| 费用报销 | 员工档案/报销单/自动匹配/凭证生成 | ExpenseReimbursementService |
| 前端框架 | 路由/布局/权限指令/API封装 | 12视图目录, 50+路由 |

### 21.2 部分实现（⚠️）

| 模块 | 缺什么 |
|:-----|:-------|
| 销售发票状态机Service | `OutputInvoiceStateMachineService` 未实现（仅常量+Entity+迁移） |
| P24 审计追踪 | `StatusChangeAspect` 已创建但仅标注 2 个 Entity，尚缺 `ReceivableEntity`/`PayableEntity` 等 |
| P20 AR/AP Service | `ArapStatus` 常量和迁移已落地，但 `ReceivableService.confirm()` / `markSettled()` / `reverse()` 未实现 |
| 期初建账 | 前端有 `beginning-balance/` 视图目录，但后端 Service 未完整实现 |
| 期末结账 | `PeriodCloseService` 存在但结账检查项未完整 |
| 数据权限 | `DataPermissionInterceptor` 是个 scaffolding，未注入 SQL |
| AI 服务 | Python FastAPI 骨架存在，RabbitMQ 集成存在，但实际 OCR/embedding 未上线 |
| 税务申报 P18 | `approveDeclaration` / `rejectDeclaration` 存在但申报→凭证自动生成未实现 |
| 银企对账 P14 | 匹配/确认端点存在，余额调节表未完整实现 |

### 21.3 未启动（❌）

| 模块 | 说明 |
|:-----|:------|
| P23 强制校验 | 统一拦截器未立项 |
| P25 账期控制 | 跨期标记 + 账期逻辑未立项 |
| 预算控制 | 单据保存时执行预算检查未实现 |
| 报表导出 | 仅有基础结构，EasyExcel 导出未实现 |
| 财务报表物化视图 | PostgreSQL 物化视图未创建 |

---

> **引用文档索引**：
> - `基于Web财务软件的项目说明书.md` — 主项目说明书，高层架构
> - `docs/需求分析书_发票与凭证状态机_V1.0.md` — 发票/凭证状态机需求
> - `docs/需求分析书_银行流水导入分类_V1.0.md` — 银行流水导入需求
> - `docs/specs/P20-arap-state-machine-spec.md` — AR/AP 状态机 SPEC
> - `docs/specs/P21-sales-invoice-state-machine.md` — 销售发票状态机 SPEC
> - `docs/specs/P22-voucher-state-machine.md` — 凭证状态机 SPEC
> - `docs/specs/P24-audit-tracking.md` — 审计追踪 SPEC
> - `docs/开发计划书.md` — 开发计划
