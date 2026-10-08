# 慧财（Huicai）系统架构设计

> **版本**：V1.0 | **日期**：2026-10-08 | **状态**：生效
> **适用范围**：系统架构总纲，各模块详细设计见 `docs/specs/` 对应 SPEC

---

## 1. 系统定位

慧财是面向中小企业与代理记账公司的云端财务核算系统，覆盖凭证、报表、应收应付、固定资产、税务、预算、期末结账等核心财务场景，并提供代理公司批量作业能力。

## 2. 技术栈

### 2.1 后端

| 类别 | 选型 |
|---|---|
| 框架 | Spring Boot 3.x |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL 16（pgvector 扩展） |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3 |
| 对象存储 | MinIO |
| 安全 | Spring Security + JWT + RLS |
| 迁移 | Flyway |
| 构建 | Maven |

### 2.2 前端

| 类别 | 选型 |
|---|---|
| 框架 | Vue 3（Composition API） |
| 构建 | Vite 5 |
| UI | Element Plus |
| 状态 | Pinia |
| HTTP | Axios |
| 测试 | Vitest |
| E2E | Playwright |

### 2.3 AI 服务

| 类别 | 选型 |
|---|---|
| 框架 | Python FastAPI |
| 部署 | 独立容器（端口 8001） |
| 通信 | HTTP REST |

---

## 3. 部署架构

```
                    ┌──────────────────┐
                    │   Nginx / 浏览器   │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ frontend │  │ backend  │  │ai-service│
        │ (Vite/  │  │(Spring)  │  │(FastAPI) │
        │  pm2)    │  │ :8080    │  │ :8001    │
        └──────────┘  └────┬─────┘  └────┬─────┘
                           │              │
              ┌────────────┼──────────────┘
              ▼            ▼              ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │PostgreSQL│ │  Redis   │ │ RabbitMQ │
        │   16     │ │    7     │ │    3     │
        └──────────┘ └──────────┘ └──────────┘
              │
        ┌──────────┐
        │  MinIO   │
        └──────────┘
```

- **前端**：独立部署（开发 Vite dev :3001，生产 pm2 preview）
- **后端**：Spring Boot 容器，端口 8080
- **AI 服务**：Python 容器，端口 8001
- **基础设施**：PostgreSQL、Redis、RabbitMQ、MinIO 均容器化

---

## 4. 后端模块划分

```
com.huicai
├── base/          基础模块
│   ├── voucher/       凭证管理（凭证/分录/模板/状态机）
│   ├── report/        财务报表（三表/科目余额/自定义/诊断）
│   ├── masterdata/    主数据（科目/币种/凭证字/辅助核算）
│   ├── system/        系统管理（用户/角色/菜单/部门/权限）
│   ├── business/      业务单据（银行流水/业务单/客户对账单）
│   ├── balance/       余额（科目余额/辅助核算余额）
│   ├── storage/       存储（文件/MinIO）
│   └── ai/            AI 接入（与 ai-service 通信）
├── sme/           中小企业财务
│   ├── arap/          应收应付（核销/账龄/坏账/预收预付）
│   ├── asset/         固定资产（卡片/折旧/盘点/处置）
│   ├── budget/        预算管理（编制/控制/分析）
│   ├── cash/          现金银行（银行账户/流水/对账）
│   ├── periodclose/   期末结账（结转/结账/反结账）
│   └── tax/           税务管理（进销项发票/申报）
├── agency/        代理公司
│   ├── batch/         批量操作（导入/审核/结账）
│   ├── client/        客户管理（客户/合同）
│   ├── dashboard/     代理工作台（进度/工作量）
│   ├── tenant/        租户管理
│   └── user/          用户管理
├── common/        公共组件
│   ├── annotation/    自定义注解（@Auditable 等）
│   ├── aspect/        AOP 切面（审计/数据权限）
│   ├── config/        配置（MyBatis/Security/Flyway）
│   ├── context/       上下文（企业上下文持有者）
│   ├── entity/        基础实体（BaseEntity）
│   ├── event/         领域事件
│   ├── exception/     异常体系
│   └── response/      统一响应
└── config/        全局配置
```

---

## 5. 核心架构设计

### 5.1 多租户隔离（三层防线）

```
请求 → JWT 解析 → X-Enterprise-Id 校验（三源并集）
                              ↓
                    第一层：DTO 隔离
                    （Controller 不直收 Entity，enterpriseId 不可传）
                              ↓
                    第二层：Context 强制覆盖
                    （MyMetaObjectHandler 无条件覆盖 enterpriseId）
                              ↓
                    第三层：RLS 行级安全
                    （SET LOCAL app.enterprise_id + NOBYPASSRLS）
```

- **第一层**：所有 Controller 使用 DTO，禁止直收 Entity
- **第二层**：`MyMetaObjectHandler.insertFill()` 对 `enterpriseId` 字段无条件覆盖为上下文值
- **第三层**：PostgreSQL RLS 策略，应用角色 `huicai_app` 设为 `NOBYPASSRLS`，每个事务 `SET LOCAL app.enterprise_id`

### 5.2 状态机治理

核心业务对象（凭证、单据、发票、核销单）均采用状态机模式：

```
状态定义 → Java 常量类 + DB CHECK 约束双重保障
状态流转 → Service 层显式方法，禁止直接 set 状态
状态校验 → check_xxx_status CHECK 约束兜底
```

