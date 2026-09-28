# P99 SPEC — 开发环境可运行性修复与构建门禁治理（缺陷修复批次）

> **作者**：opencode
> **状态**：🟡 **待老丁审核**（未审核通过前不得进入 Plan/BUILD）
> **版本**：V1.0 | **最后修改**：2026-09-28
> **编号**：HUICAI-SPC-099 | 优先级：**P0**（批次内 REQ-103/104 为 P0，其余 P1）
> **依据**：`main @ 33f36422` 全量环境搭建与实测（本机 WSL2 + Docker 29.8.1 + JDK21 + Node20.19.2 + Maven 3.8.7）
> **关联需求**：REQ-2026-103 ~ REQ-2026-110（8 条，已登记 REQUIREMENTS_REGISTRY.md）
> **上位文档**：[P97 报表专业进阶](./P97-report-p2-professional.md)、[P98 期初连续性](./P98-period-opening-continuity.md)
> **性质声明**：本 SPEC **不含任何新功能**，8 条全部为**缺陷修复**。修复过程中不得新增业务能力、不得改动业务口径。
> **test_ref（规划）**：见各 REQ 的 BDD 场景映射表

---

## 0. 背景与现状差距（全部已实测复现，证据含 file:line 与实测输出）

本批次 8 项均在本机从零搭建环境、跑通全链路的过程中**实际触发**，非静态审查推测。

### 0.1 REQ-103 前端构建门禁失败（`npm run build` 无产物）

- `frontend/package.json` 的 build 链为 `validate-api-paths.mjs && vue-tsc --noEmit && vite build` —— **vue-tsc 是硬门禁**
- `src/views/system/period/PeriodList.vue:77` 为 `import { ElMessage } from 'element-plus'`，但 `:176` 的 `handleUnClose()` 调用 `ElMessageBox.confirm(...)`
- 实测：`npm run build` → exit 2，`TS2552: Cannot find name 'ElMessageBox'`，`dist` **NOT PRODUCED**
- 运行时后果：反结账功能会抛 `ElMessageBox is not defined`
- 参照：`src/views/finance/voucher/VoucherList.vue:376` 用动态 import `(await import('element-plus')).ElMessageBox.prompt(...)`，故不报错 —— 仓库内已存在两种写法，不一致

### 0.2 REQ-103 附带：`pom.xml` 无效编译配置

- `backend/pom.xml:219-220` 显式 `<source>17</source><target>17</target>`，而 `:21-24` 属性为 `maven.compiler.source/target/release = 21`
- 实测编译日志为 `javac [debug parameters release 21]`，`maven-compiler-plugin:3.13.0` → `release` 参数优先级更高，`:219-220` **不生效**
- 风险：一旦有人删除 `maven.compiler.release` 属性，将**静默退回 Java 17**，正是 AGENTS.md §4.4-19 记录的事故场景

### 0.3 REQ-104 种子账号密码不可用（新迁移库无法登录）

- `V1__baseline.sql:1517` 写入 admin 哈希 `$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq`
- `V114__seed_agency_users.sql` 注释**三处**声明「密码: admin123」，并用同一哈希建了 `accountant01` / `reviewer01` / `assistant01`
- 实测三重证据：
  1. `POST /api/v1/auth/login` 用 `admin123` → `400 用户名或密码错误`
  2. `python3-bcrypt` 独立校验该哈希与 `admin123` **不匹配**（另试 `password`/`123456` 等 20+ 候选全败；已用 `hashpw`+`checkpw` 往返自检 = True 证明方法无误）
  3. 后端日志为 `BadCredentialsException` ×N、`UsernameNotFoundException` **0** 次 —— 用户已查到，仅密码比对失败
- 波及：4 个账号全部不可用；e2e 48+ 用例的 `helpers.ts` 硬编码 `admin/admin123` 全部受阻
- 定性：**迁移数据缺陷**，注释与哈希不符（哈希疑为从外部示例误抄）

### 0.4 REQ-105 ai-service 镜像无法构建

- `ai-service/requirements.txt:3` 钉死 `pydantic==2.6.1`；`:17` 要求 `langchain>=0.3.0`
- langchain **0.3.0 ~ 0.3.17 全部版本**均要求 `pydantic<3.0.0,>=2.7.4`
- 实测：`docker compose build ai-service` → 第 5/6 步 `ERROR: ResolutionImpossible`，耗时约 690s
- 定性：**确定性冲突**，与网络/镜像无关，100% 可复现
- 兼容性核对：fastapi 0.110.0 要求 `pydantic>=1.7.4,<3.0.0` ✓；pydantic-settings 2.2.1 要求 `>=2.3.0` ✓ —— 放宽下限无连带风险

