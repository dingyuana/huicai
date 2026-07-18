# Flyway 治理规范

> **版本**: V1.0  
> **创建日期**: 2026-07-18  
> **关联文档**: [DESIGN.md](../DESIGN.md)  
> **治理范围**: 数据库迁移脚本的编写、审查、发布和回滚

---

## §1 基线策略

### 1.1 基线定义

基线是数据库 Schema 的完整快照，包含系统初始化所需的所有表结构、约束、索引和初始数据。

### 1.2 基线合并时机

当满足以下条件之一时，执行基线合并：

| 条件 | 说明 |
|------|------|
| 迁移文件数量 ≥ 50 | 脚本通胀风险阈值 |
| 累计变更行数 ≥ 1000 | 维护成本过高 |
| 架构重大变更 | 如 P34 应收应付表迁移 |
| 每季度例行 | 定期清理历史脚本 |

### 1.3 基线合并流程

```
1. 导出当前生产库完整 schema
2. 校验 Entity ↔ DB 列一致性（三方对照）
3. 创建新基线 V1__baseline.sql
4. 归档旧迁移文件到 docs/archive/migrations/
5. 更新 flyway_schema_history 基线记录
6. 全量测试验证
```

### 1.4 当前基线状态

| 项目 | 值 |
|------|------|
| 当前基线 | V1__baseline.sql |
| 合并范围 | V1-V91 (2026-07-01 ~ 2026-07-18) |
| 归档位置 | docs/archive/migrations/ |
| 合并原因 | 脚本通胀至 V91，架构重大变更（P34） |

---

## §2 命名规范

### 2.1 迁移文件命名

```
V<版本号>__<描述>.sql
U<版本号>__<描述>.sql  -- 撤销迁移（不推荐）
R<版本号>__<描述>.sql  -- 可重复迁移
```

**规则**:

- 版本号连续递增，禁止跳号
- 描述使用小写 snake_case
- 描述不超过 50 字符
- 前缀含义：
  - `V`: Versioned Migration，版本化迁移（按顺序执行）
  - `U`: Undo Migration，撤销迁移（回滚用，不推荐使用）
  - `R`: Repeatable Migration，可重复迁移（每次执行都会重新运行）

### 2.2 描述词约定

| 前缀 | 含义 | 示例 |
|------|------|------|
| `add_` | 添加表/字段/索引 | `V92__add_xxx_column.sql` |
| `modify_` | 修改字段定义 | `V93__modify_xxx_column.sql` |
| `drop_` | 删除表/字段/索引 | `V94__drop_xxx_table.sql` |
| `create_` | 创建表 | `V95__create_xxx_table.sql` |
| `seed_` | 初始化数据 | `V96__seed_xxx_data.sql` |
| `fix_` | 修复数据 | `V97__fix_xxx_data.sql` |

---

## §3 脚本审查红线

### 3.1 破坏性操作审查

以下操作**必须经过老丁审核**，禁止直接提交：

| 操作类型 | 风险等级 | 审查要求 |
|----------|----------|----------|
| DROP TABLE | 极高 | 必须走红冲流程，禁止物理删除核心表 |
| DROP COLUMN | 高 | 需确认无代码引用，备份数据后执行 |
| DROP CONSTRAINT | 高 | 需确认无业务依赖 |
| ALTER TABLE ... DROP | 高 | 同上 |
| TRUNCATE | 极高 | 仅测试环境可用，生产禁止 |
| DELETE 无 WHERE | 极高 | 禁止 |
| UPDATE 无 WHERE | 极高 | 禁止 |

### 3.2 单脚本行数上限

- 普通脚本：≤ 100 行
- 复杂脚本：≤ 200 行
- 超出上限必须拆分为多个脚本

### 3.3 依赖声明规范

当脚本依赖其他脚本的结果时，必须在脚本头部注明：

```sql
-- DEPENDS ON: V91__add_business_doc_fields.sql
-- REASON: 需要 invoice_id 字段已存在
```

### 3.4 SQL 语法规范

| 规范 | 要求 |
|------|------|
| 表名 | snake_case，前缀 `t_` |
| 列名 | snake_case |
| 主键 | `BIGINT GENERATED ALWAYS AS IDENTITY` |
| 金额列 | `NUMERIC(18,2)` |
| JSONB 列 | 使用 `@TableField(typeHandler = JsonbTypeHandler.class)` |
| 逻辑删除 | `deleted INTEGER DEFAULT 0` |
| 约束命名 | `uq_<表名>_<列名>` (唯一约束)，`fk_<表名>_<外键列>` (外键) |

### 3.5 幂等性要求

所有 INSERT/UPDATE 操作必须保证幂等：

```sql
-- 正确：使用 ON CONFLICT
INSERT INTO t_voucher_type (id, code, name) VALUES
(1, 'JZ', '记账凭证')
ON CONFLICT (code) DO NOTHING;

-- 正确：使用 WHERE 条件保证幂等
UPDATE t_sys_config SET config_value = '2026' 
WHERE config_key = 'accounting.start_year';

-- 错误：无幂等保护
INSERT INTO t_user (username, password) VALUES ('test', 'xxx');
```

### 3.6 禁止事项

