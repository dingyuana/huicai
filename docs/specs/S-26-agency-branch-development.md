# S-26 - Agency 分支开发：多租户架构 + 代账业务引擎

> **版本**：2.0 | **编号**：HUICAI-SPC-126 | **日期**：2026-07-24 | **作者**：Hermes
> **状态**：✅ 已审核（2026-07-24 老丁确认通过）
> **关联需求**：REQ-2026-066\~075（Agency 分支需求，详见需求登记册 §十三 Agency 分支）
> **关联文档**：[多租户架构设计](../architecture/多租户架构设计.md)、[S-01 多租户](S-01-多租户架构与数据隔离.md)、[S-02 RBAC](S-02-统一身份认证与RBAC权限.md)、[P54 代码重构](P54-code-restructure-base-sme-agency.md)、[技术方案](../CORE-技术方案.md)

> **V2.0 变更摘要**（2026-07-24）：基于行业标准（金蝶账无忧、畅捷通易代账）全面对比分析，发现原设计存在核心缺陷——代账公司内部无角色分工、无会计-客户分配隔离、无服务流程管理。V2.0 从数据模型层面重构代账公司内部组织架构，新增代理内角色体系（经理/会计/审核员/助理）和客户分配机制（派工），从根本上解决"一个财务管理多个实体公司"的模型错位问题。

***

## 0. 背景与目标

### 0.1 现状

| 维度            | 现状（V1.0 实现后）                                                      |
| ------------- | ----------------------------------------------------------------- |
| Sprint 1-4    | ✅ 已完成：多租户基础设施 + 用户认证切换 + 代账业务引擎骨架 + 前端适配           |
| 数据库表         | 4 张（t_agency / t_enterprise / t_agency_enterprise / t_contract）  |
| 用户类型         | 3 种（SUPER_ADMIN / AGENCY / ENTERPRISE），但 AGENCY 内部无角色区分      |
| 客户分配         | ❌ 无 — AGENCY 用户可访问代理公司下**全部**企业，会计之间无数据隔离            |
| 代理内角色        | ❌ 无 — 所有 AGENCY 用户权限相同，无经理/会计/审核员分工                    |
| 服务流程         | ❌ 无 — 无派工记录、无服务进度跟踪、无工作量统计                            |
| 批量操作         | ⚠️ 骨架已搭建，核心逻辑标记 TODO                                       |
| 合同管理         | ✅ 基础 CRUD + 续费提醒，但未与客户分配关联                               |

### 0.2 核心问题

对比行业标准（金蝶账无忧、畅捷通易代账），慧财当前设计存在以下根本性缺陷：

| 问题 | 行业标准 | 慧财当前 | 差距等级 |
|------|---------|---------|---------|
| 代理内角色 | 记账员/审核员/客户经理/报税员 四角色 | 仅 AGENCY 一种，扁平 | **严重** |
| 客户分配隔离 | 会计只能看分配给自己的客户 | 看代理公司下全部企业 | **严重** |
| 派工机制 | 管理者分配客户给会计，可追溯 | 无 | **严重** |
| 服务流程 | 取票→记账→审核→报税 全流程跟踪 | 无 | 较大 |
| 工作量统计 | 每人负责客户数、完成率 | 无 | 较大 |

### 0.3 目标（V2.0 扩展）

在 V1.0 已完成的多租户基础设施之上，V2.0 新增：

1. **代理内角色体系**：AGENCY_ADMIN（经理）/ ACCOUNTANT（会计）/ REVIEWER（审核员）/ ASSISTANT（助理）
2. **客户分配机制**：经理将客户企业分配给具体会计，会计只能访问分配给自己的企业
3. **派工记录追溯**：记录谁在何时把哪个客户分配给了谁
4. **数据权限细化**：ACCOUNTANT 仅看自己的客户，AGENCY_ADMIN 看全部

### 0.4 设计原则

* **同一套代码、同一套数据库**：不分叉，不复制
* **三层防线**：前端路由 → MyBatis 拦截器 → PostgreSQL RLS
* **增量迁移**：每个 Sprint 可独立验证，不破坏现有 SME 功能
* **行业对标**：角色体系和客户分配对齐金蝶账无忧/畅捷通易代账标准

***

## 1. 输入契约

### 1.1 用户类型定义（V2.0 扩展）

| userType | agencyRole | agency_id | enterprise_id | 可操作范围 |
|----------|-----------|-----------|--------------|-----------|
| `SUPER_ADMIN` | — | null | null | 所有（系统管理） |
| `AGENCY` | `AGENCY_ADMIN` | 非空 | null | 代理公司下**全部**客户企业 + 管理会计 |
| `AGENCY` | `ACCOUNTANT` | 非空 | null | 仅分配给自己的客户企业 |
| `AGENCY` | `REVIEWER` | 非空 | null | 代理公司下全部企业（只读审核权限） |
| `AGENCY` | `ASSISTANT` | 非空 | null | 仅分配给自己的客户企业（仅录入权限） |
| `ENTERPRISE` | — | null | 非空 | 仅本企业 |

