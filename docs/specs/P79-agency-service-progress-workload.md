# P79 SPEC — 代理服务进度跟踪与工作量统计

> **版本**：V1.0 | **最后修改**：2026-09-17 | **作者**：Hermes
> **状态**：📝 草案（待审核）
> **编号**：HUICAI-SPC-079 | 优先级：P2
> **依据**：竞品差距核查（DSN-竞品差距与管理类报表核查.md G-5）+ DSN-代理公司场景设计.md + 需求 R-211/R-212
> **目标**：为代账公司提供"取票→记账→审核→报税"服务节点跟踪（t_service_progress）+ 超期预警 + 工作量统计（每人客户数/完成率/在办/超期），补齐管 100 个客户的运营层
> **工期**：3-4 天（新表 migration + 事件埋点 + 聚合端点 + 前端 Tab）

> **关联需求**：REQ-2026-089（代理服务进度与工作量统计）、R-211（服务流程管理）、R-212（工作量统计）
> **依赖 SPEC**：S-26（多租户 + 客户分配 + 数据权限）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-17 | 初版。基于 DSN-代理公司场景设计.md（本次新建）落地数据模型与端点契约 |

---

## 0. 背景与问题

Agency 分支已完成多租户基础设施（S-26：用户⇔多企业切换、客户分配 `t_agency_user_enterprise`、数据权限三层防线）。但代账公司管 100 个客户时，两个管理问题无解：

1. **"谁还没做完"**——某企业 9 月的取票/记账/审核/报税走到哪一步了，无人跟踪，全靠会计口头汇报（R-211）
2. **"每人产能如何"**——每人负责几个客户、完成率多少、在办/超期几个，无统计（R-212）

竞品基线：易代账服务进度跟踪 + 工作量看板、金蝶云代账（进度 + 超期提醒 + 客户管理 CRM）。

**设计边界（铁律）**：进度与统计是**只读聚合视图**，不改变任何业务单据状态；节点推进由业务动作触发写入，本 SPEC 不新增审核/状态变更入口（铁律#1 人是唯一审核主体）。

---

## 1. 竞品对标

| 竞品 | 进度/统计 | 关键设计 | 结论 |
|------|---------|---------|------|
| **易代账（用友）** | 服务进度 + 工作量看板 | 按客户 × 服务节点跟踪；会计工作量统计 | ✅ 本 SPEC 对标节点结构 |
| **金蝶云代账** | 进度跟踪 + 超期提醒 + CRM | 临近交款日期提醒；服务评价 | ✅ 超期提醒标配 |
| **慧算账** | 会计工厂标准化流程 | 标准化节点 + 质控 | ✅ 节点化 |

**结论**：三家一致"节点化服务进度 + 工作量统计"。慧财 S-26 有分配/隔离，缺节点跟踪与统计层。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | 新表 `t_service_progress`（migration） | `V148__t_service_progress.sql` | 🟡 数据迁移 | ✅ 已完成 |
| 2 | P0 | 实体/Mapper/Service（进度读写 + 节点推进 advanceStage） | `com.huicai.agency.dashboard` | ✅ 低 | ✅ 已完成 |
| 3 | P0 | 进度端点 `GET /api/v1/agency/service-progress` + `/overtime` + `/{id}/force-done` | `ServiceProgressController` | ✅ 低 | ✅ 已完成 |
| 4 | P0 | 工作量端点 `GET /api/v1/agency/workload` | 同上 | ✅ 低 | ✅ 已完成 |
| 5 | P1 | 事件发布点：发票导入完成/凭证过账/结账完成/申报 APPROVED 4 个埋点（调 `advanceStage`） | 发票/凭证/结账/税务 4 模块 | 🟡 跨模块 | 📝 待开发 |
| 6 | P1 | 前端代理工作台"进度/工作量"两个 Tab | `frontend/src/views/agency/ServiceProgressView.vue` | ✅ 低 | ✅ 已完成 |

> **V1 交付边界**：P0（新表 + 实体/服务 + 端点 + 前端）已完成。P1 第 5 项"4 模块事件埋点"为跨模块改动（需改发票/凭证/结账/税务 4 个既有模块），留待下一迭代；当前 `advanceStage` 已就绪，事件源只需一行调用接入。

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 数据模型（新表）

> **数据模型对齐项目事实**：项目无 `tenant_id` 概念，租户维度 = `agency_id`（代账公司），客户维度 = `enterprise_id`。
> 实体 `ServiceProgressEntity extends BaseEntity`（自动带 `enterprise_id/created_by/created_at/updated_by/updated_at/version/deleted`）。
> **数据权限**：`t_service_progress` 列入 `EnterpriseDataPermissionInterceptor.SHARED_TABLES`（同 `t_agency_user_enterprise`），
> 拦截器**跳过自动注入** enterprise_id，由 Service 手动 `eq(agency_id)` 隔离 —— 避免破坏"经理看全部客户"的跨企业查询。

