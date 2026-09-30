# M5b 操作手册 —— 应用角色降权（让 RLS 真正生效）

> 状态：**待老丁执行**（不可逆且有全局风险，故未写成迁移）
> 前置：V166 已落地（70 张表 FORCE）；`TenantRlsInitializer` 已在每次事务设置 `app.enterprise_id`
> 关联：REQ-2026-129 / P102 / SPEC §8

## 为什么必须降权

实测（2026-10-01）证明 RLS 失效有**三重**原因，前两重是角色属性，第三重是表属主：

| # | 原因 | 消除方式 |
|---|------|---------|
| 1 | `rolsuper = t` | `ALTER ROLE huicai NOSUPERUSER` |
| 2 | `rolbypassrls = t` | `ALTER ROLE huicai NOBYPASSRLS` |
| 3 | 83/83 张表 owner 是 `huicai`，属主默认绕过 | V166 已 `FORCE ROW LEVEL SECURITY` |

**只做第 3 项无效** —— 实测设 `app.enterprise_id` 为 1 与为 2 返回同样的 44 行。
**必须先降权，FORCE 才生效**：探针角色（NOSUPERUSER NOBYPASSRLS）下同一查询 44 行 → 1 行。

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

## 执行步骤（建议先在预发环境走一遍）

```sql
-- 0. 先确认应用侧已就位：连续跑一遍 L2 全量必须全绿
--    mvn test -DexcludedGroups= -DfailIfNoTests=false

-- 1. 降权（不可逆，回滚需重新提权）
ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS;

-- 2. 立即验证策略是否真的开始过滤（把 1 换成实际企业 id）
BEGIN;
SET LOCAL app.enterprise_id = '1';
SELECT count(*) FROM t_voucher;   -- 应只返回企业 1 的行
ROLLBACK;

-- 3. 重启应用后跑一次真实业务冒烟
```

## 回滚

```sql
ALTER ROLE huicai SUPERUSER BYPASSRLS;
```

回滚后 RLS 立即重新失效（回到本轮修复前的状态），不会造成数据损坏。

## 为什么没有写成 Flyway 迁移

1. **不可逆且无幂等语义**：迁移一旦执行就写进 `flyway_schema_history`，无法在
   「发现问题 → 回滚 → 重新启用」之间来回切；写成迁移会让回滚变成再写一个 V167。
2. **失败模式是静默的**：降权后若某路径漏设变量，故障表现为「查询返 0 行」，
   与「确实没有数据」难以区分，不适合在无人值守的迁移里做。
3. **需要与发布节奏对齐**：应与运维在预发环境验证后，于维护窗口手动执行。