# P104 SPEC — 测试门禁与成色整改（main 真库门禁 + 同义反复归零）

> **版本**：V1.0（草案，待老丁审核） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P104 | 优先级：**P0（商用门槛）** | 状态：📋 待审核
> **来源**：P101 总纲 M1/M5；测试代理 D 实测「main 门禁只跑 Mock」「29 个同义反复」
> **关联需求**：REQ-2026-131 | **前置**：无（可与 P102 并行） | **test_ref**：`l2-integration-test.yml` 门禁验证、覆盖矩阵统计
> **排除**：AI 功能测试

---

## 0. 缺陷背景（已实测）

| # | 缺陷 | 证据 |
|---|---|---|
| 1 | **main 的 CI 门禁只跑 Mock**：`l2-integration-test.yml`（真库套件）只监听 `develop`；`full-stack-test.yml` 跑 `mvn test`（默认排除 slow） | 两 workflow 实测 `branches: [develop]` vs `[main,develop]` |
| 2 | 真库套件预拉**错误镜像**：`docker pull mysql:8.0.36 / redis:7.2.4`，而实际用 `pgvector/pgvector:pg16` | workflow L29-30 |
| 3 | **29 个 `*MapperTest` 是同义反复**：`Mockito.mock(Mapper)` 后断言自己 stub 的返回值，**零 SQL 验证**（145 用例，占 7.3%），而命名让人误以为有 mapper 覆盖 | `BudgetMapperTest.java:14` 等，复核计数 29 |
| 4 | `BudgetMapperTest` 夹具违反 CHECK：`setBudgetType("OPERATION")`，`chk_budget_type` 无此值 | 夹具 + migration 对照 |
| 5 | 负向断言密度不足：`assertEquals` 2117 : `assertFalse` 94，`assertNotNull(result)` 387 处 | 全库统计 |
| 6 | 死资产：3 个 `.removed.ts`、3 个永不执行 `@Suite`、1 条指向不存在文件的 surefire `<exclude>`、`@FastTest` 0 使用 | 配置文件实测 |
| 7 | `TEST-STRATEGY.md` 严重过期（1776@Test/「CI 无」/「E2E 零」全与事实相反） | 文档 vs 实测 |

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| 测试分组 | surefire `excludedGroups=slow` | `slow` 实为「需 Docker」；`full-stack-test` 跑快测，`l2` 跑真库 |
| 测试镜像 | Testcontainers | 须 `pgvector/pgvector:pg16`，与 `AbstractMapperTest` 一致 |
| 测试资产 | `*MapperTest` | 同义反复类须改真库或删除，禁保留「命名像集成测试的 mock 自测」 |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 门禁 | main PR 上真库套件（`-DexcludedGroups=`）必跑，**失败即红**（推一次故意失败验证） |
| 成色 | 同义反复 `*MapperTest` 归零；核心模块真库覆盖 ≥60% |
| 夹具 | 无违反 CHECK/外键的夹具 |
| 文档 | `TEST-STRATEGY.md` 与实际一致（分层/覆盖/门禁/硬数字） |

## 3. 状态流转

```
PR → full-stack-test(L1 快测 1742) ─┐
                                   ├─ 全绿 → 可合并；任一红 → 阻断
    → l2-integration-test(真库) ────┘   (现仅 develop；改为主干必跑)
```
L1 拦 Mock 回归，L2 拦真库缺陷（历史上 136 项慢测缺陷均由真库测出）。

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| CI 拉镜像失败 | 拉正确 `pgvector` 镜像；`RYUK_DISABLED` 加速清理 |
| 真库套件超时 | `timeout-minutes` 调优；分片并行 |
| 同义反复改造后暴露真实失败 | 逐项修生产或测试，**不放宽断言**（REQ-124 教训） |

## 5. BDD 行为契约

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-104-1 | main 分支 PR | CI 触发 | L2 真库 job 执行（不再只在 develop） | CI |
| AT-104-2 | L2 job | 执行 | 预拉 `pgvector/pgvector:pg16` 成功 | CI 日志 |
| AT-104-3 | 真库套件有失败 | 提交 | PR 变红（门禁有效的唯一证明） | CI |
| AT-104-4 | 29 个同义反复类 | 改造 | 均 `extends AbstractMapperTest` 或删除，真实执行 SQL | 计数 |
| AT-104-5 | `BudgetMapperTest` | 改造 | 无 `OPERATION` 违规夹具，预算 CHECK 被真库验证 | 真实 DB |
| AT-104-6 | 新增核心模块测试 | 评审 | 含负向断言（assertFalse/assertNull） | 抽检 |

## 6. 竞品对标（铁律 #15）

| 维度 | 用友 CI | 金蝶 CI | SAP | Xero | QB | 本修法 |
|---|---|---|---|---|---|---|
| PR 必跑真库集成 | ✅ | ✅ | ✅ | ✅ | ✅ | L2 上主干 |
| 覆盖率门禁 | branch≥70 | 高 | 高 | 高 | 中 | 保持 INSTRUCTION/BRANCH≥70，提 line 目标 |
| 端到端 | ✅ | ✅ | ✅ | ✅ | ✅ | 122 个 Playwright 接 CI（另议） |

**结论**：主流项目 PR 门禁必含真实数据库集成测试；本项目当前 main 只跑 Mock，**历史上 136 项真库缺陷从未在 PR 阶段被拦截**，是体系性缺口。

## 7. 风险与不在范围

- **门禁加严致历史 PR 大面积变红** → 先只加触发分支，变红逐项修而非放宽断言。
- **不在范围**：前端 122 个 Playwright 接入 CI（另立）、AI 测试、性能基线阈值重定义。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：main 真库门禁 + 29 个同义反复归零 + 夹具合规 + 负向断言规范 |
