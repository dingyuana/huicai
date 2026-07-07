# huicai 测试规范与开发指南

> **编号**：HUICAI-TST-005
> **版本**：v1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部  
**最后更新**: 2026-06-27  
**适用范围**: huicai 财务系统后端所有模块

---

## 一、测试分层规范

### 五层测试防御体系

```
┌─────────────────────────────────────────────────────────────┐
│  L5 E2E 流程测试 - 跨模块业务链路验证                        │
│  验证：发票→业务单据→核销→凭证 完整流程数据一致性           │
├─────────────────────────────────────────────────────────────┤
│  L4 真实 DB Mapper 测试 ✨ 新增                              │
│  验证：MyBatis SQL 正确性、数据库约束、外键、check constraint │
├─────────────────────────────────────────────────────────────┤
│  L3 Controller 参数绑定测试                                   │
│  验证：@RequestParam/@PathVariable/@RequestBody 注解正确性   │
├─────────────────────────────────────────────────────────────┤
│  L2 Service 层 Mock 测试                                      │
│  验证：业务逻辑分支正确性                                      │
├─────────────────────────────────────────────────────────────┤
│  L1 接口/路由覆盖检测（脚本层）                                │
│  验证：前后端 API 匹配、路由-组件匹配                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、各层测试规范

### 2.1 L1 接口/路由覆盖检测

**执行命令**：
```bash
# 接口覆盖检测
python3 scripts/check_api_coverage.py

# 路由覆盖检测
python3 scripts/check_route_coverage.py
```

**触发时机**：
- CI/CD 每次 push 自动执行
- 前端 API 变更后必须执行
- 后端 Controller 新增/修改后必须执行

**验收标准**：
- 接口匹配率 ≥ 95%
- 路由匹配率 = 100%

---

### 2.2 L2 Service 层 Mock 测试

**适用场景**：
- 纯业务逻辑验证
- 不需要真实数据库的场景
- 复杂分支逻辑验证

**模板文件**：`docs/test/templates/ServiceTestTemplate.java`

**关键注解**：
```java
@ExtendWith(MockitoExtension.class)
class XxxServiceImplTest {
    
    @Mock
    private XxxMapper xxxMapper;
    
    @InjectMocks
    private XxxServiceImpl xxxService;
    
    // 测试方法
}
```

**验收标准**：
- 核心业务方法覆盖率 100%
- 分支覆盖率 ≥ 80%

---

### 2.3 L3 Controller 参数绑定测试

**适用场景**：
- 参数注解验证
- HTTP 方法验证
- 参数类型转换验证

**模板文件**：`docs/test/templates/ControllerTestTemplate.java`

**关键注解**：
```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class XxxControllerTest {
    
    @Autowired
    private MockMvc mvc;
    
    @MockBean
    private XxxService xxxService;
    
    // 测试方法
}
```

**必须覆盖的测试场景**：
1. `@RequestParam` 默认值验证
2. `@PathVariable` 类型转换验证
3. `@RequestBody` JSON 反序列化验证
4. GET/POST/PUT/DELETE 方法验证
5. 分页参数验证（pageNum/pageSize）

**验收标准**：
- 每个 Controller 至少 5 个测试用例
- 所有 HTTP 方法全覆盖

---

### 2.4 L4 真实 DB Mapper 测试 ✨ 新增

**必测清单**（每个 Mapper 必须覆盖）：

| 测试类型 | 说明 | 示例 |
|---------|------|------|
| **正向插入** | 所有必填字段齐全时插入成功 | `insert(entity) → id != null` |
| **NOT NULL 校验** | 逐个省略必填字段，验证约束生效 | `setXXX(null) → insert throws Exception` |
| **UNIQUE 校验** | 重复唯一键应失败 | `insert(dup) → throws Exception` |
| **CHECK 约束校验** | 非法枚举值应失败 | `setStatus("INVALID") → throws Exception` |
| **外键校验** | 不存在的关联 ID 应失败 | `setXxxId(99999) → throws Exception` |
| **乐观锁校验** | `@Version` 版本号机制正常 | `update with stale version → OptimisticLockingFailureException` |

**适用场景**：
- MyBatis SQL 正确性验证
- 数据库约束验证（非空、外键、check constraint）
- 字段类型映射验证
- 事务边界验证

**⚠️ 核心价值：Mock 测试永远发现不了的问题**
1. `status` 字段 check constraint 枚举值不匹配
2. 外键关联数据不存在导致插入失败
3. varchar 长度超限截断
4. DECIMAL 精度丢失
5. Flyway migration 脚本语法错误

**模板文件**：`docs/test/templates/MapperTestTemplate.java`

**关键注解**：
```java
@SpringBootTest
@Testcontainers
@Transactional
class XxxMapperTest extends AbstractMapperTest {
    
