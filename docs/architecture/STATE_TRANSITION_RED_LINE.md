# 财务系统状态转换红线规范

**版本**: v1.0  
**最后更新**: 2026-06-27  
**适用范围**: 慧财财务系统所有业务模块  

---

## 一、规范背景

为满足财务审计要求，确保所有业务单据状态变更可追溯、可审计，特制定本规范。**所有状态变更必须经过人工审核，并留下完整审计痕迹**。

---

## 二、核心原则（红线）

⚠️ **以下为系统设计红线，任何违反的代码不得合并**

| 序号 | 红线要求 | 验证方式 |
|------|---------|---------|
| 1 | **所有状态变更方法必须传入审核人ID** | 方法签名必须包含 `Long userId` 或 `Long auditorId` |
| 2 | **状态变更时必须记录审核人ID和审核时间** | Entity 必须设置 `auditedBy/auditedAt` 字段 |
| 3 | **禁止定时任务/消息队列直接变更状态** | 定时任务只能生成待审核记录，状态变更需人工触发 |
| 4 | **审核人不能审核自己创建的单据**（职责分离） | Service 层前置校验 |
| 5 | **状态变更必须记录审计日志** | 自动写入 `t_audit_log` 表 |

---

## 三、实体字段命名规范

### 3.1 统一字段命名（推荐）

所有需要审核的业务实体，必须包含以下两个字段：

| 字段名 | Java 属性 | 类型 | 说明 | 必填 |
|--------|----------|------|------|------|
| `audited_by` | `auditedBy` | `Long` | 审核人ID，关联 `t_user.id` | ✅ 是 |
| `audited_at` | `auditedAt` | `LocalDateTime` | 审核时间 | ✅ 是 |

### 3.2 历史兼容命名（不推荐，仅兼容）

以下命名为历史遗留，新建模块**必须使用统一命名**：

- `reviewed_by` / `reviewedAt` - 银行流水模块在用
- `approved_by` / `approvedAt` - 不推荐

### 3.3 数据库迁移脚本

```sql
-- 模板：给业务表添加审核字段
ALTER TABLE t_xxx 
ADD COLUMN audited_by BIGINT COMMENT '审核人ID',
ADD COLUMN audited_at TIMESTAMP COMMENT '审核时间';
```

---

## 四、Service 实现规范

### 4.1 方法签名规范

✅ **正确示例**：
```java
// 必须传入审核人ID
public void confirm(Long id, Long userId) {
    // 实现逻辑
}

public void audit(Long id, Long auditorId) {
    // 实现逻辑
}
```

❌ **错误示例**：
```java
// 缺少审核人参数 - 禁止
public void confirm(Long id) {
    // 禁止！无法追溯谁审核的
}

// 定时任务直接变更状态 - 禁止
@Scheduled(cron = "0 0 1 * * ?")
public void autoAudit() {
    // 禁止！状态变更必须人工触发
}
```

### 4.2 方法实现规范

状态变更方法必须包含以下步骤（按顺序）：

```java
@Transactional(rollbackFor = Exception.class)
public void confirm(Long id, Long userId) {
    // 1. 校验单据存在
    XxxEntity entity = mapper.selectById(id);
    if (entity == null) {
        throw BusinessException.notFound("单据不存在");
    }
    
    // 2. 校验状态流转合法性
    if (!isValidStatusTransition(entity.getStatus(), targetStatus)) {
        throw BusinessException.badRequest("状态不允许变更");
    }
    
    // 3. ⚠️ 职责分离校验：审核人不能是创建人
    if (userId.equals(entity.getCreatedBy())) {
        throw BusinessException.forbidden("不能审核自己创建的单据");
    }
    
    // 4. ⚠️ 权限校验：用户是否有该类型单据的审核权限
    if (!hasAuditPermission(userId, entity.getType())) {
        throw BusinessException.forbidden("无审核权限");
    }
    
    // 5. 设置状态 + 审核字段
    entity.setStatus(targetStatus);
    entity.setAuditedBy(userId);       // ✅ 必须设置
    entity.setAuditedAt(LocalDateTime.now());  // ✅ 必须设置
    
    // 6. 保存
    mapper.updateById(entity);
    
    // 7. 记录审计日志
    auditLogService.recordAuditLog(entity, userId, "审核通过");
}
```

### 4.3 必须设置审核字段的场景

