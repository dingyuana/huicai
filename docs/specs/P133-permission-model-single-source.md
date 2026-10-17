# SPEC-P133：权限模型单一事实源 —— 后端端点强制鉴权 + 前后端共用一份清单

| 项 | 内容 |
|---|---|
| 状态 | 🔴 **方案草案，待老丁审核**（根基建，**不得先实施**） |
| REQ | REQ-2026-149 |
| 前序 | SPEC-P130/P131（幽灵权限码 —— 本项目权限模型缺校验的**直接后果**） |
| 关联 | AGENTS §4.5 第 43 条（豁免理由须逐路径）、第 45 条（用门禁自己的口径）、§1 铁律 #14、§7 风险边界 #2 |

---

## §0 取证（全部实测，非推断）

### 0.1 现状：数据层完整，后端执行层**几乎不存在**

| 层 | 状态 | 证据 |
|---|---|---|
| **权限码数据** | ✅ **完整且单一来源** | `t_menu.permission` → `t_role_menu` → `t_user_role`；`MenuServiceImpl#getUserButtonPermissions` 是唯一解析口 |
| **JWT authorities** | ✅ 装的是**权限码**（不是角色 id） | `UserDetailsServiceImpl:84-87` 逐条 `new SimpleGrantedAuthority(perm)` |
| **前端执行** | ✅ **完整** | 路由守卫按 `meta.permission`；`v-permission` 指令；侧边栏同口径过滤（P121 已对齐） |
| **后端端点鉴权** | 🔴 **11 / 543** | `@PreAuthorize` 全仓仅 11 处，集中在 `SystemClearController`(10) + `PeriodCloseController`(1) |

### 0.2 爆炸半径

```
写端点 POST/PUT/DELETE/PATCH : 307     ← 风险最高（铁律：写路径优先）
读端点 GET                   : 236
受保护                       :  11
未受保护                     : 532
```

`SecurityConfig` 的授权配置只有：

```java
.requestMatchers( /* 9 条匿名白名单 */ ).permitAll()
.anyRequest().authenticated()          // ← 到此为止
```

⇒ **任何已登录用户可直接调用任意端点**。前端守卫是**装饰性的** —— 改个 devtools 参数或直接 curl 即可绕过。

### 0.3 🔴 根因：权限模型**没有单一事实源**

前端路由 `meta.permission` 与后端端点是**两份独立手写的清单**，二者之间无任何校验。这正是 P131 查出 **11 个幽灵权限码**的机制：前端写了一个后端/菜单表里不存在的码，页面静默 403，且症状形似「权限不足」。

**判据沉淀**：任何「A 处声明、B 处执行」的机制，若无机器校验，**必然漂移**，且漂移的症状总是「看起来像正常的权限不足」而极难定位。

### 0.4 为什么不能给 543 个端点逐个加注解

逐个加 `@PreAuthorize` 有三个问题：①**必然漏**（§4.5 第 43 条：逐点加守卫必漏）；②**无法机械校验完整性**（没有清单可查「是否每个写端点都覆盖了」）；③**前后端仍是两份清单**，漂移照旧。

### 0.5 🔴 删除端点取证（2026-10-16 实测，35 个 DELETE 逐点查证）

P133 是基建、要时间，而越权现在就敞着，故先做了一轮**止血**（commit `6c49fbb5`），本节记录取证结论与止血边界。

**关键发现一：35 个 DELETE 端点全部零权限保护**，而 `SecurityConfig` 只到 `authenticated()` ⇒ 任何已登录用户（含出纳、0 权限码账号）可直接调用。

**关键发现二：服务层有兜底，但没有一个是权限兜底。** 逐点查证（`@DeleteMapping` → `service.delete()` → 实现）：

| 端点 | 服务层兜底 | 性质 |
|---|---|---|
| `DELETE /subjects/{id}` | 有子科目则拒删 | **业务**约束，非权限 |
| `DELETE /system/role/{id}` | 角色下有用户则拒删 | **业务**约束，非权限 |
| `DELETE /vouchers/{id}` | 仅 `DRAFT` 可删 | **状态**约束，非权限 |
| `DELETE /system/user/{id}`、`DELETE /system/menu/{id}` | 无 | — |

⇒ **状态/引用/父子约束能挡住「删错对象」，挡不住「无权限者删除」。** 一个出纳可以删掉**别人的**草稿凭证，只要它是 `DRAFT`。

**关键发现三（最有说服力的一条）：已受保护的 11 处，恰好是破坏性最高的** —— 清库（`SystemClearController` 10 处）与反结账。它们被保护**不是因为设计更严谨，而是当初有人想起来加了注解**。⇒ 逐点加注解的必然结果就是这种「记得的地方严了、忘了的地方全裸」的分布，这是 P133 必须清单化的**实证**，而非理论偏好。

**关键发现四：业务兜底的边界不可由代码推断。** `SubjectServiceImpl.delete` 只查子科目、**不查是否已被凭证分录引用**；`t_voucher_entry` 上是否存在外键约束决定了删用中的科目是「报错」还是「静默悬空引用」。**本轮未实测，不下结论** —— 已登记为 P133 取证清单第 1 项（须在真库上验证，不得在 SPEC 里预判）。

**止血范围与边界（须如实声明）**：

- 止血覆盖 8 个端点：科目、角色、菜单、部门、用户、客商 ×3（客户/供应商/员工）。
- **复用页面级权限码**（`subjects:manage` / `system:*:list` / `party:list`），语义是「能进该页 = 能做该页操作」—— **粗粒度**。其中 `system:role/menu/user/dept:list` 仅 ADMIN 持有 ⇒ 立刻形成实质屏障。
- 🔴 **剩余 27 个 DELETE 未覆盖**，P133 批次 3 必须包含。
- 🔴 **止血期间不得宣称「权限管控已建立」**：全站 307 个写端点中仍只有 19 个受保护。
- 顺带修掉两个被实测暴露的返回码缺陷（`@PreAuthorize` 的 `AccessDeniedException` 与无认证上下文的 `AuthenticationCredentialsNotFoundException` 此前均落兜底返回 **500「系统繁忙」**，把权限拒绝伪装成服务端故障；已补 403/401 处理器）。**判据：鉴权类异常必须有自己的处理器，不能靠兜底 —— 兜底会把「拒绝」变成「故障」。**

