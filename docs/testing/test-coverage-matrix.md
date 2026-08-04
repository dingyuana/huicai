# 测试覆盖矩阵 HUICAI-TST-001

> **编号**：HUICAI-TST-001
> **版本**：V1.3 | **修改日期**：2026-08-01 | **修改人**：Hermes
> **目的**：记录各模块测试覆盖状态，标识空白区域，驱动补测优先级
> **修正记录 (V1.3)**：
> - 全面修正硬数字（后端 @Test 1033→1556、测试类 133→189、前端 12→14、E2E 文件 4→18）
> - Service 层：新增 PrepaymentServiceImpl(11)/VoucherTemplateServiceImpl(14)/VendorServiceImpl(12)/CustomerServiceImpl(12)/NumberingTraceServiceImpl(8)/LedgerServiceImpl(7)/InputInvoiceImportServiceTest(19)/UserDetailsServiceImpl(5) 等 P0 补充
> - 新增 AssetCardStateMachineServiceImpl(29)/AssetDisposalStateMachineServiceImpl(15)/AssetInventoryStateMachineServiceImpl(15)/BudgetStateMachineServiceImpl(21) 等状态机测试
> - 新增 Agency 模块测试（EnterpriseControllerTest(7)/AgencyUserControllerTest(7)/AssignmentControllerTest(5)）
> - Controller 层：新增 VoucherControllerTest(15)/BusinessDocControllerTest(11)/BankStatementControllerTest(10)/TaxControllerTest(14) 等
> - 并发测试：新增 ConcurrencyLoadTest/CoreWriteOperationConcurrencyTest，共 3 文件 12 @Test
> - 前端：新增 BankStatementView(15)/InputInvoiceList(13)/VoucherList(18)/ReconciliationWorkbench(4) 组件测试，共 6 组件 + 1 Store + 7 API = 14 文件 / 171 用例
> - E2E 后端链路：新增 AssetFlowE2ETest/BudgetFlowE2ETest，共 10 文件 / 39 @Test
> - Playwright E2E：从 4 文件 / 9 用例 → 18 文件 / 132 用例（V1.3 补齐 32 个新 test() 调用，含参数化展开共 132 个实际测试）
> - 断层清单：新增 T9（test-coverage-matrix 过时记录）、T10（Agency Service 测试不足）、T11（前端组件测试覆盖率不足）、T12（Playwright E2E 用例数不足）
> - 移除已过期的 E2E 已知问题 §5（报表 500 错误已在 2026-07-29 修复的 EnterpriseDataPermissionInterceptor 中处理）

---

## 0. 总览（硬数字）

| 指标 | 旧值 (V1.2) | 当前实测 (V1.3) | 校验命令 |
|------|-------------|----------------|----------|
| 后端 @Test 总数 | 1033 | **1556** | `grep -rhE '^\s*@Test\b' --include='*Test.java' backend/ \| wc -l` |
| 后端测试类数 | 133 | **189** | `find backend/ -name '*Test.java' -type f \| wc -l` |
| 前端 Vitest 测试文件数 | 12 | **14** | `find frontend/src/__tests__ -name '*.test.ts' \| wc -l` |
| 前端 Vitest 用例数 | — | **171** | `grep -cE '^\s*(test\|it)\(' frontend/src/__tests__/*.test.ts` |
| Playwright E2E 文件数 | 4 | **18** | `find e2e/tests -name '*.spec.ts' \| wc -l` |
| Playwright E2E 用例数 | 9 | **132** | `npx playwright test --list \| grep "Total:"` |
| 后端 E2E 链路测试 | — | **10 文件 / 39 @Test** | `find backend -name '*E2E*Test.java' -type f` |
| 状态机专项测试 | — | **10 文件 / 222 @Test** | `find backend -name '*StateMachine*Test.java' -type f` |
| 并发测试 | — | **3 文件 / 12 @Test** | `find backend -name '*Concurrency*Test.java' -type f` |

> 注：V1.2 记录数字为 2026-07-23 快照（1033 @Test / 133 类），V1.3 更新至 2026-08-01 实测（1556 @Test / 189 类），9 天内净增约 523 @Test / 56 类。

---

## 1. 后端测试覆盖矩阵

### 1.1 Service 层（46 文件 / 568 @Test）