### 1.2 代理内角色权限矩阵

| 操作 | AGENCY_ADMIN | ACCOUNTANT | REVIEWER | ASSISTANT |
|------|:-----------:|:----------:|:--------:|:---------:|
| 查看客户列表 | 全部 | 仅自己的 | 全部 | 仅自己的 |
| 切换客户企业 | 全部 | 仅自己的 | 全部（只读） | 仅自己的 |
| 凭证录入 | 全部 | 仅自己的 | — | 仅自己的 |
| 凭证审核 | 全部 | — | 全部 | — |
| 报表查看 | 全部 | 仅自己的 | 全部 | 仅自己的 |
| 发票管理 | 全部 | 仅自己的 | 全部 | 仅自己的 |
| 管理会计（分配/启停） | 是 | — | — | — |
| 批量操作 | 全部 | 仅自己的 | — | — |
| 合同管理 | 全部 | 仅自己的 | 全部 | — |
| 系统管理 | — | — | — | — |

### 1.3 登录输入（不变）

| 参数       | 类型     | 约束 | 说明              |
| -------- | ------ | -- | --------------- |
| username | String | 非空 | 用户名             |
| password | String | 非空 | 密码（BCrypt 加密存储） |

### 1.4 切换企业输入（V2.0 扩展）

| 参数           | 类型   | 约束 | 说明 |
| ------------ | ---- | -- | --- |
| enterpriseId | Long | 非空 | 目标企业 ID |
| 前置条件（AGENCY_ADMIN） | — | — | t_agency_enterprise 中存在绑定关系 |
| 前置条件（ACCOUNTANT） | — | — | t_agency_user_enterprise 中存在分配关系 |
| 前置条件（REVIEWER） | — | — | t_agency_enterprise 中存在绑定关系（只读） |

### 1.5 分配客户企业输入（新增）

| 参数 | 类型 | 约束 | 说明 |
|------|------|------|------|
| agencyUserId | Long | 非空 | 目标代理用户 ID（必须是 ACCOUNTANT 或 ASSISTANT） |
| enterpriseId | Long | 非空 | 目标企业 ID |
| 前置条件 | — | — | 操作者为 AGENCY_ADMIN，且目标企业已绑定到同一代理公司 |

### 1.6 新建代理用户输入（新增）

| 参数 | 类型 | 约束 | 说明 |
|------|------|------|------|
| username | String | 非空，≤50字 | 登录用户名 |
| password | String | 非空 | 密码 |
| realName | String | 非空，≤100字 | 真实姓名 |
| agencyRole | String | 非空 | AGENCY_ADMIN / ACCOUNTANT / REVIEWER / ASSISTANT |
| agencyId | Long | 非空 | 所属代理公司 |
| 前置条件 | — | — | 操作者为 SUPER_ADMIN 或 AGENCY_ADMIN |

***

## 2. 输出契约

### 2.1 登录响应（V2.0 扩展）

```json
{
  "code": 200,
  "data": {
    "token": "eyJ...",
    "refreshToken": "eyJ...",
    "userType": "AGENCY",
    "agencyRole": "ACCOUNTANT",
    "enterpriseId": null,
    "agencyId": 1,
    "enterpriseList": [
      { "id": 2, "name": "山东华杉医疗科技", "taxId": "91370100MA3..." }
    ]
  }
}
```

> **V2.0 变更**：新增 `agencyRole` 字段；ACCOUNTANT/ASSISTANT 的 `enterpriseList` 仅包含分配给自己的企业。

### 2.2 切换企业响应（不变）

```json
{
  "code": 200,
  "data": {
    "enterpriseId": 3,
    "enterpriseName": "山东恺拓蔚兰",
    "seedDataDone": true
  }
}
```

### 2.3 分配客户企业响应（新增）

```json
{
  "code": 200,
  "data": {
    "assignmentId": 1,
    "agencyUserId": 5,
    "enterpriseId": 3,
    "assignedBy": 1,
    "assignedAt": "2026-07-24T10:30:00"
  }
}
```

### 2.4 跨租户拦截响应（不变）

```json
{
  "code": 20003,
  "message": "禁止跨租户操作",
  "data": null
}
```

### 2.5 未分配客户拦截响应（新增）

```json
{
  "code": 20010,
  "message": "您未被分配该客户企业，请联系经理",
  "data": null
}
```

***

## 3. 状态流转

### 3.1 企业状态机（不变）

```
PENDING → ACTIVE → SUSPENDED → TERMINATED
```