---

## §1 方案：机器可读权限清单作为唯一事实源

### ① 清单文件（新增）

`backend/src/main/resources/permission-manifest.yml`：

```yaml
version: 1
endpoints:
  - method: POST
    path: /api/base/voucher/v1/vouchers
    permission: voucher:create
    write: true
  - method: PUT
    path: /api/base/voucher/v1/vouchers/{id}
    permission: voucher:update
    write: true
  ...
```

**一份清单，两处消费**：
- **后端**：`PermissionEnforcementInterceptor` 读它并强制拦截
- **守卫**：校验它与前端路由、`t_menu` 三方一致

### ② 后端执行（新增拦截器，不侵入 543 个方法）

```
请求 → JwtAuthenticationFilter（解析 LoginUser，authorities 已是权限码）
     → PermissionEnforcementInterceptor
         ├ 清单命中且持有权限 → 放行
         ├ 清单命中但缺权限   → 403「缺少权限：xxx」
         └ 清单未命中         → 按 §1③ 处置
```

⚠️ **不绕过 RLS、不改动数据权限**（`EnterpriseDataPermissionInterceptor` 与本拦截器职责正交：前者管**数据**，本管**动作**）。

### ③ 清单未命中的端点怎么办（关键决策）

| 策略 | 行为 | 后果 |
|---|---|---|
| **fail-open**（放行） | 未登记 = 不管 | 等于没做，且清单会悄悄腐化 |
| **fail-closed**（拒绝） | 未登记 = 403 | 一旦漏登记就**功能不可用** |
| **warn-only + 守卫**（**建议**） | 未登记 = 放行，但**结构守卫转红并列出清单** | 存量 532 个端点不至于一夜全废，同时缺口**可见** |

**裁定请求**：老丁是否同意先走 **warn-only**，待清单补齐后**再切 fail-closed**？

### ④ 守卫（三条，防漂移）

| 守卫 | 不变量 | 反证手法 |
|---|---|---|
| `G1` 清单权限码须存在于 `t_menu` | 复用 P131 守卫的口径 | 注入假码 |
| `G2` **所有写端点必须在清单中** | 缺口可见、不可增长（棘轮） | 删一条清单项 |
| `G3` 前端路由 `meta.permission` 须在清单中 | 前后端**不得**各写一份 | 改前端 meta |

⚠️ **G2 是本 SPEC 的核心**：它把「是否漏保护」从**人工检查**变成**CI 红**。存量缺口以**棘轮**记录实测值，只许减不许增。

---

## §2 输入 / 输出契约

### 2.1 新增（无破坏性）

| 项 | 内容 |
|---|---|
| `permission-manifest.yml` | 端点 → 权限码清单，版本化 |
| `GET /api/v1/system/permission-manifest`（SUPER_ADMIN） | 导出清单，便于前端/排障对齐 |

### 2.2 行为变更（**有破坏性，须裁定**）

| 场景 | 现状 | 切换到 fail-closed 后 |
|---|---|---|
| 已登录用户调用**已登记**且无权限的端点 | ✅ 200 | 🔴 **403「缺少权限：xxx」** |
| 已登录用户调用**未登记**端点 | ✅ 200 | 🔴 **403** |

> ⚠️ 这是**行为破坏性变更**：任何清单登记错误都会立刻表现为功能不可用。因此**必须先 warn-only 跑通、再切 fail-closed**，两步都要各自跑全量 L1/L2。

---

## §3 状态流转

**不涉及业务状态机**。本 SPEC 只增加一道**动作级授权层**，与既有状态机、RLS、数据权限并列：

```
请求
 ├─ [认证] JwtAuthenticationFilter        → 未登录 401
 ├─ [动作] PermissionEnforcementInterceptor → 无权限 403     ← 本 SPEC 新增
 ├─ [账套] EnterpriseWriteGuard            → 非 ACTIVE 拒绝（P113）
 ├─ [数据] EnterpriseDataPermissionInterceptor → SQL 注入企业条件
 └─ [隔离] RLS（t_bank_account 等 FORCE RLS）→ 行级过滤
```

四层职责**正交、不可互相替代**。特别地：**动作权限不能替代数据隔离** —— 会计「能打开银行对账单页」不代表「能看别家企业的流水」。

---

## §4 异常处理

| 场景 | 处理 |
|---|---|
| 缺权限 | `403 缺少权限：voucher:update`（须点名权限码，便于排障） |
| 清单未命中（warn-only 期） | 放行 + `log.warn` + 守卫转红 |
| 清单格式错误 | **启动即失败**（fail-fast）—— 配置文件错误必须在启动时暴露，不能等到运行时 |
| 用户 authorities 为空 | 按「无任何权限」处理，不放行任何受保护端点 |

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-133-1 | 会计（无 `voucher:update`） | `PUT /vouchers/{id}` | **403 缺少权限**（与 SPEC-P132 的归属校验**是两道不同的闸**，都要覆盖） |
| AT-133-2 | 管理员（有该码） | 同一请求 | 允许 |
| AT-133-3 | 任意角色 | 调用**未登记**端点 | warn-only 期放行，**且 G2 守卫转红点名该端点** |
| AT-133-4 | 清单里写一个不存在的权限码 | 跑 G1 | 转红 |
| AT-133-5 | 从清单删掉一条写端点 | 跑 G2 | 转红并指出缺口 |
| AT-133-6 | 前端 meta 写一个清单外的权限码 | 跑 G3 | 转红 |
| AT-133-7 | 清单文件语法错误 | 应用启动 | **启动失败**（非静默降级） |

