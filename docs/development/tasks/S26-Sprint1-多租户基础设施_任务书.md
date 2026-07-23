# S26 Sprint 1 任务书 — 多租户基础设施

> 日期：2026-07-23 | 任务 ID：S26-S1 | 关联 SPEC：S-26 | 关联计划：S-26-agency-dev-plan.md

## 目标
完成多租户数据隔离的 DB 层和基础设施层：建表、业务表加列、RLS、BaseEntity、拦截器。

## 前置条件
- V1 baseline + V92~V98 已合入
- com.huicai.agency 包为空目录（P54 已预留）
- DataPermissionInterceptor 已存在（dept/created_by 级别）
- SecurityUtils 仅有 getCurrentUserId/getCurrentUsername

## 任务清单（10 个微循环）

### MC1-01: Flyway V99 — 新建三张管理表
- 目标：新建 t_agency、t_enterprise、t_agency_enterprise 三表
- 涉及文件：backend/src/main/resources/db/migration/V99__create_agency_tables.sql
- 表结构（从多租户架构设计.md §2.1 提取）：
  - t_agency: id, agency_code(VARCHAR(32) UNIQUE), agency_name(VARCHAR(200)), contact_name, contact_phone, status(VARCHAR(20)), 标准审计字段(created_by/created_at/updated_by/updated_at/deleted/version)
  - t_enterprise: id, enterprise_code(VARCHAR(32) UNIQUE), enterprise_name(VARCHAR(200)), tax_id(VARCHAR(32)), mode(VARCHAR(20)), agency_id(BIGINT REFERENCES t_agency(id)), status(VARCHAR(20)), seed_data_done(BOOLEAN DEFAULT false), 标准审计字段
  - t_agency_enterprise: id, agency_id(BIGINT REFERENCES t_agency(id)), enterprise_id(BIGINT REFERENCES t_enterprise(id)), status(VARCHAR(20)), created_at
  - 所有主键 GENERATED ALWAYS AS IDENTITY
  - t_agency_enterprise 加 UNIQUE(agency_id, enterprise_id)
- 契约：SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('t_agency','t_enterprise','t_agency_enterprise') = 3
- TDD: 先写 migration，执行 flyway:migrate 验证，写 V99MigrationTest 确认三表存在
- 验证：mvn flyway:info 显示 V99 Success

### MC1-02: Flyway V100 — t_user 扩展用户类型字段
- 目标：t_user 加 user_type/agency_id/enterprise_id 三列
- 涉及文件：V100__alter_user_add_agency_columns.sql
- SQL：ALTER TABLE t_user ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'ENTERPRISE'; ADD COLUMN agency_id BIGINT; ADD COLUMN enterprise_id BIGINT;
- 插入默认企业：INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, seed_data_done, deleted, version) VALUES ('DEFAULT', '默认企业', 'SME', 'ACTIVE', true, 0, 0);
- 更新现有用户：UPDATE t_user SET enterprise_id = (SELECT id FROM t_enterprise WHERE enterprise_code='DEFAULT'), user_type = 'ENTERPRISE' WHERE enterprise_id IS NULL;
- 契约：SELECT user_type FROM t_user WHERE id=1 = 'ENTERPRISE'
- 验证：现有用户全部默认为 ENTERPRISE 类型

### MC1-03: Flyway V101~V103 — 业务表批量加 enterprise_id
- 目标：69 张业务表加 enterprise_id BIGINT NOT NULL DEFAULT 1 列 + 索引
- 分三批（每批约23张表）：
  - V101：科目/期间/凭证类（t_subject, t_subject_balance, t_period, t_voucher, t_voucher_entry, t_voucher_template, t_voucher_template_line, t_voucher_type, t_summary_lib 等）
  - V102：应收应付/资金/发票类（t_business_doc, t_business_doc_entry, t_arap_settlement, t_bank_statement, t_bank_transaction, t_bank_account, t_bank_journal, t_cash_journal, t_output_invoice, t_input_invoice 等）
  - V103：资产/预算/报表/AI类（t_asset_card, t_asset_category, t_asset_depreciation, t_asset_disposal, t_asset_inventory, t_budget, t_budget_entry, t_budget_adjustment, t_report_template, t_ai_task, t_ai_feedback_log 等）
- 排除表：t_user, t_role, t_user_role, t_menu, t_agency, t_enterprise, t_agency_enterprise, t_sys_config, t_audit_log
- 每张表格式：ALTER TABLE t_xxx ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1; CREATE INDEX IF NOT EXISTS idx_t_xxx_enterprise ON t_xxx (enterprise_id);
- 契约：SELECT COUNT(*) FROM information_schema.columns WHERE column_name='enterprise_id' AND table_name LIKE 't_%' >= 69
- 验证：所有业务表含 enterprise_id 列

### MC1-04: Flyway V104 — PostgreSQL RLS 策略
- 目标：所有业务表启用 RLS + 创建 enterprise_policy
- 涉及文件：V104__enable_rls_policies.sql
- SQL（DO 块遍历所有 t_ 开头表，排除共享表，对每张表 ENABLE ROW LEVEL SECURITY + FORCE + CREATE POLICY enterprise_policy USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint))
- 契约：SELECT relrowsecurity FROM pg_class WHERE relname='t_voucher' = true
- 验证：RLS 启用，未设置 context 时查询返回空（超级管理员除外，需要 BYPASSRLS 角色）