```sql
CREATE TABLE t_service_progress (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id             BIGINT       NOT NULL,          -- 代账公司（租户）
    enterprise_id         BIGINT       NOT NULL,          -- 被服务客户企业
    period                VARCHAR(6)   NOT NULL,
    stage                 VARCHAR(20)  NOT NULL,          -- INTAKE/BOOKING/REVIEW/FILING/DONE
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    assigned_to           BIGINT,                          -- t_agency_user.id
    started_at            TIMESTAMP,
    finished_at           TIMESTAMP,
    due_date              DATE,                            -- 申报期限/合同 SLA（V1 手工填）
    overtime_notified_at  TIMESTAMP,
    remark                VARCHAR(500),
    created_by            BIGINT,
    created_at            TIMESTAMP    DEFAULT now(),
    updated_by            BIGINT,
    updated_at            TIMESTAMP    DEFAULT now(),
    version               INTEGER      DEFAULT 0,
    deleted               INTEGER      DEFAULT 0,
    CONSTRAINT uq_service_progress UNIQUE (agency_id, enterprise_id, period, stage)
);
CREATE INDEX idx_sp_agency_period_stage ON t_service_progress (agency_id, period, stage);
CREATE INDEX idx_sp_assignee ON t_service_progress (agency_id, assigned_to, status);
CREATE INDEX idx_sp_due ON t_service_progress (agency_id, due_date, status);
```

**实体字段对齐项目约定**：`t_` 前缀、IDENTITY 主键、状态 String 大写、金额（如有）BigDecimal（本表无金额）。

### 3.2 输入契约

**进度端点** `GET /api/v1/agency/service-progress`：
- 可选参数：`period`（YYYYMM）、`enterprise_id`、`stage`、`status`
- 权限：经理（AGENCY_ADMIN/REVIEWER）见全租户；会计/助理仅见自己分配的企业（数据权限 S-26 三层防线）

**超期预警** `GET /api/v1/agency/service-progress/overtime`：
- 返回 `due_date < 今天 且 status != DONE` 的行

**人工强制标记** `POST /api/v1/agency/service-progress/{id}/force-done`：
- 权限：仅经理；请求体 `remark`（必填）；写审计日志（铁律#5）

**工作量端点** `GET /api/v1/agency/workload`：
- 参数：`periodFrom`、`periodTo`（YYYYMM 区间）、`groupBy`（`USER` 默认 / `ENTERPRISE`）

### 3.3 输出契约

```jsonc
// service-progress
{
  "period": "202609",
  "rows": [
    { "progressId": 1, "enterpriseId": 101, "enterpriseName": "华东商贸",
      "stage": "REVIEW", "status": "IN_PROGRESS",
      "assignedToName": "王会计", "dueDate": "2026-10-15", "overtime": false }
  ],
  "summary": { "total": 42, "done": 30, "inProgress": 9, "overtime": 3 }
}

// workload（groupBy=USER）
{
  "periodFrom": "202601", "periodTo": "202606", "groupBy": "USER",
  "rows": [
    { "userId": 5, "userName": "王会计", "assignedCustomers": 18,
      "completionRate": 0.94, "inProgress": 2, "overtime": 1 }
  ]
}
```

**口径定义（代码实证）**：

- **节点推进触发规则（事件驱动，不新建触发入口）**：

| 触发事件 | 推进到 | 来源 |
|---------|--------|------|
| 客户企业本期发票导入完成 | INTAKE=DONE | InputInvoiceController 事件 |
| 本期凭证全部过账 | BOOKING=DONE | VoucherController batch-post 后校验事件 |
| 本期结账完成 | REVIEW=DONE | PeriodCloseController 事件 |
| 纳税申报 APPROVED | FILING=DONE | TaxController declarations 事件 |
| 人工 force-done（经理，留审计） | 任意节点 DONE | 管理入口 |

- **工作量指标（只读聚合，无新表）**：
  - `assignedCustomers`：`t_agency_user_enterprise` 当前有效分配数（S-26）
  - `completionRate`：`DONE` 节点数 / 应做节点数（期间区间）
  - `inProgress` / `overtime`：按经办人分组聚合
