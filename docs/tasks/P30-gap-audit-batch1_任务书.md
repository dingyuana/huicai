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

| 表 | 差距类型 | 严重度 | 字段/对象 |
|---|---|---|---|
| `t_bank_statement` | Entity 有 PG 无 | 🟡 中 | `generated_doc_no`, `generated_voucher_no`（2 个字段） |
| `t_prepayment` | PG 有 Entity 无 | 🟡 中 | `deleted`（1 个死列） |
| `t_reconciliation_suggestion` | 死表 | 🔴 高 | 整张表无任何 SQL 引用 |
| `t_ticket_transaction` | 死表 | 🔴 高 | 整张表无任何 SQL 引用 |

---

## 1. P30 任务范围

### 1.1 子任务 1：t_bank_statement 补 V51 migration（影子字段修复）

**问题**: BankStatementEntity 包含 `generatedDocNo` / `generatedVoucherNo` 字段，BankStatementServiceImpl 第 N 行调用 `r.setGeneratedVoucherNo(...)` 和 `r.setGeneratedDocNo(...)` 写入这两个字段。但 PG `t_bank_statement` 表无对应列。

**风险**:
- 写入时 MyBatis Plus 默认行为可能不写这两个字段（需用 `@TableField` 显式标记）
- 业务逻辑已用，PG 没列，**等于业务永远落不了这两个值**
- 可能是设计时"打算加这两个字段冗余存储生成单据/凭证的编号"但 V50 之后没人补 migration

**实施方案**:
1. 查 `BankStatementEntity.java` 确认两个字段的 `@TableField(fill=?)` 注解
2. 查 `BankStatementServiceImpl.java` 的写入路径（INSERT/UPDATE 时是否触发写这两个字段）
3. 写 V51 migration：`ALTER TABLE t_bank_statement ADD COLUMN generated_doc_no varchar(32); ADD COLUMN generated_voucher_no varchar(32);`
4. 跑 `mvn test` 验证
5. 跑一次端到端：导入银行流水 → 生成单据 → 查 PG `t_bank_statement.generated_doc_no` 是否有值
6. 提交 V51 migration + commit

**不做**:
- 不改 Entity 字段（已是终态）
- 不改 ServiceImpl 业务逻辑（已是终态）
- 不动其他表

### 1.2 子任务 2：t_prepayment 死列处置决策（二选一）

**问题**: PG `t_prepayment.deleted` 列存在，Entity 无 `deleted` 字段、业务代码无 `@TableLogic` 逻辑删除用法。**等价于空列**——占空间但无人用。

**两套方案（人工选）**：

#### 方案 A：补 PrepaymentEntity + Service 启用逻辑删除

- PrepaymentEntity 加 `private Integer deleted;` + `@TableLogic`
- PrepaymentServiceImpl 加 `.eq(PrepaymentEntity::getDeleted, 0)` 过滤
- 跟 t_customer / t_vendor / t_employee 等表保持一致
- **适合**：未来 prepayment 数据需要支持"软删除"场景

#### 方案 B：删 t_prepayment.deleted 死列

- 手工 `ALTER TABLE t_prepayment DROP COLUMN deleted;`（不写 V52 migration，理由同 P29）
- 清理 PG schema 与实施一致
- **适合**：项目不打算用 prepayment 软删除

**决策依据**（建议在 P30 实施前确认）：
- 查 t_prepayment 现网数据是否有 `deleted=1` 记录（决定删列风险）
- 查 t_***_prepayment 业务是否有"作废"功能（决定方案 A 必要性）
- 跟老丁确认

### 1.3 子任务 3：t_reconciliation_suggestion 死表处置

**问题**: PG 有表（V38/V40 期间建），但**全项目无任何 SQL 引用**——Mapper、Service、Controller、XML 都不读不写这张表。

**风险**:
- 占 PG 存储（当前表内数据量未知，需先查）
- 维护负担：migration 描述、字段注释都白维护
- 容易让新人误以为有功能

**调查方向**（实施前必做）:
1. `SELECT count(*) FROM t_reconciliation_suggestion;` 看是否有数据
2. `SELECT version, description FROM flyway_schema_history WHERE script ILIKE '%reconciliation_suggestion%';` 查建表 migration
3. `git log --all --oneline -- 'backend/src/main/resources/db/migration/V*reconciliation*'` 看历史

**两套方案**：

#### 方案 A：删表（推荐）

适用场景：建表是规划但未实施 / 已被其他表替代（reconciliation_log?）

- 手工 `DROP TABLE t_reconciliation_suggestion CASCADE;`（不写 V52 migration）
- 同步处理 flyway_schema_history：如果建表 migration 在 git（V38/V40/V41 之类），加注释说明"该表已删除"；如果建表 migration 本身就不该存在，标废弃

#### 方案 B：补 Mapper + Service 启用

适用场景：表是设计一部分但功能未实施

- 调研产品需求：这张表打算支持什么业务？
- 建 P31 业务工单实现功能

**决策**: 默认走方案 A（推荐），如产品有需求则建 P31 转方案 B。

### 1.4 子任务 4：t_ticket_transaction 死表处置

**问题**: 跟子任务 3 同模式——表存在、无任何 SQL 引用。

**注意**: `t_ticket`（票据主表）有 Entity 和业务代码，**但 `t_ticket_transaction`（票据交易记录）是另一张表**——可能是设计时规划"票据 → 多次交易"模型但未实施。

**调查 + 处置同子任务 3**——推荐方案 A 删表。

### 1.5 不做（本次 P30）

- ❌ 一次性修 4 处差距——**风险高**（V51 migration + 删列 + 删表混合，scope 太大）
- ❌ 在 P30 内补全 t_reconciliation_suggestion / t_ticket_transaction 功能——应建 P31+ 业务工单
- ❌ 审计所有 65 张表（已审计 25 张高风险 + 检测 4 张完全死表，剩余 36 张中低风险表留 P31+）

---

## 2. 验证清单

- [ ] 子任务 1 完成：V51 migration 落地 + mvn test 通过 + 端到端验证 generated_doc_no 写入
- [ ] 子任务 2 完成：老丁确认方案 A 或 B + 执行
- [ ] 子任务 3 完成：t_reconciliation_suggestion 删表 + git history 注释
- [ ] 子任务 4 完成：t_ticket_transaction 删表 + git history 注释
- [ ] mvn test 392/0/0 持平或更好
- [ ] commit message 符合 §29 规则
- [ ] push 成功

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