### 0.5 REQ-106 `docker compose up` 整体启动失败（端口冲突）

- `docker-compose.yml:88`（`ai-service` → `8000:8000`）与 `:115`（`backend` → `8000:8000`）**映射同一宿主端口**
- `backend/src/main/resources/application.yml:2` `server.port: 8000`；`ai-service/Dockerfile:16-19` `EXPOSE 8000` + `uvicorn --port 8000`
- 附带隐患：`application.yml:103` `ai.service-url: http://localhost:8000` —— **指向后端自身端口**（自引用地雷）
- 影响面：全量 `docker compose up -d` 必然失败；本地开发需以「只起 4 个中间件」绕过
- 利好：`com.huicai.base.ai` 包内**无任何 HTTP 客户端**（已 grep `RestTemplate|WebClient|HttpClient|OkHttp|FeignClient` 确认），`ai-service.enabled: true` 但 `ocr-enabled: false`、`embedding-enabled: false`；**当前无实际调用方**

### 0.6 REQ-107 4 张租户表未启用 RLS

- `V106__enable_rls_policies.sql` 的 DO 块在执行时枚举当时的 `t_%` 表；以下 4 张由**更晚版本**创建，未纳入
- 缺失清单：`t_contract`(V107)、`t_agency_user_enterprise`(V112)、`t_service_progress`(V148)、`t_close_log`(V151) —— **4 张均含 `enterprise_id`**
- 实测：83 张表 / 66 张 `relrowsecurity=true` / 66 张 `relforcerowsecurity=true` / `pg_policies` 66 条
- 已排除误报：V106 的 `FORCE ROW LEVEL SECURITY` 独立成行未被注释吞掉（`cat -A` 确认），应用用户 `huicai` 虽为表 owner 但因 FORCE 不被绕过
- 影响：铁律 #6 与技术方案 §4.8 所述「MyBatis 拦截器 + PG RLS」第三层防线，对这 4 张表缺失

### 0.7 REQ-108 慢测全量运行失败（共享容器基建缺陷）

- `mvn test -DexcludedGroups=`（含 `slow` 组）全量跑 → 大量 `CannotCreateTransactionException: Could not open JDBC Connection`，累计 **131 次** `ConnectException`，逐类累积 85s→113s→140s→168s
- 报错形态为 `HikariPool-N - Connection is not available, request timed out after 30000ms`（连接池超时，**非断言不符**）
- **但逐类单跑全部通过**：

  | 测试类 | 结果 | 耗时 |
  |---|---|---|
  | `IncomeStatementCaliberRealDBTest` | 4/4 PASS | 15.41s |
  | `AuxiliaryDetailRealDBTest` | 5/5 PASS | 20s |
  | `OpeningContinuityRealDBTest` | 6/6 PASS | 23s |

- 已排除误报：容器发布端口（含 56923 临时端口）从 WSL 与 Windows 两侧实测均 OPEN —— **`networkingMode=mirrored` 无责**
- 根因假设：56 个类 `extends AbstractMapperTest` 共用静态容器，叠加 `pom.xml:258-259` `forkCount=1 + reuseForks=true`（单 JVM 顺序执行），容器生命周期与 Spring 上下文缓存不同步，前类停容器后缓存上下文仍指向旧端口

### 0.8 REQ-109 前端单测 5 项失败

- `vitest run` → `Test Files 1 failed | 25 passed (26)`，`Tests 5 failed | 260 passed (265)`
- 全部集中于 `src/__tests__/VoucherList.test.ts`（19 项中 5 项失败），失败行号 `:102`、`:114`、`:130`
- **根因未定位** —— 需在 Plan 阶段先做失败剖析，本 SPEC 只定义验收标准不预设修法

### 0.9 REQ-110 硬编码 NVIDIA API Key（安全问题）

- `backend/src/main/resources/application.yml:107` 明文写入 `nvidia.api-key: nvapi-EIwwEae6qp7L5EE...`（已截断）
- 违反 AGENTS.md §7-3「禁止硬编码敏感信息，必须通过环境变量或配置中心注入」
- 该文件已入 git（`main @ 33f36422`），**凭据已进入版本历史**；仅改文件不足以消除暴露面

