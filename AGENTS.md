# AGENTS.md — 慧财财务 (Huicai Financial Software)

---

## §0 项目状态（硬数字，每次 commit 后更新）

> **更新基准**：commit `29e1cde` (2026-06-27)

| 维度 | 数据 |
|---|---|
| 后端代码 | 327 Java 文件 / 27,000+ 行 |
| 前端代码 | Vue 3 + Element Plus + ECharts / 60+ 组件 |
| 数据库 | PostgreSQL 16 / V1-V60 Flyway migration 已应用 |
| 测试基线 | 391 个用例 / 0 fail / 16 个 H2 IntegrationTest 历史 error |
| API 覆盖 | 后端 292 端点 / 前端 90 调用 / 覆盖率 72% |
| 已完成模块 | 系统管理、用户权限、凭证、业务单据、发票、应收应付、核销、银行对账、固定资产、预算、报表 |
| 技术栈 | Spring Boot 3.x + MyBatis-Plus + Redis 7 + MinIO + RabbitMQ |

---

## §1 核心设计铁律（强制执行，违反直接 reject PR）

1. **人是唯一审核主体**：所有审核/结转/状态变更必须由人主动触发，系统不允许自动调整任何业务实体状态
2. **AI 输出 = 建议**：永远不自动应用 AI 结果到财务数据，必须人工确认后才能写入
3. **凭证不可变性**：已审核/已过账凭证不可修改，只能通过红冲（红-蓝 对冲）修正
4. **状态机严格转换**：所有核心实体（凭证/单据/发票/应收/应付）禁止非法状态跳转
5. **审计追踪**：所有关键写操作通过 AOP + `jsonb` 快照记录变更前后全量数据
6. **数据权限**：MyBatis 拦截器自动注入 SQL 条件，实现组织级数据隔离
7. **金额精度**：所有金额计算使用 `BigDecimal`，禁止 `double/float`，数据库 `NUMERIC(18,2)`
8. **核销架构**：银行流水不直接参与核销，必须先生成收款单/付款单后再匹配应收/应付

---

## §2 开发工作流标准

### 2.1 任务执行流程
```
需求理解 → 写 SPEC 文档 → 老丁审核 → OpenCode 开发 → Hermes 验证 → commit → push
```

### 2.2 开发规则
- **先写 SPEC 再写代码**：所有功能必须先有设计文档，禁止边想边写
- **轻量任务直写**：纯命令/查询/文档修改类任务，Hermes 直接执行，不委 OpenCode
- **测试必须 0 fail**：每次提交前 `mvn test` 必须 Failures: 0，Errors 允许保留 16 个 H2 历史问题
- **负向断言强制**：所有状态机方法必须同时验证「该做的做了」+「不该做的没做」

### 2.3 提交规范
- 每次 commit 后必须更新 §0 的硬数字
- commit message 首行 ≤ 50 字符，禁止 emoji、TODO、待办标记
- `git add` 必须指定具体文件路径，禁止根目录 `git add -A`（防止卷进 IDE 自动生成文件）

---

## §3 沟通铁律（R9/R10，强制执行）

### R9 — 选项呈现规则
- 选项必须在对话最后列出，不混入叙述文本
- 选项数量 ≤ 4 个，超过的先合并归类
- 每个选项必须标「推荐」或「⭐」
- 依据简短，不替用户做选择

### R10 — 任务完成回复规则
- 开头固定：`**任务已完成。**`
- 必须含 6 列总结表：`项 / 内容 / 状态 / 验证 / commit / push`
- 关键发现独立列出
- 遗留事项独立列出
- 查询任务：开头 `**任务已完成。**` + 结构化回答，不写 6 列表格

---

## §4 陷阱与经验库（持续更新）

### 4.1 业务逻辑类
1. **状态机副作用泄漏**：`confirm()` 只改状态，不生成凭证/业务单/应收单，后置逻辑由独立端点触发
2. **红冲匹配时机**：不在导入循环内匹配红字发票，导入完成后统一调 `batchLinkRedFlushInvoices()` 扫全库匹配
3. **发票导入模式**：导入时只创建发票（PENDING_CONFIRM），人工审核后才创建业务单+应收单

### 4.2 技术类
1. **Jackson LocalDateTime 序列化**：必须统一秒级精度，去除微秒，防止前后端时间不匹配
2. **Mockito `any()` 歧义**：MyBatis-Plus `updateById` 有双签名，必须用 `any(Entity.class)` 明确类型
3. **Flyway migration 漂移**：V 版本号必须连续，重复版本会导致迁移失败
4. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端

### 4.3 工具链类
1. **OpenCode 委派失败**：worker 异常退出（HTTP 400 No models / gateway stopped）→ Hermes 直写 + 留任务书
2. **`git add <dir>` 陷阱**：add 目录后必查 `git status --short`，防止卷进 IDE 自动生成文件
3. **`execute_code` 工具返回空**：沙箱内 `terminal()`/`read_file()` 可能返回空，改用 `subprocess.run()` 直接调系统命令
4. **Maven JDK 21 编译**：`maven-compiler-plugin` 必须 ≥ 3.12.0，否则 `--release 21` 报错

---

## §5 命名约定（强制执行）

- 表名前缀 `t_`（如 `t_voucher`、`t_business_doc`）
- 主键：`id BIGINT GENERATED ALWAYS AS IDENTITY`
- 金额列：`NUMERIC(18,2)`
- 扩展数据：`JSONB` 列（OCR 结果、AI 负载、辅助核算）
- AI 向量：`VECTOR(768)` + IVFFlat 索引
- 表/列名：**snake_case**（PostgreSQL 约定）
- Java 字段：camelCase，用 `@TableField(value = "snake_case_name")` 映射

---

## §6 技术栈（已落地）

| 层级 | 技术 |
|---|---|
| 后端 | Spring Boot 3.x, Spring Security + JWT |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL 16, pgvector, pg_trgm |
| 缓存/分布式锁 | Redis 7 |
| 消息队列 | RabbitMQ |
| 文件存储 | MinIO |
| AI 服务 | Python 3.11 + FastAPI, Hugging Face / PyTorch |
| 前端 | Vue 3 + Element Plus + ECharts |
| 报表 | EasyExcel |
| API 文档 | Knife4j (Swagger) |
| 监控 | Prometheus + Grafana（待接入） |
| 部署 | Docker Compose |