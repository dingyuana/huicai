# S-26 Agency 分支开发计划

> **版本**：1.0 | **日期**：2026-07-23 | **作者**：Hermes
> **状态**：⚠️ 待老丁审核
> **关联 SPEC**：[S-26-agency-branch-development](../../specs/S-26-agency-branch-development.md)
> **关联设计**：[多租户架构设计](../../architecture/多租户架构设计.md)
> **开发规范**：SDD 四段模板 + BDD Given-When-Then + TDD Red→Green + 微循环 5-15 分钟

---

## 总览

4 个 Sprint，每个 Sprint 含若干微循环，每个微循环对应一个可验证契约。

```
Sprint 1: 多租户基础设施（DB + 拦截器 + RLS）
  ↓ 阻塞后续所有 Sprint
Sprint 2: 用户认证与切换（JWT + 登录 + 切换接口）
  ↓ 阻塞 Sprint 4
Sprint 3: 代账业务引擎（客户管理 + 批量操作 + CRM）
  ↓ 可与 Sprint 4 并行
Sprint 4: 前端适配（代理工作台 + 客户切换 + 批量 UI）
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

## 测试计划

### 测试金字塔

```
E2E (Playwright)     ← 5 个：登录分发/切换客户/跨企业拦截/种子数据/批量操作
组件 (Vitest)         ← 8 个：auth.store/request/路由守卫/工作台/切换器/批量页
集成 (SpringBootTest) ← 15 个：登录/切换/拦截器/RLS/种子数据/批量
单元 (JUnit 5)        ← 50+ 个：状态机/拦截器/ContextHolder/Service/Mapper
契约 (YAML)           ← 8 个：AT-001~AT-008 逐条验证
```

### 测试清单（按 Sprint）

#### Sprint 1 测试（18 个）

| 测试类 | 测试方法 | 契约 | 负向断言 |
|--------|---------|------|---------|
| EnterpriseContextHolderTest | testSetGetClear | set→get→clear→null | — |
| EnterpriseDataPermissionInterceptorTest | testSelectInjectEnterpriseId | SELECT 追加条件 | 共享表不注入 |
| EnterpriseDataPermissionInterceptorTest | testInsertFillEnterpriseId | INSERT 自动填充 | — |
| EnterpriseDataPermissionInterceptorTest | testSuperAdminBypass | 超管不拦截 | — |
| EnterpriseDataPermissionInterceptorTest | testRecursiveGuard | 防递归预占 | — |
| HikariRlsConfigTest | testConnectionCleanOnBorrow | 借出时清理 | — |
| HikariRlsConfigTest | testRlsContextNotLeak | 归还后不串号 | — |
| BaseEntityTest | testEnterpriseIdField | 字段存在 | — |
| SubjectMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| VoucherMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| BusinessDocMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| OutputInvoiceMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| ... 其余 Mapper 同理 | ... | ... | ... |

#### Sprint 2 测试（15 个）

| 测试类 | 测试方法 | BDD 场景 |
|--------|---------|---------|
| AuthControllerTest | testSmeLoginDirect | 场景 1 |
| AuthControllerTest | testAgencyLoginEnterpriseList | 场景 2 |
| EnterpriseControllerTest | testSwitchEnterprise | 场景 3 |
| EnterpriseControllerTest | testCrossEnterpriseBlocked | 场景 4 |
| EnterpriseControllerTest | testEnterpriseUserCannotSwitch | 场景 6 |
| EnterpriseStateMachineServiceImplTest | testActivateSeedsData | 场景 5 |
| EnterpriseStateMachineServiceImplTest | testActivateDoesNotPolluteOthers | 负向 |
| EnterpriseStateMachineServiceImplTest | testSuspendBlocksCreate | 场景 7 |
| EnterpriseStateMachineServiceImplTest | testTerminateIsTerminal | 负向 |
| SeedDataServiceImplTest | testCloneSubjects | 种子克隆 |
| SeedDataServiceImplTest | testCloneVoucherTypes | 种子克隆 |
| SeedDataServiceImplTest | testCloneSummaryLib | 种子克隆 |
| SeedDataServiceImplTest | testClonePeriods | 种子克隆 |
| JwtProviderTest | testExtendedClaims | JWT claims |
| SecurityUtilsTest | testGetCurrentEnterpriseId | 上下文 |

#### Sprint 3 测试（10 个）

| 测试类 | 测试方法 |
|--------|---------|
| AgencyControllerTest | testCrud |
| EnterpriseControllerTest | testCreateEnterprise |
| BatchImportServiceTest | testBatchImportIsolated |
| BatchAuditServiceTest | testBatchAuditIsolated |
| BatchCloseServiceTest | testBatchCloseMultiEnterprise |
| ContractServiceTest | testContractCrud |
| ContractServiceTest | testRenewalReminder |
| EnterpriseStateMachineServiceImplTest | testFullLifecycle |
| SeedDataServiceImplTest | testIdempotentSeed |
| AgencyE2ETest | testFullAgencyFlow |

#### Sprint 4 测试（8 个）

| 测试文件 | 测试方法 |
|---------|---------|
| auth.store.test.ts | testAgencyLoginStore |
| auth.store.test.ts | testSwitchEnterprise |
| request.test.ts | testEnterpriseHeader |
| router.test.ts | testUserTypeDispatch |
| EnterpriseList.test.ts | testRenderList |
| EnterpriseSwitcher.test.ts | testSwitch |
| BatchOperation.test.ts | testBatchUI |
| agency-flow.spec.ts | testFullAgencyFlow |

### 测试规范

- **TDD-First**：每个微循环先写测试（Red），再写实现（Green）
- **负向断言强制**：状态机测试必须包含"不该做的没做"
- **Mock 策略**：单元测试 Mock Mapper；集成测试用真实 DB（Testcontainers）
- **契约验证**：每个 Sprint 结束时运行 `validate_spec_contract.py --path S-26`
- **全量回归**：每个 Sprint 结束时 `mvn test` + `npm test` 必须 Failures=0
- **Entity-DB 一致性**：每次改 Entity 后跑 `node backend/scripts/check-entity-schema.mjs`

---

## Git 提交规范

- 每个微循环独立 commit
- 格式：`S26-MC{sprint}-{num}: {描述}`
- 示例：`S26-MC1-01: V99 create agency/enterprise/agency_enterprise tables`
- `git add` 必须指定具体文件路径
- commit 前必须 `mvn test` 通过

---

## 风险登记

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| 69 表加列导致现有测试全部失败 | 高 | 高 | MC1-10 专门修复测试，Mock 数据加 enterpriseId |
| RLS 连接池串号 | 中 | 高 | MC1-09 HikariCP 连接清理 |
| BaseEntity 迁移遗漏 | 中 | 中 | 分批迁移 + check-entity-schema.mjs |
| 拦截器递归 StackOverflow | 中 | 高 | ThreadLocal 预占，参考已有方案 |
| 性能下降（enterprise_id 索引） | 低 | 低 | 加索引，RLS 无额外开销 |

---

> **审核门**：本计划需老丁审核通过后方可进入执行阶段。
