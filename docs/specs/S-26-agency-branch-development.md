# S-26 - Agency 分支开发：多租户架构 + 代账业务引擎

> **版本**：1.0 | **编号**：HUICAI-SPC-126 | **日期**：2026-07-23 | **作者**：Hermes
> **状态**：⚠️ 待老丁审核
> **关联需求**：REQ-2026-066\~075（Agency 分支需求，详见需求登记册 §十三 Agency 分支）
> **关联文档**：[多租户架构设计](../architecture/多租户架构设计.md)、[S-01 多租户](S-01-多租户架构与数据隔离.md)、[S-02 RBAC](S-02-统一身份认证与RBAC权限.md)、[P54 代码重构](P54-code-restructure-base-sme-agency.md)、[技术方案](../技术方案.md)

***

## 0. 背景与目标

### 0.1 现状

| 维度            | 现状                                                                |
| ------------- | ----------------------------------------------------------------- |
| 包结构           | `com.huicai.agency` 空目录，P54 重构已预留                                 |
| 数据隔离          | 仅 dept/created\_by 级别（DataPermissionInterceptor），无 enterprise\_id |
| JWT           | 仅含 userId + roles，无 tenantId/enterpriseId/userType/agencyId       |
| SecurityUtils | 仅 getCurrentUserId() + getCurrentUsername()                       |
| BaseEntity    | 不存在，69 个 Entity 各自独立定义审计字段                                        |
| 硬编码 tenantId  | ClassificationRuleServiceImpl + AutoGenerationService 中有 `1L` 硬编码 |
| 前端 Agency 路由  | 占位文件，children 为空数组                                                |
| Flyway        | V1 baseline + V2\~V5 + V92\~V98，下一个可用版本号 V99                      |

### 0.2 目标

基于[多租户架构设计](../architecture/多租户架构设计.md)，分 4 个 Sprint 实现 Agency 分支：

1. **多租户基础设施**：建表 + enterprise\_id 列 + 拦截器 + RLS
2. **用户与切换**：JWT 扩展 + 登录流程 + 客户切换接口
3. **代账业务引擎**：客户管理 + 批量操作 + CRM
4. **前端适配**：代理工作台 + 客户切换 + 批量操作 UI

### 0.3 设计原则

* **同一套代码、同一套数据库**：不分叉，不复制

* **三层防线**：前端路由 → MyBatis 拦截器 → PostgreSQL RLS

* **增量迁移**：每个 Sprint 可独立验证，不破坏现有 SME 功能

* **BaseEntity 优先**：先抽公共基类，再统一加 enterprise\_id

***

## 1. 输入契约

### 1.1 用户类型定义

| userType      | agency\_id | enterprise\_id | 可操作范围        |
| ------------- | ---------- | -------------- | ------------ |
| `SUPER_ADMIN` | null       | null           | 所有（系统管理）     |
| `AGENCY`      | 非空         | null           | 该代理绑定的所有客户企业 |
| `ENTERPRISE`  | null       | 非空             | 仅本企业         |

### 1.2 登录输入

| 参数       | 类型     | 约束 | 说明              |
| -------- | ------ | -- | --------------- |
| username | String | 非空 | 用户名             |
| password | String | 非空 | 密码（BCrypt 加密存储） |

### 1.3 切换企业输入

| 参数           | 类型   | 约束 | 说明                                                   |
| ------------ | ---- | -- | ---------------------------------------------------- |
| enterpriseId | Long | 非空 | 目标企业 ID                                              |
| 前置条件         | —    | —  | 当前用户 userType=AGENCY 且 t\_agency\_enterprise 中存在绑定关系 |

### 1.4 新建客户企业输入

| 参数             | 类型     | 约束       | 说明                            |
| -------------- | ------ | -------- | ----------------------------- |
| enterpriseName | String | 非空，≤200字 | 公司名称                          |
| taxId          | String | 非空，≤32字  | 纳税人识别号                        |
| agencyId       | Long   | 非空       | 所属代理公司                        |
| 前置条件           | —      | —        | 操作者为 SUPER\_ADMIN 或 AGENCY 用户 |

***

## 2. 输出契约

### 2.1 登录响应（扩展）

```json
{
  "code": 200,
  "data": {
    "token": "eyJ...",
    "refreshToken": "eyJ...",
    "userType": "AGENCY",
    "enterpriseId": null,
    "agencyId": 1,
    "enterpriseList": [
      { "id": 2, "name": "山东华杉医疗科技", "taxId": "91370100MA3..." },
      { "id": 3, "name": "山东恺拓蔚兰", "taxId": "91370100MA4..." }
    ]
  }
}
```

### 2.2 切换企业响应

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

### 2.3 跨租户拦截响应