关键状态机：
- 凭证：DRAFT → SUBMITTED → APPROVED → VOUCHERED（红冲 REVERSED）
- 销项发票：PENDING_CONFIRM → PENDING_REVIEW → CONFIRMED → VOUCHERED
- 核销单：DRAFT → SUBMITTED → APPROVED → FULLY_RECONCILED

### 5.3 审计追踪

- `AuditTrackingAspect` AOP 切面拦截 `insert/updateById/deleteById/@Auditable`
- 快照存 `t_audit_log.before_data/after_data`（jsonb）
- 随业务事务同步写入（非异步，保证一致性）

### 5.4 报表体系

```
数据源（t_voucher_entry 分录聚合）
    ↓
ReportDataMapper（SQL 聚合）
    ↓
ReportService（业务计算：三表/现金流/诊断）
    ↓
ReportController（REST + Excel 导出）
```

报表诊断规则（5 条）：零收入有费用、现金骤降、期初不连续、存货占比过高、应收占比过高。

---

## 6. 数据库设计

### 6.1 命名规范

| 对象 | 规范 | 示例 |
|---|---|---|
| 表名 | `t_` 前缀 + snake_case | `t_voucher` |
| 列名 | snake_case | `voucher_no` |
| 主键 | `id BIGINT GENERATED ALWAYS AS IDENTITY` | — |
| 金额 | `NUMERIC(18,2)` | `amount` |
| 租户列 | `enterprise_id BIGINT NOT NULL` | — |
| 逻辑删除 | `deleted INTEGER DEFAULT 0` | — |
| 时间戳 | `created_at/updated_at TIMESTAMP` | — |

### 6.2 约束命名

| 约束 | 命名 | 示例 |
|---|---|---|
| 唯一 | `uq_<表>_<列>` | `uq_subject_code_ent` |
| 外键 | `fk_<表>_<列>` | `fk_voucher_entry_subject` |
| CHECK | `chk_<表>_<含义>` | `chk_voucher_status` |

### 6.3 Flyway 迁移

- 基线：`V1__baseline.sql`（覆盖 V1-V91）
- 当前最新：V173
- 治理规范见 `docs/development/standards/flyway-governance.md`

---

## 7. 前端架构

### 7.1 目录结构

```
frontend/src/
├── api/             API 模块（按业务域划分）
├── components/      通用组件
├── composables/     组合式函数
├── router/          路由（按业务域分文件）
├── stores/          Pinia 状态
├── views/           页面视图
│   ├── finance/     财务（凭证/账薄/报表）
│   ├── arap/        应收应付
│   ├── asset/       固定资产
│   ├── tax/         税务
│   ├── agency/      代理公司
│   └── dashboard/   仪表盘
└── utils/           工具函数
```

### 7.2 关键特性

- **企业切换**：`EnterpriseSwitcher` + `X-Enterprise-Id` 请求头
- **权限控制**：路由 `meta.permission` + 指令 `v-permission`
- **期间管理**：`PeriodNavigator` 全局期间切换
- **报表打印**：`@media print` + A4 排版

---

## 8. 测试体系

| 层级 | 工具 | 数量 | 触发 |
|---|---|---|---|
| L1 单元 | JUnit5 + Mockito | 2143 用例 | push |
| L2 集成 | Testcontainers + Flyway | — | push |
| 前端单测 | Vitest | 265 用例 | push |
| E2E | Playwright | 32 用例 | 夜间 |
| 性能 | K6 | — | 夜间 |

### 8.1 测试基类

- `AbstractMapperTest`：Testcontainers 启动真实 PG + Flyway 迁移，事务回滚
- `@SlowTest`：标记需 Docker 的慢测试

### 8.2 CI 门禁

| 工作流 | 门禁能力 |
|---|---|
| l1-unit-tests | 单元测试 + 覆盖率棘轮 |
| l2-integration-test | 真库集成测试 |
| spec-contract-validation | SPEC 契约 + 登记册漂移守卫 |
| full-stack-test | 前后端联测 |
| nightly-e2e-full | 全量 E2E |
| performance-regression | 性能回归 |

---

## 9. 安全架构

| 维度 | 实现 |
|---|---|
| 认证 | JWT（无状态，环境变量密钥） |
| 授权 | `@PreAuthorize` + 权限码 |
| 租户隔离 | 三层防线（见 5.1） |
| 密码 | BCrypt 加密 |
| 审计 | AOP 切面 + jsonb 快照 |
| 敏感操作 | 反结账/清库需 `@PreAuthorize` |

---

## 10. 工程规范（铁律摘要）

1. **凭证不可变**：已审核凭证只能红冲，禁止物理删除
2. **人是唯一审核主体**：禁止自动调整业务状态
3. **金额精度**：BigDecimal + NUMERIC(18,2)
4. **DTO 隔离**：Controller 禁止直收 Entity
5. **状态机**：状态流转必须走 Service 显式方法
6. **逻辑删除**：禁止物理删除核心表
7. **编号关联溯源**：全链路双向追溯
8. **数据权限隔离**：组织级 RLS

完整铁律见 `AGENTS.md`。

---

## 11. 已知局限与未来规划

| 项目 | 现状 | 规划 |
|---|---|---|
| AI 智能化 | 暂缓 | AI 记账/审核/核销推荐 |
| 年度账层 | 仅「企业→期间」两级 | 评估是否需「企业→年度→账簿」三级 |
| 部署自动化 | deploy.yml 就位，Secrets 待配 | 配置 GitHub Secrets |
| 安全测试 | 无专项自动化 | 补越权/注入测试 |
