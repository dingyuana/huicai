# P31: 发票导入后自动全流程处理 (审核→应收+凭证)

> **P33 修正（2026-06-29）**：移除业务单中间环节。发票审核通过后，直接生成应收单 + 凭证。

## 目标

销售发票批量导入后，系统自动完成：**发票审核 → 生成应收单 + 凭证**，人工仅需最后一步凭证审核。
**硬约束**：人必须是凭证审核的最终负责人，不能跳过凭证审核；异常情况自动告警通知人工处理。

## 流程

```
销售发票导入 → PENDING_CONFIRM
  → 提交审核 → PENDING_REVIEW
  → 人工审核(confirm) → CONFIRMED
    → 自动生成：
       1. 应收单 (DRAFT)
       2. 凭证 (DRAFT, 等待人工审核)
  → 凭证管理页面 → 人工审核凭证
```

> **P33 变更**：原设计"发票审核 → 业务单 → 应收单 → 凭证"已简化为"发票审核 → 应收单 + 凭证"。
> 业务单（BusinessDoc）不再出现在销售发票链路中，仅保留给银行流水B类、费用报销等非发票场景。

## 边界
- **配置开关**: `invoice.auto-flow-after-import` (默认 `false`，避免影响现有流程)
- **触发时机**: 发票审核确认（CONFIRMED）后立即触发（异步执行，不阻塞审核返回）
  - **2026-06-25 修正**：原设计为"导入批次确认完成后立即触发"，改为"发票审核通过后异步触发"，因为业务单和应收单在审核后才创建
- **失败重试**: 失败后自动重试 1 次（处理偶发科目匹配缓存问题）
- **告警方式**: 复用站内信接口，处理完成后发送结果通知

## 流程对比
| 场景 | 当前 | 优化后 |
|------|------|--------|
| 100张发票无异常 | 6步、5-10分钟 | 2步、1分钟 (导入→审核→应收+凭证自动生成) |
| 100张发票5张异常 | 6步、5-10分钟 | 2步、1分钟 (导入→审核→2步完成95张→失败明细处理5张) |

> **P33 修正（2026-06-29）**：移除业务单环节。发票审核通过后直接生成应收单+凭证，不再异步创建业务单。
> 实现改为：`confirm()` 内直接调用 `createReceivableFromInvoiceDirect()` + `generateVoucherFromInvoiceDirect()`。

## 实现方案

### 后端
1. **配置项**: `application.yml` 新增 `invoice.auto-flow-after-import: false`
2. **异步服务**: `OutputInvoiceStateMachineServiceImpl#confirm()` 修改
   - **P33 修正**：不再调用 `postProcessAfterInvoiceConfirm()` 或 `createBusinessDocAndReceivableAfterConfirm()`
   - 直接调用 `createReceivableFromInvoiceDirect(invoice, userId)` 创建应收单
   - 直接调用 `generateVoucherFromInvoiceDirect(invoice, userId)` 创建凭证
   - 凭证状态为 `DRAFT`，等待人工审核（符合"人是唯一审核主体"原则）
   - 捕获异常，失败明细写入日志
3. **触发点**: `OutputInvoiceStateMachineServiceImpl#confirm()` 完成状态变更后立即执行
4. **告警**: 处理完成后调 `MessageCenterService` 发送站内信

### 前端
- 导入结果页新增「失败明细」折叠面板
- 显示失败发票号 + 失败原因
- 站内信收件箱集成

## 验收
- [ ] 配置项默认关闭，开启后导入发票触发全流程
- [ ] 异步执行不阻塞导入接口返回 (< 1秒)
- [ ] 异常情况下，导入结果页显示失败明细，站内信收到通知
- [ ] 人工凭证审核流程不变，凭证生成后状态为 `PENDING_REVIEW`

## 实施时间
预计 1-2 天
