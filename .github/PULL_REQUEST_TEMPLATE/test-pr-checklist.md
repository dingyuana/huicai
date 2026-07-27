---
title: "Pull Request Template"
appname: Huicai Finance
description: "用于慧财财务项目的 Pull Request 描述模板"
labels: ["pull request template"]
---

# 📌 变更说明

**更改的文件/模块：**  
（列出受影响的代码文件、目录或模块，例如：`backend/src/main/java/com/huicai/sme/tax/service/impl/OutputInvoiceStateMachineServiceImpl.java`）

**更改的原因 / 背景：**  
（简要说明为什么需要这个变更，关联的 Issue 编号等）

---

# ✅ 测试相关检查清单（Opencode 自填）

请对照 **TEST-STRATEGY.md (HUICAI-TST-008)** 逐项确认，并在完成后打钩：

- [ ] **L1 单元测试（TDD 风格）**：新增/修改的核心方法至少有一条正向单元测（先于或伴随编码实现），本地 `mvn test` / `npm test` 通过
- [ ] **分支覆盖率**：该文件的 Java 单元测分支覆盖率 ≥ 70%（查看 JaCoCo 报告，CI 自动校验）
- [ ] **L2 集成测试**（如涉及 DB/Redis 写入）：已使用 Testcontainers 在本地验证，或通过 `mvn verify -DexcludedGroups=""`
- [ ] **L3 API 契约测试**（如暴露 REST 端点）：正向 + 边界 + 认证/权限校验已覆盖
- [ ] **Test Seed 支持**：提供了 `/api/test/seeder` 端点或 SQL seed 脚本，供 Hermes E2E 构造数据
- [ ] **E2E Smoke 标签**：如有用户界面变更，已在 Playwright spec 中标记 `@smoke` 或补充新 smoke 用例

---

### 🔍 Reviewer（Hermes）审查项

- [ ] L1/L2/L3 测试质量审查（正向测试存在性、负向断言完整性、覆盖率达标）
- [ ] CI 流水线验证：l1-unit-tests、l2-integration-test、performance-regression 全部绿色
- [ ] 测试覆盖无遗漏：确认新功能有对应的正向 + 边界测试
- [ ] 批准合并 👉 [ ] 拒绝并留评论 👉 [ ] 要求补充测试后重新提交

---

> 💡 **提示**：正向来不及补的，需在 PR 中说明原因和补救计划。参考完整规范：`docs/testing/TEST-STRATEGY.md`
