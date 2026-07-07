# P29 任务书 — 清理 t_business_doc 死列（customer_name / supplier_name）

**日期**: 2026-06-24
**关联**: P28（V50 漂移修复）、P27b commit 3f02332、P26 SPEC §0 第 14 行（已废）
**前置**: P28 完成（commit 5fe0e09、push origin main）

---

## 0. 任务来源（2026-06-24 session 复盘）

P28 任务书 §1.3 把"清理 t_business_doc 死列"列为 P29 候选。本次 session 实施前**重新核对**（§89 事实校验铁律），发现原候选范围有 3 处偏差——本任务书按核对后实际范围实施。

### 0.1 原候选范围 vs 实际范围

| 原候选（来自 P28 §1.3） | 2026-06-24 核对结果 | 决定 |
|---|---|---|
| 删 t_business_doc.customer_name / supplier_name 死列 | ✅ 仍是死列（PG 验证：Java 端无任何代码引用，search_files 全文找不到 `getCustomerName`/`getSupplierName`/`customer_name`/`supplier_name` 业务调用） | **纳入 P29** |
| 删 V50 migration 文件 | ✅ 删 V50 即可（仅影响 t_business_doc，不联动其他表） | **纳入 P29** |
| 改 P27b 关联查回退到字段直读 | ❌ **任务本身错误**——P27b 引入的关联查（customerMapper.selectById / vendorMapper.selectById）就是终态代码，**没有"回退到字段直读"的目标态**。字段是死列，关联查是正确设计 | **不纳入 P29** |
| 删 AutoGenerationService 残留硬编码 | ❌ **类已不存在**——P26 实施时已清理完毕，git log 无单列 commit（推测合并入 `410bf3d` P0-1 或后续清理 commit） | **不纳入 P29** |

### 0.2 仍需 P29 处理的真任务

1. 删 `backend/src/main/resources/db/migration/V50__add_business_doc_customer_supplier_name.sql` 文件
2. 删 PG 现网 `t_business_doc` 表 `customer_name` 和 `supplier_name` 两列（**注**：只在 t_business_doc 表，**不动** V8__init_tax_tables.sql 里的 t_output_invoice.customer_name——后者是活列，OutputInvoiceEntity.java 真实使用）
3. 跑 `mvn flyway:repair` 让 history V50 行 checksum 失效（实际是删 V50 那行记录）
4. 跑 `mvn test` 验证（期望 392/0/0 持平）

---

## 1. P29 任务范围

### 1.1 必须做（本次 P29）

#### 任务 1：删 V50 migration 文件

**文件**：`backend/src/main/resources/db/migration/V50__add_business_doc_customer_supplier_name.sql`

**操作**：`git rm <file>`（**不用** `rm`——确保 git 跟踪删除）

#### 任务 2：删 t_business_doc 两列

**SQL**（手工跑，**不是**走 Flyway 写新 migration——删列不应该走 migration 历史）：

```sql
ALTER TABLE t_business_doc DROP COLUMN customer_name;
ALTER TABLE t_business_doc DROP COLUMN supplier_name;
```

**理由不写 V51 migration**：
- V50 是漂移产生的"伪 migration"（从不在 git 里、纯手工跑+塞 history）
- 删列在生产环境也是一次性操作，不需要历史追溯
- 如果未来真要保留删列历史，应该用 P30 重新设计的 migration 流程，而不是在 P29 凑合

#### 任务 3：清理 flyway_schema_history V50 行

**SQL**：

```sql
DELETE FROM flyway_schema_history WHERE version = '50';
```

**为什么手工清理而不是 repair**：
- `flyway:repair` 只重算 checksum，**不删 history 行**
- V50 文件删除后，V50 history 行就成"孤儿记录"——必须删
- **风险**：如果有别的环境也跑过 V50（理论上不存在，V50 仅本地 dev PG），需要先在那些环境跑同样的删列 SQL

#### 任务 4：mvn test 验证

```bash
cd /root/data/disk/huicai/backend
mvn test -q
```

**期望**：Tests run: 392, Failures: 0, Errors: 0, Skipped: 0（与 P27b/P28 持平）

