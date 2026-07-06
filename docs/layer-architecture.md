# 强制分层架构规范

## 分层定义

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
├── entity/           (Layer 0)
├── dto/              (Layer 0)
├── vo/               (Layer 0)
├── mapper/           (Layer 1)
├── repository/       (Layer 1)
├── service/          (Layer 2)
└── controller/       (Layer 3)

ai-service/app/
├── model/            (Layer 0)
├── repository/       (Layer 1)
├── service/          (Layer 2)
└── api/              (Layer 3)

frontend/src/
├── models/           (Layer 0)
├── composables/      (Layer 2)
└── views/            (Layer 3)
```
