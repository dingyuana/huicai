# SPEC-P112 — RLS 兜底缺口闭合（第三层：`t_bank_reconciliation_log`）

> **状态**：✅ **已审核通过并实施完成**（2026-10-08，V1.0）
> **关联需求**：REQ-2026-112（登记状态 `📋 仅登记未修` —— 本 SPEC 附取证证明该状态已漂移，见 §0）
> **前置**：M5b-V2 降权手册（`docs/development/plans/2026-10-01-M5b-role-downgrade-runbook.md`）
> **日期**：2026-10-08

---

## §0 取证结论：REQ-112 主体早已实施，登记状态是假的

原登记理由为「应用连接超级用户 ⇒ 70 张表 RLS 实为摆设」。**实测推翻该前提**。

### §0.1 行为探针（`huicai_app` 真实查询，非只看角色属性）

按 §4.5 第 27 条「**看行为，不只看属性**」执行：

| 探针 | 实测 | 判读 |
|---|---|---|
| `huicai_app` 属性 | `rolsuper=f` `rolbypassrls=f` `login=t` | 非超管 ✅ |
| 83 张 `t_*` 表 | RLS 开 **71** / FORCE 开 **71** | 兜底层覆盖 71 张 |
| 无 GUC → `count(*)` on `t_subject` | **0** | fail-closed 生效 ✅ |
| `enterprise_id=1` | **23** | 正常命中 ✅ |
| `enterprise_id=999999` | **0** | 跨租户读被拒 ✅ |
| SET LOCAL 结束后新事务（空串形态） | **0**，无报错 | V167 硬化生效 ✅ |
| **对照** 超管 `huicai` 设 `999999` | **44**（不被过滤） | 反证 RLS 确在起作用 ✅ |

配置亦已切换：`application.yml:24` / `application-dev.yml:9` / `docker-compose.yml:122` 三处均为 `${DB_USERNAME:huicai_app}`。

**⇒ REQ-112 的修复动作（新建非超管角色 + `ALTER ... OWNER` 接管属主 + 机制修复）在 2026-10-03 已完成并端到端验证。本 SPEC 不重复实施，只做剩余缺口。**

### §0.2 真实残留：12 张 `t_*` 表无 RLS

按「**有无 `enterprise_id` 列**」切开（判据来自 §4.5 第 42 条：A/B 两类理由根本不同，不可混为一谈）：

| 类 | 表 | `enterprise_id` | 可否补策略 |
|---|---|---|---|
| **C1 可补** | `t_bank_reconciliation_log` | 有（`nullable=YES`） | ✅ 本 SPEC 唯一目标 |
| **B 语义豁免** | `t_agency_enterprise` | 有 | ❌ 见 §2.1 |
| **B 语义豁免** | `t_user` | 有（3/4 行为 NULL） | ❌ 见 §2.2 |
| **A 无隔离列** | `t_agency` / `t_agency_user` / `t_audit_log` / `t_enterprise` / `t_menu` / `t_role` / `t_role_menu` / `t_sys_config` / `t_user_role` | 无 | ❌ 维持 D-2b 裁定 |

---

## §1 目标与范围

**唯一目标**：为 `t_bank_reconciliation_log` 补齐 RLS 策略 + FORCE，使第三层防线的覆盖面从 71 张增至 72 张。

**明确不做**（超出授权，需另行立项）：
- `t_user` 的 NULL 数据修复与策略补齐
- 9 张无隔离列表的补列
- `t_agency_enterprise` 的策略补齐

---

## §2 为何另两张不能补（逐条实测，非推断）

### §2.1 `t_agency_enterprise` —— 归属语义非上下文

`AgencyEnterpriseEntity` **不继承 `BaseEntity`、类内无任何 `@TableField`** ⇒ 无 fill 机制（§4.5 第 34 条同型）⇒ `enterprise_id` 由应用层手填。表语义为「代理可服务的客户企业」清单，代理登录后须**跨客户**查询。若加 `enterprise_id = ctx` 策略，代理在服务客户 B 时读不到客户 A 的关联行 ⇒ 功能破坏。

### §2.2 `t_user` —— NULL 存量 + 登录路径依赖无上下文读

**实测 `t_user` 4 行明细**：

```
id=1 admin       SUPER_ADMIN  enterprise_id=1
id=2 accountant01 AGENCY     enterprise_id=NULL   ← V114 seed agency users
id=3 reviewer01  AGENCY      enterprise_id=NULL   ← V114 seed agency users
id=4 assistant01 AGENCY      enterprise_id=NULL   ← V114 seed agency users
```