---

## §6 实施批次（**必须分批，每批独立验证**）

| 批次 | 内容 | 风险 |
|---|---|---|
| 1 | 清单骨架 + 三个守卫（G1/G2/G3），**清单先只登记已受保护的 11 个** | 低；G2 以棘轮记录 532 缺口，**不失败** |
| 2 | `PermissionEnforcementInterceptor`（warn-only） | 低；行为不变 |
| 3 | 按模块**分批**补清单：①系统管理 ②凭证/总账 ③arap ④cash ⑤其余 | 中；每批跑全量 L1/L2 |
| 4 | 切换 **fail-closed**，G2 缺口归零 | 高；此刻才真正拦住越权 |

⚠️ **批次 3 的顺序按「越权危害 × 可用性影响」排**：先做**写端点**（307 个），再做读端点；先做**高危模块**（system 清理、凭证审核），后做低危。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不做**数据级**权限（行级/字段级）—— RLS 与 `EnterpriseDataPermissionInterceptor` 已承担
- ❌ 不改 5 个角色的权限集合是否「业务上正确」—— P131 只修了「引用不存在的码」
- ❌ 不改代理端 `agencyRole` 体系（P120/P121 已收敛）

**已知局限**：
1. **动作权限 ≠ 数据隔离** —— 本 SPEC 保证「不能做某个动作」，不保证「看不到别人的数据」。后者靠 RLS/数据权限，**两者不可互相替代**。
2. **清单的准确性取决于人工登记** —— 守卫只能保证「清单与 t_menu/前端一致」，**不能保证权限分配在业务上恰当**。一个码存在但未授予任何角色时，G1/G3 都通过而页面仍 403（P131 已登记同一局限）。
3. **批次 1~3 期间后端仍未真正拦住越权**（warn-only）—— **不得对外宣称「已完成权限管控」**。
4. **543 个端点的清单登记量很大** —— 这是本 SPEC 的主要成本，也是必须机器化（而非逐点加注解）的根本原因。
5. 🔴 **权限 ≠ 引用完整性** —— §0.5 实测证明：服务层的状态/引用/父子约束能挡「删错对象」，**挡不住「无权限者删除」**；反过来，加了 `@PreAuthorize` 也**不会**阻止有权限者删掉仍被引用的数据（如删用中的会计科目）。**两者是正交问题**，本 SPEC 只解决前者；后者是数据完整性范畴，须单独立项。

---

## §8 裁定记录（老丁 2026-10-16 裁定，D-1 ~ D-6 全部裁定）

| # | 议题 | 裁定 | 落地约束 |
|---|---|---|---|
| D-1 | 清单未命中端点的初始策略 | **warn-only + G2 守卫转红** | 批次 1~3 期间**不得阻断请求**，但缺口必须可见；切 fail-closed 需单独立项并重跑 L1/L2 |
| D-2 | G2 存量缺口棘轮初值 | **钉实测值，只许减不许增** | 初值 = 批次 1 登记完成后的实际缺口数，写进 CI 命令行可审计 |
| D-3 | 批次 3 模块顺序 | **system → 总账 → arap → cash → 其余** | 每模块单独提交 + 单独验证 |
| D-4 | 前端按钮是否全接 `v-permission` | **是，但排在后端之后** | 前端是体验层；**后端 fail-closed 前不得宣称管控完成** |
| D-5 | 27 个未止血 DELETE | **与批次 1 同批** | DELETE 是破坏性最高的写操作，清空零新增成本 |
| D-6 | 删被引用的会计科目是否拦 | 🔴 **必须补应用层引用检查**（首版结论「依赖 DB 外键」已被实测推翻，见下） | 与 P133 解耦，单独立项，先于权限批次实施 |

### D-6 取证（2026-10-16 真库实测）—— ⚠️ 首版结论被推翻

**首版推理（错误）**：查到 10 张表持有指向 `t_subject` 的外键，且
`t_voucher_entry.fk_entry_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)`
既无 `ON DELETE CASCADE` 也无 `SET NULL`（默认 `NO ACTION`），据此推断「DB 会直接拒删」。

**实测推翻**：把 admin 临时切到企业 4（有分录的唯一企业），对**被 45 条分录引用**的
科目 `4583 / 1002 银行存款` 执行删除 ⇒

```
DELETE /api/v1/subjects/4583  =>  code=200  msg=ok
科目 4583 → deleted=1（逻辑删除）
t_voucher_entry 中指向它的 45 条分录 → 原样保留，无任何报错
```

🔴 **根因：外键约束对本项目结构性无效。** 铁律 #12 要求所有财务表**逻辑删除**，
行**永不被物理删除** ⇒ 外键检查只在物理删除/更新父键时触发 ⇒ **在本项目永远不会被触发**。
「DB 有 NO ACTION 外键」与「DB 会保护你」是两回事。

**裁定：必须补应用层引用检查。** 与 P133 解耦、单独立项，理由：

- 这是**会计数据完整性**问题，不是权限问题 —— 权限再严，有权限的人照样能删。
- 影响面不止科目：凡「可被软删且被其它表引用」的表都同类，
  而 `SubjectServiceImpl.delete` 目前**只查子科目**（`childCount > 0`），
  **完全不查引用**。
- 🔴 **不得归入 P133 批次**：P133 的 SCOPE 是「谁不能做」，此问题是「什么不能被删」，两者正交。
  混在一起会让权限批次的回归范围失控。

**判据沉淀（本轮最贵的一条）**：
> 裁定「DB 是否会替我兜底」时，**查外键存在性不等于查兜底有效性** ——
> 必须再问一句「**该表会不会被物理删除**」。
> 本项目铁律 #12（逻辑删除）让**所有外键的参照完整性保护归零**，
> 于是「表上有 NO ACTION 外键」变成了**安全感的假象**。
> 这与 §4.5 第 27 条同型：结论若只在最高权限/最优条件下验证，就无法证伪。

