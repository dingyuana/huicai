# P108 SPEC — RLS 策略谓词空串硬化（`NULLIF(current_setting(...), '')::bigint`）

> **版本**：V1.0 | **最后修改**：2026-10-03 | **作者**：opencode
> **编号**：HUICAI-SPC-P108 | 优先级：**P1** | 状态：✅ 已实施
> **来源**：P102-M5b 降权实测（REQ-2026-129 的遗留项，见 M5b 手册「顺带发现」小节）
> **关联需求**：REQ-2026-137 | **前置**：V166 已落地；应用已切非超级用户 `huicai_app`
> **test_ref**：`TenantRlsRealDBTest`、`TenantRlsGucRealDBTest`
> **改动范围**：仅 DDL（70 张表的策略谓词），**不改任何 Java 代码**

---

## 0. 缺陷背景（已实测，非推演）

现有 70 张租户表的策略谓词逐字为（V106 建立、V156 补齐 4 张）：

```sql
USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint)
```

`SET LOCAL` 在事务结束后，PostgreSQL 把该 GUC 读回的是**空串**而非 NULL：

```
begin; set local app.enterprise_id='1'; commit;
select current_setting('app.enterprise_id', true);   --> ''（空串）
select count(*) from t_voucher;                      --> ERROR: invalid input syntax for type bigint: ""
```

⇒ **凡「无 `EnterpriseContextHolder` 的事务」（定时任务 / 系统初始化 / 批处理）复用了
「刚刚处理过请求」的连接池连接，查询租户表会抛 SQL 错（fail-500），而不是返 0 行。**
这与 RLS 的 fail-closed 意图相反：把「查不到数据」变成了「接口 500」。

全新会话（从未 `SET` 过）读回 NULL，`NULL::bigint` 安全 —— 所以**只有复用连接时才炸**，
且本地单跑一个用例时不复现，本轮是在真实 CI 与真实应用冒烟中暴露的。

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| `app.enterprise_id` | GUC（会话/事务级） | 取值形态有三种：具体数字 / `''`（曾 `SET LOCAL` 过）/ NULL（全新会话），**谓词必须对三者都安全** |
| 租户表 | 表 | 仅含 `enterprise_id` 列且已 `ENABLE + FORCE ROW LEVEL SECURITY` 的 70 张 |
| 策略 | `enterprise_policy` | `CREATE POLICY ... USING (...)`，未显式给 `WITH CHECK`（PostgreSQL 自动复用 USING 表达式），**本次保持此形状不变** |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 谓词硬化 | 70 张表的 `enterprise_policy` 谓词改为 `enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint` |
| 空串不再报错 | 「`SET LOCAL` 过的事务结束 → 复用该连接 → 无上下文事务查租户表」返回 **0 行**，不抛 SQL 错 |
| 行为不回退 | 本企业 GUC 仍返回本企业行（实测 37 行）；跨租户 GUC 仍返回 0 行；跨租户 `UPDATE` 仍被拒 |
| 幂等 | 重复执行结果相同；只重写「仍是旧形状」的策略，不动其它策略 |

## 3. 状态流转

```
未设过 GUC(NULL)  ──旧谓词──▶ 0 行（安全）
SET LOCAL 后复用('') ──旧谓词──▶ ERROR 23522/22P02（fail-500）★缺陷
SET LOCAL 后复用('') ──新谓词──▶ 0 行（fail-closed，安全）
GUC=本企业          ──新谓词──▶ 本企业行（不变）
GUC=他企业          ──新谓词──▶ 0 行（不变）
```

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| 表上没有 `enterprise_policy`（12 张无 RLS 的平台表） | **不动**；V167 只遍历「已有该策略且谓词是旧形状」的表 |
| 策略被人工改过（谓词已含 `NULLIF`） | 跳过（`NOT LIKE '%NULLIF%'`），不覆盖人工改动 |
| `NULLIF` 结果为 NULL | `enterprise_id = NULL` 为 NULL ⇒ 不返回任何行（这正是我们要的 fail-closed） |

