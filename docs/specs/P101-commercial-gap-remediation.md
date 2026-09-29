# P101 SPEC — 商用化差距修复总纲（安全基座 / 审计落地 / 测试门禁 / 文档地基）

> **版本**：V1.0（草案，待老丁审核） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P101 | 优先级：**P0（商用门槛）**
> **状态**：📋 待审核
> **来源**：2026-09-29 四路审计（项目状况+竞品对标 / 需求文档 / 代码功能 / 测试体系），**已排除 AI 功能**
> **关联需求**：REQ-2026-128（总纲）；下挂 P102~P105 四个执行子 SPEC
> **test_ref**：`TenantIsolationSecurityTest`、`AuditSnapshotRealDBTest`、`SystemClearAuthorizationTest`、`l2-integration-test.yml` 门禁验证

---

## 0. 审计结论摘要（本 SPEC 的立项依据）

四路审计交叉后，问题分两层：**基座失效**（安全/审计/门禁）与**功能深度不足**（年结/多账套/权限粒度）。基座失效是商用一票否决项，优先级高于任何新功能。

| 层 | 结论 | 证据（我已亲自复核） |
|---|---|---|
| 基座 | **RLS 双重失效**：应用角色 `rolsuper=t` + `rolbypassrls=t`；70 张表策略依赖 `current_setting('app.enterprise_id')`，而全库 `set_config` **0 处调用** | 实测 `pg_roles` + `pg_policies`；`grep set_config` = 0 |
| 基座 | **跨租户越权**：`X-Enterprise-Id` 头无成员校验直接覆盖 JWT；全库 `@PreAuthorize` **0 处**，`hasRole/hasAuthority` **0 处** | `JwtAuthenticationFilter.java:51-54` |
| 基座 | **审计追踪实质失效**：快照字段 `exist=false`（真实列 `before_data/after_data`）；`updateById` 不在切面点；`AuditLogServiceImpl:34` 查不存在的 `status` 列 | `AuditLogEntity.java:26-33`、`AuditTrackingAspect.java:32/116/151` |
| 基座 | **8 个清库端点无鉴权、无 WHERE**，任意登录用户可物理抹库 | `SystemClearController` 8 个 `@PostMapping` |
| 门禁 | **main 的 CI 门禁只跑 Mock**：`l2-integration-test.yml` 只监听 `develop`；`full-stack-test.yml` 跑默认排除 slow 的 `mvn test` | 两 workflow 实测 |
| 测试 | 29 个 `*MapperTest` 是 `Mockito.mock(Mapper)` 同义反复（145 用例，零 SQL 验证） | `BudgetMapperTest.java:14` 等，复核计数 = 29 |
| 文档 | Registry 编号重号（`REQ-079/080` 各两次，内容互斥）、`SPC-003/006` 空指针 | 行号 `:82`vs`:273`、`:103`vs`:274` |
| 功能 | 无年结、无多账套、数据权限仅 4 张表、无制单≠审核 | 竞品对标见 §6 |

> **不在本总纲范围**：AI 能力（`ai-service/`、`com.huicai.base.ai`、AI 相关 REQ/SPEC）按老丁指示全部排除。

---

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| JWT | HttpOnly | 含 `userId` / `enterpriseId` / `roles`；**不得**被请求头无条件覆盖 |
| `X-Enterprise-Id` | Header | 仅当「该用户确属该企业」时生效；否则 403 |
| PostgreSQL 会话 | 连接 | 每个业务事务开始须 `SET LOCAL app.enterprise_id`；应用角色须 `NOBYPASSRLS` |
| 权限码 | `t_menu` | **当前无 `permission_code` 列**，需先补列并回填（V160） |
| 审计事件 | AOP | 覆盖 `insert` / `updateById` / `deleteById`；快照写 `before_data` / `after_data` |
| CI 配置 | `.github/workflows/` | 真库套件必须在 main PR 触发 |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 租户隔离 | 越权用例（跨企业读写、伪造 header）100% 被拒；`pg_roles.rolbypassrls=false` |
| 权限体系 | 高危端点（清库/反结账/制证/审核/红冲）全部有 `@PreAuthorize`，无权限码用户 403 |
| 审计 | 关键实体 `updateById` 后 `t_audit_log` 必有 `before_data`/`after_data` 非空 JSON |
| 门禁 | main PR 上真库套件（`-DexcludedGroups=`）必跑且失败即阻断 |
| 测试成色 | 「同义反复 `*MapperTest`」归零；核心模块真库覆盖率 ≥ 60% |
| 文档地基 | REQ 编号全局唯一；SPEC 状态与代码一致；硬数字单点可信 |

## 3. 状态流转

```
安全基座: 无鉴权态 ──P102──▶ 端点级鉴权态 ──P103──▶ 数据级(部门/个人)隔离态
RLS:      摆设(超级用户绕过) ──P102──▶ 生效(NOBYPASSRLS + SET LOCAL)
审计:     有行无快照 ──P103──▶ 快照真实落库
CI:       main 只跑 Mock ──P104──▶ main 跑真库门禁
文档:     双向漂移 ──P105──▶ 单点可信(编号唯一 + 状态回写)
```
每一步都必须**可独立验证**（负向用例 + 真实 DB 断言），不允许「先上后补」。

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| `X-Enterprise-Id` 指向用户无权限的企业 | 抛 `BusinessException(403)`，**不回退**到 JWT 值（防探测） |
| 非管理员调清库端点 | 403；`SUPER_ADMIN` 也不豁免 WHERE 缺失（改为带 `enterprise_id` 条件或直接下线） |
| `SET LOCAL app.enterprise_id` 失败 | fail-**closed**：抛异常终止事务（安全组件不得 fail-open，参见现状 `DataPermissionInterceptor` 4 处 fail-open） |
| 审计快照序列化失败 | 不阻断业务，记 `log.error` + 写最小快照（id/操作人/时间），**不得**吞异常后返回成功 |
| 权限码缺失（新端点忘配） | 启动自检 `PermissionCodeAuditTest` 失败，避免「静默无鉴权」 |

