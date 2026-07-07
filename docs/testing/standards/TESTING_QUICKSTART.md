# 测试框架使用指南

> **编号**：HUICAI-TST-004
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

---

## 🚀 快速开始

### 日常开发（默认）

只跑快测试（无外部依赖，< 2 分钟）：

```bash
cd backend
mvn test
```

### 发布前验证

跑完整测试套件（需要 Docker，约 10-30 分钟）：

```bash
# 全部测试（快 + 慢）
mvn test -Dsurefire.excludedGroups=

# 只跑特定 Mapper 测试
mvn test -Dtest=PayableMapperTest -Dsurefire.excludedGroups=

# 只跑 Mapper 测试套件
mvn test -Dtest=MapperTestSuite -Dsurefire.excludedGroups=
```

### CI/CD 配置

| 触发时机 | 执行命令 |
|---|---|
| 每次提交 | `mvn test` (只跑快测试) |
| PR 合并前 | `mvn test -Dsurefire.excludedGroups=` (完整测试) |
| 每晚构建 | `mvn test -Dsurefire.excludedGroups= + jacoco 覆盖率报告` |

---

## 📦 测试分层架构

```
┌──────────────────────────────────────────────────────────────┐
│  L5 E2E 全链路测试 @SlowTest                                   │
│  Testcontainers + 真实数据库 + 完整业务流程                    │
├──────────────────────────────────────────────────────────────┤
│  L4 Mapper 真实 DB 测试 @SlowTest                              │
│  验证 SQL 正确性、约束、外键、精度                              │
├──────────────────────────────────────────────────────────────┤
│  L3 Controller 参数绑定测试 @FastTest                           │
│  Mock Service，验证参数注解、HTTP 方法、JSON 反序列化          │
├──────────────────────────────────────────────────────────────┤
│  L2 Service 层 Mock 测试 @FastTest                              │
│  Mock Mapper，验证业务逻辑、分支覆盖                            │
├──────────────────────────────────────────────────────────────┤
│  L1 工具类单元测试 @FastTest                                    │
│  纯 Java，无 Spring 上下文                                      │
└──────────────────────────────────────────────────────────────┘
```

---

## 🏷️ 测试标签系统

| 标签 | 注解 | 说明 | 默认执行 |
|---|---|---|---|
| `fast` | `@FastTest` | 不需要外部依赖的快测试 | ✅ 默认执行 |
| `slow` | `@SlowTest` | 需要 Testcontainers/Docker | ❌ 默认跳过 |

### 使用示例

```java
// 快测试 - 本地开发默认执行
@FastTest
@ExtendWith(MockitoExtension.class)
class XxxServiceImplTest {
    // 纯 Mock 测试，不需要数据库
}

// 慢测试 - 需要 Docker
@SlowTest
@SpringBootTest
@Testcontainers
class XxxMapperTest extends AbstractMapperTest {
    // 真实数据库测试
}
```

---

## 🧪 测试套件

| 套件类 | 说明 | 执行命令 |
|---|---|---|
| `FastTestSuite` | 所有快测试 | `mvn test -Dtest=FastTestSuite` |
| `FullIntegrationTestSuite` | 所有慢测试 | `mvn test -Dtest=FullIntegrationTestSuite` |
| `MapperTestSuite` | 所有 Mapper 真实 DB 测试 | `mvn test -Dtest=MapperTestSuite` |

---

## 📋 新增 Mapper 测试模板

参考 `docs/test/templates/MapperTestTemplate.java`

**关键步骤**：

1. 继承 `AbstractMapperTest`（自动获得 @SlowTest）
2. 在 `@BeforeEach` 中准备前置数据（科目→客户/供应商→业务数据）
3. 覆盖 5 个标准场景：插入、查询、更新、删除、边界值

```java
public class XxxMapperTest extends AbstractMapperTest {

    @Autowired
    private XxxMapper xxxMapper;

    @BeforeEach
    void setup() {
        // 1. 创建科目
        // 2. 创建客户/供应商
        // 3. 准备其他前置数据
    }

    @Test
    void insert_shouldReturnId() { }

    @Test
    void selectById_shouldReturnCorrectData() { }

    @Test
    void updateById_shouldUpdateCorrectly() { }

    @Test
    void deleteById_shouldSoftDeleteCorrectly() { }

    @Test
    void boundaryValue_shouldNotLosePrecision() { }
}
```

---

## ⚠️ 常见陷阱与最佳实践

### 1. 编码冲突

**问题**: V60 migration 已预置 `1001`/`1002`/`1122` 等常用科目编码

