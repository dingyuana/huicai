# P8 SPEC — IntegrationTest H2 兼容性修复

> 状态：决策 SPEC（3 选 1）
> 问题根源：16 errors 全从 5 个 `*IntegrationTest` 类产生

---

## 1. 问题诊断

**当前配置**（每个 IntegrationTest 独立写死的）：
```java
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
```

**为什么跑不通**：所有 Entity 类用的 **MyBatis-Plus**（`@TableName`），**不是 JPA**（无 `@Entity`）。`ddl-auto=create-drop` 只对 JPA entity 生效 → 对 MyBatis-Plus 表全无效。H2 内空库 → 任何 `SELECT ... FROM t_xxx` 都炸。

**16 errors 分布**：

| 测试类 | errors | 模块 |
|---|---|---|
| ReportIntegrationTest | 6 | 报表 |
| BudgetIntegrationTest | 3 | 预算 |
| TaxIntegrationTest | 3 | 税务 |
| AssetIntegrationTest | 2 | 资产 |
| ArapIntegrationTest | 2 | 往来 |
| **合计** | **16** | **5 个类** |

**PG 特有语法**（32 个迁移中有 4 种不兼容 H2）：

| 语法 | 迁移 | H2 兼容性 |
|---|---|---|
| `ALTER COLUMN ... ADD GENERATED ALWAYS AS IDENTITY` | V28-V32 | H2 2.x 支持（PG 模式） |
| `COMMENT ON TABLE/COLUMN` | V1-V11 等 | H2 PG 模式支持 |
| `VECTOR(768)`（pgvector 扩展） | V10 | **完全不支持** |
| `COMMENT ON MATERIALIZED VIEW` + PL/pgSQL | V11 | 部分不支持 |

---

## 2. 三方案详细对比

### A. 写 H2 schema.sql（不推荐）

为 H2 单独写一份 `src/test/resources/schema.sql`，把 32 个迁移的 PG 语法去兼容化翻译成 H2 DDL。

**操作**：
1. 新建 `backend/src/test/resources/schema.sql`（约 2000 行）
2. 删 `COMMENT ON` / 换 `VECTOR` 为 `TEXT` / 处理 IDENTITY
3. 改 `@TestPropertySource` 加 `spring.sql.init.schema-locations=classpath:schema.sql`
4. 每个图 5 次：改单个 IntegrationTest 配置

**代价**：
- 2000 行 DDL 翻译 + 每次迁移更新
- **高维护成本** — 每次新增 migration 都要同步改 schema.sql
- 老丁投入：审核 2000 行 SQL（不可能）

**可靠性**：中 — 但同步问题是长期的。

---

### B. Flyway + H2 PG 模式（部分有效，不推荐）

启用 Flyway + 让 H2 以 PostgreSQL 兼容模式运行。

**操作**：
1. 改 `@TestPropertySource` 为：
   ```
   spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
   spring.flyway.enabled=true
   ```
   删 `ddl-auto` 行
2. **V10 必须跳过**：加 `spring.flyway.ignored-migrations=V10`
3. V28-V32 的 PL/pgSQL DO 块需改写成 H2 兼容 SQL

**代价**：
- V10（VECTOR）只能跳过——AI 附件表在测试中不可用
- PL/pgSQL DO 块需改写（但不用删除，只是兼容写法）
- H2 PG 模式不 100% 兼容 — 可能仍有个别迁移卡住

**可靠性**：低到中 — 先跑一遍才知道真正卡在哪，大概率要反复修 2-3 轮。

---

### C. 改 IntegrationTest 为 Mockito 单测（推荐）

把 5 个 `@SpringBootTest` 集成测试改成 `@ExtendWith(MockitoExtension.class)` 纯单元测试。

**操作**：
1. 每个 IntegrationTest 改成 Mockito 单测（模板完全照搬 P7/P9）
2. Service 层的 mapper 全部 mock
3. **保留测试方法名和断言逻辑**（不改业务测试语义）
4. 删 `@SpringBootTest` + `@TestPropertySource` + 启动上下文依赖

**代价**：
- 5 个文件 × 约 60 行平均 = **300 行重写**
- **做一次，永久不复发**
- 失去了"集成测试"名义（但这些测试本来就从未在 H2 上跑通过）

**可靠性**：高（已验证 P7/P9 全部 0 fail）。

---

## 3. 决策矩阵

| 维度 | A schema.sql | B Flyway+H2 | C 改单测 |
|---|---|---|---|
| 工作量 | 2000 行 | 5-8 行配置修改 | 300 行 |
| 维护性 | **差**（每次新迁移要同步） | **中**（新迁移可能又卡） | **好**（0 维护） |
| 可靠性 | 中 | 低（需迭代试错） | **高**（P7/P9 已验证） |
| 集成价值 | 保留集成测试语义 | 保留集成测试语义 | **失去集成语义** |
| 老丁投入 | 多（审核 SQL） | 中（试错决策） | **少**（看一次 diff） |
| 交付周期 | 2-3 小时 | 30 分钟+试错 | **1 小时** |

---

## 4. 推荐方案

**C**。理由：

1. **P7 已验证**：52 个 Mockito 用例 0 fail，模式成熟
2. **永久根治**：不再依赖 H2 兼容 PG 语法
3. **交付最快**：1 小时可完成
4. **老丁投入最少**：审核 diff 一次搞定

**C 的风险对冲**：
- 如果将来真的需要集成测试（端到端 DB 验证），应该用 **Testcontainers**（拉起真实 PG 容器）而非 H2
- Testcontainers 是 P10 工单，不在 P8 范围

---

## 5. 验收标准

1. `mvn test` → **Tests run ≥ 211, Failures: 0, Errors: 0**
2. 5 个 IntegrationTest 文件改名（去掉 `Integration` 后缀或保留原名但改用 Mockito）
3. 不改任何产品代码
4. **不走 Testcontainers**（P10 工单范围）
5. 原 IntegrationTest 的测试方法名和断言逻辑保留

---

## 6. 决策点

一、**A** — schema.sql（不推荐）
二、**B** — Flyway+H2 PG 模式（不推荐）
三、**C** — 改 IntegrationTest 为单测（**推荐**）
四、**自定义**

**我倾向 C**。答单字：**A / B / C / 自定义**。