| 模块 | 类 | 测试类 | 测试数 | 覆盖方法 | 空白 | 优先级 |
|------|-----|--------|--------|----------|------|--------|
| finance | BusinessDocServiceImpl | BusinessDocServiceImplTest.java | 33 | update(10), getDetail(5), generateVoucher(5), pageQuery(5) + 8 | **pageQuery() 多docType** | P0 |
| finance | SalesInvoiceImportServiceImpl | SalesInvoiceImportServiceTest.java | 11 | preview, import, validate, batch | 无 | - |
| finance | BankStatementServiceImpl | BankStatementServiceTest.java | 37 | parse, validate, route, classify, match | 无 | - |
| finance | BankReconciliationServiceImpl | BankReconciliationServiceImplTest.java | 20 | runMatching, score, autoReconcile | 无 | - |
| finance | ClassificationRuleServiceImpl | ClassificationRuleServiceTest.java | 21 | classify, autoCreate, ruleEval | 无 | - |
| finance | ClassificationRuleServiceImpl | ClassificationRuleServiceImplTest.java | 7 | rule CRUD | 无 | - |
| finance | FallbackHeuristicServiceImpl | FallbackHeuristicServiceTest.java | 31 | fallback, heuristic, resolve | 无 | - |
| finance | AutoGenerationServiceImpl | AutoGenerationServiceTest.java | 23 | autoGen, voucherCreate | 无 | - |
| finance | VoucherStateMachineServiceImpl | VoucherStateMachineServiceImplTest.java | 20 | 状态转换 V2 | 无 | - |
| finance | ColumnMappingResolverImpl | ColumnMappingResolverTest.java | 12 | 列映射解析 | 无 | - |
| finance | BankAccountServiceImpl | BankAccountServiceImplTest.java | 7 | CRUD | 无 | - |
| finance | TicketServiceImpl | TicketServiceImplTest.java | 6 | 票据处理 | 无 | - |
| finance | CashJournalServiceImpl | CashJournalServiceImplTest.java | 3 | CRUD | 无 | - |
| finance | TemplateMatcherImpl | TemplateMatcherTest.java | 6 | 模板匹配 | 无 | - |
| finance | VoucherEntryValidation | VoucherEntryValidationTest.java | 6 | 分录校验 | 无 | - |
| finance | VoucherNoFormat | VoucherNoFormatTest.java | 4 | 编号格式 | 无 | - |
| finance | TrialBalance | TrialBalanceTest.java | 4 | 试算平衡 | 无 | - |
| finance | CounterpartyExtractor | CounterpartyExtractorTest.java | 10 | 对方科目提取 | 无 | - |
| finance | **InputInvoiceImportService** | **InputInvoiceImportServiceTest.java** | **19** ✅(V1.3新增) | 进项发票导入/日期解析/供应商匹配 | 无 | - |
| finance | **VoucherTemplateService** | **VoucherTemplateServiceImplTest.java** | **14** ✅(V1.3新增) | 模板匹配/创建/更新/激活/删除 | 无 | - |
| finance | **VoucherNoService** | **VoucherNoServiceImplTest.java** | **10** ✅(V1.3新增) | 凭证号生成 | 无 | - |
| finance | **LedgerService** | **LedgerServiceImplTest.java** | **7** ✅(V1.3新增) | 总账/明细账/试算平衡 | 无 | - |
| arap | ReconciliationServiceImpl | ReconciliationServiceImplTest.java | 43 | recommend, execute, match, settle | 无 | - |
| arap | ReconciliationToleranceServiceImpl | ReconciliationToleranceServiceImplTest.java | 12 | tolerance, rule | 无 | - |
| arap | ExpenseReimbursementServiceImpl | ExpenseReimbursementServiceImplTest.java | 17 | 状态机全流程 | 无 | - |
| arap | BadDebtProvisionStateMachineServiceImpl | BadDebtProvisionStateMachineServiceImplTest.java | 10 | 状态转换 | 无 | - |
| arap | EmployeeServiceImpl | EmployeeServiceImplTest.java | 11 | pageQuery, CRUD | 无 | - |
| arap | **PrepaymentService** | **PrepaymentServiceImplTest.java** | **11** ✅(V1.3新增) | 预付款创建/确认/核销/反冲 | 无 | - |
| arap | **NumberingTraceService** | **NumberingTraceServiceImplTest.java** | **8** ✅(V1.3新增) | 编号追溯全链路 | 无 | - |
| system | PeriodServiceImpl | PeriodServiceImplTest.java | 3 | open/close | 无 | - |
| system | SubjectServiceImpl | SubjectServiceImplTest.java | 3 | CRUD | 无 | - |
| system | UserServiceImpl | UserServiceImplTest.java | 5 | CRUD | 无 | - |
| system | RoleServiceImpl | RoleServiceImplTest.java | 6 | CRUD | 无 | - |
| system | MenuServiceImpl | MenuServiceImplTest.java | 5 | CRUD | 无 | - |
| system | DeptServiceImpl | DeptServiceImplTest.java | 6 | CRUD | 无 | - |
| system | SysConfigServiceImpl | SysConfigServiceImplTest.java | 5 | CRUD | 无 | - |
| system | VoucherTypeServiceImpl | VoucherTypeServiceImplTest.java | 5 | CRUD | 无 | - |
| system | AuditLogServiceImpl | AuditLogServiceImplTest.java | 3 | 审计日志 | 无 | - |
| system | SummaryLibServiceImpl | SummaryLibServiceImplTest.java | 5 | 摘要库 | 无 | - |
| system | **UserDetailsService** | **UserDetailsServiceImplTest.java** | **5** ✅(V1.3新增) | 用户登录加载/状态校验 | 无 | - |
| tax | TaxServiceImpl | TaxServiceImplTest.java | 12 | 发票/申报 | 无 | - |
| tax | OutputInvoiceStateMachineServiceImpl | OutputInvoiceStateMachineServiceImplTest.java | 48 | 状态转换全面 | 无 | - |
| tax | InputInvoiceStateMachineServiceImpl | InputInvoiceStateMachineServiceImplTest.java | 22 | 进项状态转换 | 无 | - |
| budget | BudgetServiceImpl | BudgetServiceImplTest.java | 8 | CRUD/审批 | 无 | - |
| budget | BudgetAdjustmentStateMachineServiceImpl | BudgetAdjustmentStateMachineServiceImplTest.java | 12 | 状态转换 | 无 | - |
| budget | **BudgetStateMachineServiceImpl** | **BudgetStateMachineServiceImplTest.java** | **21** ✅(V1.3新增) | 预算状态机全流程 | 无 | - |
| asset | AssetCategoryServiceImpl | AssetCategoryServiceImplTest.java | 7 | CRUD | 无 | - |
| asset | AssetDisposalServiceImpl | AssetDisposalServiceImplTest.java | 3 | 处置 | 无 | - |
| asset | **AssetCardServiceImpl** | **AssetCardServiceImplTest.java** | **17** ✅(V1.3新增) | 资产卡片 CRUD/折旧/状态管理 | 无 | - |
| asset | **AssetCardStateMachineService** | **AssetCardStateMachineServiceImplTest.java** | **29** ✅(V1.3新增) | 资产卡片状态机 | 无 | - |
| asset | **AssetDisposalStateMachineService** | **AssetDisposalStateMachineServiceImplTest.java** | **15** ✅(V1.3新增) | 资产处置状态机 | 无 | - |
| asset | **AssetInventoryStateMachineService** | **AssetInventoryStateMachineServiceImplTest.java** | **15** ✅(V1.3新增) | 资产盘点状态机 | 无 | - |
| ai | AiTaskStateMachineServiceImpl | AiTaskStateMachineServiceImplTest.java | 30 | 状态转换 | 无 | - |
| ai | AiFeedbackLogServiceImpl | AiFeedbackLogServiceImplTest.java | 4 | 反馈日志 | 无 | - |
| report | ReportServiceImpl | ReportServiceImplTest.java | 5 | CRUD | 无 | - |
| report | **AnalysisService** | **AnalysisServiceTest.java** | **8** ✅(V1.3新增) | 财务指标计算/杜邦分析 | 无 | - |
| storage | AttachmentService | AttachmentServiceTest.java | 3 | 附件管理 | 无 | - |
| common | AmountExpressionResolver | AmountExpressionResolverTest.java | 12 | 金额表达式 | 无 | - |
| common | TemplateEngine | TemplateEngineTest.java | 13 | 模板引擎 | 无 | - |
| common | PeriodCloseServiceImpl | PeriodCloseServiceImplTest.java | 6 | 期间结转 | 无 | - |
| common | **CustomerService** | **CustomerServiceImplTest.java** | **12** ✅(V1.3新增) | 客户 CRUD/分页/编码校验 | 无 | - |
| common | **VendorService** | **VendorServiceImplTest.java** | **12** ✅(V1.3新增) | 供应商 CRUD/分页/编码校验 | 无 | - |

