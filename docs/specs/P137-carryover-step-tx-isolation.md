# SPEC-P137：结转序列单步业务跳过毒化外层事务（rollback-only 500）

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已实施并验证**（2026-10-20，方案 A 老丁已裁定） |
| REQ | REQ-2026-153 |
| 前序 | SPEC-P84（结账工作台第 1 步「单步失败不中断」设计）、AGENTS §4.5 第 58/61 条（RLS 收口批次给本类补类级事务） |
| 关联 | AGENTS §1 铁律 #11（事务红线）、§4.4 第 13 条（`@Transactional` 与异常吞噬互斥的 Spring 事务机制） |

---

## §0 取证（全部实测，2026-10-20）

### 0.1 用户报障现象

结账工作台点击「生成结转凭证序列」：

```
GET /api/base/voucher/v1/period-close/generate-sequence?period=202407 → 500
浏览器控制台：AxiosError: Request failed with status code 500
```

### 0.2 后端日志坐实

```
2026-10-20T05:15:02.366Z INFO  CarryoverSequenceServiceImpl : 结转序列单步跳过: step=DEPR
    reason=期间 202407 无可制证的折旧数据，请先在资产模块计提折旧
2026-10-20T05:15:02.413Z ERROR GlobalExceptionHandler : 系统异常:
    UnexpectedRollbackException: Transaction rolled back because it has been marked as rollback-only
    at CarryoverSequenceServiceImpl$$SpringCGLIB$$0.generateSequence(<generated>)
```

### 0.3 根因（两段设计打架）

| 时序 | 改动 | 语义 |
|---|---|---|
| P84 原设计 | `CarryoverSequenceServiceImpl` **无** `@Transactional`，三步各自独立事务 | 单步抛 `BusinessException`（无数据/幂等拦截）只影响该步，`runStep` 敢于捕获并转 SKIPPED |
| RLS 收口批次（§4.5 第 58/61 条） | 因该类读 `t_voucher`/`t_voucher_entry`（RLS 表），无事务 ⇒ GUC 恒 NULL ⇒ 结转查询恒返 0 行，给类补 `@Transactional(rollbackFor=Exception.class)` | 外层有了共享事务 |

叠加后果：内层步服务（`DepreciationVoucherServiceImpl.generate` / `PeriodCloseServiceImpl.generateProfitCarryOver/Distribution`）标 `@Transactional`（默认 REQUIRED）**加入**外层事务 ⇒ 抛 `BusinessException` 时内层拦截器把共享事务标记 **rollback-only** ⇒ `runStep` 捕获吞掉 ⇒ 外层 try commit ⇒ `UnexpectedRollbackException` → 500。

**爆炸半径**：DEPR/CLOSE/DISTRIB 三步中**任一步**命中「业务跳过」（无数据、幂等重复生成）⇒ 该端点 100% 报 500。空账套恰好三步全跳 ⇒ **正常业务路径必现**；有数据的期间反而常绿。

### 0.4 为什么测试没拦住

| 层 | 缺口 |
|---|---|
| `CarryoverSequenceServiceImplTest`（L1/10 例） | Mockito 直调 impl 方法，**无事务代理** ⇒ rollback-only 机制根本不存在，回归点测不出 |
| `CloseWorkbenchRestContractTest` | `@WebMvcTest` 切片，`CarryoverSequenceService` 是 mock bean，同上 |
| L2 真库 | 该端点路径从未被真库集成测试覆盖（仅单测/MockMvc 切片）⇒ 恰好缺「真实代理 + 真实 DB」的组合形态 |

---

## §1 方案（A，老丁 2026-10-20 裁定）

**每步用 REQUIRES_NEW 独立事务执行；单步业务异常只回滚该步自己的事务，不再毒化外层。**

### 1.1 改动

| # | 文件 | 改动 |
|---|---|---|
| A | 新增 `com.huicai.common.transaction.RequiresNewRunner` | `@Component` + `@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)` 的 `run(Supplier<T>)`。**注意**：注解必须打在被代理的公共方法上（生产经 CGLIB 代理生效），单测直调 impl 时无代理、原样 inline 执行 |
| B | `CarryoverSequenceServiceImpl` | `runStep` 改经 `stepTxRunner.run(executor::execute)`；`StepExecutor` 去 `throws Exception`（三步本身不抛受检异常）；类 javadoc 同步改写（原「无 @Transactional」的描述已过期） |
| C | 构造器 | `@RequiredArgsConstructor` 自动带上新依赖；`CarryoverSequenceServiceImplTest#setUp` 传入 `new RequiresNewRunner()`（无代理 ⇒ 行为与旧 inline 等价，10 个既有用例全保留） |

### 1.2 机制自证

- **GUC 不缺位**：`TenantGucSetter` 由 `EnterpriseDataPermissionInterceptor` **逐 SQL 调用**（非逐方法），REQUIRES_NEW 打开的新连接（新 ConnectionHolder）未登记过 ⇒ 新事务首条 SQL 前重新 `SET LOCAL`，RLS 谓词在每步自己的事务里正常取值。
- **幂等语义保留**：每步在 Runner 出口提交 ⇒ 顺延步骤的幂等检查（如"已有 CLOSE-202407 凭证"）能看到前一步**已提交**的行，与 P84 原设计一致。
- **外层事务只读**：外层仅做结果组装（toVO 只查），不写任何表 ⇒ 提交恒干净。

