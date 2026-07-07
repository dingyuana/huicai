# P28 任务书 — Flyway V50 漂移清理 + P26 SPEC §11 修正

**日期**: 2026-06-24
**关联**: P26 SPEC §11、P27b commit 3f02332、commit 1f7401a
**前置**: P27b 已完成（mvn test 392/0/0、push origin）

---

## 0. 问题发现（2026-06-24 session 重新核对）

### 0.1 记忆与现实的偏差

记忆 `MEMORY.md` 末行写：
> ⚠️ V50 Flyway 漂移残留待 P28 清理

**实际核对结果**——记忆方向大致对，但细节错位：

| 项 | 记忆/SPEC 描述 | PG/git 实际 | 偏差 |
|---|---|---|---|
| V50 是否需要清理漂移 | 待 P28 清理 | ✅ 需要 | 对 |
| V50 文件状态 | 假设"丢了" | `git log -- 'V50*'` 完全没记录 | 比想象严重（**从未提交过**） |
| V50 跑过吗 | 未明示 | PG `flyway_schema_history` rank=50 success=TRUE，checksum=1834198509 | 实际跑过（**人工 psql 跑 + 手动插 history**） |
| 跑的时间 | 未明示 | 2026-06-23 20:39:22 | 与 P27b 任务窗口（19:37 SPEC → 22:58 commit）重叠 |
| V50 schema 内容 | 未明示 | `ALTER TABLE t_business_doc ADD COLUMN customer_name varchar(200); supplier_name varchar(200);` | 见 §1.1 |
| 是否被代码使用 | 未明示 | P27b commit 3f02332 **主动绕开**它（用 customerId/supplierId 关联查 CustomerEntity/VendorEntity 的 name） | **V50 字段是死列** |
| P26 SPEC §11 "V50 种子模板" | 计划 | 实际全部种子来自 V23（基础+BANK_STMT）/ V40（核销）/ V42（结算） | SPEC §11 **虚构了 V50** |

### 0.2 三条独立问题

1. **V50 文件漂移**：PG history 里有 V50 记录 + checksum=1834198509，git 里完全没有 V50 文件。新环境跑会卡飞。
2. **P26 SPEC §11 错误**：SPEC 写"V50 迁移 15+ 种子模板"，但实际种子分散在 V23/V40/V42，V50 跟种子模板无关。
3. **t_business_doc.customer_name / supplier_name 是死列**：P27b 用关联查绕开它，没有任何代码读它——但它们确实在表里、字段已写入。

---

## 1. P28 任务范围

### 1.1 必须做（本次 P28）

#### 任务 1：补回 V50 migration 文件

**文件**：`backend/src/main/resources/db/migration/V50__add_business_doc_customer_supplier_name.sql`

**内容**（与 PG 现网 schema 一致）：

```sql
-- V50 — BusinessDoc 客户/供应商名称字段补齐
-- 依据: P27b 任务（关联查 customer_name/supplier_name 反查时的"软失败"兜底）
-- 实施时间: 2026-06-23 20:39:22 通过 psql 手工跑过，flyway_schema_history rank=50 已注册（checksum=1834198509）
-- 本文件为漂移修复，与 PG 现网 schema 保持一致
ALTER TABLE t_business_doc ADD COLUMN customer_name varchar(200);
ALTER TABLE t_business_doc ADD COLUMN supplier_name varchar(200);
COMMENT ON COLUMN t_business_doc.customer_name IS '客户名称（冗余字段，P27b 后代码已改用 customerId 关联查）';
COMMENT ON COLUMN t_business_doc.supplier_name IS '供应商名称（冗余字段，P27b 后代码已改用 supplierId 关联查）';
```

**关键技术点**：

- PG history 现存 checksum=1834198509 是 2026-06-23 手工 psql 跑时基于一个**已不存在的临时文件**算的，**无法反推**——所以**不要求新文件 checksum 等于 1834198509**
- 标准做法：本任务第一步就是 `mvn flyway:repair -Dflyway.configFiles=...`，让 Flyway 重新计算 V50 文件 checksum 并更新 `flyway_schema_history`（**只动 checksum 字段，不动 version/description/script/success**）
- 替代方案：直接删 history 那行 + 让 Flyway 重跑（**有风险**：如果未来 env 上 PG 表已有 customer_name，ADD COLUMN 会失败——**禁止采用**）