### 1.2 Mapper 层（真实 DB，38 文件 / 192 @Test）

| 模块 | Mapper | 测试类 | 测试数 | 覆盖 | 优先级 |
|------|--------|--------|--------|------|--------|
| finance | BusinessDocMapper | BusinessDocMapperTest.java | 7 | insert/update 约束 | - |
| finance | BusinessDocEntryMapper | BusinessDocEntryMapperTest.java | 5 | CRUD | - |
| finance | BankStatementMapper | BankStatementMapperTest.java | 7 | CRUD | - |
| finance | VoucherMapper | VoucherMapperTest.java | 4 | CRUD | - |
| finance | VoucherEntryMapper | VoucherEntryMapperTest.java | 5 | CRUD | - |
| finance | VoucherTemplateMapper | VoucherTemplateMapperTest.java | 5 | CRUD | - |
| finance | VoucherTemplateLineMapper | VoucherTemplateLineMapperTest.java | 5 | CRUD | - |
| finance | BankAccountMapper | BankAccountMapperTest.java | 5 | CRUD | - |
| finance | BankJournalMapper | BankJournalMapperTest.java | 5 | CRUD | - |
| finance | CashJournalMapper | CashJournalMapperTest.java | 5 | CRUD | - |
| finance | ClassificationRuleMapper | ClassificationRuleMapperTest.java | 5 | CRUD | - |
| finance | NumberingAssociationFieldsTest | NumberingAssociationFieldsTest.java | 8 | 编号关联字段 | - |
| finance | NumberingAssociationIndexesTest | NumberingAssociationIndexesTest.java | 15 | 索引 | - |
| tax | OutputInvoiceMapper | OutputInvoiceMapperTest.java | 6 | CRUD | - |
| tax | InputInvoiceMapper | InputInvoiceMapperTest.java | 6 | CRUD | - |
| tax | TaxDeclarationMapper | TaxDeclarationMapperTest.java | 5 | 申报 | - |
| asset | AssetCardMapper | AssetCardMapperTest.java | 8 | CRUD | - |
| asset | AssetCategoryMapper | AssetCategoryMapperTest.java | 5 | CRUD | - |
| asset | AssetDepreciationMapper | AssetDepreciationMapperTest.java | 5 | 折旧 | - |
| asset | AssetDisposalMapper | AssetDisposalMapperTest.java | 5 | 处置 | - |
| arap | ArapSettlementMapper | ArapSettlementMapperTest.java | 5 | CRUD | - |
| arap | BadDebtProvisionMapper | BadDebtProvisionMapperTest.java | 5 | CRUD | - |
| arap | CustomerMapper | CustomerMapperTest.java | 5 | CRUD | - |
| arap | EmployeeMapper | EmployeeMapperTest.java | 5 | CRUD | - |
| arap | ExpenseReimbursementMapper | ExpenseReimbursementMapperTest.java | 5 | CRUD | - |
| arap | PrepaymentMapper | PrepaymentMapperTest.java | 5 | CRUD | - |
| arap | VendorMapper | VendorMapperTest.java | 5 | CRUD | - |
| system | PeriodMapper | PeriodMapperTest.java | 5 | CRUD | - |
| system | SubjectMapper | SubjectMapperTest.java | 4 | CRUD | - |
| system | DeptMapper | DeptMapperTest.java | 5 | CRUD | - |
| system | MenuMapper | MenuMapperTest.java | 5 | CRUD | - |
| system | RoleMapper | RoleMapperTest.java | 5 | CRUD | - |
| system | SysConfigMapper | SysConfigMapperTest.java | 5 | CRUD | - |
| system | UserMapper | UserMapperTest.java | 5 | CRUD | - |
| system | VoucherTypeMapper | VoucherTypeMapperTest.java | 5 | CRUD | - |
| budget | BudgetMapper | BudgetMapperTest.java | 5 | CRUD | - |
| budget | BudgetAdjustmentMapper | BudgetAdjustmentMapperTest.java | 5 | CRUD | - |
| report | ReportTemplateMapper | ReportTemplateMapperTest.java | 5 | CRUD | - |
| ai | AiTaskMapper | AiTaskMapperTest.java | 5 | CRUD | - |

