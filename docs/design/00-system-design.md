# 00-基础数据管理设计

> **编号**：HUICAI-DES-001
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.system`
> 设计文档：[主文档](../DESIGN.md)

---

## 1. 模块定位

传统财务软件的核心基础模块，提供科目体系、会计期间、用户权限、主数据管理能力。

**与传统对比：**
- 传统：科目编码手工维护，辅助核算独立
- 当前：科目树形CRUD，辅助核算字段嵌入凭证分录（aux_dept_id等5个字段），MyBatis 拦截器实现组织级数据隔离

## 2. 核心组件

| 组件 | 说明 | 传统对应 |
|------|------|---------|
| SubjectService | 会计科目增删改查、树形层级管理 | 科目表 |
| PeriodService | 会计期间管理、期间状态控制 | 期间维护 |
| VoucherTypeService | 凭证类型管理 | 凭证类型设置 |
| UserService | 用户管理 | 操作用户 |
| RoleService | RBAC 角色权限 | 权限组 |
| DeptService | 部门管理 | 部门档案 |
| CustomerService | 客户主数据 | 客户档案 |
| VendorService | 供应商主数据 | 供应商档案 |
| EmployeeService | 员工主数据 | 员工档案 |
| SysConfigService | 系统参数配置 | 系统设置 |
| AuditLogService | 审计日志查询 | 操作日志 |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_subject | 会计科目 | code, name, parent_id, level, direction, is_leaf |
| t_period | 会计期间 | year, month, status(open/closed/locked) |
| t_voucher_type | 凭证类型 | code, name, number_prefix |
| t_user | 用户 | username, password, dept_id |
| t_role | 角色 | name, code |
| t_user_role | 用户角色关联 | user_id, role_id |
| t_role_menu | 角色菜单权限 | role_id, menu_id |
| t_menu | 菜单/按钮 | name, permission, path |
| t_dept | 部门 | name, parent_id |
| t_customer | 客户 | code, name, tax_no, credit_limit |
| t_vendor | 供应商 | code, name, tax_no |
| t_employee | 员工 | code, name, dept_id |
| t_sys_config | 系统参数 | config_key, config_value |
| t_summary_lib | 常用摘要 | content |
| t_audit_log | 审计日志 | entity_type, entity_id, action, snapshot(jsonb) |
| t_voucher_template | 凭证模板 | name, subject_id, entry_template(jsonb) |

## 4. 关键设计

- 科目树形编码：parent_id + level 自关联，级联删除限制
- 期间控制：CLOSED/LOCKED 期间禁止写入任何财务数据
- 数据权限：MyBatis `DataPermissionInterceptor` 注入 SQL WHERE 条件
- 审计追踪：AOP `@Log` 注解 + jsonb 快照

## 5. API 端点

| 端点 | 方法 | 功能 |
|------|------|------|
| /api/v1/subjects/** | CRUD | 科目管理 |
| /api/v1/periods/** | CRUD | 期间管理 |
| /api/v1/voucher-types/** | CRUD | 凭证类型 |
| /api/v1/users/** | CRUD | 用户管理 |
| /api/v1/roles/** | CRUD | 角色管理 |
| /api/v1/depts/** | CRUD | 部门管理 |
| /api/v1/customers/** | CRUD | 客户管理 |
| /api/v1/vendors/** | CRUD | 供应商管理 |
| /api/v1/employees/** | CRUD | 员工管理 |

## 6. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 12 个 Service |
| 前端 | ✅ 完整 | 对应页面全部存在 |
| 测试 | ⚠️ 部分 | SubjectMapperTest 存在 |
| AI 辅助 | ❌ 无 | 基础数据无需 AI |

> **文档结束**