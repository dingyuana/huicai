# 编码规范（慧财财务）

## 通用规范

### 1. 命名约定
- 类名：大驼峰（`BankStatementService`）
- 方法名：小驼峰（`importBankStatement`）
- 常量：全大写下划线（`MAX_RETRY_COUNT`）
- 私有方法：单下划线开头（`_internalHelper`）

### 2. 注释约定
- 公共 API 必须写 Docstring
- 复杂业务逻辑必须写注释
- TODO 必须包含日期和负责人

### 3. 错误处理
- 使用统一的 `BusinessException`
- 严禁吞掉异常
- 错误信息必须清晰

### 4. 日志规范
- 使用结构化日志（JSON）
- 关键业务操作必须记录日志
- 敏感信息必须脱敏

---

## Python 规范

### FastAPI 最佳实践
1. 使用 Pydantic 定义请求/响应模型
2. 使用 Depends 做依赖注入
3. 使用中间件处理 CORS、日志
4. 所有 API 必须有健康检查

### LangGraph 最佳实践
1. 状态图必须有明确的注释
2. 每个节点必须有单测
3. 工具调用必须有超时控制

---

## Java 规范

### Spring Boot 最佳实践
1. 使用构造器注入
2. Service 层必须有事务
3. Controller 层只做参数转换
4. Entity 严禁直接暴露给前端

### MyBatis-Plus 最佳实践
1. 使用 QueryWrapper/LambdaQueryWrapper
2. 禁止写复杂的连表 SQL（拆分为多次查询）
3. 使用逻辑删除（`deleted=0`）

---

## 前端规范

### Vue 3 最佳实践
1. 使用 Composition API
2. 组件必须有 Props 类型定义
3. 使用 Pinia 做状态管理
4. API 调用必须封装在 composables 中

---

## 规范进化记录

### 2026-07-06 新增规范
1. API 入参必须做空值校验
2. 金额计算必须使用 BigDecimal
3. 状态机必须有负向断言

### 坑点记录
| 坑点 | 避免方案 |
|------|----------|
| Spring 循环依赖 | 使用 @Lazy + 构造器注入 |
| Flyway 迁移版本冲突 | 每次迁移前检查最新版本 |
| CORS 配置遗漏 | 开发环境明确允许 localhost 端口 |