### 🔴 附带取证：`fk_entry_voucher ... ON DELETE CASCADE` 的含义

`t_voucher_entry.fk_entry_voucher FOREIGN KEY (voucher_id) REFERENCES t_voucher(id) ON DELETE CASCADE`
⇒ 删凭证会**级联删除其全部分录**，无二次确认、无软删。
当前唯一的防线是 `VoucherServiceImpl.delete` 的「仅 `DRAFT` 可删」。

⇒ 即便 P133 全部完成，「有权限者删掉他人 DRAFT 凭证」仍是**开放问题**（需归属校验，形态同 P132 的编辑校验）。
**已登记为独立工作项，不在本 SPEC 范围** —— 权限 ≠ 归属，二者不可互相替代（§7 局限 1）。

---

### 8.1 批次 1 实施结果（2026-10-16）

**新增清单** `backend/src/main/resources/permission-manifest.yml`（**19 条**，对应当前
19 个带 `@PreAuthorize` 的端点），字段 `method / path / permission / write`。

**新增守卫** `PermissionManifestGuardRealDBTest` **4 例**（L2 真库）：

| ID | 不变量 | 批次 1 的形态 | 实测值 |
|---|---|---|---|
| G1 | 清单权限码须存在于 `t_menu` | **零容忍** | 19/19 存在 |
| G2 | 写端点须登记 | **棘轮 288** | 写端点 307 / 已登记 19 / 缺口 288 |
| G3 | 前端路由权限码须在清单 | **棘轮 33** | 前端 39 / 已登记 6 / 缺口 33 |
| — | **守卫敏感度自证** | G1 假码可判 / G2 缺口可减 / G3 缺口可判 | — |

⚠️ **G2/G3 在批次 1 只能是棘轮，不能零容忍** —— 清单只登记了 19 个端点，
零容忍会让守卫从第一天就红、进而被忽略。切 fail-closed（批次 4）时才改零容忍。

#### 🔴 G1 第一轮就抓到一个真实缺陷：`period:year-close` 是幽灵权限码

`POST /api/base/voucher/v1/period-close/year-close`（执行年度结账）写着
`@PreAuthorize("hasAuthority('period:year-close')")`，而 **`t_menu` 无此权限码、无任何角色持有**
⇒ **年度结账对所有角色（含 ADMIN）永久 403**，报错文案「权限不足」形似正常鉴权。
与 `REQ-2026-146`（`voucher:update`）、`REQ-2026-148`（其余 10 个）**同型**。

已修：`V187` 补菜单行 + 授权（继承**反结账 `period:reopen`** 的角色集合 = ADMIN + FINANCE_MGR）。
选 `period:reopen` 而非 `period:close` 的理由：年度结账与反结账是**同一高危反记账族**，
会计能结账不能反结账本就自相矛盾。

#### 🔴 本轮踩的 5 个坑，其中 3 个是「错误信息指错方向」

1. 🔴 **G2 一度报「写端点 0 / 缺口 0」** —— 用的是 `getPatternsCondition()`，本环境返回 null。
   **「缺口=0」正是假绿形态**（守卫看起来完美，实际什么都没比）。
   正解 `getPatternValues()`。**判据：守卫的统计值为 0 时，先怀疑取数方式，别庆祝「零缺口」。**
2. 🔴 **迁移版本号撞车**：本迁移编为 V179，而仓库已有 `V179__agency_role_permission_baseline.sql`。
   我用 `ls | grep | tail -8` 查最大版本时**恰好把它截掉了**，差点以为 V179 是空的。
   **判据：取版本号前先完整 `ls` 确认，不能靠截断输出推断。**
3. 🔴 **`RAISE NOTICE` 是 PL/pgSQL 语法，不能写在裸 SQL 迁移里**（须包 `DO` 块）。
4. 🔴 **`t_role_menu` 没有 `updated_at` 列**，而 `t_menu.menu_code` 是 NOT NULL 无默认值
   ⇒ 两条 INSERT 的列清单都必须先查 `information_schema`，不能照抄相邻迁移。
5. 🔴 **复制授权关系时，我把「已存在的授权行」原样重插** ⇒ 必然撞 `uq_role_menu`。
   正解：取「持有者**角色集合**」再与新菜单**配对**插入。
6. ⚠️ **前端扫描的假阳性**：`lab:*` 是从 `lab.ts` 的**注释行**里捞出来的
   ⇒ 正则必须跳过注释。其余 35 个码在 `t_menu` **都存在**，纯是「未进清单」。


---

### 8.1 批次 1 实施结果（2026-10-16）

**新增清单** `backend/src/main/resources/permission-manifest.yml`（**19 条**，对应当前
19 个带 `@PreAuthorize` 的端点），字段 `method / path / permission / write`。

**新增守卫** `PermissionManifestGuardRealDBTest` **4 例**（L2 真库）：

| ID | 不变量 | 批次 1 的形态 | 实测值 |
|---|---|---|---|
| G1 | 清单权限码须存在于 `t_menu` | **零容忍** | 19/19 存在 |
| G2 | 写端点须登记 | **棘轮 288** | 写端点 307 / 已登记 19 / 缺口 288 |
| G3 | 前端路由权限码须在清单 | **棘轮 33** | 前端 39 / 已登记 6 / 缺口 33 |
| — | **守卫敏感度自证** | G1 假码可判 / G2 缺口可减 / G3 缺口可判 | — |

⚠️ **G2/G3 在批次 1 只能是棘轮，不能零容忍** —— 清单只登记了 19 个端点，
零容忍会让守卫从第一天就红、进而被忽略。切 fail-closed（批次 4）时才改零容忍。

#### 🔴 G1 第一轮就抓到一个真实缺陷：`period:year-close` 是幽灵权限码

