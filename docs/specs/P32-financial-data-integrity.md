# P32 SPEC — 财务数据完整性与并发控制增强

&gt; 状态：**实施中** | 优先级：高（P32）
&gt; 依据：财务系统核心数据完整性要求
&gt; 目标：修复 P0-P2 级数据完整性问题，包括乐观锁、摘要追溯、数据一致性校验
&gt; 工期：2 批交付

---

## 0. 问题背景与修复范围

### 0.1 发现的核心问题 (2026-06-27)

| 优先级 | 问题分类 | 问题描述 | 风险等级 |
|--------|----------|----------|----------|
| **P0** | 并发安全 | 销售发票、业务单据、凭证实体无 `@Version` 乐观锁，并发审核可能导致重复制证 | 🔴 高 |
| **P0** | 审计追溯 | 发票审核生成的凭证/业务单据摘要不含发票号，SQL 无法模糊查询追溯 | 🟠 中 |
| **P1** | 并发安全 | 银行流水无乐观锁，重复生成凭证风险 | 🟠 中 |
| **P2** | 数据一致性 | 无业财对账 API，无法检测 status=VOUCHERED 但 voucher_id IS NULL 等异常 | 🟠 中 |
| **P2** | 编号连续性 | 凭证号/单据号仅依赖 Redis，崩溃后可能重号，缺 DB SEQUENCE 备份 | 🟡 低 |

### 0.2 修复范围

```
├─ P0: 核心实体加 @Version 乐观锁
│   ├─ OutputInvoiceEntity (销售发票)
│   ├─ BusinessDocEntity (业务单据)
│   └─ VoucherEntity (凭证)
│
├─ P0: enrichSummary 增加发票号后缀
│   └─ BusinessDocServiceImpl.enrichSummary()
│
├─ P1: 银行流水加乐观锁
│   └─ BankStatementEntity
│
└─ P2: 数据一致性健康检查 API
    └─ FinanceHealthController + 多个 IntegrityChecker
```

---

## 1. P0 修复：核心实体乐观锁

### 1.1 设计原则

**为什么用乐观锁？**
- 财务单据审核是低频操作，冲突概率低
- 乐观锁性能好，无死锁风险
- MyBatis-Plus 原生支持 `@Version` 注解

**冲突处理策略**：
```java
try {
    mapper.updateById(entity);
} catch (OptimisticLockingFailureException e) {
    throw BusinessException.conflict("数据已被他人修改，请刷新后重试");
}
```

### 1.2 实体变更清单

| 实体 | 加锁位置 | 新增字段 | 影响 |
|------|----------|----------|------|
| `OutputInvoiceEntity` | 审核 confirm()/reject() | `private Integer version;` | ✅ 防止并发重复审核 |
| `BusinessDocEntity` | 生成凭证、红冲、删除 | `private Integer version;` | ✅ 防止重复制证 |
| `VoucherEntity` | 过账、反过账、红冲 | `private Integer version;` | ✅ 防止借贷不平并发修改 |
| `BankStatementEntity` | 审核、生成凭证 | `private Integer version;` | ✅ 防止重复生成凭证 |

### 1.3 实体代码示例

```java
// BusinessDocEntity.java
@TableName("t_business_doc")
public class BusinessDocEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // ... 其他字段 ...

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
```

---

## 2. P0 修复：凭证摘要包含发票号

### 2.1 需求

从发票生成的凭证，摘要必须包含发票号，便于审计时通过 `LIKE '%25922000000054246714%'` 直接追溯原始发票。

### 2.2 实现位置

**文件**: `BusinessDocServiceImpl.enrichSummary()`

**变更前**:
```java
private String enrichSummary(BusinessDocEntity entity) {
    String base = entity.getSummary() != null && !blank
        ? entity.getSummary()
        : "生成自单据 " + entity.getDocNo();
    String partyName = resolvePartyName(entity);
    if (partyName == null || blank) return base;
    String prefix = SUPPLIER_DOC_TYPES.contains(entity.docType) ? "付" : "收";
    return prefix + partyName + "-" + base;
}
```

**变更后**:
```java
private String enrichSummary(BusinessDocEntity entity) {
    String base = entity.getSummary() != null && !StrUtil.isBlank(entity.getSummary())
        ? entity.getSummary()
        : "生成自单据 " + entity.getDocNo();
    String partyName = resolvePartyName(entity);
    if (partyName != null && !StrUtil.isBlank(partyName)) {
        String prefix = SUPPLIER_DOC_TYPES.contains(entity.docType) ? "付" : "收";
        base = prefix + partyName + "-" + base;
    }
    // P32: 发票号后缀，用于审计追溯
    if (entity.getInvoiceNo() != null && !StrUtil.isBlank(entity.getInvoiceNo())) {
        base += "[" + entity.getInvoiceNo() + "]";
    }
    return base;
}
```

### 2.3 影响范围

- 从 SalesInvoiceImportService 导入的发票，生成的凭证自动带发票号
- 从 OutputInvoiceStateMachineServiceImpl.confirm() 生成的业务单 + 凭证，自动带发票号
- 所有 INVOICE_OUT 类型业务单的摘要均带发票号

