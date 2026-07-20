# P24 SPEC — 状态变更审计追踪规格书
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-024 | 优先级：高（P24）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §6 审计追踪
> 目标：用 AOP 切面自动捕获 `invoice.status` / `voucher.status` 等状态字段变更，写入现有 `t_audit_log`
> 工期：单批交付，3 个 commit
> 核心决策：**复用现有 `AuditLogEntity`**，不新建表；新建 `StatusChangeAspect` 切面 + `StatusChangeRecord` 注解

---

> **关联需求**: REQ-2026-051

## 1. 输入契约
→ 见本文 [改动清单总览 / 核心注解 / 参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [状态变更审计流程 / StatusChangeable 注解 / AOP 切面] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `StatusChangeable` 注解（标记需要审计的字段）| `backend/.../common/annotation/StatusChangeable.java` | ✅ 低 |
| 2 | 创建 `StatusChangeAspect` AOP 切面 | `backend/.../common/aspect/StatusChangeAspect.java` | 🟡 中 |
| 3 | 创建 `AuditLogService`（封装 t_audit_log 写入逻辑）| `backend/.../system/service/AuditLogService.java` | 🟡 中 |
| 4 | `OutputInvoiceEntity` / `InputInvoiceEntity` / `VoucherEntity` 加 `@StatusChangeable` 注解 | 3 Entity 文件 | ✅ 低 |
| 5 | 调用方适配（确保 status 变更走 Service 方法，不直接 updateById）| 调用方 | 🟡 中 |
| 6 | 单测覆盖（≥8 @Test，验证 AOP 拦截）| Test 文件 | ✅ 低 |

> **关键复用**：`com.huicai.module.system.entity.AuditLogEntity` 已有 Jsonb 字段（oldSnapshot/newSnapshot），不需要 schema 改动。

---

## 1. 核心注解

### 1.1 `@StatusChangeable`

```java
package com.huicai.module.common.annotation;

import java.lang.annotation.*;

/**
 * 标记需要审计的状态字段.
 * 被 StatusChangeAspect 拦截，写入 t_audit_log.
 *
 * 用法：
 * <pre>
 * public class OutputInvoiceEntity {
 *     {@code @StatusChangeable(entity = "OUTPUT_INVOICE", fieldName = "status")}
 *     private String status;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StatusChangeable {
    /** 实体类型，用于 audit_log.module 字段 */
    String entity();
    /** 字段名，用于 audit_log.field_name 字段 */
    String fieldName();
}
```

---

## 2. AuditLogService

**接口**：

```java
package com.huicai.module.system.service;

/**
 * 审计日志服务.
 * 写入现有 t_audit_log，复用 AuditLogEntity.
 */
public interface AuditLogService {

    /**
     * 记录状态变更.
     *
     * @param entityType   OUTPUT_INVOICE / INPUT_INVOICE / VOUCHER
     * @param entityId     实体 ID
     * @param fieldName    字段名（如 "status"）
     * @param oldValue     变更前值
     * @param newValue     变更后值
     * @param changeReason 变更原因（作废/驳回时必填）
     */
    void recordStatusChange(String entityType, Long entityId,
        String fieldName, String oldValue, String newValue,
        String changeReason);
}
```

**实现**：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    // 必须在调用方事务内，与业务变更同事务
    public void recordStatusChange(String entityType, Long entityId,
            String fieldName, String oldValue, String newValue,
            String changeReason) {
        Long userId = SecurityContextHolder.getUserId();

        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setUsername(SecurityContextHolder.getUsername());
        log.setOperation("STATUS_CHANGE");
        log.setMethod(entityType + ".updateStatus");
        log.setModule("state-machine");
        log.setOldSnapshot(buildSnapshot(fieldName, oldValue));
        log.setNewSnapshot(buildSnapshot(fieldName, newValue));
        log.setRequestParams("entityId=" + entityId + ", field=" + fieldName);
        log.setResponseResult("newValue=" + newValue);
        log.setStatus("SUCCESS");
        auditLogMapper.insert(log);

        log.info("审计日志写入: entity={}, id={}, field={}, {} → {}",
            entityType, entityId, fieldName, oldValue, newValue);
    }

    private String buildSnapshot(String fieldName, String value) {
        // Jsonb 格式: {"fieldName": "value"}
        return "{\"" + fieldName + "\":\"" + (value == null ? "" : value) + "\"}";
    }
}
```

---

## 3. StatusChangeAspect 切面

```java
@Aspect
@Component
@RequiredArgsConstructor
public class StatusChangeAspect {

