# P102 SPEC — 安全与权限基座加固（租户隔离 + 端点鉴权 + DTO 隔离）

> **版本**：V1.2（实施回写：修正 5 处与实现不符的前提，补 M1~M5a 落地结果） | **最后修改**：2026-09-30 | **作者**：opencode
> **编号**：HUICAI-SPC-P102 | 优先级：**P0（商用门槛）** | 状态：🚧 部分实施（M1~M5a 已交付，M5b 待做）
> **来源**：P101 总纲 M2/M3；四路审计交叉最严重项
> **关联需求**：REQ-2026-129 | **前置**：无 | **test_ref**：`TenantIsolationSecurityTest`、`TenantIsolationHttpTest`、`SystemClearAuthorizationTest`、`EnterpriseIdInjectionTest`、`DataPermissionFailClosedTest`、`EnterpriseDataPermissionFailClosedTest`
> **排除**：AI 功能（老丁 2026-09-29 指示）
> **V1.1 修订说明**：审核发现两处设计缺陷已修正 —— ①原「查 `t_agency_user_enterprise` 判成员」会**锁死 3 个种子账号**（`accountant01`/`reviewer01`/`assistant01` 的 `t_user.enterprise_id` 为 NULL 且无成员记录，`admin` 是 SUPER_ADMIN）→ 改为 §1.1 三源并集；②原 SPEC 漏掉**比 header 更直接**的越权路径：`@RequestBody XxxEntity` 48 处（`MyMetaObjectHandler:20` `strictInsertFill` 仅在 null 时填，`BaseEntity.enterpriseId` 是可写真实字段）→ 补 §2.2 入参封禁与铁律 #13 DTO 隔离分批
> **V1.2 修订说明（实施期回写，全部为实测推翻，非纸面推演）**：
> ① **权限列名是 `t_menu.permission`，不是 `permission_code`** —— V1.0/V1.1 称「当前无此列、需补列并回填」不成立：`MenuEntity` 已正确映射 `@TableField("permission")`，且 43/43 行权限码**早已回填**。**不需要加列**。
> ② **V160 已被 P107 D1 占用**（`add disputed to customer statement status`），权限码种子改用 **V163**。
> ③ **三源并集的第二源必须两跳**：`t_agency_user_enterprise.agency_user_id` 指向的是 `t_agency_user.id`，**不是** `t_user.id`。该表上现成的 `countByUserId` / `selectByUserId` 直接拿 `#{userId}` 比 `agency_user_id`，**语义是错的**，不得使用。正确链路 `t_user → t_agency_user.user_id → t_agency_user_enterprise.agency_user_id`。
> ④ **三源并集仍会锁死 `reviewer01`**：V1.1 以为修正后即可放行，实测 `accountant01`(agency_user=2)/`assistant01`(agency_user=4) 都有企业 1 授权行，唯独 `reviewer01`(agency_user=3) **没有任何行**，且 `enterprise_id` 为 NULL、`user_type` 是 `AGENCY` 而非 `SUPER_ADMIN` ⇒ 三源全不命中，一个企业都进不去。已由 **V165** 补齐种子行（不放宽校验规则）。
> ⑤ **清库端点是 9 个不是 8 个**（`SystemClearController` 实测：clearBankStatements / clearInvoiceRecords / clearVouchers / clearReportData / clearBusinessDocs / clearReceivables / clearPayables / clearSettlements / clearAll），原「8 个清库端点」漏计。

---

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| JWT | HttpOnly token | 含 `userId`/`enterpriseId`/`roles`，为**唯一身份来源** |
| `X-Enterprise-Id` | 请求头 | 须满足 §1.1 成员规则；否则 403 且不回退、不泄露他企业存在性 |
| 应用 DB 角色 | PostgreSQL | 须 `NOBYPASSRLS`；每业务事务 `SET LOCAL app.enterprise_id = <ctx>` |
| 权限码 | `t_menu.permission` | **列名是 `permission`（V1.2 订正，原误写 `permission_code`）**；`MenuEntity` 已映射，43/43 行已回填，**无需加列**；`@PreAuthorize` 引用的码必须在库中存在 |
| 角色权限 | `t_role`（ADMIN/FINANCE_MGR/CASHIER/OPERATOR/ACCOUNTANT） | 与权限码多对多 |

