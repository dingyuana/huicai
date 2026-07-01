# P36: 发票-业务单-应收-凭证 红冲链路补齐

## 背景

当前红冲机制在三处不完整：

| 层级 | 现状 | 问题 |
|------|------|------|
| 销售发票 | 仅有 voidInvoice(作废) | 无 reverseInvoice(红冲) 接口；审核/核销后的发票无法红冲 |
| 业务单据 | ✅ reverse() 完整 | 无联动处理下游应收和凭证 |
| 应收单 | ✅ reverse() 简单状态变更 | 无反冲凭证逻辑、无 reversedFrom 字段 |
| 凭证 | ✅ reverse() 完整 | 无上游溯源联动 |

核心缺失：**发票层没有主动红冲入口**，且发票红冲不会级联处理下游的业务单、应收单和凭证。

## 设计原则

1. **红冲 ≠ 作废**：作废是归零失效，红冲是生成反向冲销记录
2. **人审原则**：所有红冲产生的新单据/凭证均为 DRAFT，人工审核后生效
3. **级联而非自动**：上游红冲不自动改下游状态，而是提示需要红冲的下游对象
4. **可追溯**：每张红冲记录都保留 reversedFrom/reversedBy 双向关联

## 方案

### 1. 销售发票增加 reverseInvoice 接口

**接口**：`POST /api/v1/tax/output-invoices/{id}/reverse`

**前置条件**：
- 发票状态为 CONFIRMED / VOUCHERED / PARTIALLY_RECONCILED
- 终态（VOIDED / REVERSED / FULLY_RECONCILED）不可红冲
- 已全额核销（FULLY_RECONCILED）不可红冲

**红冲流程**：
1. 生成一张红字发票（金额取反，status=DRAFT）
2. 原蓝字发票标记为 REVERSED，记录 reversedByInvoiceId
3. 如果原发票已关联业务单（docId 非空）：
   - 提示用户需要对关联业务单执行红冲（不自动处理）
4. 如果原发票已生成凭证（voucherId 非空）：
   - 提示用户需要对关联凭证执行红冲（不自动处理）

**数据库变更**：
- `t_output_invoice` 已有 `reversed_by_invoice_id` 和 `original_invoice_no` 字段（V51），无需新增
- 需要新增 `reversed_from` 字段（指向被红冲的蓝字发票 ID），与凭证/业务单保持一致

### 2. 红冲发票审核通过后级联触发

当红字发票审核通过（confirm）时，自动执行以下操作：

2a. **级联创建红字业务单**：
   - 如果原发票已有关联业务单（INVOICE_OUT 类型）
   - 调用 `BusinessDocServiceImpl.reverse()` 生成红字业务单（DRAFT）
   - 红字业务单的 invoiceNo 指向新红字发票

2b. **级联创建红字业务单据**：
   - 如果原发票已有关联业务单据（INVOICE_OUT 类型）
   - 红字发票 confirm() 时自动创建红字业务单据（source=RED_FLUSH，金额取反）
   - 红字业务单据 reversedFrom 指向被红冲的蓝字业务单据

2c. **级联创建红字凭证**：
   - 如果原发票已生成凭证（voucherId 非空）
   - 调用 `VoucherServiceImpl.reverse()` 生成红字凭证（DRAFT）

### 3. 应收单增加 reversedFrom 字段

**数据库变更**：
```sql
ALTER TABLE t_receivable ADD COLUMN reversed_from BIGINT;
COMMENT ON COLUMN t_receivable.reversed_from IS '被红冲应收单ID';
CREATE INDEX idx_receivable_reversed_from ON t_receivable(reversed_from);
```

**逻辑变更**：
- `ReceivableServiceImpl.reverse()` 改为创建红字应收单而非简单状态变更
- 红字应收单金额取反，status=DRAFT
- 原应收单标记 REVERSED，记录 reversedFrom
- 如果红字应收单已核销，恢复 unsettledAmount

### 4. 状态机扩展

**InvoiceStatus.java** 新增方法：
```java
public static boolean isReversible(String status) {
    // CONFIRMED / VOUCHERED / PARTIALLY_RECONCILED 可红冲
    return CONFIRMED.equals(status)
        || VOUCHERED.equals(status)
        || PARTIALLY_RECONCILED.equals(status);
}
```

**OutputInvoiceStateMachineService.java** 新增方法：
```java
/** 红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → 生成红字发票) */
Long reverseInvoice(Long invoiceId, Long userId, String reason);
```

### 5. 完整链路图

```
场景A：发票刚审核，尚未生成业务单
  发票(CONFIRMED) ──reverse──▶ 红字发票(DRAFT)
  原发票 → REVERSED
  （无下游联动）

场景B：发票已生成业务单据
  发票(CONFIRMED) ──reverse──▶ 红字发票(DRAFT)
  原发票 → REVERSED
  红字发票审核 → 自动创建红字业务单据(DRAFT, source=RED_FLUSH)

场景C：发票已生成业务单据+凭证，已核销部分
  发票(PARTIALLY_RECONCILED) ──reverse──▶ 红字发票(DRAFT)
  原发票 → REVERSED
  红字发票审核 → 级联：
    ├── 红字业务单据(DRAFT, source=RED_FLUSH, reversedFrom=原业务单据)
    └── 红字凭证(DRAFT, reversedFrom=原凭证)

场景D：全额核销后
  发票(FULLY_RECONCILED) ──✗ 不可红冲──▶ 拒绝
  （需先反核销 → PARTIALLY_RECONCILED）
```

## 实施步骤

### Phase 1: 数据库 + 基础接口（P1）
1. Migration: 新增 `t_output_invoice.reversed_from` 字段
2. Migration: 新增 `t_receivable.reversed_from` 字段
3. Entity: OutputInvoiceEntity 添加 reversedFrom 字段
4. Entity: ReceivableEntity 添加 reversedFrom 字段
5. InvoiceStatus: 添加 isReversible() 方法
6. OutputInvoiceStateMachineService: 添加 reverseInvoice() 接口
7. TaxController: 添加 `POST /{id}/reverse` 端点

### Phase 2: 发票红冲核心逻辑（P1）
1. OutputInvoiceStateMachineServiceImpl.reverseInvoice()
   - 参数校验（状态、是否已被红冲）
   - 创建红字发票（金额取反，status=DRAFT）
   - 原发票标记 REVERSED
   - 返回红字发票 ID
2. 红字发票导入/创建时自动关联原发票

### Phase 3: 级联处理（P2）
1. 红字发票 confirm() 时检测下游依赖
2. 级联创建红字业务单
3. 级联处理应收单
4. 级联创建红字凭证
5. 前端提示需要人工审核的红冲对象列表

### Phase 4: 测试 + 文档（P2）
1. 单元测试：各场景红冲链路
2. E2E 测试：发票→业务单→应收→凭证 完整红冲
3. 更新状态机文档

## 风险与注意事项

1. **已结账期间**：红冲凭证的 period 可能与原凭证不同，需注意期间闭合限制
2. **税务合规**：红字发票在税务系统中有专门流程，本地仅模拟
3. **核销状态**：如果原发票已全额核销，需先反核销才能红冲
4. **并发控制**：红冲操作需加乐观锁，防止同一发票被多次红冲
5. **审计追踪**：所有红冲操作记录审计日志

## 不在范围内

- 进项发票红冲（P37 或后续迭代）
- 红冲后自动调整纳税申报（P37 或后续迭代）
- 批量红冲（P37 或后续迭代）