    private final AuditLogService auditLogService;

    /**
     * 拦截所有 updateById 方法调用.
     * 如果 Entity 含 @StatusChangeable 字段且值发生变化，写入审计日志.
     *
     * 注意：依赖 MyBatis-Plus 的 updateById 入口；
     * 如果调用方绕过 Service 直接调用 mapper.updateById，AOP 不拦截.
     */
    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.updateById(..))" +
            " && args(entity)")
    public Object aroundUpdateById(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        // 1. 查找 @StatusChangeable 字段
        Field[] fields = entity.getClass().getDeclaredFields();
        Field statusField = null;
        StatusChangeable annotation = null;
        for (Field f : fields) {
            StatusChangeable a = f.getAnnotation(StatusChangeable.class);
            if (a != null) {
                statusField = f;
                annotation = a;
                break;
            }
        }

        // 2. 没有标记的字段，直接放过
        if (statusField == null) {
            return pjp.proceed();
        }

        // 3. 取新值
        statusField.setAccessible(true);
        Object newValue = statusField.get(entity);

        // 4. 查旧值（必须在 update 前查）
        // 通过反射调用 BaseMapper.selectById(id) 拿旧 Entity
        // ... (省略反射获取 mapper 的样板代码)

        Object oldEntity = selectByIdViaReflection(entity);
        Object oldValue = extractStatusValue(oldEntity, statusField);

        // 5. 值相同 → 不记录
        if (Objects.equals(oldValue, newValue)) {
            return pjp.proceed();
        }

        // 6. 执行原 update
        Object result = pjp.proceed();

        // 7. 写审计日志（同事务）
        try {
            auditLogService.recordStatusChange(
                annotation.entity(),
                (Long) getIdViaReflection(entity),
                annotation.fieldName(),
                String.valueOf(oldValue),
                String.valueOf(newValue),
                null  // 调用方可在 Service 内显式补充原因
            );
        } catch (Exception e) {
            log.error("审计日志写入失败（业务事务将回滚）", e);
            throw e;  // 失败必须让业务事务回滚
        }

        return result;
    }
}
```

---

## 4. Entity 字段标注

### 4.1 `OutputInvoiceEntity`

```java
@StatusChangeable(entity = "OUTPUT_INVOICE", fieldName = "status")
private String status;
```

### 4.2 `InputInvoiceEntity`

```java
@StatusChangeable(entity = "INPUT_INVOICE", fieldName = "status")
private String status;
```

### 4.3 `VoucherEntity`

```java
@StatusChangeable(entity = "VOUCHER", fieldName = "status")
private String status;
```

### 4.4 扩展建议（未来 SPEC，不在本期）

- ReceivableEntity / PayableEntity（P20 范围）
- PrepaymentEntity（P20 范围）
- BusinessDocEntity（P20 §9 明确不动，本 SPEC 不扩展）

---

## 5. 与 P21-a/P21-b/P22 的协作

| 场景 | 触发链 |
|:---|:---|
| 销售发票审核通过 | `OutputInvoiceStateMachineService.confirm()` → updateById → **AOP 拦截** → 审计写入 |
| 凭证驳回 | `VoucherStateMachineService.reject()` → updateById → AOP → 审计写入（rejected_reason 也写入）|
| 红字冲销 | `VoucherStateMachineService.generateReversalVoucher()` → insert 红字 + updateById 原凭证 → **2 条审计写入** |

**关键约定**：
- P21-a/P21-b/P22 的 Service 实现**不显式调用** auditLogService（由 AOP 统一拦截）
- 作废/驳回等需要 reason 的场景，调用方在 Entity 上 setRemark/rejectedReason，AOP 拦截后从 Entity 提取（**扩展点**）

---

## 6. 测试要点

| 测试场景 | 方法 | 期望 |
|---------|------|------|
| status 变化写入 audit_log | `testStatusChangeAudit()` | audit_log 新增 1 条 |
| status 不变不写 | `testNoChangeNoAudit()` | audit_log 无新增 |
| POSTED 凭证 status 变化 | `testPostedChangeAudit()` | audit_log 新增 1 条（铁律审计）|
| 红字冲销双审计 | `testReversalDoubleAudit()` | audit_log 新增 2 条（原+红字）|
| AOP 拦截失败回滚业务事务 | `testAuditFailureRollback()` | status 未变，audit_log 未写入 |
| 审计写入无 userId 上下文 | `testNoUserContext()` | 抛异常或记录 system 用户 |
| @StatusChangeable 缺失 | `testNoAnnotationNoAudit()` | 直接 update，无审计 |
| 字段值含 JSON 特殊字符 | `testSpecialCharsInValue()` | Jsonb 正确转义 |

**单测要求**：≥8 @Test，用 `@SpringBootTest` 启用 AOP 代理；或用 `AnnotationAwareAspectJAutoProxyCreator` 手动注入 Aspect。

---

## 7. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/audit-logs?entityType=OUTPUT_INVOICE&entityId=123` | 查询审计日志（已有或新增）|