### 1.1 成员校验三源并集（V1.1 修正，替代原单表规则）

**允许切换到企业 E 的条件**（任一成立）：

```
member(user, E) =
      t_user.enterprise_id = E                          -- 源1 直属企业
   OR ∃ (t_user → t_agency_user.user_id = user
         → t_agency_user_enterprise.agency_user_id, E, deleted=0)   -- 源2 代理授权（V1.2：必须两跳）
   OR t_user.user_type = 'SUPER_ADMIN'                 -- 源3 超级管理员全局切换
```

- **实测依据**：`t_agency_user_enterprise` 仅 2 行；`accountant01`/`reviewer01`/`assistant01` 的 `t_user.enterprise_id` 为 **NULL**（若只查成员表将**被锁死**）；`admin` 为 `SUPER_ADMIN`（须保留其切换能力）。
- 🔴 **V1.2 补充 —— 源 2 必须两跳**：`t_agency_user_enterprise.agency_user_id` 引用的是 `t_agency_user.id`，而 `t_user` 与 `t_agency_user` 是**两张表**（`t_agency_user.user_id` 才是指向 `t_user.id` 的外键）。该表上现成的 `countByUserId(#{userId})` / `selectByUserId(#{userId})` 拿 `t_user.id` 去比 `agency_user_id`，**语义错误**，实现时不得使用（已在新类 `EnterpriseMembershipChecker` 的注释中标注）。
- 🔴 **V1.2 补充 —— 修正后仍会锁死 1 个种子账号**：`accountant01`(agency_user=2)、`assistant01`(agency_user=4) 均有企业 1 授权行，唯独 `reviewer01`(agency_user=3) **无任何行**，且 `enterprise_id` 为 NULL、`user_type=AGENCY`（非 SUPER_ADMIN）⇒ 三源逐一不命中。修复前该账号反而能进任意企业（那正是本次要堵的漏洞），修复后若无数据补齐则**一个企业都进不去**。**V165** 已按同侪账号口径补齐该种子授权行；`TenantIsolationSecurityTest#agencySeedAccountsNotLockedOut` 用真实种子数据锁死此约束。
- **SUPER_ADMIN 切换须审计留痕**（记录 from→to + 操作人）：`EnterpriseSwitchAuditService`（`REQUIRES_NEW` 独立事务）。⚠️ 注意 `AuditLogEntity` **并不存在** `before_data`/`after_data`/`entity_type` 对应属性（真实列存在但实体未声明，`requestParams`/`oldSnapshot`/`newSnapshot` 被标 `exist=false` 幽灵字段）—— 这正是 P103 待修的审计失效本身，故留痕暂用显式列名写入，P103 修好后改回 Mapper。

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 租户隔离 | 跨企业读写 100% 拒绝（403）；RLS 谓词真实生效；应用角色 `rolbypassrls=false` |
| **入参封禁** | **任何 `@RequestBody` 入参都不得携带 `enterpriseId`/`id` 归属字段生效**（见 §2.2） |
| 端点鉴权 | 清库/反结账/制证/审核/红冲/科目等高危端点全部 `@PreAuthorize`；无权限码 → 403 |
| 审计前置 | 端点鉴权与权限码体系就绪（P103 复用） |
| 自检 | `PermissionCodeAuditTest` 绿：任一 `@PreAuthorize` 权限码在 `t_menu` 有对应项 |

### 2.2 入参 `enterpriseId` 封禁（V1.1 新增，A2 审核发现）

**风险实测**：`@RequestBody XxxEntity` 共 **48 处 / 25 个 Controller**（`base` 13 + `sme` 12；`CustomerController:55,61`、`EmployeeController:52,58` 等）。`MyMetaObjectHandler:20` 用 `strictInsertFill`（**仅当字段为 null 才填**），而 `BaseEntity:23` 的 `enterpriseId` 是 `fill = FieldFill.INSERT` 的**可写真实字段** ⇒ 请求体传 `{"enterpriseId": 999}` 即**绕过上下文直写他人租户**，**不依赖任何特制 header**（比 §1 的 header 越权更隐蔽）。

