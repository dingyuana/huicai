---
标题: P1 统一核销写路径（execute→SUBMITTED→approve→CONFIRMED）
编号: P30-P1
版本: v2.1 (2026-08-27)
关联PRD: PRD05-02（核销工作台增强）
状态: 开发中
关联SPEC: P30-reconciliation-workbench-enhance.md
test_ref: ReconciliationServiceImplTest(50) + CoreWriteOperationConcurrencyTest(7) + ArapSettlementServiceImpl.approve(P1四测)

## 背景

v2.0（P30-C5）引入 execute() 直接写路径，但存在两条写路径并存：
- 路径A: execute() → 直接扣减金额 + settlement 直置 CONFIRMED（跳过审批）
- 路径B: ArapSettlementService.create(DRAFT) → submit(SUBMITTED) → approve(CONFIRMED)

路径A 是主入口但绕过了人审，违反项目铁律"人是唯一审核主体"。

## 输入契约

ExecuteRequest（沿用 v2.0），无变更。新增 V140 migration：
- t_arap_settlement.source_doc_type VARCHAR(32) — 来源单据类型
- t_arap_settlement.source_doc_id BIGINT — 来源单据ID

## 输出契约

execute() 返回值：
- ReconciliationLogEntity.status = "SUBMITTED"（原为 CONFIRMED）
- ArapSettlementEntity.status = "SUBMITTED"，携带 sourceDocType/sourceDocId
- 不修改目标单据 settled_amount（延迟到 approve）
- 不修改来源单据余额
- 不同步发票状态（延迟到 approve）
- 不标记银行流水 MATCHED（延迟到 approve）

approve(id) 返回值：
- ArapSettlementEntity.status = "CONFIRMED"
- 目标单据 settled_amount 累加，状态同步
- 来源单据（RECEIPT/PAYMENT）余额同步扣减
- 发票状态同步（销项 outputInvoiceStateMachineService + 进项 inputInvoiceStateMachineService）
- 银行流水（bank_txn）标记 MATCHED
- 超额守卫：核销金额 > 未核销余额 → 抛 BusinessException

## 状态流转

```
路径A（P1 修复后）:
execute() → SUBMITTED 核销单（提报，落库）
  └→ 人工审批 → approve(id) → CONFIRMED（金额扣减生效 + 发票同步 + 来源同步）

路径B（原有审批流，保持不变）:
create(DRAFT) → submit(SUBMITTED) → approve(CONFIRMED) → cancel()

反核销:
reverse(settlementId) → 反向核销单 + restoreUnsettledAmount()（含金额回滚 + 发票状态回滚）
```

## 异常处理

| 场景 | 异常 | 消息 |
|------|------|------|
| execute() 目标单据状态非法 | BusinessException | 状态不允许核销 |
| execute() 金额超未核销余额 | BusinessException | 核销金额超过未核销余额 |
| approve() 核销单状态非法 | BusinessException | 核销单状态不允许审批 |
| approve() 单据状态非法 | BusinessException | 仅已审批状态的业务单据可核销 |
| approve() 金额超未核销余额 | BusinessException | 核销金额超过未核销余额 |
| approve() 版本冲突 | OptimisticLockingFailureException | BusinessDoc确认版本冲突 |

## BDD

### 场景 A: execute 提报不生效
- Given 用户提交核销请求(目标单据 APPROVED, 未核销 500, 核销 200)
- When execute()
- Then 目标单据 settled_amount 不变(仍为0)
- And 来源单据 unsettled_amount 不变
- And 核销单状态 SUBMITTED
- And ReconciliationLog.status = SUBMITTED

### 场景 B: approve 生效
- Given 存在 SUBMITTED 核销单(金额200)
- When approve(id)
- Then 目标单据 settled_amount = 200, unsettled_amount = 300
- And 核销单状态 CONFIRMED
- And 来源单据(如 RECEIPT)同步扣减200

### 场景 C: approve 超额拦截
- Given SUBMITTED 核销单(金额9999, 目标未核销200)
- When approve(id)
- Then 抛 BusinessException("核销金额超过未核销余额")
- And 无任何写操作发生

### 场景 D: approve 同步发票状态
- Given SUBMITTED 核销单(目标单据 INVOICE_OUT, invoiceId=888)
- When approve(id)
- Then outputInvoiceStateMachineService.onReconciliationUpdate(888, ...) 被调用

### 场景 E: 无 period 兜底
- Given ExecuteRequest period=null, 目标单据 period=202607
- When execute()
- Then 核销单 period=202607（必建，不存在"跳过核销单"场景）

### 场景 F: 操作人审计
- Given 已登录用户(user_id=42)
- When approve(id)
- Then logReconciliationLog 记录 operatorId=42
- And SecurityUtils.getCurrentUserId() 调用成功