**前端**：审计日志查询页面（如果还没有，新建）。

---

## 8. 不做事项

- ❌ 不新建 t_audit_log 表（复用现有）
- ❌ 不实现"全字段变更"审计（仅 status 字段）
- ❌ 不实现"操作回放"功能（仅记录，不支持 replay）
- ❌ 不实现审计日志归档清理（永久保留）
- ❌ 不接收 Kafka 异步事件（同步写入，与业务同事务）

---

## 9. 后续依赖

- **依赖 P21-a / P21-b / P22**：3 个 Entity 加 `@StatusChangeable` 注解
- **被 P21-a / P21-b / P22 引用**：上线后这些 SPEC 的 Service 实现不需要改动即可获得审计能力
- **未来**：Kafka 异步事件 + 跨服务审计日志聚合（不在本期）

---

## 10. 安全注意事项

按 `huicai-java-backend` skill §10 第 7 条："公开 REST 端点必加权限校验"：

- `GET /api/v1/audit-logs` 必须加 `@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'AUDITOR')")`
- 防止普通用户查到他人的操作历史

**审计日志写入必须 fail-fast**：审计失败必须让业务事务回滚（按 §6 测试场景），不允许"业务成功但审计丢失"。
|---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P24-1 | 状态变更写入审计日志 | `confirm() → audit_log count +1` |
| AT-P24-2 | 审计日志含旧值新值 | `audit_log.oldSnapshot != null AND newSnapshot != null` |
| AT-P24-3 | 审核人ID记录 | `audit_log.userId == current user` |
| AT-P24-4 | 非状态变更不写审计 | `updateById without status change → audit_log count unchanged` |

---

## 11. BDD 验收场景（Given-When-Then）

### 11.1 状态变更 — 正常写入审计日志

```gherkin
Scenario: 状态变更时自动写入审计日志
  Given 存在一条 OUTPUT_INVOICE 记录，其 status = "DRAFT"
    And 当前登录用户为审核员张三
  When 调用 OutputInvoiceStateMachineService.confirm() 将 status 改为 "CONFIRMED"
  Then t_audit_log 表应新增 1 条记录
    And 该记录的 module = "state-machine"
    And 该记录的 operation = "STATUS_CHANGE"
    And 该记录的 userId = 张三
    And 该记录的 oldSnapshot 包含 "status": "DRAFT"
    And 该记录的 newSnapshot 包含 "status": "CONFIRMED"
```

### 11.2 状态不变 — 不写入审计日志

