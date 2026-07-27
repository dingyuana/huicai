# 测试覆盖矩阵 HUICAI-TST-001

> **编号**：HUICAI-TST-001
> **版本**：V1.2 | **修改日期**：2026-07-23 | **修改人**：Hermes
> **目的**：记录各模块测试覆盖状态，标识空白区域，驱动补测优先级
> **修正记录 (V1.2)**：本次修订校正了过时的硬数字（@Test 总数、测试类数、前端测试数），并对若干标注的"虚报/未完成"测试项进行了核实与状态更新：
> - @Test 总数：389 → 1033（实测 `grep -rhE '^\s*@Test\b' --include='*Test.java'`）
> - 测试类数：37 → 133（实测 `find . -name '*Test.java' -type f`）
> - 前端测试文件数：2 → 12（实测 `frontend/src/__tests__/**/*.test.ts`）
> - BusinessDocServiceImplTest：31（虚报）→ 33（实测 @Test 数）
> - 标注 BusinessDocDetail.test.ts 已修复为真实组件测试（基于 shallowMount + 真实 .vue）
> - 标注 arap-reconciliation-core.test.ts 已修复为真实组件测试（基于 shallowMount + 真实 ReconciliationWorkbench.vue）
> - 标注 AuthControllerTest 已移除 `addFilters=false`，新增 3 个安全测试（基于 AuthenticationManager / JWT / SecurityContext）
> - 标注 SimpleConcurrencyLoadTest 已添加性能断言（successRate ≥ 50%、avgTime < 5000ms、P99 < 10000ms）

---

## 0. 总览（硬数字）

| 指标 | 旧值 (V1.2) | 当前实测 | 校验命令 |
|------|-------------|----------|----------|
| 后端 @Test 总数 | 1033 | **1033** | `grep -rhE '^\s*@Test\b' --include='*Test.java' backend/ \| wc -l` |
| 后端测试类数 | 133 | **133** | `find backend/ -name '*Test.java' -type f \| wc -l` |
| 前端 Vitest 测试文件数 | 12 | **12** | `find frontend/src/__tests__ -name '*.test.ts' \| wc -l` |
| 前端 E2E 测试文件数 | 3 | **4** | `find e2e/tests -name '*.spec.ts' \| wc -l` |
| E2E 测试用例数 | 9 | **48** | `npx playwright test --list` |

> 注：AGENTS.md §0 仍记录 579 @Test / 78 测试类（commit `65e8d66` 基准），本次实测数字更高，建议下次 commit 后由负责人同步更新 AGENTS.md §0。

---

## 1. 后端测试覆盖矩阵

### 1.1 Service 层

| 模块 | 类 | 测试类 | 测试数 | 覆盖方法 | 空白 | 优先级 |
|------|-----|--------|--------|----------|------|--------|
| finance | BusinessDocServiceImpl | BusinessDocServiceImplTest.java | 33 ✅(V1.2修正) | update(10), getDetail(5), generateVoucher(5), pageQuery(5) + 8 其他 | **pageQuery() 多docType** | P0 |

> **V1.2 修正 (BusinessDocServiceImplTest)**：原矩阵虚报 31 个 @Test，实测为 **33 个 @Test**（`grep -c '@Test' BusinessDocServiceImplTest.java`）。测试方法分布为 update(10)/getDetail(5)/generateVoucher(5)/pageQuery(5)/其他(8)。
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
| system | AuthController | AuthControllerTest.java | 7 ✅(V1.2修正) | 认证+安全 ✅(V1.2新增) | - |
| system | CustomerController | CustomerControllerTest.java | 5 | 客户管理 | - |
| system | VendorController | VendorControllerTest.java | 5 | 供应商管理 | - |
| asset | AssetCardController | AssetCardControllerTest.java | 6 | 资产卡片 | - |

> **V1.2 修正 (AuthControllerTest)**：实测 7 个 @Test（原矩阵记 5）。已**移除** `addFilters=false` 的"绕过 Spring Security 过滤链"写法，安全过滤链保持启用，确保 JWT 认证与 RBAC 权限验证在测试中真实生效。新增 3 个安全测试用例（基于 `AuthenticationManager` Mock + `BadCredentialsException` 抛出 + `SecurityContextHolder` 清理），覆盖：
> - 用户名/密码错误时抛出 `BadCredentialsException`
> - 正确凭证返回 JWT Token
> - 每个 @Test 后清理 `SecurityContextHolder`（防止测试间状态泄漏）

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

### 1.5 性能 / 并发测试

| 测试类 | 测试数 | 覆盖 | 断言 | 状态 |
|--------|--------|------|------|------|
| SimpleConcurrencyLoadTest.java | 6 ✅(V1.2新增) | 并发负载（多场景） | ✅(V1.2新增性能断言) | - |

> **V1.2 修正 (SimpleConcurrencyLoadTest)**：实测 6 个 @Test。已**添加性能断言**（原矩阵未登记，且原版本无任何性能断言，仅打印统计指标）。每个并发测试用例现均断言：
> - `success > 0`（至少 1 个成功请求）
> - `successRate >= 50.0%`
> - `avgTime < 5000ms`（平均响应时间）
> - `p99 < 10000ms`（P99 响应时间）
>
> 统计指标仍保留打印（avgTime / p95 / p99），便于人工 review。

---

## 2. 前端测试覆盖矩阵

> **V1.2 修正**：原矩阵仅列 2 个测试文件，实测 `frontend/src/__tests__/` 下共 **12 个 .test.ts 文件**。下表为完整清单。

