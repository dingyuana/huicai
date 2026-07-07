# Mapper 层真实 DB 测试方案（Testcontainers）

> **编号**：HUICAI-TST-006
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部
**日期**：2026-06-27
**依据**：6/25 专家审计 Critical 第 6 条："真实持久层零测试，所有测试 @MockBean 持久层，Mapper/SQL 正确性从未验证"

---

## 一、现状评估

| 项 | 状态 |
|---|---|
| 总测试数 | 487 |
| Mapper 相关测试文件 | 53 个 |
| 真实 DB 测试 | 0 个 |
| 全部用 @MockBean | ✅ 100% |
| SQL 正确性从未验证 | ✅ |
| MyBatis-Plus 自动生成 SQL 覆盖 | 未知 |
| 自定义 XML SQL 覆盖 | 未知 |

---

## 二、方案设计

### 2.1 技术选型

- **容器**：Testcontainers PostgreSQL 16 模块
- **迁移**：Flyway（复用生产迁移脚本，保证表结构 100% 一致）
- **框架**：MyBatis-Plus Spring Boot Test（真实 Mapper，不 Mock）
- **数据隔离**：每个测试方法独立事务，自动回滚
- **基类模式**：`AbstractMapperTest` 统一容器初始化，子类只需注入 Mapper

### 2.2 依赖新增

```xml
<!-- pom.xml 新增，放在 <dependencies> -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```

### 2.3 基类设计

```java
@SpringBootTest
@Testcontainers
@Transactional
@AutoRollback
public abstract class AbstractMapperTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("huicai_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        // 禁用 H2，强制走 PG
        registry.add("spring.h2.console.enabled", () -> "false");
    }
}
```

### 2.4 试点 Mapper（首批 3 个）

| 优先级 | Mapper | 理由 | 测试场景 |
|---|---|---|---|
| P0 | VoucherMapper | 凭证核心，自定义 SQL 多 | insert / selectById / listByStatus / updateStatus |
| P0 | AccountMapper | 科目树，递归 SQL 复杂 | selectTree / listLedgerOnly / countChildren |
| P1 | ReceivableMapper | 应收核心，关联查询多 | insert / listByCustomerId / pageByStatus |

### 2.5 测试用例模板

```java
public class VoucherMapperTest extends AbstractMapperTest {

    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void insert_shouldReturnId() {
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-001");
        entity.setStatus("DRAFT");
        entity.setVoucherDate(LocalDate.now());
        
        int rows = voucherMapper.insert(entity);
        
        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("TEST-002");
        entity.setStatus("DRAFT");
        voucherMapper.insert(entity);
        
        // 再查询
        VoucherEntity found = voucherMapper.selectById(entity.getId());
        
        assertEquals("TEST-002", found.getVoucherNo());
        assertEquals("DRAFT", found.getStatus());
    }
}
```

---

## 三、实施路线图

| 阶段 | 内容 | 预计工作量 | 验收标准 |
|---|---|---|---|
| 1 | 新增 Testcontainers 依赖 + 基类编写 | 0.5h | 依赖引入无冲突，基类编译通过 |
| 2 | VoucherMapper 试点测试 | 1h | 3 个以上测试用例，全部通过 |
| 3 | AccountMapper 试点测试 | 1h | 树查询测试通过 |
| 4 | ReceivableMapper 试点测试 | 1h | 关联查询测试通过 |
| 5 | CI 集成配置 | 0.5h | GitHub Actions 中 Docker 可用，测试在 CI 全绿 |

**总计**：4 小时

---

## 四、风险与应对

| 风险 | 概率 | 影响 | 应对 |
|---|---|---|---|
| Flyway 在 Testcontainers 中迁移失败 | 中 | 高 | 先用 H2 兼容模式测试，再切 PG |
| CI 环境无 Docker 导致测试失败 | 高 | 中 | 在 CI YAML 中配置 `services: postgres` 作为备选 |
| 自定义 XML SQL 在真实 PG 中语法不兼容 | 中 | 高 | 发现一个修一个，记录到陷阱库 |
| 测试速度慢（容器启动开销） | 高 | 低 | 基类用 static 容器共享，避免每个类都启动 |

---

## 五、预期收益

1. **解决审计 Critical 第 6 条**：从 0% → 3 个核心 Mapper 真实 DB 覆盖
2. **验证 SQL 正确性**：发现 H2 与 PG 语法差异（已有 2 处已知差异）
3. **验证 MyBatis-Plus 自动生成 SQL**：确认 Entity 注解与 PG 类型映射正确
4. **验证自定义 XML SQL**：确认手写 SQL 在真实 PG 上执行结果符合预期

---

## 六、决策点

**老丁需确认：**
1. 是否按此方案立即实施？
2. 是否需要扩大试点范围（超过 3 个 Mapper）？
3. CI 集成是用 Testcontainers 还是用原生 PostgreSQL service？
