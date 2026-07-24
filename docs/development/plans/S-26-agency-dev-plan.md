# S-26 Agency 分支开发计划

> **版本**：2.0 | **日期**：2026-07-24 | **作者**：Hermes
> **状态**：✅ 已审核（2026-07-24 老丁确认通过）
> **关联 SPEC**：[S-26-agency-branch-development](../../specs/S-26-agency-branch-development.md)
> **关联设计**：[多租户架构设计](../../architecture/多租户架构设计.md)
> **开发规范**：SDD 四段模板 + BDD Given-When-Then + TDD Red→Green + 微循环 5-15 分钟
> **V2.0 变更**：新增 Sprint 5（代理内角色体系）+ Sprint 6（前端角色适配），共 21 个微循环

---

## 总览

6 个 Sprint，每个 Sprint 含若干微循环，每个微循环对应一个可验证契约。

```
Sprint 1: 多租户基础设施（DB + 拦截器 + RLS）          ✅ 已完成
  ↓ 阻塞后续所有 Sprint
Sprint 2: 用户认证与切换（JWT + 登录 + 切换接口）       ✅ 已完成
  ↓ 阻塞 Sprint 4
Sprint 3: 代账业务引擎（客户管理 + 批量操作 + CRM）     ✅ 已完成
  ↓ 可与 Sprint 4 并行
Sprint 4: 前端适配（代理工作台 + 客户切换 + 批量 UI）   ✅ 已完成
  ↓ V2.0 新增
Sprint 5: 代理内角色体系（建表 + Entity + Service + Controller + 权限分流） ✅ 已完成
  ↓ 阻塞 Sprint 6
Sprint 6: 前端角色适配（会计管理页 + 分配页 + 菜单权限） ✅ 已完成
```

---

## Sprint 1：多租户基础设施（P0 阻塞项）

### 微循环清单

#### MC1-01：Flyway V99 — 新建三张管理表

| 项 | 内容 |
|----|------|
| 目标 | 新建 `t_agency`、`t_enterprise`、`t_agency_enterprise` 三表 |
| 契约 | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('t_agency','t_enterprise','t_agency_enterprise') → 3` |
| 涉及文件 | `backend/src/main/resources/db/migration/V99__create_agency_tables.sql` |
| 验证 | Flyway migrate 成功 + 三表存在 |

SQL 要点：
- `t_agency`：id/agency_code/agency_name/contact_name/contact_phone/status/标准审计字段
- `t_enterprise`：id/enterprise_code/enterprise_name/tax_id/mode/agency_id/status/seed_data_done/标准审计字段
- `t_agency_enterprise`：id/agency_id/enterprise_id/status/created_at
- 主键 `GENERATED ALWAYS AS IDENTITY`，金额列 `NUMERIC(18,2)`，状态 `VARCHAR(20)`
- 外键：`t_enterprise.agency_id → t_agency.id`，`t_agency_enterprise` 双外键

#### MC1-02：Flyway V100 — t_user 扩展用户类型字段

| 项 | 内容 |
|----|------|
| 目标 | t_user 加 `user_type`/`agency_id`/`enterprise_id` 三列 |
| 契约 | `SELECT user_type FROM t_user WHERE id=1 → 'ENTERPRISE'`（默认值） |
| 涉及文件 | `V100__alter_user_add_agency_columns.sql` |
| 验证 | 现有用户全部默认为 ENTERPRISE 类型 |

SQL 要点：
- `ALTER TABLE t_user ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'ENTERPRISE'`
- `ALTER TABLE t_user ADD COLUMN agency_id BIGINT REFERENCES t_agency(id)`
- `ALTER TABLE t_user ADD COLUMN enterprise_id BIGINT REFERENCES t_enterprise(id)`
- 插入默认企业：`INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, seed_data_done) VALUES ('DEFAULT', '默认企业', 'SME', 'ACTIVE', true)`
- 更新现有用户：`UPDATE t_user SET enterprise_id = (SELECT id FROM t_enterprise WHERE enterprise_code = 'DEFAULT'), user_type = 'ENTERPRISE'`

#### MC1-03：Flyway V101~V103 — 业务表批量加 enterprise_id

| 项 | 内容 |
|----|------|
| 目标 | 69 张业务表加 `enterprise_id BIGINT NOT NULL DEFAULT 1` 列 + 索引 |
| 契约 | `SELECT COUNT(*) FROM information_schema.columns WHERE column_name = 'enterprise_id' AND table_name LIKE 't_%' → >= 69` |
| 涉及文件 | V101（科目/期间/凭证类 ~20张）、V102（应收应付/资金/发票类 ~25张）、V103（资产/预算/报表/AI类 ~24张） |
| 验证 | 所有业务表含 enterprise_id 列，现有数据全部默认为 1 |

分批策略：
```sql
-- 每批格式
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_t_voucher_enterprise ON t_voucher (enterprise_id);
-- ... 每张表同理
```

排除表（不加 enterprise_id）：`t_user`、`t_role`、`t_user_role`、`t_menu`、`t_agency`、`t_enterprise`、`t_agency_enterprise`、`t_sys_config`、`t_audit_log`

#### MC1-04：Flyway V104 — PostgreSQL RLS 策略

| 项 | 内容 |
|----|------|
| 目标 | 所有业务表启用 RLS + 创建 enterprise_policy |
| 契约 | `SELECT relrowsecurity FROM pg_class WHERE relname = 't_voucher' → true` |
| 涉及文件 | `V104__enable_rls_policies.sql` |
| 验证 | RLS 启用，未设置 context 时查询返回空 |

SQL 要点：
```sql
DO $$
DECLARE tbl TEXT;
BEGIN
  FOR tbl IN SELECT tablename FROM pg_tables WHERE schemaname='public'
    AND tablename LIKE 't_%'
    AND tablename NOT IN ('t_user','t_role','t_user_role','t_menu','t_agency','t_enterprise','t_agency_enterprise','t_sys_config','t_audit_log')
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format('CREATE POLICY enterprise_policy ON %I USING (enterprise_id = current_setting(''app.enterprise_id'', true)::bigint)', tbl);
  END LOOP;
