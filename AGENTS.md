# AGENTS.md — 慧财财务 (Huicai Financial Software)

---

## §0 项目状态（硬数字，每次 commit 后更新）

> **更新基准**：commit `0f8d420` (2026-07-08) — P42 核销能力补充
> **当前分支**：`ai-evolution`
> **关联文档**：[DESIGN.md](docs/DESIGN.md)、[需求登记册](docs/requirements/REQUIREMENTS_REGISTRY.md)、[DOCUMENT_REGISTRY.md](docs/DOCUMENT_REGISTRY.md)

| 维度 | 数据 |
|------|------|
| 后端代码 | 345 Java 文件 |
| 测试用例 | 579 个 `@Test` 方法 / 78 个测试类 |
| 数据库 | PostgreSQL 16 / V1-V83 Flyway migration |
| API 端点 | 433 个后端端点 |
| 核心模块 | 基础数据、总账、应收应付、现金管理、固定资产、费用报销、发票税务、预算、财务报表、存储管理 |
| AI 服务 | Python FastAPI 5 端点（health/anomaly/embedding/match/ocr）|
| 技术栈 | Spring Boot 3.x + MyBatis-Plus + Redis 7 + MinIO + RabbitMQ |
| 开发流程 | 大闭环（closed-loop-doc-governance）+ 内循环（three-phase-loop v3.0）|
| P0-P2 阶段 | ✅ 100% 完成（基础体系 + 缺陷修复 + AI 辅助能力）|
| P3 远期 | ⏳ 0%（经营分析/预算预测/风控/工资薪酬）|

---

## §1 核心设计铁律（强制执行，违反直接 reject PR）

1. **人是唯一审核主体**：所有审核/结转/状态变更必须由人主动触发，系统不允许自动调整任何业务实体状态
2. **AI 输出 = 建议**：永远不自动应用 AI 结果到财务数据，必须人工确认后才能写入。AI 是横切辅助能力，不替代 Java 确定性模块
3. **凭证不可变性**：已审核/已过账凭证不可修改，只能通过红冲（红-蓝 对冲）修正
4. **状态机严格转换**：所有核心实体（凭证/单据/发票/应收/应付）禁止非法状态跳转
5. **审计追踪**：所有关键写操作通过 AOP + `jsonb` 快照记录变更前后全量数据
6. **数据权限**：MyBatis 拦截器自动注入 SQL 条件，实现组织级数据隔离
7. **金额精度**：所有金额计算使用 `BigDecimal`，禁止 `double/float`，数据库 `NUMERIC(18,2)`
8. **核销架构**：银行流水不直接参与核销，必须先生成收款单/付款单后再匹配应收/应付
9. **编号关联溯源**：所有业务实体通过编号双向关联（xxx_id 外键 + xxx_no 编号冗余），支持全链路追溯
10. **三步闭环铁律**：每项开发任务必须走 SPEC→Plan→审核→执行，不能跳过前两步直接动手
11. **事务红线**：所有涉及资金流转、记账、冲红、核销的核心写操作，必须使用 `@Transactional(rollbackFor = Exception.class)`，严禁无事务执行
12. **逻辑删除**：关键财务表禁止物理删除，统一使用 `deleted` 字段（Integer，0=正常，1=删除），所有查询必须带 `deleted = 0` 条件
13. **DTO/VO 隔离**：禁止将数据库 Entity 直接暴露给前端 Controller 返回值，入参必须定义 DTO/Param，出参必须定义 VO
14. **异常处理**：禁止直接抛出原生 `Exception` 或 `RuntimeException`，必须使用 `BusinessException`（`com.huicai.common.exception`），统一错误码管理

---

## §2 开发工作流标准

### 2.1 双层闭环流程

```
大闭环（closed-loop-doc-governance）— 项目级文档治理
├── Phase 1: PRD — 需求登记册 + REQ 编号 + 验收标准
├── Phase 2: Spec — SPEC 文档 + REQ 回链 + 版本历史
├── Phase 3: DEV — 开发实施 + DIR 捕获
└── Phase 4-5: 审核 + 闭环回写

内循环（three-phase-loop v3.0）— 功能级微循环开发
├── TRIAGE → PLAN → 审核门 → BUILD(微循环) → VERIFY → REPORT
└── 每个微循环 5-15 分钟，Contract-First 先写契约再实现
```

### 2.2 任务执行流程
```
需求理解 → 写 SPEC + 需求编号 → 老丁审核 → 微循环开发 → 验证 → commit → push
```

### 2.3 开发规则
- **三步闭环**：SPEC→Plan→审核→执行，禁止跳过前两步
- **Contract-First**：每个微循环先写可执行契约（测试/断言/SQL），再写实现
- **先写 SPEC 再写代码**：所有功能必须先有设计文档，禁止边想边写
- **测试必须 0 fail**：每次提交前 `mvn test` 必须 Failures: 0
- **负向断言强制**：所有状态机方法必须同时验证「该做的做了」+「不该做的没做」
- **轻量任务直写**：纯命令/查询/文档修改类任务，Hermes 直接执行

### 2.4 文档提交规范
- 新需求必须分配 REQ 编号，写入 REQUIREMENTS_REGISTRY.md
- 每个 SPEC 必须关联至少一个 REQ 编号，含版本历史
- 每次 commit 后更新 §0 硬数字
- commit message 首行 ≤ 50 字符，禁止 emoji、TODO、待办标记
- `git add` 必须指定具体文件路径，禁止根目录 `git add -A`

---

