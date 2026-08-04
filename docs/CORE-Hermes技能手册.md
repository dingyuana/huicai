# Hermes Skill 管理手册

> **版本**：V1.0 | **生成日期**：2026-07-20 | **维护人**：Hermes
> **说明**：本文档记录所有已安装 Skill 的名称、作用、来源、路径。

---

## 概览

| 指标 | 数值 |
|------|------|
| 总计 | 58 |
| 自己开发 | 44 |
| 外部下载 | 41 |

---

## 开发工作流（14个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `bug-investigator` | 系统化 Bug 调查 — 在尝试修复前找到根因。When encountering any bug, test failure, or unexpected b... | ✅ 自己开发 | `software-development/bug-investigator` |
| `closed-loop-doc-governance` | >- | ✅ 自己开发 | `software-development/closed-loop-doc-governance` |
| `code-reviewer` | 代码审查 — 审查已完成代码的 SPEC 合规性和代码质量。在任务完成后、合并前、或工作流到达审查关卡时调用。 | ✅ 自己开发 | `software-development/code-reviewer` |
| `dev-workflow` | 统一开发工作流编排器 — 自动检测当前阶段并路由到对应 skill。任何开发指令的默认入口。覆盖需求探索 → 发布归档 → 文档体系治理全流程。 | ✅ 自己开发 | `software-development/dev-workflow` |
| `large-project-architect` | >- | ✅ 自己开发 | `software-development/large-project-architect` |
| `loop-engineering` | Hermes 指挥+测试+自进化闭环中枢，通过 CLI 精准调度 OpenCode 的 Loop Engineering 架构。新项目一键生成完整架构目录、规范... | ✅ 自己开发 | `software-development/loop-engineering` |
| `multi-role-dev-workflow` | 多角色开发工作流 — Hermes(大脑) + Kanban(任务板) + OpenCode(执行者) 三驱闭环。从 PRD 拆解到 SPEC/Plan/Kan... | ✅ 自己开发 | `software-development/multi-role-dev-workflow` |
| `need-explorer` | 需求探索 — 在写 SPEC/Plan 之前澄清意图、范围、约束和验收标准。当需求模糊、用户在比较选项、或需要稳定的需求定义时触发。对应 three-phase... | ✅ 自己开发 | `software-development/need-explorer` |
| `release-archivist` | 开发完成后的验证、报告、归档流程。Invoke when implementation is complete, verification is underwa... | ✅ 自己开发 | `software-development/release-archivist` |
| `spec-first-contracts` | Spec-driven development with machine-readable YAML contracts. Appends structured... | ✅ 自己开发 | `software-development/spec-first-contracts` |
| `spring-boot-controller-testing` | Spring Boot Controller 层测试完整框架 — MockMvc 参数绑定验证、DTO 序列化、HTTP 响应断言、Mockito 交互验证、常... | ✅ 自己开发 | `software-development/spring-boot-controller-testing` |
| `three-phase-loop` | >- | ✅ 自己开发 | `software-development/three-phase-loop` |
| `tool-evaluation` | 第三方工具/项目/技能评估框架 — 系统化对比外部项目与现有体系，判断是否值得引入。当用户问"这个项目怎么样"、"对比一下 X 和我们的 Y"、"要不要用 Z"... | ✅ 自己开发 | `software-development/tool-evaluation` |
| `writing-plans` | "Write implementation plans: bite-sized tasks, paths, code." | ✅ 自己开发 | `software-development/writing-plans` |

---

## 外部Agent委派（4个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `claude-code` | "Delegate coding to Claude Code CLI (features, PRs)." | 📦 外部下载 | `autonomous-ai-agents/claude-code` |
| `codex` | "Delegate coding to OpenAI Codex CLI (features, PRs)." | 📦 外部下载 | `autonomous-ai-agents/codex` |
| `hermes-agent` | "Configure, extend, or contribute to Hermes Agent." | 📦 外部下载 | `autonomous-ai-agents/hermes-agent` |
| `opencode` | Delegate coding tasks to OpenCode CLI agent for feature implementation, refactor... | 📦 外部下载 | `autonomous-ai-agents/opencode` |

---

## Kanban编排（2个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `kanban-orchestrator` | Decomposition playbook + anti-temptation rules for an orchestrator profile routi... | ✅ 自己开发 | `devops/kanban-orchestrator` |
| `kanban-worker` | Pitfalls, examples, and edge cases for Hermes Kanban workers. The lifecycle itse... | ✅ 自己开发 | `devops/kanban-worker` |

---

