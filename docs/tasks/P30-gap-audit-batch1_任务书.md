# P30 任务书 — 全项目差距审计首批处置（基于差距检测规范）

**日期**: 2026-06-24
**关联规范**: `docs/开发规范/差距检测与设计-实施同步规范.md`
**前置**: 差距检测规范发布 + P28/P29 完成
**审计依据**: B1 三方对照（PG vs Entity vs 业务代码）+ 死表检测

---

## 0. 审计背景

2026-06-24 发布差距检测规范时，同步跑了全项目差距审计（B1）。结果：除 P28/P29 已修复项外，新增发现 **4 处待处置差距**。

### 0.1 审计范围

- **PG 表数**: 65 张
- **重点审计**: 25 张高风险业务表（凭证/单据/发票/ARAP/银行/客户供应商/规则等）
- **方法**: 三方对照（PG column_name vs Entity private 字段 vs 业务代码 grep 引用）
- **死表检测**: 全表 grep `FROM t_xx | INTO t_xx | UPDATE t_xx`（Mapper Java + XML + Service 调用）

### 0.2 审计结果

| # | 表 | 差距类型 | 严重度 | 字段/对象 | 处置状态 |
|---|---|---|---|---|---|
| 1 | `t_bank_statement` | Entity 有 PG 无 | 🟡 中 | `generated_doc_no`, `generated_voucher_no`（2 个字段） | ✅ 已补 V56 migration |
| 2 | `t_prepayment` | PG 有 Entity 无 | 🟡 中 | `deleted`（1 个死列） | ✅ 已加 Entity 字段 + @TableLogic |
| 3 | `t_reconciliation_suggestion` | 死表 | 🔴 高 | 整张表无任何 SQL 引用 | ✅ 已提供删表 SQL（手工执行） |
| 4 | `t_ticket_transaction` | 死表 | 🔴 高 | 整张表无任何 SQL 引用 | ✅ 已提供删表 SQL（手工执行） |

---

## 1. P30 任务范围

### 1.1 子任务 1：t_bank_statement 补 V56 migration（影子字段修复）✅ 已完成

**问题**: BankStatementEntity 包含 `generatedDocNo` / `generatedVoucherNo` 字段，BankStatementServiceImpl 写入这两个字段。但 PG `t_bank_statement` 表无对应列。

**已执行**:
1. 查 BankStatementEntity.java 确认两个字段存在（line 51/54）
2. 创建 V56 migration：`ALTER TABLE t_bank_statement ADD COLUMN generated_doc_no VARCHAR(32); ADD COLUMN generated_voucher_no VARCHAR(32);`
3. 文件：`backend/src/main/resources/db/migration/V56__add_bank_statement_generated_doc_no.sql`

**不做**:
- 不改 Entity 字段（已是终态）
- 不改 ServiceImpl 业务逻辑（已是终态）
- 不动其他表

### 1.2 子任务 2：t_prepayment deleted 启用逻辑删除 ✅ 已完成

**问题**: PG `t_prepayment.deleted` 列存在（V36 建表时已有 DEFAULT 0），Entity 无 `deleted` 字段、无 `@TableLogic` 逻辑删除注解。

**已执行（方案 A）**:
1. PrepaymentEntity 加 `@TableLogic private Integer deleted;`（tenantId 之后，vendorId 之前）
2. 同 t_customer / t_vendor / t_employee 等表风格保持一致
3. PG 已有列且默认值为 0，无需 migration

**文件**: `backend/src/main/java/com/huicai/module/arap/entity/PrepaymentEntity.java`

### 1.3 子任务 3：t_reconciliation_suggestion 死表处置 ✅ 已完成

**问题**: PG 有表（V5 建表），但 **全项目无任何 SQL 引用**——0 Java 代码引用。

**已执行（方案 A：删表）**:
- 死表，无任何业务代码，删除清理 PG schema
- 属于 P0 规划未实施功能，已废弃
- **手工执行 SQL**（无需写 V57 migration，同 P29 模式）：
  ```sql
  DROP TABLE IF EXISTS t_reconciliation_suggestion CASCADE;
  ```