## §3 沟通铁律（强制执行）

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
3. **发票导入模式**：导入时只创建发票（PENDING_CONFIRM），人工审核后才创建业务单

### 4.2 Entity-DB 不一致类
4. **Entity 字段 vs DB 列对不齐**：`OutputInvoiceEntity` 的 `auditedBy`/`auditedAt` 没有 `@TableField(exist = false)`，但 `t_output_invoice` 表没有这列。MyBatis-Plus 自动生成 SELECT 报 "column does not exist"。根源：注释写"V63 已添加列"但实际没加。修复：每次新增 Entity 字段时必须检查 DB schema，`exist = false` 和 `value=` 二选一，不可裸写。同类字段（`auditedBy/auditedAt/updatedBy/docStatus/voucherStatus`）是高风险模式。
5. **注释说"Vxx 列已添加"但 migration 实际没写**：V63 只给 `t_output_invoice` 加了 `version`，注释声称的 `audited_by`/`audited_at` 从未存在。教训：Entity 注释中的 migration 引用必须与 migration SQL 逐行对证。
6. **String 映射 JSONB 列缺 typeHandler**：`ai_mapping_result`/`aux_dimension`/`assist_json`/`ocr_data` 等字段在 Entity 中是 `String`，但 DB 列是 `JSONB`。全字段 UPDATE 时 PostgreSQL 报错 "column is of type jsonb but expression is of type character varying"。项目已有 `JsonbTypeHandler`，所有 String→JSONB 字段必须加 `@TableField(typeHandler = JsonbTypeHandler.class)`。已经用 AuditLogEntity 验证过正确的写法。

### 4.3 测试类
6. **测试假阳性**：测试通过 ≠ 功能完成。跨实体链路必须真实贯通，不能只测单个模块 CRUD。E2E 测试必须模拟真实用户操作路径
7. **Mock 测试盲区**：Mock 测试发现不了 DB 约束（NOT NULL、CHECK、UNIQUE）、Flyway 不匹配、SQL 语法错误。核心 Mapper 必须跑真实 DB 测试（Testcontainers）
8. **Service 签名变更同步**：扩展现有方法签名时，所有调用点（Controller、Service 实现、所有测试文件）必须同步更新

### 4.4 技术类
7. **Jackson LocalDateTime 序列化**：`application.yml` 的 `date-format` 对 `LocalDateTime` 无效，必须注册专用序列化器
8. **Mockito `any()` 歧义**：MyBatis-Plus `updateById` 有双签名，必须用 `any(Entity.class)` 明确类型
9. **Flyway migration 漂移**：V 版本号必须连续，重复版本会导致迁移失败。迁移文件 commit 后不会自动执行，必须重启应用
10. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端

### 4.4 文档治理类
11. **SPEC 漂移检测**：架构变更后（如 P33→P34），SPEC 仍描述旧架构。每次大变更后必做 SPEC vs 代码一致性审计
12. **DIR 提交**：开发中发现需求模糊/规范缺失/流程不准确时，提交 DIR 到 `docs/dir/`，迭代结束时统一回写
13. **文档版本联动**：SPEC 版本号必须与 DESIGN.md 联动，修改代码后同步更新文档版本历史
14. **设计文档冲突**：DESIGN.md 变更后，检查 `docs/development/` 下所有文档是否冲突，冲突文件移入 `archive/`

### 4.5 工具链类
15. **OpenCode 委派失败**：worker 异常退出 → Hermes 直写 + 留任务书
16. **`git add <dir>` 陷阱**：add 目录后必查 `git status --short`，防止卷进 IDE 自动生成文件
17. **`execute_code` 工具返回空**：沙箱内 `terminal()`/`read_file()` 可能返回空，改用 `subprocess.run()` 直接调系统命令
18. **Maven JDK 21 编译**：`maven-compiler-plugin` 必须 ≥ 3.12.0，否则 `--release 21` 报错

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

## §6 常用命令速查

| 操作 | 命令 |
|------|------|
| 启动后端 | `cd backend && mvn spring-boot:run` |
| 启动前端 | `cd frontend && npm run dev` |
| 运行全部测试 | `cd backend && mvn test` |
| 快测试（无 Docker） | `cd backend && mvn test` |
| 完整测试（含真实 DB） | `cd backend && mvn test -Dsurefire.excludedGroups=` |
| 单个测试类 | `cd backend && mvn test -Dtest=XxxTest` |
| 编译检查 | `cd backend && mvn compile` |
| 打包 | `cd backend && mvn clean package -DskipTests` |
| 启动 Docker 环境 | `docker compose up -d` |
| 查看 Flyway 状态 | `cd backend && mvn flyway:info` |
| 查看 Git 日志 | `git log --oneline -10` |

---

## §7 风险边界（操作前需人工确认）

1. **禁止 `git push -f`**：任何强制推送都会覆盖 Git 历史，必须经老丁确认
2. **禁止直接修改生产配置**：`application-prod.yml` 等生产环境配置文件不可修改
3. **禁止硬编码敏感信息**：数据库密码、API Key、密钥等必须通过环境变量或配置中心注入
4. **DDL 必须走 Flyway**：禁止直接修改数据库表结构，所有 schema 变更必须生成 Flyway migration
5. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端
6. **Git add 禁止通配**：`git add` 必须指定具体文件路径，禁止根目录 `git add -A`
7. **破坏性操作确认**：DROP、DELETE 无 WHERE、truncate 等操作必须经老丁确认

---

## §8 技术栈（已落地）

| 层级 | 技术 |
|------|------|
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