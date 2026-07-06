# Loop Engineering 架构规范（慧财财务）

## 三层 Loop 闭环

### 1. 编码行为循环（Code Review Loop）
```
OpenCode 提交代码 → Hermes 自动化测试 → 发现违规或 Bug → Hermes 通过 CLI 指令要求 OpenCode 重构
```

### 2. 规范自我进化循环（Skill Evolution Loop）
```
Hermes 测试发现坑点 → 沉淀到项目规范文档 → OpenCode 下次开发自动读取规范 → 避免同类错误
```

### 3. 记忆与状态实体化（Memory Persistence Loop）
```
测试失败记录 → 实体化为 Markdown 文件 → 通过文件系统跨 Agent 状态传递
```

---

## 目录结构

```
harness/
├── tasks/          # 任务定义（每个任务一个 Markdown 文件）
├── trace/          # OpenCode 执行日志与 trace
├── logs/           # Hermes 自动化测试日志
├── memory/         # 长期记忆（坑点、经验、规范进化记录）
├── scripts/        # CLI 调度脚本
└── ARCHITECTURE.md # 本文件
```

---

## 规范目录（只读给 OpenCode）

```
docs/
├── coding-conduct.md     # 编码规范（由 Hermes 维护，OpenCode 只读）
├── layer-architecture.md # 强制分层架构规范
└── security-policy.md    # 安全策略
```

---

## CLI 调度协议

### Plan Phase（规划阶段）
```bash
opencode plan --prompt "请阅读 harness/tasks/P40.md，结合 docs/ARCHITECTURE.md 规范，生成实施方案"
```

### Build Phase（执行阶段）
```bash
opencode build --prompt "方案已确认。请严格按照计划创建代码文件，并编写单元测试。完成后提交 Git Commit。"
```

### Test & Review Phase（测试审查阶段）
```bash
# Hermes 接管控制权，启动自动化测试
pytest backend/tests/
```

### Self-Healing Loop（自愈循环）
```bash
opencode build --prompt "测试失败。请阅读 harness/trace/test_failure.md，修复代码逻辑。"
```

---

## 强制分层架构（Layered Architecture）

### Layer 0：纯数据对象（DTO/Entity）
- 严禁导入任何业务逻辑
- 严禁调用外部服务
- 只包含字段定义和验证

### Layer 1：Repository/DAO（数据访问层）
- 只负责数据库交互
- 严禁包含业务逻辑
- Layer 1 只能导入 Layer 0

### Layer 2：Service（业务逻辑层）
- 核心业务逻辑
- Layer 2 只能导入 Layer 0 和 Layer 1
- 严禁直接导入 Controller（Layer 3）

### Layer 3：Controller（接口层）
- API 路由定义
- 请求响应转换
- Layer 3 只能导入 Layer 0 和 Layer 2

---

## 工具级别权限控制

### OpenCode 权限限制
- ✅ 可以修改：`backend/src/`、`frontend/src/`、`ai-service/app/`
- ❌ 禁止修改：`docs/`、`harness/`、`.git/`
- ✅ 可以创建新文件
- ✅ 可以运行测试
- ❌ 禁止直接 git push

### Hermes 权限限制
- ✅ 可以修改：`docs/`、`harness/`、所有代码
- ✅ 可以运行所有测试
- ✅ 可以 git commit/push

---

## 上下文隔离机制

### OpenCode 每次被唤起时，只读取：
1. 当前任务文件：`harness/tasks/PXX-xxx.md`
2. 相关规范文件：`docs/*.md`
3. 当前工作区代码
4. 禁止读取 Hermes 的内部状态

### Hermes 掌握：
1. 全局记忆（所有历史对话、所有测试结果）
2. 规范进化记录（`harness/memory/`）
3. 任务调度状态（`harness/tasks/`）

---

## 验收标准

每次任务完成必须满足：
1. ✅ 所有自动化测试通过（0 failure）
2. ✅ 代码符合分层架构规范
3. ✅ API 契约与 SPEC 一致
4. ✅ 没有违反安全策略
5. ✅ Git 提交符合规范

