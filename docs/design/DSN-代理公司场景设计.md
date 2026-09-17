# 代理公司场景设计

> **关联PRD**：../prd/（待建，Agency 分支 PRD 待补）
> **关联SPEC**：S-26-agency-branch-development.md（多租户+角色+客户分配）
> **编号**：HUICAI-DES-013
> **版本**：V1.0 | **修改日期**：2026-09-17 | **修改人**：Hermes | **修改内容**：初始创建——服务流程管理（R-211）+ 工作量统计（R-212）设计，对齐竞品代账平台标配能力
> 代码包：`com.huicai.agency`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

Agency 分支已有多租户基础设施（S-26：用户⇔多企业切换、客户分配、数据权限三层防线）。
本模块补齐代账公司的**经营管理层**——管 100 个客户时，"谁还没做完"和"每个人产能如何"是核心管理问题。

**竞品基线**：易代账（用友）服务进度跟踪 + 工作量看板、金蝶云代账（CRM+服务评价+超期提醒）、慧算账（会计工厂标准化流程）。

**设计边界（铁律）**：进度与统计是**只读聚合视图**，不改变任何业务单据状态；
节点推进由对应业务动作（取票/制证/审核/结账）触发写入，本模块不新增任何审核/状态变更入口。

## 2. 数据模型

### 2.1 t_service_progress（服务进度表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 代账公司（Agency）租户 |
| enterprise_id | BIGINT | 客户企业 |
| period | VARCHAR(6) | 会计期间（YYYYMM） |
| stage | VARCHAR(20) | 节点：INTAKE(取票)→BOOKING(记账)→REVIEW(审核)→FILING(申报)→DONE(完成) |
| status | VARCHAR(20) | PENDING / IN_PROGRESS / DONE |
| assigned_to | BIGINT | 经办代理用户（NULL=未分配） |
| started_at | TIMESTAMP | 节点开始时间 |
| finished_at | TIMESTAMP | 节点完成时间 |
| due_date | DATE | 期限（FILING 按税种申报期限，其他按合同 SLA 配置） |
| overtime_notified_at | TIMESTAMP | 超期提醒已发送时间（幂等防重） |

**唯一约束**：`(tenant_id, enterprise_id, period, stage)` 唯一。

### 2.2 节点推进触发规则（事件驱动写入，不新建触发入口）

| 触发事件 | 推进到 | 来源 |
|---------|--------|------|
| 客户企业本期发票导入完成（InputInvoiceController） | INTAKE=DONE | 事件监听 |
| 本期凭证全部过账（VoucherController.batch-post 后校验） | BOOKING=DONE | 事件监听 |
| 本期结账完成（PeriodCloseController） | REVIEW=DONE | 事件监听 |
| 纳税申报 APPROVED（TaxController declarations） | FILING=DONE | 事件监听 |
| 人工强制标记（经理操作，留审计日志） | 任意节点 DONE | 管理入口 |

> 触发是"尽力而为"：某事件缺数据时该节点保持 IN_PROGRESS，汇总视图如实显示，不造假进度。

## 3. 工作量统计

**统计口径（只读聚合，无新表）**：

| 指标 | 数据源 | 说明 |
|------|--------|------|
| 每人负责客户数 | t_assignment（客户分配） | 当前有效分配 |
| 期间完成率 | t_service_progress | DONE 节点数 / 应做节点数 |
| 在办件数 | t_service_progress | status=IN_PROGRESS 按经办人分组 |
| 超期件数 | t_service_progress | due_date < 今天 且未 DONE |
| 人均产能 | 上述三列组合 | 月报/季报切换 |

**竞品差异说明**：金蝶/易代账的"工作量统计"均包含绩效评分。慧财 V1 只做**客观统计**，
绩效评分涉及薪酬主观判定，留给经理自行处理（超出财务系统边界）。

## 4. API 端点

| 端点 | 方法 | 说明 | SPEC |
|------|------|------|------|
| /api/v1/agency/service-progress | GET | 服务进度总览（参数：period, enterprise_id, stage, status；经理可见全租户，会计仅见自己的分配） | P79 |
| /api/v1/agency/service-progress/overtime | GET | 超期预警列表（due_date 已过未 DONE） | P79 |
| /api/v1/agency/service-progress/{id}/force-done | POST | 人工强制标记完成（仅经理，写审计日志） | P79 |
| /api/v1/agency/workload | GET | 工作量统计（参数：period_from, period_to, group_by=USER\|ENTERPRISE） | P79 |
| /api/v1/agency/workload/export | GET | 导出 Excel | P79 |

**响应结构（service-progress 示意）：**

```json
{
  "period": "202609",
  "rows": [
    {"enterpriseId": 101, "enterpriseName": "华东商贸",
     "stage": "REVIEW", "status": "IN_PROGRESS",
     "assignedToName": "王会计", "dueDate": "2026-10-15",
     "overtime": false}
  ],
  "summary": {"total": 42, "done": 30, "inProgress": 9, "overtime": 3}
}
```

## 5. 权限与数据隔离

| 角色 | 可见范围 | 可操作 |
|------|---------|--------|
| AGENCY_ADMIN / REVIEWER | 全租户全部客户 | force-done（仅 ADMIN） |
| ACCOUNTANT | 仅自己分配的客户 | 查看进度 |
| ASSISTANT | 仅自己分配的客户 | 查看进度 |

数据隔离复用 S-26 三层防线（enterprise_id 拦截器 + 客户分配校验 + RLS），
本模块**不引入新隔离机制**——统计类聚合 SQL 必须在拦截器作用域内执行（Mapper 层带 enterprise_id 条件）。

## 6. 与 R-211/R-212 的映射

| 需求 | 本节 | 说明 |
|------|------|------|
| R-211 服务流程管理 | §2 + §4 前三个端点 | 取票→记账→审核→报税节点跟踪 |
| R-212 工作量统计 | §3 + §4 后两个端点 | 每人客户数、完成率 |
| R-214 批量报税 | 不在本节 | 报税执行另建 SPEC；本模块只跟踪其完成节点 |

## 7. 异常与边界

| 场景 | 处理 |
|------|------|
| 客户企业本期无业务数据 | 节点保持 PENDING，计入"未完成"统计，不误报超期 |
| 事件触发顺序乱（先结账后过账完成事件） | stage 推进只允许前进不允许回退；乱序事件忽略并记日志 |
| 反结账/红冲 | 对应节点不回退进度（报表数据可反，但服务进度是过程记录），经理可 force 重置（留审计） |
| 超期提醒 | 每日批量扫描 due_date < 今天 且 overtime_notified_at IS NULL，走既有消息通道，扫描幂等 |

## 8. 非功能性约束

- 统计 SQL 走索引：`t_service_progress (tenant_id, period, stage)`、`(tenant_id, assigned_to, status)`
- 工作量报表 100 客户 × 4 节点规模下 < 2s（与项目列表查询指标一致）
- 全部金额/日期遵循项目约定（LocalDateTime 无秒、GMT+8）

## 9. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ❌ 待开发 | SPEC P79 待建；依赖 S-26 已完成的分配/隔离机制 |
| 前端 | ❌ 待开发 | 代理工作台加"进度/工作量"两个 Tab |
| 事件埋点 | ⚠️ 需梳理 | 4 个触发事件分布在 3 个模块（发票/凭证/结账/税务），需先加事件发布点 |

> **文档结束**。关联：[S-26-agency-branch-development](../specs/S-26-agency-branch-development.md) | [DSN-发票税务管理](./DSN-发票税务管理.md) | [DSN-总账管理](./DSN-总账管理.md)