---

## 1. 输入契约

| REQ | 输入 | 约束 |
|---|---|---|
| 103a | `PeriodList.vue` 现有 import 结构 | 复用既有 `unplugin-auto-import`（`vite.config.ts:12-14` `ElementPlusResolver`），不新增依赖 |
| 103b | `pom.xml:21-24` 既有属性 | **保留** `maven.compiler.release=21`，只删被覆盖的 `:219-220` |
| 104 | 新 migration 文件 `V155__*.sql` | 只写 `UPDATE`，不建表不改表结构；**不得** `DELETE`/`TRUNCATE` |
| 105 | `requirements.txt` 现有 18 个固定版本 | **只放宽 `pydantic` 一项下限**，其余 17 个 pin 不动（避免连带变更） |
| 106 | `docker-compose.yml` 现有 6 服务 | `ai-service` 宿主端口 `8000`→`8001`；`ai-service` 容器内端口**保持 8000 不变**（`EXPOSE`/uvicorn 不动，最小改动） |
| 107 | `V106` 的策略谓词原文 | **必须逐字复用** `USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint)`，不得另造口径 |
| 108 | `AbstractMapperTest` 现有容器声明 | 优先改 surefire 配置而非重写 56 个子类；单类隔离必须保持 ≤ 60s |
| 109 | `VoucherList.test.ts` 现状 | **禁止**通过放宽断言（如 `toBeTruthy`）掩盖问题 |
| 110 | `application.yml:100-115` 的 `ai` 块 | 新增占位默认值 `${NVIDIA_API_KEY:}`；compose 侧从宿主环境注入 |

---

## 2. 输出契约

| REQ | 输出 | 可验证判据 |
|---|---|---|
| 103a | `PeriodList.vue:77` 改为同时导入 `ElMessage` 与 `ElMessageBox` | `npx vue-tsc --noEmit` **0 error**；`npm run build` **exit 0** 且产出 `dist/` |
| 103b | `pom.xml` 移除 `:219-220` 两行 | 编译日志仍为 `release 21`（**不得**退回 17） |
| 104 | `V155` 落库；`flyway_schema_history` 出现 `155` 且 `success=true` | `admin/admin123` 登录 **200** + 拿到 JWT；错误密码仍 **400** |
| 105 | `ai-service` 镜像构建成功 | `docker compose build ai-service` **exit 0**；容器 `/health` **200** |
| 106 | `ai-service` 宿主端口 8001；`ai.service-url` 指向 8001 | `docker compose up -d`（全量）**6 服务全起**无 bind 冲突 |
| 107 | 4 张表 `relrowsecurity=true` 且 `relforcerowsecurity=true` | `pg_policies` 中 4 张表各有一条 `enterprise_policy`；迁移**幂等**（重跑零变更） |
| 108 | 慢测全量可跑完 | `mvn test -DexcludedGroups=` **BUILD SUCCESS**；逐类单跑结果**不得**变差 |
| 109 | 5 项断言恢复 | `vitest run` → `Test Files 26 passed`、`Tests 265 passed` |
| 110 | `application.yml` 无明文密钥 | `grep -rn 'nvapi-'` 在仓库源码中**零命中** |

---

## 3. 状态流转与副作用

**铁律声明：本批次 8 项全部零业务副作用。**

| REQ | 是否改业务数据 | 是否改业务口径 | 副作用约束 |
|---|---|---|---|
| 103 | ❌ 否 | ❌ 否 | 仅前端类型与构建 |
| 104 | ⚠️ **是**（仅 `t_user.password`，4 行） | ❌ 否 | 属**凭据初始化**非财务数据；迁移只 UPDATE，不触碰任何金额/余额/凭证表 |
| 105 | ❌ 否 | ❌ 否 | 仅 Python 依赖版本 |
| 106 | ❌ 否 | ❌ 否 | 仅端口映射；`ai-service` 当前无调用方，**不得**顺带接入调用 |
| 107 | ❌ 否 | ❌ 否 | 仅 `pg_class` 标志与 policy；**FORCE RLS 后表 owner 行为改变**（应用用户将真正受策略约束），需先确认这 4 张表的现有查询在策略下仍可正常返回 |
| 108 | ❌ 否 | ❌ 否 | 仅测试执行方式 |
| 109 | ❌ 否 | ❌ 否 | 仅测试文件 |
| 110 | ❌ 否 | ❌ 否 | 仅配置读取方式 |

