# 多租户架构设计（Agency + SME 双模式）

> **版本**：V2.0 | **最后修改**：2026-07-24 | **作者**：Hermes
> **状态**：📋 草案
> **V2.0 变更**：新增代理内角色体系（AGENCY_ADMIN/ACCOUNTANT/REVIEWER/ASSISTANT）+ 客户分配机制（t_agency_user + t_agency_user_enterprise）
> **关联文档**：[技术方案](../技术方案.md)、[项目说明](../项目说明.md)

---

## 1. 概述

### 1.1 目标

一套系统同时支持两种使用模式：

| 模式 | 适用场景 | 示例 |
|------|---------|------|
| **SME（自管）** | 中小企业自己管自己的账 | 山东华杉医疗科技直接使用系统 |
| **Agency（代理）** | 代理记账公司管理多个客户 | 慧算账代账公司管理 50 家客户 |

### 1.2 核心原则

1. **同一套代码**，不分叉，不复制
2. **同一套数据库**，单 schema，`enterprise_id` 列隔离
3. **三层防线**：前端路由 → MyBatis 拦截器 → PostgreSQL RLS
4. **用户登录时确定模式**，运行时无感切换

---

## 2. 数据模型

### 2.1 实体关系

```
t_agency (代理公司)
├── id            BIGINT PK
├── agency_code   VARCHAR(32)  UNIQUE  -- 代理公司编码
├── agency_name   VARCHAR(200)         -- 代理公司名称
├── contact_name  VARCHAR(100)
├── contact_phone VARCHAR(32)
├── status        VARCHAR(20)          -- PENDING / ACTIVE / SUSPENDED / TERMINATED（见 SPEC §3.1 企业状态机）
├── created_at, updated_at, deleted

t_enterprise (实际公司 = 账套)
├── id              BIGINT PK
├── enterprise_code VARCHAR(32)  UNIQUE  -- 公司编码
├── enterprise_name VARCHAR(200)        -- 公司名称
├── tax_id          VARCHAR(32)         -- 纳税人识别号
├── mode            VARCHAR(20)         -- 'SME' | 'AGENCY_CLIENT'
├── agency_id       BIGINT  → t_agency -- 所属代理公司（SME 模式为 null）
├── status          VARCHAR(20)         -- PENDING / ACTIVE / SUSPENDED / TERMINATED（见 SPEC §3.1 企业状态机）
├── seed_data_done  BOOLEAN             -- 种子数据是否已初始化
├── created_at, updated_at, deleted

t_user (用户)
├── id              BIGINT PK
├── username        VARCHAR(50)  UNIQUE
├── password        VARCHAR(255)
├── real_name       VARCHAR(100)
├── user_type       VARCHAR(20)         -- 'SUPER_ADMIN' | 'AGENCY' | 'ENTERPRISE'
├── agency_id       BIGINT  → t_agency -- 代理用户所属代理公司
├── enterprise_id   BIGINT  → t_enterprise -- 自管用户所属公司
├── status          VARCHAR(20)         -- ACTIVE / INACTIVE / LOCKED（用户登录态，非企业状态机）
├── last_login_ip, last_login_at
├── created_at, updated_at, deleted

t_agency_enterprise (代理→客户绑定)
├── id              BIGINT PK
├── agency_id       BIGINT  → t_agency
├── enterprise_id   BIGINT  → t_enterprise
├── status          VARCHAR(20)         -- ACTIVE / INACTIVE（绑定关系状态，非企业状态机）
├── created_at

t_role, t_user_role (共享)
├── 不变，所有用户共用角色定义
├── 角色与 enterprise 无关，通过菜单权限控制可见范围

t_agency_user (代理公司内部用户) — V2.0 新增
├── id              BIGINT PK
├── agency_id       BIGINT  → t_agency
├── user_id         BIGINT  → t_user (UNIQUE)
├── agency_role     VARCHAR(20)  -- AGENCY_ADMIN / ACCOUNTANT / REVIEWER / ASSISTANT
├── status          VARCHAR(20)  -- ACTIVE / SUSPENDED / TERMINATED
├── created_by, created_at, updated_by, updated_at, deleted, version

t_agency_user_enterprise (会计-客户分配) — V2.0 新增
├── id              BIGINT PK
├── agency_user_id  BIGINT  → t_agency_user
├── enterprise_id   BIGINT  → t_enterprise
├── assigned_by     BIGINT  -- 分配人 user_id
├── assigned_at     TIMESTAMP
├── unassigned_by   BIGINT
├── unassigned_at   TIMESTAMP
├── deleted         INTEGER DEFAULT 0
├── UNIQUE (agency_user_id, enterprise_id)
```

