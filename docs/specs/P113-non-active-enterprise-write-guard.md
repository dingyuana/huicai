# SPEC-P113 — 非 ACTIVE 账套禁写（内控缺口①）

> **状态**：✅ **已审核通过并实施完成**（2026-10-08，V1.0）
> **关联需求**：P106 SPEC §1.2 / §8 登记的「未立项缺口①」；新登记 REQ-2026-140
> **日期**：2026-10-08

---

## §0 取证：缺口真实存在

| 探针 | 实测 |
|---|---|
| `EnterpriseStatus` 全仓引用者 | **仅 2 个文件**：枚举自身 + `EnterpriseStateMachineServiceImpl` |
| `VoucherServiceImpl` 中 `EnterpriseStatus\|ACTIVE\|assertEnterprise` 出现次数 | **0** |
| `BusinessDocServiceImpl` 中 `EnterpriseStatus\|assertEnterprise` 出现次数 | **0** |
| `t_enterprise.status` CHECK | `PENDING / ACTIVE / SUSPENDED / TERMINATED`（4 态齐备，人工触发，符合铁律 #1） |

⇒ 状态机只管「改状态」，**没有任何业务写入路径检查当前账套状态** ⇒ `PENDING`/`SUSPENDED`/`TERMINATED` 账套仍可正常开单、录凭证、导发票。

**竞品对标**（铁律 #15）：用友 U8C、金蝶云星空的账套均有「账套启用/停用」开关，停用后**所有业务单据禁止录入**，且控制点在**服务端统一入口**而非各业务模块 —— 这决定本 SPEC 选切面而非逐点加守卫。

---

## §1 范围

**做**：非 ACTIVE 账套禁止**业务数据写入**。
**不做**：读路径（停用账套应仍可查询历史数据，否则无法对账审计）。

---

## §2 方案选型（为何用切面而非逐点守卫）

写入端点共 **268 个**（`@PostMapping`/`@PutMapping` 全仓统计）。

| 方案 | 评估 |
|---|---|
| 逐个 Service 方法加守卫 | ❌ 268 个端点必然漏；且 §4.5 第 43 条已实证「只解释一条路径就整表放行」的教训 |
| **服务端切面（选中）** | ✅ 一次覆盖全部写端点；不依赖「谁记得调」；与既有 `TenantRlsInitializer` 切面同构 |
| DB 层 RLS `WITH CHECK` | ❌ 跨表条件（要 join `t_enterprise`）代价高，且只覆盖有 RLS 的 72 张表 |

⚠️ **切面方案的已知风险**（§4.5 第 27 条同型：「有注解」不等于「会生效」）：
切面依赖 Spring AOP 代理，若目标类**自调用**（同类内 `this.xxx()`）则**绕过切面**。
⇒ 必须有守卫测试证明切面真被触发，而非只证明「加了注解」。

---

## §3 输入契约 / 输出契约 / 状态流转 / 异常处理

### 输入契约
- 新增 `EnterpriseWriteGuard` 切面，拦 `@(Post|Put|Delete)Mapping` 的**公开方法**。
- 状态来源：`EnterpriseContextHolder.get()` → `t_enterprise.status`。
- **无企业上下文时放行**（定时任务/初始化等系统路径，铁律不误伤）—— 与 `EnterpriseDataPermissionInterceptor` 的 fail-open 姿态一致，但**必须显式记录该取舍**。

### 输出契约
- 非 ACTIVE ⇒ 抛 `BusinessException`（铁律 #14），消息含账套 id 与当前状态。
- ACTIVE ⇒ 完全放行，**不改变任何现有行为**。

### 状态流转
不改状态机。仅**读取**状态做准入判定。

### 异常处理
| 情形 | 行为 |
|---|---|
| 账套不存在 | 拒绝（fail-closed）—— 查不到主体即视为不可写 |
| 状态查询异常 | 拒绝并记日志（fail-closed） |
| 无企业上下文 | 放行（系统路径），日志标注 |
| 状态为 `ACTIVE` | 放行 |

