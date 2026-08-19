# SPEC: Phase 0 — 项目骨架与基础设施

> **编号**：HUICAI-SPC-000
> **test_ref**：待补（无对应测试类，AI Agent/架构/迁移类 SPEC 待建测试）
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部
- **任务 ID**: P0-SKELETON
- **类型**: infra
- **优先级**: high
- **依赖**: 无
- **执行工具**: OpenCode

> **关联需求**: REQ-2026-001, REQ-2026-003, REQ-2026-004, REQ-2026-005, REQ-2026-006

## 1. 输入契约
→ 见本文 具体要求（1 后端、2 前端、3 Docker Compose、4 文档的详细技术约束）

## 2. 输出契约
→ 见本文 验收标准（6 条验收条件：编译/构建/Docker/扩展/Health/登录页）

## 3. 状态流转
→ 见本文 目录结构（backend → frontend → docker-compose 的全项目文件组织）

## 4. 异常处理
→ 见本文 技术约束（PostgreSQL 16 + pgvector、Maven、JDK 17 等强制技术栈约束）

## 背景
慧财财务为新项目，需要从零搭建 Spring Boot + Vue 3 项目骨架，包含基础的开发环境和部署配置。

## 目标
创建可启动的 Spring Boot 3.x 后端 + Vue 3 前端 + Docker Compose 基础设施。

## 技术约束
- 后端：Spring Boot 3.x + Maven + JDK 17
- 数据库：PostgreSQL 16 + pgvector + pg_trgm
- 前端：Vue 3 + Element Plus + ECharts + vue-router + pinia + axios
- 中间件：Redis 7、MinIO
- ORM：MyBatis-Plus
- 统一响应体结构

## 目录结构

```
/data/disk/disk/huicai/
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/huicai/
│   │   ├── HuicaiApplication.java
│   │   ├── common/
│   │   │   ├── response/           # 统一响应 R<T>
│   │   │   ├── exception/          # 全局异常处理
│   │   │   └── constant/           # 常量
│   │   ├── config/
│   │   │   ├── MyBatisPlusConfig.java
│   │   │   ├── SwaggerConfig.java  # Knife4j
│   │   │   └── RedisConfig.java
│   │   ├── module/
│   │   │   └── system/
│   │   │       ├── controller/
│   │   │       └── model/
│   │   └── ...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml     # 开发环境配置
│   │   └── db/
│   │       └── init.sql            # 建库建表 + 扩展
│   └── src/test/java/
│       └── com/huicai/
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── src/
│   │   ├── main.js
│   │   ├── App.vue
│   │   ├── router/
│   │   │   └── index.js
│   │   ├── store/
│   │   ├── api/
│   │   │   └── request.js          # axios 封装
│   │   ├── views/
│   │   │   ├── login/
│   │   │   └── dashboard/
│   │   └── components/
│   └── env.d.ts
├── docker-compose.yml
├── docker/
│   ├── postgres/
│   │   └── init.sql
│   ├── redis/
│   └── minio/
├── .gitignore
└── README.md
```

## 具体要求

### 1. 后端 Spring Boot
- pom.xml 包含：spring-boot-starter-web, mybatis-plus-spring-boot3, postgresql, spring-boot-starter-data-redis, knife4j, lombok, hutool, validation
- 统一响应体 R<T>(code, msg, data)
- 全局异常处理 @RestControllerAdvice
- Knife4j 配置，访问 /doc.html 可看 API
- Health 端点 GET /api/v1/health 返回 {"code":200,"msg":"ok"}
- application-dev.yml 配置 PostgreSQL (localhost:5432/huicai) + Redis (localhost:6379)
- 使用 Maven Wrapper

### 2. 前端 Vue 3
- Vue 3 + Vite 5
- Element Plus (完整引入)
- vue-router 4 (history 模式)
- pinia 状态管理
- axios 封装：baseURL=/api/v1，请求/响应拦截器
- 登录页面（空壳，仅表单 UI）
- Dashboard 页面（空壳，仅标题）
- 路由配置：/login -> Login, / -> Dashboard (需登录守卫)

### 3. Docker Compose
- PostgreSQL 16：端口 5432，用户 huicai/密码 huicai123，数据库 huicai
  - 挂载初始化 SQL（建 pgvector+pg_trgm 扩展）
- Redis 7：端口 6379
- MinIO：端口 9000 (API) + 9001 (Console)，用户 huicai/密码 huicai123
- 所有服务通过网络 huicai-net 互通
- volumes 放在 docker/volumes/

### 4. 文档
- docs/development/ 目录
- docs/architecture/ 目录
- README.md 基本说明

## OpenCode 执行指令

**目标**：创建慧财财务 Spring Boot + Vue 3 项目骨架

**约束**：
- 后端必须是 Spring Boot 3.x + Maven + JDK 17
- 前端必须是 Vue 3 + Vite 5 + Element Plus
- Docker Compose 一鍵启动所有中间件
- pom.xml 用的所有依赖版本必须是主流兼容版本
- MySQL 不可用，必须 PostgreSQL 16

**上下文**：
- 项目路径：/data/disk/disk/huicai/
- 规格文档：docs/开发计划/慧财财务总体开发计划_2026-06-15.md

**验收标准**：
1. ✅ `cd backend && ./mvnw clean compile -DskipTests` 编译通过
2. ✅ `cd frontend && npm install && npm run build` 构建通过
3. ✅ `docker compose up -d` 拉起所有服务
4. ✅ pgvector + pg_trgm 扩展已安装
5. ✅ 访问 localhost:8080/api/v1/health 返回 200
6. ✅ 访问 localhost:5173 显示登录页

**注意**：
- 本项目已经有 .gitignore、README.md、docs/ 目录，不要覆盖它们
- docker-compose.yml 放在项目根目录
- 后端不产生任何业务逻辑代码，只是骨架

---

## 五、BDD 验收标准

### 场景 1：后端编译通过
**Given** Spring Boot 3.x + Maven + JDK 17 环境就绪  
**When** 运行 `cd backend && ./mvnw clean compile -DskipTests`  
**Then** BUILD SUCCESS

### 场景 2：前端构建通过
**Given** Node.js 环境就绪  
**When** 运行 `cd frontend && npm install && npm run build`  
**Then** 构建成功，无报错

### 场景 3：Docker 基础设施就绪
**Given** Docker Compose 配置完成  
**When** 运行 `docker compose up -d`  
**Then** PostgreSQL 16、Redis 7、MinIO 全部启动  
**And** pgvector + pg_trgm 扩展已安装

---

## 六、YAML 契约

```yaml
---
# === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
states:
  - DRAFT
  - COMPILED
  - BUILT
  - DEPLOYED
transitions:
  - from: DRAFT
    to: COMPILED
    trigger: mvn_compile
  - from: COMPILED
    to: BUILT
    trigger: npm_build
  - from: BUILT
    to: DEPLOYED
    trigger: docker_up
acceptance_tests:
  - id: AT-001
    description: "mvn compile 通过"
    assertion: "BUILD SUCCESS"
  - id: AT-002
    description: "npm run build 通过"
    assertion: "构建成功无报错"
  - id: AT-003
    description: "docker compose 拉起所有服务"
    assertion: "PG/Redis/MinIO 全部正常运行"
```