| 状态         | 说明       | 可转换到               | terminal |
| ---------- | -------- | ------------------ | -------- |
| PENDING    | 创建未激活    | ACTIVE             | false    |
| ACTIVE     | 正常使用     | SUSPENDED          | false    |
| SUSPENDED  | 暂停（欠费等）  | ACTIVE, TERMINATED | false    |
| TERMINATED | 终止（数据保留） | —                  | true     |

### 3.2 代理公司状态机（不变）

```
ACTIVE → SUSPENDED → TERMINATED
```

### 3.3 代理用户状态机（新增）

```
ACTIVE → SUSPENDED → TERMINATED
```

| 状态 | 说明 | 可转换到 | terminal |
|------|------|---------|----------|
| ACTIVE | 正常在职 | SUSPENDED | false |
| SUSPENDED | 暂停（休假/离职） | ACTIVE, TERMINATED | false |
| TERMINATED | 离职（不可恢复） | — | true |

### 3.4 客户分配状态机（新增）

```
ASSIGNED → UNASSIGNED
```

| 状态 | 说明 | 可转换到 | terminal |
|------|------|---------|----------|
| ASSIGNED | 已分配 | UNASSIGNED | false |
| UNASSIGNED | 已取消分配 | — | true（逻辑删除） |

### 3.5 合法转换

| 转换 | 触发方法 | 前置条件 | 副作用 |
|------|---------|---------|--------|
| PENDING→ACTIVE | activateEnterprise | status==PENDING | 初始化种子数据 |
| ACTIVE→SUSPENDED | suspendEnterprise | status==ACTIVE | 记录原因 |
| SUSPENDED→ACTIVE | reactivateEnterprise | status==SUSPENDED | — |
| SUSPENDED→TERMINATED | terminateEnterprise | status==SUSPENDED | 数据保留不删除 |
| ACTIVE→SUSPENDED（用户） | suspendAgencyUser | status==ACTIVE | 该用户无法登录 |
| SUSPENDED→ACTIVE（用户） | reactivateAgencyUser | status==SUSPENDED | 恢复登录 |
| SUSPENDED→TERMINATED（用户） | terminateAgencyUser | status==SUSPENDED | 用户不可恢复 |
| 未分配→已分配 | assignEnterprise | 目标用户为 ACCOUNTANT/ASSISTANT | 写入分配记录 |
| 已分配→未分配 | unassignEnterprise | 分配记录存在 | 软删除分配记录 |

### 3.6 负向断言

* PENDING 状态不能执行业务操作（只能管理企业信息）
* SUSPENDED 状态不能创建新数据（只能查看）
* TERMINATED 状态不能执行任何写操作
* 不能通过修改 enterprise_id 参数访问其他企业数据
* AGENCY 用户不能访问未绑定的企业
* ENTERPRISE 用户不能切换企业
* **ACCOUNTANT 不能访问未分配给自己的企业**（新增）
* **ASSISTANT 不能执行审核操作**（新增）
* **非 AGENCY_ADMIN 不能管理代理用户**（新增）
* **非 AGENCY_ADMIN 不能分配客户企业**（新增）
* **SUSPENDED 代理用户不能登录**（新增）
* **不能将客户分配给非同一代理公司的用户**（新增）

***

## 4. 异常处理

| 异常场景 | 错误码 | 降级策略 |
|---------|--------|---------|
| 企业上下文缺失 | 20004 | 拦截请求，要求重新登录 |
| 跨企业越权访问 | 20003 | 拦截操作，记录审计日志 |
| 企业已暂停 | 20005 | 返回提示，引导联系管理员 |
| 企业不存在 | 20007 | 拦截请求，记录异常 |
| 代理未绑定该企业 | 20008 | 拦截切换请求 |
| 种子数据初始化失败 | 20009 | 回滚企业创建事务 |
| **未分配该客户企业** | **20010** | **拦截切换请求，提示联系经理**（新增） |
| **无权管理代理用户** | **20011** | **拦截操作，仅 AGENCY_ADMIN 可执行**（新增） |
| **代理用户已暂停** | **20012** | **拦截登录，提示联系管理员**（新增） |
| **跨代理公司分配** | **20013** | **拦截分配，目标用户与目标企业不在同一代理公司**（新增） |

***

## 验收标准（BDD）

### V1.0 已有场景（8 个，保持不变）

#### 场景 1：SME 用户登录直达业务空间

* **Given** userType=ENTERPRISE 的用户，enterprise_id=1
* **When** 调用 POST /api/v1/auth/login
* **Then** 返回 token + userType=ENTERPRISE + enterpriseId=1
* **And** 不返回 enterpriseList
* **And** 前端跳转 /dashboard

#### 场景 2：AGENCY 用户登录看到客户列表

