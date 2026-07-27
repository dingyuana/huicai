慧财财务 — TDD 实践指南 (Team Internal)

版本：V1.0 | 最后修订：2026-07-27 | 适用范围：所有后端 Java / 前端 TypeScript 开发

---

## 一、什么是 TDD？

**Test-Driven Development（测试驱动开发）** 是一种软件开发方法，核心流程：

```
        ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
        │   RED        │──────▶│   GREEN     │──────▶│   REFACTOR   │
        │ 写失败测试   │       │ 最小实现    │       │ 重构优化     │
        │ (预期失败)   │       │ (只让测试过)│       │ (保持绿色)   │
        └──────┬─────┘       └──────┬─────┘       └──────┬─────┘
               │                    │                    │
               └───────(Repeat)─────┘
```

- **Red**：先写一个测试，预测代码的行为 → 此时测试应该**失败**（编译错误或断言失败）
- **Green**：写**最少**的代码让测试通过 → 不优化、不加新功能
- **Refactor**：在保证测试全绿的前提下重构代码 → 消除重复、提纯逻辑、改进命名

> **关键原则：** 每个小步后都运行测试，确保始终保持在 Green 状态。

---

## 二、什么时候该用 TDD？

### ✅ 推荐场景（强烈建议）

| 场景 | 理由 |
|------|------|
| **状态机/状态转换逻辑**（如发票状态 PENDING→CONFIRMED→VOUCHERED） | 状态流转复杂，边界多，TDD 能清晰定义每种情况的预期 |
| **算法/计算逻辑**（金额计算、税额计算、匹配算法） | 纯函数性质强，易于编写断言，测试即文档 |
| **新特性从零开始** | 先明确需求（测试），再逐步实现，避免过度设计 |
| **高风险/高复杂度模块** | 如凭证生成、核销算法、红冲逻辑等，容错率低 |

### ⚠️ 可以不强制的场景

| 场景 | 说明 |
|------|------|
| **简单的 CRUD 接口** | 已有成熟框架和模板，加测试反而冗余 |
| **快速原型探索** | 目的是验证可行性，不保证长期维护 |
| **一次性脚本/工具** | 不会长期维护，测试投入产出比低 |
| **已有充分回归覆盖的老旧代码** | Refactoring 时可配合 TDD 逐步改善，但不必从头改写 |

---

## 三、TDD 实操案例：reverseInvoice remark 格式

见 `OutputInvoiceStateMachineServiceImplTest.java` 中的新增测试（`reverseInvoice_existingRemark_appendsCorrectly` 等）。这是**事后补充测试**的例子——即使代码已存在，先用测试确认行为正确，也是一种 TDD 思维的应用。

真正的**完整 TDD 流程**示例（从空白开始）：

```java
// STEP 1: RED — 先写测试（此时 reverseInvoice 还未实现 remark 拼接逻辑）
@Test
void reverseInvoice_withExistingRemark_shouldAppend() {
    OutputInvoiceEntity original = new OutputInvoiceEntity();
    original.setRemark("Old note");
    // ... setup mock
    
    Long id = service.reverseInvoice(1L, 1L, "new reason");
    
    ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(...);
    verify(invoiceMapper).insert(captor.capture());
    // THIS WILL FAIL — the actual implementation may not set remark correctly
    assertEquals("Old note | [1] new reason", captor.getValue().getRemark());
}
// 运行测试 → FAIL (RED)

// STEP 2: GREEN — 添加最少代码使测试通过
// 在 reverseInvoice 方法中加入:
redInvoice.setRemark(appendReason(original.getRemark(), reason, userId));
// 或者如果 appendReason 不存在，临时硬编码返回 "Old note | [1] new reason"

// 运行测试 → PASS (GREEN)

// STEP 3: REFACTOR — 提取公共方法
// 将 remark 拼接逻辑提取到 private String appendReason(String, String, Long) 方法中
// 删除硬编码，调用新方法的版本
// 运行测试 → 仍然 PASS (仍为 Green)
```

---

## 四、TDD 编程 checklist（开发时自问）

在开始写一个新功能前，自问以下问题：

- [ ] 我是否已经明确了这个功能的**预期行为**（用测试语言表达）？
- [ ] 我是否先写了至少一条**正向**的测试用例？
- [ ] 这条测试在没有实现时是否能**失败**（验证测试有效性）？
- [ ] 我的实现是否是**足够简单**让测试通过？有没有过早优化？
- [ ] 我是否考虑了**边界情况**（null、空、极端值、非法状态）并写了相应测试？
- [ ] 实现后是否进行了**重构**（消除重复、改进命名、拆分方法）且所有测试仍通过？

---

## 五、TDD 与 CI/CD 集成

在 GitHub Actions 中，TDD 产出的单元测试应通过以下 gate：

```yaml
# .github/workflows/l1-unit-tests.yml
- name: Run Java Unit Tests
  run: cd/backend && mvn test
  
- name: Check Branch Coverage (≥70%)
  run: |
    cd/backend
    mvn jacoco:check  # 低于阈值构建失败
```

**PR 合并要求：**
- 所有 L1 测试必须 Green
- 分支覆盖率 ≥70%（新引入的代码行不应导致整体下降超过 5%）
- PR Checklist 中标注"新增/修改的核心方法至少有一条正向单元测"

---

## 六、常见 Pitfall & 避坑指南

| Pitfall | 表现 | 如何避免 |
|---------|------|----------|
| **测试写得比实现还复杂** | 先花大量时间设计测试架构，迟迟不写实现 | 最简单的 test 就能捕获 bug，先让 test 跑起来再说 |
| **Green 阶段过度设计** | 实现时就想"以后可能要用到"而加了额外逻辑 | 只满足当前测试的需要，[YAGNI](https://en.wikipedia.org/wiki/YAGNI) |
| **Refactor 时忘了跑测试** | 重构后某个测试 silently fail | 每次改动后立即运行相关测试，确保 Green |
| **只测 Happy Path** | 所有测试都是成功场景，没有负向断言 | 每个核心方法至少配一个负向测试（异常、非法输入） |
| **测试依赖外部资源** | 测试连真实 DB/网络，变得慢且不稳定 | 用 Mockito/PowerMock 隔离依赖，单元测应在内存中完成 |

---

## 七、学习资源

- 书籍：《Test-Driven Development: By Example》(Kent Beck) —— TDD 经典原著
- 视频：[Java TDD Workshop](https://www.youtube.com/results?search_query=tdd+java+workshop) 系列实战演练
- 项目内参考：`docs/testing/TDD-REVERSE-INV-DEMO.md`（本项目的 TDD 演示范例）
- Code Review 模板中已加入 TDD 检查项（PR Checklist）

---

## 八、本 Sprint (D) 行动项

| # | 任务 | Owner | 状态 |
|---|------|-------|------|
| D1 | 在 `OutputInvoiceStateMachineServiceImplTest.java` 中添加 reverseInvoice remark 格式的补充测试 | Opencode | ✅ Done (commit 1936ab3) |
| D2 | 编写本 TDD 指南文档 (`TDD-GUIDE.md`) | Hermes | ✅ Done (此文件) |
| D3 | 运行本地 `mvn jacoco:check` 确认分支覆盖率达标 | Opencode | ⏳ To verify |
| D4 | Code Review 时审查新增测试的质量（正向+负向、覆盖率贡献） | Hermes | ⏳ To do |

---

> 📌 **TDD 不是教条，而是思维习惯。** 不必每一条代码都严格遵循 Red-Green-Refactor，但在关键业务逻辑上坚持"测试先行"，能显著提升代码质量和可维护性。