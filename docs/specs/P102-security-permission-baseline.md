# P102 SPEC — 安全与权限基座加固（租户隔离 + 端点鉴权 + DTO 隔离）

> **版本**：V1.1（修订：补 Entity 直入越权路径与成员校验三源并集） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P102 | 优先级：**P0（商用门槛）** | 状态：📋 待审核
> **来源**：P101 总纲 M2/M3；四路审计交叉最严重项
> **关联需求**：REQ-2026-129 | **前置**：无 | **test_ref**：`TenantIsolationSecurityTest`、`SystemClearAuthorizationTest`、`PermissionCodeAuditTest`、`EnterpriseIdInjectionTest`
> **排除**：AI 功能（老丁 2026-09-29 指示）
> **V1.1 修订说明**：审核发现两处设计缺陷已修正 —— ①原「查 `t_agency_user_enterprise` 判成员」会**锁死 3 个种子账号**（`accountant01`/`reviewer01`/`assistant01` 的 `t_user.enterprise_id` 为 NULL 且无成员记录，`admin` 是 SUPER_ADMIN）→ 改为 §1.1 三源并集；②原 SPEC 漏掉**比 header 更直接**的越权路径：`@RequestBody XxxEntity` 48 处（`MyMetaObjectHandler:20` `strictInsertFill` 仅在 null 时填，`BaseEntity.enterpriseId` 是可写真实字段）→ 补 §2.2 入参封禁与铁律 #13 DTO 隔离分批

---

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| JWT | HttpOnly token | 含 `userId`/`enterpriseId`/`roles`，为**唯一身份来源** |
| `X-Enterprise-Id` | 请求头 | 须满足 §1.1 成员规则；否则 403 且不回退、不泄露他企业存在性 |
| 应用 DB 角色 | PostgreSQL | 须 `NOBYPASSRLS`；每业务事务 `SET LOCAL app.enterprise_id = <ctx>` |
| 权限码 | `t_menu.permission_code` | **当前无此列**，V160 补列并回填；`@PreAuthorize` 引用须在库中存在 |
| 角色权限 | `t_role`（ADMIN/FINANCE_MGR/CASHIER/OPERATOR/ACCOUNTANT） | 与权限码多对多 |

### 1.1 成员校验三源并集（V1.1 修正，替代原单表规则）

**允许切换到企业 E 的条件**（任一成立）：

```
member(user, E) =
      t_user.enterprise_id = E                          -- 直属企业
   OR ∃ t_agency_user_enterprise(user, E, deleted=0)   -- 代理端多企业授权
   OR t_user.user_type = 'SUPER_ADMIN'                 -- 超级管理员全局切换
```

- **实测依据**：`t_agency_user_enterprise` 仅 2 行；`accountant01`/`reviewer01`/`assistant01` 的 `t_user.enterprise_id` 为 **NULL** 且无成员记录（若只查成员表将**被锁死**）；`admin` 为 `SUPER_ADMIN`（须保留其切换能力）。
- **`t_agency_user_enterprise` 无记录但 `t_user.enterprise_id` 非空**的用户**允许访问其 `enterprise_id` 指向的企业**（覆盖 ACCT/AGENCY 两种登录路径）。
- **SUPER_ADMIN 切换须审计留痕**（记录 from→to + 操作人），复用 P103 审计切面。

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

| 层 | 措施 | 覆盖 |
|---|---|---|
| **L1 兜底（全量、一次生效）** | 在 `MyMetaObjectHandler` 增**强制覆盖**：`enterpriseId` 无条件 `setFieldValByName(..., EnterpriseContextHolder.get())`，忽略入参值 | 48 处全覆盖，1 个类改动 |
| **L2 治本（分批）** | 按铁律 #13 迁移为 DTO/Param：`@RequestBody XxxCreateReq`/`XxxUpdateReq`，**不含** `enterpriseId` 字段 | 25 个 Controller，按 §7 分批 |

> L1 用「强制覆盖」而非「拒绝」：改动面小、一次性堵死写入路径；L2 再从协议层消除该字段。

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