* **Given** userType=AGENCY 的用户，agency_id=1
* **When** 调用 POST /api/v1/auth/login
* **Then** 返回 token + userType=AGENCY + agencyId=1
* **And** 返回 enterpriseList（包含所有绑定的客户企业）
* **And** enterpriseId=null（未选择客户）
* **And** 前端跳转 /agency/enterprise-list

#### 场景 3：代理用户切换客户企业

* **Given** AGENCY 用户已登录，选择 enterprise_id=3
* **When** 调用 POST /api/v1/enterprise/switch?enterpriseId=3
* **Then** 返回 200 + enterpriseId=3
* **And** 后端设置 RLS context: app.enterprise_id=3
* **And** 后续所有查询自动受 enterprise_id=3 限制

#### 场景 4：跨企业访问被拦截

* **Given** AGENCY 用户已切换到 enterprise_id=2
* **When** 尝试查询 enterprise_id=3 的凭证数据
* **Then** 返回错误码 20003「禁止跨租户操作」
* **And** 不返回任何数据
* **And** 记录审计日志

#### 场景 5：新建客户企业自动初始化种子数据

* **Given** AGENCY 用户创建新客户企业"济南华信科技"
* **When** 调用 POST /api/v1/agency/enterprises
* **Then** t_enterprise 插入一条 PENDING 记录
* **And** 调用 activateEnterprise 后，自动克隆科目模板、凭证类型、摘要库
* **And** 初始化数据的 enterprise_id 全部为新企业 ID
* **And** 不影响其他企业的数据

#### 场景 6：ENTERPRISE 用户不能切换企业

* **Given** userType=ENTERPRISE 的用户
* **When** 调用 POST /api/v1/enterprise/switch?enterpriseId=99
* **Then** 返回错误码 20008「无权切换企业」
* **And** 不修改当前 enterprise_id

#### 场景 7：已暂停企业不能创建数据

* **Given** enterprise_id=5 状态为 SUSPENDED
* **When** 该企业用户尝试创建新凭证
* **Then** 返回错误码 20005「企业已暂停」
* **And** 不创建任何数据

#### 场景 8：RLS 兜底拦截

* **Given** MyBatis 拦截器被绕过（如原生 SQL）
* **When** 查询 t_voucher 时未带 enterprise_id 条件
* **Then** PostgreSQL RLS 自动注入 enterprise_id 条件
* **And** 仅返回当前 enterprise_id 的数据

### V2.0 新增场景（10 个）

#### 场景 9：AGENCY_ADMIN 创建会计并分配客户

* **Given** AGENCY_ADMIN 用户已登录，agency_id=1
* **When** 调用 POST /api/v1/agency/users 创建 ACCOUNTANT 用户"张会计"
* **Then** t_user 插入一条 userType=AGENCY, agencyRole=ACCOUNTANT 记录
* **And** t_agency_user 插入一条绑定记录
* **When** 调用 POST /api/v1/agency/assignments 将 enterprise_id=2 分配给张会计
* **Then** t_agency_user_enterprise 插入一条分配记录
* **And** 张会计登录后 enterpriseList 仅包含 enterprise_id=2

#### 场景 10：会计只能看到分配给自己的客户

* **Given** ACCOUNTANT 用户"张会计"已登录，被分配了 enterprise_id=2
* **When** 调用 GET /api/v1/agency/enterprises
* **Then** 返回的 enterpriseList 仅包含 enterprise_id=2
* **And** 不包含同一代理公司下的其他企业（如 enterprise_id=3）

#### 场景 11：会计不能切换到未分配的企业

* **Given** ACCOUNTANT 用户"张会计"已登录，仅被分配 enterprise_id=2
* **When** 调用 POST /api/v1/enterprise/switch?enterpriseId=3
* **Then** 返回错误码 20010「您未被分配该客户企业」
* **And** 不修改当前 enterprise_id

#### 场景 12：AGENCY_ADMIN 查看全部客户

* **Given** AGENCY_ADMIN 用户已登录，agency_id=1
* **When** 调用 GET /api/v1/agency/enterprises
* **Then** 返回代理公司下所有绑定的客户企业
* **And** 包含分配给不同会计的所有企业

#### 场景 13：ASSISTANT 不能执行审核操作

* **Given** ASSISTANT 用户已登录，已切换到 enterprise_id=2
* **When** 调用 POST /api/v1/voucher/{id}/audit
* **Then** 返回错误码 20011「无权执行审核操作」
* **And** 凭证状态不变

#### 场景 14：非 AGENCY_ADMIN 不能管理代理用户

* **Given** ACCOUNTANT 用户已登录
* **When** 调用 POST /api/v1/agency/users（创建新代理用户）
* **Then** 返回错误码 20011「无权管理代理用户」
* **And** 不创建任何用户

#### 场景 15：AGENCY_ADMIN 取消会计的客户分配

