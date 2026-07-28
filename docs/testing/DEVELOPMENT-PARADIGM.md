# 慧财开发范式 2.0 — 测试嵌入全生命周期

## 问题：现有流程的测试盲区

```
当前流程： 需求 → 设计 → 开发 → 审查 → 测试 → 归档
                                      ↑ 测试是最后一关
```

两次生产 bug 的原因是：
- 发票 500：`AuditLogEntity.username` 列不存在 → 测试时用 H2 发现不了
- 银行流水：CHECK 约束违反 → 测试时用 H2 发现不了

**根因不是测试覆盖不够，而是测试时机太晚 + 测试类型选错。**

## 修改方案：测试前置 & 分层嵌入

### 嵌入方式总览

```
Phase 1 需求探索 → 输出：测试策略（风险路径识别）
Phase 2 规划/契约 → 输出：测试契约（每层测试计划）
Phase 3 执行 → 动作：测试>代码（TDD）或 代码+测试（并行）
Phase 4 审查 → 守卫：CI 门禁（全部测试通过 + 覆盖率 + schema check）
Phase 5 归档 → 守卫：文档同步（含测试文档）
```

### Phase 1 需求探索 → 增加"测试策略"

**当前产出：** 结构化需求定义（问题/范围/非目标/验收标准）
**新增产出：** 测试策略小节（Risk Assessment section）

每一条需求，必须回答三个问题：

```
1. 这条需求的高风险路径是什么？
   - 有 @StatusChangeable 吗？  → 需要 Testcontainers 测试
   - 有 @Transactional 传播吗？ → 需要 Testcontainers 测试
   - 有 CHECK 约束关联吗？     → 需要 Testcontainers 测试
   - 有 @TableField(exist=false) 吗？ → 需要 Schema 检查
   - 都是普通 CRUD？            → 普通 @MockBean 测试即可

2. 用什么测试类型覆盖？
   - L1 Mock: 纯业务逻辑
   - L2 H2:   HTTP 参数绑定
   - L2 PG:   高风险路径（标红）
   - L5 E2E:  完整用户流

3. 验收标准可以用 BDD 写吗？
   - 能 → 写 Given-When-Then
   - 不能 → 至少写数据流 + 异常路径
```

**守卫 G1→2：** 需求文档中是否有 `测试策略` 或 `Risk Assessment` 小节？没有则不能进入 Phase 2。

### Phase 2 规划/契约 → 增加"测试契约"

**当前产出：** SPEC 文件（SDD 四段 + BDD 验收标准 + YAML 契约）
**新增产出：** 测试计划表（Target Test Plan table）

SPEC 中新增一个 section：

```markdown
## 测试计划

| 测试场景 | 层级 | 类型 | 风险 | 说明 |
|---------|------|------|------|------|
| 分页查询 | L2 | @MockBean | 低 | 验证参数绑定 |
| 新增 | L2 | @MockBean | 低 | 验证请求体解析 |
| 状态变更 | L2 | Testcontainers + PG | 🔴高 | 验证 CHECK 约束 + Aspect |
| 重复提交 | L2 | Testcontainers + PG | 🟡中 | 幂等守卫验证 |
| 完整用户流 | L5 | Playwright | 🟡中 | E2E 冒烟 |
```

**守卫 G2→3：** SPEC 中是否有 `测试计划` 小节？没有则不能进入审核门。

### Phase 3 执行 → 改为"测试驱动"

**当前：** 开发完成后跑测试验证
**修改：** 测试与代码同步或先行

**具体规则：**

| 场景 | 测试时机 | 方式 |
|------|---------|------|
| 新功能开发 | 测试先行 | TDD（先写测试，再写代码，再验证） |
| 已有功能增强 | 测试并行 | 先写修改部分的测试，再改代码 |
| Bug 修复 | 测试先行 | 先写 Regression 测试（重现 bug），再修，再验证 |
| 新增 Entity/DB 字段 | 测试先行 | 先写一条 Testcontainers 测试验证 insert/update，再改 schema |

**测试编写顺序（按优先级）：**

```
1. 先写 @SlowTest Testcontainers 测试（高风险路径） ← 这是真正能挡住 bug 的
2. 再写 @MockBean 测试（参数绑定）
3. 最后写 L5 E2E 测试（可选，只在关键用户流）
```

**Phase 3 守卫（执行中）：**
- 每次 git commit 前自动跑：
  - `Entity ↔ DB Schema 检查`（已有 `check-entity-schema.mjs`）
  - 新增的测试文件必须编译通过
- 有 `@StatusChangeable` 变更时必须写 Testcontainers 测试，否则 commit 拒绝

### Phase 4 审查 → 强化"CI 门禁"

**当前：** 代码审查 → 测试验证
**修改：** CI 门禁强制执行

**CI 流水线：**

```
Push → compile → L1+L2 H2 测试（快速） → JaCoCo 70% → 阻塞
                                                      ↓
Nightly → @SlowTest Testcontainers（慢速） → E2E → 报告
```

**CI 门禁规则：**

| 检查项 | 触发时机 | 通过条件 | 阻塞 |
|--------|---------|---------|------|
| 编译 | 每次 push | 编译成功 | ✅ |
| L1+L2 H2 测试 | 每次 push | 全部通过 | ✅ |
| JaCoCo 分支覆盖率 | 每次 push | ≥70% | ✅ |
| Entity-DB Schema | Pre-commit | 无新增不匹配 | ✅ |
| L2 Testcontainers | 夜构建 | 全部通过 | ❌（次日报告） |
| E5 E2E | 夜构建 | 全部通过 | ❌（次日报告） |
| 性能基线 | 夜构建 | 无退化 | ❌（次日报告） |

### Phase 5 归档 → 增加"测试文档"

**当前：** 设计文档同步 + 文档注册表更新
**新增：** 测试报告同步

**归档清单：**
- 新增测试文件是否已合入？
- 测试覆盖的分支路径是否在设计文档中标注？
- 如果有高风险路径（Testcontainers），确认文档已更新
- 文档注册表更新（含测试文件索引）

## 范式变更总结

| 阶段 | 当前 | 修改后 | 价值 |
|------|------|--------|------|
| Phase 1 需求 | 只定义需求 | 增加测试策略（风险识别） | 避免测试选错类型 |
| Phase 2 设计 | SPEC + BDD | 增加测试计划表 | 每条需求对应测试 |
| Phase 3 开发 | 做完再测 | 测试先行/并行 | 早发现、早修复 |
| Phase 4 审查 | 人工审查 + 跑测试 | CI 门禁强制 | 不可跳过 |
| Phase 5 归档 | 设计文档同步 | 测试文档同步 | 可追溯 |
| CI/CD | 手动 | 分层自动化（push+nightly） | 快慢分离 |

## 核心原则

**测试不是 Phase 4 的一个步骤，而是 Phase 1~5 的贯穿线。**