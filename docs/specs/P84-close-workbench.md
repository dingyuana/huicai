# P84 SPEC — 结账工作台：4 步向导 + Drawer 凭证卡片 + 一键人工审核记账

> **版本**：V1.0 | **最后修改**：2026-09-23 | **作者**：Hermes
> **状态**：✅ 已实现（后端 + 前端交付，测试通过，commit 见文末）
> **编号**：HUICAI-SPC-084 | 优先级：P1（结账链路操作闭环）
> **依据**：PRD-017 §P84 + R-136 + DSN-期末结账工作台.md + `PeriodCloseController`/`PeriodCloseView.vue` 代码 trace
> **关联**：P68（期间状态机）、P85（结转序列数据源：DEPR/CLOSE/DISTRIB）、P87（期间锁）、人工审核铁律（MEMORY）
> **test_ref**：`PeriodCloseServiceImplTest#batchReviewPost_*`（6）、`CarryoverSequenceServiceImplTest`（8）、`CloseWorkbenchRestContractTest`（8）、`TaxControllerTest`（14，完整 context 启动验证共享路径映射）

---

## 0. 背景与现状

PRD-017 P84 要求把结账从"几个分散按钮"升级为"4 步向导 + Drawer 凭证卡片 + 全程不离开结账页"。trace 现有实现后，发现三个真实缺口。

### P84-A：生成入口分散，无统一序列

原 `PeriodCloseView.vue`（212 行）是三个独立按钮：`结账前检查` / `生成损益结转` / `执行结账`。问题：

1. **只生成损益结转，不生成 DEPR 折旧和 DISTRIB 利润分配**——但 P85 已交付这两条制证能力（`DepreciationVoucherService`、`generateProfitDistribution`），前端从未暴露入口
2. **凭证管理要跳转页面**——`gotoCarryoverVoucher()` 直接 `router.push` 到凭证列表，违反 PRD"全程不离开结账页"
3. **单张审核**——`auditCarryover()` 硬编码只处理 `carryoverId` 一张凭证，多凭证场景无入口

### P84-B：一键审核记账不存在

前端 `auditCarryover()` 是三次串行 HTTP 调用：`submitVoucher → auditVoucher → postVoucher`。问题：

1. **非原子**——第 2、3 次调用失败时会留下"已提交未审核"或"已审核未记账"的中间态
2. **前端控制状态机**——提交/审核/记账的规则散落在前端，后端状态机校验被绕过风险
3. **无法批量**——PRD 要求勾选多张凭证一键处理

### P84-C：无契约测试入口

`PeriodCloseController` 只有基础路径映射，无新端点；前端 API 层无对应封装。

---

## 1. 改动契约

### 1.1 后端端点契约

| 端点 | 方法 | 用途 | 事务 |
|---|---|---|---|
| `/api/base/voucher/v1/period-close/generate-sequence` | GET `?period=` | 生成三步结转序列（DEPR→CLOSE→DISTRIB） | 无（三步各自独立事务） |
| `/api/base/voucher/v1/period-close/batch-review-post` | POST `{voucherIds}` | 一键人工审核记账 | `@Transactional` 单事务 |

**请求/响应 DTO（新增 3 个）：**

```
CarryoverStepResult(step, stepName, voucherId, status, reason, vouchers)
    status ∈ GENERATED / SKIPPED / FAILED
SequenceVoucherVO(voucherId, voucherNo, voucherTypeName, totalDebit, totalCredit, entryCount, status, createdAt)
BatchReviewPostRequest(voucherIds)
```

### 1.2 架构归属决策（关键）

**查证结论：** `com.huicai.api` 主包**只有测试无源码**（实测 `find src/main/java/com/huicai/api` 空）；base 全层对 sme 零依赖且**无任何违反先例**（`grep -rln "import com.huicai.sme" src/main/java/com/huicai/base` 返回空）。

而 `generate-sequence` 需同时调 `sme.asset.DepreciationVoucherService`（折旧）与 `base.voucher.PeriodCloseService`（结转/分配）——放 base 的任何层都会产生 **base→sme 反向依赖**。

**最终归属：** 新建 `com.huicai.sme.periodclose` 包：

- `CarryoverSequenceService`（编排接口 + 实现）——依赖 sme.asset + base.voucher + base.system，均为 **sme→base 单向合法方向**（sme/tax、sme/cash 已有先例）
- `CloseWorkbenchController`（新端点入口）——与 `PeriodCloseController` 共享基础路径，子路径不重叠