**修法（分两层，L1 立即、L2 分批）**：

| 层 | 措施 | 覆盖 | 落地状态 |
|---|---|---|---|
| **L1 兜底（全量、一次生效）** | 在 `MyMetaObjectHandler` 增**强制覆盖**：`enterpriseId` 无条件 `setFieldValByName(..., EnterpriseContextHolder.get())`，忽略入参值 | 48 处全覆盖，1 个类改动 | ✅ **已实施**。实际用 `metaObject.setValue` + `hasSetter` 守卫（无该字段的实体不受影响） |
| **L2 治本（分批）** | 按铁律 #13 迁移为 DTO/Param：`@RequestBody XxxCreateReq`/`XxxUpdateReq`，**不含** `enterpriseId` 字段 | 25 个 Controller，按 §7 分批 | ⏳ 未开始 |

> L1 用「强制覆盖」而非「拒绝」：改动面小、一次性堵死写入路径；L2 再从协议层消除该字段。

**实施补充（V1.2）**：
- `strictInsertFill` 换成无条件 `setValue` 是必需的：前者**仅当字段为 null 才填**，而 `BaseEntity.enterpriseId` 是 `FieldFill.INSERT` 的可写真实字段。
- **上下文为 null 时保持原值不动**（定时任务 / 系统初始化等无登录态路径由调用方显式指定企业）。该分支不可被外部请求触达的前提已固化为两条**前提守卫测试**：① `SecurityConfig` 对全部请求 `.authenticated()`（白名单恰为登录/健康检查/文档 9 条）② 白名单为精确集合断言（多一条即红）。
- 🔴 **对测试造数的影响**：`entity.setEnterpriseId(2L)` + `mapper.insert()` 在上下文为企业 1 时**不再写 2**，跨租户隔离用例的前提会被静默改写（已实测导致 `DataIsolationAuditTest` / `BankStatementDataIsolationTest` 各 1 条转红，且失败信息表现为「企业B 的数据不应被查到」，极易误判为隔离失效）。已在 `AbstractMapperTest` 补**显式出口** `withoutEnterpriseContext(Runnable)` 供隔离类测试造数，**不放宽生产逻辑**。

## 3. 状态流转

```
请求 ──> JwtFilter(解析JWT) ──> X-Enterprise-Id 三源并集校验 ──> @PreAuthorize 权限校验 ──> 业务
         │                              │                          │
         │                        越权 → 403(不回退)          无权限码 → 403
         ▼
     业务事务: MyMetaObjectHandler 强制 enterpriseId=ctx (L1) + SET LOCAL app.enterprise_id (RLS)
     ──> 双重过滤 ──> 返回
```
任一校验失败即终止，**不得降级放行**（fail-closed）。

## 4. 异常处理

| 场景 | 处理 | 落地状态 |
|------|------|---------|
| `X-Enterprise-Id` 越权 | `BusinessException(403)`，不回退 JWT 值，**不区分「企业不存在」与「无权限」** | ✅ 已实施 |
| 入参携带 `enterpriseId` | L1：**静默覆盖为上下文值**（不报错，避免破坏存量前端）；L2：DTO 层无此字段，Jackson 未知字段忽略 | ✅ L1 已实施 |
| `SET LOCAL` 失败 | 抛异常终止事务（安全组件 fail-closed，禁 fail-open） | ⏳ 属 M5b |
| SQL 解析/注入异常 | 事务中止并抛错（原 `DataPermissionInterceptor` 4 处 `catch→return null` 需改） | ✅ 已改，**实测是 3 处**（`:112` 注入失败、`:125` 写回 BoundSql 失败、`:273` 查权限失败→返回 null）；另加 `EnterpriseDataPermissionInterceptor` 1 处 |
| 权限码缺失 | 启动自检失败（`PermissionCodeAuditTest`），防「静默无鉴权」 | ✅ 以 `SystemClearAuthorizationTest` 落地（反射断言注解存在 + 权限码在 `t_menu` 真实存在） |

