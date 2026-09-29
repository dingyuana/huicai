# P103 SPEC — 审计追踪真实落地（快照落库 + updateById 覆盖）

> **版本**：V1.0（草案，待老丁审核） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P103 | 优先级：**P0（商用门槛）** | 状态：📋 待审核
> **来源**：P101 总纲 M4；审计代理 C 实测「铁律 #5 实质失效，0% 合规」
> **关联需求**：REQ-2026-130 | **前置**：P102（权限码就位，越权/敏感操作复用） | **test_ref**：`AuditSnapshotRealDBTest`、`AuditTrackingAspectTest`
> **排除**：AI 功能

---

## 0. 缺陷背景（已实测）

| # | 缺陷 | 证据 |
|---|---|---|
| 1 | `AuditLogEntity` 4 个快照字段标 `@TableField(exist = false)`，**完全不参与 SQL**；真实列为 `before_data`/`after_data JSONB` | `AuditLogEntity.java:26-33`；`t_audit_log` 实测列：`id,module,operation,entity_type,entity_id,entity_no,before_data,after_data,operator_id,operator_name,operation_time,ip_address,deleted` |
| 2 | `AuditTrackingAspect` 只拦 `@Auditable`(5 处) + `insert` + `deleteById`，**`updateById` 完全无审计** | `AuditTrackingAspect.java:32/116/151`；全库 `@Auditable` = 5 |
| 3 | `AuditLogServiceImpl:34` 按 `status` 查库，而 `t_audit_log` **无 `status` 列** → 带参查询必报 SQL 错 | 实测列清单 |
| 4 | 状态变更快照手工拼串 `"entityId=..., field=..."`，**根本不是 JSON**，无转义 | `AuditLogServiceImpl.java:66-69` |
| 5 | `AuditLogEntity` 缺 `entityType/entityId/entityNo` 声明 → `idx_audit_log_entity` 索引永久失效（违反铁律 #9） | 同上 |

**后果**：审计表**有行无快照**，「谁在什么时候把什么改成什么」完全查不到 —— 铁律 #5 名存实亡，商用审计一票否决。

## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| 领域操作 | Service/Mapper 层 | 覆盖 `insert` / `updateById` / `deleteById` 三类写 |
| 变更前后值 | JSON | 序列化为合法 JSON（`jsonb`），含变更字段与操作人/时间/IP |
| 权限上下文 | P102 | 越权 403、清库等敏感操作同样留痕 |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| 快照落库 | 关键实体 `updateById` 后 `t_audit_log` 有非空 `before_data`/`after_data`，为合法 JSON |
| 溯源完整 | `entity_type`/`entity_id`/`entity_no` 均落库，索引可用（能按实体查审计） |
| 覆盖完整 | `updateById` 进切面；核心实体（凭证/单据/发票/核销/科目）全覆盖 |
| 查询可用 | 按模块/操作/实体/时间过滤均不报 SQL 错；删不存在的 `status` 过滤 |

## 3. 状态流转

```
写操作(insert/updateById/deleteById 或 @Auditable)
   ──> AuditTrackingAspect 环绕 ──> 序列化 before/after 为 JSON
   ──> 写 t_audit_log(operator/ip/时间/实体三标识/快照) ──> 原业务照常
```
切面**旁路**：审计写失败记 `log.error` + 写最小快照（id/操作人/时间），**不阻断业务但必须留痕**（不吞异常后返回「成功且无痕」）。

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| 快照序列化失败（循环引用/非序列字段） | `log.error` + 写最小快照，不抛异常打断业务 |
| `before_data` 取旧值失败 | 同上；不得留空 |
| 审计表写失败 | `log.error` 告警；业务不回滚（审计旁路），但需在监控可见 |

## 5. BDD 行为契约

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-103-1 | 一条 DRAFT 凭证 | `updateById` 改金额 | `t_audit_log` 有该凭证记录，`before_data` 含旧金额、`after_data` 含新金额，合法 JSON | 真实 DB |
| AT-103-2 | 任意核心实体 | insert | 有 `entity_type`+`entity_id`+`entity_no`，可按实体反查（索引生效） | 真实 DB |
| AT-103-3 | 含引号/换行的字段值 | 状态变更 | 快照为合法 JSON（能 `::jsonb` 解析），不因未转义而损坏 | 真实 DB |
| AT-103-4 | 查审计带 `status` 参数 | — | 不报 `column "status" does not exist` | 真实 DB |
| AT-103-5 | 凭证/单据/发票/核销/科目 | 各做一次写 | 五类均在 `t_audit_log` 留痕 | 真实 DB |

## 6. 竞品对标（铁律 #15）

| 维度 | 用友 U8 | 金蝶 K/3 | SAP B1 | Xero | QuickBooks | 本修法 |
|---|---|---|---|---|---|---|
| 字段级变更留痕 | ✅ 全量 | ✅ | ✅ | 弱 | ✅ | before/after JSON 落库 |
| 按实体反查审计 | ✅ | ✅ | ✅ | 弱 | ✅ | 补 `entity_type/id/no` + 索引 |
| 覆盖 update | ✅ | ✅ | ✅ | ⚠️ | ✅ | `updateById` 进切面（当前缺） |

**结论**：审计追踪是财务 SaaS 商用底线（四家均全量字段级留痕）。本项目「有行无快照」属致命差距，本 SPEC 补齐至竞品底线。

## 7. 风险与不在范围

- **审计表膨胀** → 快照只存变更字段，非全量 dump；必要时加保留策略（另议）。
- **切面覆盖 `updateById` 性能** → 快照只取变更字段，非全表 dump。
- **不在范围**：审计报表 UI、归档策略、AI 功能。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：修复铁律 #5 实质失效（exist=false 快照 / updateById 缺审计 / status 死列 / 非 JSON 拼串） |
