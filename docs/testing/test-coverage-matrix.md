# 测试覆盖矩阵 HUICAI-TST-001

> **编号**：HUICAI-TST-001
> **版本**：V1.1 | **修改日期**：2026-07-18 | **修改人**：Hermes
> **目的**：记录各模块测试覆盖状态，标识空白区域，驱动补测优先级

---

## 1. 后端测试覆盖矩阵

### 1.1 Service 层

| 模块 | 类 | 测试类 | 测试数 | 覆盖方法 | 空白 | 优先级 |
|------|-----|--------|--------|----------|------|--------|
| finance | BusinessDocServiceImpl | BusinessDocServiceImplTest.java | 31 | update(10), getDetail(5), generateVoucher(5), pageQuery(5) | **pageQuery() 多docType** | P0 |
| finance | SalesInvoiceImportServiceImpl | SalesInvoiceImportServiceTest.java | 11 | preview, import, validate, batch | 无 | - |
| finance | BankStatementServiceImpl | BankStatementServiceTest.java | 37 | parse, validate, route, classify, match | 无 | - |
| finance | BankReconciliationServiceImpl | BankReconciliationServiceImplTest.java | 20 | runMatching, score, autoReconcile | 无 | - |
| finance | ClassificationRuleServiceImpl | ClassificationRuleServiceTest.java | 21 | classify, autoCreate, ruleEval | 无 | - |
| finance | ClassificationRuleServiceImpl | ClassificationRuleServiceImplTest.java | 7 | rule CRUD | 无 | - |
| finance | FallbackHeuristicServiceImpl | FallbackHeuristicServiceTest.java | 31 | fallback, heuristic, resolve | 无 | - |
| finance | AutoGenerationServiceImpl | AutoGenerationServiceTest.java | 23 | autoGen, voucherCreate | 无 | - |
| finance | VoucherStateMachineServiceImpl | VoucherStateMachineServiceImplTest.java | 12 | 状态转换 | 无 | - |
| finance | ColumnMappingResolverImpl | ColumnMappingResolverTest.java | 12 | 列映射解析 | 无 | - |
| finance | BankAccountServiceImpl | BankAccountServiceImplTest.java | 7 | CRUD | 无 | - |
| finance | TicketServiceImpl | TicketServiceImplTest.java | 6 | 票据处理 | 无 | - |
| finance | CashJournalServiceImpl | CashJournalServiceImplTest.java | 3 | CRUD | 无 | - |
| finance | TemplateMatcherImpl | TemplateMatcherTest.java | 6 | 模板匹配 | 无 | - |
| finance | VoucherEntryValidation | VoucherEntryValidationTest.java | 6 | 分录校验 | 无 | - |
| finance | VoucherNoFormat | VoucherNoFormatTest.java | 4 | 编号格式 | 无 | - |
| finance | TrialBalance | TrialBalanceTest.java | 4 | 试算平衡 | 无 | - |
| finance | CounterpartyExtractor | CounterpartyExtractorTest.java | 10 | 对方科目提取 | 无 | - |
| arap | ReconciliationServiceImpl | ReconciliationServiceImplTest.java | 43 | recommend, execute, match, settle | 无 | - |
| arap | ReconciliationToleranceServiceImpl | ReconciliationToleranceServiceImplTest.java | 12 | tolerance, rule | 无 | - |
| arap | ExpenseReimbursementServiceImpl | ExpenseReimbursementServiceImplTest.java | 18 | 状态机全流程 | 无 | - |
| arap | BadDebtProvisionStateMachineServiceImpl | BadDebtProvisionStateMachineServiceImplTest.java | 4 | 状态转换 | 无 | - |
| arap | EmployeeServiceImpl | EmployeeServiceImplTest.java | 11 | pageQuery, CRUD | 无 | - |
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
| tax | TaxServiceImpl | TaxServiceImplTest.java | 12 | 发票/申报 | 无 | - |
| tax | OutputInvoiceStateMachineServiceImpl | OutputInvoiceStateMachineServiceImplTest.java | 45 | 状态转换全面 | 无 | - |
| tax | InputInvoiceStateMachineServiceImpl | InputInvoiceStateMachineServiceImplTest.java | 22 | 进项状态转换 | 无 | - |
| budget | BudgetServiceImpl | BudgetServiceImplTest.java | 5 | CRUD/审批 | 无 | - |
| budget | BudgetAdjustmentStateMachineServiceImpl | BudgetAdjustmentStateMachineServiceImplTest.java | 4 | 状态转换 | 无 | - |
| asset | AssetCategoryServiceImpl | AssetCategoryServiceImplTest.java | 7 | CRUD | 无 | - |
| asset | AssetDisposalServiceImpl | AssetDisposalServiceImplTest.java | 3 | 处置 | 无 | - |
| ai | AiTaskStateMachineServiceImpl | AiTaskStateMachineServiceImplTest.java | 12 | 状态转换 | 无 | - |
| ai | AiFeedbackLogServiceImpl | AiFeedbackLogServiceImplTest.java | 4 | 反馈日志 | 无 | - |
| report | ReportServiceImpl | ReportServiceImplTest.java | 5 | CRUD | 无 | - |
| storage | AttachmentService | AttachmentServiceTest.java | 3 | 附件管理 | 无 | - |
| common | AmountExpressionResolver | AmountExpressionResolverTest.java | 12 | 金额表达式 | 无 | - |
| common | TemplateEngine | TemplateEngineTest.java | 13 | 模板引擎 | 无 | - |