> **状态字段语义对齐说明**（2026-07-24 更新）：
> - **t_agency / t_enterprise**：遵循企业状态机 4 态（PENDING/ACTIVE/SUSPENDED/TERMINATED）
> - **t_user**：用户登录态（ACTIVE/INACTIVE/LOCKED）
> - **t_agency_enterprise**：绑定关系状态（ACTIVE/INACTIVE）
> - **t_agency_user**：代理用户状态（ACTIVE/SUSPENDED/TERMINATED）
> - **t_agency_user_enterprise**：分配状态（通过 deleted 软删除标记 ASSIGNED/UNASSIGNED）

### 2.2 用户类型说明（V2.0 扩展）

| userType | agencyRole | agency_id | enterprise_id | 可操作的公司 |
|----------|-----------|-----------|--------------|-------------|
| `SUPER_ADMIN` | — | null | null | 所有（系统管理） |
| `AGENCY` | `AGENCY_ADMIN` | 1 | null | 代理公司下全部客户 + 管理会计 |
| `AGENCY` | `ACCOUNTANT` | 1 | null | 仅分配给自己的客户 |
| `AGENCY` | `REVIEWER` | 1 | null | 代理公司下全部客户（只读审核） |
| `AGENCY` | `ASSISTANT` | 1 | null | 仅分配给自己的客户（仅录入） |
| `ENTERPRISE` | — | null | 1 | 仅本公司 |

### 2.3 业务数据表

所有 69 张业务表加 `enterprise_id` 列：

```sql
ALTER TABLE t_voucher ADD COLUMN enterprise_id BIGINT NOT NULL REFERENCES t_enterprise(id);
ALTER TABLE t_subject ADD COLUMN enterprise_id BIGINT NOT NULL REFERENCES t_enterprise(id);
ALTER TABLE t_bank_statement ADD COLUMN enterprise_id BIGINT NOT NULL REFERENCES t_enterprise(id);
-- ... 其余 66 张表同理
```

**种子数据表**（科目、凭证类型、摘要库等）每个公司独立一份：

```sql
-- 创建新客户公司时，克隆种子数据
INSERT INTO t_subject (enterprise_id, code, name, ...)
SELECT #{newEnterpriseId}, code, name, ... FROM t_subject_template;
```

---

## 3. 三层防线

### 3.1 第一层：前端路由 + 菜单权限

```
AGENCY 用户登录
  → 看到「客户列表」页面（替换原来的首页）
  → 选择客户 → 进入该客户的业务空间
  → 业务空间内的菜单按角色权限显示

ENTERPRISE 用户登录
  → 直接进入自己公司的业务空间
  → 与现在的行为一致
```

### 3.2 第二层：MyBatis DataPermissionInterceptor

```java
@Component
public class EnterpriseDataPermissionInterceptor implements InnerInterceptor {
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        // 1. 从 SecurityContext 获取当前 enterprise_id
        Long enterpriseId = SecurityUtils.getCurrentEnterpriseId();
        if (enterpriseId == null) return; // 超级管理员不拦截

        // 2. 获取表名，判断是否业务表
        String tableName = extractTableName(boundSql.getSql());
        if (!isBusinessTable(tableName)) return;

        // 3. 自动注入 AND enterprise_id = ?
        String newSql = boundSql.getSql().replace("WHERE", "WHERE enterprise_id = " + enterpriseId + " AND");
        // 用反射替换 boundSql.sql
    }
}
```