`POST /api/base/voucher/v1/period-close/year-close`（执行年度结账）写着
`@PreAuthorize("hasAuthority('period:year-close')")`，而 **`t_menu` 无此权限码、无任何角色持有**
⇒ **年度结账对所有角色（含 ADMIN）永久 403**，报错文案「权限不足」形似正常鉴权。
与 `REQ-2026-146`（`voucher:update`）、`REQ-2026-148`（其余 10 个）**同型**。

已修：`V187` 补菜单行 + 授权（继承**反结账 `period:reopen`** 的角色集合 = ADMIN + FINANCE_MGR）。
选 `period:reopen` 而非 `period:close` 的理由：年度结账与反结账是**同一高危反记账族**，
会计能结账不能反结账本就自相矛盾。

#### 🔴 本轮踩的 5 个坑，其中 3 个是「错误信息指错方向」

1. 🔴 **G2 一度报「写端点 0 / 缺口 0」** —— 用的是 `getPatternsCondition()`，本环境返回 null。
   **「缺口=0」正是假绿形态**（守卫看起来完美，实际什么都没比）。
   正解 `getPatternValues()`。**判据：守卫的统计值为 0 时，先怀疑取数方式，别庆祝「零缺口」。**
2. 🔴 **迁移版本号撞车**：本迁移编为 V179，而仓库已有 `V179__agency_role_permission_baseline.sql`。
   我用 `ls | grep | tail -8` 查最大版本时**恰好把它截掉了**，差点以为 V179 是空的。
   **判据：取版本号前先完整 `ls` 确认，不能靠截断输出推断。**
3. 🔴 **`RAISE NOTICE` 是 PL/pgSQL 语法，不能写在裸 SQL 迁移里**（须包 `DO` 块）。
4. 🔴 **`t_role_menu` 没有 `updated_at` 列**，而 `t_menu.menu_code` 是 NOT NULL 无默认值
   ⇒ 两条 INSERT 的列清单都必须先查 `information_schema`，不能照抄相邻迁移。
5. 🔴 **复制授权关系时，我把「已存在的授权行」原样重插** ⇒ 必然撞 `uq_role_menu`。
   正解：取「持有者**角色集合**」再与新菜单**配对**插入。
6. ⚠️ **前端扫描的假阳性**：`lab:*` 是从 `lab.ts` 的**注释行**里捞出来的
   ⇒ 正则必须跳过注释。其余 35 个码在 `t_menu` **都存在**，纯是「未进清单」。


---

### 8.1 批次 1 实施结果（2026-10-16）

**新增清单** `backend/src/main/resources/permission-manifest.yml`（**19 条**，对应当前
19 个带 `@PreAuthorize` 的端点），字段 `method / path / permission / write`。

**新增守卫** `PermissionManifestGuardRealDBTest` **4 例**（L2 真库）：

| ID | 不变量 | 批次 1 的形态 | 实测值 |
|---|---|---|---|
| G1 | 清单权限码须存在于 `t_menu` | **零容忍** | 19/19 存在 |
| G2 | 写端点须登记 | **棘轮 288** | 写端点 307 / 已登记 19 / 缺口 288 |
| G3 | 前端路由权限码须在清单 | **棘轮 33** | 前端 39 / 已登记 6 / 缺口 33 |
| — | **守卫敏感度自证** | G1 假码可判 / G2 缺口可减 / G3 缺口可判 | — |

⚠️ **G2/G3 在批次 1 只能是棘轮，不能零容忍** —— 清单只登记了 19 个端点，
零容忍会让守卫从第一天就红、进而被忽略。切 fail-closed（批次 4）时才改零容忍。

#### 🔴 G1 第一轮就抓到一个真实缺陷：`period:year-close` 是幽灵权限码

`POST /api/base/voucher/v1/period-close/year-close`（执行年度结账）写着
`@PreAuthorize("hasAuthority('period:year-close')")`，而 **`t_menu` 无此权限码、无任何角色持有**
⇒ **年度结账对所有角色（含 ADMIN）永久 403**，报错文案「权限不足」形似正常鉴权。
与 `REQ-2026-146`（`voucher:update`）、`REQ-2026-148`（其余 10 个）**同型**。

已修：`V187` 补菜单行 + 授权（继承**反结账 `period:reopen`** 的角色集合 = ADMIN + FINANCE_MGR）。
选 `period:reopen` 而非 `period:close` 的理由：年度结账与反结账是**同一高危反记账族**，
会计能结账不能反结账本就自相矛盾。

#### 🔴 本轮踩的 5 个坑，其中 3 个是「错误信息指错方向」

1. 🔴 **G2 一度报「写端点 0 / 缺口 0」** —— 用的是 `getPatternsCondition()`，本环境返回 null。
   **「缺口=0」正是假绿形态**（守卫看起来完美，实际什么都没比）。
   正解 `getPatternValues()`。**判据：守卫的统计值为 0 时，先怀疑取数方式，别庆祝「零缺口」。**
2. 🔴 **迁移版本号撞车**：本迁移编为 V179，而仓库已有 `V179__agency_role_permission_baseline.sql`。
   我用 `ls | grep | tail -8` 查最大版本时**恰好把它截掉了**，差点以为 V179 是空的。
   **判据：取版本号前先完整 `ls` 确认，不能靠截断输出推断。**
3. 🔴 **`RAISE NOTICE` 是 PL/pgSQL 语法，不能写在裸 SQL 迁移里**（须包 `DO` 块）。
4. 🔴 **`t_role_menu` 没有 `updated_at` 列**，而 `t_menu.menu_code` 是 NOT NULL 无默认值
   ⇒ 两条 INSERT 的列清单都必须先查 `information_schema`，不能照抄相邻迁移。
5. 🔴 **复制授权关系时，我把「已存在的授权行」原样重插** ⇒ 必然撞 `uq_role_menu`。
   正解：取「持有者**角色集合**」再与新菜单**配对**插入。
6. ⚠️ **前端扫描的假阳性**：`lab:*` 是从 `lab.ts` 的**注释行**里捞出来的
   ⇒ 正则必须跳过注释。其余 35 个码在 `t_menu` **都存在**，纯是「未进清单」。


