# P-TRANSFER SPEC — 转账单（资金调拨单）

> 版本: 1.0
> 状态: 待审核
> 对应的 P 系列编号: 新增（无已有编号）
> 最后更新: 2026-08-18
> 所属模块: sme-arap（业务单据体系扩展）
> 协议分支: sme

> **test_ref**：BusinessDocServiceImplTest, BusinessDocRestContractTest
---

## 业务背景

当前 `t_business_doc` 支持的 7 种单据类型：`RECEIPT`（收款）、`PAYMENT`（付款）、`EXPENSE`（报销）、`INVOICE_IN`（进项）、`INVOICE_OUT`（销项）、`OTHER_RECEIVABLE`（其他应收）、`OTHER_PAYABLE`（其他应付）。

新增 **第 8 种**：`TRANSFER`（转账单）。用于记录企业内部资金调拨（基本户转一般户、支付宝提现到银行卡、公司账户转个人备用金等），不涉及损益，仅改变资产存放形态。

**为什么用业务单据而不是凭证：** 转账是资金流转事件，会计需要看到"什么时间、什么账户转什么账户、多少金额"，需要和银行流水、凭证可追溯关联。

---

## SDD 契约

### 1. 输入契约

**创建转账单 — `POST /api/v1/finance/business-doc`**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|:----:|------|------|
| docType | String | ✅ | = "TRANSFER" | 固定值 |
| docDate | LocalDate | ✅ | ≤ today | 转账日期 |
| amount | BigDecimal | ✅ | > 0 | 转账金额（绝对值） |
| summary | String | ✅ | ≤ 200 | 摘要（如"基本户转一般户"） |
| sourceAccount | Long | ✅ | 科目 ID，必须为资产类科目（1001/1002/1012/1018/1021/1031） | 转出账户（被借出方的对应账户，即"借方科目"） |
| targetAccount | Long | ✅ | 科目 ID，必须为资产类科目 | 转入账户（"贷方科目"） |

**凭证分录结构（转账单）：**
```
借：源账户（sourceAccount）    — amount
贷：目标账户（targetAccount）  — amount
```
> 与收款/付款单不同，转账单的分录中 **debit/credit 方向相反**：转出方记贷方，转入方记借方。
> 这是因为"转出"意味着该账户资金减少（贷方），"转入"意味着该账户资金增加（借方）。

**银行流水自动生成转账单 — 场景：** 银行流水分类为 `INTERNAL_TRANSFER` 时，自动创建转账单 DRAFT。

**状态约束：** 初始状态 = `DRAFT`（不允许其他初始状态）。

---

### 2. 输出契约

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| docNo | String | 格式: `ZC+YYYYMM+0001`（如 `ZC2024080001`） |
| docType | String | "TRANSFER" |
| amount | BigDecimal | 转账金额 |
| status | String | 当前状态 |
| sourceAccount | Long | 转出账户科目 ID |
| targetAccount | Long | 转入账户科目 ID |
| sourceAccountName | String | 转出科目名称（VO 填充） |
| targetAccountName | String | 转入科目名称（VO 填充） |
| voucherId | Long | 生成凭证后的关联 ID |
| voucherNo | String | 生成凭证后的凭证编号 |

**前端展示标签：** 列表页/详情页显示"转账单"。

---

### 3. 状态流转

```
DRAFT
  └─ submit() → SUBMITTED
       ├─ approve() → APPROVED
       ├─ reject() → REJECTED
       ├─ void() → VOID
       └─ close() → CLOSED
```

**已审批单据生成凭证：** `POST /api/v1/finance/business-doc/{id}/voucher`
```
APPROVED → VOUCHERED
```

**负向断言：**
- DRAFT 不能直接跳到 APPROVED（必须经过 SUBMITTED）
- 已生成凭证的单据（VOUCHERED）不能提交/审批/作废
- 转账金额不能为 0 或负数
- sourceAccount 和 targetAccount 不能为同一账户

---

### 4. 异常处理