- ❌ 禁止硬编码 ID 值（初始数据除外）
- ❌ 禁止使用 `SELECT *`
- ❌ 禁止使用隐式类型转换
- ❌ 禁止在迁移脚本中调用存储过程
- ❌ 禁止使用事务 DDL（PostgreSQL 不支持）
- ❌ 禁止修改 `flyway_schema_history` 表

---

## §4 回滚机制

### 4.1 回滚策略

| 场景 | 策略 |
|------|------|
| 字段添加 | 直接删除字段 |
| 字段修改 | 恢复原定义 |
| 表添加 | 删除表 |
| 数据修正 | 执行反向 SQL |
| 架构重大变更 | 保留旧表，通过业务逻辑兼容 |

### 4.2 回滚脚本编写

**推荐方式**: 使用单独的回滚脚本，而非 Flyway 的 U 前缀撤销迁移。

回滚脚本命名：`rollback/V<版本号>__<描述>_rollback.sql`

```sql
-- rollback/V92__add_invoice_id_column_rollback.sql
ALTER TABLE t_business_doc DROP COLUMN IF EXISTS invoice_id;
```

### 4.3 回滚执行流程

```
1. 确认回滚范围（单个脚本或连续多个）
2. 编写回滚 SQL
3. 在测试环境验证回滚
4. 生产环境执行回滚
5. 更新 flyway_schema_history（可选）
```

### 4.4 数据备份要求

执行任何破坏性操作前，必须备份相关表数据：

```sql
CREATE TABLE t_business_doc_backup_20260718 AS TABLE t_business_doc;
```

---

## §5 发布流程

### 5.1 开发环境

```
1. 编写迁移脚本
2. 本地执行 `mvn flyway:migrate`
3. 验证 Schema 变更
4. 运行单元测试
```

### 5.2 测试环境

```
1. 代码合并到测试分支
2. CI/CD 自动执行迁移
3. 运行全量测试套件
4. 手动验证业务流程
```

### 5.3 生产环境

```
1. 备份数据库
2. 执行迁移（分批执行，每批不超过 10 个脚本）
3. 验证核心业务功能
4. 监控异常日志
```

### 5.4 发布检查清单

- [ ] 迁移脚本已通过代码审查
- [ ] 测试环境验证通过
- [ ] 数据库备份完成
- [ ] 回滚脚本已准备
- [ ] 业务验证用例已准备

---

## §6 监控与告警

### 6.1 Flyway 状态检查

定期执行：

```bash
mvn flyway:info
```

### 6.2 关键指标

| 指标 | 阈值 | 告警动作 |
|------|------|----------|
| 迁移脚本数量 | ≥ 50 | 触发基线合并 |
| 执行失败次数 | ≥ 1 | 停止发布，排查问题 |
| Checksum 不匹配 | ≥ 1 | 禁止发布，修复脚本 |

### 6.3 Checksum 不匹配处理

当 `flyway_schema_history` 的 checksum 与脚本文件不匹配时：

1. 确认是否为合法修改
2. 如果是合法修改，执行 `mvn flyway:repair`
3. 如果是意外修改，恢复原脚本

---

## §7 三方对照审计

任何 Schema 变更必须经过三方对照：

| 维度 | 检查内容 |
|------|----------|
| PostgreSQL | 实际表结构、约束、索引 |
| Entity | Java 实体字段、注解、映射 |
| 业务代码 | Service/Mapper 对字段的引用 |

**检查工具**:

- DB: `\dt`, `\d <表名>`
- Entity: `@TableField`, `@TableId`, `@TableLogic`
- 代码: `grep -r <字段名> --include="*.java"`

---

## §8 归档管理

### 8.1 归档位置

旧迁移文件归档至：`docs/archive/migrations/`

### 8.2 归档命名

保留原始文件名，按版本号排序。

### 8.3 归档说明

每个归档批次创建说明文件：

```
docs/archive/migrations/archive_20260718.md
```

内容包括：
- 归档范围（V1-V91）
- 基线文件名（V1__baseline.sql）
- 归档原因
- 操作人
- 操作日期

---

## §9 违规处理

| 违规类型 | 处理方式 |
|----------|----------|
| 未按命名规范 | 退回修改 |
| 破坏性操作未审核 | 禁止合并，通报批评 |
| 脚本无幂等保护 | 退回修改 |
| 未执行三方对照 | 禁止发布 |
| 测试未通过 | 禁止发布 |

---

## §10 附录

### 10.1 常用命令

| 操作 | 命令 |
|------|------|
| 执行迁移 | `mvn flyway:migrate` |
| 查看状态 | `mvn flyway:info` |
| 修复 checksum | `mvn flyway:repair` |
| 清理数据库 | `mvn flyway:clean`（仅测试环境） |
| 基线设置 | `mvn flyway:baseline` |

### 10.2 版本号管理

当前版本号追踪：

| 基线版本 | 覆盖迁移范围 | 创建日期 |
|----------|--------------|----------|
| V1__baseline.sql | V1-V91 | 2026-07-18 |

---

**文档版本历史**:

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| V1.0 | 2026-07-18 | 初始版本，建立基线策略、审查红线和回滚机制 |