**状态机影响**：无。本批次不含任何状态流转变更，不涉及 `VoucherStatus` / `BusinessDocStatus` / 任何 `*StateMachine*`。

**REQ-107 需老丁额外确认的风险**：V106 有 11 张表是**故意排除**在 RLS 之外的（`t_user`/`t_role`/`t_user_role`/`t_menu`/`t_role_menu`/`t_agency`/`t_enterprise`/`t_agency_enterprise`/`t_sys_config`/`t_audit_log`/`t_dept`）。本 SPEC **不动这 11 张**，只补 4 张漏网的。若这 4 张中有谁本应属于「故意排除」类，需在审核时剔除。

---

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| 103 修完仍报其他 TS 错误 | 视为**新发现缺陷**，不扩大本 REQ 范围；登记后单独排期 |
| 104 迁移后登录仍 400 | 立即 `git revert` 该 migration（纯数据 UPDATE，无结构变更，回滚安全） |
| 105 放宽后出现新冲突 | 回退 pin，恢复 `pydantic==2.6.1`，并将冲突明细登记为新 REQ，**不得**连锁放宽其他依赖 |
| 106 改端口后 backend 启动失败 | 说明存在未发现的硬编码 8000 依赖，**停止实施**并回报 |
| 107 补 RLS 后业务查询报空 | 立即回滚该 migration。**这是本批次唯一可能影响生产读路径的项**，必须真实 DB 验证后再合并 |
| 108 全量跑超时（>40min） | 降级为「按类分组跑」并在报告中登记，不得为提速而关闭 `reuseForks` 之外的任何隔离 |
| 109 根因是组件真 bug | 停止本 REQ，按新缺陷走完整三步闭环 |
| 110 凭据已在 git 历史 | 报告老丁，**建议同步吊销该 key**（代码改动无法消除历史暴露） |

---

## 验收标准（BDD）

### 场景 103-1：前端类型门禁通过
```
Given 仓库处于 main @ 33f36422 之后、frontend 依赖已 npm ci 完成
 When 执行 npx vue-tsc --noEmit
 Then 退出码为 0，stderr 无 "error TS"
 And 执行 npm run build 时 vue-tsc 不再中断，产出 dist/index.html
```
→ `@Test`/脚本断言：`vue-tsc --noEmit` exit code = 0 且 `dist/index.html` 存在

### 场景 103-2：编译版本不倒退
```
Given pom.xml 已移除 <source>17</source><target>17</target>
 When 执行 mvn -q compile
 Then 编译日志含 "release 21"
 And 不得出现 "release 17" 或 "-source 17"
```

### 场景 104-1：正向登录（真实 DB）
```
Given 一个从零迁移到 V155 的全新数据库
 When POST /api/v1/auth/login  body={"username":"admin","password":"admin123"}
 Then HTTP 200，data.token 非空且可解析出 sub=admin
 And data.userType == "SUPER_ADMIN"
```

### 场景 104-2：负向登录不回归
```
Given 同上
 When 以错误密码 admin 登录
 Then HTTP 400，msg == "用户名或密码错误"
 And 后端日志出现 BadCredentialsException 而非 UsernameNotFoundException
```
→ 负向断言强制：证明「用户可查到、仅密码不符」，与 0.3 的诊断口径一致

### 场景 104-3：迁移幂等与落库
```
Given 执行 V155
 When 查询 flyway_schema_history
 Then 存在 version=155 且 success=true
 And 4 个账号（admin/accountant01/reviewer01/assistant01）密码哈希均已更新
 And t_subject / t_voucher / t_business_doc 行数与迁移前一致（未误伤业务表）
```

### 场景 105-1：镜像可构建
```
Given requirements.txt 的 pydantic 下限已放宽
 When 执行 docker compose build ai-service
 Then 退出码 0，产出 huicai-ai-service 镜像
 And 日志不含 "ResolutionImpossible"
```

### 场景 105-2：容器可启动并健康
```
Given 镜像已构建
 When 启动 ai-service 容器并请求 /health
 Then HTTP 200
 And 容器日志无 ImportError / pydantic 版本冲突告警
```

### 场景 106-1：全量 compose 可启动（正负向）
```
Given docker-compose.yml 的 ai-service 宿主端口已改 8001
 When 执行 docker compose up -d
 Then 6 个服务全部进入 running/healthy
 And 无 "port is already allocated" 错误
 And docker compose ps 显示 ai-service 映射 0.0.0.0:8001->8000/tcp
```

