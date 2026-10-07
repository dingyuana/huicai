DIR_TITLE: 分页插件排序导致跨租户 total 泄漏（COUNT 未注入 enterprise_id）

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

**方案 B：MP 官方租户插件** —— 用 `TenantLineInnerInterceptor` 替换自研拦截器，
与分页插件天然兼容（官方推荐顺序）。改动大，等价于重做三层防线第二层，不建议现在动。

**方案 C：断言式封杀** —— 先加一条守卫测试锁住「total == 本企业行数」，
把存量接口逐个暴露，再配合 A 一次性修复。仅靠 C 无法修复，只暴露不修复。

## 关联

- 本条是 P106 批次 1a-3（D-1）实施过程中的**附带发现**，不属于 D-1 范围，
  故未在 D-1 批次内改动全局配置（铁律 #10 三步闭环）。
- 与 AGENTS §4.5 第 31 条同型：「D-1 改完之后删除条件做反证」才暴露出
  平时被应用层条件掩盖的底层缺陷 —— 兜底层的有效性只有在**移除上层条件**时才可观测。