**解决**: 测试数据统一使用 `9999.xxxx` 编码范围

```java
// ❌ 错误
subject.setCode("1001");  // 已预置，唯一键冲突

// ✅ 正确
subject.setCode("9999.0001");  // 测试专用编码
```

### 2. 字段大小写

**问题**: `direction` 字段数据库约束为小写，但代码枚举使用大写

**解决**: 测试中使用小写，生产代码需注意转换

```java
// ❌ 错误
entity.setDirection("DEBIT");  // 违反 check constraint

// ✅ 正确
entity.setDirection("debit");
```

### 3. period 字段长度

**问题**: `period` 字段定义为 `varchar(6)`，不能带横杠

```java
// ❌ 错误
entity.setPeriod("2026-06");  // 长度 7 > 6

// ✅ 正确
entity.setPeriod("202606");
```

### 4. 外键依赖顺序

```
t_subject → t_customer/t_vendor → 业务表
```

必须先创建科目，再创建客户/供应商，最后创建业务数据。

---

## 🔍 已发现的数据库约束问题（Mock 测试永远发现不了）

| 模块 | 字段 | 问题类型 |
|---|---|---|
| 科目 | `direction` | check constraint 大小写不匹配 |
| 科目 | `code` | 预置数据编码冲突 |
| 应收/应付 | `period` | varchar(6) 长度限制 |
| 应付单 | `vendorId` | 外键约束 |
| 销售发票 | `invoiceType` | check constraint 只允许 3 个值 |
| 销售发票 | `status` | V46 migration 扩展到 8 个状态 |

---

## 📊 测试覆盖率目标

| 层级 | 最低覆盖率 | 备注 |
|---|---|---|
| Service 层 | ≥ 80% | 核心业务逻辑必须全覆盖 |
| Controller 层 | ≥ 60% | 所有接口参数绑定至少覆盖一次 |
| Mapper 层 | ≥ 50% | 核心 CRUD + 自定义 SQL |
| 整体 | ≥ 20% | 持续提升中 |

---

## 📁 目录结构

```
src/test/java/com/huicai/
├── common/test/
│   ├── AbstractMapperTest.java      # Mapper 测试基类
│   ├── FastTest.java                 # 快测试注解
│   └── SlowTest.java                 # 慢测试注解
├── suite/
│   ├── FastTestSuite.java            # 快测试套件
│   ├── FullIntegrationTestSuite.java # 完整集成测试套件
│   └── MapperTestSuite.java          # Mapper 测试套件
├── module/xxx/mapper/                # Mapper 测试
├── module/xxx/service/               # Service 测试
└── module/xxx/controller/            # Controller 测试

docs/test/
├── TESTING_STANDARD.md               # 测试规范主文档
├── TESTING_QUICKSTART.md             # 本文档
└── templates/                        # 测试模板文件
```

---

## 🐛 问题排查

### Testcontainers 启动失败

```bash
# 1. 检查 Docker 是否运行
docker ps

# 2. 清理旧容器
docker stop $(docker ps -q --filter "ancestor=pgvector/pgvector:pg16")
docker rm $(docker ps -aq --filter "ancestor=pgvector/pgvector:pg16")

# 3. 手动拉取镜像
docker pull pgvector/pgvector:pg16
```

### 测试运行很慢

- **原因**: 每个测试类启动一个独立的 PostgreSQL 容器
- **缓解**: 本地开发默认只跑快测试，慢测试在 CI 执行
- **优化**: 启用容器重用（`withReuse(true)`），所有测试共享一个容器

### 外键约束错误

- 检查 `@BeforeEach` 是否正确准备了所有前置数据
- 参考 `PayableMapperTest` / `OutputInvoiceMapperTest` 的完整示例
- 确认编码使用 `9999.xxxx` 范围，不与预置数据冲突

---

## 📝 维护指南

### 新增模块

1. 复制模板创建 Mapper 测试
2. 测试通过后添加到 `MapperTestSuite`
3. 更新 `TESTING_STANDARD.md` 的"已发现问题"清单（如有新发现）

### 新增数据库字段

1. 同步更新 Mapper 测试中的字段赋值
2. 检查是否有新的 check constraint / 外键约束
3. 真实 DB 测试验证后才能合并 PR

### Migration 变更

1. 所有 migration 变更必须通过真实 DB 测试验证
2. H2 测试不保证 PostgreSQL 兼容性，必须用 Testcontainers 验证
3. V60 之后新增预置数据，注意更新测试编码冲突列表

---

**祝测试愉快！ 🧪✨**