    @Autowired
    private XxxMapper xxxMapper;
    
    // 测试方法
}
```

**必须覆盖的测试场景**：
1. ✅ insert 插入验证（所有必填字段）
2. ✅ selectById 查询验证
3. ✅ updateById 更新验证
4. ✅ deleteById 删除验证
5. ✅ 复杂自定义 SQL 验证（连表查询、分组聚合）

**验收标准**：
- 核心业务 Mapper 100% 覆盖
- 每个 Mapper 至少 4 个测试用例

---

### 2.5 L5 E2E 流程测试

**适用场景**：
- 跨模块业务链路验证
- 状态机全链路验证
- 数据一致性验证

**模板文件**：`docs/test/templates/E2EFlowTestTemplate.java`

**示例流程（销售流程）**：
```
创建销售发票 → 审核发票 → 生成业务单据 → 收款核销 → 生成记账凭证
```

**必须验证的一致性**：
1. 状态流转正确性（PENDING_CONFIRM → CONFIRMED → VOUCHERED）
2. 金额一致性（发票总金额 = 业务单据金额 = 凭证借贷金额）
3. 外键关联正确性（发票 ID = 业务单据 invoiceId）
4. 审计字段自动填充（createdAt/updatedAt/createdBy）

**验收标准**：
- 每个核心业务流程至少 1 个完整 E2E 测试
- 至少 3 个模块以上的链路验证

---

## 三、测试文件命名规范

| 测试类型 | 命名规范 | 存放路径 |
|---|---|---|
| Service 层测试 | `XxxServiceImplTest.java` | `src/test/java/**/service/impl/` |
| Controller 测试 | `XxxControllerTest.java` | `src/test/java/**/controller/` |
| Mapper 真实 DB 测试 | `XxxMapperTest.java` | `src/test/java/**/mapper/` |
| E2E 流程测试 | `XxxFlowE2ETest.java` | `src/test/java/**/e2e/` |
| 并发测试 | `XxxConcurrencyTest.java` | `src/test/java/**/concurrency/` |

---

## 四、测试执行规范

### 4.1 本地开发执行

```bash
# 执行全部测试
mvn test

# 只执行某一类测试
mvn test -Dtest="*MapperTest"
mvn test -Dtest="*ControllerTest"
mvn test -Dtest="*E2ETest"

# 排除 Testcontainers 测试（快速验证）
mvn test -Dtest="!*MapperTest,!*E2ETest"
```

### 4.2 CI/CD 执行规则

| 分支 | 执行范围 | 要求 |
|---|---|---|
| feature/* | 全部测试 | 所有测试必须通过 |
| develop | 全部测试 + 覆盖率检查 | 指令覆盖率 ≥ 15% |
| main | 全部测试 + 性能基准测试 | 全绿才可发布 |

### 4.3 提交代码前必须检查

```bash
# 1. 运行核心测试（排除最慢的 Testcontainers）
mvn test -Dtest="!*MapperTest,!*E2ETest,!*ConcurrencyTest"

# 2. 运行接口覆盖检测
python3 scripts/check_api_coverage.py

# 3. 确认没有破坏现有测试
# 通过率 = 100% 才可提交 PR
```

---

## 五、新增代码必须编写的测试

### 5.1 新增业务模块必须包含

| 测试类型 | 最少用例数 | 责任人 |
|---|---|---|
| Service 层测试 | 每个方法 ≥ 1 个 | 开发工程师 |
| Controller 测试 | 每个端点 ≥ 1 个 | 开发工程师 |
| Mapper 真实 DB 测试 | CRUD 4 个 + 复杂 SQL 1 个 | 开发工程师 |

### 5.2 修改代码必须补充的测试

| 修改类型 | 测试要求 |
|---|---|
| 修改 status 状态机 | 必须补充状态流转测试 |
| 新增/修改数据库字段 | 必须补充 Mapper 真实 DB 测试 |
| 修改 Controller 参数 | 必须更新 Controller 参数绑定测试 |
| 跨模块流程变更 | 必须补充 E2E 流程测试 |

---

## 六、Testcontainers 最佳实践

### 6.1 加速测试技巧

```java
// 1. 重用容器（测试类之间共享同一个 PostgreSQL 实例）
@Testcontainers(parallel = true)
public abstract class AbstractMapperTest {
    
    @Container
    protected static final PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("pgvector/pgvector:16")
            .withReuse(true);  // ✅ 启用容器重用
}