**base 层保持零依赖 sme，硬约束不破坏。**

> **与 P85 SPEC 的差异说明：** P85-C 的 `DepreciationVoucherService` Javadoc 当时写"generate-sequence 编排必须在 Controller 层完成"。实际实现**下移到 Service 层**（`CarryoverSequenceServiceImpl`），Controller 只做参数提取与鉴权。原因：Controller 层编排无法单测；Service 层编排可单测且事务边界清晰。此差异已在本 SPEC 和 P85 SPEC 双向记录。

### 1.3 副作用声明

| 方法 | 改动 | 事务 |
|---|---|---|
| `CarryoverSequenceService.generateSequence(period, userId)` | **新增**。三步依次调 DEPR/CLOSE/DISTRIB，**单步异常不中断** | 无（三步各自事务） |
| `PeriodCloseService.batchReviewPost(voucherIds, userId)` | **新增**。DRAFT→SUBMITTED→AUDITED→POSTED 四步状态链 | `@Transactional` REQUIRED |
| `PeriodCloseServiceImpl` | 新增字段 `voucherService`（**追加到 final 列表末尾**，避免改乱既有构造顺序） | — |

### 1.4 部分成功语义（设计核心）

`generateSequence` **不因单步失败中断**，每步结果必有一条：

- `BusinessException`（无数据 / 幂等拦截）→ `SKIPPED` + 业务原因
- 其他 `Exception`（系统性故障）→ `FAILED` + 异常消息

理由：结账序列要"尽力而为"。若有折旧凭证却因无损益而整体中断，操作员看到的是一堆半成品；返回完整三步状态（哪怕三步都 SKIPPED）前端才能渲染完整步骤条。

**幂等保护：** 各步重复调用会抛"已存在…凭证"，被本层捕获记为 `SKIPPED`，不会重复建单——支持安全重试。

### 1.5 一键审核记账的原子性

`batchReviewPost` 通过 **REQUIRED 传播嵌套调用** `voucherService.batchSubmit / batchAudit / batchPost`：

```
batchReviewPost (@Transactional)
  ├─ 预校验：全部凭证必须 DRAFT（否则抛，不推进任何状态）
  ├─ batchSubmit  → REQUIRED，加入本事务
  ├─ batchAudit   → REQUIRED，加入本事务
  └─ batchPost    → REQUIRED，加入本事务
```

**查证过的两个前提：**

1. **无循环依赖**：`grep "PeriodClose" VoucherServiceImpl.java` 返回 0 命中
2. **无乐观锁冲突**：`batchUpdateStatus` 的 SQL 里 `version` 参数为 null 时**不追加** `AND version = #{version}`，三次串行更新无版本冲突

任一步失败 → 整个事务回滚，不留"部分已审核未记账"中间态。

**复用而非重复规则：** 状态机校验（`assertAuditable`/`assertPostable`）、期间锁检查（`assertPeriodOpen`）、期初校验全部通过嵌套调用复用 `VoucherServiceImpl` 既有逻辑，不重新实现。

### 1.6 人工审核铁律

- `generateSequence` 只生成 **DRAFT**，绝不自动审核/过账
- `batchReviewPost` 仅由用户主动点击触发（前端 `ElMessageBox.confirm` 二次确认）
- `batchReviewPost` 拒绝非 DRAFT 凭证——防止覆盖既有状态（如已 POSTED 的凭证被"一键"回退到 DRAFT 路径）

---

## 2. 前端契约

### 2.1 路由与页面

- 页面：`src/views/finance/period-close/PeriodCloseView.vue`（重写，212 行 → 400+ 行）
- 路由不变（`/finance/period-close`），保持"全程不离开结账页"

### 2.2 4 步向导

| 步 | 内容 | 进入下一步条件 |
|---|---|---|
| 1 结账前检查 | 调 `checkClose`，展示问题清单 + 试算平衡 | 检查已执行 |
| 2 生成结转凭证 | 调 `generateSequence`，表格展示三步状态（已生成/已跳过/失败）+ 原因 | 序列已返回 |
| 3 核对凭证 | 一键审核记账 + Drawer 凭证卡片 | 无（可直达第 4 步） |
| 4 完成结账 | `canClose` 通过后执行 `closePeriod` | — |

