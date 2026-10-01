# 测试策略与规范

> **编号**：HUICAI-TEST-001
> **版本**：V1.2 | **日期**：2026-10-01 | **作者**：opencode
> **V1.2 变更**：**全文硬数字按实测重写**（V1.1 的 5 处数字均已失真，见下方「修订说明」）；补 CI 真实拓扑、覆盖率实测值与棘轮阈值、Mock 同义反复台账
> **关联文档**：[项目说明](../CORE-项目说明.md)、[技术方案](../CORE-技术方案.md)、[P104 测试门禁与成色整改](../specs/P104-test-gate-quality.md)
> **关联 Skill**：`dy-测试方法`（分层策略、pitfall 库）、`dy-测试门禁`（完成前验证）

---

## 0. 当前测试状态（2026-10-01 实测）

| 维度 | 数据 | 口径 |
|------|------|------|
| 后端可执行用例 | **2067** = `@Test` 2064 + `@TestFactory` 3 | `grep -oE '@Test\b'`（排除 `@Testcontainers`/`@TestPropertySource` 等误匹配） |
| 后端测试类 | **243** | `find -name '*Test.java'` |
| 其中真库测试类 | **66**（继承 `AbstractMapperTest`） | 真库 = 真实 PostgreSQL 16 + Flyway |
| 前端单测 | **27** 个 Vitest 文件 | `frontend/**/*.test.*` |
| 前端 E2E | **32** 个 Playwright spec | `frontend/**/*.spec.ts` |
| 覆盖率实测 | **INSTRUCTION 32% / BRANCH 13% / METHOD 56%** | JaCoCo 首次真实执行（此前从未执行过，见下） |
| 覆盖率门禁 | 棘轮 **30% / 12% / 55%** | 按实测值留约 2 个百分点缓冲 |
| CI 门禁 | **4 个 workflow，全部真实生效** | L1 / L2 / Full Stack / SPEC |
| CI 实测 | L1 `1767/0/0/5`、L2 `2065/0/0/5` | `Failures/Errors/Skipped` |

### V1.2 修订说明：V1.1 的 5 处数字均已失真

| V1.1 的声称 | 实测 | 错在哪 |
|---|---|---|
| 后端 1776 @Test / 210 类 | **2067 / 243** | 未随新增测试回写 |
| 前端 17 个 Vitest 文件 | **27** | 同上 |
| E2E 19 个 Playwright 文件 | **32** | 同上 |
| 「无自动 CI（本地 `mvn test` 前置）」 | **有 4 个 workflow**，且 PR 上强制 | 该结论早已过期，误导新人以为可跳过 CI |
| 「JaCoCo 配置就绪，branch ≥ 70%」 | 配置就绪但**门禁从未执行过**；且 branch 实测仅 **13%** | 双重失真：既不知道门禁是假绿，也不知道真实值离 70% 差多远 |

> **教训**：`branch ≥ 70%` 这类**未经实测的门槛**比没有门槛更危险 ——
> 它让人以为覆盖率已达标。本项目 `surefire <argLine>` 未含 `@{argLine}`，
> 覆盖了 `jacoco:prepare-agent` 设置的属性，导致 agent 从未挂载、无 `jacoco.exec`、
> `report` 与 `check` 双双打印 `Skipping JaCoCo execution` 静默跳过。
> **凡写覆盖率门槛，必须先实测一次再定值，并按棘轮逐步抬高。**

---

## 1. 测试分层（L1-L5）

### 1.1 L1 单元测试 — 每个 PR 强制

- 触发：`pull_request` / `push` 到 `main`、`develop`
- 命令：`mvn test -DexcludedGroups=slow -DfailIfNoTests=false`
- 范围：不碰真库的测试（Mockito 单测、契约切片、纯函数）
- **必须真实执行覆盖率门禁**（随 `mvn test` 同批生效，勿重复调 `jacoco:check`）

### 1.2 L2 集成测试 — 涉及 DB/Redis 写入

