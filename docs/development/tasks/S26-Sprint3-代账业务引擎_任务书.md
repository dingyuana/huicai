# S26 Sprint 3 任务书 — 代账业务引擎

> 日期：2026-07-23 | 任务 ID：S26-S3 | 关联 SPEC：S-26 | 前置：Sprint 1 + Sprint 2 完成

## 目标
完成代理公司管理、客户企业 CRUD、批量操作引擎（导入/审核/结账）、客户 CRM 合同管理。

## 前置条件
- Sprint 1 完成：多租户基础设施（enterprise_id/RLS/拦截器/BaseEntity）
- Sprint 2 完成：JWT 扩展、登录流程、企业切换、种子数据克隆
- com.huicai.agency 包结构已建立（tenant/batch/client 子包）

## 任务清单（7 个微循环）

### MC3-01: AgencyController — 代理公司 CRUD
- 目标：代理公司增删改查 API
- 涉及文件：
  - agency/tenant/controller/AgencyController.java
  - agency/tenant/service/AgencyService.java + impl/AgencyServiceImpl.java
  - agency/tenant/entity/AgencyEntity.java
  - agency/tenant/mapper/AgencyMapper.java
  - agency/tenant/dto/AgencyCreateDTO.java, AgencyUpdateDTO.java, AgencyVO.java
  - agency/tenant/constant/AgencyStatus.java（ACTIVE/SUSPENDED/TERMINATED）
- API 端点：
  - POST /api/v1/agency/agencies — 创建代理公司
  - PUT /api/v1/agency/agencies/{id} — 更新
  - GET /api/v1/agency/agencies/{id} — 查询详情
  - GET /api/v1/agency/agencies/page — 分页查询
  - DELETE /api/v1/agency/agencies/{id} — 逻辑删除
- 权限：仅 SUPER_ADMIN 可操作
- 契约：POST /api/v1/agency/agencies 创建代理公司返回 200 + agencyId
- TDD：先写 AgencyControllerTest（testCreate, testUpdate, testPage, testDelete）
- 验证：Controller 测试通过

### MC3-02: EnterpriseController — 客户企业 CRUD + 绑定关系
- 目标：客户企业增删改查 + 代理绑定关系管理
- 涉及文件：
  - agency/tenant/controller/EnterpriseController.java（扩展，Sprint 2 已创建切换接口）
  - agency/tenant/service/EnterpriseService.java + impl/EnterpriseServiceImpl.java
  - agency/tenant/entity/AgencyEnterpriseEntity.java
  - agency/tenant/mapper/AgencyEnterpriseMapper.java
  - agency/tenant/dto/EnterpriseCreateDTO.java, EnterpriseVO.java
- API 端点：
  - POST /api/v1/agency/enterprises — 创建客户企业 + 自动绑定到当前代理
  - PUT /api/v1/agency/enterprises/{id} — 更新
  - GET /api/v1/agency/enterprises/{id} — 查询详情
  - GET /api/v1/agency/enterprises/page — 分页查询当前代理的客户列表
  - DELETE /api/v1/agency/enterprises/{id} — 逻辑删除（解绑）
  - POST /api/v1/agency/enterprises/{id}/bind — 绑定到指定代理
  - POST /api/v1/agency/enterprises/{id}/unbind — 解绑
- 创建企业时：
  1. 插入 t_enterprise（mode=AGENCY_CLIENT, status=PENDING）
  2. 插入 t_agency_enterprise 绑定关系
  3. 不自动激活（需人工 activateEnterprise 触发种子数据）
- 契约：创建企业后 t_agency_enterprise 有绑定记录
- TDD：先写 EnterpriseControllerTest（testCreateEnterprise, testPageEnterprises, testBindUnbind）
- 验证：Controller 测试通过

### MC3-03: 批量导入服务
- 目标：多租户批量导入发票（复用 SME 导入逻辑，加 enterprise_id 隔离）
- 涉及文件：
  - agency/batch/controller/BatchOperationController.java
  - agency/batch/service/BatchImportService.java + impl/BatchImportServiceImpl.java
  - agency/batch/dto/BatchImportDTO.java, BatchResultVO.java
- 功能：
  - POST /api/v1/agency/batch/import — 批量导入发票（当前 enterprise_id 下）
  - 支持多文件上传，每个文件独立解析
  - 导入结果汇总返回（成功数/失败数/失败详情）
  - 所有导入的发票 enterprise_id = 当前切换的企业 ID（由 EnterpriseDataPermissionInterceptor 自动注入）