* **Given** AGENCY_ADMIN 已登录，张会计被分配了 enterprise_id=2
* **When** 调用 DELETE /api/v1/agency/assignments/{assignmentId}
* **Then** 分配记录软删除（deleted=1）
* **And** 张会计登录后 enterpriseList 不再包含 enterprise_id=2

#### 场景 16：AGENCY_ADMIN 暂停会计账号

* **Given** AGENCY_ADMIN 已登录，张会计状态为 ACTIVE
* **When** 调用 POST /api/v1/agency/users/{userId}/suspend
* **Then** 张会计状态变为 SUSPENDED
* **And** 张会计无法登录（返回错误码 20012）

#### 场景 17：REVIEWER 可查看全部企业但不可修改

* **Given** REVIEWER 用户已登录，agency_id=1
* **When** 切换到 enterprise_id=2 后尝试创建凭证
* **Then** 返回错误码 20011「无权执行写操作」
* **And** 可以正常查看凭证列表和报表

#### 场景 18：跨代理公司分配被拦截

* **Given** AGENCY_ADMIN 用户属于 agency_id=1
* **When** 尝试将 agency_id=2 的会计分配给 agency_id=1 的企业
* **Then** 返回错误码 20013「跨代理公司分配」
* **And** 不创建分配记录

***

## 5. 技术方案

### 5.1 数据模型（V2.0 扩展）

在 V1.0 已有 4 张表基础上，V2.0 新增 2 张表：

#### 5.1.1 新增表：t_agency_user（代理公司内部用户）

```sql
CREATE TABLE t_agency_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id BIGINT NOT NULL REFERENCES t_agency(id),
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    agency_role VARCHAR(20) NOT NULL CHECK (agency_role IN ('AGENCY_ADMIN', 'ACCOUNTANT', 'REVIEWER', 'ASSISTANT')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id)
);
```

| 列 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| agency_id | BIGINT FK→t_agency | 所属代理公司 |
| user_id | BIGINT FK→t_user | 关联登录用户（UNIQUE，一个用户只能属于一个代理公司的一个角色） |
| agency_role | VARCHAR(20) | AGENCY_ADMIN / ACCOUNTANT / REVIEWER / ASSISTANT |
| status | VARCHAR(20) | ACTIVE / SUSPENDED / TERMINATED |

#### 5.1.2 新增表：t_agency_user_enterprise（会计-客户分配）

```sql
CREATE TABLE t_agency_user_enterprise (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_user_id BIGINT NOT NULL REFERENCES t_agency_user(id),
    enterprise_id BIGINT NOT NULL REFERENCES t_enterprise(id),
    assigned_by BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_by BIGINT,
    unassigned_at TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    UNIQUE (agency_user_id, enterprise_id)
);
```

| 列 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| agency_user_id | BIGINT FK→t_agency_user | 被分配的代理用户 |
| enterprise_id | BIGINT FK→t_enterprise | 分配的客户企业 |
| assigned_by | BIGINT | 分配人（AGENCY_ADMIN 的 user_id） |
| assigned_at | TIMESTAMP | 分配时间 |
| unassigned_by | BIGINT | 取消分配人 |
| unassigned_at | TIMESTAMP | 取消分配时间 |

#### 5.1.3 已有表扩展

**t_user 扩展**：新增 `agency_role` 列（冗余，方便查询）

```sql
ALTER TABLE t_user ADD COLUMN agency_role VARCHAR(20);
-- 值：AGENCY_ADMIN / ACCOUNTANT / REVIEWER / ASSISTANT / null（非 AGENCY 用户）
```

### 5.2 数据权限调整

```
原模型（V1.0）：
  AGENCY 用户 → agency_id → t_agency_enterprise → 全部企业

新模型（V2.0）：
  AGENCY_ADMIN → agency_id → t_agency_enterprise → 全部企业
  ACCOUNTANT   → agency_user_id → t_agency_user_enterprise → 仅分配的企业
  REVIEWER     → agency_id → t_agency_enterprise → 全部企业（只读）
  ASSISTANT    → agency_user_id → t_agency_user_enterprise → 仅分配的企业（仅录入）
```

EnterpriseController.switchEnterprise 校验逻辑调整：

```java
// V1.0: 仅校验 t_agency_enterprise 绑定
// V2.0: 按 agencyRole 分流校验
if ("AGENCY_ADMIN".equals(agencyRole) || "REVIEWER".equals(agencyRole)) {
    // 校验 t_agency_enterprise 绑定
} else if ("ACCOUNTANT".equals(agencyRole) || "ASSISTANT".equals(agencyRole)) {
    // 校验 t_agency_user_enterprise 分配
}
```

### 5.3 包结构（V2.0 扩展）