### 1.3 行为契约（BDD）

**场景 1：空账套生成序列（用户报障路径）**

```gherkin
Given 全新账套（无计提折旧/无结转数据/无可分配利润）
And   期间 209801 已开
When  调用 generateSequence("209801")
Then  正常返回 3 条结果（无异常）
And   DEPR = SKIPPED（reason 含「无折旧」）
And   三步均为 SKIPPED/GENERATED，绝无 FAILED
```

**场景 2：反证（证明用例对回归敏感）**

```gherkin
Given 暂时把 RequiresNewRunner 的传播级别改回 REQUIRED（注入回归）
When  跑场景 1 用例
Then  抛 UnexpectedRollbackException（用例转红）⇒ 还原后转绿
```

**场景 3：幂等重复调用**

```gherkin
Given 期间内已存在 DEPR-xxx 凭证
When  再次 generateSequence
Then  DEPR = SKIPPED（reason 为幂等拦截文案），其余步同样不 500
```

---

## §2 守卫与测试

| 测试 | 层 | 断言 |
|---|---|---|
| `CarryoverSequenceServiceImplTest`（既有 10 例，构造器补 runner） | L1 | 单步 SKIPPED/FAILED 不中断序列、顺序 DEPR→CLOSE→DISTRIB、toVO 组装 |
| `CarryoverSequenceTxIsolationRealDBTest#businessSkipMustNotPoisonOuterTransaction` | **L2 新增** | 空账套 `generateSequence` 不抛 `UnexpectedRollbackException`、DEPR=SKIPPED、无 FAILED |
| 反证 | L2 | 传播级别改回 REQUIRED（注入回归）⇒ 用例转红；还原 ⇒ 恢复绿 |

---

## §3 已知局限（如实登记）

1. **REQUIRES_NEW 挂起外层事务**，步骤期间同时占用 2 条池内连接（默认 Hikari 10）——单请求语义，无并发放大。
2. **步间独立提交**：后置步失败不会回滚已成功的步（P84 原设计即如此，部分成功由幂等键兜底），**不是**本 SPEC 引入的行为。
3. L2 用例选**动态企业号**：REQUIRES_NEW 内若未来某步产生写入会**真提交**（不受测试回滚保护），动态号保证残留数据永不撞键。
4. `runStep` 吞 `BusinessException` 转 SKIPPED 的口径不变；若某步失败的原因其实是**该修的数据问题**，仍表现为 SKIPPED 文案（由 reason 文案区分），属既有设计。

---

## §4 裁定记录

- 老丁 2026-10-20：**选方案 A**（每步 REQUIRES_NEW，独立事务）。
- 方案 B（不吞异常）破坏 P84「返回完整三步序列」契约；方案 C（撤类级事务）令 RLS 守卫转红且结转查询恒返 0 行，均否。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: CarryoverSequenceServiceImpl
module: sme.periodclose
table: null

rules:
  - id: C-01
    source: "P137 §1.1 B"
    rule: "generateSequence 的每个步骤必须经 REQUIRES_NEW 独立事务执行，单步事务的失败不得把外层事务标记 rollback-only"
    mapping: { propagation: REQUIRES_NEW, isolation: per_step, poison: forbidden }
    implementation: "RequiresNewRunner.run（CarryoverSequenceServiceImpl#runStep 调用）"
  - id: C-02
    source: "P84 原设计 / P137 §1.1"
    rule: "单步抛 BusinessException 记 SKIPPED（reason 取异常文案）；其他异常记 FAILED；序列始终返回全部三步"
    mapping: { business_exception: SKIPPED, other_exception: FAILED, result_size: 3 }
    implementation: "CarryoverSequenceServiceImpl#runStep"
  - id: C-03
    source: "P137 §1.2"
    rule: "每个 REQUIRES_NEW 事务的首条 SQL 前必须重新 SET LOCAL app.enterprise_id（GUC 不因新连接缺失）"
    mapping: { guc: per_transaction, setter: TenantGucSetter_via_DataPermissionInterceptor }
    implementation: "EnterpriseDataPermissionInterceptor（逐 SQL 触发，既有）"
  - id: C-04
    source: "P137 §1.2"
    rule: "步骤提交独立、顺延可见：后续步的幂等检查必须能看到前序步已提交的凭证"
    mapping: { commit: per_step, visibility: subsequent_steps_ok }
    implementation: "RequiresNewRunner 提交时机"
  - id: C-05
    source: "P137 §2"
    rule: "真库回归锁：空账套序列生成必须正常返回且无 FAILED；传播级别回退 REQUIRED 时该用例必须转红"
    mapping: { guard: unexpected_rollback_forbidden, falsification: change_propagation }
    implementation: "CarryoverSequenceTxIsolationRealDBTest"

acceptance_tests:
  - id: AT-137-1
    ref: "sme/periodclose/CarryoverSequenceTxIsolationRealDBTest#businessSkipMustNotPoisonOuterTransaction"
  - id: AT-137-2
    ref: "CarryoverSequenceServiceImplTest#generateSequence_deprSkipped_continuesOtherSteps"