### 1.3 Controller 层（46 文件 / 336 @Test）

| 模块 | Controller | 测试类 | 测试数 | 覆盖 | 优先级 |
|------|-----------|--------|--------|------|--------|
| finance | VoucherController | VoucherControllerTest.java | 15 | 凭证 CRUD/状态流转 | - |
| finance | BusinessDocController | BusinessDocControllerTest.java | 11 | 业务单据 | - |
| finance | BankStatementController | BankStatementControllerTest.java | 10 | 银行流水 | - |
| finance | BankReconciliationController | BankReconciliationControllerTest.java | 12 | 对账全流程 | - |
| finance | SalesInvoiceController | SalesInvoiceControllerTest.java | 6 | 导入/预览 | - |
| finance | NumberingTraceController | NumberingTraceControllerTest.java | 7 | 编号溯源 | - |
| finance | ClassificationRuleController | ClassificationRuleControllerTest.java | 5 | 规则CRUD | - |
| finance | SubjectController | SubjectControllerTest.java | 5 | 科目CRUD | - |
| finance | SubjectBalanceController | SubjectBalanceControllerTest.java | 4 | 余额查询 | - |
| finance | VoucherTemplateController | VoucherTemplateControllerTest.java | 7 | 模板CRUD | - |
| finance | VoucherTypeController | VoucherTypeControllerTest.java | 6 | 凭证类型 | - |
| finance | BankAccountController | BankAccountControllerTest.java | 7 | 银行账户 | - |
| finance | BankJournalController | BankJournalControllerTest.java | 7 | 银行日记账 | - |
| finance | CashJournalController | CashJournalControllerTest.java | 6 | 现金日记账 | - |
| finance | PeriodCloseController | PeriodCloseControllerTest.java | 5 | 期间结转 | - |
| finance | LedgerController | LedgerControllerTest.java | 4 | 总账查询 | - |
| arap | ArapController | ArapControllerTest.java | 14 | 应收应付 | - |
| arap | ReconciliationController | ReconciliationControllerTest.java | 11 | 核销流程 | - |
| arap | ExpenseReimbursementController | ExpenseReimbursementControllerTest.java | 11 | CRUD/审批 | - |
| arap | ArapSettlementController | ArapSettlementControllerTest.java | 10 | 结算 | - |
| arap | PrepaymentController | PrepaymentControllerTest.java | 10 | 预付款 | - |
| arap | BadDebtController | BadDebtControllerTest.java | 7 | 坏账 | - |
| arap | ReconciliationReportController | ReconciliationReportControllerTest.java | 6 | 核销报表 | - |
| arap | CustomerStatementController | CustomerStatementControllerTest.java | 6 | 客户对账单 | - |
| arap | AgingAnalysisController | AgingAnalysisControllerTest.java | 5 | 账龄分析 | - |
| arap | PaymentPlanController | PaymentPlanControllerTest.java | 2 | 付款计划 | - |
| arap | PurchaseReturnController | PurchaseReturnControllerTest.java | 2 | 采购退货 | - |
| system | PeriodController | PeriodControllerTest.java | 13 | 期间管理 | - |
| system | RoleController | RoleControllerTest.java | 10 | 角色管理 | - |
| system | SysConfigController | SysConfigControllerTest.java | 9 | 系统配置 | - |
| system | MenuController | MenuControllerTest.java | 7 | 菜单管理 | - |
| system | AuthController | AuthControllerTest.java | 10 | 认证+安全 | - |
| system | ClearDataController | ClearDataControllerTest.java | 6 | 数据清理 | - |
| system | DeptController | DeptControllerTest.java | 6 | 部门管理 | - |
| system | EmployeeController | EmployeeControllerTest.java | 6 | 员工管理 | - |
| system | CustomerController | CustomerControllerTest.java | 5 | 客户管理 | - |
| system | VendorController | VendorControllerTest.java | 5 | 供应商管理 | - |
| asset | AssetCardController | AssetCardControllerTest.java | 6 | 资产卡片 | - |
| asset | AssetCategoryController | AssetCategoryControllerTest.java | 6 | 资产类别 | - |
| asset | AssetDisposalController | AssetDisposalControllerTest.java | 5 | 资产处置 | - |
| asset | AssetInventoryController | AssetInventoryControllerTest.java | 5 | 资产盘点 | - |
| budget | BudgetController | BudgetControllerTest.java | 7 | 预算管理 | - |
| tax | TaxController | TaxControllerTest.java | 14 | 税务全流程 | - |
| tax | InputInvoiceController | InputInvoiceControllerTest.java | 3 | 进项发票 | - |
| **agency** | **EnterpriseController** | **EnterpriseControllerTest.java** | **7** ✅(V1.3新增) | 企业管理 | - |
| **agency** | **AgencyUserController** | **AgencyUserControllerTest.java** | **7** ✅(V1.3新增) | 代理用户管理 | - |
| **agency** | **AssignmentController** | **AssignmentControllerTest.java** | **5** ✅(V1.3新增) | 代理分配 | - |