```json
{
  "code": 20003,
  "message": "禁止跨租户操作",
  "data": null
}
```

***

## 3. 状态流转

### 3.1 企业状态机

```
PENDING → ACTIVE → SUSPENDED → TERMINATED
```

| 状态         | 说明       | 可转换到               | terminal |
| ---------- | -------- | ------------------ | -------- |
| PENDING    | 创建未激活    | ACTIVE             | false    |
| ACTIVE     | 正常使用     | SUSPENDED          | false    |
| SUSPENDED  | 暂停（欠费等）  | ACTIVE, TERMINATED | false    |
| TERMINATED | 终止（数据保留） | —                  | true     |

### 3.2 代理公司状态机

```
ACTIVE → SUSPENDED → TERMINATED
```

### 3.3 合法转换

| 转换                   | 触发方法                 | 前置条件              | 副作用                     |
| -------------------- | -------------------- | ----------------- | ----------------------- |
| PENDING→ACTIVE       | activateEnterprise   | status==PENDING   | 初始化种子数据（科目/凭证类型/摘要库/期间） |
| ACTIVE→SUSPENDED     | suspendEnterprise    | status==ACTIVE    | 记录原因                    |
| SUSPENDED→ACTIVE     | reactivateEnterprise | status==SUSPENDED | —                       |
| SUSPENDED→TERMINATED | terminateEnterprise  | status==SUSPENDED | 数据保留不删除                 |

### 3.4 负向断言

* PENDING 状态不能执行业务操作（只能管理企业信息）

* SUSPENDED 状态不能创建新数据（只能查看）

* TERMINATED 状态不能执行任何写操作

* 不能通过修改 enterprise\_id 参数访问其他企业数据

* AGENCY 用户不能访问未绑定的企业

* ENTERPRISE 用户不能切换企业

***

## 4. 异常处理

| 异常场景      | 错误码   | 降级策略         |
| --------- | ----- | ------------ |
| 企业上下文缺失   | 20004 | 拦截请求，要求重新登录  |
| 跨企业越权访问   | 20003 | 拦截操作，记录审计日志  |
| 企业已暂停     | 20005 | 返回提示，引导联系管理员 |
| 企业不存在     | 20007 | 拦截请求，记录异常    |
| 代理未绑定该企业  | 20008 | 拦截切换请求       |
| 种子数据初始化失败 | 20009 | 回滚企业创建事务     |

***

## 验收标准（BDD）

### 场景 1：SME 用户登录直达业务空间

* **Given** userType=ENTERPRISE 的用户，enterprise\_id=1

* **When** 调用 POST /api/v1/auth/login

* **Then** 返回 token + userType=ENTERPRISE + enterpriseId=1

* **And** 不返回 enterpriseList

* **And** 前端跳转 /dashboard

### 场景 2：AGENCY 用户登录看到客户列表

* **Given** userType=AGENCY 的用户，agency\_id=1

* **When** 调用 POST /api/v1/auth/login

* **Then** 返回 token + userType=AGENCY + agencyId=1

* **And** 返回 enterpriseList（包含所有绑定的客户企业）

* **And** enterpriseId=null（未选择客户）

* **And** 前端跳转 /agency/enterprise-list

### 场景 3：代理用户切换客户企业

* **Given** AGENCY 用户已登录，选择 enterprise\_id=3

* **When** 调用 POST /api/v1/enterprise/switch?enterpriseId=3

* **Then** 返回 200 + enterpriseId=3

* **And** 后端设置 RLS context: app.enterprise\_id=3

* **And** 后续所有查询自动受 enterprise\_id=3 限制

### 场景 4：跨企业访问被拦截

* **Given** AGENCY 用户已切换到 enterprise\_id=2

* **When** 尝试查询 enterprise\_id=3 的凭证数据

* **Then** 返回错误码 20003「禁止跨租户操作」

* **And** 不返回任何数据

* **And** 记录审计日志

### 场景 5：新建客户企业自动初始化种子数据

* **Given** AGENCY 用户创建新客户企业"济南华信科技"

* **When** 调用 POST /api/v1/agency/enterprises

* **Then** t\_enterprise 插入一条 PENDING 记录

* **And** 调用 activateEnterprise 后，自动克隆科目模板、凭证类型、摘要库

* **And** 初始化数据的 enterprise\_id 全部为新企业 ID

* **And** 不影响其他企业的数据

### 场景 6：ENTERPRISE 用户不能切换企业

* **Given** userType=ENTERPRISE 的用户

* **When** 调用 POST /api/v1/enterprise/switch?enterpriseId=99

* **Then** 返回错误码 20008「无权切换企业」

* **And** 不修改当前 enterprise\_id

### 场景 7：已暂停企业不能创建数据

* **Given** enterprise\_id=5 状态为 SUSPENDED