### 场景 106-2：不再自引用
```
Given application.yml 的 ai.service-url 已更新
 When 读取该项
 Then 值为 http://localhost:8001
 And 不得等于后端自身端口 8000
```

### 场景 107-1：RLS 标志位（真实 DB）
```
Given 已执行补齐 migration
 When 查询 pg_class
 Then t_contract / t_agency_user_enterprise / t_service_progress / t_close_log
      四者的 relrowsecurity 与 relforcerowsecurity 均为 true
 And 查询 pg_policies 时四者各存在一条 enterprise_policy
```

### 场景 107-2：既有 66 表零变更（负向）
```
Given 补齐 migration 已执行
 When 对比执行前后
 Then 既有 66 张表的 relrowsecurity / relforcerowsecurity 均未变化
 And V106 故意排除的 11 张表仍为 false（本批次不动它们）
```

### 场景 107-3：租户隔离真实生效
```
Given 在 huicai_test 库造 enterprise_id=1 与 =2 各一行 t_contract
 When SET app.enterprise_id = '1' 后查询
 Then 只返回 enterprise_id=1 的行（FORCE RLS 下应用用户同样受限）
```

### 场景 108-1：慢测全量可跑
```
Given surefire 配置已按方案调整
 When 执行 mvn test -DexcludedGroups=
 Then BUILD SUCCESS，Failures 0 / Errors 0
 And 日志中不再出现 "Connection is not available, request timed out"
```

### 场景 108-2：单类隔离不回归（负向）
```
Given 108-1 已通过
 When 单独执行 -Dtest=IncomeStatementCaliberRealDBTest
 Then 仍为 Tests run: 4, Failures: 0, Errors: 0
 And 耗时不超过 60s（不得因隔离方案显著变慢）
```

### 场景 109-1：5 项断言恢复
```
Given 根因已定位并按根因修复
 When 执行 npx vitest run
 Then Test Files 26 passed (26)
 And Tests 265 passed (265)
 And src/__tests__/VoucherList.test.ts 的 5 项全部通过
```

### 场景 109-2：禁止掩盖式修复（负向）
```
Given 修复后的测试
 When 检查 VoucherList.test.ts 的改动
 Then 不得出现断言弱化（toBeTruthy 取代具体值断言、删除 await、注释掉 skip）
```

### 场景 110-1：仓库无明文密钥
```
Given application.yml 已改为环境变量占位
 When 在仓库源码中 grep "nvapi-"
 Then 零命中
 And 核对 git diff 确认 application.yml 已无明文密钥
```
→ 注意：`git log` 历史中仍存在，本场景只保证**工作区与新提交**不含明文

---

## 竞品对标（铁律 #15）

| REQ | 竞品做法 | 我们的取舍 |
|---|---|---|
| 104 | 金蝶/用友的初始化数据库均内置 admin 并在**首次登录强制改密**；开源项目（RuoYi 等）则直接给 `admin/123456` 明文口令并在 README 写明 | 采纳 RuoYi 路线（文档写明 dev 口令），**不**实现强制改密——那是独立需求。**明确记录为已知局限**：本系统无首登改密机制，生产部署前必须另行处理 |
| 105 | 主流 AI 服务（LangChain 官方示例）一律用**区间约束**（`pydantic>=2.7,<3`）而非精确 pin，以容纳上游依赖演进 | 与之一致：只放宽 `pydantic` 为区间，**不**顺手放宽其余 17 个 pin，避免一次性大改掩盖回归 |
| 106 | docker-compose 官方最佳实践为**单服务单端口、不跨服务复用宿主端口** | 同向修正；且我们额外发现 `ai.service-url` 自引用，**比竞品模板多一个坑**，需一并修 |
| 107 | PostgreSQL 多租户主流方案（Supabase/Row Level Security 官方文档）明确要求 `FORCE ROW LEVEL SECURITY`，否则表 owner 绕过策略 | 与之一致：必须 ENABLE + **FORCE** 两条都做，只做 ENABLE 等于没做 |
| 108 | Testcontainers 官方推荐**每个测试类独立容器**（`@Container` 非 static）；代价是耗时 | 优先用 surefire 配置层面解决（`reuseForks`），避免改 56 个子类；但必须用场景 108-2 守住「单类不显著变慢」 |
| 110 | 主流 SaaS 项目密钥一律走环境变量 / Secret 管理，CI 中密钥视为已泄露假设 | 同向；额外建议：**吊销已泄露 key**，这是代码改动覆盖不到的部分 |