---

### 8.1 批次 1 实施结果（2026-10-16）

**新增清单** `backend/src/main/resources/permission-manifest.yml`（**19 条**，对应当前
19 个带 `@PreAuthorize` 的端点），字段 `method / path / permission / write`。

**新增守卫** `PermissionManifestGuardRealDBTest` **4 例**（L2 真库）：

| ID | 不变量 | 批次 1 的形态 | 实测值 |
|---|---|---|---|
| G1 | 清单权限码须存在于 `t_menu` | **零容忍** | 19/19 存在 |
| G2 | 写端点须登记 | **棘轮 288** | 写端点 307 / 已登记 19 / 缺口 288 |
| G3 | 前端路由权限码须在清单 | **棘轮 33** | 前端 39 / 已登记 6 / 缺口 33 |
| — | **守卫敏感度自证** | G1 假码可判 / G2 缺口可减 / G3 缺口可判 | — |

⚠️ **G2/G3 在批次 1 只能是棘轮，不能零容忍** —— 清单只登记了 19 个端点，
零容忍会让守卫从第一天就红、进而被忽略。切 fail-closed（批次 4）时才改零容忍。

#### 🔴 G1 第一轮就抓到一个真实缺陷：`period:year-close` 是幽灵权限码

`POST /api/base/voucher/v1/period-close/year-close`（执行年度结账）写着
`@PreAuthorize("hasAuthority('period:year-close')")`，而 **`t_menu` 无此权限码、无任何角色持有**
⇒ **年度结账对所有角色（含 ADMIN）永久 403**，报错文案「权限不足」形似正常鉴权。
与 `REQ-2026-146`（`voucher:update`）、`REQ-2026-148`（其余 10 个）**同型**。

已修：`V187` 补菜单行 + 授权（继承**反结账 `period:reopen`** 的角色集合 = ADMIN + FINANCE_MGR）。
选 `period:reopen` 而非 `period:close` 的理由：年度结账与反结账是**同一高危反记账族**，
会计能结账不能反结账本就自相矛盾。

#### 🔴 本轮踩的 5 个坑，其中 3 个是「错误信息指错方向」

1. 🔴 **G2 一度报「写端点 0 / 缺口 0」** —— 用的是 `getPatternsCondition()`，本环境返回 null。
   **「缺口=0」正是假绿形态**（守卫看起来完美，实际什么都没比）。
   正解 `getPatternValues()`。**判据：守卫的统计值为 0 时，先怀疑取数方式，别庆祝「零缺口」。**
2. 🔴 **迁移版本号撞车**：本迁移编为 V179，而仓库已有 `V179__agency_role_permission_baseline.sql`。
   我用 `ls | grep 加 tail -8` 查最大版本时**恰好把它截掉了**，差点以为 V179 是空的。
   **判据：取版本号前先完整 `ls` 确认，不能靠截断输出推断。**
3. 🔴 **`RAISE NOTICE` 是 PL/pgSQL 语法，不能写在裸 SQL 迁移里**（须包 `DO` 块）。
4. 🔴 **`t_role_menu` 没有 `updated_at` 列**，而 `t_menu.menu_code` 是 NOT NULL 无默认值
   ⇒ 两条 INSERT 的列清单都必须先查 `information_schema`，不能照抄相邻迁移。
5. 🔴 **复制授权关系时，我把「已存在的授权行」原样重插** ⇒ 必然撞 `uq_role_menu`。
   正解：取「持有者**角色集合**」再与新菜单**配对**插入。
6. ⚠️ **前端扫描的假阳性**：`lab:*` 是从 `lab.ts` 的**注释行**里捞出来的
   ⇒ 正则必须跳过注释。其余 35 个码在 `t_menu` **都存在**，纯是「未进清单」。


---

### 8.2 批次 2 实施结果：`PermissionEnforcementInterceptor`（warn-only）

**新增 3 个生产类**：
- `PermissionManifest` —— 清单加载器（snakeyaml，**唯一解析实现**）
- `PermissionEnforcementInterceptor` —— 端点权限执行
- `PermissionWebMvcConfig` —— 注册（本项目**首个** `WebMvcConfigurer`）

**三条分支（D-1 裁定）**：
| 情形 | 行为 | 理由 |
|---|---|---|
| 命中清单且持有权限 | 放行 | — |
| 命中清单但缺权限 | **403**，响应体指名所需权限码 | 与这 19 个端点既有的 `@PreAuthorize` **行为一致** ⇒ 本阶段**不改变任何现有行为** |
| **清单未命中** | **放行 + 告警（聚合去重）** | 存量 288 个写端点未登记，此刻 fail-closed 会**全站功能一夜全废** |

⚠️ **不得对外宣称「已完成权限管控」** —— 批次 4 切 fail-closed 之前越权仍畅通。

**守卫** `PermissionEnforcementInterceptorRealDBTest` **9 例**：
逻辑 8 例（缺权限 403 / 持有放行 / 未登记 warn-only 放行 / 匿名跳过 /
非 HandlerMethod 跳过 / `{id}` 单段匹配 / 清单加载 / `find` 可空）
+ **注册 1 例**（证明它真被接进请求链，而非只是一段正确的死代码）。
**✅ 反证**：摘除 `addInterceptors` 注册 ⇒ 注册断言转红。

🔴 **判据沉淀：逻辑对 ≠ 生效。** 只断言「清单里有这 19 条」只能证明清单存在，
证明不了它在拦截任何东西（§4.5 第 21 条）。故必须同时验
①`preHandle` 的分支行为 ②它确实被注册进拦截器链 —— 两者合起来才等价于「生效」。

#### 🔴 本轮踩的 5 个坑，其中 4 个「报错指错方向」

