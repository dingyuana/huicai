# T1 SPEC — BankStatement 数据隔离测试方案

> **版本**：1.0
> **状态**：待审核
> **最后更新**：2026-08-01
> **关联设计**：S-01 多租户架构与数据隔离、S-26 Agency 分支、P23 银行流水状态机

---

## 1. Why — 业务背景

BankStatement（银行流水）是慧财多租户架构下的核心财务数据，涉及企业银行账户、交易金额、客户/供应商等敏感信息。**多租户数据隔离是安全红线**：

- **当前状态**：`EnterpriseDataPermissionInterceptor` 已在 SQL 层自动注入 `WHERE enterprise_id = :currentId`，但现有 `DataIsolationAuditTest` 仅验证了 `BaseMapper.selectList` 层面的数据泄露（企业 B 的数据对企业 A 可见），**未覆盖** Service 层状态机方法、自定义 Mapper XML 查询、以及批量操作场景的数据隔离。
- **风险**：BankStatement 有 8 个状态转换方法（review/audit/approve/processManual 等），每个方法都可能绕过 enterprise_id 过滤。若某条路径未加隔离，企业 A 的操作可能修改企业 B 的流水，或企业 A 的查询返回企业 B 的数据。
- **不做会怎样**：跨企业数据泄露（合规风险）、跨企业操作篡改（业务风险）。

---

## 2. What — 范围与验收标准

### 功能范围

**覆盖的数据访问路径（6 类）：**

| # | 路径 | 说明 | 风险等级 |
|---|------|------|----------|
| 1 | Service 层状态机方法 | review/audit/approve/processManual 等，通过 Mapper 操作 DB | 高 |
| 2 | Service 层查询方法 | pageQuery/getDetail/classificationCounts | 高 |
| 3 | 自定义 Mapper XML 查询 | 若存在 XML 中手写 SQL，可能绕过拦截器 | 中 |
| 4 | 批量操作 | batchReview/batchAudit/batchGenerateVouchers | 高 |
| 5 | 导入（importFromCsv） | 导入时设置 enterprise_id 是否正确 | 高 |
| 6 | 逻辑删除（deleteStatement） | 删除时 enterprise_id 过滤是否生效 | 中 |

**不覆盖：**
- 前端 UI 级别的数据隔离（前端仅展示 Service 层返回的数据，不再单独验证）
- 其他 Entity 的数据隔离（已有 DataIsolationAuditTest 覆盖 Voucher/BusinessDoc/InputInvoice/OutputInvoice/AssetCard）
- 非 BankStatement 模块（ClassificationRule/AutoGeneration 等）

### 验收标准

- 所有 6 类数据访问路径均有对应的测试用例
- 每个测试用例验证：企业 A 的数据对企业 B 不可见（查询返回空/修改操作抛出异常）
- 每个测试用例验证：企业 A 的数据对企业 A 自身可见（正常返回）
- 使用 Testcontainers + AbstractMapperTest 基类（与现有 DataIsolationAuditTest 一致）
- 测试方法命名：`方法名_企业隔离_预期` 三段式

### BDD 场景

```gherkin
Feature: BankStatement 数据隔离
  As a 多租户系统
  I want 确保企业间的银行流水数据完全隔离
  So that 企业 A 不能看到或操作企业 B 的流水

  Scenario: 企业 A 查询银行流水，不应看到企业 B 的数据
    Given 企业 A 有 1 条银行流水，企业 B 有 1 条银行流水
    And 当前上下文为企业 A（enterpriseId=1）
    When 调用 pageQuery 查询
    Then 返回结果仅包含企业 A 的流水
    And 不包含企业 B 的流水

  Scenario: 企业 A 审核企业 B 的流水，应被拒绝
    Given 企业 B 有 1 条 reviewStatus=PENDING 的流水（id=99）
    And 当前上下文为企业 A（enterpriseId=1）
    When 调用 review(99L, userId)
    Then 应抛出 BusinessException 或返回空
    And 企业 B 的流水状态未改变

  Scenario: 批量操作不跨企业泄漏
    Given 企业 A 有 2 条流水，企业 B 有 1 条流水
    And 当前上下文为企业 A
    When 调用 batchReview 包含所有 3 条 ID
    Then 仅企业 A 的 2 条流水被确认
    And 企业 B 的流水状态不变
```

---

## 3. How — 技术方案

