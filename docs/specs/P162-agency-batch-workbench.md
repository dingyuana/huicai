# P162 代理记账批量作业工作台

> 日期：2026-11-17
> 状态：已实施并验证
> 范围：将代理端批量导入、审核、结账从 501 桩改为调用现有业务状态机；不引入 AI。

## 1. 事实基线

本 SPEC 以源码为准：

- `agency/batch` 下批量导入、批量审核、批量结账服务当前显式返回 501。
- `BatchOperation.vue` 已提供导入、凭证审核、结账界面，但导入类型未传给后端，且上传控件展示了现有 Excel 导入器并不支持的 CSV/PDF/OFD/XML 格式。
- 销项/进项发票已有独立 Excel 导入服务；凭证审核复用 `VoucherService.audit`；发票审核复用进/销项状态机；结账复用 `PeriodCloseService.closePeriod`。
- 这些业务方法依赖事务与租户上下文。跨客户批处理不得在一个共享事务中运行，否则一项失败可能回滚其他客户，也可能污染 rollback-only 状态。
- 进项导入与结账完成事件原从登录凭证读取企业 ID；批处理临时切换的是 `EnterpriseContextHolder`，事件也必须使用该有效租户上下文，否则可能推进代理管理员当前账套而非目标客户的进度。

## 2. 目标与非目标

### 2.1 目标

1. 每项业务调用现有状态机/结账服务，不复制业务规则。
2. 每个文件、单据或客户在独立事务中处理；单项失败形成明确结果，后续项继续。
3. 处理某客户前切换 `EnterpriseContextHolder`，结束后无论成功或异常都恢复原上下文。
4. 控制器除角色校验外，校验目标客户属于当前代理，防止代理管理员伪造其他代理的企业 ID。
5. 请求级非法参数明确拒绝；单项业务失败返回逐项失败原因，不以 HTTP 成功外观掩盖失败。

### 2.2 非目标

- 不新增数据库表或 Flyway migration；不实现持久化异步任务队列。
- 不解析 PDF/OFD，不接入 OCR/AI；本次批量发票导入仅允许现有 Excel 导入器支持的 XLS/XLSX。
- 不改变代理角色策略：保持现有批量端点仅代理管理员/系统超管可调用；业务状态机仍负责制审分离等校验。
- 批量结账只调用现有 `closePeriod`。不自动生成结转凭证，也不自动审核或过账凭证。

## 3. API 与行为契约

| 端点 | 必要输入 | 执行语义 |
|---|---|---|
| `POST /api/v1/agency/batch/import` | `files[]`、`enterpriseId`、`invoiceType=input|output` | 每个 XLS/XLSX 文件分别进入进项或销项现有导入流程；结果按文件返回，文件内解析/导入问题不得被报告为完整成功 |
| `POST /api/v1/agency/batch/audit-vouchers` | 凭证 ID 列表、`enterpriseId` | 每张凭证调用 `VoucherService.audit(id, operatorId)` |
| `POST /api/v1/agency/batch/audit-invoices` | 发票 ID 列表、`enterpriseId`、`invoiceType=input|output` | 每张发票调用对应状态机的 `confirm(id, operatorId)` |
| `POST /api/v1/agency/batch/close` | 企业 ID 列表、`period=YYYYMM` | 每个客户分别调用 `PeriodCloseService.closePeriod(period, operatorId)` |

- 批量单据/企业 ID 数量上限为 100；空列表、空文件、重复或非法 ID、非法期间、未知发票类型均作为请求错误拒绝。
- XLS/XLSX 以外的文件明确失败；前端接受格式与后端一致。
- 单项失败不终止后续项。响应的 `total/success/failed/details` 以本次请求项为口径；部分成功必须保留逐项失败消息。
- 写入操作不做全有全无，也不吞掉数据库或状态机异常；异常必须转换为该项失败明细并记录日志。
- 发票审核类型必须显式传递，不能在进项/销项两个独立 ID 空间之间猜测。
- 发票导入中由既有去重逻辑识别并跳过的重复行按已处理计入成功文件，并在结果明细中单独显示数量。
- 前端批量审核页提供凭证/进项发票/销项发票类型；期间输入和提示统一为 `YYYYMM`。

## 4. 实施微循环与验收契约

1. **租户事务执行器**：切换目标客户，在 `REQUIRES_NEW` 中执行并恢复上下文。
   - 契约：成功和异常两条路径均使用目标企业上下文执行；执行结束后恢复入口上下文。
