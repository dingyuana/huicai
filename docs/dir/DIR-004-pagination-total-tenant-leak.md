DIR_TITLE: 分页插件排序导致跨租户 total 泄漏（COUNT 未注入 enterprise_id）

> **状态**：✅ **已闭环（2026-10-07，方案 A 实施完成）** —— 老丁裁定采方案 A，`MyBatisPlusConfig` 拦截器顺序已调整为「企业/部门权限 → 分页 → 乐观锁」，新增 `PaginationTotalTenantIsolationRealDBTest`（2 例）钉死，反证「还原顺序 ⇒ 2 条转红」已实测。

## 一句话结论

`MyBatisPlusConfig` 把 `PaginationInnerInterceptor` 注册在
`EnterpriseDataPermissionInterceptor` **之前**，导致**分页 total 走 COUNT 时不注入
`enterprise_id`** ⇒ 任何未显式带企业条件的分页接口都会**返回全局条数**，
而 records 仍是本企业的 —— 对外表现为「总条数/总页数跨租户泄漏」+「翻页错乱」。

## 实测证据（非推演）

探针（临时 Testcontainers，用已种子的 `t_subject`，**未加任何企业条件**）：

| 上下文企业 | total | records |
|---|---|---|
| 990001（该企业无科目） | **43** | 0 |
| 1 | 43 | 10 |

`total=43 / records=0` 即证明 COUNT 未被隔离，而 SELECT 被隔离了。
同型现象在 `t_classification_rule` 上复现：反证 D-1 时把 `page()` 的
`enterprise_id` 过滤删掉，探针打出 `total=10 / records=1`（10 = 全表行数）。

## 根因

MyBatis-Plus 的 `MybatisPlusInterceptor` 按注册顺序调用内层拦截器的 `willDoQuery`。
`PaginationInnerInterceptor` 先执行：它自己拼 COUNT 并用**传入的 executor 直接执行**，
该执行路径**不再回到拦截器链**，因此后续
`EnterpriseDataPermissionInterceptor#beforeQuery` 无机会改写 COUNT 的 SQL。
当前顺序（`MyBatisPlusConfig:24-30`）为：

```
Pagination → OptimisticLocker → EnterpriseDataPermission → DataPermission
```

企业隔离排在分页之后 ⇒ COUNT 永远拿不到 `enterprise_id`。

## 为什么至今无人发现

1. **应用层普遍自己带条件**：多数 Service 已按上下文加了 `enterprise_id`，
   于是 COUNT 与 SELECT 都带条件，总数恰好正确 ⇒ 缺陷被掩盖。
   D-1 恰好是「第一次把 `page()` 里的条件删掉看看」的场合，才暴露出来。
2. **断言普遍只查 records 不查 total**：AGENTS §4.4 第 16 条已登记「断言必须落到内容」，
   但分页场景下「内容」被普遍理解为 records，total 无人断言。
3. **Mock 测不出**：`total` 由分页拦截器在运行时计算，Mock mapper 直接返回 Page 对象，
   永远看不到 COUNT（§4.3 第 7 条）。

## 建议修法（待老丁决策，本次未实施）

**方案 A（推荐）：调整插件顺序** —— 把企业/数据权限拦截器移到分页之前：

```
EnterpriseDataPermission → DataPermission → Pagination → OptimisticLocker
```

- 优点：改动 4 行，一次性修好**所有**分页接口。
- 风险：属**全局行为变更**，需评估是否影响既有查询与优化器换算（`optimizeCountSql`）。

### ✅ 方案 A 实施记录（2026-10-07）

**机理已用反编译确认**（MP 3.5.7 `mybatis-plus-extension`）：`MybatisPlusInterceptor.intercept`
按注册顺序对每个拦截器依次调用 `willDoQuery` → `beforeQuery`；而
`PaginationInnerInterceptor.willDoQuery` 会**自行拼 COUNT 并直接用传入的 executor 执行**，
该路径**不再回到拦截器链**。故「分页排在条件注入之前」⇒ COUNT 生成时
`boundSql` 尚未被注入 `enterprise_id`。

**改动**：`MyBatisPlusConfig#mybatisPlusInterceptor` 顺序改为
企业权限 → 部门权限 → 分页 → 乐观锁（乐观锁只作用于 UPDATE，置后不影响）。

**守卫**：`PaginationTotalTenantIsolationRealDBTest`（2 例）——
- `totalMustNotLeakWhenCurrentEnterpriseHasNoRows`：上下文企业 990002 无科目 ⇒
  `total` 与 `records` 必须同时为 0。**修复前实测 `total=43 / records=0`**。
- `totalMustCountOnlyCurrentEnterpriseRows`：企业 1 造 2 行 + 企业 990003 造 3 行，
  切到 990003 ⇒ `total` 与 `records` 必须同时为 3。**修复前实测 `total=5 / records=3`**。
- ⚠️ **守卫刻意不加任何应用层企业条件、直接调 mapper** ——
  否则缺陷会被应用层条件掩盖，守卫就退化为「对本次改动不敏感」的假绿（§4.5 第 40 条）。

**反证**（`mvn clean test`，排除增量编译未重编这一假来源）：
还原旧顺序 ⇒ **2 条同时转红**，报错信息正是 `total 跨租户泄漏：…total 却为 43/5`；
恢复修复后顺序 ⇒ 2/2 绿。

**全量回归**：L1 `1663/0/0/5`（clean 口径 49.14/41.55/67.88，阈值 49/41/67，
`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；
L2 `2104/0/0/5`（+2 即本守卫；`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；
前端 `vue-tsc` exit 0 + vitest 26 files / 265 tests 全绿；
`check_tenant_fixture.py` / `check_entity_status_massassignment.py` exit 0；
`check-entity-schema.mjs` ✅。**无既有测试因重排序而转红。**

**方案 B：MP 官方租户插件** —— 用 `TenantLineInnerInterceptor` 替换自研拦截器。
改动大，等价于重做三层防线第二层，**本次不采纳**。

**方案 C：断言式封杀** —— 仅加守卫测试只暴露不修复。**本次已作为 A 的配套一并落地**
（`PaginationTotalTenantIsolationRealDBTest`），使其成为长期回归锁。

## 关联

- 本条是 P106 批次 1a-3（D-1）实施过程中的**附带发现**，不属于 D-1 范围，
  故未在 D-1 批次内改动全局配置（铁律 #10 三步闭环）。
- 与 AGENTS §4.5 第 31 条同型：「D-1 改完之后删除条件做反证」才暴露出
  平时被应用层条件掩盖的底层缺陷 —— 兜底层的有效性只有在**移除上层条件**时才可观测。