## 5. 子 SPEC 与执行顺序

| 子 SPEC | 范围 | REQ | 优先级 | 依赖 |
|---|---|---|---|---|
| **P102** 安全与权限基座 | 跨租户越权、RLS 生效、8 清库端点、`@PreAuthorize` 体系、`t_menu.permission_code` 补列 | REQ-2026-129 | **P0** | 无 |
| **P103** 审计追踪落地 | 快照字段映射、`updateById` 纳入、`status` 列、`@Auditable` 覆盖核心实体 | REQ-2026-130 | **P0** | P102（权限码复用） |
| **P104** 测试门禁与成色 | main 真库门禁、29 个同义反复改造、失败门禁、覆盖矩阵 | REQ-2026-131 | **P0** | 无（可与 P102 并行） |
| **P105** 文档治理地基 | REQ 重号、`SPC-003/006` 空指针、SPEC 状态批量回写、硬数字单点 | REQ-2026-132 | P1 | 无 |
| **P106** 内控与核算深度 | 年结、制单≠审核、多账套、数据权限粒度、部门级扩展 | REQ-2026-133 | P1 | P102/P103 |

> P106 体量最大（年结涉及结转损益 + 利润分配 + 期初衔接 + 跨年期间生成），**建议独立立项**，本总纲只锁边界与依赖。

## 6. 竞品对标（铁律 #15 强制）

| 差距 | 用友 U8/NC | 金蝶 K/3·星空 | SAP B1 | Xero | QuickBooks | 本项目修法 |
|---|---|---|---|---|---|---|
| 功能级+数据级权限 | 双权限体系 | 双权限 | 角色+组织 | 角色 | 角色 | P102 端点鉴权 + P106 部门级数据权限 |
| 反结账保护 | **需账套主管口令** | 独立反审核权限 | 授权 | 角色 | 角色 | P102：`period:reopen` 权限码 + 二次确认 |
| 制单≠审核 | 不相容职务分离 | 同 | 同 | 有限 | 有限 | P106：`created_by != currentUser` 校验 |
| 多组织/多账套 | 账套一等对象 | 账套一等 | 公司+账簿 | 单实体 | 公司文件 | P106（长期） |
| 年结 | ✅ | ✅ | ✅ | ✅ | ✅ | P106（**当前全库 0 命中**） |
| 审计追踪 | ✅ 全量 | ✅ | ✅ | 弱 | ✅ | P103（当前**有行无快照**） |

**结论**：四家竞品均以「权限 + 审计 + 账套」为商用底线；本项目功能广度已达 QuickBooks Advanced 约 70–80%，**但基座三项全缺**，属「能演示不能上线」。P102~P105 是把基座补齐到竞品底线，成本远低于新增功能。

## 7. 验收标准（Given-When-Then 摘要，详见各子 SPEC）

| # | Given | When | Then |
|---|---|---|---|
| AT-101-1 | 用户 A（企业 1）持合法 JWT | 带 `X-Enterprise-Id: 2` 请求 | 403，且**不泄露**企业 2 是否存在 |
| AT-101-2 | 应用角色已 `NOBYPASSRLS` | 业务事务查询 `t_voucher` | 仅返回本企业行（`SET LOCAL` 生效） |
| AT-101-3 | 任意登录用户（无 `system:clear` 权限码） | `POST /api/v1/system/clear-vouchers` | 403，`t_voucher` 行数不变（**负向断言**） |
| AT-101-4 | 凭证已被 `updateById` 修改 | 查 `t_audit_log` | 存在 `before_data`/`after_data` 且为合法 JSON，字段值确实变化 |
| AT-101-5 | main 分支 PR | CI 触发 | 真库套件（`-DexcludedGroups=`）执行，失败即红 |
| AT-101-6 | 全部 4 个领域文档 | 交叉引用 | REQ 编号全局唯一，SPEC 状态与代码一致 |

## 8. 风险与不在范围

| 风险 | 缓解 |
|---|---|
| 切企业（`X-Enterprise-Id`）是代理端核心功能，直接禁掉会破坏业务 | 保留能力，改为**成员校验**（`t_agency_user_enterprise` 已存在） |
| RLS 收紧后可能使现存「超级用户」查询返 0 行 | 先在 Testcontainers 跑全量真库套件验证，再切生产角色 |
| 补 `permission_code` 列需回填 82 张菜单 | 用 V160 migration + 现有 `t_menu` 数据回填，幂等 |
| **不在范围**：AI 能力全部排除；P106 年结/多账套不在本批；前端 122 个 Playwright 用例接入 CI（另立任务） |

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：基于四路审计立项，锁定 P102~P106 五个子 SPEC 的边界、依赖与验收；排除 AI |