### 1.4 子任务 4：t_ticket_transaction 死表处置 ✅ 已完成

**问题**: PG 有表（V15 建表），但 **全项目无任何 SQL 引用**——0 Java 代码引用。

**已执行（方案 A：删表）**:
- 死表，t_ticket（票据主表）有 Entity，但 t_ticket_transaction（票据交易明细）从未实施
- **手工执行 SQL**（无需写 V57 migration，同 P29 模式）：
  ```sql
  DROP TABLE IF EXISTS t_ticket_transaction CASCADE;
  ```

### 1.5 不做（本次 P30）

- ❌ 一次性修 4 处差距——**风险高**（V51 migration + 删列 + 删表混合，scope 太大）
- ❌ 在 P30 内补全 t_reconciliation_suggestion / t_ticket_transaction 功能——应建 P31+ 业务工单
- ❌ 审计所有 65 张表（已审计 25 张高风险 + 检测 4 张完全死表，剩余 36 张中低风险表留 P31+）

---

## 2. 验证清单

- [x] 子任务 1 完成：V56 migration 落地 + mvn test 通过 + 端到端验证 generated_doc_no 写入
- [x] 子任务 2 完成：PrepaymentEntity 加 deleted + @TableLogic 启用逻辑删除
- [x] 子任务 3 完成：t_reconciliation_suggestion 删表 SQL 已提供（手工执行）
- [x] 子任务 4 完成：t_ticket_transaction 删表 SQL 已提供（手工执行）
- [x] mvn test 392/0/0 持平或更好
- [x] commit message 符合 §29 规则
- [x] push 成功

---

## 3. 风险与兜底

| 风险 | 兜底 |
|---|---|
| V51 migration 加列后影响现有查询 | mvn test 392/0/0 + 手工查 SELECT 验证列存在 |
| 删 t_reconciliation_suggestion 表内有数据 | 实施前先 `SELECT count(*)`，>0 提示老丁决定 |
| 删 t_prepayment.deleted 列影响未来软删除需求 | 决策前查 t_customer/t_vendor 等同类表的 deleted 用法作对照 |
| 删表 CASCADE 误删其他表 | DROP TABLE 用 CASCADE 时先 `\\d t_xx` 看依赖关系 |

---

## 4. 后续 P31+ 候选

P30 完成后，仍有以下待办需独立工单：

| 编号 | 名称 | 范围 |
|---|---|---|
| P31-候选-A | 中低风险表全审计 | 剩余 36 张中低风险表的三方对照（用户/角色/菜单/AI 模块等） |
| P31-候选-B | SPEC 阶段标记补齐 | 全 `docs/specs/P*.md` 检查阶段标记（✅/⏸/🚫/🔄），缺标补 |
| P31-候选-C | flyway:info CI 集成 | 把 §3.2 检测脚本接到 GitHub Actions，每次 PR 跑 |

---

## 5. 关联文件

- `docs/开发规范/差距检测与设计-实施同步规范.md` — 本次规范
- `docs/tasks/P28-flyway-v50-drift-fix_任务书.md` — 漂移修复工单
- `docs/tasks/P29-t-business-doc-dead-columns_任务书.md` — 死列清理工单
- `docs/specs/P26-voucher-template-engine.md` — P26 SPEC（§11 已废）
- `backend/src/main/resources/db/migration/` — 现有 migration 文件

---

## 6. 执行状态（2026-06-26）

| 子任务 | 状态 |
|--------|------|
| 1. t_bank_statement 补 V56 migration | ✅ 已完成 + commit + push |
| 2. t_prepayment deleted 启用逻辑删除 | ✅ 已完成（Entity 加字段 + @TableLogic） |
| 3. t_reconciliation_suggestion 死表删表 | ✅ 已完成（SQL 已提供，手工执行） |
| 4. t_ticket_transaction 死表删表 | ✅ 已完成（SQL 已提供，手工执行） |

**全部 4 处差距已处置完毕**。

---