### 2.3 关键交互

- **Drawer 而非跳转**：`el-drawer size="46%"` 展示凭证卡片，每张卡片含类型/借贷合计/分录数/状态 + 单张"审核记账"按钮
- **状态即时刷新**：一键审核成功后前端把对应卡片状态置为 `POSTED`，不重新请求、不跳转——满足 PRD"记账后 Drawer 卡片状态即时变 ✅"
- **`canClose` 严格化**：`检查通过 && 有凭证 && 全部 POSTED`。原实现只判断 `checkResult.passed`，可能漏过未记账的结转凭证
- **切换期间重置**：`onPeriodChange` 回到第 1 步并清空所有中间状态（检查/序列/凭证均与期间绑定）

### 2.4 API 层新增

```ts
generateSequence(period): Promise<CarryoverStepResult[]>   // GET
batchReviewPost(voucherIds): Promise<void>                  // POST {voucherIds}
```

**注意：** `batchReviewPost` 用 **POST JSON body**（`{voucherIds}`），不用 `@RequestParam`——前端 axios 发 JSON body，后端 `@RequestParam` 取不到查询参数会 500（项目高频陷阱）。

### 2.5 前端错误处理

`request.ts` 拦截器已统一弹 `ElMessage.error`，因此页面 `catch` 块**不再重复弹提示**（沿用原实现约定）。

---

## 3. 实现要点与踩坑记录

### 3.1 嵌套 lambda 括号陷阱

初版把三步编排写成内联 lambda：

```java
// ❌ 错误：lambda 闭合是 `},` 而非 `})`，漏了 runStep 的右括号
results.add(runStep(STEP_DEPR, "折旧凭证", () -> {
    ...
}, "当期无折旧计提数据");
```

编译报 `')' or ',' expected`。**已重构为提取私有方法**（`stepDepreciation`/`stepCarryOver`/`stepDistribution`），消除嵌套括号，可读性同时提升。

### 3.2 `@RequestParam` vs `@RequestBody`

初版 `batch-review-post` 用 `@RequestParam List<Long> voucherIds`，前端发 JSON body 取不到 → 500。**改为 `@RequestBody BatchReviewPostRequest`**。这是记忆库里的高频陷阱，写代码时应前置判断而非等报错。

### 3.3 `List.of(null)` 编译期陷阱

测试里 `List.of(null, null)` **构造时就抛 NPE**（`List.of` 不允许 null 元素），导致"过滤后为空"的分支无法被触发。测试改用 `Arrays.asList((Long) null, (Long) null)`。

### 3.4 import 遗漏（两次）

- `PeriodCloseServiceImpl` 在 `.impl` 子包，引用父包 `VoucherService` 需显式 import
- `@RequestBody`/`@PostMapping` 需 import（项目 controller 用显式导入而非通配符）

### 3.5 契约测试的 mock 遗漏（P85-C 教训复用）

P85-C 曾给 `AssetCardController` 加依赖未同步 `@WebMvcTest` 的 `@MockBean`，导致 6 个 error。本次**主动前置检查**：`PeriodCloseRestContractTest` 是 `@WebMvcTest(PeriodCloseController.class)`，**只加载该 controller，不含** 新建的 `CloseWorkbenchController`，故不受影响。新建 controller 已补契约测试（见 §4.2）。

### 3.6 共享路径映射验证

两个 controller 共享 `/api/base/voucher/v1/period-close`。这只有启动完整 Spring context 才能发现问题。**已验证**：`TaxControllerTest`（`@SpringBootTest` + H2，无需 Docker）14/14 绿，无 `Ambiguous mapping` / context 启动失败。

### 3.7 异常状态码的项目约定（契约测试发现）

写契约测试时发现两个与直觉不符的映射，记录以免后续踩坑：

| 场景 | 返回 | 依据 |
|---|---|---|
| `@RequestParam` 缺失 | **500** | `GlobalExceptionHandler` 无专门分支，落通用异常处理 |
| `@Valid` 校验失败 | **400** | `@ExceptionHandler(MethodArgumentNotValidException.class)` + `@ResponseStatus(BAD_REQUEST)` |

因此 `batch-review-post` 的输入校验必须走 `@Valid`（400 语义正确）；`generate-sequence` 的 `@RequestParam period` 缺失会返回 500，与业务异常同码，契约测试只断言"报错"不断言码位。