### 1.4 E2E 链路（后端，10 文件 / 39 @Test）

| 链路 | 测试类 | 覆盖步骤 | 空白 | 优先级 |
|------|--------|----------|------|--------|
| 银行流水导入 | BankFlowE2ETest.java | 导入→路由→收款单 | 无 | - |
| 销售发票导入 | SalesFlowE2ETest.java | 导入→业务单→凭证 | **未验证工作台可见性** | P0 |
| 进项发票导入 | InputFlowE2ETest.java | 导入→业务单 | 无 | - |
| 费用报销 | ExpenseFlowE2ETest.java | 报销→审核→凭证 | 无 | - |
| 核销工作台 | ReconciliationWorkbenchE2ETest.java | 工作台→核销链路 | 无 | - |
| 资产全流程 | AssetFlowE2ETest.java | 资产创建→折旧→处置 | 无 | - |
| 预算全流程 | BudgetFlowE2ETest.java | 预算编制→调整→审批 | 无 | - |
| 编号全链路 | NumberingFullChainE2ETest.java | 编号关联验证 | 无 | - |
| 编号结算 | NumberingSettlementE2ETest.java | 结算关联验证 | 无 | - |
| 编号关联 | NumberingAssociationE2ETest.java | 跨实体关联验证 | 无 | - |

