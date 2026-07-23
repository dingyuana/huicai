# S-26 Agency 分支测试计划

> **版本**：1.0 | **日期**：2026-07-23 | **关联 SPEC**：[S-26](../../specs/S-26-agency-branch-development.md)
> **测试规范**：TDD-First (Red→Green) + 负向断言强制 + BDD 场景全覆盖

---

## 1. 测试金字塔

```
E2E (Playwright)       ← 5 个：完整代理业务流程
组件 (Vitest)           ← 8 个：Store/请求/路由/页面/组件
集成 (SpringBootTest)   ← 15 个：登录/切换/拦截器/RLS/种子/批量
单元 (JUnit 5)          ← 50+ 个：状态机/拦截器/ContextHolder/Service/Mapper
契约 (YAML)             ← 8 个：AT-001~AT-008 逐条验证
```

## 2. 测试清单（按 Sprint）

### Sprint 1 测试（18 个）

| # | 测试类 | 测试方法 | 契约 | 负向断言 |
|---|--------|---------|------|---------|
| 1 | EnterpriseContextHolderTest | testSetGetClear | set→get→clear→null | — |
| 2 | EnterpriseDataPermissionInterceptorTest | testSelectInjectEnterpriseId | SELECT 追加条件 | 共享表不注入 |
| 3 | EnterpriseDataPermissionInterceptorTest | testInsertFillEnterpriseId | INSERT 自动填充 | — |
| 4 | EnterpriseDataPermissionInterceptorTest | testSuperAdminBypass | 超管不拦截 | — |
| 5 | EnterpriseDataPermissionInterceptorTest | testRecursiveGuard | 防递归预占 | — |
| 6 | HikariRlsConfigTest | testConnectionCleanOnBorrow | 借出时清理 | — |
| 7 | HikariRlsConfigTest | testRlsContextNotLeak | 归还后不串号 | — |
| 8 | BaseEntityTest | testEnterpriseIdField | 字段存在 | — |
| 9 | SubjectMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 10 | VoucherMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 11 | BusinessDocMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 12 | OutputInvoiceMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 13 | BankStatementMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 14 | AssetCardMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 15 | BudgetMapperTest | testEnterpriseIdColumn | DB 列存在 | 跨企业查询为空 |
| 16 | MetaObjectHandlerTest | testInsertFillEnterpriseId | 填充正确 | 上下文为空不填充 |
| 17 | RlsPolicyTest | testRlsEnabled | RLS 启用 | 未设 context 返回空 |
| 18 | V99MigrationTest | testTablesExist | 三表存在 | — |

### Sprint 2 测试（15 个）

| # | 测试类 | 测试方法 | BDD 场景 | 负向断言 |
|---|--------|---------|---------|---------|
| 19 | LoginUserTest | testExtendedFields | — | — |
| 20 | JwtProviderTest | testExtendedClaims | — | — |
| 21 | JwtProviderTest | testParseEnterpriseId | — | — |
| 22 | JwtProviderTest | testParseUserType | — | — |
| 23 | SecurityUtilsTest | testGetCurrentEnterpriseId | — | — |
| 24 | SecurityUtilsTest | testGetCurrentUserType | — | — |
| 25 | AuthControllerTest | testSmeLoginDirect | 场景 1 | enterpriseList 为空 |
| 26 | AuthControllerTest | testAgencyLoginEnterpriseList | 场景 2 | enterpriseId 为 null |
| 27 | EnterpriseControllerTest | testSwitchEnterprise | 场景 3 | — |
| 28 | EnterpriseControllerTest | testCrossEnterpriseBlocked | 场景 4 | 返回 20003 |
| 29 | EnterpriseControllerTest | testEnterpriseUserCannotSwitch | 场景 6 | 返回 20008 |
| 30 | EnterpriseStateMachineServiceImplTest | testActivateSeedsData | 场景 5 | 不影响其他企业 |
| 31 | EnterpriseStateMachineServiceImplTest | testSuspendBlocksCreate | 场景 7 | 返回 20005 |
| 32 | EnterpriseStateMachineServiceImplTest | testTerminateIsTerminal | — | 不可再转换 |
| 33 | SeedDataServiceImplTest | testCloneAllTemplates | — | 幂等性验证 |

### Sprint 3 测试（10 个）

| # | 测试类 | 测试方法 | 契约 | 负向断言 |
|---|--------|---------|------|---------|
| 34 | AgencyControllerTest | testCrud | 代理公司 CRUD | — |
| 35 | EnterpriseControllerTest | testCreateEnterprise | 创建+绑定 | — |
| 36 | BatchImportServiceTest | testBatchImportIsolated | enterprise_id 隔离 | 不含其他企业数据 |
| 37 | BatchAuditServiceTest | testBatchAuditVouchers | 批量审核 | 不能审核其他企业 |
| 38 | BatchAuditServiceTest | testBatchAuditPartialFailure | 部分失败 | 失败不影响成功 |
| 39 | BatchCloseServiceTest | testBatchCloseMultiEnterprise | 多企业结账 | 独立事务 |
| 40 | ContractServiceTest | testContractCrud | 合同 CRUD | — |
| 41 | ContractServiceTest | testRenewalReminder | 30 天提醒 | 未到期不提醒 |
| 42 | ContractServiceTest | testRenew | 续约 | — |
| 43 | AgencyE2ETest | testFullAgencyFlow | 完整流程 | — |

### Sprint 4 测试（8 个）