### 3.8 `@Valid` + `@NotEmpty` 补齐

初版 `BatchReviewPostRequest` 无校验注解，空数组/缺字段会穿透到 service 才报错。已补 `@NotEmpty(message = "凭证ID列表不能为空")` + controller 加 `@Valid`，与项目既有范式一致（`VoucherController.batchSubmit` 用 `VoucherStatusDTO` 的 `@NotEmpty`）。

---

## 4. 测试覆盖

### 4.1 单元测试

| 测试类 | 覆盖 | 结果 |
|---|---|---|
| `PeriodCloseServiceImplTest#batchReviewPost_*` | 三步链正常 / 空列表 / 全 null / 不存在 / 非 DRAFT / 审计失败不记账（原子性） | 6/6 |
| `CarryoverSequenceServiceImplTest` | 三步全成功 + 顺序 / DEPR 跳过 / CLOSE 跳过 / 运行时异常 FAILED / 三步全失败仍返 3 条 / getVoucherView ×3 | 8/8 |

**新增测试中特别验证的关键断言：**

- `batchReviewPost_auditFails_doesNotPost`：提交已执行、审核抛异常后 **`batchPost` 绝不被调用**——原子性语义的核心校验
- `generateSequence_allFail_stillReturnsThreeSteps`：**即使三步全失败，仍返回 3 条结果**——保证前端始终能渲染完整步骤条

### 4.2 契约测试

| 测试类 | 覆盖 | 结果 |
|---|---|---|
| `CloseWorkbenchRestContractTest` | 三步序列返回 / 部分跳过仍返 3 步 / 缺 period / JSON body 正确反序列化并调用 service / 单张 / 空数组 400 / 缺字段 400 / 仅 query 参数不触及 service | 8/8 |

其中 `batchReviewPost_jsonBody` 是 `@RequestBody` vs `@RequestParam` 陷阱的回归断言：JSON body 必须能被反序列化并调用到 service。

### 4.3 Context 启动验证

`TaxControllerTest` 14/14（验证两个 controller 共享路径不冲突、`CarryoverSequenceServiceImpl` bean 正常装配）

### 4.4 前端构建

`npx vite build` 成功，2651 模块，dist 时间戳晚于源码修改（部署铁律）

---

## 5. 局限与后续

| 项 | 说明 |
|---|---|
| **P84 无 RealDB 测试** | `batchReviewPost` 的事务回滚行为需在真实 DB 上验证。Mock 测试无法证明 REQUIRED 传播实际生效——**这是本 SPEC 最明显的测试缺口** |
| **Drawer 状态前端乐观更新** | 一键审核后前端直接置 `POSTED`，若后端实际失败则状态不一致。后端抛错时前端 catch 不刷新，但用户已看到"成功"提示前可能闪现 |
| **`createdBy` 缺陷未修** | `BaseEntity.createdBy` 标 `@TableField(exist=false)`，所有自动生成凭证的 `created_by` 均为 NULL（含本次生成的 DEPR/CLOSE/DISTRIB）。审计仅靠 `t_close_log.operator_id` 与应用日志。建议单独开工单 |
| **Step 3 可直达 Step 4** | 允许跳过一键审核直接结账，由后端 `closePeriod` 的"未记账凭证"检查兜底拦截 |

---

## 6. Commit

- 后端契约（DTO + 编排 + `batchReviewPost` + Controller + 测试）：`aa2702b`
- 契约测试（8 例）+ `@NotEmpty`/`@Valid` 补齐：`7ce5802`
- 前端（API 封装 + 4 步向导重写，含于 `aa2702b`）
- 文档（本 SPEC + PRD-017 翻转 + CORE R-136 翻转 + 注册表 V4.7）：`d7d8511`

---

## 附录 A：与 P85 的关系

P85 交付**数据源**（三条结转能力 + 结账日志），P84 交付**操作闭环**（把三条能力编排进工作台 UI）。两者共享 `PeriodCloseController` 基础路径：

- P84 新端点：`generate-sequence`、`batch-review-post`（`CloseWorkbenchController`，`sme.periodclose`）
- 既有端点：`check`、`profit-carryover`、`profit-distribution`、`close`、`reopen`、`log`（`PeriodCloseController`，`base.voucher`）