### MC1-05: BaseEntity 抽取 + enterprise_id 字段统一
- 目标：新建 common/entity/BaseEntity.java，所有 Entity 继承
- BaseEntity 含：id(Long, @TableId AUTO), enterpriseId(Long), createdBy(Long), createdAt(LocalDateTime, @TableField fill=INSERT), updatedBy(Long), updatedAt(LocalDateTime, @TableField fill=INSERT_UPDATE), deleted(Integer, @TableLogic), version(Integer, @Version)
- 迁移策略：逐个模块迁移（base.system → base.voucher → base.report → sme.tax → sme.arap → sme.cash → sme.asset → sme.budget → base.ai），每批 10-15 个 Entity
- 契约：mvn compile 通过 + check-entity-schema.mjs 通过
- 验证：全量编译 + Entity-DB 一致性检查

### MC1-06: EnterpriseContextHolder 实现
- 目标：ThreadLocal 存储当前 enterpriseId
- 涉及文件：common/context/EnterpriseContextHolder.java
- 方法：set(Long), get() -> Long, clear()
- 契约：EnterpriseContextHolder.set(3L); get() = 3L; clear(); get() = null
- TDD: 先写 EnterpriseContextHolderTest（testSetGet, testClear, testNullWhenNotSet）
- 验证：单元测试通过

### MC1-07: EnterpriseDataPermissionInterceptor 实现
- 目标：MyBatis InnerInterceptor，SELECT 自动注入 enterprise_id 条件
- 涉及文件：agency/interceptor/EnterpriseDataPermissionInterceptor.java
- 参考现有 DataPermissionInterceptor 的 jsqlparser 解析模式
- beforeQuery: 解析 SQL → 对业务表追加 AND enterprise_id = ? → 超级管理员跳过 → 共享表白名单
- ThreadLocal 预占防护防递归（参考 DataPermissionInterceptor 已有方案）
- 共享表白名单：t_user, t_role, t_user_role, t_menu, t_agency, t_enterprise, t_agency_enterprise, t_sys_config, t_audit_log
- 契约：SELECT 语句自动追加 AND enterprise_id = ?
- TDD: 先写 EnterpriseDataPermissionInterceptorTest（testSelectInjectEnterpriseId, testSharedTableNotInjected, testSuperAdminBypass, testRecursiveGuard）
- 验证：单元测试通过

### MC1-08: MetaObjectHandler 扩展 — 自动填充 enterprise_id
- 目标：INSERT 时自动从 EnterpriseContextHolder 取 enterpriseId 填充
- 涉及文件：config/MyMetaObjectHandler.java（已存在，需扩展）
- 在 insertFill 方法中加：if (entity instanceof BaseEntity) { ((BaseEntity) entity).setEnterpriseId(EnterpriseContextHolder.get()); }
- 如果 EnterpriseContextHolder.get() 为 null，不填充（由 DB DEFAULT 1 兜底）
- 契约：insert 时 entity.enterpriseId = 当前上下文值
- TDD: 先写 MyMetaObjectHandlerTest
- 验证：单元测试通过

### MC1-09: HikariCP 连接池 RLS context 管理
- 目标：连接借出时清理 session 变量，归还时重置
- 涉及文件：config/HikariRlsConfig.java
- 方案：实现 HikariDataSource 的连接自定义器，在连接借出时执行 SELECT set_config('app.enterprise_id', '', false) 清理
- 或使用 Spring Boot 的 ConnectionCustomizer
- 契约：连接池借出连接时 app.enterprise_id 为空
- TDD: 先写 HikariRlsConfigTest（testConnectionCleanOnBorrow, testRlsContextNotLeak）
- 验证：集成测试

### MC1-10: 拦截器注册 + 全量回归测试
- 目标：MyBatisPlusConfig 中注册 EnterpriseDataPermissionInterceptor + 修复被破坏的测试
- 涉及文件：config/MyBatisPlusConfig.java + 所有测试文件
- 拦截器顺序：Pagination → OptimisticLocker → EnterpriseDataPermission → DataPermission(dept)
- 修复所有测试：Mock 数据中 Entity 需要设置 enterpriseId
- 契约：mvn test Failures=0
- 验证：全量测试通过

## 验收标准
- mvn test Failures=0
- mvn flyway:info 显示 V99~V104 全部 Success
- check-entity-schema.mjs 通过
- 所有业务表有 enterprise_id 列
- RLS 策略启用
- EnterpriseDataPermissionInterceptor 单元测试通过
- EnterpriseContextHolder 单元测试通过

## 风险
- 69 表加列导致现有测试失败 → MC1-10 专门修复
- BaseEntity 迁移遗漏 → 分批迁移 + check-entity-schema.mjs
- 拦截器递归 StackOverflow → ThreadLocal 预占防护
- RLS 连接池串号 → HikariCP 连接清理（MC1-09）

## Git 提交规范
- 每个微循环独立 commit
- 格式：S26-MC1-{num}: {描述}
- git add 必须指定具体文件路径
- commit 前必须 mvn test 通过（MC1-10 完成后）