---

## §4 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-113-1 | 账套 `ACTIVE` | 调写端点 | 成功落库（**正向**） |
| AT-113-2 | 账套 `SUSPENDED` | 调写端点 | `BusinessException`，且**数据未变动**（负向） |
| AT-113-3 | 账套 `PENDING` | 调写端点 | 同上被拒 |
| AT-113-4 | 账套 `TERMINATED` | 调写端点 | 同上被拒 |
| AT-113-5 | **无**企业上下文 | 调写端点 | 放行（系统路径不误伤） |
| AT-113-6 | 账套不存在 | 调写端点 | fail-closed 拒绝 |
| AT-113-7 | 账套 `SUSPENDED` | **读**端点 | **放行**（停用账套仍可查历史） |
| AT-113-8 | 切面类 | 结构守卫：切面已注册 + 未被 `@Transactional` 顺序破坏 | 断言成立 |

**反证要求**（缺一不可）：
- 反证①：把切面 `@Order` 调到事务之后 / 移除切面注册 ⇒ AT-113-2/3/4 转红
- 反证②：把「无上下文放行」改成「拒绝」⇒ AT-113-5 转红

---

## §5 风险与遗留

| 风险 | 处置 |
|---|---|
| **自调用绕过切面** | AT-113-8 结构守卫 + 至少一个走 HTTP 的端到端用例（走代理 ⇒ 切面必触发） |
| 切面把系统初始化/定时任务误伤 | 无上下文时放行 + AT-113-5 锁定 |
| 268 个写端点全部纳入后性能开销 | 每次写多一次 `t_enterprise` 查询；用 `SELECT status` 单列 + 走主键索引，开销可忽略；若成瓶颈再引入短 TTL 缓存（**不在本轮**） |
| `t_enterprise` 未开 RLS | 已由 D-2b 裁定为平台级，不受影响 |

---

## §6 裁定与实施结果（2026-10-08）

### §6.1 老丁裁定（4 项全部通过）
1. ✅ 采用**切面方案**（而非 268 端点逐个加守卫）。
2. ✅ **无企业上下文时放行**（系统初始化/定时任务/种子克隆需要）。
3. ✅ **读路径放行**（停用账套仍可查历史，否则无法对账审计）。
4. ✅ 同步**更正 P106 SPEC §8 范围声明**（删除 3 项已实现子项）。

### §6.2 实施内容
- 新增 `EnterpriseWriteGuard`（`backend/src/main/java/com/huicai/common/security/EnterpriseWriteGuard.java`）：
  `@Around` 拦 `@PostMapping/@PutMapping/@DeleteMapping`；非 ACTIVE 抛 `BusinessException`（铁律 #14）；
  账套不存在 fail-closed；无上下文放行；读路径不拦。
- 新增 `P113WriteTarget`（测试夹具，顶层类才能被 component-scan 扫到 —— 嵌套类不行，
  首版踩过 `NoSuchBeanDefinitionException`）。
- 新增守卫 `EnterpriseWriteGuardRealDBTest`（9 例，覆盖 AT-113-1~8）。

### §6.3 反证证据（含两次「无效反证」的自我纠正）
| 反证 | 做法 | 结果 | 判定 |
|---|---|---|---|
| 首版① | 摘掉 `@Component` | 9 条全挂 `UnsatisfiedDependencyException` | ❌ **无效**：红在 Bean 装配而非断言 |
| 首版① | 切点改指向不存在的注解 | 9 条全挂在**上下文启动失败**（`Type referred to is not an annotation type`） | ❌ **无效**：与「编译失败不算反证成功」同型 |
| **干净①** | 切点改为 `@GetMapping`（真实注解但目标类不命中），上下文正常启动 | **5 条以 `AssertionFailedError` 转红**（SUSPENDED / TERMINATED / 账套不存在等），启动失败计数 0 | ✅ **有效** |
| **干净②** | 「无上下文放行」改为拒绝 | AT-113-5 转红（**Error 形态**：被测方法直接抛出注入异常，非 `AssertionFailedError`） | ✅ 有效检出，形态差异如实记录 |