### 1.2 Mapper 层（真实 DB）

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

### 1.3 Controller 层

| 模块 | Controller | 测试类 | 测试数 | 覆盖 | 优先级 |
|------|-----------|--------|--------|------|--------|
| finance | SalesInvoiceController | SalesInvoiceControllerTest.java | 6 | 导入/预览 | - |
| finance | BankReconciliationController | BankReconciliationControllerTest.java | 12 | 对账全流程 | - |
| finance | NumberingTraceController | NumberingTraceControllerTest.java | 7 | 编号溯源 | - |
| finance | ClassificationRuleController | ClassificationRuleControllerTest.java | 5 | 规则CRUD | - |
| finance | SubjectController | SubjectControllerTest.java | 5 | 科目CRUD | - |
| finance | SubjectBalanceController | SubjectBalanceControllerTest.java | 4 | 余额查询 | - |
| finance | VoucherTemplateController | VoucherTemplateControllerTest.java | 7 | 模板CRUD | - |
| finance | VoucherTypeController | VoucherTypeControllerTest.java | 6 | 凭证类型 | - |
| arap | ReconciliationController | ReconciliationControllerTest.java | 11 | 核销流程 | - |
| arap | ExpenseReimbursementController | ExpenseReimbursementControllerTest.java | 11 | CRUD/审批 | - |
| arap | ArapController | ArapControllerTest.java | 14 | 应收应付 | - |
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
| system | ClearDataController | ClearDataControllerTest.java | 6 | 数据清理 | - |
| system | DeptController | DeptControllerTest.java | 6 | 部门管理 | - |
| system | EmployeeController | EmployeeControllerTest.java | 6 | 员工管理 | - |
| system | AuthController | AuthControllerTest.java | 5 | 认证 | - |
| system | CustomerController | CustomerControllerTest.java | 5 | 客户管理 | - |
| system | VendorController | VendorControllerTest.java | 5 | 供应商管理 | - |
| asset | AssetCardController | AssetCardControllerTest.java | 6 | 资产卡片 | - |

### 1.4 E2E 链路

| 链路 | 测试类 | 覆盖步骤 | 空白 | 优先级 |
|------|--------|----------|------|--------|
| 银行流水导入 | BankFlowE2ETest.java | 导入→路由→收款单 | 无 | - |
| 销售发票导入 | SalesFlowE2ETest.java | 导入→业务单→凭证 | **未验证工作台可见性** | P0 |
| 进项发票导入 | InputFlowE2ETest.java | 导入→业务单 | 无 | - |
| 费用报销 | ExpenseFlowE2ETest.java | 报销→审核→凭证 | 无 | - |
| 编号全链路 | NumberingFullChainE2ETest.java | 编号关联验证 | 无 | - |
| 编号结算 | NumberingSettlementE2ETest.java | 结算关联验证 | 无 | - |
| 编号前端API | NumberingFrontendApiTest.java | 前端API验证 | 无 | - |
| 编号关联 | NumberingAssociationE2ETest.java | 跨实体关联验证 | 无 | - |
| **核销工作台** | ReconciliationWorkbenchE2ETest.java | 工作台→核销链路 | 无 | - |

---

## 2. 前端测试覆盖矩阵

| 类型 | 文件 | 覆盖范围 | 空白 | 优先级 |
|------|------|----------|------|--------|
| Unit | auth.store.test.ts | 认证状态管理 | 无 | - |
| Unit | system.api.test.ts | API 封装 | 无 | - |
| **空白** | **ReconciliationWorkbench.vue** | **零测试** | **组件级** | **P0** |
| **空白** | **BusinessDocList.vue** | 未知 | 列表页 | P1 |
| E2E | login.spec.ts | 登录 | 无 | - |
| E2E | bank-statement-import.spec.ts | 银行对账单导入 | 无 | - |
| E2E | sales-invoice-import.spec.ts | 销售发票导入 | **未验证下游** | P0 |
| **空白** | **核销工作台 E2E** | **无** | **完整链路** | **P0** |

---

## 3. 断层类型清单

| # | 断层描述 | 涉及模块 | 发现方式 | 修复状态 |
|---|----------|----------|----------|----------|
| T1 | pageQuery() 多 docType 参数化测试 | BusinessDocService | 手动审查 | 🔴 待修复 |
| T2 | 前端 ReconciliationWorkbench 零组件测试 | 前端 | 手动审查 | 🔴 待修复 |
| T3 | 销售发票导入 E2E 未验证工作台可见性 | 全链路 | 手动审查 | 🔴 待修复 |
| T4 | docType 扩展时未同步更新查询条件 | 后端+前端 | Bug 发现 | 🔴 待修复 |

---

## 4. 补测优先级

1. **P0**：pageQuery() 多 docType 测试（含 INVOICE_OUT/INVOICE_IN/OTHER_RECEIVABLE/OTHER_PAYABLE）
2. **P0**：端到端核销链路 E2E（导入销项发票→工作台可见→核销匹配）
3. **P0**：前端 ReconciliationWorkbench 组件测试（tab 切换、docTypes 过滤）
4. **P1**：所有 pageQuery() 方法的参数化测试（Customer/Vendor/Employee/BadDebt/Prepayment/Expense/Budget/Asset）
5. **P1**：前端业务列表页组件测试

---

> **文档结束**