# S-02 - 统一身份认证与RBAC权限

> **版本**：1.1
> **编号**：HUICAI-SPC-101
> **日期**：2026-07-23
> **状态**：✅ 已实现（JWT 认证 + RBAC 权限 + 数据权限拦截器）
> **层级**：基础设施层
> **预估复杂度**：中
> **关联需求**：REQ-2026-004

---

## 概述

JWT 鉴权链路、数据权限过滤规则、操作审计日志写入。当前代码已有完整的 Spring Security + JWT 认证实现和 RBAC 权限体系。

**已有代码基础**：
- `SecurityConfig`：Spring Security 配置，无状态会话（STATELESS）、CORS、CSRF 禁用
- `JwtAuthenticationFilter`：Token 解析 -> Redis 黑名单校验 -> UserDetails 加载 -> SecurityContext 设置
- `JwtProvider`：JWT Token 生成/验证/解析
- `LoginUser`：扩展 Spring Security User，携带 userId
- `UserDetailsServiceImpl`：从 DB 加载用户 + 权限
- RBAC Entity：UserEntity/RoleEntity/MenuEntity/UserRoleEntity/RoleMenuEntity
- `DataPermissionInterceptor`：MyBatis-Plus InnerInterceptor，按角色 data_scope 自动注入 SQL 条件
- Controller：AuthController（登录/登出）/RoleController/MenuController

**已实现的数据权限范围（data_scope）**：
| data_scope | 过滤规则 | 适用场景 |
|------------|---------|---------|
| ALL | 不注入 | 管理员 |
| DEPT | WHERE dept_id = :currentUserDept | 部门数据 |
| DEPT_AND_CHILD | 降级为 DEPT | 部门+下级 |
| SELF | WHERE created_by = :currentUserId | 个人数据 |
| CUSTOM | 不注入，业务代码处理 | 自定义 |

**已配置数据权限的表**：t_expense_reimbursement, t_business_doc, t_asset_card, t_budget_entry

---

## 1. 输入契约

### 1.1 登录

| 参数 | 类型 | 约束 | 说明 |
|------|------|------|------|
| username | String | 非空 | 用户名 |
| password | String | 非空 | 密码（BCrypt 加密存储） |

### 1.2 请求认证

| 参数 | 类型 | 约束 | 说明 |
|------|------|------|------|
| Authorization | Header | Bearer {token} | JWT Token |

### 1.3 前置条件

- 用户存在且 status = ACTIVE
- 密码匹配（BCrypt）
- 账号未锁定

---

## 2. 输出契约

