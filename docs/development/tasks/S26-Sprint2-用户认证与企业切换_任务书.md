# S26 Sprint 2 任务书 — 用户认证与企业切换

> 日期：2026-07-23 | 任务 ID：S26-S2 | 关联 SPEC：S-26 | 前置：Sprint 1 完成

## 目标
完成 JWT 扩展、登录流程改造、企业切换接口、企业状态机、种子数据克隆。

## 前置条件
- Sprint 1 完成：enterprise_id 列已加、RLS 已启用、BaseEntity 已抽取、EnterpriseContextHolder 已实现、EnterpriseDataPermissionInterceptor 已注册
- 现有 JWT 仅有 userId + roles 两个 claims
- LoginUser 仅有 userId 一个扩展字段
- SecurityUtils 仅有 getCurrentUserId/getCurrentUsername
- AuthController 登录不设置 RLS context

## 任务清单（10 个微循环）

### MC2-01: LoginUser 扩展
- 目标：LoginUser 加 enterpriseId/agencyId/userType 字段
- 涉及文件：backend/src/main/java/com/huicai/config/security/LoginUser.java
- 改造：构造函数增加 enterpriseId(Long)、agencyId(Long)、userType(String) 参数
- 添加 getter 方法
- 契约：new LoginUser(userEntity, authorities, 1L, null, "ENTERPRISE").getEnterpriseId() == 1L
- TDD：先写 LoginUserTest
- 验证：编译通过 + 单元测试

### MC2-02: JwtProvider 扩展 claims
- 目标：JWT Token 加 enterpriseId/agencyId/userType claims
- 涉及文件：backend/src/main/java/com/huicai/config/security/JwtProvider.java
- generateAccessToken 方法签名改为 (username, userId, roles, enterpriseId, agencyId, userType)
- Token 中增加 .claim("enterpriseId", enterpriseId).claim("agencyId", agencyId).claim("userType", userType)
- 解析方法增加 getEnterpriseId/getAgencyId/getUserType 从 Claims 提取
- 契约：生成 Token → 解析 → claims 包含 userType
- TDD：先写 JwtProviderTest（testExtendedClaims, testParseEnterpriseId, testParseUserType）
- 验证：单元测试通过

### MC2-03: SecurityUtils 扩展
- 目标：加 getCurrentEnterpriseId/getCurrentUserType/getCurrentAgencyId
- 涉及文件：backend/src/main/java/com/huicai/base/system/util/SecurityUtils.java
- 从 LoginUser 实例取值：
  - getCurrentEnterpriseId(): 从 LoginUser 取 enterpriseId
  - getCurrentUserType(): 从 LoginUser 取 userType
  - getCurrentAgencyId(): 从 LoginUser 取 agencyId
- 契约：SecurityUtils.getCurrentEnterpriseId() == 1L（SME 用户）
- TDD：先写 SecurityUtilsTest
- 验证：单元测试通过

### MC2-04: JwtAuthenticationFilter 扩展
- 目标：从 Token 解析 claims 填充 LoginUser + 设置 EnterpriseContextHolder
- 涉及文件：backend/src/main/java/com/huicai/config/security/JwtAuthenticationFilter.java
- 在 doFilterInternal 中：
  1. 解析 Token 得到 enterpriseId/agencyId/userType
  2. 构造 LoginUser 时传入这三个字段
  3. 设置 EnterpriseContextHolder.set(enterpriseId)
  4. 设置 RLS context: jdbcTemplate.execute("SELECT set_config('app.enterprise_id', enterpriseId, false)")
  5. 请求结束后 finally 中 EnterpriseContextHolder.clear()
- 契约：请求到达 Controller 时 EnterpriseContextHolder.get() == Token 中的 enterpriseId
- TDD：先写 JwtAuthenticationFilterTest
- 验证：集成测试

### MC2-05: AuthController 登录流程改造
- 目标：登录时设置 RLS context + 返回 userType/enterpriseList
- 涉及文件：backend/src/main/java/com/huicai/base/system/controller/AuthController.java
- 登录方法改造：
  1. 校验密码后，构造 LoginUser 传入 userType/enterpriseId/agencyId
  2. 如果是 AGENCY 用户，查询 t_agency_enterprise 获取绑定的企业列表
  3. 如果是 ENTERPRISE 用户，enterpriseId 直接从 user.enterprise_id 取
  4. 设置 RLS context
  5. 返回 LoginResponse 包含 userType/enterpriseId/agencyId/enterpriseList
- 需要新建 LoginResponseVO 扩展（加 userType/enterpriseId/agencyId/enterpriseList 字段）
- 需要新建 EnterpriseSimpleVO（id/name/taxId/status）
- 契约：AGENCY 用户登录返回 enterpriseList.length > 0
- BDD 场景：场景 1（SME 登录直达）+ 场景 2（AGENCY 登录看客户列表）
- TDD：先写 AuthControllerTest（testSmeLoginDirect, testAgencyLoginEnterpriseList）
- 验证：Controller 测试通过

### MC2-06: EnterpriseController — 切换接口
- 目标：POST /api/v1/enterprise/switch 接口
- 涉及文件：backend/src/main/java/com/huicai/agency/tenant/controller/EnterpriseController.java
- 接口设计：
  - POST /api/v1/enterprise/switch?enterpriseId=3
  - 校验：当前用户 userType=AGENCY 且 t_agency_enterprise 中有绑定关系
  - 设置 RLS context: set_config('app.enterprise_id', '3', false)
  - 更新 EnterpriseContextHolder
  - 返回 EnterpriseSwitchVO（enterpriseId/enterpriseName/seedDataDone）