```
com.huicai.agency
├── tenant/              # 多租户管理（V1.0 已有）
│   ├── controller/      # AgencyController, EnterpriseController
│   ├── service/         # AgencyService, EnterpriseService, EnterpriseStateMachineService
│   ├── mapper/          # AgencyMapper, EnterpriseMapper, AgencyEnterpriseMapper
│   ├── entity/          # AgencyEntity, EnterpriseEntity, AgencyEnterpriseEntity
│   └── dto/             # ...
├── user/                # 代理用户管理（V2.0 新增）
│   ├── controller/      # AgencyUserController
│   ├── service/         # AgencyUserService, AgencyUserStateMachineService
│   ├── mapper/          # AgencyUserMapper, AgencyUserEnterpriseMapper
│   ├── entity/          # AgencyUserEntity, AgencyUserEnterpriseEntity
│   └── dto/             # AgencyUserCreateDTO, AssignmentCreateDTO, ...
├── batch/               # 批量操作引擎（V1.0 已有，待完善）
│   ├── controller/      # BatchOperationController
│   ├── service/         # BatchImportService, BatchAuditService, BatchCloseService
│   └── dto/             # ...
├── client/              # 客户CRM（V1.0 已有）
│   ├── controller/      # ClientController
│   ├── service/         # ClientService, ContractService
│   ├── entity/          # ContractEntity
│   └── dto/             # ...
└── interceptor/         # 企业级数据权限拦截器（V1.0 已有）
    └── EnterpriseDataPermissionInterceptor
```

### 5.4 公共基础设施改造（V2.0 扩展）

| 改造项 | 文件 | 说明 |
|--------|------|------|
| LoginUser 扩展 | `config/security/LoginUser.java` | 加 agencyRole 字段 |
| JWT 扩展 | `config/security/JwtProvider.java` | 加 agencyRole claim |
| SecurityUtils 扩展 | `base/system/util/SecurityUtils.java` | 加 getCurrentAgencyRole() |
| AuthController 扩展 | `base/system/controller/AuthController.java` | 登录时查询 t_agency_user 获取 agencyRole |
| /userinfo 扩展 | 同上 | 返回 agencyRole |
| 前端 auth.store 扩展 | `frontend/src/stores/auth.store.ts` | 加 agencyRole 状态 |

***

## 6. 开发计划

### 6.1 已完成（V1.0 Sprint 1-4）

| Sprint | 内容 | 状态 |
|--------|------|------|
| Sprint 1 | 多租户基础设施（建表 + enterprise_id + 拦截器 + RLS） | ✅ 完成 |
| Sprint 2 | 用户认证与企业切换（JWT 扩展 + 登录 + 切换 + 状态机） | ✅ 完成 |
| Sprint 3 | 代账业务引擎（客户管理 + 批量操作骨架 + CRM） | ✅ 完成 |
| Sprint 4 | 前端适配（代理工作台 + 切换组件 + 批量 UI） | ✅ 完成 |

### 6.2 V2.0 新增 Sprint

#### Sprint 5：代理内角色体系（P0 阻塞项）

| 微循环 | 内容 | 涉及文件 |
|--------|------|---------|
| MC5-01 | Flyway V111：创建 t_agency_user + t_agency_user_enterprise 表 | V111__create_agency_user_tables.sql |
| MC5-02 | Flyway V112：t_user 加 agency_role 列 | V112__alter_user_add_agency_role.sql |
| MC5-03 | AgencyUserEntity + AgencyUserEnterpriseEntity | entity/ |
| MC5-04 | AgencyUserMapper + AgencyUserEnterpriseMapper | mapper/ |
| MC5-05 | AgencyUserService + Impl（CRUD + 状态机） | service/ |
| MC5-06 | AgencyUserController（CRUD + 启停） | controller/ |
| MC5-07 | 客户分配 Service（assign/unassign/list） | service/ |
| MC5-08 | 客户分配 Controller | controller/ |
| MC5-09 | LoginUser + JWT + SecurityUtils 扩展 agencyRole | config/security/ |
| MC5-10 | AuthController 登录时查询 agencyRole | controller/ |
| MC5-11 | EnterpriseController.switchEnterprise 按 agencyRole 分流 | controller/ |
| MC5-12 | 测试：AgencyUser CRUD + 状态机 + 分配 + 权限分流 | test/ | ✅ 已完成 |
| MC5-13 | 种子数据：admin 设为 AGENCY_ADMIN + 创建测试会计 | V113__seed_agency_users.sql | ✅ 已完成 |
| MC5-14 | Sprint 5 全量回归 | mvn test | ✅ 已验证 2176 测试 0 Failure |

#### Sprint 6：前端角色适配