---

## 明确不做（防范围蔓延）

- ❌ 不做**首次登录强制改密**（104 的已知局限只登记，不实现）
- ❌ 不做**统一凭据管理 / Secret 管理器接入**（110 只做环境变量占位）
- ❌ 不**顺带接入** ai-service 与后端的调用（106 只改端口，调用方为 0 现状保持 0）
- ❌ 不动 `V106` **故意排除**的 11 张表的 RLS 状态
- ❌ 不顺手改 `pydantic-settings` / `fastapi` / `langchain` 的其他版本约束
- ❌ 不重写 56 个 `AbstractMapperTest` 子类
- ❌ 不修 `e2e/README.md` 的用例数漂移（属 D 组文档项，另行排期）
- ❌ 不修 C1（Flyway 版本缺口 85 个）与 D1/D2（文档数字漂移）

---

## 风险与回滚

| REQ | 风险 | 概率 | 缓解 | 回滚 |
|---|---|---|---|---|
| 103 | 补 import 与 auto-import 插件重复声明 | 低 | 构建后 `dist` 存在即通过；插件对显式 import 优先 | `git revert` 单文件 |
| 104 | 覆盖了真实环境已自定义的口令 | **高** | ⚠️ **仅限 dev/测试库执行**；生产需人工评估 | `git revert` migration（仅 UPDATE） |
| 105 | 放宽后暴露 langchain 新版不兼容 | 中 | 锁定构建成功 + `/health` 200 两道门 | 恢复原 pin 单行 |
| 106 | 存在未发现的 8000 硬编码依赖 | 低 | 已 grep 确认 base/ai 无 HTTP 客户端 | 改回 8000 |
| **107** | **FORCE RLS 后既有查询被拦** | **中** | ⚠️ **最高风险项**：必须真实 DB 跑 `t_contract`/`t_service_progress` 业务查询验证 | `git revert` migration |
| 108 | 隔离化导致全量耗时翻倍 | 中 | 场景 108-2 设 60s 上限 | 还原 surefire 配置 |
| 109 | 根因为组件真 bug，范围外扩 | 中 | 场景 109-1 失败即停 | `git revert` |
| 110 | 换占位后本地开发无 key | 低 | 文档写明本地 export 用法 | 还原配置 |

**执行顺序约束（技术依赖，非优先级）**：
```
103（解前端 build 门禁）→ 105（解镜像构建）→ 106（解全量 compose）
    ↘ 104（解登录）→ 109（依赖登录态跑前端测试）
    ↘ 107 独立但风险最高，建议最后做
    ↘ 108 / 110 可并行
```

---

## 待老丁拍板项

| 编号 | 议题 | 推荐默认 | 理由 |
|---|---|---|---|
| **D1** | REQ-104 是否在**本批次**修，还是等专门的需求做凭据治理 | ⭐ 本批次修 | 否则「项目能跑」这一目标无法达成，e2e 与人工验收全部受阻 |
| **D2** | REQ-104 的 dev 口令统一为 `admin123`？ | ⭐ 4 个账号统一 `admin123` | 与 `V114` 注释与 e2e `helpers.ts` 保持一致，零认知成本 |
| **D3** | REQ-106 ai-service 宿主端口定为 **8001**？ | ⭐ 8001 | 与 backend 8000 相邻易记；`application.yml` 的 AI 段紧随 server 段，8001 无歧义 |
| **D4** | REQ-107 的 4 张表是否全部补 RLS（含 `t_service_progress`）？ | ⭐ 全部 4 张 | 四张都有 `enterprise_id` 且都在 V106 排除名单之外，属漏网而非有意 |
| **D5** | REQ-108 优先改 surefire 还是改 `AbstractMapperTest` 容器声明？ | ⭐ 先 surefire（`reuseForks=false`） | 改动面从 56 个文件降到 1 个；若不达标再降级到容器隔离 |
| **D6** | REQ-110 是否现在吊销已泄露的 NVIDIA key？ | ⭐ 吊销 | 代码改动不能消除 git 历史暴露；该 key 需按已泄露处理 |

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-28 | opencode | 初稿：8 条缺陷修复（REQ-103~110），全部为本机环境搭建与全链路实测所得，含 file:line 与实测输出双重证据；四段模板齐备；BDD 14 场景含负向；竞品对标 6 项；D1-D6 待拍板；**未审核，未进入实现** |
