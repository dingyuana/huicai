# 慧财财务 AI 演进 — 闭环开发流程规范

> **编号**：HUICAI-DEV-029
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

## 1. 完整开发循环

```
设计架构文档 → 写 SPEC + YAML 契约 → 老丁审核 → OpenCode 开发 → Hermes 验证 → commit → push
       ↑                                                                    ↓
       └──────────────────  问题反馈与修复（loop engineer）  ────────────────────────┘
```

### 角色分工

| 角色 | 职责 |
|------|------|
| **Hermes（Harness Engineer）** | 制定计划、写 SPEC、审核验证、协调流程 |
| **OpenCode（Execution Worker）** | 按 SPEC 开发代码 |
| **老丁（Human Manager）** | 审核 SPEC、验收成果、做最终决策 |

---

## 2. 核心 SPEC 规范（含 YAML 契约）

### 2.1 SPEC 结构

每个 SPEC 文档包含两部分：
```
# 人类可读的 SPEC（上半部分）
---
# 机器可读的 YAML 契约（下半部分）
```

### 2.2 YAML 契约结构（参考 SPEC-CONTRACT-SCHEMA.md）

```yaml
contract_version: "1.0"
entity: EntityName
module: module_name
table: t_table_name

# API 端点契约
endpoints:
  - method: GET
    path: /api/v1/xxx
    description: "描述"
    request: RequestDTO
    response: ResponseVO
    test_ref: test_name

# 数据库契约
schema:
  - column: id
    type: BIGINT
    description: "主键"

# 验收测试
acceptance_tests:
  - id: AT-001
    description: "描述"
    assertion: "条件"
```

---

## 3. 开发步骤标准化

### 3.1 启动新任务

1. Hermes 写 SPEC + YAML 契约
2. 提交到 git（`docs/specs/PXX-xxx.md`）
3. 老丁审核 SPEC
4. Hermes 使用 OpenCode 委派开发

### 3.2 OpenCode 开发

```bash
opencode --context /data/disk/huicai --task-file /data/disk/huicai/docs/specs/PXX-xxx.md
```

### 3.3 Hermes 验证

1. 运行测试
2. 验证 API 契约
3. 检查数据库 schema
4. 验证业务逻辑

### 3.4 Loop Engineer 闭环

发现问题 → 修正 SPEC → 重新开发 → 再次验证 → 直到通过

---

## 4. Git 提交规范

- **SPEC 提交**：`docs(PXX): 添加 xxx 功能 SPEC`
- **代码开发**：`feat(PXX): 实现 xxx 功能`
- **修复**：`fix(PXX): 修复 xxx 问题`

---

## 5. 当前待办

- [ ] 清理当前工作区
- [ ] 为任务 1（AI 服务骨架增强）写 SPEC + YAML
- [ ] 老丁审核 SPEC
- [ ] OpenCode 开发
- [ ] Hermes 验证