// 2. 排除慢测试快速验证
mvn test -Dtest="!*MapperTest,!*E2ETest"
```

### 6.2 测试数据准备原则

1. **每个测试方法独立准备数据** - 避免测试间互相影响
2. **事务自动回滚** - `@Transactional` 注解保证测试后数据清理
3. **最小数据原则** - 只准备测试必需的最小数据集
4. **预置基础数据** - 客户、科目等基础数据在 `@BeforeEach` 中统一准备

### 6.3 ⚠️ V60 预置数据与测试隔离

**问题背景**：
V60__seed_common_subjects_and_templates.sql 已预置大量生产级基础数据：
- **科目编码**：1001(库存现金)、1002(银行存款)、1122(应收账款)、2202(应付账款)、6001(主营业务收入)、6602(管理费用) 等 20+ 个常用科目
- **凭证模板**：销售开票、收款核销、付款核销、工资计提、差旅费报销等 16+ 个常用模板

**冲突后果**：
测试数据使用上述编码会触发数据库唯一键约束异常，且 Mock 测试无法发现此类问题（因为 Mock 不连真实数据库）。

**解决方案**：
测试数据统一使用 `9999.xxxx` 编码范围，完全避开预置数据范围。

| 字段 | 错误写法 ❌ | 正确写法 ✅ |
|---|---|---|
| 科目编码 | `"1001"` / `"1002"` | `"9999.0001"` |
| direction | `"DEBIT"` / `"CREDIT"` | `"debit"` / `"credit"` |
| period | `"2026-06"` | `"202606"` (varchar(6)) |

### 6.4 🔍 真实 DB 测试发现的问题清单

Mock 测试**永远无法发现**的数据库约束问题，真实 DB 测试已捕获：

| 模块 | 字段 | 问题类型 | 发现时间 |
|---|---|---|---|
| 科目 (Subject) | `direction` | check constraint 大小写不匹配 | 2026-06-27 |
| 科目 (Subject) | `code` | 预置数据编码冲突 (1001/1002 已存在) | 2026-06-27 |
| 业务单据 (BusinessDoc) | `period` | varchar(6) 长度限制，不能用 `"2026-06"` | 2026-06-27 |
| 业务单据 (BusinessDoc) | `customerId/vendorId` | 外键约束 | 2026-06-27 |
| 销售发票 (OutputInvoice) | `invoiceType` | check constraint 只允许 SPECIAL/PLAIN/CUSTOMS | 2026-06-27 |
| 销售发票 (OutputInvoice) | `status` | V46 migration 已从 4 状态扩展到 8 状态 | 2026-06-27 |

### 6.5 ⚓ 外键依赖关系梳理

新增/修改 Mapper 测试时必须先准备前置数据：

| 表 | 依赖表 | 备注 |
|---|---|---|
| t_business_doc | t_customer/t_vendor | customerId/vendorId 外键 |
| t_vendor | t_subject | subjectId 外键 |
| t_customer | t_subject | subjectId 外键 |
| t_output_invoice | t_customer | customerId 外键 |
| t_input_invoice | t_vendor | vendorId 外键 |
| t_bank_statement | t_bank_account | bankAccountId 外键 |

**测试数据准备顺序**：`t_subject` → `t_customer/t_vendor` → `t_business_doc` → 业务表

---

## 七、测试质量门禁

| 检查项 | 阈值 | 不达标后果 |
|---|---|---|
| 测试通过率 | = 100% | ❌ 禁止合并 PR |
| 新增代码覆盖率 | ≥ 80% | ❌ 禁止合并 PR |
| Mapper 测试覆盖 | 核心 Mapper 100% | ⚠️ 警告 |
| Controller 测试覆盖 | 核心 Controller 100% | ⚠️ 警告 |
| E2E 流程测试 | 每个核心流程 1 个 | ⚠️ 警告 |

---

## 八、附录：模板文件索引

| 模板文件 | 路径 | 说明 |
|---|---|---|
| ServiceTestTemplate.java | `docs/test/templates/ServiceTestTemplate.java` | Service 层 Mock 测试模板 |
| ControllerTestTemplate.java | `docs/test/templates/ControllerTestTemplate.java` | Controller 参数绑定测试模板 |
| MapperTestTemplate.java | `docs/test/templates/MapperTestTemplate.java` | Mapper 真实 DB 测试模板 |
| E2EFlowTestTemplate.java | `docs/test/templates/E2EFlowTestTemplate.java` | 跨模块 E2E 流程测试模板 |
| AbstractMapperTest.java | `src/test/java/com/huicai/common/test/AbstractMapperTest.java` | 真实 DB 测试基类 |

---

## 九、测试历史里程碑

| 日期 | 版本 | 里程碑 |
|---|---|---|
| 2026-06-27 | v1.0 | 五层测试防御体系建立，真实 DB Mapper 测试上线 |
| 2026-06-20 | v0.9 | 487 测试基线稳定，Surefire + JaCoCo CI 集成 |
| 2026-06-15 | v0.8 | Controller 并发测试框架上线 |

---

**本规范自发布之日起强制执行，所有新提交的代码必须符合上述测试要求。**