### 测试框架

| 维度 | 决策 |
|------|------|
| 基类 | `AbstractMapperTest`（Testcontainers + PostgreSQL 16 + Flyway 迁移） |
| 测试类 | `BankStatementDataIsolationTest`，包路径 `com.huicai.security`（与 `DataIsolationAuditTest` 同包） |
| 隔离方式 | 通过 `EnterpriseContextHolder.set(enterpriseId)` 模拟不同企业上下文 |
| 事务 | 每个测试方法独立事务，自动回滚 |
| 断言 | JUnit 5 Assertions，不使用 AssertJ |

### 核心测试用例

#### 3.1 Service 层状态机方法数据隔离

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 1 | `pageQuery_企业隔离_仅返回本企业数据` | 企业 A 查 pageQuery，企业 B 也有数据 | 结果只含企业 A 的 |
| 2 | `getDetail_企业隔离_跨企业ID返回空` | 企业 A 查企业 B 的流水 ID | 返回 null 或抛异常 |
| 3 | `review_企业隔离_跨企业流水被拒` | 企业 A 调用 review(企业B的流水ID) | 抛 BusinessException 或状态不变 |
| 4 | `audit_企业隔离_跨企业审核被拒` | 同上，audit 方法 | 抛 BusinessException |
| 5 | `approve_企业隔离_跨企业核准被拒` | 同上，approve 方法 | 抛 BusinessException |
| 6 | `processManual_企业隔离_跨企业处理被拒` | 同上，processManual 方法 | 抛 BusinessException |
| 7 | `classifySingle_企业隔离_跨企业分类被拒` | 同上，classifySingle 方法 | 抛 BusinessException |
| 8 | `updateClassification_企业隔离_跨企业修改被拒` | 同上，updateClassification 方法 | 抛 BusinessException |

#### 3.2 批量操作数据隔离

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 9 | `batchReview_企业隔离_仅处理本企业流水` | 企业 A 批量确认包含企业 B 的 ID | 仅企业 A 的状态变更 |
| 10 | `batchAudit_企业隔离_仅审核本企业流水` | 同上，audit 方法 | 仅企业 A 的状态变更 |
| 11 | `batchGenerateVouchers_企业隔离_仅生成本企业` | 同上，generateVoucher 方法 | 仅企业 A 的状态变更 |

#### 3.3 导入操作数据隔离

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 12 | `importFromCsv_企业隔离_导入数据enterpriseId正确` | 企业 A 导入 CSV | 导入数据的 enterprise_id 全部为 A |
| 13 | `importFromCsv_企业隔离_不同企业导入不互相影响` | 企业 A 和企业 B 先后导入 | 数据正确归属各自企业 |

#### 3.4 删除操作数据隔离

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 14 | `deleteStatement_企业隔离_跨企业删除被拒` | 企业 A 尝试删除企业 B 的流水 | 抛异常或企业 B 数据未删除 |

#### 3.5 拦截器注入验证

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 15 | `interceptor_企业隔离_EnterpriseContextHolder未设置_不拦截` | 未设置 context holder | 返回所有企业数据（超级管理员行为） |
| 16 | `interceptor_企业隔离_EnterpriseContextHolder设置_过滤生效` | 设置 context holder 后查询 | 仅返回本企业数据 |

#### 3.6 自定义 SQL 查询（如有 Mapper XML）

| # | 测试方法 | 场景 | 验证 |
|---|---------|------|------|
| 17 | `customSql_企业隔离_自定义查询也过滤enterpriseId` | 通过 Mapper 自定义方法查询 | SQL 中含有 enterprise_id 条件 |

### 关键实现模式

