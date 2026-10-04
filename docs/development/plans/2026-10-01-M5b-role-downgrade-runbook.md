# M5b 操作手册 —— 应用角色降权（让 RLS 真正生效）

> 状态：**✅ 2026-10-03 已修复并端到端验证**。机制缺陷（`TenantRlsInitializer` 从未生效）已定位为**两个根因**并修复：① 切面与事务 advice 顺序不确定（`SET LOCAL` 落在事务外即失效）；② **29 个 `*ServiceImpl` 一个 `@Transactional` 都没有**，读路径切面根本不触发。修复后应用以非超级用户 `huicai_app` 连接，`/subjects/tree` **21 个节点**、`/vouchers/page` **total 37**，跨租户读返 0 行
> 前置：V166 已落地（70 张表 FORCE）；`TenantRlsInitializer` 已在每次事务设置 `app.enterprise_id`
> 关联：REQ-2026-129 / P102 / SPEC §8

---

## 0.2 机制缺陷修复（2026-10-03，Red → Green → 端到端绿）

### 根因（两个，缺一不可）

| # | 根因 | 证据 | 修法 |
|---|---|---|---|
| **A** | **切面与事务 advice 顺序不确定**：二者默认都是 `Ordered.LOWEST_PRECEDENCE`，`SET LOCAL app.enterprise_id` 落在**事务外**的自动提交连接上，语句结束即被数据库丢弃 | 抓 `pg_stat_activity`：调用 `@Transactional(readOnly = true)` 的 `/asset-reports/category-summary` 时 GUC 仍为 **NULL**；真库用例 `TenantRlsGucRealDBTest#gucIsVisibleInsideTransaction` 红（`expected: not <null>`） | 新增 `TransactionAdviceOrderConfig`：`@EnableTransactionManagement(order = 0, proxyTargetClass = true)` ⇒ 事务 advice 在最外层、切面在其内。⚠️ **加了该类会让 Boot 的 `TransactionAutoConfiguration` 退让，必须补回 `proxyTargetClass = true`**，否则 CGLIB 代理失效、`@Transactional` 静默不生效（又是一个假绿） |
| **B** | **方法/类根本没有事务**：全库 75 个 `*ServiceImpl` 中 **29 个一个 `@Transactional` 都没有**；另有 17 个类的读方法无注解（`SubjectServiceImpl#getTree`、`VoucherServiceImpl#pageQuery`、`ReportServiceImpl` 12 个方法、`LedgerServiceImpl`、`PeriodServiceImpl`…）⇒ 切面不触发 | 代码审计 + 冒烟：这三条读路径修复前全部返回 0 行 | 给**全部非 AI 的 `*ServiceImpl`** 加**类级** `@Transactional`（方法级注解优先级更高，不受影响），共 **72 个文件**；AI 模块按本轮范围排除 |

### 回归锁（真库，Red → Green）

`backend/src/test/java/com/huicai/security/TenantRlsGucRealDBTest`（3/3）：

| 用例 | 锁什么 |
|---|---|
| `AT-102-8` | 事务内 `current_setting('app.enterprise_id')` 能读到上下文企业 —— **advice 顺序错则红** |
| `AT-102-9` | 事务外读不到该值 ⇒ 必须是 `SET LOCAL` 而非 `SET`（否则连接池复用跨租户串数据） |
| `AT-102-10` | 无企业上下文（定时任务/初始化）时**不设**假值，仍为 NULL |

**测试设计上的一个坑（已修）**：初版用例在事务里直接调 `subjectMapper.selectList(null)` —— mapper 既不经 Spring 事务也不经切面，用例会**假红**（测的不是被修的东西）。改为调用**被代理的 `@Transactional` service 方法** `subjectService.getTree()` 才是真红 ⇒ 这类「测不到机制」的用例比没有用例更危险。

### 端到端验证（应用以 `huicai_app` 非超级用户连接）

| 检查 | 修复前 | 修复后 |
|---|---|---|
| `GET /api/v1/subjects/tree` | 0 个科目 | **21 个节点** |
| `POST /api/base/voucher/v1/vouchers/page` | 0 条 / total 0 | **3 条 / total 37** |
| 会话层 `GUC=999`（跨租户） | — | **0 行** |
| 会话层 `GUC=1`（本企业） | — | **37 行** |

