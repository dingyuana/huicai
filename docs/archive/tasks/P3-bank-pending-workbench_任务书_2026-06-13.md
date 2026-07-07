# P3 任务书：C 类待处理工作台 + 应收应付待处理

> 日期：2026-06-13 | 任务 ID：P3-BANK-PENDING-WORKBENCH
> 上游文档：Go 版 `internal/service/bank_txn_review_service.go:279-358` `ProcessManual` 函数（已读）
> 关联：H1 任务书（P3-BANK-IMPORT-ACTIVE）的 C 类分支
> 老丁原话："其他应收应付款的待处理的流水"

## 目标

新增 C 类流水待处理工作台 API：
- 列表查询：`GET /api/finance/bank-statement/pending` —— C 类未处理流水
- 人工选路：`POST /api/finance/bank-statement/{id}/process-manual` —— 接收 "第一类" / "第二类" 决策 + paymentType

对照 Go 版 `ProcessManual`（279-358 行）实现。

## 现状

C 类流水在 `importFromCsv` 导入时（改造后 H1 流程）**只 log 不处理**，状态 `review_status='UNCONFIRMED'`，**无前端工作台、无 API**。

## 实施步骤

### Step 1：新增 Service 方法 `processManual`

**位置**：`BankStatementService` 接口 + `BankStatementServiceImpl` 实现

**Go 版对照**（ProcessManual 279-358 行）：
1. 校验 txn 状态为 manual_pending
2. switch action:
   - `action="第一类"` → `GenerateFromBankTxn`（直接制证草稿）
   - `action="第二类"` + `paymentType` → `CreateFromBankTransaction`（PaymentEntry 草稿 = Java 版 `autoGenerate` for B 类）
   - default → 返回 skip

**Java 版实现**：

```java
// BankStatementService.java 新增
ProcessManualResult processManual(Long statementId, String action, String paymentType, Long userId);

// BankStatementServiceImpl.java 新增
@Override
public ProcessManualResult processManual(Long statementId, String action, String paymentType, Long userId) {
    BankStatementEntity stmt = statementMapper.selectById(statementId);
    if (stmt == null) throw BusinessException.notFound("银行流水不存在");
    if (!"UNCONFIRMED".equals(stmt.getReviewStatus())) {
        throw new BusinessException(400, "该流水非待处理状态, reviewStatus=" + stmt.getReviewStatus());
    }

    boolean ok = false;
    switch (action.toUpperCase()) {
        case "A":    // 第一类
        case "第一类":
            // 直接走 autoGenerate 走 A 类分支
            ok = autoGenerationService.autoGenerate(statementId, userId);
            break;
        case "B":    // 第二类
        case "第二类":
            if (StrUtil.isBlank(paymentType)) {
                throw new BusinessException(400, "第二类必须指定 paymentType (RECEIPT/PAYMENT)");
            }
            // 修改 classification 后走 autoGenerate 走 B 类分支
            stmt.setClassification(guessBClassFromPaymentType(paymentType));
            statementMapper.updateById(stmt);
            ok = autoGenerationService.autoGenerate(statementId, userId);
            break;
        default:
            throw new BusinessException(400, "action 必须是 第一类/A 或 第二类/B");
    }
    return new ProcessManualResult(statementId, ok, stmt.getGeneratedVoucherId(), stmt.getGeneratedDocId());
}
```

### Step 2：新增 Controller 端点

**位置**：`BankStatementController.java`

```java
@GetMapping("/pending")
public R<PageResult<BankStatementVO>> listPending(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) String direction
) { ... }

@PostMapping("/{id}/process-manual")
public R<ProcessManualResult> processManual(
    @PathVariable Long id,
    @RequestBody ProcessManualRequest req
) { ... }
```

### Step 3：ProcessManualRequest DTO

```java
@Data
public class ProcessManualRequest {
    @NotBlank
    private String action;        // "第一类" / "A" / "第二类" / "B"
    private String paymentType;    // B 类必填: "RECEIPT" / "PAYMENT"
    private String paymentSubType; // 业务付款单: SALARY/TAX/SOCIAL/EXPENSE
}
```

### Step 4：单测

新增 `BankStatementProcessManualTest.java`：

| Test | 覆盖 |
|---|---|
| `testProcessManual_actionA_生成凭证` | action=A 走第一类 |
| `testProcessManual_actionB_RECEIPT_生成收款单` | action=B + RECEIPT |
| `testProcessManual_actionB_PAYMENT_SALARY_生成付款单` | action=B + PAYMENT + SALARY |
| `testProcessManual_已确认流水_拒绝` | 状态校验 |
| `testProcessManual_actionB_缺paymentType_400` | 参数校验 |
| `testListPending_C类分页` | 分页查询 |

## 验收标准

1. 6 单测全绿
2. 真实路径：插入 1 条 classification=NULL 流水 + review_status=UNCONFIRMED → 调 `/pending` 能查到 → 调 `/process-manual` action=A → `t_voucher` 多 1 行 + t_bank_statement.generated_voucher_id 非 NULL
3. `./mvnw test` 不退化

## 不做的事

- ❌ 不改前端（C 类工作台页面在 P3 后期）
- ❌ 不改 ClassificationRule / autoGenerate 主流程
- ❌ 不实现批量 process-manual
- ❌ 不做权限校验（沿用现有 dev 环境 stub）

## 风险

| 风险 | 应对 |
|---|---|
| review_status 字段在 ENUM 中不存在 UNCONFIRMED | P1 V17 已加 3 值：PENDING/UNCONFIRMED/CONFIRMED/RECLASSIFIED（核对 P1 文档）—— 实际是 UNCONFIRMED |
| process-manual 后 C 类二次处理 | 加 review_status != UNCONFIRMED 校验 |

## 提交

1. 修改 `BankStatementService.java` 接口
2. 修改 `BankStatementServiceImpl.java` 实现
3. 修改 `BankStatementController.java` 加 2 端点
4. 新增 `ProcessManualRequest.java` / `ProcessManualResult.java` DTO
5. 新增 `BankStatementProcessManualTest.java` 单测
6. **commit 永远 Hermes 亲手**
