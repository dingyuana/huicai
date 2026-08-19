# 测试策略与规范

> **编号**：HUICAI-TEST-001
> **版本**：V1.0 | **日期**：2026-08-19 | **作者**：Hermes
> **关联文档**：[项目说明](../CORE-项目说明.md)、[技术方案](../CORE-技术方案.md)
> **关联 Skill**：`dy-测试方法`（分层策略、pitfall 库）、`dy-测试门禁`（完成前验证）

---

## 0. 当前测试状态

| 维度 | 数据 |
|------|------|
| 后端测试 | 1479 个 `@Test` 方法，0 Failures，0 Errors |
| 测试框架 | JUnit 5 + Mockito + Testcontainers |
| 前端测试 | 零（无 Vitest/Jest 测试） |
| E2E 测试 | Playwright 骨架存在，无持续运行 |
| 覆盖率门禁 | JaCoCo 配置就绪，branch ≥ 70% |
| CI | 无自动 CI（本地 `mvn test` 前置） |

---

## 1. 测试分层（L1-L5）

```
L5: Full E2E (nightly)                 ← 全量 Playwright，耗时 ~15min
L4: E2E Smoke (PR)                     ← 核心流程验证，@smoke 标签，~8min
L3: API Contract (MockMvc/RestAssured)  ← 控制器层契约，正向+边界+认证，~3min
L2: Integration (Testcontainers)       ← DB/Redis 交互，真实数据，~5min
L1: Unit Test (JUnit 5)                ← 每 commit 触发，纯逻辑，<2min
```

### 1.1 L1 单元测试 — 每 commit 强制执行

| 规则 | 说明 |
|------|------|
| 范围 | Service、Mapper、Util 纯逻辑 |
| 工具 | JUnit 5 + Mockito |
| 覆盖 | 每个公共方法 ≥ 1 条正向 + 1 条负向断言 |
| 命名 | `<method>_<scenario>_<expected>()`，例 `approve_selfReview_throws()` |
| 门禁 | `mvn test` 0 fail 方可提交 |

**不可 mock 的测试：** MyBatis-Plus Mapper 查询（用 Testcontainers）。
**必须 mock 的测试：** 外部依赖（Redis、第三方 API）。

### 1.2 L2 集成测试 — 涉及 DB/Redis 写入

| 规则 | 说明 |
|------|------|
| 范围 | Mapper 查询、Service 事务、Flyway 迁移 |
| 工具 | Testcontainers（PostgreSQL 16） |
| 触发 | 修改 SQL/Entity/Service 事务逻辑时 |
| 数据 | 每个测试用唯一前缀，`afterAll` 按前缀清理 |

### 1.3 L3 API 契约测试 — 控制器层

Track A（轻量，推荐）：`@WebMvcTest` + MockMvc
Track B（真实 HTTP）：`@SpringBootTest(webEnvironment=RANDOM_PORT)` + RestAssured

| 规则 | 说明 |
|------|------|
| 范围 | 每个 REST 端点正向 + 边界 + 非法状态 + 认证 |
| 状态机端点 | 每个状态转换必须覆盖成功 + 非法状态两个场景 |
| `@WebMvcTest` 适用 | 构造函数依赖 ≤ 5 个，无自定义 `@Component` 过滤器 |
| `@SpringBootTest` 适用 | 依赖 ≥ 6 个，或复杂安全过滤器 |
| 响应断言 | 断言 `code`（业务码），不是 HTTP 状态码 |

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