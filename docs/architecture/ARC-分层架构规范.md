# 强制分层架构规范

> **编号**：HUICAI-ARC-004
> **版本**：V1.1 | **修改日期**：2026-07-23 | **修改人**：Hermes | **修改内容**：补充 base/sme/agency 三层包结构

## 包结构规范（P54 重构后）

```
com.huicai/
├── base/              ← 底座引擎（SME + Agency 共享）
│   ├── ai/            # AI 任务编排
│   ├── balance/       # 科目余额
│   ├── business/      # 业务单据 Mapper（跨模块共享）
│   ├── masterdata/    # 基础数据（客户/供应商/员工）
│   ├── report/        # 报表引擎
│   ├── storage/       # MinIO 附件存储
│   ├── system/        # 系统管理（用户/角色/权限/拦截器）
│   └── voucher/       # 凭证引擎
├── sme/               ← 中小企业分支
│   ├── arap/          # 应收应付/核销/费用报销/坏账
│   ├── asset/         # 固定资产
│   ├── budget/        # 预算管理
│   ├── cash/          # 资金管理/银行流水/对账
│   ├── salary/        # 工资薪酬
│   └── tax/           # 进销项发票/税务
├── agency/            ← 代账公司分支（P54 预留，S-26 实现）
│   ├── tenant/        # 代理公司 + 企业管理
│   ├── batch/         # 批量操作引擎
│   ├── client/        # 客户 CRM
│   └── interceptor/   # 企业级数据权限拦截器
├── common/            ← 公共组件（异常/响应/AOP/工具）
└── config/            ← 全局配置（Security/MyBatis/Redis/Swagger）
```

### 依赖方向规则

- `agency` 可依赖 `base`，不可依赖 `sme`
- `sme` 可依赖 `base`，不可依赖 `agency`
- `base` 不依赖 `sme` 或 `agency`
- `common` 和 `config` 被所有层共享
- 横切关注点（拦截器、AOP）放在 `common` 或 `config` 中，不放在业务包下

### Layer 0：纯数据对象（Entity/DTO/VO）
✅ 可以：
- 定义字段
- 简单的验证（Pydantic/Validation）
- 静态工厂方法

❌ 禁止：
- 导入任何业务逻辑
- 调用外部服务
- 包含复杂计算
- 注入任何依赖

---

### Layer 1：Repository/DAO（数据访问层）
✅ 可以：
- 数据库 CRUD 操作
- 简单的查询组装
- 事务控制

❌ 禁止：
- 包含业务逻辑
- 调用外部服务
- 导入 Layer 2/3

---

### Layer 2：Service（业务逻辑层）
✅ 可以：
- 核心业务逻辑
- 调用 Layer 0 和 Layer 1
- 事务控制

❌ 禁止：
- 直接导入 Layer 3（Controller）
- 暴露 HTTP 相关对象（Request/Response）
- 前端业务逻辑

---

### Layer 3：Controller（接口层）
✅ 可以：
- API 路由定义
- 请求参数转换
- 响应格式转换
- 调用 Layer 2

❌ 禁止：
- 包含业务逻辑
- 直接操作数据库
- 导入 Layer 1 以外的 Repository

---

## 依赖关系检查规则

### 自动检查（CI/CD 必须执行）
```bash
# 检查 Layer 0 是否导入了其他层
grep -r "from .*service" backend/src/main/java/com/huicai/entity

# 检查 Layer 1 是否导入了 Layer 2/3
grep -r "from .*controller" backend/src/main/java/com/huicai/repository

# 检查 Layer 3 是否直接操作数据库
grep -r "from .*repository" backend/src/main/java/com/huicai/controller | grep -v "service"
```

### 违反规则后果
1. OpenCode 的代码被 Hermes 打回重做
2. CI/CD 流水线失败
3. 坑点记录到 `harness/memory/pitfalls.md`

---

## 目录结构映射

```
backend/src/main/java/com/huicai/
├── base/
│   ├── system/entity/         (Layer 0)
│   ├── system/dto/            (Layer 0)
│   ├── system/vo/             (Layer 0)
│   ├── system/mapper/         (Layer 1)
│   ├── system/service/        (Layer 2)
│   ├── system/controller/    (Layer 3)
│   ├── voucher/...            (同上分层)
│   ├── report/...
│   └── ai/...
├── sme/
│   ├── arap/...               (同上分层)
│   ├── tax/...
│   ├── asset/...
│   ├── budget/...
│   ├── cash/...
│   └── salary/...
├── agency/                    (P54 预留，S-26 实现)
│   ├── tenant/...
│   ├── batch/...
│   └── client/...
├── common/                    (公共组件 — 跨层共享)
└── config/                    (全局配置 — 跨层共享)

ai-service/app/
├── models/            (Layer 0)
├── api/               (Layer 3)
├── core/              (Layer 2)
└── workers/           (Layer 2)

frontend/src/
├── api/               (Layer 1)
├── composables/       (Layer 2)
└── views/             (Layer 3)
```
