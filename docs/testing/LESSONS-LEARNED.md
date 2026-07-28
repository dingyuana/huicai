# 测试体系经验教训总结

## 一、结构性缺陷：H2 不是 PostgreSQL

现有 36 个 `@SpringBootTest` Controller 测试用 H2 + `flyway.enabled: false`，**永远无法检测**：
- CHECK 约束违反（银行流水 bug 的根因）
- Entity ↔ DB 列映射错误（发票 500 的根因）
- Flyway migration 脚本错误
- PostgreSQL 专属语法错误

**教训：** 标注"集成测试"不意味着它真的能测集成。H2 在 schema 层面和 PG 有本质差异，用 H2 跑的测试只能叫"带 Spring Context 的单元测试"。

## 二、@MockBean 测试的边界

`@SpringBootTest + @MockBean` 模式只测 HTTP 参数绑定（`@PathVariable`、`@RequestParam`、`@RequestBody` 解析正确性），**不测**：
- Service 层业务逻辑
- Mapper ↔ DB 映射
- AOP 拦截（`@StatusChangeable` → `StatusChangeAspect`）
- 事务传播行为

**教训：** 97 条 Controller 测试降低了覆盖率数字，但真正能拦截新 bug 的只有 3 条 Testcontainers 测试。**覆盖率统计不能替代风险分析。**

## 三、风险优先级 > 覆盖率优先级

两条生产 bug 的根因路径：
```
audit() → updateById() → CHECK 约束违反 → 回滚         ← 银行流水
confirm() → updateById() → StatusChangeAspect → 
  AuditLogMapper.insert(username不存在) → 回滚          ← 发票 500
```

两条路径的共同特征：
- 涉及**多组件协作**（Controller → Service → Mapper → Aspect → DB）
- 只在**真实 PostgreSQL** 上才暴露
- 无法用 Mock 或 H2 检测

**教训：** 3 条覆盖高风险路径的 Testcontainers 测试 > 100 条覆盖低风险 CRUD 的 @MockBean 测试。测试投资应优先对准**状态变更、AOP 拦截、事务边界**这三类路径。

## 四、模板化 Controller 测试的收益与局限

`@SpringBootTest + @MockBean` 模式高度重复，适合批量生成。14 个 Controller 测试（97 条方法）可以在 2 小时内写完。但**这种高效是假象**——你在快速覆盖低价值路径。

**教训：** 模板化批量生成适合提升覆盖率统计数字，但不要在它上面花太多时间。真正的测试价值在于：
- 写 Testcontainers 基类（`AbstractMapperTest` 已存在）
- 识别高风险路径（@StatusChangeable、CHECK 约束、事务传播）
- 为每条高风险路径写 1-2 条真正的集成测试

## 五、可复用的工作流

```
新 Controller 开发 → 写 1 条 @SpringBootTest + @MockBean（参数绑定验证）
                   → 如果有状态变更 → 写 1 条 @SlowTest Testcontainers（真实 DB 验证）
                   → 如果有 AOP 拦截 → 写 1 条 @SlowTest Testcontainers（Aspect 链路验证）
```

这个模式在 14 个 Controller 中验证可行。关键投入在**第 2 和第 3 步**，第 1 步可以模板化。

## 六、当前测试体系的状态

```
152 个测试文件
├── 45 个 L1 单测（Mockito，纯业务逻辑）
├── 97 个 L2 @SpringBootTest（H2，测参数绑定）
├──  3 个 L2 @SlowTest（Testcontainers + PG，真正的集成测试）★ 最有价值
└──  7 个 L5 E2E（Playwright，测完整用户流）
```

**3 条 Testcontainers 测试是唯一能拦截新 DB 映射 bug 的防御。** 如果下一轮开发改了 Entity 或 DB schema，现有测试体系会再次漏掉。