# P33 开发计划：销售发票流程简化

> **目标**：移除销售发票→应收单链路中的业务单中间环节，发票审核通过后直接生成应收单+凭证
> **编号关联**：发票↔应收单↔凭证 双向追溯必须保持
> **依据**：`docs/specs/P33-streamline-sales-invoice-flow-v2.md`

---

## 当前状态

| 批次 | 内容 | 状态 | 说明 |
|------|------|------|------|
| 1 | DB Migration + Entity 字段 | ✅ 已完成 | V65/V66 已创建，Entity 已更新 |
| 2 | 核心逻辑修改 — 状态机 | ⚠️ 部分完成 | 应收单直连已实现，凭证直连待补充 |
| 3 | TaxService 适配 | ✅ 已完成 | `sourceDocId` 已正确设置 |
| 4 | 编号关联查询 | ✅ 已完成 | `NumberingTraceServiceImpl` 已更新 |
| 5 | 前端展示优化 | ✅ 已完成 | 发票详情页合并展示 |
| 6 | 测试更新 | ❌ 待执行 | 测试用例需要更新 |
| 7 | 文档更新 | ✅ 已完成 | 所有文档已同步更新 |

---

## 剩余工作

### 批次 1：核心逻辑补全（高优先级）

**文件**：
- `backend/src/main/java/com/huicai/module/tax/service/impl/OutputInvoiceStateMachineServiceImpl.java`

**改动**：
1. 在 `confirm()` 方法中补充 `generateVoucherFromInvoiceDirect(invoiceId, userId)` 调用
2. 新增 `generateVoucherFromInvoiceDirect()` 方法：
```java
/**
 * P33 简化：发票审核后直接创建凭证（DRAFT 状态，等待人工审核）。
 */
private void generateVoucherFromInvoiceDirect(Long invoiceId, Long userId) {
    try {
        taxService.generateVoucherFromInvoice(invoiceId, userId);
        log.info("P33 销售发票凭证直连生成: invoiceId={}", invoiceId);
    } catch (Exception e) {
        log.error("P33 销售发票凭证生成失败: invoiceId={}, error={}", invoiceId, e.getMessage());
    }
}
```

**验证**：
- 编译通过
- 发票审核后应收单+凭证自动创建

---

### 批次 2：测试更新（高优先级）

**文件**：
- `backend/src/test/java/com/huicai/module/tax/service/impl/OutputInvoiceStateMachineServiceImplTest.java`
- `backend/src/test/java/com/huicai/module/finance/e2e/SalesFlowE2ETest.java`

**改动**：
1. 移除 `confirm` 后检查业务单创建的断言
2. 新增 `confirm` 后检查应收单+凭证创建的断言
3. 新增负断言：`verify(docMapper, never()).insert(any())`
4. E2E 测试更新链路：发票 → 应收单 → 凭证（移除业务单步骤）

**验证**：
- `mvn test` 全部通过

---

### 批次 3：V66 Migration 补全（中优先级）

**文件**：
- `backend/src/main/resources/db/migration/V66__add_invoice_id_to_receivable.sql`

**改动**：
1. 确认包含 `invoice_id` 列定义
2. 确认包含 `receivable_no` 列定义
3. 确认包含历史数据补全 SQL

**验证**：
- `mvn package -Dmaven.test.skip=true` 打包成功
- 后端启动时 Flyway 迁移成功

---

### 批次 4：前端接口适配（低优先级）

**文件**：
- `frontend/src/views/finance/business-doc/BusinessDocList.vue`

**改动**：
1. 增加提示："历史数据保留，新数据在发票页面统一管理"
2. 过滤 `INVOICE_OUT` 类型的单据

**验证**：
- 前端正常加载，无报错

---

## 整体时间估算

| 批次 | 预估时间 | 风险 |
|------|---------|------|
| 1. 核心逻辑补全 | 30 分钟 | 低 |
| 2. 测试更新 | 1-2 小时 | 中 |
| 3. V66 Migration 补全 | 30 分钟 | 低 |
| 4. 前端接口适配 | 30 分钟 | 低 |
| **总计** | **约 2-3 小时** | |

---

## 执行顺序

```
批次1 (核心逻辑) → 批次3 (V66 Migration) → 编译 & 启动
                                                         ↓
                                                    mvn test 全绿
                                                         ↓
                                                批次2 (测试更新) → 批次4 (前端)
```

每批次完成后 commit + push，跑 CI 确认全绿后再进行下一批。
