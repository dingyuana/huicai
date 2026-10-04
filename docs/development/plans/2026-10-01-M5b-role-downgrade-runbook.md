# M5b 操作手册 —— 应用角色降权（让 RLS 真正生效）

> 状态：**🚧 原方案已被实测推翻**（2026-10-03）。V1 的「`ALTER ROLE huicai NOSUPERUSER`」**被 PostgreSQL 拒绝**；已部分执行（`NOBYPASSRLS` 生效），RLS 仍未生效；替代方案见 §V2，**待老丁审核**
> 前置：V166 已落地（70 张表 FORCE）；`TenantRlsInitializer` 已在每次事务设置 `app.enterprise_id`
> 关联：REQ-2026-129 / P102 / SPEC §8

---

## 0. 2026-10-03 实测结论（V1 方案不可行，先看这里）

| # | 动作 | 结果 |
|---|---|---|
| 1 | `ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS;` | ❌ **被数据库拒绝**：`ERROR: permission denied to alter role` / `DETAIL: The bootstrap user must have the SUPERUSER attribute.` |
| 2 | `ALTER ROLE huicai NOBYPASSRLS;`（单独执行） | ✅ 成功，`rolbypassrls = f` |
| 3 | 降权后跑 RLS 探针 | ❌ **仍不生效**：`无 GUC=37 / GUC=1=37 / GUC=2=37` —— 因为**超级用户本身就绕过 RLS**，`rolbypassrls=f` 对超级用户无意义 |
| 4 | 造 `NOSUPERUSER NOBYPASSRLS` 探针角色（只授 SELECT） | ✅ **RLS 立刻生效**：`无 GUC=0 / GUC=1=37 / GUC=2=0` |
| 5 | 对**非自己**的角色执行 `ALTER ROLE ... NOSUPERUSER NOBYPASSRLS` | ✅ 成功 ⇒ 不是权限问题，是 **PostgreSQL 对 bootstrap 超级用户的内置保护** |

**结论**：V1 方案（降权 `huicai` 本身）**在设计上不可行**，除非换掉 bootstrap 超级用户（例如重建数据目录 / 用 `pg_upgrade` 迁移到新集群），代价远超收益。**唯一可行路径是新建非超级应用角色**（§V2）。

**当前环境状态（2026-10-03 收尾）**：`huicai` = `rolsuper=t` + `rolbypassrls=f`，**与降权前的实际行为完全一致**（超级用户仍绕过 RLS），无任何功能影响；开发库已由 Flyway 补齐到 **V166**。

## 为什么必须降权

实测（2026-10-01）证明 RLS 失效有**三重**原因，前两重是角色属性，第三重是表属主：

| # | 原因 | 消除方式 |
|---|------|---------|
| 1 | `rolsuper = t` | 换用非超级用户角色（**不能改 bootstrap 超级用户**，见 §0） |
| 2 | `rolbypassrls = t` | `ALTER ROLE ... NOBYPASSRLS`（已对 `huicai` 执行，但超级用户不受此约束） |
| 3 | 83/83 张表 owner 是 `huicai`，属主默认绕过 | V166 已 `FORCE ROW LEVEL SECURITY` |

**只做第 3 项无效** —— 实测设 `app.enterprise_id` 为 1 与为 2 返回同样的 37 行。
**必须先换成非超级角色，FORCE 才生效**：探针角色（NOSUPERUSER NOBYPASSRLS）下同一查询 37 行 → 无 GUC 时 0 行、GUC=2 时 0 行。

## 风险（务必先读）

降权后，**任何未设置 `app.enterprise_id` 的查询都会返 0 行**（策略 `current_setting(..., true)`
未设置时返回 NULL，`enterprise_id = NULL` 永不匹配）。这意味着：

- 每一条业务查询都必须落在设置了该值的事务里；
- 定时任务、系统初始化、跨线程 `@Async` 等无 `EnterpriseContextHolder` 的路径**查不到数据**；
- 一旦遗漏，故障表现为「查不到数据」而非报错，排查成本高。

当前代码侧已就位：`TenantRlsInitializer` 拦截 `@Transactional` 方法，
用 `DataSourceUtils.getConnection` 取**事务连接**执行 `SET LOCAL app.enterprise_id`。
必须用 `SET LOCAL`（事务级）而非 `SET`（会话级）—— 后者会被连接池带到下一个请求，
造成**跨租户串数据**。

## V2 方案（唯一可行路径，待老丁审核）

> 与 V1 的差异：**不动 `huicai`，新建一个非超级应用角色并把应用切过去**。
> 风险点是「对象属主变更」与「配置改用户名」，故按三步闭环先审后执行。

### V2.1 步骤

```sql
-- 1) 建非超级应用角色（NOSUPERUSER NOBYPASSRLS 是关键，缺一 RLS 仍被绕过）
CREATE ROLE huicai_app NOSUPERUSER NOBYPASSRLS LOGIN PASSWORD '<由运维注入>';

-- 2) 让它接管对象属主：DDL（Flyway 迁移要 ALTER TABLE / 建索引）与 RLS 的 FORCE 语义
--    都要求「当前角色是属主」，只 GRANT 不够
REASSIGN OWNED BY huicai TO huicai_app;

-- 3) 兜底授权（对象属主已足够，此处防后续新建对象）
GRANT CONNECT ON DATABASE huicai TO huicai_app;
GRANT USAGE  ON SCHEMA public TO huicai_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO huicai_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO huicai_app;  -- identity 列必需
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO huicai_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO huicai_app;
```