**边界情况：**

| 场景 | 处理 |
|------|------|
| INSERT 语句 | 拦截器自动填充 `enterprise_id` 字段 |
| UPDATE 语句 | 自动加 `AND enterprise_id = ?` |
| DELETE 语句 | 同上 |
| 原生 SQL / 自定义 Mapper | 拦截器同样生效（基于 BoundSql） |
| 超级管理员 | 跳过拦截，可查看所有 |

### 3.3 第三层：PostgreSQL RLS

```sql
-- 1. 设置会话参数
-- 应用启动时执行
-- 用户登录/切换公司时更新

-- 2. 对所有业务表启用 RLS
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 't_%' 
               AND tablename NOT IN ('t_user','t_role','t_user_role','t_menu','t_agency','t_enterprise','t_agency_enterprise','t_sys_config')
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format(
            'CREATE POLICY enterprise_policy ON %I USING (enterprise_id = current_setting(''app.enterprise_id'')::bigint)',
            tbl
        );
    END LOOP;
END;
$$;
```

```java
// 用户登录/切换公司时同步设置 RLS 上下文
@PostMapping("/switch-enterprise")
public R<Void> switchEnterprise(@RequestParam Long enterpriseId) {
    // 1. 校验权限（代理用户是否有权访问该公司）
    validateAgencyAccess(enterpriseId);
    
    // 2. 设置 RLS 上下文
    jdbcTemplate.execute("SELECT set_config('app.enterprise_id', '" + enterpriseId + "', false)");
    
    // 3. 存入当前会话
    SecurityUtils.setCurrentEnterpriseId(enterpriseId);
    return R.ok();
}
```

**RLS 不适用于共享表：** 用户、角色、菜单、代理公司、公司映射等表不加 RLS，这些表不受企业隔离限制。

---

## 4. 用户登录与切换流程

### 4.1 SME 用户（自管）

```
登录 → 校验用户名密码 → user.enterprise_id = 1
  → 设置 RLS context: app.enterprise_id = 1
  → 跳转 dashboard
  → 后续所有操作自动受 enterprise_id=1 限制
```

### 4.2 AGENCY 用户（代理）

```
登录 → 校验用户名密码 → user.agency_id = 1
  → 跳转客户列表页（显示所有绑定的客户公司）
  → 选择"山东恺拓蔚兰"
  → 校验绑定关系
  → 设置 RLS context: app.enterprise_id = 2
  → 跳转该客户的 dashboard
  → 后续所有操作自动受 enterprise_id=2 限制
  → 可随时切换其他客户
```

### 4.3 切换客户（仅代理用户）

```
POST /api/v1/enterprise/switch
Request: { enterpriseId: 3 }
校验: t_agency_enterprise 中存在当前 agent_id + enterprise_id=3
Backend:
  1. jdbcTemplate.execute("SELECT set_config('app.enterprise_id', '3', false)")
  2. SecurityUtils.setCurrentEnterpriseId(3)
  3. 返回新 enterprise 的 dashboard 数据
```

---

## 5. 种子数据隔离

### 5.1 初始化流程

新客户公司创建时，需要初始化一套独立的种子数据：

```sql
-- 1. 插入 t_enterprise 记录
-- 2. 克隆种子数据
INSERT INTO t_subject (enterprise_id, code, name, parent_code, type, ...)
SELECT #{newEnterpriseId}, code, name, parent_code, type, ... FROM t_subject WHERE enterprise_id = 0;  -- 0 = 模板

INSERT INTO t_voucher_type (enterprise_id, code, name, ...)
SELECT #{newEnterpriseId}, code, name, ... FROM t_voucher_type WHERE enterprise_id = 0;

INSERT INTO t_summary_lib (enterprise_id, summary_code, summary_text, ...)
SELECT #{newEnterpriseId}, summary_code, summary_text, ... FROM t_summary_lib WHERE enterprise_id = 0;

INSERT INTO t_period (enterprise_id, year, month, ...)
SELECT #{newEnterpriseId}, year, month, ... FROM t_period WHERE enterprise_id = 0;
```