| 微循环 | 内容 | 涉及文件 |
|--------|------|---------|
| MC6-01 | auth.store 加 agencyRole + 按角色过滤 enterpriseList | stores/auth.store.ts |
| MC6-02 | 会计管理页面（AGENCY_ADMIN 可见） | views/agency/AccountantList.vue |
| MC6-03 | 客户分配页面（AGENCY_ADMIN 可见） | views/agency/AssignmentManage.vue |
| MC6-04 | EnterpriseSwitcher 按 agencyRole 过滤可选企业 | layouts/components/EnterpriseSwitcher.vue |
| MC6-05 | 路由守卫按 agencyRole 控制菜单可见性 | router/index.ts |
| MC6-06 | 侧边栏按 agencyRole 控制菜单 | layouts/AppSidebar.vue |
| MC6-07 | Sprint 6 全量回归 | npm test |

### 6.3 后续迭代（不在本期范围）

| 迭代 | 内容 | 优先级 |
|------|------|--------|
| Sprint 7 | 服务流程管理（取票→记账→审核→报税 节点跟踪） | P1 |
| Sprint 8 | 工作量统计（每人负责客户数、完成率、报税准时率） | P1 |
| Sprint 9 | 客户标签/分类 + 收款/催收管理 | P1 |
| Sprint 10 | 批量操作完善（批量取票、批量报税、批量生成凭证） | P2 |
| Sprint 11 | 经营分析驾驶舱（收入/回款/续费率/会计绩效） | P2 |
| Sprint 12 | 客户看账门户（客户自助查报表） | P2 |

***

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 权限模型变更影响现有 AGENCY 用户 | 高 | V113 种子数据将现有 AGENCY 用户迁移为 AGENCY_ADMIN，保持向后兼容 |
| 会计分配数据迁移 | 中 | 现有 AGENCY 用户无分配记录，默认可访问全部企业（AGENCY_ADMIN 行为） |
| 前端角色判断逻辑复杂化 | 中 | 统一使用 authStore.isAgencyAdmin / isAccountant 等 computed 属性 |
| 性能（分配关系 JOIN 查询） | 低 | t_agency_user_enterprise 加 (agency_user_id, enterprise_id) 唯一索引 |

***

## 8. 版本历史

| 版本 | 日期 | 作者 | 变更 |
|------|------|------|------|
| 1.0 | 2026-07-23 | Hermes | 初始版本，4 Sprint 多租户架构 + 代账业务引擎 |
| 2.0 | 2026-07-24 | Hermes | 基于行业标准对比，新增代理内角色体系 + 客户分配机制 + 数据权限细化 |

***