| 场景 | 是否需要设置 auditedBy/auditedAt |
|------|--------------------------------|
| 单据提交（草稿 → 待审核） | ❌ 不需要（提交人即创建人） |
| 审核通过（待审核 → 已审核） | ✅ 必须 |
| 审核驳回（待审核 → 草稿） | ✅ 必须 |
| 记账/过账 | ✅ 必须 |
| 红冲/作废 | ✅ 必须 |
| 修改单据内容 | ❌ 不需要（只需设置 updatedBy） |

---

## 五、已完成模块清单

### ✅ 已达标模块（P0）

| 模块 | 实体 | 字段 | Service 方法 | 完成状态 |
|------|------|------|-------------|----------|
| 凭证管理 | `VoucherEntity` | `auditedBy/auditedAt` | `audit()/batchAudit()/reject()` | ✅ 已完成（原有逻辑） |
| 应收管理 | `ReceivableEntity` | `auditedBy/auditedAt` | `confirm()` | ✅ 已完成（2026-06-27） |
| 应付管理 | `PayableEntity` | `auditedBy/auditedAt` | `confirm()` | ✅ 已完成（2026-06-27） |
| 销售发票 | `OutputInvoiceEntity` | `auditedBy/auditedAt` | `confirm()/reject()` | ✅ 已完成（2026-06-27） |
| 银行流水 | `BankStatementEntity` | `reviewedBy/reviewedAt` | `review()/audit()` | ✅ 已完成（2026-06-27） |

### ⏳ 待完成模块（P1）

| 模块 | 实体 | 预计完成 | 优先级 |
|------|------|----------|--------|
| 采购发票 | `InputInvoiceEntity` | 2026-06-30 | P1 |
| 费用报销 | `ExpenseReimbursementEntity` | 2026-07-05 | P1 |
| 预算管理 | `BudgetEntity` | 2026-07-10 | P2 |
| 税务申报 | `TaxDeclarationEntity` | 2026-07-15 | P2 |
| 固定资产 | `AssetEntity` | 2026-07-20 | P2 |

---

## 六、数据库迁移说明

### 已执行 Migration

- **V63__add_audit_fields_to_core_tables.sql**
  - 影响表：`t_receivable`、`t_payable`、`t_output_invoice`、`t_input_invoice`
  - 新增字段：`audited_by`、`audited_at`
  - 执行状态：待发布后执行

### 历史数据处理

新增字段允许为空，不影响历史数据。历史单据如需补全审核信息，可通过后台脚本批量处理。

---

## 七、测试验证规范

### 7.1 单元测试必须覆盖

```java
@Test
void confirm_shouldSetAuditFields() {
    // Given
    Long id = 1L;
    Long userId = 100L;
    
    // When
    service.confirm(id, userId);
    
    // Then
    XxxEntity entity = mapper.selectById(id);
    assertThat(entity.getAuditedBy()).isEqualTo(userId);  // ✅ 验证审核人
    assertThat(entity.getAuditedAt()).isNotNull();         // ✅ 验证审核时间
    assertThat(entity.getAuditedAt()).isAfterOrEqualTo(entity.getCreatedAt());
}
```

### 7.2 集成测试必须覆盖

- ✅ 测试职责分离：审核人不能审核自己创建的单据
- ✅ 测试权限校验：无权限用户不能审核
- ✅ 测试状态流转：非法状态流转被拒绝

---

## 八、审计日志规范

所有状态变更必须自动记录审计日志，包含以下信息：

| 字段 | 说明 |
|------|------|
| `entity_type` | 实体类型（如：VOUCHER、RECEIVABLE） |
| `entity_id` | 实体ID |
| `operator_id` | 操作人ID |
| `operation` | 操作类型（CREATE/UPDATE/AUDIT/REJECT） |
| `old_status` | 变更前状态 |
| `new_status` | 变更后状态 |
| `remark` | 变更原因/备注 |
| `created_at` | 操作时间 |

---

## 九、Code Review 检查清单

PR 合并前，Reviewer 必须确认：

- [ ] 状态变更方法签名包含审核人ID参数
- [ ] 状态变更时设置了 `auditedBy/auditedAt` 字段
- [ ] 包含职责分离校验（审核人 ≠ 创建人）
- [ ] 包含权限校验
- [ ] 包含对应的单元测试
- [ ] 数据库 Migration 脚本已更新

---

## 附录：变更记录

| 版本 | 日期 | 修改人 | 修改内容 |
|------|------|--------|---------|
| v1.0 | 2026-06-27 | Hermes | 初始版本，完成 P0 核心模块规范 |

---

**本文档为强制规范，所有开发人员必须遵守。如有疑问，请提交 Issue 讨论。**