* **When** 该企业用户尝试创建新凭证

* **Then** 返回错误码 20005「企业已暂停」

* **And** 不创建任何数据

### 场景 8：RLS 兜底拦截

* **Given** MyBatis 拦截器被绕过（如原生 SQL）

* **When** 查询 t\_voucher 时未带 enterprise\_id 条件

* **Then** PostgreSQL RLS 自动注入 enterprise\_id 条件

* **And** 仅返回当前 enterprise\_id 的数据

***

## 5. 技术方案

### 5.1 数据模型（详见多租户架构设计.md §2）

三张新表 + t\_user 扩展 + 69 张业务表加列。

### 5.2 三层防线

1. **前端路由**：按 userType 分发，AGENCY 用户先进客户列表
2. **MyBatis 拦截器**：`EnterpriseDataPermissionInterceptor` 自动注入 enterprise\_id
3. **PostgreSQL RLS**：DB 级兜底，防止拦截器被绕过

### 5.3 包结构

```
com.huicai.agency
├── tenant/          # 代理公司 + 企业管理
│   ├── controller/  # AgencyController, EnterpriseController
│   ├── service/     # AgencyService, EnterpriseService, EnterpriseStateMachineService
│   ├── mapper/      # AgencyMapper, EnterpriseMapper, AgencyEnterpriseMapper
│   ├── entity/      # AgencyEntity, EnterpriseEntity, AgencyEnterpriseEntity
│   └── dto/         # AgencyCreateDTO, EnterpriseCreateDTO, EnterpriseSwitchDTO
├── batch/           # 批量操作引擎
│   ├── controller/  # BatchOperationController
│   ├── service/     # BatchImportService, BatchAuditService, BatchCloseService
│   └── dto/         # BatchImportDTO, BatchResultVO
├── client/          # 客户CRM
│   ├── controller/  # ClientController
│   ├── service/     # ClientService, ContractService
│   ├── entity/      # ContractEntity, RenewalReminderEntity
│   └── dto/         # ClientVO, ContractCreateDTO
└── interceptor/     # 企业级数据权限拦截器
    └── EnterpriseDataPermissionInterceptor
```

### 5.4 公共基础设施改造

| 改造项               | 文件                                    | 说明                                                                        |
| ----------------- | ------------------------------------- | ------------------------------------------------------------------------- |
| BaseEntity 抽取     | 新建 `common/entity/BaseEntity.java`    | 含 id/createdBy/updatedBy/createdAt/updatedAt/deleted/version/enterpriseId |
| SecurityUtils 扩展  | `base/system/util/SecurityUtils.java` | 加 getCurrentEnterpriseId / getCurrentUserType / getCurrentAgencyId        |
| LoginUser 扩展      | `config/security/LoginUser.java`      | 加 enterpriseId / agencyId / userType 字段                                   |
| JWT 扩展            | `config/security/JwtProvider.java`    | 加 4 个 claims: enterpriseId/agencyId/userType/enterpriseList               |
| MetaObjectHandler | `config/MyMetaObjectHandler.java`     | 自动填充 enterprise\_id（INSERT 时从 SecurityUtils 取）                            |
| 拦截器注册             | `config/MyBatisPlusConfig.java`       | 链中加入 EnterpriseDataPermissionInterceptor                                  |

***

## 6. 开发计划（4 个 Sprint）

### Sprint 1：多租户基础设施（P0 阻塞项）

详见 [开发计划文档](../development/plans/S-26-agency-dev-plan.md)。

### Sprint 2：用户认证与切换

### Sprint 3：代账业务引擎

### Sprint 4：前端适配

***

## 7. 风险与缓解

| 风险                      | 影响 | 缓解措施                                                          |
| ----------------------- | -- | ------------------------------------------------------------- |
| 69 张表加列工作量大             | 高  | 分批 Flyway migration，每批 10-15 张表                               |
| Entity-DB 不一致           | 高  | 先抽 BaseEntity，统一加 enterprise\_id，用 check-entity-schema.mjs 验证 |
| RLS context 连接池串号       | 高  | HikariCP 连接借出时清理 session 变量，归还时重置                             |
| 现有测试被 enterprise\_id 破坏 | 中  | Sprint 1 中同步修改所有测试的 Mock 数据                                   |
| 拦截器递归（已有前科）             | 中  | ThreadLocal 预占防护，参考 DataPermissionInterceptor 已有方案            |
| 批量导入跨企业                 | 中  | 所有导入导出接口显式校验 enterprise\_id                                   |

***