1. 🔴 **L1 一次 97 个用例全挂**，报 `NoSuchBeanDefinitionException: PermissionManifest`。
   根因：清单是普通 `@Component`，而 **`@WebMvcTest` 是切片上下文，只加载白名单 bean**
   （Controller / WebMvcConfigurer / Filter / HandlerInterceptor…），**不扫描普通 `@Component`**。
   🔴 **判断依据**：97 个失败里 86 个是 `ApplicationContext failure threshold exceeded`
   的**级联**，真正的首因只有 1 个。**只看错误汇总会被级联淹没、误判成 97 个不同缺陷。**
2. 🔴 **把清单声明成 `WebMvcConfigurer` 里的 `@Bean`** ⇒
   `BeanCurrentlyInCreationException: unresolvable circular reference`
   （配置创建中 → 需要拦截器 → 需要清单 → 清单的 `@Bean` 方法在本配置里）。
   **判据：同一个配置类内的多个 bean 应彼此不注入，让容器排序。**
3. 🔴 **守卫自身的错断言**：`InterceptorRegistry` 里装的是 `InterceptorRegistration`
   **包装器**而非拦截器，我直接 `instanceof` 断言必然失败 ——
   而这红灯一度让我以为是「注册没生效」，差点去改生产代码。
   **判据：守卫报错时先问「是代码错还是断言错」。**
4. 🔴 **`@PostConstruct` 不是注册注解** —— 只写它而忘了 `@Component`，
   对象压根不进容器（报错说的是「缺 bean」，真因是「我以为回调等于注册」）。
5. 🔴 **我自己的脚本用 `if "@Component" not in s` 判断是否加注解**，
   而 **javadoc 里本就出现该字符串** ⇒ 条件恒为假 ⇒ 注解一直没加上，我据此以为已修好。
   **判据：不要用「字符串是否出现」判断「注解是否存在」，要用行首匹配。**

**最终解法（三者都是切片友好的形态）**：清单 = 普通 `@Component`；
拦截器 = `@Component` 且用 **`ObjectProvider` 可选依赖**清单（切片里没有清单就不安装执行）；
配置 = 构造器注入拦截器。


---

### 8.3 批次 3 模块 1（系统管理）：先补执行模式开关，再登记端点

#### 🔴 登记端点前必须先有模式开关（否则「登记」会直接变成破坏性变更）

批次 2 的拦截器在「命中清单但缺权限」时**直接 403**。若此时直接登记那 288 个写端点，
**登记动作本身就会让它们开始拦截** —— 等于**跳过批次 4 就 fail-closed**，
与 D-1 裁定直接冲突，且没有任何一处人工决策。

⇒ 清单新增**显式**字段（不给默认值，缺失即拒绝加载）：

```yaml
enforcement: warn-only   # warn-only | enforcing，批次 4 才切 enforcing
```

- `warn-only`：命中但缺权限 ⇒ **只告警仍放行**（告警按端点聚合去重）
- `enforcing`：命中但缺权限 ⇒ 403

⚠️ **不给默认值**是刻意的：将来有人切 `enforcing` 时，任何人打开清单都能立刻看到现值。

#### 模块 1 登记结果

**48 条**（批次 1 的 19 + 本批 29），覆盖 307 个写端点中的 48 个：

| 分组 | 端点数 | 复用权限码 |
|---|---|---|
| 清库 | 9 | `system:clear` |
| 用户 / 角色 / 菜单 / 部门 | 16 | `system:{user,role,menu,dept}:list` |
| 会计期间（含 close） | 7 | `periods:manage` + `period:close` |
| 凭证类型 / 常用摘要 / 系统参数 | 10 | `voucher:type:list` / `summary:lib:list` / `sys:config:list` |

🔴 **本批不新增任何权限码**，全部复用 `t_menu` 现有码（G1 已验证 10 个码全部存在）。
理由：批次 3 的目的是**让清单覆盖真值**，不是**发明权限模型**；
发明权限码属于业务裁定，应在 SPEC §8 单独立项。

**棘轮收紧**（§4.5 第 25 条：实测值变好必须重抬）：
`G2 288 → 259`、`G3 33 → 28`。

#### 🔴 我自己的脚本 bug，以及它催生的 G4 守卫

登记脚本的去重逻辑写错了 —— 我用两个独立循环把「所有 method」与「所有 path」两两配对，
于是 `already` 集合里几乎全是无效键，**跳过判定形同虚设**，一次混进 **9 条重复的 `clear-*` 条目**。

症状很有欺骗性：清单里 `grep -c path` 得 **57**，但 G2 报告「已登记 48」——
两者不一致才暴露了问题。若没有 G2 的独立统计，这 9 条重复会**冒充 9 次覆盖**，
而清单看起来「更全」了。

⇒ 新增 **G4：清单内不得有重复的 `METHOD+path`**。

> **判据**：清单类资产的「条数」与「去重后的条数」必须同时可见。
> 重复项**不会**让缺口变小，却会让**清单看起来更全** ——
> 即用一个重复项冒充一次覆盖。凡「数量统计」类指标，都要同时报**去重后**的值。

**其余踩坑**：`enforcing` 分支的守卫用例里，我先置 `enforcing=true` 再 `loadForTest()`，
而 `loadForTest()` 会从配置文件读回 `warn-only` 把它覆盖 ⇒ 用例假红
（症状是「以为已切 enforcing 却仍放行」）。**顺序要紧：先加载、后置位**，
并在置位后加一条自证断言。


---

### 8.4 批次 3 模块 2（总账/凭证）：🔴 撞上真正的阻塞，改为提出裁定

按 D-3 顺序本轮做总账/凭证（31 个写端点，含 report/balance）。结果**只登记了 4 个**，
其余 27 个**刻意不登记** —— 因为它们需要一个**还不存在的权限码**。

#### 撞上的问题：凭证生命周期**没有动作级权限码**

`t_menu` 里凭证类权限码只有两个：`voucher:list`、`voucher:update`（`voucher:type:list` 属类型）。
而这批端点涉及**人审铁律 #1**（审核/过账/红冲必须由人触发）与**铁律 #3**（已审核凭证只能红冲）：