- 错误处理：
  - ENTERPRISE 用户调切换 → 20008「无权切换企业」
  - 未绑定的企业 → 20008
- 契约：切换后 RLS context 更新
- BDD 场景：场景 3（切换客户）+ 场景 6（ENTERPRISE 用户不能切换）
- TDD：先写 EnterpriseControllerTest（testSwitchEnterprise, testEnterpriseUserCannotSwitch, testUnboundEnterpriseBlocked）
- 验证：Controller 测试通过

### MC2-07: EnterpriseStateMachineService 实现
- 目标：企业状态机 PENDING→ACTIVE→SUSPENDED→TERMINATED
- 涉及文件：
  - agency/tenant/service/EnterpriseStateMachineService.java（接口）
  - agency/tenant/service/impl/EnterpriseStateMachineServiceImpl.java（实现）
  - agency/tenant/entity/EnterpriseEntity.java
  - agency/tenant/mapper/EnterpriseMapper.java
  - agency/tenant/constant/EnterpriseStatus.java（枚举）
- 状态机方法：
  - activateEnterprise(Long id): PENDING→ACTIVE, 触发种子数据克隆
  - suspendEnterprise(Long id, String reason): ACTIVE→SUSPENDED
  - reactivateEnterprise(Long id): SUSPENDED→ACTIVE
  - terminateEnterprise(Long id): SUSPENDED→TERMINATED
- 每个方法必须：前置校验 + 状态转换 + 审计日志
- 契约：PENDING→ACTIVE 触发种子数据克隆
- BDD 场景：场景 5（新建客户初始化种子数据）+ 场景 7（暂停不能创建）
- TDD：先写 EnterpriseStateMachineServiceImplTest（testActivateSeedsData, testActivateDoesNotPolluteOthers, testSuspendBlocksCreate, testTerminateIsTerminal）
- 负向断言：activate 不影响其他企业数据；TERMINATED 不可再转换
- 验证：状态机测试通过 + 负向断言通过

### MC2-08: 种子数据克隆逻辑
- 目标：activateEnterprise 时克隆科目/凭证类型/摘要库/期间模板
- 涉及文件：
  - agency/tenant/service/SeedDataService.java（接口）
  - agency/tenant/service/impl/SeedDataServiceImpl.java（实现）
- 克隆逻辑：
  1. 从 t_subject WHERE enterprise_id=0 克隆到新 enterprise_id（INSERT INTO t_subject (enterprise_id, code, name, ...) SELECT #{newId}, code, name, ... FROM t_subject WHERE enterprise_id=0）
  2. 同理克隆 t_voucher_type、t_summary_lib、t_period
  3. 标记 enterprise.seed_data_done=true
- 幂等性：如果 seed_data_done=true 则跳过
- 契约：克隆后 t_subject WHERE enterprise_id=新ID 数量 = 模板数量
- BDD 场景：场景 5（种子数据初始化）
- TDD：先写 SeedDataServiceImplTest（testCloneSubjects, testCloneVoucherTypes, testCloneSummaryLib, testClonePeriods, testIdempotentSeed）
- 验证：种子克隆测试通过 + 数据完整性校验

### MC2-09: 移除硬编码 tenantId
- 目标：清除 ClassificationRuleServiceImpl + AutoGenerationService 中的 1L 硬编码
- 涉及文件：
  - sme/cash/service/impl/ClassificationRuleServiceImpl.java（第49行 entity.setTenantId(1L) 和第128-129行 Long tenantId = 1L）
  - sme/arap/service/impl/AutoGenerationService.java（第709行和第749行 prepay.setTenantId(1L)）
- 改为：从 SecurityUtils.getCurrentEnterpriseId() 获取（注意：tenantId 字段名后续可统一为 enterpriseId，但本次只改值不改名）
- 契约：grep -r "tenantId.*1L" backend/src/main → 0 命中
- 验证：grep 无命中 + 相关测试通过

### MC2-10: Sprint 2 全量回归
- 目标：mvn test Failures=0 + 契约验证
- 修复被破坏的测试（JWT 签名变了导致所有 Token 测试需更新）
- 运行 validate_spec_contract.py 验证 S-26 YAML 契约
- 契约：S-26 acceptance_tests AT-001~AT-007 全部覆盖
- 验证：全量测试通过 + 契约脚本通过

## 验收标准
- mvn test Failures=0
- BDD 场景 1~7 全部有对应 @Test 且 PASS
- JWT Token 含 enterpriseId/agencyId/userType claims
- AGENCY 用户登录返回 enterpriseList
- 切换企业后 RLS context 更新
- 种子数据克隆正确
- grep 无硬编码 tenantId=1L
- 负向断言：activate 不影响其他企业；TERMINATED 不可再转换；ENTERPRISE 用户不能切换

## 风险
- JWT 签名变更导致所有 Token 测试失败 → MC2-10 专门修复
- 种子数据克隆事务过大 → 分表克隆，每个表独立事务
- RLS context 在异步线程中丢失 → 异步任务需手动传递 enterpriseId