END;
$$;
```

#### MC1-05：BaseEntity 抽取 + enterprise_id 字段统一

| 项 | 内容 |
|----|------|
| 目标 | 新建 `common/entity/BaseEntity.java`，69 个 Entity 继承它 |
| 契约 | `EnterpriseEntity extends BaseEntity` 编译通过 + `BaseEntity` 含 enterpriseId 字段 |
| 涉及文件 | `common/entity/BaseEntity.java` + 所有 Entity 类 |
| 验证 | `mvn compile` 通过 + check-entity-schema.mjs 通过 |

BaseEntity 设计：
```java
@Data
public abstract class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enterpriseId;      // 新增
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
}
```

迁移策略：逐个模块迁移，每批 10-15 个 Entity，每批编译+测试通过后 commit。

#### MC1-06：EnterpriseContextHolder 实现

| 项 | 内容 |
|----|------|
| 目标 | ThreadLocal 存储当前 enterpriseId，支持 get/set/clear |
| 契约 | `EnterpriseContextHolder.set(3L); EnterpriseContextHolder.get() → 3L` |
| 涉及文件 | `common/context/EnterpriseContextHolder.java` |
| 验证 | 单元测试：set → get → clear → get(null) |

```java
public class EnterpriseContextHolder {
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();
    public static void set(Long enterpriseId) { CONTEXT.set(enterpriseId); }
    public static Long get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

#### MC1-07：EnterpriseDataPermissionInterceptor 实现

| 项 | 内容 |
|----|------|
| 目标 | MyBatis InnerInterceptor，自动注入 enterprise_id 条件 |
| 契约 | SELECT 语句自动追加 `AND enterprise_id = ?`；INSERT 自动填充 enterprise_id |
| 涉及文件 | `agency/interceptor/EnterpriseDataPermissionInterceptor.java` |
| 验证 | 单元测试：Mock SQL → 验证注入条件 |

实现要点：
- 参考 `DataPermissionInterceptor` 的 jsqlparser 解析模式
- ThreadLocal 预占防护（防递归）
- `beforeQuery`：SELECT 追加 WHERE enterprise_id = ?
- INSERT 拦截：通过 `MetaObjectHandler` 自动填充 enterpriseId 字段
- 共享表白名单：t_user/t_role/t_menu/t_agency/t_enterprise 等不注入

#### MC1-08：MetaObjectHandler 扩展 — 自动填充 enterprise_id

| 项 | 内容 |
|----|------|
| 目标 | INSERT 时自动从 EnterpriseContextHolder 取 enterpriseId 填充 |
| 契约 | `insertFieldFill` 方法设置 entity.enterpriseId = 当前上下文值 |
| 涉及文件 | `config/MyMetaObjectHandler.java` |
| 验证 | 单元测试：Mock 上下文 → insert → 验证 enterpriseId 被填充 |

#### MC1-09：HikariCP 连接池 RLS context 管理

| 项 | 内容 |
|----|------|
| 目标 | 连接借出时清理 session 变量，归还时重置 |
| 契约 | 连接池借出连接时 `app.enterprise_id` 为空 |
| 涉及文件 | `config/HikariRlsConfig.java` |
| 验证 | 集成测试：切换企业 → 查询 → 归还连接 → 新请求不串号 |

```java
// HikariCP 连接借出时清理
connection.addStatementEventListener(new StatementEventListener() {
    @Override
    public void statementClosed(StatementEvent event) {
        // 清理 session 变量
    }
});
```
或使用 `ConnectionCustomizer`：
```java
jdbcTemplate.execute("SELECT set_config('app.enterprise_id', '', false)");
```

#### MC1-10：拦截器注册 + 全量回归测试

| 项 | 内容 |
|----|------|
| 目标 | MyBatisPlusConfig 中注册 EnterpriseDataPermissionInterceptor + 修复被破坏的测试 |
| 契约 | `mvn test` Failures=0 |
| 涉及文件 | `config/MyBatisPlusConfig.java` + 所有测试文件（Mock 数据加 enterpriseId） |
| 验证 | 全量测试通过 |

---

## Sprint 2：用户认证与企业切换

### 微循环清单

#### MC2-01：LoginUser 扩展

| 项 | 内容 |
|----|------|
| 目标 | LoginUser 加 enterpriseId/agencyId/userType 字段 |
| 契约 | `loginUser.getEnterpriseId() == 1L`（SME 用户） |
| 涉及文件 | `config/security/LoginUser.java` |
| 验证 | 编译通过 |

#### MC2-02：JwtProvider 扩展 claims

| 项 | 内容 |
|----|------|
| 目标 | JWT Token 加 enterpriseId/agencyId/userType/enterpriseList claims |
| 契约 | 解析 Token 得到 `userType=AGENCY` |
| 涉及文件 | `config/security/JwtProvider.java` |
| 验证 | 单元测试：生成 Token → 解析 → 验证 claims |

#### MC2-03：SecurityUtils 扩展

| 项 | 内容 |
|----|------|
| 目标 | 加 getCurrentEnterpriseId / getCurrentUserType / getCurrentAgencyId |
| 契约 | `SecurityUtils.getCurrentEnterpriseId() == 1L`（SME 用户） |
| 涉及文件 | `base/system/util/SecurityUtils.java` |
| 验证 | 单元测试 |

#### MC2-04：JwtAuthenticationFilter 扩展

| 项 | 内容 |
|----|------|
| 目标 | 从 Token 解析 claims 填充 LoginUser + 设置 EnterpriseContextHolder |
| 契约 | 请求到达 Controller 时 EnterpriseContextHolder.get() == Token 中的 enterpriseId |
| 涉及文件 | `config/security/JwtAuthenticationFilter.java` |
| 验证 | 集成测试：带 Token 请求 → 验证上下文已设置 |

#### MC2-05：AuthController 登录流程改造

| 项 | 内容 |
|----|------|
| 目标 | 登录时设置 RLS context + 返回 userType/enterpriseList |
| 契约 | AGENCY 用户登录返回 enterpriseList.length > 0 |
| 涉及文件 | `base/system/controller/AuthController.java` |
| 验证 | BDD 场景 1 + 场景 2 |

#### MC2-06：EnterpriseController — 切换接口

| 项 | 内容 |
|----|------|
| 目标 | POST /api/v1/enterprise/switch 接口 |
| 契约 | 切换后 RLS context 更新 + 后续查询受新 enterprise_id 限制 |
| 涉及文件 | `agency/tenant/controller/EnterpriseController.java` |
| 验证 | BDD 场景 3 |

#### MC2-07：EnterpriseStateMachineService 实现

| 项 | 内容 |
|----|------|
| 目标 | 企业状态机：PENDING→ACTIVE→SUSPENDED→TERMINATED |
| 契约 | PENDING→ACTIVE 触发种子数据克隆 |
| 涉及文件 | `agency/tenant/service/EnterpriseStateMachineService.java` + Impl + Test |
| 验证 | BDD 场景 5 + 负向断言 |

#### MC2-08：种子数据克隆逻辑

| 项 | 内容 |
|----|------|
| 目标 | activateEnterprise 时克隆科目/凭证类型/摘要库/期间模板 |
| 契约 | 克隆后 t_subject WHERE enterprise_id=新ID 的数量 = 模板数量 |
| 涉及文件 | `agency/tenant/service/SeedDataService.java` |
| 验证 | BDD 场景 5 + 数据完整性校验 |

#### MC2-09：移除硬编码 tenantId

| 项 | 内容 |
|----|------|
| 目标 | 清除 ClassificationRuleServiceImpl + AutoGenerationService 中的 `1L` 硬编码 |
| 契约 | `grep -r "tenantId.*1L" backend/src/main → 0 命中` |
| 涉及文件 | `sme/cash/service/impl/ClassificationRuleServiceImpl.java`、`sme/arap/service/impl/AutoGenerationService.java` |
| 验证 | grep 无命中 + 相关测试通过 |

#### MC2-10：Sprint 2 全量回归

| 项 | 内容 |
|----|------|
| 目标 | mvn test Failures=0 + 契约验证 |
| 契约 | S-26 YAML acceptance_tests AT-001~AT-006 全部覆盖 |
| 验证 | 全量测试 + 契约脚本 |

---

## Sprint 3：代账业务引擎

### 微循环清单

#### MC3-01：AgencyController — 代理公司 CRUD

| 项 | 内容 |
|----|------|
| 目标 | 代理公司增删改查 |
| 契约 | POST /api/v1/agency/agencies 创建代理公司 |
| 涉及文件 | `agency/tenant/controller/AgencyController.java` + Service + Mapper |
| 验证 | Controller 测试 |

#### MC3-02：EnterpriseController — 客户企业 CRUD

| 项 | 内容 |
|----|------|
| 目标 | 客户企业增删改查 + 绑定关系管理 |
| 契约 | POST /api/v1/agency/enterprises 创建企业 + 自动绑定到当前代理 |
| 涉及文件 | `agency/tenant/controller/EnterpriseController.java` + Service |
| 验证 | BDD 场景 5 |

#### MC3-03：批量导入服务

| 项 | 内容 |
|----|------|
| 目标 | 多租户批量导入发票（复用 SME 导入逻辑，加 enterprise_id 隔离） |
| 契约 | 批量导入的发票 enterprise_id = 当前切换的企业 ID |
| 涉及文件 | `agency/batch/service/BatchImportService.java` |
| 验证 | 导入测试 + 数据隔离校验 |

#### MC3-04：批量审核服务

| 项 | 内容 |
|----|------|
| 目标 | 批量审核凭证/发票 |
| 契约 | 批量审核只影响当前 enterprise_id 的数据 |
| 涉及文件 | `agency/batch/service/BatchAuditService.java` |
| 验证 | 批量审核测试 |

#### MC3-05：批量结账服务

| 项 | 内容 |
|----|------|
| 目标 | 批量期末结账 |
| 契约 | 批量结账遍历多个企业，每个独立结账 |
| 涉及文件 | `agency/batch/service/BatchCloseService.java` |
| 验证 | 批量结账测试 |

#### MC3-06：客户 CRM — 合同管理

| 项 | 内容 |
|----|------|
| 目标 | 客户合同 CRUD + 续费提醒 |
| 契约 | 合同到期前 30 天提醒 |
| 涉及文件 | `agency/client/service/ContractService.java` |
| 验证 | 合同 CRUD 测试 |

#### MC3-07：Sprint 3 全量回归

---

## Sprint 4：前端适配

### 微循环清单

#### MC4-01：auth.store 扩展

| 项 | 内容 |
|----|------|
| 目标 | 加 currentEnterpriseId/userType/enterpriseList 状态 + switchEnterprise 方法 |
| 契约 | AGENCY 登录后 store.enterpriseList.length > 0 |
| 涉及文件 | `frontend/src/stores/auth.store.ts` |
| 验证 | Vitest 单元测试 |

#### MC4-02：request.ts 拦截器

| 项 | 内容 |
|----|------|
| 目标 | 请求头自动携带 X-Enterprise-Id |
| 契约 | 所有 API 请求 header 含 X-Enterprise-Id |
| 涉及文件 | `frontend/src/api/request.ts` |
| 验证 | API 单元测试 |

#### MC4-03：路由守卫 — 按 userType 分发

| 项 | 内容 |
|----|------|
| 目标 | SUPER_ADMIN→/admin/dashboard, AGENCY→/agency/enterprise-list, ENTERPRISE→/dashboard |
| 契约 | AGENCY 用户登录后跳转 /agency/enterprise-list |
| 涉及文件 | `frontend/src/router/index.ts` |
| 验证 | 路由测试 |

#### MC4-04：代理工作台页面

| 项 | 内容 |
|----|------|
| 目标 | 客户企业列表 + 切换组件 + 批量操作入口 |
| 契约 | 页面展示客户列表，点击进入客户业务空间 |
| 涉及文件 | `frontend/src/views/agency/EnterpriseList.vue`、`frontend/src/router/routes/agency.ts` |
| 验证 | 组件测试 + E2E |

#### MC4-05：客户切换组件

| 项 | 内容 |
|----|------|
| 目标 | 顶部导航栏客户切换下拉 |
| 契约 | 切换后所有页面数据更新为新企业 |
| 涉及文件 | `frontend/src/layouts/components/EnterpriseSwitcher.vue` |
| 验证 | 组件测试 |

#### MC4-06：批量操作页面

| 项 | 内容 |
|----|------|
| 目标 | 批量导入/审核/结账 UI |
| 契约 | 批量操作页面可执行批量任务 |
| 涉及文件 | `frontend/src/views/agency/BatchOperation.vue` |
| 验证 | 组件测试 |

#### MC4-07：Sprint 4 全量回归

---

## Sprint 5：代理内角色体系（V2.0 P0 阻塞项）

### 微循环清单

#### MC5-01：Flyway V111 — 创建 t_agency_user + t_agency_user_enterprise 表

| 项 | 内容 |
|----|------|
| 目标 | 新建代理用户角色表和客户分配表 |
| 契约 | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('t_agency_user','t_agency_user_enterprise') → 2` |
| 涉及文件 | `backend/src/main/resources/db/migration/V111__create_agency_user_tables.sql` |
| 验证 | Flyway migrate 成功 + 两表存在 |

#### MC5-02：Flyway V112 — t_user 加 agency_role 列

| 项 | 内容 |
|----|------|
| 目标 | t_user 加 agency_role VARCHAR(20) 列（冗余字段） |
| 契约 | `SELECT agency_role FROM t_user WHERE id=1 → NULL`（非 AGENCY 用户为 null） |
| 涉及文件 | `V112__alter_user_add_agency_role.sql` |
| 验证 | 列存在，现有用户 agency_role 为 null |

#### MC5-03：AgencyUserEntity + AgencyUserEnterpriseEntity

| 项 | 内容 |
|----|------|
| 目标 | 创建 Entity 类，映射 t_agency_user 和 t_agency_user_enterprise |
| 契约 | `mvn compile` 通过 + check-entity-schema.mjs 通过 |
| 涉及文件 | `agency/user/entity/AgencyUserEntity.java`、`AgencyUserEnterpriseEntity.java` |
| 验证 | Entity-DB 一致性检查通过 |

#### MC5-04：AgencyUserMapper + AgencyUserEnterpriseMapper

| 项 | 内容 |
|----|------|
| 目标 | MyBatis-Plus Mapper，含按 agency_id 查询用户列表、按 agency_user_id 查询分配企业 |
| 契约 | `agencyUserMapper.selectList()` 返回代理公司下所有用户 |
| 涉及文件 | `agency/user/mapper/AgencyUserMapper.java`、`AgencyUserEnterpriseMapper.java` |
| 验证 | Mapper 集成测试 |

#### MC5-05：AgencyUserService + Impl（CRUD + 状态机）

| 项 | 内容 |
|----|------|
| 目标 | 代理用户 CRUD + 状态机（ACTIVE→SUSPENDED→TERMINATED） |
| 契约 | create 时同时插入 t_user 和 t_agency_user；suspend 后用户无法登录 |
| 涉及文件 | `agency/user/service/AgencyUserService.java` + Impl + StateMachine |
| 验证 | BDD 场景 9、16 |

#### MC5-06：AgencyUserController（CRUD + 启停）

| 项 | 内容 |
|----|------|
| 目标 | REST 端点：POST/GET/PUT/DELETE /api/v1/agency/users + POST suspend/reactivate |
| 契约 | 仅 AGENCY_ADMIN 可调用；ACCOUNTANT 调用返回 20011 |
| 涉及文件 | `agency/user/controller/AgencyUserController.java` |
| 验证 | BDD 场景 9、14 |

#### MC5-07：客户分配 Service（assign/unassign/list）

| 项 | 内容 |
|----|------|
| 目标 | 分配/取消分配/查询分配列表 |
| 契约 | assign 时校验目标用户 role 为 ACCOUNTANT/ASSISTANT 且同代理公司 |
| 涉及文件 | `agency/user/service/AgencyUserEnterpriseService.java` + Impl |
| 验证 | BDD 场景 9、15、18 |

#### MC5-08：客户分配 Controller

| 项 | 内容 |
|----|------|
| 目标 | REST 端点：POST/DELETE /api/v1/agency/assignments |
| 契约 | 仅 AGENCY_ADMIN 可调用 |
| 涉及文件 | `agency/user/controller/AssignmentController.java` |
| 验证 | BDD 场景 9、15 |

#### MC5-09：LoginUser + JWT + SecurityUtils 扩展 agencyRole

| 项 | 内容 |
|----|------|
| 目标 | LoginUser 加 agencyRole 字段；JWT claims 加 agencyRole；SecurityUtils 加 getCurrentAgencyRole() |
| 契约 | AGENCY_ADMIN 登录后 JWT 含 agencyRole=AGENCY_ADMIN |
| 涉及文件 | `config/security/LoginUser.java`、`JwtProvider.java`、`SecurityUtils.java` |
| 验证 | 单元测试 |

#### MC5-10：AuthController 登录时查询 agencyRole

| 项 | 内容 |
|----|------|
| 目标 | 登录时从 t_agency_user 查询 agencyRole，写入 LoginUser 和 JWT |
| 契约 | AGENCY 用户登录返回 agencyRole 字段 |
| 涉及文件 | `base/system/controller/AuthController.java` |
| 验证 | BDD 场景 9 |

#### MC5-11：EnterpriseController.switchEnterprise 按 agencyRole 分流

| 项 | 内容 |
|----|------|
| 目标 | AGENCY_ADMIN/REVIEWER 校验 t_agency_enterprise；ACCOUNTANT/ASSISTANT 校验 t_agency_user_enterprise |
| 契约 | ACCOUNTANT 切换到未分配企业返回 20010 |
| 涉及文件 | `agency/tenant/controller/EnterpriseController.java` |
| 验证 | BDD 场景 11、12 |

#### MC5-12：测试：AgencyUser CRUD + 状态机 + 分配 + 权限分流

| 项 | 内容 |
|----|------|
| 目标 | 覆盖 BDD 场景 9-18 的所有后端测试 |
| 契约 | 所有新增 @Test PASS |
| 涉及文件 | `AgencyUserServiceTest.java`、`AgencyUserEnterpriseServiceTest.java`、`AgencyUserControllerTest.java`、`AssignmentControllerTest.java` |
| 验证 | mvn test 新增测试全部 PASS |

#### MC5-13：种子数据：admin 设为 AGENCY_ADMIN + 创建测试会计

| 项 | 内容 |
|----|------|
| 目标 | Flyway V113：admin 插入 t_agency_user (AGENCY_ADMIN) + 创建测试 ACCOUNTANT 用户 |
| 契约 | admin 登录后 agencyRole=AGENCY_ADMIN |
| 涉及文件 | `V113__seed_agency_users.sql` |
| 验证 | 登录测试 |

#### MC5-14：Sprint 5 全量回归

| 项 | 内容 |
|----|------|
| 目标 | mvn test Failures=0 + BDD AT-009~AT-018 全部覆盖 |
| 验证 | ✅ 已验证（2026-07-24）：2176 测试 0 Failure，Agency 模块 19 测试全部通过 |
| 修复 | EnterpriseControllerTest 从 @WebMvcTest 改为 @SpringBootTest + JWT mock，3 个测试从 isNotFound() 改为 isOk() + jsonPath("$.code") |
| 修复 | EnterpriseEntity 测试中补充 setDeleted(0) 避免 NPE |
| 修复 | LoginUser 构造函数使用完整 7 参数版本（含 agencyId） |

---

## Sprint 6：前端角色适配

### 微循环清单

#### MC6-01：auth.store 加 agencyRole + 按角色过滤 enterpriseList

| 项 | 内容 |
|----|------|
| 目标 | store 加 agencyRole 状态 + computed isAgencyAdmin/isAccountant/isReviewer/isAssistant |
| 契约 | ACCOUNTANT 登录后 enterpriseList 仅包含分配的企业 |
| 涉及文件 | `frontend/src/stores/auth.store.ts` |
| 验证 | Vitest 单元测试 |

#### MC6-02：会计管理页面（AGENCY_ADMIN 可见）

| 项 | 内容 |
|----|------|
| 目标 | 代理用户列表 + 创建/启停操作 |
| 契约 | AGENCY_ADMIN 可见，ACCOUNTANT 不可见 |
| 涉及文件 | `frontend/src/views/agency/AccountantList.vue` |
| 验证 | 组件测试 |

#### MC6-03：客户分配页面（AGENCY_ADMIN 可见）

| 项 | 内容 |
|----|------|
| 目标 | 为会计分配/取消分配客户企业 |
| 契约 | 分配后会计登录可见该企业 |
| 涉及文件 | `frontend/src/views/agency/AssignmentManage.vue` |
| 验证 | 组件测试 |

#### MC6-04：EnterpriseSwitcher 按 agencyRole 过滤可选企业

| 项 | 内容 |
|----|------|
| 目标 | ACCOUNTANT 切换器仅显示分配的企业；AGENCY_ADMIN 显示全部 |
| 契约 | ACCOUNTANT 切换器选项 = 分配的企业列表 |
| 涉及文件 | `frontend/src/layouts/components/EnterpriseSwitcher.vue` |
| 验证 | 组件测试 |

#### MC6-05：路由守卫按 agencyRole 控制菜单可见性

| 项 | 内容 |
|----|------|
| 目标 | ACCOUNTANT 看不到会计管理/客户分配菜单；ASSISTANT 看不到审核按钮 |
| 契约 | 路由守卫按 isAgencyAdmin/isAccountant 控制 |
| 涉及文件 | `frontend/src/router/index.ts` |
| 验证 | 路由测试 |

#### MC6-06：侧边栏按 agencyRole 控制菜单

| 项 | 内容 |
|----|------|
| 目标 | AppSidebar 菜单按 agencyRole 显示/隐藏 |
| 契约 | ACCOUNTANT 侧边栏无「会计管理」「客户分配」菜单 |
| 涉及文件 | `frontend/src/layouts/AppSidebar.vue` |
| 验证 | 组件测试 |

#### MC6-07：Sprint 6 全量回归

| 项 | 内容 |
|----|------|
| 目标 | npm test 全部通过 + 前端构建成功 |
| 验证 | npm test + npm run build |

---

## 测试计划（V2.0 更新）

### 测试金字塔

```
E2E (Playwright)     ← 7 个：+2 个（会计分配流程/角色权限验证）
组件 (Vitest)         ← 12 个：+4 个（会计管理/分配页/角色切换器/角色路由）
集成 (SpringBootTest) ← 25 个：+10 个（AgencyUser CRUD/状态机/分配/权限分流）
单元 (JUnit 5)        ← 70+ 个：+20 个（新 Entity/Mapper/Service/Controller）
契约 (YAML)           ← 18 个：+10 个（AT-009~AT-018）
```

### Sprint 5 测试（新增 20 个）

| 测试类 | 测试方法 | BDD 场景 |
|--------|---------|---------|
| AgencyUserServiceTest | testCreateAccountant | 场景 9 |
| AgencyUserServiceTest | testSuspendUser | 场景 16 |
| AgencyUserServiceTest | testReactivateUser | 场景 16 |
| AgencyUserServiceTest | testTerminateUser | 场景 16 |
| AgencyUserServiceTest | testNonAdminCannotManage | 场景 14 |
| AgencyUserEnterpriseServiceTest | testAssignEnterprise | 场景 9 |
| AgencyUserEnterpriseServiceTest | testUnassignEnterprise | 场景 15 |
| AgencyUserEnterpriseServiceTest | testCrossAgencyBlocked | 场景 18 |
| AgencyUserEnterpriseServiceTest | testAccountantOnlySeesOwn | 场景 10 |
| AgencyUserControllerTest | testCreateUser | 场景 9 |
| AgencyUserControllerTest | testSuspendUser | 场景 16 |
| AssignmentControllerTest | testAssignEnterprise | 场景 9 |
| AssignmentControllerTest | testUnassignEnterprise | 场景 15 |
| AuthControllerTest | testAgencyAdminLogin | 场景 12 |
| AuthControllerTest | testAccountantLogin | 场景 10 |
| EnterpriseControllerTest | testAccountantSwitchOwn | 场景 10 |
| EnterpriseControllerTest | testAccountantSwitchBlocked | 场景 11 |
| EnterpriseControllerTest | testReviewerReadOnly | 场景 17 |
| EnterpriseControllerTest | testAssistantCannotAudit | 场景 13 |
| AgencyUserStateMachineTest | testFullLifecycle | 场景 16 |

### Sprint 6 测试（新增 6 个）

| 测试文件 | 测试方法 |
|---------|---------|
| auth.store.test.ts | testAgencyRoleState |
| auth.store.test.ts | testAccountantEnterpriseList |
| AccountantList.test.ts | testRenderList |
| AssignmentManage.test.ts | testAssignUnassign |
| EnterpriseSwitcher.test.ts | testRoleFilter |
| router.test.ts | testAgencyRoleGuard |

### 测试通过标准（V2.0 更新）

- **后端**：`mvn test` Failures=0, Errors=0
- **前端**：`npm test` 所有测试通过
- **E2E**：`npx playwright test` 全部通过
- **契约**：`validate_spec_contract.py` exit code=0
- **Entity-DB**：`check-entity-schema.mjs` 无不一致
- **BDD 覆盖**：18 个场景全部有对应 @Test 且 PASS
- **负向断言**：17 条负向断言全部 PASS
- **并发测试**：6 个并发测试全部 PASS
- **测试总数**：V1.0 57 个 + V2.0 26 个 = 83 个 @Test