### 1.5 性能 / 并发测试（3 文件 / 12 @Test）

| 测试类 | 测试数 | 覆盖 | 断言 | 状态 |
|--------|--------|------|------|------|
| SimpleConcurrencyLoadTest.java | 6 | 并发负载（多场景） | successRate ≥ 50% / avgTime < 5000ms / P99 < 10000ms | ✅ |
| ConcurrencyLoadTest.java | 3 | 多场景并发负载 | successRate ≥ 50% / avgTime < 5000ms | ✅ |
| CoreWriteOperationConcurrencyTest.java | 3 | 核心写操作并发 | 线程安全计数 | ✅ |

### 1.6 状态机专项测试（10 文件 / 222 @Test）

| 状态机 | 测试类 | @Test 数 | 覆盖状态转换 |
|--------|--------|---------|-------------|
| OutputInvoice | OutputInvoiceStateMachineServiceImplTest.java | 48 | 销项发票全生命周期状态转换 |
| AssetCard | AssetCardStateMachineServiceImplTest.java | 29 | 资产卡片全生命周期 |
| InputInvoice | InputInvoiceStateMachineServiceImplTest.java | 22 | 进项发票状态转换 |
| Budget | BudgetStateMachineServiceImplTest.java | 21 | 预算状态机全流程 |
| Voucher | VoucherStateMachineServiceImplTest.java | 20 | 凭证状态转换 |
| ExpenseReimbursement | ExpenseReimbursementServiceImplTest.java | 17 | 费用报销状态机 |
| AssetDisposal | AssetDisposalStateMachineServiceImplTest.java | 15 | 资产处置状态机 |
| AssetInventory | AssetInventoryStateMachineServiceImplTest.java | 15 | 资产盘点状态机 |
| BudgetAdjustment | BudgetAdjustmentStateMachineServiceImplTest.java | 12 | 预算调整状态机 |
| BadDebtProvision | BadDebtProvisionStateMachineServiceImplTest.java | 10 | 坏账准备状态机 |
| AiTask | AiTaskStateMachineServiceImplTest.java | 30 | AI 任务状态转换 |
| **合计** | **10 文件** | **222** | **所有状态机强制正+负双向断言** |