- 触发：同 L1
- 命令：`mvn test -DexcludedGroups="" -DfailIfNoTests=false`
- 范围：`@SlowTest`（真库 Testcontainers `pgvector/pgvector:pg16` + Redis 服务）
- **性能阈值在此环境放宽**：`-Dperf.query.threshold.ms=2000 -Dperf.page.threshold.ms=3000`
  （共享 runner 负载不可控，硬编码 500ms 会抖动；本地仍用默认 500ms 以便及早发现退化）
- ⚠️ **涉及租户隔离、事务、RLS、真实 SQL 方言的改动，合并前必须本地跑一次 L2** ——
  本地 L1 全绿不代表安全：曾出现 L2 连续三轮红而 L1 全绿的案例，
  其中 `assist_json ->> ?` 在真实库里意味着**辅助核算账一直返回跨租户数据**

### 1.3 L3 API 契约测试 — 控制器层

见 §2.3。

### 1.4 L4-L5 E2E 测试 — 前端

当前状态：**零测试**。需要逐步建立。

| 层 | 覆盖 | 工具 | 优先级 |
|----|------|------|--------|
| L4 Smoke | 登录、凭证录入、业务单据列表、审核、红冲 | Playwright | P2 |
| L5 Full | 全流程 + 自动生成链 | Playwright | P3 |

---

## 2. 测试命名约定

### 2.1 后端测试类命名

```
{Module}ServiceImplTest.java        — Service 单元测试
{Module}ControllerTest.java         — Controller 契约测试 (@WebMvcTest)
{Module}RestContractTest.java       — HTTP 契约测试 (RestAssured)
{Module}MapperTest.java             — Mapper 集成测试 (Testcontainers)
```

### 2.2 测试方法命名

```java
<method>_<scenario>_<expected>()
// 正向:    approve_normal_状态变为APPROVED()
// 负向:    approve_自审拦截_制单人不能审核自己()
// 边界:    create_amount为0_抛出异常()
// 状态机:  submit_已提交不可重复提交_抛出异常()
```

### 2.3 测试类分组

```java
// ====================================================================
// 1. approve 审批（对应任务中的 audit）
// ====================================================================
// 正向
@Test void approve_normal_状态变为APPROVED() { ... }
// 负向
@Test void approve_状态不允许DRAFT_抛出异常() { ... }
@Test void approve_自审拦截_制单人不能审核自己() { ... }
```

---

## 3. 测试规范

### 3.1 正向断言模式

```
Given（准备数据）→ When（调用方法）→ Then（验证结果 + 负向验证不该发生的）
```

```java
// 正向：状态正确
assertEquals(BusinessDocStatus.APPROVED, entity.getStatus());
// 负向：不应生成凭证（审核 ≠ 制证铁律）
verify(voucherMapper, never()).insert(any(VoucherEntity.class));
```

### 3.2 负向断言模式

```java
BusinessException ex = assertThrows(BusinessException.class,
    () -> service.approve(DOC_ID, USER_ID));
assertTrue(ex.getMessage().contains("仅已提交状态可审批"));
// 负向：状态不变，未更新
verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
```

### 3.3 Mock 设置规范

| 规则 | 说明 |
|------|------|
| `when().thenReturn()` | 非 void 方法 |
| `doNothing().when()` / `doThrow().when()` | void 方法 |
| `lenient().when()` | 仅用于测试不关心的辅助 stub |
| `any()` 歧义 | 避免 `import static org.mockito.ArgumentMatchers.*`；用 `any(Entity.class)` 明确类型 |

### 3.4 被测试类正常行为验证

```java
// 正常 test
doAnswer(inv -> {
    BusinessDocEntity e = inv.getArgument(0);
    e.setId(999L);
    return 1;
}).when(docMapper).insert(any(BusinessDocEntity.class));
```

### 3.4.1 🔴 禁止 Mock「被测对象本身」（Mock 同义反复）

**反模式**（真实存在于本仓库 28 个类 / 140 个 `@Test`）：

```java
class CustomerMapperTest {
    @Test void insert_shouldAcceptValidParams() {
        CustomerMapper mapper = Mockito.mock(CustomerMapper.class);   // mock 被测对象本身
        Mockito.when(mapper.insert(entity)).thenReturn(1);            // stub 成期望值
        assertEquals(1, mapper.insert(entity));                      // 断言拿到该值
        Mockito.verify(mapper).insert(entity);                       // 断言 mock 被调用（恒真）
    }
}
```