```gherkin
Scenario: 状态值未变化时不写入审计日志
  Given 存在一条 OUTPUT_INVOICE 记录，其 status = "CONFIRMED"
  When 调用 updateById 且 status 仍为 "CONFIRMED"（未实际变更）
  Then t_audit_log 表应无新增记录
```

### 11.3 多实体审计覆盖

```gherkin
Scenario Outline: 各类实体状态变更均被审计
  Given 存在一条 <entity> 记录，其 status = "<oldStatus>"
  When 调用 updateById 将 status 改为 "<newStatus>"
  Then t_audit_log 表新增 1 条记录
    And 该记录的 entityType = "<entityType>"

  Examples:
    | entity            | entityType      | oldStatus | newStatus   |
    | OUTPUT_INVOICE    | OUTPUT_INVOICE  | DRAFT     | CONFIRMED    |
    | INPUT_INVOICE     | INPUT_INVOICE   | DRAFT     | VERIFIED     |
    | VOUCHER           | VOUCHER         | DRAFT     | POSTED       |
```

### 11.4 红字冲销 — 双审计记录

```gherkin
Scenario: 红字冲销生成两条审计日志
  Given 存在一条 VOUCHER 记录，其 status = "POSTED"
  When 调用 VoucherStateMachineService.generateReversalVoucher()
  Then t_audit_log 表应新增 2 条记录
    And 第 1 条记录：原凭证 status 从 "POSTED" → "REVERSED"
    And 第 2 条记录：红字凭证 INSERT 后的 status 审计
```

### 11.5 审计写入失败 — 业务事务回滚

```gherkin
Scenario: 审计日志写入失败时业务事务回滚
  Given 存在一条 OUTPUT_INVOICE 记录，其 status = "DRAFT"
    And AuditLogService 抛出异常（如数据库不可用）
  When 调用 updateById 试图将 status 改为 "CONFIRMED"
  Then 业务事务应回滚
    And 该记录的 status 仍为 "DRAFT"
    And t_audit_log 表无新增记录
```

### 11.6 无用户上下文 — 安全降级

```gherkin
Scenario: SecurityContext 中无用户信息时审计写入失败
  Given 存在一条 OUTPUT_INVOICE 记录，其 status = "DRAFT"
    And SecurityContextHolder.getUserId() 返回 null
  When 调用 updateById 将 status 改为 "CONFIRMED"
  Then 应抛出异常
    And 业务事务回滚
    And t_audit_log 表无新增记录
```

### 11.7 无 @StatusChangeable 注解 — 不审计

```gherkin
Scenario: 未标记 @StatusChangeable 的字段不触发审计
  Given 存在一条 Entity 记录，其字段未标注 @StatusChangeable
  When 调用 updateById 修改该字段值
  Then t_audit_log 表无新增记录
    And 业务更新正常执行
```

### 11.8 审计日志查询 API

```gherkin
Scenario: 按 entityType + entityId 查询审计日志
  Given 存在 3 条审计日志，entityType = "OUTPUT_INVOICE", entityId = 123
  When 当前用户是 ADMIN 角色
    And 发送 GET /api/v1/audit-logs?entityType=OUTPUT_INVOICE&entityId=123
  Then 响应状态码为 200
    And 响应 body 包含 3 条记录
    And 每条记录的 userId 不为空
    And 每条记录的 oldSnapshot 和 newSnapshot 均为有效 JSON

Scenario: 无权限用户查询审计日志返回 403
  Given 当前用户是普通用户（无 ADMIN / FINANCE / AUDITOR 角色）
  When 发送 GET /api/v1/audit-logs?entityType=OUTPUT_INVOICE&entityId=123
  Then 响应状态码为 403
```

### 11.9 字段值含特殊字符

```gherkin
Scenario: 状态字段值包含 JSON 特殊字符时正确转义
  Given 存在一条 VOUCHER 记录，其 rejectedReason = "金额不符，请"重新"提交"
  When 调用 updateById 记录驳回原因
  Then t_audit_log 的 oldSnapshot/newSnapshot 为合法 Jsonb
    And 特殊字符（引号、反斜杠等）被正确转义
```

---

## 12. 输出契约 — YAML 合约

### 12.1 审计日志查询 API

