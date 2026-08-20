# PRD04：权限、数据安全与审计日志 PRD

> **编号**：HUICAI-PRD-004
> **版本**：V1.0 | **日期**：2026-08-20
> **关联总 PRD**：`../CORE-需求分析.md`
> **关联设计**：DSN-基础数据管理.md（权限章节）
> **关联SPEC**：S-01-多租户架构与数据隔离.md、S-02-统一身份认证与RBAC权限.md
> **对应包**：com.huicai.config.security / com.huicai.base.system / com.huicai.common

---

## 1. 模块定位

权限、数据安全与审计日志模块是**全局安全基础设施层**，负责所有业务模块的访问控制、数据隔离和操作留痕。它不承载任何业务逻辑，为所有模块提供统一的安全能力。

**核心原则**：
- 功能权限：基于角色（RBAC），控制用户能访问哪些页面/按钮
- 数据权限：基于组织（多租户），控制用户能看到哪些数据行
- 操作留痕：所有关键写操作自动记录变更前后快照
- 禁止物理删除：所有业务表使用逻辑删除（`deleted` 字段）

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| SEC-01 | JWT 认证登录 | P0 | ✅ 已完成 | 用户名+密码登录，签发 JWT token，过期自动失效 |
| SEC-02 | RBAC 权限控制 | P0 | ✅ 已完成 | 角色绑定菜单+按钮权限，用户绑定角色，Spring Security 注解拦截 |
| SEC-03 | 数据权限隔离 | P0 | ✅ 已完成 | MyBatis 拦截器自动注入 `enterprise_id` 条件，跨租户数据隔离 |
| SEC-04 | 审计日志 | P0 | ✅ 已完成 | `@Auditable` 注解 AOP 记录操作/模块/参数/IP，JSONB 存储变更快照 |
| SEC-05 | 敏感操作管控 | P1 | ⚠️ 部分实现 | 审核/过账/红冲/结账等操作需二次确认 |
| SEC-06 | 禁止物理删除 | P0 | ✅ 已完成 | 所有业务表 `deleted` 字段逻辑删除，`@TableLogic` 注解，查询自动带 `deleted=0` |
| SEC-07 | 登录日志 | P1 | ⚠️ 部分实现 | 记录登录成功/失败，失败次数锁定 |

---

## 3. 认证体系（JWT + Redis）

### 3.1 登录流程

```
用户输入账号密码 → [JwtAuthEntryPoint] 校验 → 签发 JWT Token
    ↓
[Redis] 缓存 Token（过期时间 24h）
    ↓
前端每次请求携带 Authorization: Bearer {token}
    ↓
[JwtAuthenticationFilter] 解析 Token → 从 Redis 获取用户信息
    ↓
Spring Security 设置 SecurityContextHolder → 鉴权完成
```

### 3.2 Token 规范

| 属性 | 值 |
|------|-----|
| 算法 | HMAC-SHA256 |
| 过期时间 | 24 小时 |
| 存储 | Redis（`TOKEN:{userId}`），支持手动踢下线 |
| 刷新 | 前端拦截 401 后引导重新登录，无自动刷新 |

### 3.3 权限校验

| 层级 | 实现方式 | 示例 |
|------|---------|------|
| 接口级 | `@PreAuthorize("hasAuthority('system:user:list')")` | 校验用户是否有该权限 code |
| 按钮级 | 前端 `v-permission="system:user:list"` | 控制按钮显示/隐藏 |
| 数据级 | `DataPermissionInterceptor` 注入 SQL | 自动追加 `WHERE enterprise_id = ?` |

---

## 4. RBAC 权限模型

### 4.1 数据模型

```
t_user ──→ t_user_role ──→ t_role ──→ t_role_menu ──→ t_menu
  │                                                     │
  └─ user_id ──────────── role_id ──────────────── menu_id
```

| 表 | 说明 | 关键字段 |
|----|------|---------|
| t_user | 用户 | username, password, status, user_type, agency_id, enterprise_id |
| t_role | 角色 | role_code, role_name, status, data_scope |
| t_menu | 菜单/权限 | menu_name, permission, menu_type, parent_id, path |
| t_user_role | 用户角色关联 | user_id, role_id |
| t_role_menu | 角色菜单关联 | role_id, menu_id |

### 4.2 权限粒度

| 粒度 | 说明 | 示例 |
|------|------|------|
| 模块级 | 控制整个模块可见 | `voucher:access` |
| 功能级 | 控制具体功能 | `voucher:create`, `voucher:audit` |
| 按钮级 | 控制页面按钮 | `voucher:delete`, `voucher:export` |
| 数据级 | 控制数据行可见 | 本企业数据/本部门数据/全部数据 |

---

## 5. 数据权限隔离

### 5.1 多租户隔离