**为什么零信号**：mock 的返回值由测试自己设定，断言只是确认「我写的桩被返回了」。
它**永远通过**，既不校验 SQL、也不校验约束、也不校验字段映射。实测危害：
`AutoGenerationServiceTest` 替**生产库根本不存在**的科目 `2203` 打桩，
导致该缺陷跨月潜伏，直到 REQ-127 服务器手工测试才暴露。

**判定规则**：若一个测试类 mock 的类型**就是它名字里的那类**（`XxxMapperTest` mock `XxxMapper`），
即为同义反复。处置二选一：

| 处置 | 适用 | 做法 |
|------|------|------|
| **改真库** | 该 Mapper 有实际业务价值 | 继承 `AbstractMapperTest`，断言 DB 真实行为 |
| **删除** | 纯 CRUD 样板，无业务规则 | 直接删；覆盖率数字会下降，但那是**真实**下降 |

真库版能断言 mock 版做不到的事（以 `CustomerMapperRealDBTest` 为例）：

- 插入后 id 由数据库生成、`created_at` 有值
- `uq_customer_code_enterprise` 唯一约束**真实生效**（重复 code 必失败）
- `deleteById` 是**软删除** —— 行仍在但 `deleted=1`，默认查询查不到（铁律 #12）
- 租户过滤真实生效 —— 企业 B 上下文看不到企业 A 的数据

#### Mock 同义反复台账（2026-10-01 实测）

| 项 | 数值 |
|----|------|
| 原规模 | 29 个类 / **145 个 `@Test`**（占全量 7.2%） |
| 已归零 | 1 个（`CustomerMapperTest` → `CustomerMapperRealDBTest`，5 假 → 7 真） |
| 剩余 | **28 个类 / 140 个 `@Test`** |
| 机械检查 | `grep -rl 'Mockito.mock' --include='*MapperTest.java'` 应逐批收敛 |

剩余 28 个按模块分组（无对应的 `*RealDBTest`，即完全无真库覆盖）：

| 模块 | 类 |
|------|---|
| `base/system` | `UserMapper` `RoleMapper` `MenuMapper` `DeptMapper` `SysConfigMapper` `VoucherTypeMapper` |
| `base/voucher` | `VoucherEntryMapper` `VoucherTemplateMapper` `VoucherTemplateLineMapper` |
| `base/masterdata` | `EmployeeMapper` `VendorMapper`（`CustomerMapper` 已归零） |
| `base/ai` / `base/report` | `AiTaskMapper` `ReportTemplateMapper` |
| `sme/arap` | `ArapSettlementMapper` `BusinessDocEntryMapper` `PrepaymentMapper` `ExpenseReimbursementMapper` `BadDebtProvisionMapper` |
| `sme/asset` | `AssetCategoryMapper` `AssetDepreciationMapper` `AssetDisposalMapper` |
| `sme/budget` | `BudgetMapper` `BudgetAdjustmentMapper` |
| `sme/cash` | `BankAccountMapper` `BankJournalMapper` `CashJournalMapper` `ClassificationRuleMapper` |
| `sme/tax` | `TaxDeclarationMapper` |

> 建议批次：`base/system` + `base/masterdata`（权限与客商，直接关系越权与应收应付）→
> `sme/cash` + `sme/arap`（资金）→ 其余。每批一个 PR，改完跑 L2。

### 3.5 避免测试假阳性

| 假阳性模式 | 防法 |
|-----------|------|
| 只测正向不测负向 | 每个方法至少 1 个负向断言 |
| Mock 测试覆盖不到 DB 约束 | 核心 Mapper 必须跑 Testcontainers |
| 方法签名变更不同步 | 修改 Service 签名后 `grep -r` 查所有调用点 |
| `@Transactional(REQUIRES_NEW)` 不可见 | 测试类用 `@Transactional(NOT_SUPPORTED)` |

---

## 4. 测试门禁（提交前 Checklist）