```yaml
# === MACHINE-READABLE CONTRACT ===

contract_version: "2.0"
module: agency

# ===== V1.0 已有实体 =====

entities:
  - name: EnterpriseEntity
    table: t_enterprise
    states: [PENDING, ACTIVE, SUSPENDED, TERMINATED]
    initial: PENDING
    terminal: [TERMINATED]

  - name: AgencyEntity
    table: t_agency
    states: [ACTIVE, SUSPENDED, TERMINATED]
    initial: ACTIVE
    terminal: [TERMINATED]

  # ===== V2.0 新增实体 =====

  - name: AgencyUserEntity
    table: t_agency_user
    states: [ACTIVE, SUSPENDED, TERMINATED]
    initial: ACTIVE
    terminal: [TERMINATED]
    roles: [AGENCY_ADMIN, ACCOUNTANT, REVIEWER, ASSISTANT]

  - name: AgencyUserEnterpriseEntity
    table: t_agency_user_enterprise
    states: [ASSIGNED, UNASSIGNED]
    initial: ASSIGNED
    terminal: [UNASSIGNED]

# ===== V1.0 已有转换 =====

transitions:
  # 企业状态机
  - id: T-01
    from: PENDING
    to: ACTIVE
    trigger: activateEnterprise
    precondition: "status == PENDING"
    postcondition: "status == ACTIVE; seedDataDone == true"
    side_effects:
      - entity: SubjectEntity
        action: clone_from_template
      - entity: VoucherTypeEntity
        action: clone_from_template
      - entity: SummaryLibEntity
        action: clone_from_template
      - entity: PeriodEntity
        action: clone_from_template

  - id: T-02
    from: ACTIVE
    to: SUSPENDED
    trigger: suspendEnterprise
    precondition: "status == ACTIVE"

  - id: T-03
    from: SUSPENDED
    to: ACTIVE
    trigger: reactivateEnterprise
    precondition: "status == SUSPENDED"

  - id: T-04
    from: SUSPENDED
    to: TERMINATED
    trigger: terminateEnterprise
    precondition: "status == SUSPENDED"

  # ===== V2.0 新增转换 =====

  # 代理用户状态机
  - id: T-05
    from: ACTIVE
    to: SUSPENDED
    trigger: suspendAgencyUser
    precondition: "status == ACTIVE"
    postcondition: "status == SUSPENDED; user cannot login"

  - id: T-06
    from: SUSPENDED
    to: ACTIVE
    trigger: reactivateAgencyUser
    precondition: "status == SUSPENDED"

  - id: T-07
    from: SUSPENDED
    to: TERMINATED
    trigger: terminateAgencyUser
    precondition: "status == SUSPENDED"
    postcondition: "status == TERMINATED; user irrecoverable"

  # 客户分配
  - id: T-08
    from: null
    to: ASSIGNED
    trigger: assignEnterprise
    precondition: "target user role in [ACCOUNTANT, ASSISTANT]; same agency"
    postcondition: "t_agency_user_enterprise record created"

  - id: T-09
    from: ASSIGNED
    to: UNASSIGNED
    trigger: unassignEnterprise
    precondition: "assignment exists and not deleted"
    postcondition: "assignment soft-deleted"

constraints:
  - id: C-01
    type: database
    rule: "所有业务表必须包含 enterprise_id BIGINT NOT NULL 列"
  - id: C-02
    type: database
    rule: "PostgreSQL RLS 策略：所有业务表启用 enterprise_policy"
  - id: C-03
    type: business
    rule: "JWT Token 中的 enterpriseId 优先于请求参数"
  - id: C-04
    type: immutability
    rule: "TERMINATED 状态的企业不能执行任何写操作"
  - id: C-05
    type: business
    rule: "AGENCY 用户只能操作已绑定的企业"
  - id: C-06
    type: business
    rule: "ACCOUNTANT/ASSISTANT 只能操作已分配的企业"
    enforcement: "EnterpriseController.switchEnterprise + t_agency_user_enterprise"
  - id: C-07
    type: business
    rule: "非 AGENCY_ADMIN 不能管理代理用户和分配客户"
    enforcement: "AgencyUserController @PreAuthorize"
  - id: C-08
    type: business
    rule: "ASSISTANT 不能执行审核操作"
    enforcement: "Service 层 agencyRole 校验"
  - id: C-09
    type: business
    rule: "REVIEWER 只能查看不能修改"
    enforcement: "Service 层 agencyRole 校验"
  - id: C-10
    type: business
    rule: "不能跨代理公司分配客户"
    enforcement: "校验 agency_user.agency_id == enterprise.agency_id"

acceptance_tests:
  # V1.0
  - id: AT-001
    description: "SME 用户登录直达业务空间"
    status: implemented
  - id: AT-002
    description: "AGENCY 用户登录看到客户列表"
    status: implemented
  - id: AT-003
    description: "代理用户切换客户企业"
    status: implemented
  - id: AT-004
    description: "跨企业访问被拦截"
    status: implemented
  - id: AT-005
    description: "新建客户企业自动初始化种子数据"
    status: implemented
  - id: AT-006
    description: "ENTERPRISE 用户不能切换企业"
    status: implemented
  - id: AT-007
    description: "已暂停企业不能创建数据"
    status: implemented
  - id: AT-008
    description: "RLS 兜底拦截"
    status: implemented
  # V2.0 新增
  - id: AT-009
    description: "AGENCY_ADMIN 创建会计并分配客户"
    status: missing
  - id: AT-010
    description: "会计只能看到分配给自己的客户"
    status: missing
  - id: AT-011
    description: "会计不能切换到未分配的企业"
    status: missing
  - id: AT-012
    description: "AGENCY_ADMIN 查看全部客户"
    status: missing
  - id: AT-013
    description: "ASSISTANT 不能执行审核操作"
    status: missing
  - id: AT-014
    description: "非 AGENCY_ADMIN 不能管理代理用户"
    status: missing
  - id: AT-015
    description: "AGENCY_ADMIN 取消会计的客户分配"
    status: missing
  - id: AT-016
    description: "AGENCY_ADMIN 暂停会计账号"
    status: missing
  - id: AT-017
    description: "REVIEWER 可查看全部企业但不可修改"
    status: missing
  - id: AT-018
    description: "跨代理公司分配被拦截"
    status: missing

out_of_scope:
  - "Schema 级隔离（当前采用行级隔离）"
  - "租户计费/计量"
  - "租户资源配额限制"
  - "移动端原生 App"
  - "服务流程节点跟踪（取票→记账→审核→报税）— 计划 Sprint 7"
  - "工作量统计与绩效 — 计划 Sprint 8"
  - "客户标签/分类 + 收款催收 — 计划 Sprint 9"
  - "批量取票/批量报税/批量生成凭证 — 计划 Sprint 10"
  - "经营分析驾驶舱 — 计划 Sprint 11"
  - "客户看账门户 — 计划 Sprint 12"

dependencies:
  - spec: S-01
    relation: "多租户架构基础"
  - spec: S-02
    relation: "JWT Token 扩展 claims"
  - spec: P54
    relation: "agency 包结构已预留"
```