**教训沉淀**：切面类守卫的反证**必须保证 Spring 上下文能启动**，否则整类挂在启动阶段，
看起来全红实则与断言无关。「让切点失配」的正确做法是换一个**真实存在但目标方法没标注**的注解，
而不是引用一个不存在的类型。

### §6.4 门禁
L1 `1665/0/0/5`（clean 口径 `INSTR 0.4913 / BRANCH 0.4156 / METHOD 0.6786`，阈值 49/41/67，
`All coverage checks have been met`，`Skipping JaCoCo execution` 计数 0）；
L2 `2138/0/0/5`（+9，`RedisConnectionFailure` 计数 0，`BUILD SUCCESS`）；
前端 `vue-tsc` exit 0 + vitest `26 files / 265 tests`；三静态门禁全过。

⚠️ **切面全量生效的回归风险已实测排除**：切面对全部 268 个写端点生效，若 Testcontainers
里企业 1 在 `t_enterprise` 不存在，fail-closed 会让既有写测试全线变红。实测 L2 零回归
（`2138/0/0/5`）⇒ 种子中企业 1 存在，该风险不成立。**这是「加了全局守卫后必须跑全量」
的又一个实例**（§4.5 第 33 条同型：不能只跑自己那几条）。

### §6.5 遗留
- 每次写端点多一次 `t_enterprise` 主键查询。若成瓶颈再引入短 TTL 缓存（不在本轮）。
- Spring AOP 自调用（同类内 `this.xxx()`）仍会绕过切面 —— 本轮靠"切面拦在 Controller 边界"
  规避（Controller 之间不自调用）；若将来把守卫下沉到 Service 层，须重新评估。

---

## §6 待老丁裁定项

1. 是否同意**切面方案**（而非 268 端点逐个加守卫）？
2. **无企业上下文时放行**是否可接受？（系统初始化/定时任务需要；若要求 fail-closed，需另行安排这些系统路径显式声明身份）
3. 读路径**放行**（停用账套可查历史）是否符合预期？
4. 是否同意同时**更正 P106 SPEC §8 范围声明**（删除 3 项已实现子项：制单≠审核 / 数据权限粒度 / 年结 —— 取证证明已实现）？

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: EnterpriseEntity
module: security
table: t_enterprise

# 本 SPEC 不改状态机，只**读取**状态做写入准入判定。
# 四态来自 chk_enterprise_status（t_enterprise.status），与
# EnterpriseStateMachineServiceImpl 人工触发的四态一一对应。
states:
  PENDING:
    description: "待激活 —— 未激活账套禁止写入业务数据"
    initial: true
    terminal: false
  ACTIVE:
    description: "已激活 —— 唯一允许写入业务数据的状态"
    initial: false
    terminal: false
  SUSPENDED:
    description: "已暂停 —— 禁止写入，但读路径放行以便对账审计"
    initial: false
    terminal: false
  TERMINATED:
    description: "已终止（终态）—— 禁止写入"
    initial: false
    terminal: true

transitions:
  - id: activate
    from: PENDING
    to: ACTIVE
    trigger: activateEnterprise
    precondition: "status == PENDING"
    postcondition: "status == ACTIVE"
    side_effects: []
    test_ref: EnterpriseStateMachineServiceImpl
  - id: suspend
    from: ACTIVE
    to: SUSPENDED
    trigger: suspendEnterprise
    precondition: "status == ACTIVE"
    postcondition: "status == SUSPENDED"
    side_effects: []
    test_ref: EnterpriseStateMachineServiceImpl
  - id: reactivate
    from: SUSPENDED
    to: ACTIVE
    trigger: reactivateEnterprise
    precondition: "status == SUSPENDED"
    postcondition: "status == ACTIVE"
    side_effects: []
    test_ref: EnterpriseStateMachineServiceImpl
  - id: terminate
    from: SUSPENDED
    to: TERMINATED
    trigger: terminateEnterprise
    precondition: "status == SUSPENDED"
    postcondition: "status == TERMINATED"
    side_effects: []
    test_ref: EnterpriseStateMachineServiceImpl