| 场景 | 处理 |
|---|---|
| `X-Enterprise-Id` 越权 | `BusinessException(403)`，不回退 JWT 值，不区分「企业不存在」与「无权限」 |
| 入参携带 `enterpriseId` | L1：**静默覆盖为上下文值**（不报错，避免破坏存量前端）；L2：DTO 层无此字段，Jackson 未知字段忽略 |
| `SET LOCAL` 失败 | 抛异常终止事务（安全组件 fail-closed，禁 fail-open） |
| SQL 解析/注入异常 | 事务中止并抛错（现 `DataPermissionInterceptor` 4 处 `catch→return null` 需改） |
| 权限码缺失 | 启动自检失败（`PermissionCodeAuditTest`），防「静默无鉴权」 |

## 5. BDD 行为契约

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-102-1 | 企业1用户（`t_user.enterprise_id=1`）持合法 JWT | 带 `X-Enterprise-Id: 2` 请求 | 403；`t_*` 无企业2数据泄漏 | 集成 |
| **AT-102-1b** | `accountant01`（`enterprise_id=NULL`、无成员记录）持合法 JWT | 带 `X-Enterprise-Id: 1` 请求 | **放行**（三源并集经代理成员表/角色判定）；若其无任何关联企业则 403 | 集成 |
| **AT-102-1c** | `admin`（SUPER_ADMIN） | 带 `X-Enterprise-Id: 2` | 放行，且审计留痕 from→to | 集成 |
| AT-102-2 | 应用角色已 `NOBYPASSRLS` | 业务事务查 `t_voucher` | 仅本企业行（`SET LOCAL` 生效） | 真实 DB |
| AT-102-3 | 任意登录用户无 `system:clear` | `POST /clear-vouchers` | 403；`t_voucher` 行数不变（负向） | 集成 |
| AT-102-4 | 用户无 `period:reopen` | `POST /period/{id}/reopen` | 403（反结账受保护） | 集成 |
| AT-102-5 | 存在 `@PreAuthorize("hasAuthority('x:y')")` 端点 | 启动自检 | 权限码在 `t_menu` 存在，否则测试红 | 单测 |
| AT-102-6 | SQL 注入解析抛异常 | 拦截器执行 | 事务中止抛错（非静默放行） | 单测 |
| **AT-102-7** | 合法用户上下文为企业1 | `POST /customers`，body 含 `{"enterpriseId": 999}` | 落库 `enterprise_id=1`（L1 强制覆盖），**非 999**（负向） | 真实 DB |
| **AT-102-8** | 已迁移 DTO 的 Controller | 收含 `enterpriseId` 的 body | 该字段不生效（DTO 无此字段） | 集成 |

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

- **切企业三源并集**误伤 → 已用实测数据校准（3 个 NULL 企业账号 + SUPER_ADMIN 豁免）；先只读校验不删功能。
- **L1 强制覆盖**可能掩盖前端 bug（前端若真传错企业会静默纠正）→ 已在契约注明；L2 DTO 化后前端字段消失，问题显性化。
- **RLS 收紧**可能使现存超级用户查询返 0 行 → 先 Testcontainers 全量验证，单独 commit 便于回滚。
- **不在范围**：数据级（部门/个人）权限粒度 → 归 P106；AI 功能排除；点状缺陷 → 归 P107。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.1 | 2026-09-29 | opencode | **审核修订**：①成员校验改三源并集（`t_user.enterprise_id` ∪ `t_agency_user_enterprise` ∪ SUPER_ADMIN），修正「只查成员表会锁死 accountant01/reviewer01/assistant01」的设计缺陷；②新增 §2.2 入参 `enterpriseId` 封禁（L1 强制覆盖 + L2 DTO 分批），补上比 header 更直接的越权路径；③新增 AT-102-1b/1c/7/8 四条 BDD；④新增 §7 DTO 隔离三批计划 |
| V1.0 | 2026-09-29 | opencode | 初稿：租户隔离 + 端点鉴权，P101-M2/M3，RLS/越权/清库端点/权限码补列 |