| 场景 | 错误码 | 错误信息 |
|------|:------:|---------|
| 金额为 0 | 400 | 转账金额必须大于 0 |
| 金额为负数 | 400 | 转账金额不能为负数 |
| 转出=转入 | 400 | 转出账户与转入账户不能相同 |
| 科目非资产类 | 400 | 科目 {code} 不是资产类科目，不能用于转账 |
| 科目不存在 | 400 | 科目 {code} 不存在 |
| 期间已关闭 | 400 | 会计期间不可操作: {period} |

---

## BDD 验收标准

### 场景 1：创建转账单（L2 Controller）
- **Given** 已登录用户，存在"基本户"（1002.01）和"一般户"（1002.02）
- **When** 创建转账单：amount=10000，sourceAccount=基本户，targetAccount=一般户
- **Then** 创建成功，docNo = "ZC2024080001"，状态=DRAFT
- **And** 不会生成凭证

### 场景 2：审批后生成凭证（L2 集成，🟡 状态变更 + 🟡 凭证生成）
- **Given** DRAFT 状态的转账单
- **When** submit() → 状态变为 SUBMITTED
- **And** approve() → 状态变为 APPROVED
- **And** generateVoucher()
- **Then** 状态变为 VOUCHERED
- **And** 生成凭证：借 一般户 / 贷 基本户，金额相等
- **And** doc.voucherNo 被填充

### 场景 3：负向断言 — 转出=转入（L2 Controller）
- **Given** 创建转账单时 sourceAccount=targetAccount=基本户
- **When** 提交请求
- **Then** 返回 400，错误信息包含"不能相同"

### 场景 4：负向断言 — 金额为负（L2 Controller）
- **Given** 创建转账单时 amount=-1000
- **When** 提交请求
- **Then** 返回 400，错误信息包含"不能为负数"

### 场景 5：银行流水自动生成转账单（L2 集成，🟡 状态变更）
- **Given** 一条银行流水，分类为 `INTERNAL_TRANSFER`，金额=5000，对方="一般户"
- **When** 出纳确认（review）
- **Then** 自动生成转账单 DRAFT，金额=5000，sourceAccount=该流水的账户
- **And** 目标账户（贷方）取自 `auxData.targetAccount` 或默认值
- **And** 银行流水 reviewStatus 变为 PAYMENT_CREATED
- **And** 银行流水的 generatedDocId 指向该转账单

### 场景 6：已凭证化单据不可审批（L2 集成，🟡 状态变更）
- **Given** VOUCHERED 状态的转账单
- **When** 尝试 submit()
- **Then** 返回 400 或忽略（状态不变）

### 场景 7：负向断言 — 非资产类科目（L2 Controller）
- **Given** 创建转账单时 sourceAccount 为损益类科目 ID（如 6001 主营业务收入）
- **When** 提交请求
- **Then** 返回 400，错误信息包含"不是资产类科目"

---

## 数据模型

**不需要新增表或列**，复用 `t_business_doc` 现有结构。

`sourceAccount` 和 `targetAccount` 利用已有的 `t_business_doc.supplier_id` 和 `t_business_doc.customer_id` 字段做复用——但这不是好方案（语义污染）。

**方案 A（推荐）：使用现有 `t_business_doc_entry` 存储账户映射。**

转账单生成凭证时，不依赖 `BusinessDocEntity.supplierId/customerId`，而是通过 `BusinessDocEntryEntity` 存储分录：

```
BusinessDocEntry 分录 1:
  doc_id = {转账单ID}
  subject_id = {targetAccount}  (转入账户 — 借方)
  amount = {转账金额}

BusinessDocEntry 分录 2:
  doc_id = {转账单ID}
  subject_id = {sourceAccount}  (转出账户 — 贷方)
  amount = {转账金额}
```

`sourceAccount` 和 `targetAccount` 从 `BusinessDocEntry` 的两条分录反推。

**方案 B（简化）：新增两个列。**

```sql
-- V136 迁移
ALTER TABLE t_business_doc ADD COLUMN source_account_id BIGINT;
ALTER TABLE t_business_doc ADD COLUMN target_account_id BIGINT;
```