- **事件幂等**：同一 `(tenant, enterprise, period, stage)` 已 DONE 的事件不重复推进（唯一约束 + 状态前置检查）
- **节点只进不退**：stage 推进只允许前进（INTAKE→BOOKING→REVIEW→FILING→DONE），乱序事件忽略记日志；反结账/红冲不回退进度（过程记录），经理可 force 重置（留审计）
- **超期提醒**：每日批量扫描 `due_date < 今天 且 overtime_notified_at IS NULL`，走既有消息通道，幂等（扫后回写 notified_at）

### 3.4 状态流转（节点状态机）

```
PENDING ──(事件触发/分配)──→ IN_PROGRESS ──(事件/force-done)──→ DONE
   终态：DONE（不可再推进；乱序事件忽略）
```

**负向断言**：
- DONE 不可回退到 IN_PROGRESS/PENDING（事件侧忽略，force 重置走独立审计入口，不在本状态机内）
- 事件不改变任何业务单据状态（铁律#1：人是唯一审核主体，本 SPEC 零状态变更入口）
- 会计/助理不可调 force-done（仅经理）

### 3.5 异常处理

| 场景 | 处理 | 错误码 |
|------|------|--------|
| `force-done` 缺 `remark` | `BusinessException`（400） | P79_001 |
| `force-done` 目标已 DONE | `BusinessException`（409 幂等拒绝） | P79_002 |
| `force-done` 非经理角色 | 403（S-26 权限） | P79_003 |
| 期间参数缺失/非法（workload） | 400 | P79_004 |
| 事件乱序（stage 已 DONE 收到前序事件） | 忽略 + WARN 日志，不报错 | P79_005（WARN） |
| 客户企业本期无业务数据 | 节点保持 PENDING，不计误报超期 | — |
| 数据隔离 | S-26 三层防线；统计 SQL 在拦截器作用域内（Mapper 带 enterprise_id） | — |

**事务**：`force-done` 单表写 + 审计日志 `@Transactional(rollbackFor = Exception.class)`；事件推进单表 UPSERT 同事务。

---

## 4. BDD 验收场景

### 场景 1：事件推进节点（L2 集成，Testcontainers PG16，🟡 事件埋点）

```gherkin
Given 企业 101 期间 202609 的 t_service_progress 行 stage=INTAKE, status=PENDING
When 发票导入完成事件触发
Then 该行 status=IN_PROGRESS→DONE, finished_at 落库
And 节点只进不退：再次触发 REVIEW=DONE 事件后 INTAKE 保持 DONE 不回退（负向断言）
```

### 场景 2：事件幂等（L2 集成，🟡 幂等守卫）

```gherkin
Given 企业 101 期间 202609 stage=INTAKE 已 DONE
When 同一事件再次触发
Then 不产生重复行/不回退/不改 finished_at（唯一约束 + 状态前置检查）
```

### 场景 3：force-done 权限与审计（L2 Controller，🟡 权限 + 审计）

```gherkin
Given 经理对一条 IN_PROGRESS 节点
When POST /{id}/force-done（remark="客户暂停经营"）
Then 该行 status=DONE, finished_at=now
And 审计日志记录操作人/时间/remark（铁律#5）
And 会计角色调同端点 → 403 P79_003（负向断言）
```

### 场景 4：超期预警（L2 集成）

```gherkin
Given 一条 due_date=昨天、status=IN_PROGRESS 的节点 + 一条 due_date=昨天、status=DONE 的节点
When GET /service-progress/overtime
Then 仅第一条返回（负向断言：DONE 不计超期）
And 每日扫描幂等：第二次扫描不重复提醒（overtime_notified_at 已写）
```

### 场景 5：工作量统计（L2 集成，🟡 聚合口径）

```gherkin
Given 王会计分配 18 家客户，区间内 18×4 节点中 67 个 DONE
When GET /workload?periodFrom=202601&periodTo=202606&groupBy=USER
Then 王会计行 assignedCustomers=18, completionRate=67/72≈0.93, 在办/超期计数正确
And 负向断言：他人数据不混入（agency_id 租户隔离，Service 手动 eq(agency_id)）
```

### 场景 6：负向断言——零业务状态变更（L1）

```gherkin
Given 事件触发节点推进全程
When 完成 INTAKE→DONE
Then 发票/凭证/结账/申报各业务单据状态与推进前完全一致（本 SPEC 不写任何业务表）
```

---

## 5. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | +1 表（t_service_progress，migration 🟡 数据迁移） |
| 后端 | 实体/Mapper/Service + 2 Controller 共 5 端点 + 4 模块事件发布点 |
| 前端 | 代理工作台 +2 Tab（进度/工作量） |
| 测试 | +6 测试（6 场景，含 3 个 🟡 强制 Testcontainers） |
| API | 新增 5 端点 |

**性能**：统计 SQL 走 `idx_sp_period_stage`、`idx_sp_assignee` 索引；100 客户 × 4 节点规模 < 2s（与项目列表查询指标一致）。