2. **凭证/发票审核**：校验输入并逐项调用既有状态机。
   - 契约：成功项状态机调用一次；单项抛错只生成该项失败，之后的项仍执行。
3. **批量结账**：校验期间及企业列表，逐企业执行既有结账服务。
   - 契约：每个企业独立调用；前一家失败不阻止后一家；日期格式错误在业务调用前拒绝。
4. **批量发票导入**：按显式类型分发到既有 XLS/XLSX 导入器，汇总解析与写入结果。
   - 契约：input/output 分别调用正确导入器；不支持格式不能进入导入器；部分行失败不得报告文件完整成功。
5. **API 与前端对齐**：增加客户归属校验和类型参数，前端只宣传后端支持的格式与状态。
   - 契约：管理员不能对其他代理的企业 ID 执行批量动作；前后端请求参数名称和值一致。
6. **目标租户事件**：批量路径产生的进项导入/结账进度事件引用正在处理的客户上下文。
   - 契约：事件 enterpriseId 与正在处理的 enterpriseId 一致，不读取代理管理员令牌里的默认企业 ID。

## 5. 验证要求

- L1：以上微循环的单元/控制器契约测试通过；原批量服务“未实现必须报错”测试替换为真实行为测试。
- 前端：批量页面/API 类型检查与相关 Vitest 测试通过。
- 回归：后端 `mvn test` 与前端测试、类型检查通过。
- L2：使用真实 PostgreSQL 与非超级用户探针角色验证 `REQUIRES_NEW` 中目标租户 GUC 的切换、RLS 的实际隔离结果及调用方上下文恢复。本测试不代替具体业务写路径的提交/回滚集成验证。

## 6. 验证结果

- 后端 L1：`cd backend && mvn test` — 3856 passed / 0 failures / 0 errors / 10 skipped。
- 后端 L2：`cd backend && mvn -q -DexcludedGroups= -Dtest=AgencyBatchTransactionRunnerRealDBTest test` — 1/1 passed；在真实 PostgreSQL 中通过 `NOSUPERUSER NOBYPASSRLS` 探针角色断言企业 2/3 分别只能看到自身行，并确认入口企业上下文恢复。
- 前端：`cd frontend && npm test` — 39 个测试文件、374 tests passed；`cd frontend && npm run build` — passed。
- 定向 API 契约：`cd frontend && npm test -- agency-batch.api.test.ts` — 2 tests passed。

```yaml
contracts:
  - id: P162-C1
    description: 批量任务在目标租户新事务中运行并恢复调用方上下文
    type: unit_test
    target: AgencyBatchTransactionRunnerTest
    assertion: target enterprise context is active during action and entry context is restored after success or failure
  - id: P162-C2
    description: 凭证及发票批量审核逐项执行且保留部分失败
    type: unit_test
    target: BatchAuditServiceImplTest
    assertion: one failed item is reported failed and later items are still invoked
  - id: P162-C3
    description: 批量结账复用既有期间结账服务并隔离客户失败
    type: unit_test
    target: BatchCloseServiceImplTest
    assertion: each enterprise invokes closePeriod independently and invalid period is rejected before execution
  - id: P162-C4
    description: 发票导入按类型分发且不接受未支持的文件格式
    type: unit_test
    target: BatchImportServiceImplTest
    assertion: input and output invoke their respective importer; non-XLS/XLSX files are rejected; duplicate rows are reported as skipped rather than failed
  - id: P162-C5
    description: 批量控制器校验代理管理员权限与目标企业归属
    type: contract_test
    target: BatchOperationControllerTest
    assertion: denied enterprise ownership prevents service invocation and authenticated operator is forwarded
  - id: P162-C6
    description: 多客户批处理事件标记实际目标企业
    type: unit_test
    target: PeriodCloseServiceImplTest
    assertion: close event enterpriseId comes from EnterpriseContextHolder
  - id: P162-C7
    description: 进项批量导入事件标记实际目标企业
    type: unit_test
    target: InputInvoiceImportServiceTest
    assertion: intake event enterpriseId comes from EnterpriseContextHolder
  - id: P162-C8
    description: 多客户 REQUIRES_NEW 执行实际受 PostgreSQL RLS 隔离并恢复入口上下文
    type: integration_test
    target: AgencyBatchTransactionRunnerRealDBTest
    assertion: a non-superuser probe sees only the target enterprise row in each transaction and the caller context is restored
```