### V2.2 配置改动（必须与 SQL 同步，否则应用起不来）

| 文件 | 改动 |
|---|---|
| `backend/src/main/resources/application.yml:22` | `username: ${DB_USERNAME:huicai_app}` |
| `backend/src/main/resources/application-dev.yml:8` | 同上 |
| `docker-compose.yml` | `DB_USERNAME` / `DB_PASSWORD` 注入到 backend 服务 |
| `.env.example` | 补 `DB_USERNAME` / `DB_PASSWORD` 说明 |

### V2.3 验证（与 §V1「验证」同一套探针）

| 检查 | 期望 |
|---|---|
| `pg_roles` 中 `huicai_app` | `rolsuper=f`、`rolbypassrls=f` |
| 探针：`无 GUC / GUC=1 / GUC=2` | `0 / 本企业行数 / 0` |
| 应用启动 | `/api/v1/system/health` 200 |
| 真实业务冒烟 | 登录 → 查凭证/报表 → 数据非空 |
| **L2 全量** | `mvn test -DexcludedGroups=` 全绿（2023 用例） |
| **回滚演练** | 配置改回 `huicai` 即恢复（`huicai` 超级用户仍在，权限不受影响） |

### V2.4 风险与遗留

- **`t_user` / `t_agency_enterprise` 两张带 `enterprise_id` 的表仍无 RLS**（2026-10-03 实测：70 张有策略、12 张无 RLS，其中这两张带租户列）。属既有设计选择，非本次引入；若要补需单独评估（`t_user` 的跨租户读由 `EnterpriseMembershipChecker` 三源并集覆盖）。
- `huicai` 超级用户**保留**（运维/迁移用），因此「应用不再用超级用户」依赖**配置正确**而非数据库强制 —— 真正的强制手段是把 bootstrap 超级用户的口令也改为环境变量注入（当前 `.env` 已有 `JWT_SECRET` 同类实践）。
- 12 张无 RLS 的表在 `huicai_app` 下同样无策略保护（RLS 只对有策略的表生效）。

## V1 执行步骤（已实测失败，保留供追溯）

```sql
-- 0. 先确认应用侧已就位：连续跑一遍 L2 全量必须全绿
--    mvn test -DexcludedGroups= -DfailIfNoTests=false

-- 1. 降权 —— 2026-10-03 实测：❌ 被拒绝
--    ERROR: permission denied to alter role
--    DETAIL:  The bootstrap user must have the SUPERUSER attribute.
-- ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS;

-- 1b. 只去 BYPASSRLS 可行，但对超级用户无实质效果（超级用户必然绕过 RLS）
ALTER ROLE huicai NOBYPASSRLS;

-- 2. 验证策略是否真的开始过滤（❌ 实测仍不生效：三种 GUC 都返回 37 行）
--    因为 huicai 仍是超级用户 ⇒ 见 §0 与 §V2
-- BEGIN;
-- SET LOCAL app.enterprise_id = '1';
-- SELECT count(*) FROM t_voucher;
-- ROLLBACK;

-- 3. 重启应用后跑一次真实业务冒烟
```

## 回滚

```sql
-- V1：恢复超级用户属性（当前未成功降权，故无需执行）
ALTER ROLE huicai SUPERUSER BYPASSRLS;
```

回滚后 RLS 立即重新失效（回到本轮修复前的状态），不会造成数据损坏。

**V2 回滚更简单**：把配置里的 `DB_USERNAME` 改回 `huicai` 即可 —— `huicai` 超级用户仍在，权限不受影响（新角色 `huicai_app` 可以先留着不删，便于二次切换）。

## 为什么没有写成 Flyway 迁移

1. **不可逆且无幂等语义**：迁移一旦执行就写进 `flyway_schema_history`，无法在
   「发现问题 → 回滚 → 重新启用」之间来回切；写成迁移会让回滚变成再写一个 V167。
2. **失败模式是静默的**：降权后若某路径漏设变量，故障表现为「查询返 0 行」，
   与「确实没有数据」难以区分，不适合在无人值守的迁移里做。
3. **需要与发布节奏对齐**：应与运维在预发环境验证后，于维护窗口手动执行。
4. **补充（2026-10-03）**：即便写成迁移也**解决不了** —— `ALTER ROLE` 会被
   PostgreSQL 的 bootstrap 超级用户保护拒绝，Flyway 只会得到一个失败迁移。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-10-01 | opencode | 初稿：`ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS` + 探针验证 + 回滚 |
| V1.1 | 2026-10-03 | opencode | **实测推翻 V1 方案**：`ALTER ROLE ... NOSUPERUSER` 被 PostgreSQL 以「bootstrap 用户必须保有 SUPERUSER 属性」拒绝；对非自己的角色同样语句则成功 ⇒ 属数据库内置保护，非权限问题。`NOBYPASSRLS` 已单独执行（`rolbypassrls=f`）但**超级用户仍绕过 RLS**，探针 37/37/37 未变；改用 `NOSUPERUSER NOBYPASSRLS` 探针角色后 RLS 立刻生效（0/37/0）。新增 §V2 方案（新建非超级应用角色 + `REASSIGN OWNED` + 改配置）与验证/风险清单，标注**待审核** |