**🔴 V1.2 新增发现：租户/数据隔离链路上原有 4 处 fail-open**（原 SPEC 只提到 `DataPermissionInterceptor`，实测更多，且都在安全组件上）：

| 位置 | 原行为 | 危害 |
|---|---|---|
| `EnterpriseDataPermissionInterceptor.injectEnterpriseIdCondition` | 解析失败 → `log.debug` 后放过 | **该查询返回全表数据**（第二层租户防线） |
| `DataPermissionInterceptor` 注入失败 | `log.warn` + `return` | 过滤不生效，放行全表 |
| `DataPermissionInterceptor` 写回 BoundSql 失败 | `log.warn` 放过 | 同上 |
| `DataPermissionInterceptor.getPermission` 失败 | `log.warn` + `return null` | `null` 在 `beforeQuery` 里等于「不加过滤」，**最隐蔽** |

四处现均改为抛 `BusinessException`（铁律 #14）+ `log.error` 保留堆栈。

> `EnterpriseDataPermissionInterceptor` 中 `enterpriseId == null` 的早退**保留**：它表示「当前线程没有业务租户上下文」（定时任务 / 系统初始化 / 漏洞确认用例），**不是**超级管理员判断——超管身份由 §1.1 的 `EnterpriseMembershipChecker` 在入口处解决。原注释「超级管理员，不拦截」把两件事混为一谈，已订正。

## 5. BDD 行为契约

> **V1.2**：下表为**实施后实际落地**的 BDD 契约与用例编号。原 V1.1 编号（AT-102-1b/1c 等）在实现中细分为多组可独立定位的用例；`PermissionCodeAuditTest` 更名为 `SystemClearAuthorizationTest`（更贴近其实际断言内容）。

