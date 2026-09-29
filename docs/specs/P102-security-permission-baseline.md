# P102 SPEC — 安全与权限基座加固（租户隔离 + 端点鉴权）

> **版本**：V1.0（草案，待老丁审核） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P102 | 优先级：**P0（商用门槛）** | 状态：📋 待审核
> **来源**：P101 总纲 M2/M3；四路审计交叉最严重项
> **关联需求**：REQ-2026-129 | **前置**：无 | **test_ref**：`TenantIsolationSecurityTest`、`SystemClearAuthorizationTest`、`PermissionCodeAuditTest`
> **排除**：AI 功能（老丁 2026-09-29 指示）

---

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| JWT | HttpOnly token | 含 `userId`/`enterpriseId`/`roles`，为**唯一身份来源** |
| `X-Enterprise-Id` | 请求头 | 仅「用户确属该企业」时生效；否则 403 且不回退、不泄露他企业存在性 |
| 应用 DB 角色 | PostgreSQL | 须 `NOBYPASSRLS`；每业务事务 `SET LOCAL app.enterprise_id = <ctx>` |
| 权限码 | `t_menu.permission_code` | **当前无此列**，V160 补列并回填；`@PreAuthorize` 引用须在库中存在 |
| 角色权限 | `t_role`（ADMIN/FINANCE_MGR/CASHIER/OPERATOR/ACCOUNTANT） | 与权限码多对多 |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 租户隔离 | 跨企业读写 100% 拒绝（403）；RLS 谓词真实生效；应用角色 `rolbypassrls=false` |
| 端点鉴权 | 清库/反结账/制证/审核/红冲/科目等高危端点全部 `@PreAuthorize`；无权限码 → 403 |
| 审计前置 | 端点鉴权与权限码体系就绪（P103 复用） |
| 自检 | `PermissionCodeAuditTest` 绿：任一 `@PreAuthorize` 权限码在 `t_menu` 有对应项 |

## 3. 状态流转

```
请求 ──> JwtFilter(解析JWT) ──> X-Enterprise-Id 成员校验 ──> @PreAuthorize 权限校验 ──> 业务
         │                        │                        │
         │                   越权 → 403(不回退)        无权限码 → 403
         ▼
     业务事务: SET LOCAL app.enterprise_id = ctx ──> RLS + 拦截器 双重过滤 ──> 返回
```
任一校验失败即终止，**不得降级放行**（fail-closed）。

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| `X-Enterprise-Id` 越权 | `BusinessException(403)`，不回退 JWT 值，不区分「企业不存在」与「无权限」 |
| `SET LOCAL` 失败 | 抛异常终止事务（安全组件 fail-closed，禁 fail-open） |
| SQL 解析/注入异常 | 事务中止并抛错（现 `DataPermissionInterceptor` 4 处 `catch→return null` 需改） |
| 权限码缺失 | 启动自检失败（`PermissionCodeAuditTest`），防「静默无鉴权」 |

## 5. BDD 行为契约

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-102-1 | 企业1用户持合法 JWT | 带 `X-Enterprise-Id: 2` 请求 | 403；`t_*` 无企业2数据泄漏 | 集成 |
| AT-102-2 | 应用角色已 `NOBYPASSRLS` | 业务事务查 `t_voucher` | 仅本企业行（`SET LOCAL` 生效） | 真实 DB |
| AT-102-3 | 任意登录用户无 `system:clear` | `POST /clear-vouchers` | 403；`t_voucher` 行数不变（负向） | 集成 |
| AT-102-4 | 用户无 `period:reopen` | `POST /period/{id}/reopen` | 403（反结账受保护） | 集成 |
| AT-102-5 | 存在 `@PreAuthorize("hasAuthority('x:y')")` 端点 | 启动自检 | 权限码在 `t_menu` 存在，否则测试红 | 单测 |
| AT-102-6 | SQL 注入解析抛异常 | 拦截器执行 | 事务中止抛错（非静默放行） | 单测 |

## 6. 竞品对标（铁律 #15）

| 差距 | 用友 U8/NC | 金蝶 K/3 | SAP B1 | Xero | QB | 本修法 |
|---|---|---|---|---|---|---|
| 反结账 | **账套主管口令** | 独立反审核权限 | 授权 | 角色 | 角色 | `period:reopen` 权限码 + 审计 |
| 功能级权限 | 功能+数据双层 | 双层 | 角色+组织 | 角色 | 角色 | `@PreAuthorize` 端点级 + 后续数据级 |
| 切企业 | 受权限约束 | 账套切换鉴权 | 公司切换 | 单实体 | 公司文件 | 成员校验（保留切企业功能） |
| 审计兜底 | 越权留痕 | 同 | 同 | 弱 | ✅ | 越权 403 记审计（P103 复用） |

**结论**：竞品底线是「反结账/清库类高危动作必须权限码 + 留痕」。本项目现全开放，本 SPEC 补齐。

## 7. 风险与不在范围

- **切企业成员校验**误伤代理端 → 成员表已存在，先只读校验不删功能。
- **RLS 收紧**可能使现存超级用户查询返 0 行 → 先 Testcontainers 全量验证，单独 commit 便于回滚。
- **不在范围**：数据级（部门/个人）权限粒度 → 归 P106；AI 功能排除。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：租户隔离 + 端点鉴权，P101-M2/M3，RLS/越权/清库端点/权限码补列 |