```yaml
openapi: 3.0.3
info:
  title: 审计追踪查询 API
  version: "1.0.0"
  description: P24 状态变更审计日志查询接口

paths:
  /api/v1/audit-logs:
    get:
      summary: 查询审计日志
      description: 按实体类型 + 实体 ID 查询状态变更审计日志
      operationId: queryAuditLogs
      tags:
        - 审计追踪
      parameters:
        - name: entityType
          in: query
          description: 实体类型
          required: true
          schema:
            type: string
            enum: [OUTPUT_INVOICE, INPUT_INVOICE, VOUCHER]
          example: OUTPUT_INVOICE
        - name: entityId
          in: query
          description: 实体 ID
          required: true
          schema:
            type: integer
            format: int64
          example: 123
        - name: pageNum
          in: query
          description: 页码
          required: false
          schema:
            type: integer
            default: 1
        - name: pageSize
          in: query
          description: 每页条数
          required: false
          schema:
            type: integer
            default: 20
            maximum: 100
      security:
        - bearerAuth: []
      responses:
        "200":
          description: 成功返回审计日志列表
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    example: 200
                  message:
                    type: string
                    example: "success"
                  data:
                    type: object
                    properties:
                      records:
                        type: array
                        items:
                          $ref: "#/components/schemas/AuditLogResponse"
                      total:
                        type: integer
                        description: 总记录数
                        example: 1
                      pageNum:
                        type: integer
                        example: 1
                      pageSize:
                        type: integer
                        example: 20
        "403":
          description: 无权限
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        "500":
          description: 服务器内部错误
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    AuditLogResponse:
      type: object
      description: 审计日志响应
      properties:
        id:
          type: integer
          format: int64
          description: 审计日志 ID
        userId:
          type: integer
          format: int64
          description: 操作人 ID
        username:
          type: string
          description: 操作人用户名
        operation:
          type: string
          description: 操作类型（固定为 "STATUS_CHANGE"）
          example: STATUS_CHANGE
        method:
          type: string
          description: 方法名
          example: OUTPUT_INVOICE.updateStatus
        module:
          type: string
          description: 模块名（固定为 "state-machine"）
          example: state-machine
        oldSnapshot:
          type: string
          description: 变更前快照（Jsonb 格式：{"fieldName": "value"}）
          example: '{"status": "DRAFT"}'
        newSnapshot:
          type: string
          description: 变更后快照（Jsonb 格式：{"fieldName": "value"}）
          example: '{"status": "CONFIRMED"}'
        requestParams:
          type: string
          description: 请求参数
          example: "entityId=123, field=status"
        responseResult:
          type: string
          description: 响应结果
          example: "newValue=CONFIRMED"
        status:
          type: string
          description: 日志状态（固定为 "SUCCESS"）
          example: SUCCESS
        createTime:
          type: string
          format: date-time
          description: 创建时间
          example: "2026-07-19T14:30:00"

    ErrorResponse:
      type: object
      description: 错误响应
      properties:
        code:
          type: integer
          example: 403
        message:
          type: string
          example: "权限不足"
```

### 12.2 内部服务接口 — AuditLogService

```yaml
service:
  name: AuditLogService
  description: 审计日志写入服务（内部调用，非 REST）
  methods:
    recordStatusChange:
      description: 记录状态变更审计日志
      transactional: MANDATORY  # 必须在调用方事务内
      parameters:
        - name: entityType
          type: string
          required: true
          description: 实体类型（OUTPUT_INVOICE / INPUT_INVOICE / VOUCHER）
        - name: entityId
          type: integer
          format: int64
          required: true
          description: 实体 ID
        - name: fieldName
          type: string
          required: true
          description: 变更字段名（如 "status"）
        - name: oldValue
          type: string
          required: true
          description: 变更前值
        - name: newValue
          type: string
          required: true
          description: 变更后值
        - name: changeReason
          type: string
          required: false
          description: 变更原因（作废/驳回时必填）
      throws:
        - exception: 审计写入异常
          description: 写入失败时抛出，导致业务事务回滚
```