```java
@SlowTest
@DisplayName("BankStatement 数据隔离测试")
class BankStatementDataIsolationTest extends AbstractMapperTest {

    @Autowired private BankStatementMapper bankStatementMapper;
    @Autowired private BankStatementService bankStatementService;

    private BankStatementEntity createStatement(Long enterpriseId, String txType, BigDecimal amount) {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setTxDate(LocalDate.of(2026, 8, 1));
        entity.setTxType(txType);
        entity.setAmount(amount);
        entity.setCounterAccount("客户" + enterpriseId);
        entity.setSummary("货款");
        entity.setReviewStatus("PENDING");
        entity.setEnterpriseId(enterpriseId);
        bankStatementMapper.insert(entity);
        return entity;
    }

    @Test
    @DisplayName("pageQuery: 企业A查询不应返回企业B的流水")
    void pageQuery_企业隔离_仅返回本企业数据() {
        // 设置企业 A 上下文
        EnterpriseContextHolder.set(1L);
        BankStatementEntity bsA = createStatement(1L, "INCOME", new BigDecimal("1000"));

        // 切换为企业 B 上下文
        EnterpriseContextHolder.set(2L);
        BankStatementEntity bsB = createStatement(2L, "INCOME", new BigDecimal("2000"));

        // 企业 A 查询
        EnterpriseContextHolder.set(1L);
        IPage<BankStatementEntity> page = bankStatementService.pageQuery(
            null, null, null, null, 1, 100);

        // 验证：企业 A 只看到自己的数据
        assertTrue(page.getRecords().stream()
            .anyMatch(s -> s.getEnterpriseId() == 1L));
        assertTrue(page.getRecords().stream()
            .noneMatch(s -> s.getEnterpriseId() == 2L),
            "⚠️ 漏洞：企业B的流水被企业A查到");
    }

    // ... 其余测试方法类似
}
```

### 数据清理策略

每个测试方法：
1. `@BeforeEach` 清理测试数据：`mapper.delete(new LambdaQueryWrapper<>().eq(BankStatementEntity::getEnterpriseId, testEnterpriseId))`
2. `@Transactional` 确保方法级事务回滚
3. 测试结束后清理 `EnterpriseContextHolder`（`@AfterEach` 或 `finally` 块）

---

## 4. Gotchas — 边界与陷阱

### 4.1 EnterpriseContextHolder 未设置时的行为

当 `EnterpriseContextHolder.get()` 返回 `null` 时，`EnterpriseDataPermissionInterceptor` **不注入** enterprise_id 条件（超级管理员模式）。测试必须验证：
- 设置 context holder → 过滤生效
- 不设置 context holder → 不过滤（预期行为，非漏洞）

### 4.2 拦截器跳过共享表

`EnterpriseDataPermissionInterceptor.SHARED_TABLES` 包含 `t_user`、`t_role` 等系统表，拦截器不会在这些表上注入 enterprise_id。测试不应验证这些表。

### 4.3 自定义 Mapper XML 查询

如果 `BankStatementMapper.xml` 中有手写 SQL（如统计查询、关联查询），这些 SQL 会经过拦截器，但拦截器可能无法正确解析复杂 SQL（如 UNION、子查询）。需检查 XML 中是否有复杂 SQL 并针对性地测试。

### 4.4 批量操作的 ID 验证

`batchReview` 等方法接收 `List<Long> statementIds`，如果 Service 层没有对每个 ID 做 enterprise_id 校验，企业 A 可以通过传入企业 B 的 ID 来操作跨企业数据。这是高风险路径。

### 4.5 与现有测试的冲突

现有 `BankStatementAuditIntegrationTest` 和 `DataIsolationAuditTest` 都使用 `AbstractMapperTest` 基类。新测试类 `BankStatementDataIsolationTest` 应：
- 使用独立的测试数据前缀（避免与现有测试数据冲突）
- `@BeforeEach` 清理本测试类创建的数据

### 4.6 金额精度

测试中使用的金额保持 `BigDecimal`，不要使用 `double`/`float`。

### 4.7 测试方法命名

遵循 P7 规范的三段式：`方法_场景_预期`，例如 `pageQuery_企业隔离_仅返回本企业数据`。

### 4.8 测试运行标记

使用 `@SlowTest` 注解标记为慢测试（需要 Docker + Testcontainers），本地开发默认跳过。

---

## 慧财约束检查清单

- [x] 初始状态 = DRAFT（测试数据使用 PENDING 初始状态）
- [x] 审核流程由人工完成，系统不自动审核（测试验证的是数据隔离，非自动审核）
- [x] 涉及金额用 BigDecimal
- [x] 涉及多表操作加 @Transactional(rollbackFor = Exception.class)
- [x] 有对应的 BDD 正向 + 异常场景
- [x] 对称模块已检查（DataIsolationAuditTest 已有 Voucher/BusinessDoc/Invoice/AssetCard 的隔离测试）
- [x] 数据隔离已考虑（enterprise_id）
- [x] 非功能需求已考虑（测试运行时间：Testcontainers 启动约 30s，每个测试约 1-2s）