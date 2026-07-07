# 时间戳精度规范

> **编号**：HUICAI-SPC-100
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

项目中所有时间戳字段（`createdAt`、`updatedAt`、`submittedAt`、`approvedAt`、`postedAt`、`auditedAt` 等）使用 `LocalDateTime`（Java 8 Date/Time API），数据库列类型为 `timestamp`（PostgreSQL）。

Jackson 默认将 `LocalDateTime` 序列化为 ISO-8601 格式（如 `2026-06-25T21:41:11.643184`），包含毫秒/纳秒精度。业务上不需要此精度，冗余信息徒增数据量且无实际用途。

> **关联需求**: REQ-2026-049
## 决定

**全项目统一：`LocalDateTime` 序列化/反序列化格式为 `yyyy-MM-dd'T'HH:mm:ss`（秒级精度），去除毫秒和纳秒。**

## 改动范围

### 1. Jackson 全局配置

`JacksonConfig.java` 注册 `Jackson2ObjectMapperBuilderCustomizer`，为 `JavaTimeModule` 添加 `LocalDateTimeSerializer` / `LocalDateTimeDeserializer`，使用 `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")`。

- 文件: `backend/src/main/java/com/huicai/common/config/JacksonConfig.java`
- 影响: 所有 Controller 响应的 JSON 序列化、请求体的 JSON 反序列化

### 2. application.yml 现有配置

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
```

此配置仅作用于 `java.util.Date`，不影响 `LocalDateTime`。保留不变。

### 3. 数据库

PostgreSQL `timestamp` 默认精度为 `timestamp(6)`（微秒）。改为 `timestamp(0)` 可在 DDL 层截断微秒。**当前暂不修改数据库列精度**——业务层序列化截断已满足需求，未来如有批量写入性能诉求可追加 DDL 变更。

## 不变

- `LocalDate` 序列化格式不变（默认 `yyyy-MM-dd`）
- DB 列类型不变（保持 `timestamp`，不改 `timestamp(0)`）
- 不涉及前端格式化逻辑（后端输出已是最终格式）

## 影响评估

- **无破坏性变更**：输出格式从 `2026-06-25T21:41:11.643184` 变为 `2026-06-25T21:41:11`，前端 JSON 解析兼容
- **测试**：涉及时间断言的测试可能需要调整预期值
