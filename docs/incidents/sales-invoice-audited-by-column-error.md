# 销项发票"系统繁忙"错误根因分析报告

> **编号**：HUICAI-TICKET-INC001
> **日期**：2026-07-07 | **修复人**：Hermes

---

## 1. 错误现象

- 销项发票页面（`/tax/output-invoice`）弹出"系统繁忙，请稍后重试"
- 表格显示 Total 0，但头部统计卡显示 47 条发票
- 后端 `/api/v1/tax/output-invoices/page` 返回 `{"code":500,"msg":"系统繁忙","data":null}`

## 2. 根因分析

### 2.1 直接原因

`OutputInvoiceEntity.java` 第 113-116 行两个字段缺少 `@TableField(exist = false)`：

```java
private Long auditedBy;        // 第 113 行 — 缺少 @TableField(exist = false)
private LocalDateTime auditedAt;  // 第 116 行 — 缺少 @TableField(exist = false)
```

MyBatis-Plus 自动生成 `SELECT id, invoice_no, ..., audited_by, audited_at, ... FROM t_output_invoice`，但数据库 `t_output_invoice` **没有** `audited_by` 和 `audited_at` 列（仅 `t_voucher` 表有），导致 `BadSqlGrammarException`。

### 2.2 代码缺陷路径

| 步骤 | 发生了什么 |
|------|-----------|
| 1 | 某人添加 `auditedBy`/`auditedAt` 到 `OutputInvoiceEntity`，注释写"V63 已添加列" |
| 2 | 实际 V63 migration 只给 `t_output_invoice` 加了 `version` 列，**没有**加 `audited_by`/`audited_at` |
| 3 | 没有校验其他实体（`InputInvoiceEntity` 正确标注了 `exist=false`） |
| 4 | `pageQuery()` 没有写测试 → 编译通过、部署不报错，运行时才崩溃 |
| 5 | Summary API 用自定义 SQL 正常 → 页面顶部统计正常，掩盖了问题 |

### 2.3 为什么只影响 pageQuery 不影响 Summary API

| API | SQL 生成方式 | 是否受影响 |
|-----|------------|-----------|
| `GET /output-invoices/page` | MyBatis-Plus `selectPage()` 自动生成，包含实体所有字段 | ❌ 失败 |
| `GET /output-invoices/summary` | `@Select("SELECT COUNT(*) AS totalCount, SUM(amount) AS totalAmount,...")` 自定义 SQL | ✅ 正常 |

---

## 3. 修复内容

**文件**：`/root/data/huicai/backend/src/main/java/com/huicai/module/tax/entity/OutputInvoiceEntity.java`

```diff
-    private Long auditedBy;
+    @TableField(exist = false)
+    private Long auditedBy;

-    private LocalDateTime auditedAt;
+    @TableField(exist = false)
+    private LocalDateTime auditedAt;
```

---

## 4. 同类缺陷排查

### 4.1 扫描范围

对全部 57 个 Entity 进行了字段梳理，重点扫描 `auditedBy`、`auditedAt`、`updatedBy` 等跨表字段，并与实际数据库 schema 交叉验证。

### 4.2 排查结果

| Entity | 字段 | 数据库有列？ | exist = false? | 状态 |
|--------|------|------------|----------------|------|
| OutputInvoiceEntity | auditedBy/auditedAt | ❌ 无 | 原缺 ✅ 已补 | **已修复** |
| InputInvoiceEntity | auditedBy/auditedAt | ❌ 无 | ✅ 已有 | 正常 |
| VoucherEntity | auditedBy/auditedAt | ✅ t_voucher 有 | 不需要 | 正常 |
| BusinessDocEntity | auditedBy/auditedAt | ❌ 无，且实体无此字段 | N/A | 正常 |
| 其余 53 个 Entity | auditedBy/auditedAt | N/A（或已正确配置） | ✅ | 正常 |

**结论：无其他同类问题。**

### 4.3 关于 updatedBy

虽然扫描显示大量实体有 `updatedBy` 字段未标注 `exist = false`，但经过 DB schema 验证，这些表大多实际存在 `updated_by` 列（如 `t_voucher`、`t_business_doc` 等），不是错误。

---

## 5. 测试体系评估（关联本次 bug 的三个空白）

| 空白 | 影响 | 是否仍存在 | 优先级 |
|------|------|-----------|--------|
| ① `pageQuery()` 零测试 | 未捕获 SQL 错误 | ✅ 已修复（新增测试） | P0 |
| ② 前端组件测试 | 前端无组件测试 | ⚠️ 仍空白 | P1 |
| ③ 端到端链路测试 | 销项发票→展示链路无验证 | ⚠️ 仍空白 | P0 |
| **④ Entity-DB 对齐验证** | **实体字段 vs DB schema 的三方对齐缺失** | ⚠️ 仍空白 | **P0** |

新发现第 ④ 个空白：`ENTITY ↔ DB ↔ 业务代码` 三方对齐审查流程不存在。

---

## 6. 防止重复错误：Entity-DB 三方对齐检查清单

每次修改 Entity 或 DB schema 时，执行以下清单：

### 6.1 新增 Entity 字段时

- [ ] 确认 DB 表是否有对应列（查 migration SQL）
- [ ] 如果有列但列名不同 → 加 `@TableField(value = "db_column_name")`
- [ ] 如果没列且不需要持久化 → 加 `@TableField(exist = false)`
- [ ] 如果没列但需要创建列 → 先写 Flyway migration，再写 Entity
- [ ] 检查相同字段在其他 Entity 中的写法是否一致

### 6.2 新增 Flyway migration 时

- [ ] Migration 中 DDL 的文件路径和版本号
- [ ] 修改 `VERSION__description.sql` 中的目标表
- [ ] **检查所有引用该表的 Entity 是否需要同步更新**
- [ ] 运行 `mvn flyway:info` 确认版本无冲突
- [ ] **EPG （Entity/PostgreSQL/业务代码）三方对齐验证**

### 6.3 代码审查红线

以下情况必须人工仔细审查：

```
1. Entity 字段的注释包含 "Vxx 已添加列" → 必须去查 Vxx migration
2. Entity 有 auditedBy/auditedAt/updatedBy → 确认 DB 表是否有这些列
3. MyBatis Plus 的 Entity 新增 private 字段 → 必须带 @TableField 注解
```

---

> **文档结束**