两条独立的阻断理由：

1. **NULL 存量**：策略谓词 `enterprise_id = ctx` 对 NULL 恒 false ⇒ 3 行对**所有人**不可见。
2. **登录链路**：`UserServiceImpl:98 getByUsername` → `userMapper.selectByUsername(username)`，在**无企业上下文**时按用户名查人（登录尚未确定企业）。无 GUC 时 RLS 返 0 行 ⇒ **全员无法登录**。

⇒ 与 P106 P0 修复时保留 `t_user` 在 `SHARED_TABLES` 的理由同源（SPEC §V2.4 已记录），**不是新问题，是同一豁免的 DB 层延续**。

### §2.3 三条路都不通（§4.5 第 43 条「先确认数据真的都带那列的值」）

| 方案 | 后果 |
|---|---|
| A 严格 `enterprise_id = ctx` | 3 行 NULL 消失 + 全员无法登录 |
| B 放行 `OR enterprise_id IS NULL` | NULL 行对**所有**企业可见 ⇒ 跨租户泄漏 |
| C 先修数据再加策略 | 代理用户的 `enterprise_id` 按设计就是「当前服务客户」，补成固定值即**错误的归属**；且属 DDL + 数据修复，超本 SPEC 授权 |

---

## §3 输入契约 / 输出契约 / 状态流转 / 异常处理（四段模板）

### 输入契约
- 仅一个 Flyway 迁移 `V173`，幂等（`ALTER TABLE ... ENABLE/FORCE` + `DROP POLICY IF EXISTS` + `CREATE POLICY`）。
- **不依赖任何应用层改动**（该表已在 `SHARED_TABLES` 之外 ⇒ 第二层拦截器已注入 `enterprise_id`；本 SPEC 只补第三层）。

### 输出契约
- `pg_policies` 中 `t_bank_reconciliation_log` 有 1 条 `enterprise_policy`，`cmd=ALL`。
- `relrowsecurity=t` 且 `relforcerowsecurity=t`。
- 谓词与既有 71 张**逐字一致**（复用 `t_subject` 的写法，避免出现第二种风格）。

### 状态流转
- 无业务状态机改动。表本身无 `status` 语义。

### 异常处理
- 迁移用 `IF EXISTS` / `IF NOT EXISTS` 保证可重跑。
- **谓词必须用 V167 硬化写法** `NULLIF(current_setting('app.enterprise_id', true), '')::bigint`，否则「曾 `SET LOCAL` 的会话」会抛 `invalid input syntax for type bigint: ""`（§4.5 第 28 条）。

---

## §4 BDD 验收场景

| # | Given | When | Then | 类型 |
|---|---|---|---|---|
| AT-112-1 | `huicai_app` + 无 GUC | `SELECT count(*) FROM t_bank_reconciliation_log` | `0` | 负向 |
| AT-112-2 | `huicai_app` + `enterprise_id=1` | 同上 | `>0`（有种子行） | 正向 |
| AT-112-3 | `huicai_app` + `enterprise_id=999999` | 同上 | `0` | 负向 |
| AT-112-4 | `huicai_app` 曾 `SET LOCAL` 后开新事务 | 同上 | `0` 且**不抛 SQL 错** | 负向（V167 硬化） |
| AT-112-5 | 超管 `huicai` + `enterprise_id=999999` | 同上 | **不被过滤** | 反证 |
| AT-112-6 | `huicai_app` + `enterprise_id=1` | `INSERT` 一行 | 成功；切 `=999999` 后**读不到** | 写路径 |
| AT-112-7 | 守卫类 | 检查 12 张无 RLS 表清单 | 与 §0.2 台账一致（防漂移） | 结构 |

**门禁要求**：每个场景一个 `@Test`；必须**双向反证**（去掉策略 ⇒ 跨租户用例转红；把谓词改回未硬化写法 ⇒ AT-112-4 转红）。

---

## §5 竞品对标（铁律 #15）

- **用友 U8C / 金蝶云星空**：均以「应用层数据权限 + 数据库层 RLS」双层实现，RLS 谓词统一为「当前账套 = 目标账套」，且对**平台级字典表**（角色/菜单/功能权限）明确排除在账套隔离之外。
- 结论：与本项目「`t_role`/`t_menu`/`t_sys_config` 保持平台级」的 D-2b 裁定一致，**不需要**为了"全表都开 RLS"而给平台表硬塞 `enterprise_id`（那会污染语义）。