---

## 6. 遗留事项

- **R-214 批量报税执行**（另立项）：本 SPEC 只跟踪其 FILING=DONE 节点，不做报税执行
- **服务评价/客户满意度**（竞品 CRM 项）：超出财务系统边界，另立项
- **绩效评分**：只做客观统计，薪酬判定留给经理（设计文档已说明边界）
- **due_date SLA 配置化**：V1 按税种申报期限硬编码 + 合同约定，配置化另立项

---

```yaml
# === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
entity: ServiceProgressEntity
module: agency
table: t_service_progress

states:
  PENDING:
    description: "待处理（节点已生成未开始）"
    initial: true
    terminal: false
  IN_PROGRESS:
    description: "进行中（事件触发开始）"
    initial: false
    terminal: false
  DONE:
    description: "完成（事件完成或 force-done，终态不可回退）"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: PENDING
    to: IN_PROGRESS
    trigger: onStageStart
    precondition: "status == PENDING"
    postcondition: "status = IN_PROGRESS; started_at = now"
    side_effects: []
    test_ref: test_stage_event_in_progress
  - id: T-02
    from: IN_PROGRESS
    to: DONE
    trigger: onStageComplete
    precondition: "status == IN_PROGRESS && 对应业务事件已完成（如发票导入完成）"
    postcondition: "status = DONE; finished_at = now"
    side_effects: []
    test_ref: test_stage_event_done
  - id: T-03
    from: IN_PROGRESS
    to: DONE
    trigger: forceDone
    precondition: "仅经理；remark 必填"
    postcondition: "status = DONE; 审计日志写入（operator_id/name/remark）"
    side_effects:
      - entity: AuditLogEntity
        action: create
        status: DRAFT
    test_ref: test_force_done_with_audit
    negative_assertions:
      - assertion: "会计/助理调 force-done 应被 403 拒绝"
        method: test_force_done_denied_for_accountant

constraints:
  - id: C-01
    type: business
    rule: "事件幂等：同 (tenant, enterprise, period, stage) 已 DONE 的事件忽略（唯一约束 + 状态前置）"
    enforcement: "UPSERT 前置检查 + UNIQUE 约束"
  - id: C-02
    type: business
    rule: "节点只进不退；乱序事件忽略记 WARN"
    enforcement: "stage 顺序前置校验"
  - id: C-03
    type: immutability
    rule: "本 SPEC 不写任何业务表（发票/凭证/结账/申报），事件只读业务状态推进进度行（铁律#1）"
    enforcement: "事件监听器只 INSERT/UPDATE t_service_progress"
  - id: C-04
    type: security
    rule: "统计 SQL 在数据权限拦截器作用域内（Mapper 带 enterprise_id，S-26 三层防线）"
    enforcement: "MyBatis 拦截器注入"

acceptance_tests:
  - id: AT-001
    description: "事件推进节点"
    method: test_stage_event_progression
    assertion: "PENDING→IN_PROGRESS→DONE；节点只进不退"
    status: missing
  - id: AT-002
    description: "事件幂等"
    method: test_event_idempotent
    assertion: "已 DONE 事件重触发无副作用"
    status: missing
  - id: AT-003
    description: "force-done 权限+审计"
    method: test_force_done_with_audit
    assertion: "经理可 force-done 且审计落库；会计 403"
    status: missing
  - id: AT-004
    description: "超期预警"
    method: test_overtime_scan
    assertion: "due_date<today 且非 DONE 才返回；扫描幂等不重发"
    status: missing
  - id: AT-005
    description: "工作量统计"
    method: test_workload_stats
    assertion: "assignedCustomers/completionRate/inProgress/overtime 口径正确，跨租户不混入"
    status: missing
  - id: AT-006
    description: "零业务状态变更"
    method: test_no_business_state_change
    assertion: "事件推进后各业务单据状态与推进前一致"
    status: missing

out_of_scope:
  - "R-214 批量报税执行（另立项，本 SPEC 只跟踪 FILING 节点）"
  - "客户满意度/评价（CRM，超出边界）"
  - "绩效评分（留经理人工）"
  - "due_date SLA 配置化（V1 硬编码）"

dependencies:
  - spec: S-26
    relation: "多租户隔离/客户分配（t_agency_user_enterprise）/权限三层防线（本 SPEC 复用）"
  - spec: P73
    relation: "无直接依赖；事件来源分布发票/凭证/结账/税务 4 模块"
```

> **文档结束**。关联：[S-26-agency-branch-development](./S-26-agency-branch-development.md) | [DSN-代理公司场景设计](../design/DSN-代理公司场景设计.md)