---

## 3. P2 修复：数据一致性健康检查 API

### 3.1 设计目标

提供一个统一的健康检查入口，支持：
- 人工触发检查（财务主管）
- 定时任务每天凌晨扫描
- 检查结果导出 Excel

### 3.2 检查项清单

| 检查 ID | 检查名称 | SQL 逻辑 | 严重程度 |
|---------|----------|----------|----------|
| CHK-001 | 发票状态与凭证一致性 | `SELECT * FROM t_output_invoice WHERE status IN ('VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED') AND voucher_id IS NULL` | 🔴 P0 |
| CHK-002 | 业务单据状态与凭证一致性 | `SELECT * FROM t_business_doc WHERE status = 'VOUCHERED' AND voucher_id IS NULL` | 🔴 P0 |
| CHK-003 | 应收金额与已核销/未核销一致性 | `SELECT * FROM t_receivable WHERE ABS(amount - settled_amount - unsettled_amount) &gt; 0.01` | 🟠 P1 |
| CHK-004 | 凭证借贷平衡检查 | `SELECT v.id, v.voucher_no, v.total_debit, v.total_credit FROM t_voucher v LEFT JOIN (SELECT voucher_id, SUM(debit) d, SUM(credit) c FROM t_voucher_entry GROUP BY voucher_id) e ON v.id = e.voucher_id WHERE ABS(v.total_debit - v.total_credit) &gt; 0.01 OR ABS(COALESCE(e.d, 0) - COALESCE(e.c, 0)) &gt; 0.01` | 🟠 P1 |
| CHK-005 | 发票号唯一性校验 | `SELECT invoice_no, COUNT(*) FROM t_output_invoice GROUP BY invoice_no HAVING COUNT(*) &gt; 1` | 🟠 P1 |
| CHK-006 | 银行流水状态与生成结果一致性 | `SELECT * FROM t_bank_statement WHERE review_status IN ('voucher_generated', 'payment_created') AND generated_voucher_id IS NULL AND generated_doc_id IS NULL` | 🟡 P2 |
| CHK-007 | 期间关账后仍有修改 | （待 P22 状态机完成后补充） | 🟡 P2 |

### 3.3 API 设计

**端点**: `POST /api/v1/finance/health/integrity`

**请求参数**:
```json
{
  "checks": ["CHK-001", "CHK-002", "ALL"],
  "period": "202606",
  "autoFix": false
}
```

**响应格式**:
```json
{
  "code": 200,
  "message": "数据一致性检查完成",
  "data": {
    "totalChecks": 7,
    "passed": 5,
    "failed": 2,
    "checkResults": [
      {
        "checkId": "CHK-001",
        "checkName": "发票状态与凭证一致性",
        "status": "PASSED",
        "affectedRows": 0,
        "severity": "P0"
      },
      {
        "checkId": "CHK-002",
        "checkName": "业务单据状态与凭证一致性",
        "status": "FAILED",
        "affectedRows": 3,
        "severity": "P0",
        "details": [
          {
            "docId": 123,
            "docNo": "FPS202606001",
            "status": "VOUCHERED",
            "voucherId": null
          }
        ]
      }
    ],
    "checkTime": "2026-06-27T14:30:00",
    "durationMs": 245
  }
}
```

### 3.4 文件结构

```
backend/src/main/java/com.huicai.module/finance/
├── controller/
│   └── FinanceHealthController.java
├── service/
│   └── impl/
│       ├── FinanceIntegrityService.java
│       └── checker/
│           ├── InvoiceVoucherIntegrityChecker.java
│           ├── BusinessDocVoucherIntegrityChecker.java
│           ├── ReceivableAmountIntegrityChecker.java
│           ├── VoucherBalanceIntegrityChecker.java
│           └── BankStatementIntegrityChecker.java
└── dto/
    └── IntegrityCheckResult.java
```

---

## 4. Flyway 迁移

### V63__add_version_columns.sql

```sql
-- V63: 核心财务实体加乐观锁 version 字段
-- P32: 财务数据完整性与并发控制增强

-- t_output_invoice
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_output_invoice.version IS '乐观锁版本号';

-- t_business_doc
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_business_doc.version IS '乐观锁版本号';

-- t_voucher
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_voucher.version IS '乐观锁版本号';

-- t_bank_statement
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_bank_statement.version IS '乐观锁版本号';
```

---

## 5. 验收标准

| # | 验收项 | 模块 | 优先级 |
|---|--------|------|--------|
| AC1 | OutputInvoiceEntity 加 @Version 乐观锁 | 销售发票 | P0 |
| AC2 | BusinessDocEntity 加 @Version 乐观锁 | 业务单据 | P0 |
| AC3 | VoucherEntity 加 @Version 乐观锁 | 凭证 | P0 |
| AC4 | BankStatementEntity 加 @Version 乐观锁 | 银行流水 | P1 |
| AC5 | enrichSummary 自动加发票号后缀 [发票号] | 业务单据/凭证 | P0 |
| AC6 | 并发重复审核时返回明确错误提示 | 所有状态机 | P0 |
| AC7 | 数据一致性检查 API 完成 CHK-001 ~ CHK-006 | FinanceHealthController | P1 |
| AC8 | Flyway V63 成功执行 | 数据库 | P0 |