## 5. BDD 行为契约

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-108-1 | 非超级用户连接，`SET LOCAL app.enterprise_id='1'` 后事务结束 | 同一连接上查询 `t_voucher` | 返回 **0 行**，日志无 `invalid input syntax` | 真实 DB（`TenantRlsRealDBTest`） |
| AT-108-2 | 非超级用户连接，`SET LOCAL app.enterprise_id='1'`（事务内） | 查 `t_voucher` | 返回本企业行（与硬化前一致） | 真实 DB |
| AT-108-3 | 全新会话（从未 `SET` 过） | 查 `t_voucher` | 返回 0 行（NULL::bigint 仍安全） | 真实 DB |
| AT-108-4 | 迁移已应用 | 统计策略谓词形状 | 含 `current_setting` 且**不含** `NULLIF` 的策略数 = **0** | 脚本 |

## 6. 竞品对标（铁律 #15）

| 维度 | PostgreSQL 最佳实践 | 本项目原做法 | 修法 |
|---|---|---|---|
| RLS + 会话变量 | 官方文档示例即 `USING (tenant = current_setting('app.tenant')::text)`，但**要求每次查询前必设**；社区更稳的写法是 `NULLIF(current_setting(..., true), '')` 或 `COALESCE(..., '0')` | 直接 `::bigint`，未考虑「已 `SET LOCAL` 过」的中间态 | 加 `NULLIF` |
| 失败语义 | 安全组件应 fail-closed（拒访问）而非 fail-open | 实际是 **fail-500**（既非 open 也非 closed） | 落到 fail-closed |

## 7. 风险与不在范围

- **风险**：改 70 张表的策略需要逐表 `DROP + CREATE`，事务内执行；耗时极短（实测 <1s），且期间应用并发读会短暂拿不到策略 —— 因此迁移应安排在启动阶段（Flyway 在应用启动时执行，此时无并发读）。
- **不在范围**：不给 `t_user` / `t_agency_enterprise` 两张带 `enterprise_id` 的表补 RLS（属既有设计选择，另议）；不改任何 Java 代码。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-10-03 | opencode | 初稿：四段模板 + BDD + 竞品对标；V167 迁移内容与验证清单 |

## 实施结果（2026-10-03）

| 项 | 内容 |
|---|---|
| 迁移 | `V167__harden_rls_predicate_empty_string.sql`：动态遍历「已有 `enterprise_policy` 且谓词仍是旧形状」的表并 `DROP + CREATE`，**实测重写 70 张**（幂等：谓词已含 `NULLIF` 的会被跳过） |
| 开发库实测 | 空串场景 `count(*)` 由 **`ERROR: invalid input syntax for type bigint: ""`** 变为 **0 行**；本企业 GUC=1 仍 **37 行**、他企业 GUC=999 **0 行**、全新会话 **0 行**；谓词形状统计 `hardened=70 / still_old=0` |
| **红→绿反证** | 把 V167 从 `src` **与** `target/classes` 同时移除后重跑 `TenantRlsRealDBTest#emptyGucReturnsZeroRowsInsteadOfError` ⇒ **`ERROR: invalid input syntax for type bigint: ""` 变红**；还原后 6/6 绿（反证手法同 AGENTS §4.2 第 19 条：只删 src 会被 target/classes 的副本“补”回来而拿到假绿） |
| 新增用例 | `TenantRlsRealDBTest#emptyGucReturnsZeroRowsInsteadOfError`（AT-108-1）：显式 `SET ROLE` 到非超级用户 → 先在事务内 `SET LOCAL` 再提交（连接进入「空串」形态）→ 断言查租户表**返 0 行**。Superuser 走不到谓词（绕过 RLS），故必须切角色才能测到 |
| 全量回归 | L2 `2027/0/0/5` + `All coverage checks have been met` |