```markdown
## 提交前测试检查
- [ ] mvn test 全量通过（0 Failures, 0 Errors）
- [ ] 新增公共方法有正向 + 负向单元测试
- [ ] 修改 Service 签名 → 同步更新所有调用点（Controller + 测试）
- [ ] 涉及 DB schema 变更 → 三方对照（PG ↔ Entity ↔ 业务代码）
- [ ] 涉及状态机 → 覆盖非法状态转换场景
- [ ] 前端改动 → npx vite build 通过
```

---

## 5. 自动生成链测试

### 5.1 当前链条（高优先级）

| 链条 | 前端触发 | 后端产出 | 测试状态 |
|:----|:---------|:--------|:--------:|
| 进项发票确认 → 业务单 + 凭证 | 发票列表 → 确认 | t_business_doc + t_voucher | ✅ 后端有 |
| 销项发票确认 → 业务单 + 凭证 | 发票列表 → 确认 | t_business_doc + t_voucher | ✅ 后端有 |
| 银行流水确认 → 分类 → 业务单 → 凭证 | 流水列表 → 核准 | t_business_doc + t_voucher | ✅ 后端有 |
| 费用报销审批 → 凭证 | 报销单 → 审批 | t_voucher | ✅ 后端有 |
| 核销单确认 → 凭证 | 核销工作台 → 确认 | t_voucher | ✅ 后端有 |
| 期末结账 → 损益结转凭证 | 结账向导 → 执行结转 | t_voucher | ✅ 后端有 |
| 折旧计提 → 凭证 | 折旧计提 → 执行 | t_voucher | ✅ 后端有 |

### 5.2 缺失的测试（P2）

| 链条 | 缺失原因 | 建议 |
|:----|---------|------|
| 前端全链路 E2E | 无 Playwright 测试 | 先用 Playwright mock 覆盖核心流程 |
| 跨模块数据一致性 | 无跨 Service 集成测试 | 添加 L2 Testcontainers 测试 |

---

## 6. 覆盖率要求

| 指标 | 要求 | 工具 |
|------|------|------|
| 分支覆盖率 | ≥ 70% | JaCoCo `mvn jacoco:check` |
| 行覆盖率 | ≥ 80% | JaCoCo |
| 新增代码覆盖率 | ≥ 85% | 代码审查时人工检查 |

**CI 门禁：** `mvn verify` 积累覆盖率 → `mvn jacoco:check` 读已有 .exec 文件。

---

## 7. 前端测试（当前缺口）

前端测试当前为 **零**。建立路径：

| 优先级 | 类型 | 覆盖 | 建议工具 |
|--------|------|------|---------|
| P2 | 页面渲染 | 核心页面加载不报错 | Vitest + vue-test-utils |
| P2 | 组件交互 | 业务单据编辑、筛选 | Vitest |
| P3 | E2E Smoke | 登录→凭证→业务单据→报表 | Playwright |
| P3 | E2E Full | 全流程 + 自动生成链 | Playwright |

---

## 8. 常见陷阱

| 陷阱 | 后果 | 预防 |
|------|------|------|
| 只测 Happy Path | 无负向断言 | 每方法至少 1 个负向 |
| branch coverage 不达标 | line 100% 但分支漏网 | JaCoCo 强制 BRANCH ≥ 70% |
| `@WebMvcTest` 缺 MockBean | Context 启动失败 | 检查 Controller 构造函数 |
| 测试方法名与 MockMvc 静态 import 冲突 | 编译错误 | 方法名避免 `delete`/`post`/`get` |
| `@Transactional(REQUIRES_NEW)` 不可见 | 断言时数据不存在 | 测试类 `NOT_SUPPORTED` |
| 集成测试 cleanup 调不存在的 API | afterAll 超时 | 用 `psql` 直接 DB 清理 |
| L3 测试用 `@SpringBootTest` 不设 security | 401 误判 | 加 `@ActiveProfiles({"test","contract-test"})` |

---

## 9. 文档更新规则

- 每次 commit 后更新 AGENTS.md §0 硬数字
- 测试数量变化 ≥ 10 条时，更新 `TEST-STRATEGY.md` §0
- 新增测试模块时，更新 §5.1 自动生成链表格

---

> **文档结束。** 配套 Skill：[dy-测试方法](../.hermes/skills/software-development/dy-测试方法/SKILL.md) | [dy-测试门禁](../.hermes/skills/software-development/dy-测试门禁/SKILL.md)