#### 任务 2：P26 SPEC §11 章节标废弃

**文件**：`docs/specs/P26-voucher-template-engine.md`

**改动 1**：§0 改动清单表第 14 行（V50 种子模板）

| 原 | 改 |
|---|---|
| `\| 14 \| V50 迁移: 15+ 种子模板 \| Flyway \| ✅ 低 \| P2 \|` | `\| 14 \| **（已废）** V50 种子模板：实际由 V23/V40/V42 业务功能分批插入，无独立 migration \| — \| — \| — \|` |

**改动 2**：§11 整章标题加废弃说明

```markdown
## 11. 种子模板（V50 迁移）  [🚫 已废 — 2026-06-24 P28]

> **废弃原因**：P26 设计时假设种子模板需要独立 V50 迁移，实际 15+ 模板分散在以下 migration：
> - V23 `add_voucher_template_tables.sql`：银行类基础模板 + E2E 测试插入
> - V40 `add_reconciliation_voucher_templates.sql`：收款/付款核销模板
> - V42 `add_settlement_voucher_templates.sql`：结算模板
>
> **保留原因**：P27b 已用 customerId/supplierId 关联查绕开 V50 字段，V50 字段实际为冗余/死列
> **本节内容保留作为历史参考，不实施**（11.1 资金与出纳类、11.2 往来与结算类模板已在 V23/V40/V42 落地，11.3 期末结转类为 P2 阶段任务——见 §0 第 15 行）
```

#### 任务 3：mvn test 验证

```bash
cd /root/data/disk/huicai/backend
mvn test -q
```

**期望**：V50 文件存在 + checksum 对齐 + 所有测试通过（上次 392/0/0 不应回退）

#### 任务 4：commit + push

**commit message**：
```
fix(P28): 补回 V50 migration 文件（漂移修复） + P26 SPEC §11 标废弃
```

**严禁**（按 §29 沉淀）：body 不加 TODO/待办/⚠️/🚨 标注

### 1.2 不做（本次 P28）

- ❌ **不删** `t_business_doc.customer_name` / `supplier_name` 字段（虽为死列）—— 删除需要迁移 + 修代码 + 重新走 e2e，超出 P28 范围
- ❌ **不动** PG 现网数据（`flyway clean` 禁止）
- ❌ **不**改 P27b 代码（commit 3f02332 已稳定）

### 1.3 待办（开 P29）

- P29-候选：清理 `t_business_doc` 死列（删 customer_name / supplier_name + 删 V50 migration 文件 + 修 P27b 代码移除关联查逻辑）

---

## 2. 验证清单

- [ ] V50 文件存在
- [ ] `mvn flyway:repair` 跑过、`flyway_schema_history` V50 checksum 已更新（不要求等于 1834198509）
- [ ] mvn test 通过（与 P27b 上次 392/0/0 持平或更好）
- [ ] P26 SPEC §0 改动清单表第 14 行已标废弃
- [ ] P26 SPEC §11 标题加废弃说明
- [ ] commit message 首行 < 50 字符、body 干净
- [ ] push 成功

---

## 3. 风险与兜底

| 风险 | 兜底 |
|---|---|
| V50 checksum 不一致 | 跑 `mvn flyway:repair`（仅 dev profile）→ 重启自动对齐 |
| Flyway 启动校验失败 | `application-dev.yml` 加 `flyway.validate-on-migrate=false`（不推荐，会掩盖后续真漂移） |
| mvn test 出现新失败 | 立即停手，rollback P28 commit，回退到 P27b 状态 |
| P26 SPEC §11 改动破坏章节编号 | 后续章节顺序保持，§11 整段加废弃说明而非删除 |

---

## 4. 关联文件

- `docs/tasks/P27b-businessdoc-name-fix_任务书.md`（P27b 实施记录）
- `docs/specs/P27b-businessdoc-template-context-customer-vendor-name.md`（P27b SPEC，D 选项决策）
- `docs/specs/P26-voucher-template-engine.md`（P26 SPEC，本次改 §0/§11）
- `docs/DESIGN.md §305`（V46 注释，已说明 V45 跳号是有意废弃）
- `MEMORY.md`（待 P28 完成后修订"V50 漂移待清理"和"P27b D 选项"两条记忆）