| 类型 | 文件 | 覆盖范围 | 空白 | 优先级 |
|------|------|----------|------|--------|
| Unit | auth.store.test.ts | 认证状态管理 | 无 | - |
| Unit | system.api.test.ts | API 封装 | 无 | - |
| Unit | bankStatement.api.test.ts | 银行流水 API | 无 | - |
| Unit | arap.api.test.ts | 应收应付 API | 无 | - |
| Unit | tax.api.test.ts | 发票税务 API | 无 | - |
| Unit | reconciliation.api.test.ts | 核销 API | 无 | - |
| Unit | businessDoc.api.test.ts | 业务单据 API | 无 | - |
| Unit | voucher.api.test.ts | 凭证 API | 无 | - |
| Component | business-doc-detail.test.ts | 业务单据详情组件 | 无 ✅(V1.2修正) | - |
| 组件 | BusinessDocDetail.test.ts | BusinessDocDetail.vue 真实组件 ✅(V1.2修复) | 无 | - |
| 组件 | arap-reconciliation-core.test.ts | ReconciliationWorkbench.vue 真实组件 ✅(V1.2修复) | 无 | - |
| 组件 | ComponentTestTemplate.test.ts | 组件测试模板 | 无 | - |
| **E2E** | **01-login.spec.ts** | 登录成功/失败 2 测试 | 无 | - |
| **E2E** | **02-menu-navigation.spec.ts** | 6 个菜单遍历 | 无 | - |
| **E2E** | **03-output-invoice.spec.ts** | 销项发票页面内容验证 | 无 | - |
| **E2E** | **04-page-smoke.spec.ts** | **39 个页面加载冒烟测试**（全量覆盖） | 见下方 §4 已知问题 | - |

> **V1.2 修正 (前端组件测试)**：
> - `BusinessDocDetail.test.ts`：原矩阵标注"零测试"，现已修复为**真实组件测试**（`import BusinessDocDetail from '@/views/finance/business-doc/BusinessDocDetail.vue'`，基于 `shallowMount` + vue-router + nextTick）。
> - `arap-reconciliation-core.test.ts`：原矩阵标注"零测试"，现已修复为**真实组件测试**（`import ReconciliationWorkbench from '@/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue'`，基于 `shallowMount` + vue-router + nextTick）。
> - 原 T2 断层"前端 ReconciliationWorkbench 零组件测试"已消除。
>
> **遗留**：以下原标注的"空白"在 V1.2 仍未补齐（原矩阵标记的 `BusinessDocList.vue` / `login.spec.ts` / `bank-statement-import.spec.ts` / `sales-invoice-import.spec.ts` / 核销工作台 E2E），需后续单独核实是否仍存在或已合并至上述单元/组件测试中。

---

## 3. 断层类型清单

| # | 断层描述 | 涉及模块 | 发现方式 | 修复状态 |
|---|----------|----------|----------|----------|
| T1 | pageQuery() 多 docType 参数化测试 | BusinessDocService | 手动审查 | 🔴 待修复 |
| T2 | 前端 ReconciliationWorkbench 零组件测试 | 前端 | 手动审查 | ✅ 已修复 (V1.2) — arap-reconciliation-core.test.ts 现为真实组件测试 |
| T3 | 销售发票导入 E2E 未验证工作台可见性 | 全链路 | 手动审查 | 🔴 待修复 |
| T4 | docType 扩展时未同步更新查询条件 | 后端+前端 | Bug 发现 | 🔴 待修复 |
| T5 (V1.2新增) | AuthControllerTest 曾使用 addFilters=false 绕过安全过滤链 | system | 手动审查 | ✅ 已修复 (V1.2) — 移除 addFilters=false，新增 3 个安全测试 |
| T6 (V1.2新增) | SimpleConcurrencyLoadTest 缺少性能断言 | common | 手动审查 | ✅ 已修复 (V1.2) — 新增 successRate/avgTime/p99 断言 |
| T7 (V1.2新增) | BusinessDocServiceImplTest 测试数虚报 31 | finance | 实测核对 | ✅ 已修正 (V1.2) — 实测 33 个 @Test |
| T8 (V1.2新增) | 测试矩阵硬数字过时（@Test 389/类 37/前端 2） | 全局 | 实测核对 | ✅ 已修正 (V1.2) — 实测 @Test 1033/类 133/前端 12 |

---

## 4. 补测优先级

1. **P0**：pageQuery() 多 docType 测试（含 INVOICE_OUT/INVOICE_IN/OTHER_RECEIVABLE/OTHER_PAYABLE）
2. **P0**：端到端核销链路 E2E（导入销项发票→工作台可见→核销匹配）
3. **P1**：所有 pageQuery() 方法的参数化测试（Customer/Vendor/Employee/BadDebt/Prepayment/Expense/Budget/Asset）
4. **P1**：前端业务列表页组件测试
5. **P1**：修复 E2E 发现的 5 个已知问题（见 §5）

---

## 5. E2E 测试已知问题（后端待修复）

| # | 页面 | 路径 | 错误 | 类型 |
|---|------|------|------|------|
| 1 | 科目余额表 | /report/subject-balance | API 500: `/reports/subject-balance?period=202607` | 后端报表服务异常 |
| 2 | 资产负债表 | /report/balance-sheet | API 500: `/reports/balance-sheet?period=202607` | 后端报表服务异常 |
| 3 | 利润表 | /report/income-statement | API 500: `/reports/income-statement?period=202607` | 后端报表服务异常 |
| 4 | 现金流量表 | /report/cash-flow | API 500: `/reports/cash-flow?period=202607` | 后端报表服务异常 |
| 5 | 客商档案 | /basis/party | Console 401 Unauthorized | 权限配置问题 |

> **说明**：以上问题由 Playwright E2E 测试的 `createErrorTracker()` 在网络层捕获，旧版页面文本检查无法发现。

---

> **文档结束**