| # | Given | When | Then | 验证层 | 状态 |
|---|---|---|---|---|---|
| AT-102-1 | 企业1用户持合法 JWT | 带 `X-Enterprise-Id: 2` 请求 | 403；`t_*` 无企业2数据泄漏 | 集成 | ✅ 规则层 `TenantIsolationSecurityTest` + HTTP 层 `TenantIsolationHttpTest` |
| AT-102-1b | `accountant01`（`enterprise_id=NULL`）持合法 JWT | 带 `X-Enterprise-Id: 1` 请求 | **放行**（三源并集经代理成员表判定） | 集成 | ✅ `agencySeedAccountsNotLockedOut`（真实种子数据） |
| AT-102-1c | `admin`（SUPER_ADMIN） | 带 `X-Enterprise-Id: 2` | 放行，且审计留痕 from→to | 集成 | ✅ `superAdminAllowedEverywhere` + `EnterpriseSwitchAuditService` |
| AT-102-1d | 目标企业为 null | 请求 | 不因缺头失败 | 单测 | ✅ `nullTargetIsAllowed` |
| AT-102-2 | 应用角色已 `NOBYPASSRLS` + 事务内已 `SET LOCAL` | 业务事务查 `t_voucher` | 仅本企业行 | 真实 DB | ⏳ **M5b 未做** |
| AT-102-3 | 任意登录用户无 `system:clear` | `POST /clear-vouchers` | 403；`t_voucher` 行数不变（负向） | 集成 | ✅ 反射断言注解 + 权限码存在性（`SystemClearAuthorizationTest`） |
| AT-102-4 | 用户无 `period:reopen` | `POST /period/{id}/reopen` | 403（反结账受保护） | 集成 | ✅ `periodReopenIsProtected` |
| AT-102-5a | `SystemClearController` 破坏性端点数为 **9** | 源码反射 | 数量变化即红（防新增端点漏保护） | 单测 | ✅ `clearEndpointsCountIsNine` |
| AT-102-5b | 存在 `@PreAuthorize` 端点 | 启动自检 | 权限码在 `t_menu` 存在，否则测试红 | 真实 DB | ✅ `allPermissionCodesExistInMenuTable` |
| AT-102-5c | 库中不存在的权限码 | 查询 | 查出 0 条（**反证**：防断言恒真） | 真实 DB | ✅ `nonExistentPermissionCodeIsDetected` |
| AT-102-6a | SQL 解析失败 | 拦截器执行 | 事务中止抛错（非静默放行） | 单测 | ✅ `DataPermissionFailClosedTest#sqlParseFailureMustThrow` |
| AT-102-6b | 查用户数据权限失败 | 拦截器执行 | 中止（`null` 权限 ≠ 不过滤） | 单测 | ✅ `permissionLookupFailureMustThrow` |
| AT-102-6c | 第二层拦截器解析失败 | 拦截器执行 | 中止（不得返回全表） | 单测 | ✅ `EnterpriseDataPermissionFailClosedTest#parseFailureMustAbort` |
| AT-102-6d | `dataScope=ALL` / 共享表 / INSERT | 拦截器执行 | 不抛错（正常路径未误伤） | 单测 | ✅ 4 条对照用例 |
| AT-102-7a | 合法上下文为企业1 | `POST /customers`，body 含 `{"enterpriseId": 999}` | 落库 `enterprise_id=1`（L1 强制覆盖），**非 999** | 真实 DB | ✅ `EnterpriseIdInjectionTest#forgedEnterpriseIdIsOverridden` |
| AT-102-7b | 同上 | 同上 | 落库值**绝不等于** 999（负向） | 真实 DB | ✅ `persistedEnterpriseIdIsNeverTheForgedOne` |
| AT-102-7c | 上下文已切到企业 2 | body 含 999 | 落库为 2（上下文优先） | 真实 DB | ✅ `switchedContextWins` |
| AT-102-7d | body 不含 `enterpriseId` | 插入 | 仍按上下文回填（未破坏原行为） | 真实 DB | ✅ `absentEnterpriseIdStillFilled` |
| AT-102-7e | `SecurityConfig` | 静态核对 | 全部请求 `.authenticated()`，白名单恰为 9 条 | 单测 | ✅ **前提守卫**（L1 修复成立的前提） |
| AT-102-7f | 同上 | 静态核对 | 共享表 `agency_user_id` 走两跳（反证） | 真实 DB | ✅ `agencyUserIdIsNotUserId` |
| AT-102-8 | 已迁移 DTO 的 Controller | 收含 `enterpriseId` 的 body | 该字段不生效（DTO 无此字段） | 集成 | ⏳ 属 L2（§7 分批），未开始 |

## 6. 竞品对标（铁律 #15）

| 差距 | 用友 U8/NC | 金蝶 K/3 | SAP B1 | Xero | QB | 本修法 |
|---|---|---|---|---|---|---|
| 反结账 | **账套主管口令** | 独立反审核权限 | 授权 | 角色 | 角色 | `period:reopen` 权限码 + 审计 |
| 功能级权限 | 功能+数据双层 | 双层 | 角色+组织 | 角色 | 角色 | `@PreAuthorize` 端点级 + 后续数据级 |
| 切企业 | 受权限约束 | 账套切换鉴权 | 公司切换 | 单实体 | 公司文件 | 三源并集校验（保留切企业） |
| 入参企业隔离 | 服务端强制 | 强制 | 强制 | 强制 | 强制 | L1 强制覆盖 + L2 DTO 化 |
| 审计兜底 | 越权留痕 | 同 | 同 | 弱 | ✅ | 越权 403 + SUPER_ADMIN 切换留痕（P103 复用） |

**结论**：竞品底线是「反结账/清库类高危动作必须权限码 + 留痕」且**入参绝不接受企业归属字段**。本项目现全开放 + 入参可写租户字段，本 SPEC 两层堵死。

## 7. DTO 隔离分批计划（铁律 #13，L2）

| 批次 | 范围 | Controller 数 | 备注 |
|---|---|---|---|
| 批1（最高危） | 涉资金模块 `cash` / `tax` / `arap` / `voucher` | ~10 | 优先，金额与凭证相关 |
| 批2 | 基础数据 `masterdata`（`Customer`/`Vendor`/`Employee` 等） | ~8 | 客商归属影响应收应付 |
| 批3 | 其余 `base` / `sme` | ~7 | 收尾 |