```yaml
# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"
entity: EnterpriseEntity (待创建)
module: agency
table: t_enterprise (待创建)

states:
  PENDING:
    description: "创建未激活"
    initial: true
    terminal: false
  ACTIVE:
    description: "正常使用"
    initial: false
    terminal: false
  SUSPENDED:
    description: "暂停"
    initial: false
    terminal: false
  TERMINATED:
    description: "终止（数据保留）"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: PENDING
    to: ACTIVE
    trigger: activateEnterprise
    precondition: "status == PENDING"
    postcondition: "status == ACTIVE; activatedAt recorded; seedDataDone == true"
    side_effects:
      - entity: SubjectEntity
        action: clone_from_template
        filter: "enterprise_id = 0"
      - entity: VoucherTypeEntity
        action: clone_from_template
        filter: "enterprise_id = 0"
      - entity: SummaryLibEntity
        action: clone_from_template
        filter: "enterprise_id = 0"
      - entity: PeriodEntity
        action: clone_from_template
        filter: "enterprise_id = 0"
    test_ref: test_activate_enterprise_seeds_data
    negative_assertions:
      - assertion: "activate should not affect other enterprise data"
        method: test_activate_does_not_pollute_other_enterprises

  - id: T-02
    from: ACTIVE
    to: SUSPENDED
    trigger: suspendEnterprise
    precondition: "status == ACTIVE"
    postcondition: "status == SUSPENDED; reason recorded"
    side_effects: []
    test_ref: test_suspend_enterprise

  - id: T-03
    from: SUSPENDED
    to: ACTIVE
    trigger: reactivateEnterprise
    precondition: "status == SUSPENDED"
    postcondition: "status == ACTIVE"
    side_effects: []
    test_ref: test_reactivate_enterprise

  - id: T-04
    from: SUSPENDED
    to: TERMINATED
    trigger: terminateEnterprise
    precondition: "status == SUSPENDED"
    postcondition: "status == TERMINATED; terminatedAt recorded"
    side_effects: []
    test_ref: test_terminate_enterprise

constraints:
  - id: C-01
    type: database
    rule: "所有 69 张业务表必须包含 enterprise_id BIGINT NOT NULL 列"
    migration: V99~V103
  - id: C-02
    type: database
    rule: "PostgreSQL RLS 策略：所有业务表启用 enterprise_policy"
    migration: V104
  - id: C-06
    type: database
    rule: "t_contract 合同表（代账客户合同管理）"
    migration: V105
  - id: C-03
    type: business
    rule: "JWT Token 中的 enterpriseId 优先于请求参数"
    enforcement: "EnterpriseContextHolder 从 Token 解析"
  - id: C-04
    type: immutability
    rule: "TERMINATED 状态的企业不能执行任何写操作"
    enforcement: "Service 层校验 + RLS"
  - id: C-05
    type: business
    rule: "AGENCY 用户只能操作 t_agency_enterprise 中绑定的企业"
    enforcement: "EnterpriseController.switchEnterprise 校验"

acceptance_tests:
  - id: AT-001
    description: "SME 用户登录直达业务空间"
    method: test_sme_login_direct
    assertion: "userType==ENTERPRISE, enterpriseId!=null, enterpriseList==null"
    status: missing
  - id: AT-002
    description: "AGENCY 用户登录看到客户列表"
    method: test_agency_login_enterprise_list
    assertion: "userType==AGENCY, enterpriseList.length > 0"
    status: missing
  - id: AT-003
    description: "代理用户切换客户企业"
    method: test_switch_enterprise
    assertion: "RLS context updated, subsequent queries scoped to new enterprise"
    status: missing
  - id: AT-004
    description: "跨企业访问被拦截"
    method: test_cross_enterprise_blocked
    assertion: "returns error 20003"
    status: missing
  - id: AT-005
    description: "新建客户企业自动初始化种子数据"
    method: test_new_enterprise_seed_data
    assertion: "subject/voucherType/summaryLib/period cloned with new enterpriseId"
    status: missing
  - id: AT-006
    description: "ENTERPRISE 用户不能切换企业"
    method: test_enterprise_user_cannot_switch
    assertion: "returns error 20008"
    status: missing
  - id: AT-007
    description: "已暂停企业不能创建数据"
    method: test_suspended_enterprise_blocked
    assertion: "returns error 20005"
    status: missing
  - id: AT-008
    description: "RLS 兜底拦截"
    method: test_rls_fallback
    assertion: "raw SQL without enterprise_id still scoped"
    status: missing

out_of_scope:
  - "Schema 级隔离（当前采用行级隔离）"
  - "租户计费/计量"
  - "租户资源配额限制"
  - "移动端原生 App（H5 响应式在 V1.1）"

dependencies:
  - spec: S-01
    relation: "多租户架构基础，S-26 是 S-01 的实现落地"
  - spec: S-02
    relation: "JWT Token 扩展 claims"
  - spec: P54
    relation: "agency 包结构已预留"
```