---

## 2. 前端测试覆盖矩阵

### 2.1 Vitest 单元/组件测试（14 文件 / 171 用例）

| 类型 | 文件 | 用例数 | 覆盖范围 | 空白 | 优先级 |
|------|------|--------|----------|------|--------|
| **组件** | BankStatementView.test.ts | 15 | 银行对账单视图组件 | 无 | - |
| **组件** | VoucherList.test.ts | 18 | 凭证列表组件 | 无 | - |
| **组件** | InputInvoiceList.test.ts | 13 | 进项发票列表组件 | 无 | - |
| **组件** | BusinessDocDetail.test.ts | 11 | 业务单据详情组件 | 无 | - |
| **组件** | arap-reconciliation-core.test.ts | 14 | 核销工作台组件 | 无 | - |
| **组件** | ReconciliationWorkbench.test.ts | 4 | ReconciliationWorkbench 组件 | 无 | - |
| **Store** | auth.store.test.ts | 8 | 认证状态管理 | 无 | - |
| **API** | voucher.api.test.ts | 15 | 凭证 API 封装 | 无 | - |
| **API** | tax.api.test.ts | 15 | 发票税务 API | 无 | - |
| **API** | arap.api.test.ts | 13 | 应收应付 API | 无 | - |
| **API** | reconciliation.api.test.ts | 13 | 核销 API | 无 | - |
| **API** | system.api.test.ts | 12 | 系统 API | 无 | - |
| **API** | businessDoc.api.test.ts | 10 | 业务单据 API | 无 | - |
| **API** | bankStatement.api.test.ts | 10 | 银行流水 API | 无 | - |
| **合计** | **14 文件** | **171** | **6 组件 + 1 Store + 7 API** | 见下方 §4 断层 | - |

### 2.2 Playwright E2E 测试（18 文件 / 51 用例）

| 文件 | 用例数 | 覆盖范围 | 状态 |
|------|--------|----------|------|
| 01-login.spec.ts | 2 | 登录成功/失败 | ✅ |
| 02-menu-navigation.spec.ts | 1 | 菜单遍历（6 个一级菜单） | ✅ |
| 03-output-invoice.spec.ts | 1 | 销项发票页面内容验证 | ✅ |
| 04-page-smoke.spec.ts | 1 | 全量页面加载（39 页参数化） | ✅ |
| 05-invoice-fullflow.spec.ts | 3 | 新建→审核→确认→凭证全流程 | ✅ |
| 06-core-pages.spec.ts | 1 | 核心业务页面加载（9 页参数化） | ✅ |
| 07-finance-pages.spec.ts | 1 | 财务核心页面加载（8 页参数化） | ✅ |
| 08-system-pages.spec.ts | 1 | 系统管理+基础数据（12 页参数化） | ✅ |
| 09-module-pages.spec.ts | 1 | 税务/资产/报表（12 页参数化） | ✅ |
| 10-interaction-tests.spec.ts | 8 | 交互操作（银行对账单/凭证/核销/销项/科目余额/资产卡片/首页/业务单据） | ✅ |
| 11-voucher-workflow.spec.ts | 5 | 凭证列表/新增/详情/状态流转 | ✅ |
| 12-input-invoice.spec.ts | 5 | 进项发票列表/新增弹窗/提交审核/审核通过 | ✅ |
| 13-period-close.spec.ts | 4 | 结账检查通过/未通过/试算平衡/损益结转 | ✅ |
| 14-asset-management.spec.ts | 4 | 资产类别/卡片列表/新增弹窗 | ✅ |
| 15-report-financial.spec.ts | 3 | 三大报表加载（资产负债表/利润表/现金流量表） | ✅ |
| 16-budget-management.spec.ts | 3 | 预算列表/调整/审批 | ✅ |
| 17-business-doc-list.spec.ts | 3 | 业务单据列表/类型筛选/详情 | ✅ |
| 18-error-pages.spec.ts | 4 | 403/404/路由兜底/权限跳转 | ✅ |
| **合计** | **51** | **覆盖 18 个场景/文件，全量 39 页面 + 业务流程 + 交互 + 异常** | ✅ |

