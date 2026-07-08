---
name: doc-consistency-guardian
description: 代码变更后自动检测设计文档是否过时，生成差距报告，推动文档同步更新
trigger: 每次 commit 后自动执行，或手动触发
---

# 文档一致性守护者（Doc Consistency Guardian）

## 核心思想

代码变更 → 自动检测涉及的设计文档 → 检查版本/字段/API/状态是否过时 → 生成差距报告 → 推送更新提醒

## 检测规则

### 规则 1：Flyway migration 变更 → 检查对应 Entity + 设计文档

```
migration 新增/修改了 t_business_doc 的列
→ 检查 BusinessDocEntity.java 的 @TableField 是否同步
→ 检查 docs/design/02-arap-design.md 数据模型章节是否更新
→ 检查 docs/architecture/NUMBERING_ASSOCIATION.md 关联字段是否更新
```

### 规则 2：Entity 字段变更 → 检查 DB 列 + 设计文档 + 测试

```
Entity 新增了字段
→ 检查 DB migration 是否有对应列创建
→ 检查 @TableField(value=) 或 @TableField(exist=false)
→ 若字段类型是 String 但 DB 是 JSONB → 必须有 typeHandler
→ 检查 docs/design/ 对应模块文档是否更新
→ 检查 OutputInvoiceMapperTest 等 Mapper 测试是否覆盖
```

### 规则 3：API 端点变更 → 检查 SPEC 文档 + 前端 API 调用

```
Controller 新增/修改了 @GetMapping/@PostMapping
→ 检查 docs/specs/ 对应 SPEC 文档的 API 表格是否更新
→ 检查 frontend/src/api/modules/ 对应 API 调用是否同步
→ 检查 frontend/e2e/ 对应 E2E 测试是否更新
```

### 规则 4：状态机变更 → 检查状态转换图 + 枚举定义

```
状态机新增/删除了状态
→ 检查 docs/architecture/STATE_TRANSITION_RED_LINE.md 是否更新
→ 检查 Entity 中 status 字段的 CHECK 约束是否同步
```

### 规则 5：文档版本号滞后

```
设计文档的版本号 < 对应模块最后一次 commit 的 tag
→ 标记为"可能需要更新"
```

## 守护流程

```
┌─────────────────────────────────────────────────────┐
│  Step 1: 扫描变更文件                               │
│  git diff HEAD~1 --name-only → 分类变更类型          │
│  - migration/*.sql → 规则 1                         │
│  - *Entity.java → 规则 2                            │
│  - *Controller.java → 规则 3                        │
│  - *StateMachine*.java → 规则 4                     │
│  - docs/** → 规则 5 (版本号滞后检测)                 │
│  - 无 docs/** 变更 → 规则 0 (最严重: 代码改了但文档没动)│
└──────────┬──────────────────────────────────────────┘
           ▼
┌─────────────────────────────────────────────────────┐
│  Step 2: 执行检测                                   │
│  对每个变更类型执行对应的检查规则                    │
│  收集所有差距项 → 生成 GapReport                    │
└──────────┬──────────────────────────────────────────┘
           ▼
┌─────────────────────────────────────────────────────┐
│  Step 3: 输出报告                                   │
│  差距报告格式:                                      │
│  [🔴/🟡/🟢] 类型 | 文件 | 描述 | 建议操作           │
│  ──────────────────────────────────────             │
│  🔴 代码变更但文档未更新 | 02-arap-design.md |     │
│     V1.1 但 BusinessDoc 新增了 bank_stmt_id 字段    │
│     建议: 更新数据模型章节 + 版本号 → V1.2          │
└──────────┬──────────────────────────────────────────┘
           ▼
┌─────────────────────────────────────────────────────┐
│  Step 4: 推送通知                                   │
│  - 有 🔴 项 → 阻断 CI，要求先更新文档再合并         │
│  - 有 🟡 项 → 推送提醒，建议补更                    │
│  - 全部 🟢 → 通过                                    │
└─────────────────────────────────────────────────────┘
```

## 差距等级

| 等级 | 标记 | 含义 | 动作 |
|------|------|------|------|
| 严重 | 🔴 | 代码变更但设计文档完全未更新 | 阻断 CI，强制要求更新 |
| 警告 | 🟡 | 文档版本滞后或部分字段缺失 | 推送提醒，建议补更 |
| 通过 | 🟢 | 文档已同步 | 无需操作 |

## 触发方式

### 方式 1：CI 门禁（推荐）

每次 `git push` 后自动执行，作为 CI pipeline 的一步：

```bash
# 在 CI 中调用
hermes skill run doc-consistency-guardian \
  --since HEAD~1 \
  --report docs/consistency/$(date +%Y%m%d-%H%M%S)-gap-report.md
```

### 方式 2：定时巡检（Cron）

```bash
# 每天凌晨检查最近 24 小时的变更
hermes cron create \
  --name doc-consistency-nightly \
  --schedule "0 2 * * *" \
  --skill doc-consistency-guardian
```

### 方式 3：手动触发

```bash
hermes skill run doc-consistency-guardian --since HEAD~3
```

## 关联文档

- 本技能：`~/.hermes/skills/doc-consistency-guardian/SKILL.md`
- 检测脚本：`scripts/check-doc-consistency.py`
- 差距报告模板：`docs/consistency/TEMPLATE.md`
- 防错机制文档：`docs/testing/test-prevention-mechanism.md`