---

## §6 风险与遗留

| 风险 | 处置 |
|---|---|
| 该表为企业维度日志，若某条写入路径绕过 fill ⇒ 该行对所有人不可见 | 该 Entity `extends BaseEntity` 且 `enterpriseId` 带 `fill=INSERT`（已实测），`MyMetaObjectHandler` 无条件覆盖为上下文企业 ⇒ 不会产生 NULL |
| 未来有人给 `t_user`/`t_agency_enterprise` 加策略 ⇒ 全员无法登录 | §4 AT-112-7 结构守卫把 12 张清单钉住；新增开策略必须同步改台账并显式说明为何不能加 |
| L2 Testcontainers 连接角色是超管 ⇒ RLS 恒被绕过 | 本 SPEC 的行为断言**必须**走非超管探针（`CREATE ROLE ... NOSUPERUSER NOBYPASSRLS` + `SET LOCAL ROLE`），否则恒绿假绿（§4.5 第 33 条） |

---

## §7 裁定与实施结果（2026-10-08）

### §7.1 老丁裁定（3 项全部通过）
1. ✅ 同意**只补 `t_bank_reconciliation_log` 一张**，另两张按 §2 论证排除。
2. ✅ `t_user` 的 3 行 NULL 数据修复与策略补齐**单独立项**（本 SPEC 不含）。
3. ✅ 9 张无隔离列表维持 D-2b 裁定「平台级不加列」。

### §7.2 实施内容
- **V173**（`V173__p112_bank_reconciliation_log_rls.sql`）：`ENABLE` + `FORCE ROW LEVEL SECURITY`
  + `DROP POLICY IF EXISTS` / `CREATE POLICY enterprise_policy`，谓词与既有 71 张**逐字一致**
  （含 V167 的 `NULLIF(...,'')::bigint` 硬化），幂等可重跑。
- **新增守卫** `BankReconLogRlsRealDBTest`（5 例，覆盖 AT-112-1~7 七个场景）。

### §7.3 TDD 与反证证据
| 阶段 | 结果 |
|---|---|
| RED（V173 未写） | 5 例 **3 红** —— 结构守卫 `expected <1> but was <0>`、两条跨企业各 `expected <0> but was <1> ⇒ RLS 未生效` |
| GREEN（V173 落地） | **5/5 绿** |
| 反证①移走 V173（`mvn clean`） | **3 红**，红灯来自 `AssertionFailedError` 而非编译失败 |
| 反证②谓词去 `NULLIF` | AT-112-4 报 `ERROR: invalid input syntax for type bigint: ""` ⇒ 证明 V167 硬化是必要的，不是抄来的装饰 |
| 还原后 | **5/5 绿** |

### §7.4 真实库行为复核（开发库，非仅 Testcontainers）
RLS 覆盖 **71 → 72**；`enterprise_policy` 谓词逐字一致；本企业可见、跨企业 0 行、空串 0 行；
**`t_user` 策略仍未开、4 个用户仍全部可见 ⇒ 登录链路零影响**。

### §7.5 门禁
L1 `1665/0/0/5`（clean 口径 `INSTR 0.4914 / BRANCH 0.4157 / METHOD 0.6787`，阈值 49/41/67，
`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）—— 真库用例按设计不进 L1；
L2 `2129/0/0/5`（+5，`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；
前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests`；三静态门禁全过。

### §7.6 本 SPEC 留下的后续（已登记，不在本轮）
- **REQ-2026-112 状态更正**：登记册现写 `📋 仅登记未修`，实测主体早已实施（M5b-V2 完成于 2026-10-03）
  ⇒ 状态漂移，由 REQ-2026-112 状态更正一并处理。
- **`t_user` NULL 数据修复 + 策略补齐**：单独立项。
- 9 张无隔离列表：维持平台级裁定。

---

## §7 待老丁裁定项

1. 是否同意**只补 `t_bank_reconciliation_log` 一张**，另两张按 §2 论证排除？
2. `t_user` 的 NULL 数据修复（3 行 `AGENCY` 种子）与策略补齐，是否**单独立项**？（本 SPEC 不含）
3. 9 张无隔离列表是否维持 D-2b 裁定「平台级不加列」？