---

---

## 0.1 2026-10-03 V2 执行结果与**新发现的 P0 缺陷**

### 已完成（全部实测通过）

| # | 项 | 结果 |
|---|---|---|
| 1 | 开发库 Flyway 补齐 | V160 → **V166**（V161~V166 一次性应用成功） |
| 2 | 建角色 | `huicai_app`：`rolsuper=**f**`、`rolbypassrls=**f**`、`login=t` |
| 3 | 接管属主 | `REASSIGN OWNED` 因**扩展对象不可转移**而失败（`cannot reassign ownership ... required by the database system`）⇒ 改为逐对象 `ALTER ... OWNER TO`，并跳过两类对象：**identity 序列**（与表绑定，改表属主时自动跟随）与**扩展成员**（`vector` / `_vector` 等）。最终 **84 张表 + 409 个对象**属主变为 `huicai_app`，`flyway_schema_history` 82 行完好 |
| 4 | 授权 | `CONNECT` / `USAGE,CREATE ON SCHEMA` / `ALL TABLES` / `ALL SEQUENCES` / `ALL FUNCTIONS` + 两组 `ALTER DEFAULT PRIVILEGES` |
| 5 | **RLS 真的生效了** | 以 `huicai_app` 探针：无 GUC=**0**、GUC=1=**37**、GUC=2=**0**；对照超级用户 `huicai` 三种取值全是 **37** |
| 6 | 写入侧防护 | 事务内把某行 `UPDATE ... SET enterprise_id=2` 被数据库拒绝：`new row violates row-level security policy for table "t_voucher"` ⇒ 策略带 `WITH CHECK`，**跨租户写也被拦**（比只读过滤更强） |
| 7 | DDL 能力 | `ALTER TABLE ... ADD COLUMN` 成功（属主身份 OK）⇒ Flyway 以 `huicai_app` 跑 `flyway:info` **BUILD SUCCESS**，82 个迁移校验通过 |
| 8 | 应用能启动 | `mvn spring-boot:run` 以 `huicai_app` 启动成功：`HikariPool-1 Start completed` / Flyway 连上 / `Started HuicaiApplication in 7.754 seconds`；`/api/v1/system/health` 200、`POST /api/v1/auth/login` 200 拿到 token |

### 🔴 P0 缺陷：租户表全部读 0 行（**降权才暴露出来**）

| 请求 | 结果 | 说明 |
|---|---|---|
| `GET /api/v1/system/menu/tree` | 200，**7 项** | 平台表无 RLS ⇒ 正常 |
| `GET /api/v1/system/menu/routes` | 200，**7 项** | 同上 |
| `GET /api/v1/subjects/tree` | 200，**0 个科目** | `t_subject` 有 RLS ⇒ **读不到** |
| `POST /api/base/voucher/v1/vouchers/page` | 200，**0 条 / total 0** | `t_voucher` 有 RLS ⇒ **读不到**（库里实有 37 条） |

**直接证据（抓应用会话的 GUC 值）**：请求期间 `pg_stat_activity` 显示后端连接的 `app.enterprise_id` 为 **NULL**，最后执行的语句是 `SELECT COUNT(*) AS total FROM t_voucher ...` ⇒ **`TenantRlsInitializer` 从未把值设进去**。

**两个待判别的候选根因**（都指向「机制从未真正生效」，而非本次降权操作）：

| # | 候选 | 判别依据 |
|---|---|---|
| A | **切面与事务 advice 顺序不确定** —— `@Around` 在事务**外**执行，`SET LOCAL` 落在自动提交语句里，语句结束即失效（全库无 `@EnableTransactionManagement`，两侧 order 都是 `LOWEST_PRECEDENCE`） | 在事务外执行 `SET LOCAL` 同样表现为 GUC=NULL，与实测一致 |
| B | **该读路径的方法根本没有 `@Transactional`** ⇒ 切面不触发（全库 `@Transactional` 237 处，但并非每个读路径都有） | 该路径的 Service 方法无 `@Transactional` 即可解释 |

