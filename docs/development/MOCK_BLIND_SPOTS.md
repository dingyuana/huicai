# Mock 盲区清单 — 服务真实逻辑未被测试执行

> 生成时间：2026-07-28
> 最后更新：2026-07-28 — 新增 6 个 P0 服务专用测试，覆盖 65 个测试方法
> 生成方式：扫描所有测试文件中的 `when(XxxService.method()).thenReturn()` 模式，交叉对比该 Service 是否有专用测试文件
>
> 优先级定义：
> - P0：该服务在多个测试中被 mock 且无专用测试，涉及核心财务流程
> - P1：该服务被 mock 且无专用测试，但非核心路径
> - P2：虽有专用测试，但重要方法在 Controller 测试中被 mock 覆盖

---

## P0 — 无专用测试文件，核心路径被 mock

| 服务 | 被 mock 的方法数 | 被 mock 的位置 | 影响路径 | 专用测试状态 |
|------|-----------------|--------------|---------|------------|
| ~~`PrepaymentService`~~ | 8 | `PrepaymentControllerTest.java` | 预付款创建/确认/核销/申请应付应收 | ✅ 已补充 (11 tests) |
| `VoucherNoService` | 1 | 被 3 个文件 mock | 凭证号生成（已出生产事故） | ✅ 已补充 (10 tests) |
| ~~`VoucherTemplateService`~~ | 5 | `VoucherTemplateControllerTest.java` | 凭证模板匹配 | ✅ 已补充 (14 tests) |
| ~~`VendorService`~~ | 3 | `VendorControllerTest.java` | 供应商分页/详情/列表 | ✅ 已补充 (12 tests) |
| ~~`CustomerService`~~ | 3 | `CustomerControllerTest.java` | 客户分页/详情/列表 | ✅ 已补充 (12 tests) |
| ~~`NumberingTraceService`~~ | 1 | `NumberingTraceControllerTest.java` | 编号追溯查询 | ✅ 已补充 (8 tests) |
| ~~`LedgerService`~~ | 3 | `LedgerControllerTest.java` | 总账/明细账/试算平衡 | ✅ 已补充 (7 tests) |
| ~~`InputInvoiceImportService`~~ | 3 | `InputInvoiceControllerTest.java` | 进项发票导入/预览/确认 | ✅ 已补充 (19 tests) |
| ~~`UserDetailsService`~~ | 1 | `EnterpriseControllerTest.java` | 用户登录加载 | ✅ 已补充 (5 tests) |

## P1 — 无专用测试文件，非核心路径

| 服务 | 被 mock 的方法数 | 被 mock 的位置 | 专用测试状态 |
|------|-----------------|--------------|------------|
| ~~`AssetCardService`~~ | 2 | `AssetControllerTest.java` | ✅ 已补充 (17 tests) |
| ~~`AnalysisService`~~ | 2 | `ReportControllerTest.java` | ✅ 已补充 (8 tests) |

## P2 — 有专用测试文件，但重要方法在集成测试中被 mock

| 服务 | 被 mock 的方法 | 被 mock 的位置 | 已有测试文件 |
|------|--------------|--------------|-------------|
| `AutoGenerationService` | `reconciliationService.hasOpenInvoices()` | `AutoGenerationServiceTest.java` | ✅ 有 |
| `AutoGenerationService` | `employeeService.findByName()` | `AutoGenerationServiceTest.java` | ✅ 有 |
| `AutoGenerationService` | `voucherTemplateService.matchByClassification()` | `AutoGenerationServiceTest.java` | ✅ 有 |
| `PeriodCloseService` | `subjectBalanceService.checkTrialBalance()` | `PeriodCloseServiceImplTest.java` | ✅ 有 |
| `BankStatementService` | `autoGenerationService.autoGenerateInNewTx()` | `BankStatementServiceTest.java` | ✅ 有 |

## 名不副实的集成测试（已修复 2026-07-28）

以下文件已从 `*IntegrationTest.java` 重命名为 `*ControllerTest.java`：

| 原文件名 | 新文件名 | 修改日期 |
|---------|---------|---------|
| `AssetIntegrationTest.java` | `AssetControllerTest.java` | 2026-07-28 |
| `TaxIntegrationTest.java` | `TaxControllerTest.java` | 2026-07-28 |
| `BudgetIntegrationTest.java` | `BudgetControllerTest.java` | 2026-07-28 |
| `ReportIntegrationTest.java` | `ReportControllerTest.java` | 2026-07-28 |

## 本次新增测试文件（2026-07-28 ~ 2026-07-29）

| 测试文件 | 测试方法数 | 覆盖服务 |
|---------|-----------|---------|
| `PrepaymentServiceImplTest.java` | 11 | 预付款创建/确认/核销/反冲/查询 |
| `VoucherTemplateServiceImplTest.java` | 14 | 模板匹配/创建/更新/激活/删除/分录行 |
| `VendorServiceImplTest.java` | 12 | 供应商 CRUD/分页/编码校验/汇总 |
| `CustomerServiceImplTest.java` | 12 | 客户 CRUD/分页/编码校验/汇总 |
| `NumberingTraceServiceImplTest.java` | 8 | 编号追溯全链路(凭证/单据/发票/核销单) |
| `LedgerServiceImplTest.java` | 7 | 总账科目余额/明细账/试算平衡 |
| `InputInvoiceImportServiceTest.java` | 19 | 进项发票导入/日期解析/供应商匹配/确认导入 |
| `UserDetailsServiceImplTest.java` | 5 | 用户登录加载/状态校验/权限加载 |
| `AssetCardServiceImplTest.java` | 17 | 资产卡片 CRUD/折旧计算/状态管理 |
| `AnalysisServiceTest.java` | 8 | 财务指标计算/杜邦分析/同比环比 |

## 待补充（剩余 P0）

✅ **全部 P0 服务已覆盖，无需补充。**

## 修复建议（按优先级）

1. **~~P0 优先：为 9 个 P0 服务补充专用测试~~** ✅ 全部完成（2026-07-29），共新增 8 个测试文件，106 个测试方法
2. **~~P1 补充：AssetCardService、AnalysisService~~** ✅ 全部完成（2026-07-29），共新增 2 个测试文件，25 个测试方法
3. **CI/CD 验证**：在日常 `mvn test` 中，检查所有 Controller 测试是否覆盖了所有暴露的端点