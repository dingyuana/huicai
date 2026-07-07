# 测试覆盖矩阵 HUICAI-TST-001

> **编号**：HUICAI-TST-001
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **目的**：记录各模块测试覆盖状态，标识空白区域，驱动补测优先级

---

## 1. 后端测试覆盖矩阵

### 1.1 Service 层

| 模块 | 类 | 测试类 | 测试数 | 覆盖方法 | 空白 | 优先级 |
|------|-----|--------|--------|----------|------|--------|
| finance | BusinessDocServiceImpl | BusinessDocServiceImplTest.java | 18 | update(10), getDetail(5), generateVoucher(5) | **pageQuery()** | P0 |
| finance | SalesInvoiceImportServiceImpl | SalesInvoiceImportServiceTest.java | 5+ | preview, import, validate | 无 | - |
| finance | BankStatementServiceImpl | BankStatementServiceTest.java | 5+ | parse, validate, route | 无 | - |
| finance | BankReconciliationServiceImpl | BankReconciliationServiceImplTest.java | 5+ | runMatching, score | 无 | - |
| finance | ClassificationRuleServiceImpl | ClassificationRuleServiceTest.java | 5+ | classify, autoCreate | 无 | - |
| arap | ReconciliationServiceImpl | ReconciliationServiceImplTest.java | 5+ | recommend, execute | 无 | - |
| arap | ExpenseReimbursementServiceImpl | ExpenseReimbursementServiceImplTest.java | 5+ | 状态机 | 无 | - |
| arap | BadDebtProvisionStateMachineServiceImpl | BadDebtProvisionStateMachineServiceImplTest.java | 5+ | 状态转换 | 无 | - |
| arap | EmployeeServiceImpl | EmployeeServiceImplTest.java | 3+ | pageQuery | 无 | - |
| arap | VendorServiceImpl | VendorServiceImplTest.java | 3+ | pageQuery | 无 | - |
| arap | CustomerServiceImpl | CustomerServiceImplTest.java | 3+ | pageQuery | 无 | - |
| system | PeriodServiceImpl | PeriodServiceImplTest.java | 5+ | open/close/check | 无 | - |
| system | SubjectServiceImpl | SubjectServiceImplTest.java | 5+ | CRUD | 无 | - |
| tax | TaxServiceImpl | TaxServiceImplTest.java | 5+ | 发票/申报 | 无 | - |
| tax | OutputInvoiceStateMachineServiceImpl | OutputInvoiceStateMachineServiceImplTest.java | 5+ | 状态转换 | 无 | - |
| budget | BudgetServiceImpl | BudgetServiceImplTest.java | 5+ | CRUD/审批 | 无 | - |
| budget | BudgetAdjustmentStateMachineServiceImpl | BudgetAdjustmentStateMachineServiceImplTest.java | 5+ | 状态转换 | 无 | - |

### 1.2 Mapper 层（真实 DB）

| 模块 | Mapper | 测试类 | 测试数 | 覆盖 | 优先级 |
|------|--------|--------|--------|------|--------|
| finance | BusinessDocMapper | BusinessDocMapperTest.java | 7 | insert/update 约束 | - |
| finance | BankStatementMapper | BankStatementMapperTest.java | 5+ | CRUD | - |
| finance | VoucherMapper | VoucherMapperTest.java | 5+ | CRUD | - |
| finance | NumberingAssociationFieldsTest | NumberingAssociationFieldsTest.java | 5+ | 编号关联字段 | - |
| finance | NumberingAssociationIndexesTest | NumberingAssociationIndexesTest.java | 5+ | 索引 | - |
| tax | OutputInvoiceMapper | OutputInvoiceMapperTest.java | 5+ | CRUD | - |
| tax | InputInvoiceMapper | InputInvoiceMapperTest.java | 5+ | CRUD | - |
| asset | AssetCardMapper | AssetCardMapperTest.java | 5+ | CRUD | - |
| system | PeriodMapper | PeriodMapperTest.java | 5+ | CRUD | - |
| system | SubjectMapper | SubjectMapperTest.java | 5+ | CRUD | - |

### 1.3 Controller 层

| 模块 | Controller | 测试类 | 测试数 | 覆盖 | 优先级 |
|------|-----------|--------|--------|------|--------|
| finance | SalesInvoiceController | SalesInvoiceControllerTest.java | 5+ | 导入/预览 | - |
| finance | BankReconciliationController | BankReconciliationControllerTest.java | 5+ | 对账 | - |
| finance | NumberingTraceController | NumberingTraceControllerTest.java | 3+ | 编号溯源 | - |
| arap | ReconciliationController | ReconciliationControllerTest.java | 42 | 核销全流程 | - |
| arap | ExpenseReimbursementController | ExpenseReimbursementControllerTest.java | 5+ | CRUD/审批 | - |

### 1.4 E2E 链路

| 链路 | 测试类 | 覆盖步骤 | 空白 | 优先级 |
|------|--------|----------|------|--------|
| 银行流水导入 | BankFlowE2ETest.java | 导入→路由→收款单 | 无 | - |
| 销售发票导入 | SalesFlowE2ETest.java | 导入→业务单→凭证 | **未验证工作台可见性** | P0 |
| 费用报销 | ExpenseFlowE2ETest.java | 报销→审核→凭证 | 无 | - |
| 编号全链路 | NumberingFullChainE2ETest.java | 编号关联验证 | 无 | - |
| 编号结算 | NumberingSettlementE2ETest.java | 结算关联验证 | 无 | - |
| 编号前端API | NumberingFrontendApiTest.java | 前端API验证 | 无 | - |
| 编号关联 | NumberingAssociationE2ETest.java | 跨实体关联验证 | 无 | - |
| **核销工作台** | **无** | **无** | **INVOICE_OUT→工作台→核销** | **P0** |

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
| T1 | pageQuery() 零测试 | BusinessDocService | 手动审查 | 🔴 待修复 |
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