**处置**：应用配置已回滚到 `huicai`（`application.yml` / `application-dev.yml` / `docker-compose.yml` 三处默认值），开发环境恢复可用；`huicai_app` 角色与属主**保留**，机制修好后一行配置即可启用。**机制修复属独立开发任务（需 SPEC + TDD）**，未擅自改代码。

**这条缺陷的价值**：它证明 P102 的「三层防线」里，第三层（数据库 RLS）此前**只是文档上的存在** —— 全绿的业务流程掩盖了「兜底层从未真正工作」。与 AGENTS §4.5 第 27 条同源：**对最高权限主体有效的结论，对降权后的真实主体未必成立**。

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

⚠️ **2026-10-03 更新：V2 已执行，但触发了 P0 缺陷（见 §0.1）** —— 角色/授权/属主全部到位且 RLS 确实开始过滤，**但应用层 `TenantRlsInitializer` 从未生效，导致租户表读 0 行**；配置已回滚到 `huicai`，角色保留待机制修复后启用。

- **`t_user` / `t_agency_enterprise` 两张带 `enterprise_id` 的表仍无 RLS**（2026-10-03 实测：70 张有策略、12 张无 RLS，其中这两张带租户列）。属既有设计选择，非本次引入；若要补需单独评估（`t_user` 的跨租户读由 `EnterpriseMembershipChecker` 三源并集覆盖）。
- `huicai` 超级用户**保留**（运维/迁移用），因此「应用不再用超级用户」依赖**配置正确**而非数据库强制 —— 真正的强制手段是把 bootstrap 超级用户的口令也改为环境变量注入（当前 `.env` 已有 `JWT_SECRET` 同类实践）。
- 12 张无 RLS 的表在 `huicai_app` 下同样无策略保护（RLS 只对有策略的表生效）。
- **`REASSIGN OWNED` 走不通**（扩展对象不可转移），已改为逐对象 `ALTER ... OWNER TO` 并跳过 identity 序列与扩展成员；新环境部署时**必须**用这一版脚本，不能直接用 `REASSIGN`。

### 🔴 顺带发现：`SET LOCAL` 结束后的空串会让策略抛 SQL 错（待办）

PostgreSQL 在「曾执行过 `SET LOCAL` 的事务」结束后，该变量读回来是**空串**而非 NULL：

```
begin; set local app.enterprise_id='1'; commit;
select current_setting('app.enterprise_id', true);            --> ''（空串）
select count(*) from t_voucher;                               --> ERROR: invalid input syntax for type bigint: ""
```

而 70 张表的策略谓词是 `enterprise_id = current_setting('app.enterprise_id', true)::bigint` ⇒ **凡复用「曾经处理过请求」的连接、又处于无企业上下文的事务（定时任务 / 初始化），查租户表会抛 SQL 错而不是返 0 行**。全新会话（从未 set 过）读回 NULL，`NULL::bigint` 安全。

- **建议修法**（**未做**，属 schema 变更，需 SPEC + 迁移）：把谓词改成
  `enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint`
  —— 空串归 NULL ⇒ 返 0 行（fail-closed 且不报错）。
- 本轮已把测试断言改成「NULL 或空串都算未设置」，并在断言消息里写明该现象。

## V3 待办

| # | 事项 | 状态 |
|---|---|---|
| 1 | 判定根因 A（切面/事务顺序）或 B（读路径无 `@Transactional`） | ✅ 已判定：两者都存在，均已修（见 §0.2） |
| 2 | 修好后必须补**真库**用例 | ✅ `TenantRlsGucRealDBTest` 3/3 |
| 3 | 把 `huicai_app` 启用写回 3 处配置 + `.env.example` | ✅ 已启用（`application.yml` / `application-dev.yml` / `docker-compose.yml` 默认值 + `.env.example` + `ai/app/config.py`） |
| 4 | 冒烟：`/system/health` + 登录 + 科目树/凭证分页**必须非空** | ✅ 21 个科目节点 / total 37 条凭证 |
| 5 | **策略谓词空串抛错**（见上） | ⏳ **未做**，需迁移 |

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