**潜在失败点**：
- 如果某测试读 `entity.getCustomerName()`——会编译失败（已 search_files 确认无业务代码引用，但**测试代码**需要再查）
- 如果某测试读 `t_business_doc.customer_name` 列（直接 SQL）——会查询失败

#### 任务 5：commit + push

**commit message**（按 §29 规则）：
- 首行 < 50 字符
- body 严禁 TODO/待办/⚠️/🚨 标注

候选：
```
refactor(P29): 删 t_business_doc 死列 + 删 V50 migration
```

### 1.2 不做（本次 P29）

- ❌ **不动** t_output_invoice.customer_name（V8 活列，OutputInvoiceEntity.java 真实使用）
- ❌ **不动** ReceivableMapper.java 的 `c.name AS customer_name`（ARAP 模块 JOIN 别名，与 t_business_doc 无关）
- ❌ **不**写 V51 migration（删列一次性操作，不入历史）
- ❌ **不**改 P27b commit 3f02332 的关联查代码（已是终态）
- ❌ **不**做 P26 P2 阶段剩余工作（期末结账制证 + 残留硬编码——已确认无残留）

### 1.3 关联已完成

- P28 §0.1 "V50 字段是死列"判断已在 P28 任务书中澄清
- P28 §1.3 "清理死列"候选 P29-A 现已被本任务书承接

---

## 2. 验证清单

- [ ] V50 文件已删（`git status` 显示 `D`）
- [ ] `t_business_doc` 表无 `customer_name`/`supplier_name` 列
- [ ] `flyway_schema_history` 无 version=50 行
- [ ] mvn test 通过（392/0/0 持平）
- [ ] commit message 首行 < 50 字符、body 干净
- [ ] push 成功
- [ ] 工作树干净

---

## 3. 风险与兜底

| 风险 | 兜底 |
|---|---|
| mvn test 编译失败（某测试读 entity.getCustomerName） | `git reset --soft HEAD~1` 回退 + 任务书记录"需要先改测试代码" + 转 P30 |
| mvn test 运行失败（某测试读 t_business_doc.customer_name 列） | 同上 |
| 删列后生产数据丢失 | **无影响**——两列在 P27b 后从未被 Java 代码写入（search_files 全文确认），PG 现有 200 多条 t_business_doc 数据里这两列全是 NULL |
| 其他环境 V50 history 残留 | 当前只有本地 dev PG 跑过 V50（V50 文件从不在 git），无其他环境需处理 |

### 3.1 回退方案

如果 P29 出问题，按以下顺序回退：

1. `git reset --soft HEAD~1` 回退 commit
2. `git restore backend/src/main/resources/db/migration/V50__add_business_doc_customer_supplier_name.sql` 恢复文件
3. 重新跑 P28 流程：mvn flyway:repair + commit
4. PG 删列需要重跑（如果跑了的话）：`ALTER TABLE t_business_doc ADD COLUMN customer_name varchar(200); ...`

---

## 4. 关键事实沉淀（防 P30 再踩坑）

| 事实 | 来源 |
|---|---|
| V50 是 2026-06-23 手工 psql 跑 + 手动塞 history，**从不在 git** | P28 §0.1 + 本次 git log 确认 |
| P27b commit 3f02332 用 customerId/supplierId 关联查是**终态设计**，不是过渡 | git show 3f02332 确认无后续回退 commit |
| AutoGenerationService 类已**不存在**于代码库 | search_files 全文 0 命中 |
| t_output_invoice.customer_name 是**活列**（OutputInvoiceEntity.java 使用） | search_files 确认 |
| ReceivableMapper `c.name AS customer_name` 是 **ARAP JOIN 别名** | 与 t_business_doc 无关 |

---

## 5. 关联文件

- `docs/tasks/P28-flyway-v50-drift-fix_任务书.md`（P28，本任务的来源）
- `docs/specs/P27b-businessdoc-template-context-customer-vendor-name.md`（P27b 关联查设计）
- `docs/specs/P26-voucher-template-engine.md`（P26，§0 第 14 行已废、§11 已废）