| 层级 | 实现 | 说明 |
|------|------|------|
| 数据库 | `enterprise_id` 列 | 所有业务表含企业 ID |
| MyBatis 拦截器 | `EnterpriseDataPermissionInterceptor` | 自动注入 `enterprise_id = ?` 到所有查询 |
| 业务层 | `EnterpriseContextHolder` | 当前请求的 enterprise_id 从 JWT 中提取 |
| 管理员 | 超级管理员可跨租户查询 | 通过在拦截器中跳过管理员 ID |

### 5.2 数据隔离范围

| 数据 | 隔离粒度 | 说明 |
|------|---------|------|
| 凭证/单据/发票 | 企业级 | 不同企业互不可见 |
| 科目/档案/人员 | 企业级 | 不同企业独立档案 |
| 系统参数 | 全局 | 系统参数所有企业共享 |
| 字典 | 全局 | 字典值所有企业共享 |

---

## 6. 审计日志

### 6.1 注解方式

```java
@Auditable(operation = "凭证审核", module = "voucher", trackSnapshot = true)
public void audit(Long id) { ... }
```

### 6.2 日志记录内容

| 字段 | 说明 | 来源 |
|------|------|------|
| operator_id | 操作人 ID | JWT |
| operator_name | 操作人名称 | JWT |
| operation | 操作类型 | `@Auditable(operation = ...)` |
| module | 模块名 | `@Auditable(module = ...)` |
| ip_address | 请求 IP | `HttpServletRequest.getRemoteAddr()` |
| old_snapshot | 变更前快照（JSONB） | 当 `trackSnapshot=true` 时 AOP 拦截 |
| new_snapshot | 变更后快照（JSONB） | 同上 |

### 6.3 审计范围

| 触发类型 | 说明 |
|---------|------|
| 自动审计 | `@Auditable` 注解，所有 Controller 关键操作 |
| 状态变更 | 审核/驳回/过账/红冲/结账/反结账 |
| 敏感操作 | 删除数据、修改密码、修改权限、修改角色 |
| 非审计 | 查询操作、导出操作、不触发审计 |

---

## 7. 禁止物理删除规则

| 规则 | 说明 |
|------|------|
| 逻辑删除 | 所有业务表使用 `deleted` 字段（Integer，0=正常，1=删除） |
| 自动过滤 | 所有查询通过 `@TableLogic` 自动带 `deleted = 0` 条件 |
| 数据恢复 | 管理员可撤销删除（将 `deleted` 置回 0） |
| 审计 | 删除操作记录审计日志，含删除前快照 |
| 例外 | 系统日志表、临时表、关联表允许物理删除 |

---

## 8. 不做的事

| 不做 | 理由 |
|------|------|
| 第三方 OAuth/OIDC 登录 | 非 MVP 范围，当前仅用户名密码 |
| 字段级权限 | 粒度太细，当前按角色+数据权限隔离 |
| 操作审批流 | 属于 PRD11 审批流引擎，慧财手动审核 |
| 多因素认证 | 非 MVP 范围 |
| 数据脱敏 | 非 MVP 范围 |

---

## 9. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/user-info` | 获取当前用户信息+权限 |
| GET | `/api/v1/users` | 用户列表 |
| POST | `/api/v1/users` | 新增用户 |
| PUT | `/api/v1/users/{id}` | 修改用户 |
| PUT | `/api/v1/users/{id}/password` | 修改密码 |
| GET | `/api/v1/roles` | 角色列表 |
| POST | `/api/v1/roles` | 新增角色 |
| PUT | `/api/v1/roles/{id}` | 修改角色+权限 |
| GET | `/api/v1/menus` | 菜单树 |
| GET | `/api/v1/audit-logs` | 审计日志列表 |
| GET | `/api/v1/audit-logs/{id}` | 审计日志详情（含快照） |

---

## 10. 验收标准

| ID | BDD 场景 |
|----|---------|
| SEC-AT-01 | Given 正确账号密码 When 登录 Then 返回 JWT Token + 用户信息 |
| SEC-AT-02 | Given 错误密码 When 登录 Then 返回 401 错误 |
| SEC-AT-03 | Given 过期 Token When 调用 API Then 返回 401 |
| SEC-AT-04 | Given 用户无权限 When 访问菜单 Then 侧边栏不显示该菜单 |
| SEC-AT-05 | Given 用户无权限 When 调用 API Then 返回 403 |
| SEC-AT-06 | Given 不同企业用户 When 查询相同 API Then 只返回本企业数据 |
| SEC-AT-07 | Given 关键操作 When 触发审计 Then 审计日志含操作人/IP/时间 |
| SEC-AT-08 | Given 删除数据 When 审计日志开启 trackSnapshot Then 快照记录变更前后 |
| SEC-AT-09 | Given 删除业务数据 When 查询列表 Then 逻辑删除的数据不显示 |
| SEC-AT-10 | Given 管理员 When 恢复逻辑删除数据 Then 数据重新可见 |

---

> **文档结束。**