## 项目级（6个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `huicai-banking-flow` | 慧财财务 (huicai) 项目银行流水端到端自动接入完整工作流。从银行流水导入 → AI 分类 → A/B/C 三路由 → 应收/应付/核销 → 凭证 → 员... | ✅ 自己开发 | `projects/huicai-banking-flow` |
| `huicai-crud-generator` | 慧财财务标准 CRUD 代码生成器。基于项目实际代码规范（Voucher/BusinessDoc 模块为模板），生成可直接编译运行的后端+前端代码。 | ✅ 自己开发 | `projects/huicai-crud-generator` |
| `huicai-java-backend` | 慧财财务 Java 后端（Huicai Financial）— Spring Boot 3.x + MyBatis-Plus + PostgreSQL 16。*... | ✅ 自己开发 | `projects/huicai-java-backend` |
| `huihua-business-auditor` | | | ✅ 自己开发 | `projects/huihua-business-auditor` |
| `huihua-go-backend` | 【⚠️ 暂停/参考资产 — 2026-06-XX 老丁确认主线切换】慧智财务 Go 后端（huihua-financial）— Fiber v2 API。**当... | ✅ 自己开发 | `projects/huihua-go-backend` |
| `kaoyan-knowledge-base` | | | ✅ 自己开发 | `projects/kaoyan-knowledge-base` |

---

## 测试与质量（2个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `expert-code-audit` | 专家级代码审计框架 — 9 项专项审计（架构/业务/数据库/测试/安全/性能/API契约/文档/综合） + 静态分析 + 改进路线图。适用于生产级软件（金融/政... | ✅ 自己开发 | `devops/expert-code-audit` |
| `skill-truth-validator` | 校验 ~/.hermes/skills/**/SKILL.md 里的硬数字声明是否仍与现实一致。Triggers — 每次 session 启动时（SOUL s... | ✅ 自己开发 | `devops/skill-truth-validator` |

---

## 运维自动化（3个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `nightly-project-patrol` | "Nightly system health patrol across all projects — checks disk/memory/swap, Doc... | ✅ 自己开发 | `devops/nightly-project-patrol` |
| `project-design-sync` | "每天夜间扫描所有项目，对比设计文档与开发进度，检测重大变化并更新文档。报告推送到飞书。" | ✅ 自己开发 | `devops/project-design-sync` |
| `spring-vue-fullstack-debug` | Debug 500 errors across Spring Boot, Vue, and Postgres. | ✅ 自己开发 | `devops/spring-vue-fullstack-debug` |

---

## 文档与写作（6个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `doc-coauthoring` | Guide users through a structured workflow for co-authoring documentation. Use wh... | ✅ 自己开发 | `doc-coauthoring` |
| `tech-evangelism-writing` | 技术布道式写作 — 用打怪升级的叙事弧线把枯燥技术原理讲活。适用于所有技术文章、教程、wiki 笔记、博客。覆盖需求定义→方案迭代→原理拆解→情绪升华的全流程写... | ✅ 自己开发 | `writing/tech-evangelism-writing` |
| `tutorial-content-audit` | 系统化审计多章节教程/课程内容的一致性——前后矛盾、数据不匹配、术语漂移、跨章引用缺失。适用于有线性章节关系的教程系列（10+ 章）。不是代码审计（用 expe... | ✅ 自己开发 | `writing/tutorial-content-audit` |
| `tutorial-creator` | 从大纲和素材创建结构化教程 — 章节拆分、文档扫描、gap 分析、逐章撰写、提交推送。适用于"充实大纲""完善知识库""写教程""大学教程""教材编写"等任务。 | ✅ 自己开发 | `tutorial-creator` |
| `voice-extract` | Extracts a user's writing voice from text samples via SICO comparative analysis | ✅ 自己开发 | `voice-extract` |
| `voice-generate` | Generates text in a learned writing voice | ✅ 自己开发 | `voice-generate` |

---

## 飞书/腾讯集成（9个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `huihua-account-mapping` | | | ✅ 自己开发 | `openclaw-imports/huihua-account-mapping` |
| `huihua-financial` | | | ✅ 自己开发 | `openclaw-imports/huihua-financial` |
| `find-skills` | Highest-priority skill discovery flow. MUST trigger when users ask to find/insta... | 📦 外部下载 | `openclaw-imports/find-skills` |
| `lark-doc` | "飞书云文档：创建和编辑飞书文档。从 Markdown 创建文档、获取文档内容、更新文档（追加/覆盖/替换/插入/删除）、上传和下载文档中的图片和文件、搜索云空... | 📦 外部下载 | `openclaw-imports/lark-doc` |
| `lark-im` | "飞书即时通讯：收发消息和管理群聊。发送和回复消息、搜索聊天记录、管理群聊成员、上传下载图片和文件、管理表情回复。当用户需要发消息、查看或搜索聊天记录、下载聊天... | 📦 外部下载 | `openclaw-imports/lark-im` |
| `lark-shared` | "飞书/Lark CLI 共享基础：应用配置初始化、认证登录（auth login）、身份切换（--as user/bot）、权限与 scope 管理、Perm... | 📦 外部下载 | `openclaw-imports/lark-shared` |
| `openspec` | Spec-driven development with OpenSpec CLI. Use when building features, migration... | 📦 外部下载 | `openclaw-imports/openspec` |
| `superpowers` | > | 📦 外部下载 | `openclaw-imports/superpowers` |
| `tencent-docs` | 腾讯文档（docs.qq.com）-在线云文档平台，是创建、编辑、管理文档的首选 skill。涉及"新建文档"、"创建文档"、"写文档"、"在线文档"、"云文档... | 📦 外部下载 | `openclaw-imports/tencent-docs` |

---

## 其他（12个）