### 5.2 模板表设计

种子数据模板放在 `enterprise_id = 0` 的记录中（0 表示模板，不归属任何公司）：

```sql
-- 科目模板
INSERT INTO t_subject (enterprise_id, code, name, ...) VALUES (0, '1001', '库存现金', ...);
INSERT INTO t_subject (enterprise_id, code, name, ...) VALUES (0, '1002', '银行存款', ...);

-- 凭证类型模板
INSERT INTO t_voucher_type (enterprise_id, code, name, ...) VALUES (0, 'SK', '收款凭证', ...);
INSERT INTO t_voucher_type (enterprise_id, code, name, ...) VALUES (0, 'FK', '付款凭证', ...);
```

---

## 6. 前端适配

### 6.1 登录后路由

```
SUPER_ADMIN → /admin/dashboard
AGENCY      → /agency/enterprise-list  （客户公司列表）
ENTERPRISE  → /dashboard               （直接进业务空间）
```

### 6.2 代理用户顶部导航栏

```
┌─────────────────────────────────────────────────────┐
│ 慧财财务  [当前客户：山东恺拓蔚兰 ▼]  系统管理员 ▼  │
│          ┌─────────────────────┐                     │
│          │ 山东华杉医疗科技     │                     │
│          │ 山东恺拓蔚兰  ✓     │                     │
│          │ 济南华信科技         │                     │
│          └─────────────────────┘                     │
│  首页 财务核心 业务单据 税务发票 固定资产 报表中心    │
└─────────────────────────────────────────────────────┘
```

### 6.3 API 调用

前端在请求头中携带当前 enterprise_id：

```typescript
// request.ts 拦截器
request.interceptors.request.use((config) => {
  const enterpriseId = localStorage.getItem('current_enterprise_id')
  if (enterpriseId) {
    config.headers['X-Enterprise-Id'] = enterpriseId
  }
  return config
})
```

---

## 7. 迁移计划

### 7.1 阶段一：建表 + 用户改造

| 任务 | 内容 |
|------|------|
| 1.1 | 新建 `t_agency`、`t_enterprise`、`t_agency_enterprise` 表 |
| 1.2 | `t_user` 加 `user_type`、`agency_id`、`enterprise_id` 列 |
| 1.3 | 插入种子数据到 `t_enterprise`（当前系统作为第一个 SME 公司） |
| 1.4 | 迁移现有用户数据（默认为 ENTERPRISE 类型） |

### 7.2 阶段二：业务表加 enterprise_id

| 任务 | 内容 |
|------|------|
| 2.1 | 69 张业务表加 `enterprise_id` 列 + NOT NULL + 索引 |
| 2.2 | 填充现有数据（当前系统默认为 enterprise_id=1） |
| 2.3 | 种子数据表（科目、凭证类型等）克隆到每个企业 |

### 7.3 阶段三：拦截器 + RLS

| 任务 | 内容 |
|------|------|
| 3.1 | 实现 `EnterpriseDataPermissionInterceptor` |
| 3.2 | 所有业务表启用 RLS + 创建策略 |
| 3.3 | 登录/切换接口同步设置 RLS context |

### 7.4 阶段四：前端适配

| 任务 | 内容 |
|------|------|
| 4.1 | 代理用户登录后显示客户列表 |
| 4.2 | 客户切换组件（顶部导航栏） |
| 4.3 | 请求头自动携带 enterprise_id |

---

## 8. 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 拦截器漏写导致数据泄露 | 高 | RLS 兜底 |
| 原生 SQL 绕过拦截器 | 中 | RLS 兜底 |
| 批量导入/导出跨公司 | 中 | 所有导入导出接口显式校验 enterprise_id |
| 跨公司报表查询 | 低 | 超级管理员权限可查看所有 |
| 性能下降（enterprise_id 索引） | 低 | 加索引，RLS 无额外开销 |
| 切换公司时 RLS context 未同步 | 高 | 在统一拦截器中设置，不依赖业务代码 |