| 端点族 | 需要的动作码 | 现有码能否诚实对应 |
|---|---|---|
| `/vouchers/{id}/audit`、`batch-audit` | `voucher:audit` | ❌ 不存在 |
| `/vouchers/{id}/post`、`batch-post` | `voucher:post` | ❌ 不存在 |
| `/vouchers/{id}/unpost` | `voucher:unpost` | ❌ 不存在 |
| `/vouchers/{id}/reverse` | `voucher:reverse` | ❌ 不存在 |
| `/vouchers/{id}/reject` | `voucher:reject` | ❌ 不存在 |
| `/vouchers/{id}/submit` | `voucher:submit` | ❌ 不存在 |
| `/vouchers/{id}/close` | `voucher:close` | ❌ 不存在 |
| `/vouchers`（创建） | `voucher:create` | ❌ 不存在（只有 update） |

#### 🔴 为什么**不能**先用粗粒度码顶上（这是本节最重要的一条）

在 warn-only 阶段，登记 `voucher:list` 给审核端点**毫无拦截效果** ——
看起来零成本。但批次 4 切 fail-closed 那一刻，它会变成
**「能看凭证就能审核凭证」**的静默漏洞，而此时：

- 清单**看起来是齐的**（G2 缺口已补）
- G1 **绿的**（`voucher:list` 确实存在于 `t_menu`）
- G2 **绿的**（端点已登记）
- G3 **绿的**
- **没有任何守卫会报错**

> **判据沉淀：权限码的语义错配属于「不会自己暴露的缺陷」** ——
> 它不会让任何现有守卫变红，只会在**守卫全绿时**悄悄放行越权。
> 因此这类缺口必须**显式登记为「待裁定」并加锁**，而不是用近似值填平 ——
> 填平会让「未完成」看起来像「已完成」。

#### 已登记的 4 个（现有码能精确对应）

| 端点 | 权限码 |
|---|---|
| `POST /period-close/close` | `period:close` |
| `POST /period-close/batch-review-post` | `period:close` |
| `POST /period-close/profit-distribution` | `period:close` |
| `POST /period-close/profit-carryover` | `period:carryover:guide` |

清单现有 **52 条**，棘轮 **G2 255 / G3 27**。

#### 新增两条守卫（把「未完成」钉成可见红灯）

- **G5**：10 个人审生命周期端点**在裁定前不得被登记** ——
  防止后人顺手用 `voucher:list` 交差。断言消息直接指向本节的裁定项。
- **G5b**：这 10 个端点**确实仍存在于运行时** ——
  防止端点改名后 G5 **空转**（清单里的端点都不存在了，断言恒成立）。
  **判据：守卫的清单必须与真值源保持同步，否则它锁的是空气。**

#### 🔴 待老丁裁定（阻塞批次 3 剩余 255 个写端点的大部分）

| # | 议题 | 我的建议 |
|---|---|---|
| **D-7** | 是否新增凭证动作级权限码（`voucher:create/audit/post/unpost/reverse/reject/submit/close` 共 8 个） | **建议新增**。⚠️ 角色分配是**业务裁定**，我不擅自决定；建议按「同页面既有码的角色集合」给默认值（`voucher:list` 的持有者 = ACCOUNTANT/ADMIN/FINANCE_MGR），再由老丁按「制单/审核/反审核是否要分权」调整 |
| **D-8** | 审核与过账是否**必须分权**（制单人不得审自己的凭证 = 铁律 #1） | 🔴 **强烈建议分权**：`voucher:audit` 与 `voucher:post` 分属不同角色，否则「能审就能过账」，制单人可自审自过 |
| **D-9** | `voucher:reverse`（红冲）是否独立成码 | 建议**独立**。红冲是最高危的反记账操作，不应与普通修改同权 |


---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: EndpointPermissionManifest
module: security
table: null

rules:
  - id: C-01
    source: "P133 §1①"
    rule: "权限清单是端点权限的唯一事实源，前端路由 meta 与后端执行均从它派生，不得各写一份"
    mapping: { single_source: permission-manifest.yml, consumers: [backend_enforcement, frontend_guard_G3] }
    implementation: "permission-manifest.yml + PermissionEnforcementInterceptor"
  - id: C-02
    source: "P133 §1④ G2"
    rule: "所有 POST/PUT/DELETE/PATCH 端点必须登记在清单中；存量缺口以棘轮记录，只许减不许增"
    mapping: { coverage_required: write_endpoints, ratchet: only_shrink }
    implementation: "ManifestWriteCoverageTest"
  - id: C-03
    source: "P133 §0.3 / P131"
    rule: "清单中的每个权限码必须在 t_menu 中存在对应行"
    mapping: { invariant: permission_exists_in_menu }
    implementation: "RoutePermissionGhostCodeRealDBTest（P131 G1）"
  - id: C-04
    source: "P133 §1③"
    rule: "清单未命中的端点：warn-only 期放行但必须留痕并使 G2 转红；fail-closed 期一律拒绝"
    mapping: { warn_only: allow_and_flag, fail_closed: deny, must_log: true }
    implementation: "PermissionEnforcementInterceptor"
  - id: C-05
    source: "P133 §4"
    rule: "清单文件格式错误必须导致应用启动失败，不得静默降级为放行"
    mapping: { on_parse_error: fail_fast }
    implementation: "PermissionManifestLoader"
  - id: C-06
    source: "P133 §2.2"
    rule: "缺权限时须返回 403 并点名权限码，便于排障"
    mapping: { status: 403, message_must_contain: permission_code }
    implementation: "PermissionEnforcementInterceptor"
  - id: C-07
    source: "P133 §3"
    rule: "动作权限不得替代数据隔离：数据级仍由 RLS 与 EnterpriseDataPermissionInterceptor 承担"
    mapping: { orthogonal_layers: [authentication, action_permission, book_status, data_permission, rls] }
    implementation: "n/a"