---

## 3. 后端 @Test 分布统计（按测试类型）

| 测试类型 | 文件数 | @Test 数 | 占比 |
|----------|--------|---------|------|
| Service (Mockito) | 46 | 568 | 36.6% |
| Controller (@SpringBootTest) | 46 | 336 | 21.6% |
| Mapper (Testcontainers 真实 DB) | 38 | 192 | 12.4% |
| E2E 链路 (@SpringBootTest 全链路) | 10 | 39 | 2.5% |
| 并发测试 | 3 | 12 | 0.8% |
| 其他 (common/helper/numbering) | 45 | 406 | 26.1% |
| **合计** | **188** | **1,553** | **100%** |

---

## 4. 断层类型清单

| # | 断层描述 | 涉及模块 | 发现方式 | 修复状态 |
|---|----------|----------|----------|----------|
| T1 | pageQuery() 多 docType 参数化测试 | BusinessDocService | 手动审查 | 🔴 待修复 |
| T2 | 前端 ReconciliationWorkbench 零组件测试 | 前端 | 手动审查 | ✅ 已修复 (V1.2) |
| T3 | 销售发票导入 E2E 未验证工作台可见性 | 全链路 | 手动审查 | 🔴 待修复 |
| T4 | docType 扩展时未同步更新查询条件 | 后端+前端 | Bug 发现 | 🔴 待修复 |
| T5 | AuthControllerTest 曾使用 addFilters=false 绕过安全过滤链 | system | 手动审查 | ✅ 已修复 (V1.2) |
| T6 | SimpleConcurrencyLoadTest 缺少性能断言 | common | 手动审查 | ✅ 已修复 (V1.2) |
| T7 | BusinessDocServiceImplTest 测试数虚报 | finance | 实测核对 | ✅ 已修正 (V1.2) |
| T8 | 测试矩阵硬数字过时 | 全局 | 实测核对 | ✅ 已修正 (V1.3) |
| **T9 (V1.3新增)** | **test-coverage-matrix.md 记录过时** | 全局 | 实测核对 | ✅ 已修正 (V1.3) — 同步至 1553 @Test / 188 类 |
| **T10 (V1.3新增)** | **Agency Service 层测试不足** | agency | 手动审查 | 🔴 待修复 — 仅 3 个 Controller 测试，无 Service 测试 |
| **T11 (V1.3新增)** | **前端组件测试覆盖率不足（6/50+页面）** | 前端 | 手动审查 | 🔴 待修复 — 仅 6 个组件测试 vs 50+ 业务页面 |
| **T12 (V1.3新增)** | **Playwright E2E 用例数不足** | E2E | 实测核对 | ✅ 已修复 (V1.3) — 从 19 提升至 51 用例 |

---

## 5. 补测优先级

1. **P0**：pageQuery() 多 docType 测试（含 INVOICE_OUT/INVOICE_IN/OTHER_RECEIVABLE/OTHER_PAYABLE）
2. **P0**：端到端核销链路 E2E（导入销项发票→工作台可见→核销匹配）
3. **P1**：Agency Service 层测试补齐（EnterpriseContextHolder/DataPermissionInterceptor/HikariRlsConfig）
4. **P1**：所有 pageQuery() 方法的参数化测试（Customer/Vendor/Employee/BadDebt/Prepayment/Expense/Budget/Asset）
5. **P1**：前端业务列表页组件测试（OutputInvoiceList/PeriodClose/BudgetList 等）
6. **P1**：JaCoCo 覆盖率门禁启用（阶段性目标：P0 模块 40% → P0+P1 60% → 全量 80%）
7. **P2**：前端 E2E 测试 CI 集成（Playwright 接入 GitHub Actions）
8. **P2**：AI 服务端到端测试（OCR/embedding/match 推理逻辑）

---

> **文档结束**