| 名称 | 作用 | 来源 | 路径 |
|------|------|------|------|
| `baoyu-article-illustrator` | Analyzes article structure, identifies positions requiring visual aids, generate... | ✅ 自己开发 | `baoyu-article-illustrator` |
| `cron-daily-work-plan` | "生成每日工作计划并推送到消息平台（微信/飞书）。用于每天早上 8:00 (Asia/Shanghai) 的定时 cron 任务。核心步骤：日期计算 → 读取本... | ✅ 自己开发 | `productivity/cron-daily-work-plan` |
| `daily-tech-news-briefing` | Daily tech news briefing - multi-platform scraping, AI/tech curation, Feishu pus... | ✅ 自己开发 | `daily-tech-news-briefing` |
| `doc-consistency-guardian` | 提交后自动执行多项验证：文档一致性、API 参数契约、Entity-DB 对齐 | ✅ 自己开发 | `doc-consistency-guardian` |
| `dogfood` | "Exploratory QA of web apps: find bugs, evidence, reports." | ✅ 自己开发 | `dogfood` |
| `knowledge-ingest` | Compile raw notes into wiki via LLM-Wiki. | ✅ 自己开发 | `productivity/knowledge-ingest` |
| `ocr-and-documents` | "Extract text from PDFs/scans (pymupdf, marker-pdf)." | ✅ 自己开发 | `productivity/ocr-and-documents` |
| `retrospective` | 定期回顾 Hermes + OpenCode 协作日志，生成改进建议报告。必须 Human Manager 审核后才能实施任何建议。 | ✅ 自己开发 | `retrospective` |
| `stable-tech-news-fetcher` |  | ✅ 自己开发 | `stable-tech-news-fetcher` |
| `segment-anything-model` | "SAM: zero-shot image segmentation via points, boxes, masks." | 📦 外部下载 | `mlops/models/segment-anything` |
| `whisper` | OpenAI's general-purpose speech recognition model. Supports 99 languages, transc... | 📦 外部下载 | `mlops/models/whisper` |
| `yuanbao` | "Yuanbao (元宝) groups: @mention users, query info/members." | 📦 外部下载 | `yuanbao` |

---

## 归档 Skill（27个）

> 以下 Skill 已归档，不参与日常开发，保留作为历史参考。

| 名称 | 来源 | 路径 |
|------|------|------|
| `agent-memory-persistence` | 📦 外部下载 | `.archive/agent-memory-persistence` |
| `daily-tech-news-learner` | 📦 外部下载 | `.archive/daily-tech-news-learner` |
| `debugging-hermes-tui-commands` | 📦 外部下载 | `.archive/debugging-hermes-tui-commands` |
| `design-code-issue-triage` | 📦 外部下载 | `.archive/software-development/design-code-issue-triage` |
| `find-skills` | 📦 外部下载 | `.archive/find-skills` |
| `github` | 📦 外部下载 | `.archive/github-thin-stub` |
| `hermes-project-onboarding` | 📦 外部下载 | `.archive/hermes-project-onboarding` |
| `huihua-business-flow-test` | 📦 外部下载 | `.archive/huihua-business-flow-test` |
| `huihua-financial-code-audit` | 📦 外部下载 | `.archive/huihua-financial-code-audit` |
| `huihua-financial-mvp` | 📦 外部下载 | `.archive/huihua-financial-mvp` |
| `memory-hygiene` | 📦 外部下载 | `.archive/memory-hygiene` |
| `memory-reme` | 📦 外部下载 | `.archive/memory-reme` |
| `node-inspect-debugger` | 📦 外部下载 | `.archive/node-inspect-debugger` |
| `opencode-execution` | 📦 外部下载 | `.archive/opencode-execution` |
| `python-debugpy` | 📦 外部下载 | `.archive/python-debugpy` |
| `skill-vetter` | 📦 外部下载 | `.archive/skill-vetter` |
| `skillhub-preference` | 📦 外部下载 | `.archive/skillhub-preference` |
| `smartedu-feature-porting` | 📦 外部下载 | `.archive/smartedu-feature-porting` |
| `smartedu-github-review` | 📦 外部下载 | `.archive/smartedu-github-review` |
| `smartedu-paper-review` | 📦 外部下载 | `.archive/smartedu-paper-review` |
| `superpowers-dispatching-parallel-agents` | 📦 外部下载 | `.archive/superpowers-dispatching-parallel-agents` |
| `superpowers-requesting-code-review` | 📦 外部下载 | `.archive/superpowers-requesting-code-review` |
| `superpowers-systematic-debugging` | 📦 外部下载 | `.archive/superpowers-systematic-debugging` |
| `superpowers-verification` | 📦 外部下载 | `.archive/superpowers-verification` |
| `task-breakdown` | 📦 外部下载 | `.archive/task-breakdown` |
| `test-suite-audit` | 📦 外部下载 | `.archive/test-suite-audit` |
| `xitter` | 📦 外部下载 | `.archive/xitter` |

---

> **文档结束。** 本文档由 `multi-role-dev-workflow` skill 生成，与各 SKILL.md 文件的 `origin` 字段保持一致。