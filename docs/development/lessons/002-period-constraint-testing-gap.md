# 复盘：`t_period.period_code NOT NULL` 约束违反 — 测试盲区

> **编号**：HUICAI-DEV-033
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **分类**：测试策略 | **严重度**：🔴 可线上阻断

---

## 一、事件

2026-07-07，前端访问 `/basis/period` 页面时返回 HTTP 500，后端错误：

```
PSQLException: null value in column "period_code" of relation "t_period" violates not-null constraint
```

## 二、根因分析

| 层 | 原因 |
|----|------|
| **直接原因** | `t_period` 表为空(0条)，前端自动触发 POST 创建期间，但未传 `period_code` 字段 |
| **代码缺陷** | `PeriodServiceImpl` 继承 MyBatis-Plus 默认 `save()`，**没有自动生成 `period_code`** |
| **测试缺失** | **Period 模块完全没有测试文件**——零 Controller 测试、零 Service 测试、零 Mapper 测试 |

## 三、为什么测试体系没拦住

### 3.1 测试覆盖盲区

```
backend/src/test/
├── finance/      27 个测试文件  ← 核心业务
├── arap/         17 个测试文件  ← 核心业务
├── tax/           3 个测试文件
├── system/        1 个测试文件  ← Period 零测试！
├── budget/        2 个测试文件
└── ...
```

**system 模块 11 个 Service，仅有 1 个 SubjectMapperTest。**

### 3.2 Mock 测试永远发现不了

即使写了测试，如果用 Mock 方式：

```java
@MockBean
private PeriodMapper periodMapper;  // 压根不碰数据库
```

`periodMapper.insert(entity)` 被 mock 掉 → **永远不会检查 DB 约束**。

只有 **真实 DB 测试（Testcontainers）** 才能捕获这类问题：

```java
// 真实 DB 测试会发现
// INSERT INTO t_period (period_code, ...) VALUES (NULL, ...)
// → PSQLException: null value in column "period_code" violates not-null constraint
```

### 3.3 认知偏差

| 偏差 | 后果 |
|------|------|
| "基础模块是简单 CRUD，不需要测试" | ❌ 基础模块影响全局，断裂后全系统不可用 |
| "前端不会传空字段" | ❌ 前端行为不可控，必须有后端防御 |
| "MyBatis-Plus 默认就行" | ❌ 默认 save() 不会填补必填字段 |

---

## 四、修复措施

| 措施 | 文件 |
|------|------|
| 重写 `PeriodServiceImpl.save()` 自动生成 `period_code` | `system/service/impl/PeriodServiceImpl.java` |
| 初始化 36 条期间数据（2024-2026 每月 1 条） | 数据库种子 |
| 新增 PeriodService/Controller 真实 DB 测试 | `PeriodServiceMapperTest.java` |

---

## 五、通用防范规则

### 5.1 所有 Entity 的 NOT NULL 字段必须有防御

```java
// ✅ 正确：save() 时自动填补
public boolean save(PeriodEntity entity) {
    if (entity.getPeriodCode() == null) {
        entity.setPeriodCode(autoGenerate(entity));
    }
    return super.save(entity);
}
```

**检查清单**（每新增一个 Entity 时逐项核对）：
```
□ 所有 NOT NULL 字段在 save/create 前是否有默认值/自动生成？
□ 数据库约束（NOT NULL/CHECK/UNIQUE）是否在 Service 层有对应校验？
□ 前端 POST 缺字段时后端是否兜底？
```

### 5.2 每个 Entity 至少有一个真实 DB Mapper 测试

```java
@SpringBootTest
@Testcontainers
class PeriodMapperTest extends AbstractMapperTest {
    
    @Test
    void save_shouldAutoGeneratePeriodCode() {
        PeriodEntity entity = new PeriodEntity();
        entity.setYear(2026);
        entity.setMonth(7);
        // 故意不设 period_code，验证自动生成
        periodService.save(entity);
        assertNotNull(entity.getPeriodCode());
        assertEquals("202607", entity.getPeriodCode());
    }
    
    @Test
    void save_shouldFailWithoutRequiredFields() {
        PeriodEntity entity = new PeriodEntity();
        // 不设任何字段，验证校验失败
        assertThrows(Exception.class, () -> periodService.save(entity));
    }
}
```

### 5.3 测试优先级规则

```
P0: 每个 Entity 至少 1 个真实 DB 测试（验证 NOT NULL/CHECK/UNIQUE）
P1: 核心业务 Controller 参数绑定测试（验证前端缺字段时的行为）
P2: Service 层正向+负向测试
```

---

## 六、受影响模块检查（system 模块）

| Entity | NOT NULL 字段 | 是否有真实 DB 测试 | Service 是否自动填补 |
|--------|--------------|------------------|-------------------|
| t_period | period_code, year, month, start_date, end_date, status | ❌ → **本次新增** | ❌ → **本次修复** |
| t_subject | code, name, direction | ✅ SubjectMapperTest | — |
| t_user | username, password | ❌ | — |
| t_role | name, code | ❌ | — |
| t_customer | name | ❌ | — |
| t_vendor | name | ❌ | — |

> **system 模块共 15+ 个 Entity，目前仅有 1 个有真实 DB 测试。** 建议按 P0 优先级补充。

---

> **文档结束。**