### 2.1 登录成功

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJ...",
    "userId": 1,
    "username": "admin",
    "roles": ["ADMIN"],
    "permissions": ["subject:list", "voucher:create", ...]
  },
  "traceId": "uuid"
}
```

### 2.2 认证失败响应

| 场景 | HTTP | code | message |
|------|------|------|--------|
| Token 过期/无效 | 401 | 401 | 未授权，请先登录 |
| 权限不足 | 403 | 403 | 无权限访问 |
| 账号锁定 | 401 | 401 | 账号已锁定 |
| 用户名/密码错误 | 400 | 400 | 用户名或密码错误 |

---

## 3. 状态流转

### 3.1 用户状态机

```
ACTIVE -> LOCKED -> ACTIVE
ACTIVE -> DISABLED
```

| 状态 | 说明 | 可转换到 |
|------|------|---------|
| ACTIVE | 正常使用 | LOCKED, DISABLED |
| LOCKED | 锁定（密码错误次数过多） | ACTIVE |
| DISABLED | 禁用 | （终态，需管理员恢复） |

### 3.2 Token 生命周期

```
ISSUED -> ACTIVE -> BLACKLISTED (登出) -> EXPIRED
```

- 登出时 Token 加入 Redis 黑名单（`token:blacklist:{token}`）
- Token 过期时间由配置决定

### 3.3 负向断言

- DISABLED 用户不能登录
- LOCKED 用户不能登录（需管理员解锁）
- 黑名单中的 Token 不能使用
- 无 Token 的请求不能访问受保护资源（除白名单）

### 3.4 白名单（不需要认证的路径）

- `/api/v1/auth/login`
- `/api/v1/system/health`
- `/actuator/health`
- `/doc.html`, `/swagger-ui/**`, `/v3/api-docs/**`

---

## 4. 异常处理

| 异常场景 | 错误码 | 降级策略 |
|---------|--------|---------|
| Token 过期 | 401 | 返回 401，前端跳转登录 |
| Token 在黑名单中 | 401 | 返回 401，前端跳转登录 |
| Token 格式错误 | 401 | 返回 401 |
| 权限不足 | 403 | 返回 403 |
| 账号锁定 | 401 | 返回 401，提示联系管理员 |
| 用户名/密码错误 | 400 | 返回 400 |
| 跨租户访问 | 403 | 返回 403（依赖 S-01） |

---

## 验收标准（BDD）

### 场景 1：正常登录
- **Given** 用户 admin 存在，状态 ACTIVE，密码正确
- **When** POST /api/v1/auth/login {username: "admin", password: "xxx"}
- **Then** 返回 200 + JWT Token
- **And** Token 中包含 userId、username
- **And** 返回角色和权限列表

### 场景 2：携带 Token 访问受保护资源
- **Given** 用户已登录，持有有效 JWT Token
- **When** GET /api/v1/subjects/tree，Header: Authorization: Bearer {token}
- **Then** 返回 200 + 科目树数据
- **And** SecurityContext 中 LoginUser.userId 正确

### 场景 3：无 Token 访问被拒
- **Given** 请求未携带 Authorization Header
- **When** GET /api/v1/subjects/tree
- **Then** 返回 401，错误码 20002
- **And** 不返回任何业务数据

### 场景 4：登出后 Token 失效
- **Given** 用户已登录，持有有效 Token
- **When** POST /api/v1/auth/logout
- **Then** Token 加入 Redis 黑名单
- **And** 再次使用该 Token 访问返回 401

### 场景 5：数据权限过滤 - DEPT 级别
- **Given** 用户角色 data_scope = DEPT，属于部门 A
- **When** 查询 t_business_doc 列表
- **Then** SQL 自动注入 `WHERE dept_id = A`
- **And** 不返回部门 B 的数据

### 场景 6：数据权限过滤 - SELF 级别
- **Given** 用户角色 data_scope = SELF，userId = 1
- **When** 查询 t_expense_reimbursement 列表
- **Then** SQL 自动注入 `WHERE created_by = 1`
- **And** 不返回其他用户的数据

### 场景 7：数据权限过滤 - ALL 级别
- **Given** 用户角色 data_scope = ALL
- **When** 查询 t_business_doc 列表
- **Then** SQL 不注入过滤条件
- **And** 返回全部数据

### 场景 8：密码错误
- **Given** 用户 admin 存在
- **When** POST /api/v1/auth/login {username: "admin", password: "wrong"}
- **Then** 返回 400，错误码 20011
- **And** 不返回 Token

---

## 依赖关系

- **S-01**：多租户 - JWT Token 中需包含 tenantId（多租户场景）
- **S-03**：全局字典 - 角色/菜单属于基础档案

---

## API 端点清单

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /api/v1/auth/login | 登录 | 否 |
| POST | /api/v1/auth/logout | 登出 | 是 |
| GET | /api/v1/system/role/page | 角色列表(分页) | 是 |
| GET | /api/v1/system/role/{id} | 角色详情 | 是 |
| GET | /api/v1/system/role/{id}/menus | 角色菜单ID列表 | 是 |
| PUT | /api/v1/system/role/{id}/menus | 分配角色菜单 | 是 |
| POST | /api/v1/system/role | 创建角色 | 是 |
| PUT | /api/v1/system/role/{id} | 修改角色 | 是 |
| PUT | /api/v1/system/role/{id}/status | 修改角色状态 | 是 |
| DELETE | /api/v1/system/role/{id} | 删除角色 | 是 |
| GET | /api/v1/system/menu/tree | 菜单树 | 是 |
| GET | /api/v1/system/menu/options | 菜单选项 | 是 |
| GET | /api/v1/system/menu/{id} | 菜单详情 | 是 |
| POST | /api/v1/system/menu | 创建菜单 | 是 |
| PUT | /api/v1/system/menu/{id} | 修改菜单 | 是 |
| DELETE | /api/v1/system/menu/{id} | 删除菜单 | 是 |
| GET | /api/v1/system/menu/routes | 菜单路由 | 是 |
| GET | /api/v1/system/user/page | 用户列表(分页) | 是 |
| GET | /api/v1/system/user/{id} | 用户详情 | 是 |
| POST | /api/v1/system/user | 创建用户 | 是 |
| PUT | /api/v1/system/user/{id} | 修改用户 | 是 |
| PUT | /api/v1/system/user/{id}/status | 修改用户状态 | 是 |
| PUT | /api/v1/system/user/{id}/reset-pwd | 重置用户密码 | 是 |
| PUT | /api/v1/system/user/{id}/roles | 分配用户角色 | 是 |
| DELETE | /api/v1/system/user/{id} | 删除用户 | 是 |
| GET | /api/v1/system/user/roles | 所有角色列表 | 是 |

---

```yaml
# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"
entity: UserEntity
module: system
table: t_user

states:
  ACTIVE:
    description: "正常使用"
    initial: true
    terminal: false
  LOCKED:
    description: "锁定（密码错误次数过多）"
    initial: false
    terminal: false
  DISABLED:
    description: "禁用"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: ACTIVE
    to: LOCKED
    trigger: lockUser
    precondition: "status == ACTIVE"
    postcondition: "status == LOCKED; lockedAt recorded"
    side_effects: []
    test_ref: test_lock_user

  - id: T-02
    from: LOCKED
    to: ACTIVE
    trigger: unlockUser
    precondition: "status == LOCKED"
    postcondition: "status == ACTIVE"
    side_effects: []
    test_ref: test_unlock_user

  - id: T-03
    from: ACTIVE
    to: DISABLED
    trigger: disableUser
    precondition: "status == ACTIVE"
    postcondition: "status == DISABLED"
    side_effects: []
    test_ref: test_disable_user
    negative_assertions:
      - assertion: "DISABLED 用户不能登录"
        method: test_disabled_user_cannot_login

constraints:
  - id: C-01
    type: database
    rule: "password 列存储 BCrypt 加密后的密码"
    enforcement: "BCryptPasswordEncoder"

  - id: C-02
    type: business
    rule: "所有 Token 在登出时加入 Redis 黑名单"
    enforcement: "JwtLogoutHandler + Redis"

  - id: C-03
    type: business
    rule: "data_scope=ALL 的角色不注入 SQL 过滤条件"
    enforcement: "DataPermissionInterceptor"

  - id: C-04
    type: business
    rule: "已配置数据权限的表: t_expense_reimbursement, t_business_doc, t_asset_card, t_budget_entry"
    enforcement: "DataPermissionInterceptor FILTER_CONFIG"

acceptance_tests:
  - id: AT-001
    description: "正常登录返回 Token 和权限"
    method: test_login_success
    assertion: "返回 JWT Token + userId + roles + permissions"
    status: covered

  - id: AT-002
    description: "携带 Token 访问受保护资源"
    method: test_authenticated_access
    assertion: "返回 200 + 业务数据"
    status: covered

  - id: AT-003
    description: "无 Token 访问被拒"
    method: test_unauthenticated_blocked
    assertion: "返回 401, 错误码 20002"
    status: covered

  - id: AT-004
    description: "登出后 Token 失效"
    method: test_logout_token_blacklisted
    assertion: "Token 在 Redis 黑名单中, 再次使用返回 401"
    status: covered

  - id: AT-005
    description: "数据权限 DEPT 级别过滤"
    method: test_data_scope_dept
    assertion: "SQL 包含 WHERE dept_id = :currentUserDept"
    status: covered

  - id: AT-006
    description: "数据权限 SELF 级别过滤"
    method: test_data_scope_self
    assertion: "SQL 包含 WHERE created_by = :currentUserId"
    status: covered

  - id: AT-007
    description: "数据权限 ALL 级别不过滤"
    method: test_data_scope_all
    assertion: "SQL 不包含 dept_id/created_by 过滤条件"
    status: covered

  - id: AT-008
    description: "密码错误返回 20011"
    method: test_wrong_password
    assertion: "返回 400, 错误码 20011"
    status: covered

out_of_scope:
  - "OAuth2 第三方登录（不在当前范围）"
  - "短信验证码登录（不在当前范围）"
  - "多因子认证（不在当前范围）"
  - "细粒度 API 权限（当前仅角色级，未到按钮级）"

dependencies:
  - spec: S-01
    relation: "多租户场景下 JWT Token 需包含 tenantId"
  - spec: S-03
    relation: "角色/菜单属于基础档案"
```