---

## 6. 风险与回滚

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| 已有代码依赖旧实体结构，加字段后编译失败 | 中 | 高 | 运行 `mvn compile` 验证所有引用 |
| 加字段后 MyBatis-Plus 自动更新 SQL 不兼容 | 低 | 高 | 检查 `updateById` 后 version 是否自动递增 |
| 并发场景下乐观锁冲突导致用户体验下降 | 低 | 低 | 提供友好错误提示 + 自动重试机制 |

---

## 7. 测试清单

### 7.1 单元测试
- `OutputInvoiceStateMachineServiceImplTest`: 并发 confirm() 测试（2 线程）
- `BusinessDocServiceImplTest`: 并发 generateVoucher() 测试
- `EnrichSummaryTest`: 带/不带发票号的摘要生成

### 7.2 集成测试
- `FinanceIntegrityServiceTest`: 各检查项正常/异常场景
- 全量数据跑批测试（生产数据脱敏后）

---

## 8. 部署顺序

1. ✅ 设计文档（本文档）
2. ✅ Flyway V63 迁移脚本
3. ✅ 实体加 `@Version` 字段
4. ✅ enrichSummary 发票号增强
5. ✅ FinanceHealthController + Checkers
6. ✅ 单元测试更新
7. ✅ 部署后手动执行 health/integrity 验证

---

## 9. 变更日志

| 日期 | 版本 | 内容 | 作者 |
|------|------|------|------|
| 2026-06-27 | v1.0 | 初版，涵盖 P0-P2 数据完整性修复 | 系统审计 |

---

# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: All core entities
module: finance / tax / arap
table: multiple

acronym: P32

contracts:
  - id: P32-C1
    description: "所有核心实体含乐观锁 @Version 字段"
    type: code_review
    target: OutputInvoiceEntity, BusinessDocEntity, VoucherEntity, BankStatementEntity, CashJournalEntity
    assertion: "每类 Entity 均有 @Version 注解的 version 字段"

  - id: P32-C2
    description: "凭证摘要包含发票号，支持 SQL LIKE '%发票号%' 模糊查询"
    type: db_query
    assertion: |
      SELECT summary FROM t_voucher WHERE summary LIKE '%25922000000082010917%'
      → 返回至少 1 条（发票号存在的位置摘要包含该号）

  - id: P32-C3
    description: "健康检查接口检测 status=VOUCHERED 但 voucher_id IS NULL 的异常"
    type: api
    endpoint: GET /api/v1/system/health/integrity
    expected: "200 + checkResults 中包含业务单据完整性检查结果"

  - id: P32-C4
    description: "并发更新时乐观锁正确拦截冲突"
    type: unit_test
    target: OutputInvoiceStateMachineServiceImplTest.testConcurrentConfirmFailsWithOptimisticLock
    assertion: "两个事务同时 confirm 同一发票 → 其中一个抛出 OptimisticLockException"

  - id: P32-C5
    description: "期初余额不重复创建（幂等性）"
    type: unit_test
    target: BeginningBalanceServiceTest.testDuplicateBalanceRejected
    assertion: "同一期间科目再次创建期初 → BusinessException"

acceptance_tests:
  - id: AT-P32-1
    description: "乐观锁 @Version 字段已添加到所有核心实体"
    method: testEntitiesHaveVersion
    status: covered
  - id: AT-P32-2
    description: "摘要含发票号可模糊搜索"
    method: testSummaryContainsInvoiceNo
    status: covered
  - id: AT-P32-3
    description: "健康检查接口正常返回完整性报告"
    method: testHealthIntegrityEndpoint
    status: covered
  - id: AT-P32-4
    description: "并发 confirm 不会重复制证"
    method: testConcurrentConfirmFailsWithLock
    status: covered

constraints:
  - id: C-P32-1
    type: business
    rule: "所有修改操作必须通过带 @Version 的 Entity 执行，不能直接写 UPDATE SQL"
    enforcement: "代码审查 + 禁止 MyBatis XML 直接 UPDATE 关键字段"
  - id: C-P32-2
    type: audit
    rule: "所有写操作记录操作人 + 时间"
    enforcement: "MyBatisPlus 自动填充（createdBy/updatedBy）"

dependencies:
  - spec: P34
    relation: "BusinessDocEntity 的乐观锁版本号与 P34 共享"
  - spec: P22
    relation: "VoucherEntity 的 @Version 字段与 P22 状态机配合"
  - spec: P24
    relation: "审计日志记录补充 P24 的日志内容"

out_of_scope:
  - "数据库级 SEQUENCE 备份方案（编号连续性 - 低优先级）"
  - "业财对账 API 的自动修复能力（只检测不修复）"