| # | 测试文件 | 测试方法 | 契约 |
|---|---------|---------|------|
| 44 | auth.store.test.ts | testAgencyLoginStore | enterpriseList > 0 |
| 45 | auth.store.test.ts | testSwitchEnterprise | currentEnterpriseId 更新 |
| 46 | request.test.ts | testEnterpriseHeader | X-Enterprise-Id 携带 |
| 47 | router.test.ts | testUserTypeDispatch | 按 userType 跳转 |
| 48 | EnterpriseList.test.ts | testRenderList | 列表渲染 |
| 49 | EnterpriseSwitcher.test.ts | testSwitch | 切换功能 |
| 50 | BatchOperation.test.ts | testBatchUI | 批量操作 |
| 51 | agency-flow.spec.ts | testFullAgencyFlow | E2E 全流程 |

## 3. BDD 场景覆盖矩阵

| BDD 场景 | 对应测试 | Sprint | 状态 |
|---------|---------|--------|------|
| 场景 1：SME 登录直达 | AuthControllerTest.testSmeLoginDirect | S2 | 🆕 |
| 场景 2：AGENCY 登录看列表 | AuthControllerTest.testAgencyLoginEnterpriseList | S2 | 🆕 |
| 场景 3：切换客户 | EnterpriseControllerTest.testSwitchEnterprise | S2 | 🆕 |
| 场景 4：跨企业拦截 | EnterpriseControllerTest.testCrossEnterpriseBlocked | S2 | 🆕 |
| 场景 5：种子数据初始化 | EnterpriseStateMachineServiceImplTest.testActivateSeedsData | S2 | 🆕 |
| 场景 6：ENTERPRISE 不能切换 | EnterpriseControllerTest.testEnterpriseUserCannotSwitch | S2 | 🆕 |
| 场景 7：暂停不能创建 | EnterpriseStateMachineServiceImplTest.testSuspendBlocksCreate | S2 | 🆕 |
| 场景 8：RLS 兜底 | RlsPolicyTest.testRlsEnabled | S1 | 🆕 |

## 4. 负向断言清单

| 测试 | 断言 |
|------|------|
| EnterpriseDataPermissionInterceptorTest | 共享表不注入 enterprise_id |
| EnterpriseDataPermissionInterceptorTest | 超级管理员跳过拦截 |
| EnterpriseDataPermissionInterceptorTest | 递归防护生效 |
| EnterpriseStateMachineServiceImplTest | activate 不影响其他企业数据 |
| EnterpriseStateMachineServiceImplTest | TERMINATED 不可再转换 |
| EnterpriseControllerTest | 跨企业访问返回 20003 |
| EnterpriseControllerTest | ENTERPRISE 用户切换返回 20008 |
| EnterpriseStateMachineServiceImplTest | SUSPENDED 企业创建返回 20005 |
| BatchImportServiceTest | 导入数据不含其他企业 |
| BatchAuditServiceTest | 不能审核其他企业凭证 |
| ContractServiceTest | 未到期合同不出现在提醒列表 |

## 4.1 并发测试清单（2026-07-23 补充）

多租户架构下 RLS 连接池串号、ThreadLocal 上下文泄漏是高风险项，需显式并发测试覆盖。

| # | 测试类 | 测试方法 | 场景 | 期望 |
|---|--------|---------|------|------|
| C1 | HikariRlsConfigTest | testConcurrentBorrowNoLeak | 100 线程并发借出连接，各自设置不同 enterprise_id | 连接归还后 RLS context 清空，无串号 |
| C2 | EnterpriseContextHolderTest | testConcurrentSetGetClear | 50 线程并发 set/get/clear 不同 enterpriseId | ThreadLocal 隔离正确，无交叉污染 |
| C3 | EnterpriseDataPermissionInterceptorTest | testConcurrentInsertDifferentEnterprise | 2 企业 × 20 线程并发 INSERT 各自数据 | 数据正确隔离，无混入 |
| C4 | EnterpriseDataPermissionInterceptorTest | testConcurrentSelectNoCrossLeak | 2 企业 × 20 线程并发 SELECT | 各企业仅查到自己的数据 |
| C5 | EnterpriseStateMachineServiceImplTest | testConcurrentActivateSameEnterprise | 5 线程并发激活同一 PENDING 企业 | 仅 1 个成功，其余抛状态冲突异常 |
| C6 | EnterpriseControllerTest | testConcurrentSwitchEnterprise | 3 线程并发切换到不同企业 | 各线程 SecurityContext 中 enterpriseId 独立正确 |

**通过标准**：6 个并发测试全部 PASS，成功率 ≥ 95%，无未捕获异常逃逸。

## 5. 测试执行命令

```bash
# 后端全量测试
cd backend && mvn test

# 后端单个测试类
cd backend && mvn test -Dtest=EnterpriseStateMachineServiceImplTest

# 前端单元测试
cd frontend && npm test

# 前端 E2E
cd frontend && npx playwright test src/__tests__/e2e/agency-flow.spec.ts

# 契约验证
python scripts/validate_spec_contract.py --path S-26

# Entity-DB 一致性检查
node backend/scripts/check-entity-schema.mjs

# Flyway 状态
cd backend && mvn flyway:info
```

## 6. 测试通过标准

- **后端**：`mvn test` Failures=0, Errors=0
- **前端**：`npm test` 所有测试通过
- **E2E**：`npx playwright test` 全部通过
- **契约**：`validate_spec_contract.py` exit code=0
- **Entity-DB**：`check-entity-schema.mjs` 无不一致
- **BDD 覆盖**：8 个场景全部有对应 @Test 且 PASS
- **负向断言**：11 条负向断言全部 PASS
- **并发测试**：6 个并发测试（C1~C6）全部 PASS，成功率 ≥ 95%
- **测试总数**：51 基础 + 6 并发 = 57 个 @Test