- 复用 SME 的 SalesInvoiceImportService / InputInvoiceImportService 逻辑
- 契约：批量导入的发票 enterprise_id = 当前企业 ID
- TDD：先写 BatchImportServiceTest（testBatchImportIsolated, testBatchImportMultipleFiles, testBatchImportErrorHandling）
- 负向断言：导入的发票不包含其他企业的数据
- 验证：导入测试通过 + 数据隔离校验

### MC3-04: 批量审核服务
- 目标：批量审核凭证/发票
- 涉及文件：
  - agency/batch/service/BatchAuditService.java + impl/BatchAuditServiceImpl.java
  - agency/batch/dto/BatchAuditDTO.java, BatchAuditResultVO.java
- 功能：
  - POST /api/v1/agency/batch/audit-vouchers — 批量审核凭证
  - POST /api/v1/agency/batch/audit-invoices — 批量审核发票
  - 接收 ID 列表，逐个调用状态机 confirm 方法
  - 返回每个 ID 的审核结果（成功/失败/原因）
  - 事务策略：每个审核独立事务，一个失败不影响其他
- 契约：批量审核只影响当前 enterprise_id 的数据
- TDD：先写 BatchAuditServiceTest（testBatchAuditVouchers, testBatchAuditInvoices, testBatchAuditPartialFailure, testBatchAuditIsolated）
- 负向断言：不能审核其他企业的凭证
- 验证：批量审核测试通过

### MC3-05: 批量结账服务
- 目标：批量期末结账（多企业遍历结账）
- 涉及文件：
  - agency/batch/service/BatchCloseService.java + impl/BatchCloseServiceImpl.java
  - agency/batch/dto/BatchCloseDTO.java, BatchCloseResultVO.java
- 功能：
  - POST /api/v1/agency/batch/close — 批量结账
  - 接收企业 ID 列表 + 期间参数
  - 遍历每个企业：切换 RLS context → 调用 PeriodCloseService.closePeriod → 记录结果
  - 返回每个企业的结账结果
  - 事务策略：每个企业独立事务
- 契约：批量结账遍历多个企业，每个独立结账
- TDD：先写 BatchCloseServiceTest（testBatchCloseMultiEnterprise, testBatchClosePartialFailure, testBatchCloseIsolated）
- 验证：批量结账测试通过

### MC3-06: 客户 CRM — 合同管理
- 目标：客户合同 CRUD + 续费提醒
- 涉及文件：
  - agency/client/controller/ClientController.java
  - agency/client/service/ContractService.java + impl/ContractServiceImpl.java
  - agency/client/entity/ContractEntity.java
  - agency/client/mapper/ContractMapper.java
  - agency/client/dto/ContractCreateDTO.java, ContractVO.java, RenewalReminderVO.java
  - Flyway V105__create_contract_table.sql
- DB 表 t_contract：
  - id, enterprise_id(关联客户企业), agency_id, contract_no(VARCHAR UNIQUE)
  - start_date, end_date, contract_type(VARCHAR), amount(NUMERIC(18,2))
  - status(VARCHAR: ACTIVE/EXPIRED/TERMINATED), renewal_notice_sent(BOOLEAN DEFAULT false)
  - 标准审计字段
- API 端点：
  - POST /api/v1/agency/contracts — 创建合同
  - GET /api/v1/agency/contracts/page — 分页查询
  - GET /api/v1/agency/contracts/renewal-reminders — 到期提醒列表（30天内到期且未续约）
  - PUT /api/v1/agency/contracts/{id}/renew — 续约
- 契约：合同到期前 30 天出现在续费提醒列表
- TDD：先写 ContractServiceTest（testContractCrud, testRenewalReminder, testRenew, testExpiredContract）
- 验证：合同 CRUD 测试 + 续费提醒测试

### MC3-07: Sprint 3 全量回归
- 目标：mvn test Failures=0 + 跨模块集成测试
- 新增 AgencyE2ETest：完整代理流程测试（创建代理→创建客户→激活→种子数据→批量导入→批量审核→批量结账）
- 契约：全量测试通过
- 验证：mvn test + E2E 测试通过

## 验收标准
- mvn test Failures=0
- 代理公司 CRUD 可用
- 客户企业 CRUD + 绑定关系可用
- 批量导入/审核/结账可用且数据隔离有效
- 合同管理 + 续费提醒可用
- 负向断言：不能操作其他企业的数据
- E2E 完整流程测试通过

## 风险
- 批量操作事务过大 → 每个操作独立事务
- 批量结账 RLS context 切换 → 每个企业结账前显式设置 context
- 合同表 Flyway 版本号冲突 → 确认 V105 未被占用