**推荐方案 A**（零 schema 变更），前端直接展示分录的 subject 即可。

---

## 代码改造点

| 文件 | 改动 |
|------|------|
| `BusinessDocServiceImpl.java` | DOC_TYPE_CODE 加 `"TRANSFER", "ZC"`；DOC_VOUCHER_SUBJECTS 加 TRANSFER 条目（特殊处理，不固定科目对）；enrichSummary 对 TRANSFER 不做"付/收"前缀 |
| `BusinessDocServiceImpl.java` | createVoucher() 对 TRANSFER 类型走特殊逻辑：source=贷方，target=借方 |
| `BankStatementServiceImpl.java` | INTERNAL_TRANSFER 路由：当前生成 `BusinessDoc` PAYMENT，改为生成 TRANSFER |
| `AutoGenerationService.java` | `docType()` 方法返回 TRANSFER（已有 case，需确认） |
| `frontend/business-doc/BusinessDocEdit.vue` | DOC_TYPE_LABELS 加 "转账单"，docType 选择下拉加入 TRANSFER，转账单特殊表单（只显示转出/转入账户 + 金额） |
| `frontend/business-doc/BusinessDocList.vue` | DOC_TYPE_LABELS 同步加入 |
| `frontend/business-doc/BusinessDocDetail.vue` | TRANSFER 类型特殊展示（转出→转入账户名） |

---

## 测试计划

| 场景 | 层级 | 文件 |
|------|:----:|------|
| 场景 1-4, 7 | L2 Controller | `BusinessDocServiceImplTest` |
| 场景 2, 5, 6 | L2 集成 (Testcontainers) | `BusinessDocIntegrationTest` |
| 前端表单验证 | L1 unit | `BusinessDocEdit.test.ts` |

---

## 12 项检查清单

- [ ] 所有业务单据初始状态 = DRAFT
- [ ] 审核流程由人工完成，系统不允许自动审核/自动批准
- [ ] 涉及金额用 BigDecimal，不用 double/float
- [ ] 涉及多表写操作加 @Transactional(rollbackFor = Exception.class)
- [ ] 数据隔离已考虑（每条数据带 enterprise_id）
- [ ] 状态变更加 @StatusChangeable 注解
- [ ] SDD 四契约完整（输入/输出/状态流转/异常处理）
- [ ] 每个 BDD 场景标注了测试层级（L1-L5）
- [ ] 高风险路径标注了 🟡 并指定 Testcontainers
- [ ] 状态机场景包含负向断言
- [ ] 对称模块已检查（转账单无对称模块，银行流水 INTERNAL_TRANSFER 已存在）
- [ ] 前端路由注册在 sme 分支

---

```yaml
states:
  DRAFT:
    entry: "转账单创建"
    transitions:
      - submit → SUBMITTED
      - void → VOID
      - close → CLOSED
  SUBMITTED:
    entry: "转账单已提交"
    transitions:
      - approve → APPROVED
      - reject → REJECTED
      - void → VOID
  APPROVED:
    entry: "转账单已审批"
    transitions:
      - generateVoucher → VOUCHERED
  VOUCHERED:
    entry: "转账单已生成凭证"
    transitions: []
  REJECTED:
    entry: "转账单已驳回"
    transitions:
      - submit → SUBMITTED
  CLOSED:
    entry: "转账单已关闭"
    transitions: []

doc_type: TRANSFER
doc_no_prefix: ZC
voucher_entries:
  - direction: debit
    account_ref: target_account_id
    amount: amount
  - direction: credit
    account_ref: source_account_id
    amount: amount

acceptance_tests:
  - L2: 创建转账单（成功）
  - L2: 审批后生成凭证
  - L2: 转出=转入 拒绝
  - L2: 金额为负 拒绝
  - L2: 银行流水自动生成转账单
  - L2: 已凭证化不可审批
  - L2: 非资产科目 拒绝

constraints:
  - amount > 0
  - source_account_id != target_account_id
  - source/target 必须为资产类科目（10xx）
  - 不生成应收/应付关系
  - 不计入损益
```