- 每批：新建 `dto/*Req.java` → Controller 改签名 → 加 `EnterpriseIdInjectionTest` 负向用例 → 跑真库回归。
- **先做 L1 兜底**（一次堵死 48 处），再分批治本。

## 8. 风险与不在范围

- **切企业三源并集**误伤 → 已用实测数据校准（3 个 NULL 企业账号 + SUPER_ADMIN 豁免）；先只读校验不删功能。**V1.2 实测仍误伤 `reviewer01`**，已由 V165 补种子修复，并由真实种子数据测试锁死。
- **L1 强制覆盖**可能掩盖前端 bug（前端若真传错企业会静默纠正）→ 已在契约注明；L2 DTO 化后前端字段消失，问题显性化。
- **L1 强制覆盖会改写跨租户造数**（测试与系统任务）→ 已提供 `AbstractMapperTest#withoutEnterpriseContext` 显式出口；生产无上下文路径不可被外部触达，由 AT-102-7e 守卫。
- **RLS 收紧**可能使现存超级用户查询返 0 行 → 先 Testcontainers 全量验证，单独 commit 便于回滚。**V1.2 补充**：RLS 失效有**三重**原因，不止 SPEC 原列的两项 ——
  ① `rolsuper=t`（超级用户绕过）；② `rolbypassrls=t`（BYPASSRLS 属性绕过）；③ **83/83 张表的 owner 都是 `huicai`，而表属主默认也绕过 RLS**，故前两项改掉仍不生效，还需 `FORCE ROW LEVEL SECURITY`。
  已实测：70 张表启用 RLS 且**都有策略**（无「启用了却无策略」的漏网表），70 条策略全部依赖 `current_setting('app.enterprise_id', true)`；未设置时策略返 **0 行**，设为 1 时可见 44 行 ⇒ 方案可行，但 `SET LOCAL` 一旦漏设即全站返 0 行，故列为 M5b 独立里程碑。
- **不在范围**：数据级（部门/个人）权限粒度 → 归 P106；AI 功能排除；点状缺陷 → 归 P107。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| **V1.2** | 2026-09-30 | opencode | **实施回写（M1~M5a 已交付）**：5 处与实现不符的前提经实测推翻并订正 —— ①权限列名是 `t_menu.permission`（非 `permission_code`），43/43 行早已回填，**不需加列**；②V160 已被 P107 D1 占用，权限码种子改用 **V163**；③三源并集源 2 **必须两跳**（`agency_user_id` 指向 `t_agency_user.id`），该表现成 `countByUserId`/`selectByUserId` 语义错误不得使用；④修正后**仍锁死 `reviewer01`**，由 **V165** 补种子；⑤清库端点**是 9 个不是 8 个**。另新增：§4 补 4 处 fail-open 的实际位置（`EnterpriseDataPermissionInterceptor` 1 处 + `DataPermissionInterceptor` 3 处，与原 SPEC 所述「4 处」分布不同）；§5 BDD 表替换为实施后实际落地的 22 条用例编号与状态；§8 补 RLS 三重失效原因与实测结论；`AuditLogEntity` 缺 `before_data`/`after_data` 属性（**P103 的失效本体**）这一实现障碍 |
| V1.1 | 2026-09-29 | opencode | **审核修订**：①成员校验改三源并集（`t_user.enterprise_id` ∪ `t_agency_user_enterprise` ∪ SUPER_ADMIN），修正「只查成员表会锁死 accountant01/reviewer01/assistant01」的设计缺陷；②新增 §2.2 入参 `enterpriseId` 封禁（L1 强制覆盖 + L2 DTO 分批），补上比 header 更直接的越权路径；③新增 AT-102-1b/1c/7/8 四条 BDD；④新增 §7 DTO 隔离三批计划 |
| V1.0 | 2026-09-29 | opencode | 初稿：租户隔离 + 端点鉴权，P101-M2/M3，RLS/越权/清库端点/权限码补列 |