constraints:
  - id: C-01
    type: security
    rule: "EnterpriseWriteGuard 切面拦 @PostMapping/@PutMapping/@DeleteMapping；非 ACTIVE 抛 BusinessException.forbidden"
    implementation: "EnterpriseWriteGuard.assertEnterpriseWritable()"
    enforcement: "服务端统一入口，一次覆盖全仓 268 个写端点"
  - id: C-02
    type: business
    rule: "无企业上下文时放行（系统初始化/定时任务/种子克隆走此路径，fail-closed 会使其全部不可用）"
    enforcement: "EnterpriseWriteGuard 内 enterpriseId == null 分支"
  - id: C-03
    type: business
    rule: "读路径（@GetMapping）刻意不拦：停用账套须仍可查询历史数据以满足对账审计"
    enforcement: "切点只匹配 Post/Put/DeleteMapping"
  - id: C-04
    type: database
    rule: "t_enterprise.status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','TERMINATED'))"
    migration: chk_enterprise_status
  - id: C-05
    type: business
    rule: "账套不存在按 fail-closed 拒绝（查不到主体即视为不可写）"
    enforcement: "currentStatus 返回 null 时抛 BusinessException.forbidden"

acceptance_tests:
  - id: AT-113-1
    description: "ACTIVE 账套写端点放行"
    method: activeEnterpriseMayWrite
    assertion: "ACTIVE 账套 doWrite 返回 'written'"
    status: covered
  - id: AT-113-2
    description: "SUSPENDED 账套写端点被拒且异常指明状态"
    method: suspendedEnterpriseCannotWrite
    assertion: "抛 BusinessException 且消息含 SUSPENDED"
    status: covered
  - id: AT-113-3
    description: "PENDING 账套写端点被拒"
    method: pendingEnterpriseCannotWrite
    assertion: "抛 BusinessException"
    status: covered
  - id: AT-113-4
    description: "TERMINATED 账套写端点被拒"
    method: terminatedEnterpriseCannotWrite
    assertion: "抛 BusinessException"
    status: covered
  - id: AT-113-5
    description: "无企业上下文时放行（系统路径不误伤）"
    method: noContextIsAllowedThrough
    assertion: "withoutEnterpriseContext 下 doWrite 返回 'written'"
    status: covered
  - id: AT-113-6
    description: "账套不存在 fail-closed 拒绝"
    method: missingEnterpriseFailsClosed
    assertion: "抛 BusinessException 且消息含「不存在」或「不可用」"
    status: covered
  - id: AT-113-7
    description: "读路径不受守卫影响"
    method: readPathNotGuarded
    assertion: "SUSPENDED 账套下 doRead 仍返回 'read'"
    status: covered
  - id: AT-113-8
    description: "切面真被织入（同一方法随账套状态改变结果）"
    method: aspectIsActuallyWeaved
    assertion: "ACTIVE 放行 → 改 SUSPENDED 被拒 → 改回 ACTIVE 又放行"
    status: covered
  - id: AT-113-9
    description: "守卫切面已注册为 Spring Bean"
    method: guardBeanExists
    assertion: "EnterpriseWriteGuard Bean 非空"
    status: covered

out_of_scope:
  - "读路径准入控制（停用账套仍可查历史，见 C-03）"
  - "DEPT_AND_CHILD 递归数据权限（DataPermissionInterceptor 现降级为 DEPT，属独立已知简化）"
  - "t_user 的 3 行 enterprise_id=NULL 数据修复（老丁裁定单独立项）"

dependencies:
  - spec: P106
    relation: "多账套子项已全部收口；本 SPEC 补其 §8 登记的唯一未立项缺口"
  - spec: P108
    relation: "RLS 谓词空串硬化；本 SPEC 读 t_enterprise 状态，与 GUC 无关"
