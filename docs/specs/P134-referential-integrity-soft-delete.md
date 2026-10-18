# SPEC-P134 —— 逻辑删除使外键保护结构性失效（引用完整性守卫）

> **状态**：批次 1 已实施并验证（2026-10-16）；D-1~D-4 已裁定；批次 2~4 待办
> **REQ**：`REQ-2026-150`
> **优先级**：P0（会计数据完整性，无报错、无告警、不可逆）
> **前置**：本 SPEC 由 `SPEC-P133` 的 D-6 取证**派生**（裁定 D-6 时发现首版结论错误）
> **竞品对标**（铁律 #15）：用友 U8 / 金蝶云星空 的基础档案删除均有「引用检查」开关，删除被引用的科目时**明确列出引用单据并拒绝**，不提供静默软删。

---

## §0 取证（全部实测，非推断）

### 0.1 实测证明：外键**从未**保护过本项目

对**被 45 条凭证分录引用**的科目 `4583 / 1002 银行存款` 执行 `DELETE /api/v1/subjects/4583`：

```
HTTP  code=200  msg=ok
t_subject 4583            → deleted=1（逻辑删除，行仍在）
t_voucher_entry 指向它的 45 条分录 → 原样保留，零报错
```

### 0.2 根因：铁律 #12 让**所有外键的参照完整性保护归零**

外键检查**只在父行被物理删除或父键被更新时触发**。
而铁律 #12 规定「关键财务表禁止物理删除，统一使用 `deleted` 字段」
⇒ 本项目**没有任何财务表会被物理删除**
⇒ **表上写 `FOREIGN KEY ... NO ACTION` 也永远不会执行。**

### 0.3 这是**类缺陷**，不是单表缺陷

```sql
select confrelid::regclass::text, count(*),
       bool_or(pg_get_constraintdef(oid) like '%ON DELETE CASCADE%')
from pg_constraint where contype='f' group by 1;
```

实测：**25 张表**同时满足「持有入向外键」+「有 `deleted` 列」
⇒ 25 张表的外键保护**全部归零**。

⚠️ 部分由 grep 匹配 Controller 得出，**可能含误判**（如 `t_user` 命中 `AssignmentController`
是「用户-企业分配」而非「删用户」）⇒ 逐表核实是**实施时**的任务，本 SPEC 不预判。

### 0.4 🔴 首版推理错在哪（本轮最贵的判据）

首版裁定「依赖 DB 外键即可」，理由是查到 `fk_entry_subject` 无 `CASCADE`/`SET NULL`
⇒ 默认 `NO ACTION` ⇒ 「DB 会直接拒删」。

**错在：查了外键"存不存在"，没查外键"会不会触发"。**

**判据沉淀**：
> 裁定「DB 是否会替我兜底」时，查到外键存在**不等于**查到兜底有效；
> 必须再问「**该表会不会被物理删除**」。
> 在本项目答案恒为「永远不会」⇒ **任何「表上有 NO ACTION 外键所以安全」的结论都是安全感假象**。
> 与 `AGENTS §4.5 第 27 条`同型：结论若只在最优条件下验证，就无法证伪。

---

## §1 问题定义与范围

### 1.1 一句话

**软删让数据库静默吞掉所有引用完整性检查，导致「删掉仍在使用的会计主数据」全程无报错。**

### 1.2 危害排序

| 危害 | 说明 |
|---|---|
| 🔴 **报表静默缺数** | 分录仍在，但 `JOIN t_subject ON deleted=0` 取不到科目名 ⇒ 科目明细列空/缺行 |
| 🔴 **不可逆** | 逻辑删除无级联，恢复需人工逐条 |
| 🔴 **无告警** | 返回 200 `ok`，运维与业务都以为删成功了 |
| 🟡 权限再严也无用 | 这是完整性问题，**ADMIN 一样能删** —— 与 P133 正交 |

### 1.3 明确不做（§7.7）

- ❌ 不改逻辑删除机制本身（铁律 #12 是设计决策，不是缺陷）
- ❌ 不做**级联软删**（"删科目连带删凭证"是业务决策，须单独裁定）
- ❌ 不补 `ON DELETE CASCADE`（对软删**无效**，属装饰）
- ❌ 不在 P134 内做权限改造（属 P133）

---

## §2 输入契约 / §3 输出契约

### 2.1 新增输入契约：声明式注解

```java
@ReferenceGuard(
    parent = "t_subject",
    label  = "会计科目",
    children = {
        @Ref("t_voucher_entry",   "subject_id", "凭证分录"),
        @Ref("t_subject_balance", "subject_id", "科目余额"),
        @Ref("t_business_doc_entry", "subject_id", "业务单分录"),
        @Ref("t_bank_account",    "subject_id", "银行账户"),
        @Ref("t_customer",        "subject_id", "客户档案"),
        @Ref("t_vendor",          "subject_id", "供应商档案"),
        @Ref("t_employee",        "subject_id", "员工档案"),
        @Ref("t_account_mapping_rule", "target_subject_id", "科目映射"),
        @Ref("t_voucher_template_line", "subject_id", "凭证模板行"),
        @Ref("t_budget_entry",    "subject_id", "预算明细"),
    })
```

**设计要点**：
- 复用 P113 `EnterpriseWriteGuard` 的**切面范式**（一次覆盖全仓写端点，逐点加必漏）
- 声明式 ⇒ 引用关系写在代码里、可 review、可 grep；**不靠运行时反射扫 FK**（那会让「表结构」与「业务引用」混淆）

### 2.2 运行时输出契约（被引用时）

```json
{ "code": 400, "msg": "该会计科目已被以下数据引用，无法删除：凭证分录(45条)、银行账户(1条)" }
```

**要求**：
- HTTP **400**（业务规则冲突），**不是 500**
- 消息**指名被引用的子表与条数** —— 让用户知道该先处理什么
- 🔴 **不得回显表结构细节**给前端（`ConstraintName` 等只进日志），避免泄漏库结构

### 2.3 契约测试

| ID | Given-When-Then |
|---|---|
| AT-134-1 | **被引用的科目删除 ⇒ 拒绝**：科目被 45 条分录引用时删除 ⇒ 抛 `BusinessException`，且**科目 `deleted` 仍为 0** |
| AT-134-2 | **负向断言（关键）**：删除被拒后，`t_subject` 该行 `deleted` 仍为 0、引用它的分录数不变 |
| AT-134-3 | **未被引用的科目可删**：无引用的科目删除 ⇒ 成功，`deleted=1` |
| AT-134-4 | **消息含引用方**：抛出的消息同时包含子表中文名与条数 |
| AT-134-5 | **多子表**：科目同时被分录与银行账户引用 ⇒ 消息列出**两者**（不是只报第一个） |
| AT-134-6 | **跨企业不误伤**：企业 A 的科目被企业 B 的数据引用 ⇒ **不计入**（引用检查必须带 `enterprise_id`） |
| AT-134-7 | **结构守卫**：所有「可软删 + 有入向 FK + 有 DELETE 端点」的表，必须有 `@ReferenceGuard` |
| AT-134-8 | **守卫自证**：注入一个未标注的引用型 DELETE 端点 ⇒ AT-134-7 转红 |

🔴 **AT-134-6 不可省**：`t_voucher_entry` 有 45 条指向 4583 的分录，若引用检查不带企业维度，
企业 B 的任何科目都会被「全局有引用」误杀 ⇒ **功能不可用**。

---

## §4 状态流转

```
删除请求
   │
   ├─ 未标注 @ReferenceGuard ──────────────► 放行（warn 日志，守卫 G7 转红）
   │
   └─ 已标注
        ├─ 无企业上下文 ───────────────────► 放行 + warn（系统路径，不误伤定时任务）
        ├─ 逐个子表 count(子表.列 = id AND 企业一致)
        │     ├─ 全部为 0 ───────────────► 执行删除（成功）
        │     └─ 任一 > 0 ───────────────► 抛 BusinessException(400)，**不删**
        └─ 事务保证：抛异常 ⇒ 删除未发生
```

---

## §5 异常处理

| 场景 | 处理 |
|---|---|
| 子表查询失败（表不存在等） | 🔴 **fail-loud**：抛 `BusinessException` 并**指明缺哪个子表**，不得静默跳过（§4.5 第 49 条同型：静默跳过 = 用一个洞换另一个洞） |
| 子表无 `enterprise_id` | 以父表企业为准；守卫测试须显式登记该例外 |
| 守卫自身抛异常 | 删除**不得**执行 |

---

## §6 实施批次（必须分批，每批独立验证）

| 批次 | 内容 | 依据 |
|---|---|---|
| **1** | 切面机制 + `t_subject`（**已实测证明**）+ AT-134-1~6 | §0.1 实测 |
| **2** | 客商/系统：`t_customer`、`t_vendor`、`t_role`、`t_menu`、`t_user` | §0.3 待逐表核实 |
| **3** | 其余取证后补：`t_agency`、`t_enterprise`、`t_ticket`、`t_bank_statement` … | ⚠️ **必须先实测**，不得按同型推定 |
| **4** | 结构守卫 G7 + 棘轮（防类缺陷复发） | §2.3 AT-134-7/8 |

**批次 3 的前置条件（硬性）**：每张表都必须有**独立的真库实测**证明「删了会静默丢数据」，
**不得由 `t_subject` 同型推定** —— §0.3 已声明 grep 结果可能含误判。

---

## §7 已知局限（§7.7）

1. **不解决物理删除的级联问题** —— 若将来引入物理删除，FK 才会重新生效，本守卫仍是有益的第二层。
2. **引用清单依赖人工登记** —— 守卫 G7 只能保证「有引用型 DELETE 端点就有标注」，
   **不能保证标注里的子表列名写对** ⇒ AT 须逐个覆盖已登记的子表。
3. **不覆盖无 DELETE 端点的表** —— 25 张里有相当一部分没有删除入口（如 `t_budget`、`t_asset_card`），
   本 SPEC **不为其新增端点**，仅在守卫中标注「无入口」以免后人误加。
4. 🔴 **与 P133 正交**：本 SPEC 不解决「谁不能删」，只解决「什么不能被删」。
   **P133 完成后本 SPEC 依然必要** —— ADMIN 依然能删掉仍被引用的数据。

---

## §8 裁定记录（老丁 2026-10-16 裁定 D-1~D-4）

| # | 议题 | 裁定 |
|---|---|---|
| D-1 | 是否覆盖全部 25 张表 | **否**，按批次 1~3 分批，每张表必须有**独立真库实测**，不得按 `t_subject` 同型推定 |
| D-2 | 引用检查是否带 `enterprise_id` | 🔴 **原裁定「必须带」已被批次 3 实测推翻，现为「必须不带（全局检查）」** —— 见 §8.5 |
| D-3 | 被引用时是否允许「强制删除」 | **不允许**。需要时应**停用**科目而非删除；强制删除若确有场景须单独立项，且须二次确认 + 审计 |
| D-4 | 是否同时拦 `role`/`menu` 等系统表 | **拦**，但须先实测确认有无合法删除场景（已并入批次 2 的前置条件） |

### 8.2 批次 2 的**选表依据**：真库「活引用数」（而非按 `t_subject` 同型推定）

D-1 要求「每张表须独立实测」。原计划的猜测清单是错的 —— 实测（plpgsql 逐外键执行真查询，
统计「子表列值非空**且能对上父表主键**」的行数）得到 **19 条外键确有活引用**：

| 父表 ← 子表 | 活引用 | 对应 DELETE 端点 | 原实现有无检查 |
|---|---|---|---|
| `t_menu` ← `t_role_menu` | **150** | `MenuController` | 🔴 无（且**手动物理删** `t_role_menu`） |
| `t_role` ← `t_role_menu` | **150** | `RoleController` | ⚠️ **只查了 `t_user_role`，漏掉 150 条授权** |
| `t_bank_account` ← `t_bank_statement` | **80** | `BankAccountController` | 🔴 零检查 |
| `t_subject` ← `t_voucher_entry` | 90 | `SubjectController` | ✅ 批次 1 已修 |
| `t_voucher` ← `t_voucher_entry` | 90 | `VoucherController` | ⚠️ 仅「DRAFT 可删」 |
| `t_voucher_template` ← `_line` | 50 | `VoucherTemplateController` | 🔴 手动物理删行 |
| `t_voucher_type` ← `t_voucher` | 45 | `VoucherTypeController` | 🔴 零检查 |
| `t_agency` ← 3 子表 | 13 | `AgencyController` | 🔴 零检查 |
| `t_user` ← `t_user_role`/`t_agency_user` | 12 | `UserController` | 🔴 零检查 |
| `t_enterprise` ← 2 子表 | 9 | `EnterpriseController` | ⚠️ 仅角色/可见性守卫 |

⇒ **原 SPEC 里「`t_customer`/`t_vendor` 排在前面」的猜测是错的**：`t_customer`、`t_vendor`、
`t_ticket`、`t_menu`（其引用方）等**活引用为 0**；真正的高危是 `t_role`/`t_menu`（150）、
`t_bank_account`（80）、`t_agency`、`t_user`。批次 2 按实测重排。

⚠️ **`t_menu` 与 `t_voucher_template` 是另一种危险**：`MenuServiceImpl#delete` 会
`roleMenuMapper.delete(...)` **物理删掉 150 行角色授权**，`VoucherTemplateServiceImpl#delete`
会 `lineMapper.deleteByTemplateId(id)` 级联删行。**硬删无软删可逆性** ——
这不是「静默孤儿」，是**即时数据损失**。已登记为批次 3 的**最高优先级**，需老丁单独裁定
「菜单删除是否应连带删授权」。

### 8.3 批次 2 实施结果（2026-10-16）

**应用 `@ReferenceGuard` 的 6 个服务**（子表声明数 15）：

| 服务 | 父表 | 子表 |
|---|---|---|
| `BankAccountServiceImpl` | `t_bank_account` | 流水 / 银行日记账 / 对账异常 |
| `AgencyServiceImpl` | `t_agency` | 代理用户 / 代理客户关系 / 客户企业 / 合同 |
| `UserServiceImpl` | `t_user` | 用户角色 / 代理用户 |
| `RoleServiceImpl` | `t_role` | **角色菜单授权**（补上原实现漏掉的 150 条）/ 用户角色 |
| `CustomerServiceImpl` | `t_customer` | 客户对账单 / 销项发票 |
| `VendorServiceImpl` | `t_vendor` | 进项发票 / 采购退货 |

**守卫** `MultiEntityReferenceGuardRealDBTest` **8 例**（L2 真库），含**正控**（无引用的银行账户仍可删）
与**自证**（6 个服务缺任一注解即红）。
**✅ 反证**：摘除 `BankAccountServiceImpl` 的 `@ReferenceGuard` ⇒ **2 例转红**，还原后 8/8 绿。

🔴 **踩坑登记（本轮最密集的一次夹具排错，全部同型）**：
1. 🔴 **`bean.getClass()` 在 AOP 代理下拿到的是代理类**，JDK 代理上**看不到 impl 的注解**
   ⇒ 自证假红。正解 `AopUtils.getTargetClass(bean)`。**判据：反射断言注解前，先确认
   拿到的类是不是实现类。**
2. 🔴 `t_agency` **没有 `enterprise_id` 列**（凭印象加会直接语法错）。
3. 🔴 `chk_stmt_type` 允许集是 `INCOME/EXPENSE/TRANSFER_IN/TRANSFER_OUT`（我写的 `DEPOSIT` 违规）。
4. 🔴 `chk_invoice_type` 允许集是 `SPECIAL/PLAIN/CUSTOMS/TRANSPORT`（我写的 `VAT_SPECIAL` 违规）。
5. 🔴 `t_input_invoice` 有 **8 个必填列**（`invoice_date`/`period`/金额三件套/类型）。
6. ⚠️ `t_customer` 活引用为 0 ⇒ 用例**必须自己造引用**，否则「不抛异常」是**正确行为**，
   按错误预期写断言会把守卫误判为失效。
   **判据：断言「必须被拒」前，先确认测试库里确实造出了引用。**

**判据沉淀**：本轮 6 个坑有 5 个是「凭语感/印象写 schema」——
与 `AGENTS §4.2 第 9/14 条`（同名状态跨表不同、CHECK 大小写敏感、必填列缺失）完全同型。
**测试夹具优先用 Entity 造数**；必须裸 SQL 时，
**一条 `information_schema.columns` + `pg_get_constraintdef` 能省掉 5 次试错**。

### 8.4 批次 3 遗留（需老丁单独裁定）

- `t_menu` / `t_voucher_template` 的**级联硬删**（见 §8.2 末）—— 优先级最高
- `t_voucher`（仅 DRAFT 可删，但他人 DRAFT 仍可删 —— 需归属校验，形态同 P132）
- `t_enterprise`、`t_ticket` 等
- 结构守卫 G7 + 棘轮

---

### 8.1 批次 1 实施结果（2026-10-16）

**新增 3 个生产类**：
- `ReferenceGuard`（声明式注解）—— `parent` / `label` / `idArgIndex` / `children`
- `Ref`（子表引用声明）—— `table` / `column` / `label` / `scopedByEnterprise`
- `ReferenceGuardAspect` —— 逐子表 count，>0 即抛 `BusinessException(400)` 且**不执行删除**

**应用到**：`SubjectServiceImpl#delete`（§0.1 唯一实测证明的表），声明 10 个子表引用。

**守卫**：`SubjectReferenceGuardRealDBTest` **6 例**（L2 真库）
- AT-134-1 被引用的科目删除被拒
- AT-134-2 🔴 **负向断言**：删除被拒后科目 `deleted` 仍为 0、引用它的分录数**不变**
  （只验异常无法排除「先删再校验」的实现）
- AT-134-3 **正控**：无引用的科目**必须能删** —— 防止守卫把功能全打挂
- AT-134-4 消息含子表中文名与条数
- AT-134-6 其它企业的引用**不得**导致误杀
- 🔴 **守卫自证**：`delete` 上的 `@ReferenceGuard` 被摘掉即红

**✅ 反证已做且成立**：摘除 `@ReferenceGuard` 注解块 ⇒ **4 例转红**（含自证例），还原后 6/6 绿。
⇒ 证明守卫**真在起作用**，而非一组恒绿的断言。

**踩到并登记的坑**：
- 🔴 `pjp.getMethod()` **不存在**于 `ProceedingJoinPoint`，须经 `(MethodSignature) pjp.getSignature()).getMethod()`。
- 🔴 夹具凭语感写 `'2026-01'` ⇒ `period` 是 `varchar(6)` **超长报错**（§4.2 第 14 条的老对手，
  同「凭语感写列名」）。已改 `202601`。**判据：测试夹具优先用 Entity 造数；必须裸 SQL 时先
  `information_schema.columns` 查长度与允许集。**
- ⚠️ **守卫只跑 L2**（真库），L1 默认分组会跳过它 —— 覆盖率门禁不含这 6 例。

**批次 2 前置条件（硬性）**：`t_customer`/`t_vendor`/`t_role`/`t_menu`/`t_user` **各须独立真库实测**
证明「删了会静默丢数据」，其中系统表还须确认有无合法删除场景（D-4）。**不得由 `t_subject` 推定。**

---

---

### 8.5 批次 3 实施结果 + 🔴 D-2 裁定反转（本轮最贵的一条）

**实施 3 处**：
- `MenuServiceImpl#delete` —— `@Ref(t_role_menu)` **+ 显式子树检查**
- `EnterpriseServiceImpl#delete` —— 3 个子表（代理客户关系 / 代理用户企业 / 合同）
- `VoucherTypeServiceImpl` —— **覆写继承来的 `removeById`**，补 `t_voucher`（45 条活引用）

**守卫** `CascadingDeleteGuardRealDBTest` **8 例**，含**负向断言**（授权行数/合同数不变）
与**子树专项用例**（子菜单有授权 ⇒ 父菜单也须拒绝）。
**✅ 反证**：摘除 `MenuServiceImpl` 的 `@ReferenceGuard` ⇒ 1 例转红（自证例）。

#### 🔴 D-2 裁定反转：「引用检查必须带 enterprise_id」是**错的**，且是 fail-open

**原裁定理由**：不带过滤则「企业 B 的数据会让企业 A 的记录永删不掉」⇒ 功能不可用。

**实测推翻**：企业用例「造一条合同引用该企业 ⇒ 删除应被拒」**没有抛异常，守卫直接放行**。
根因：`t_contract.enterprise_id` 存的是**合同所属客户企业**，而检查按**当前上下文企业**过滤
⇒ 新建企业的合同**根本不在过滤范围内** ⇒ 查不到 ⇒ **守卫判定「无引用，可以删」**。

🔴 **这是 fail-open，方向与 fail-closed 完全相反**：过滤掉的那部分引用**被静默漏报**，
守卫会给出**放行**。而漏报的方向永远是「说安全」，比误报危险得多。

**修正**：`Ref#scopedByEnterprise` 默认值 `true` → **`false`（全局检查）**。

**原裁定担心的场景重新审视**：跨租户引用同一条父记录 —— 这种情况**本身就是已损坏的数据**
（正常写入不会跨租户引用），此时**应当阻止删除**。用「读权限」的直觉去套「完整性」，
方向反了。

> **判据沉淀（本轮最贵的一条）**
> 完整性检查的过滤维度必须与「这条引用**是否存在**」一致，
> 而**不是**与「调用者**能否看见**」一致。
> 后者是**读权限**，前者是**存在性** —— 混用必然产生静默漏报。
> ⚠️ 与 `AGENTS §4.5 第 26 条`（emoji 破坏 XML）同型的隐蔽性来源：
> **这类错误在「测试全部通过」时不会暴露**，只有当被过滤掉的引用**恰好存在**时才会显形。

**连带修正测试语义**：批次 1 的 AT-134-6 原文是「其它企业的引用**不得**导致误杀」——
那是**错误的期望**（它固化了我原先的 fail-open 口径）。已改写为
「跨企业的引用**同样必须**阻止删除」，并另加正控「确实无人引用的科目仍可删」。

#### ⚠️ 刻意**不拦**的一处（授权是引用，明细是从属）

`VoucherTemplateServiceImpl#delete` 会 `lineMapper.deleteByTemplateId(id)` 级联删模板行，
实测该 SQL 是 `UPDATE ... SET deleted = 1`（**软删**），且模板明细是**从属关系**（composition）
⇒ **级联正确，不加守卫**。

> **判据：授权（角色→菜单）是「别的实体引用你」⇒ 该拦；
> 明细（模板→行）是「你拥有它」⇒ 不该拦。**
> 两者结构上都是「父表被删时连带处理子表」，但语义相反 ——
> 不加区分地把所有级联都拦掉，会让「删模板」这种正常操作也无法进行。

#### 其余踩坑（同 §8.3 类）

- 🔴 `AbstractMapperTest` **已有 `createMenu()`** ⇒ 自定义同名方法**编译报错**
  （`cannot override`）。正解：**用基类助手**（§4.5 第 12 条本就要求如此，
  它走 Mapper 路径并正确落 `menu_code`；裸 SQL 插 `t_menu` 会同时踩必填列与序列两个坑）。
- 🔴 `t_enterprise` 的列是 `enterprise_name`（**不是 `name`**）且**没有 `enterprise_id` 列**。
- 🔴 跨企业用例必须造**真实凭证行**：`fk_entry_voucher` 是**真外键**且**仍在生效**
  （凭证行是真插入的，不是软删）⇒ 填假 `voucher_id` 会撞外键。
  ⚠️ 讽刺之处：**这恰好证明外键并非完全失效** —— 它在「插入侧」照常工作，
  失效的只是「父行被软删时的级联约束检查」这一路径。§0.2 的表述据此校准为
  「外键的**删除级联**保护归零」，而非「外键全部无效」。

### 8.6 批次 4（结构守卫 G7）待办

尚未实现「所有引用型 DELETE 端点必须有 `@ReferenceGuard`」的**结构性**棘轮。
现状：已手工覆盖 10 个服务（`t_subject`/`t_bank_account`/`t_agency`/`t_user`/`t_role`/
`t_customer`/`t_vendor`/`t_menu`/`t_enterprise`/`t_voucher_type`），
但**无机器校验** ⇒ 下一张表仍可能被漏掉。
⚠️ 本轮已三次证明静态扫描器不可靠（见 §8.7），故 G7 的实现方式需先裁定。

### 8.7 🔴 方法论：静态扫描器在本项目**连续三次产出错误结论**

本轮为「哪些表有删除入口 / 哪些有引用检查」写了 4 版静态扫描脚本，**全部产出错误**：

| 版本 | 方法 | 错误形态 |
|---|---|---|
| 1 | 按 Entity 名全文匹配 Service | `VoucherServiceImpl` 被算到 `t_user` 头上 |
| 2 | 严格按 Mapper 归属 | 实体名剥了 `Entity` 后缀再去比对完整类名 ⇒ **全部匹配落空** |
| 3 | 按 Controller 反查 Service | 方法体窗口串到下��个方法；`mapper2ent` 再次落空 |
| 4 | plpgsql 真查询 | ✅ 唯一正确的口径（DB 里数真实引用行） |

> **判据**：§4.5 第 45 条「必须用门禁自己的口径」在此处的推论是 ——
> **代码结构的「事实」（哪些方法被调用、哪些列有引用）应以运行时/数据库为真值源，
> 不要用文本解析推断。** 静态扫描在**格式规整**时可用，在本项目这种
> 实体命名不统一、Service 注入交叉、Mapper 泛型多种写法的代码库里**不可靠**，
> 且失败形态是「静默产出错误清单」而非报错。
> 🔴 讽刺点：脚本 2 和 3 的失败**都是我自己引入的 bug**，而第 1 版的错误清单
> 我差点直接写进 SPEC —— 若如此，会得到一份**看似严谨、实则全错**的选表依据。

---

### 8.6 批次 4 实施结果：结构守卫 G7

**守卫** `ReferenceGuardStructureRealDBTest` **4 例**（L2 真库）：

| ID | 断言 | 真值源 |
|---|---|---|
| G7-1 | 全部 DELETE 端点都已登记（新增端点未登记 ⇒ 红） | **Spring 运行时** `RequestMappingHandlerMapping` |
| G7-2 | 必须守卫的 10 个 Service 实现类都带 `@ReferenceGuard` | Spring 上下文 + 反射 |
| G7-3 | 每个守卫的 `parent` 是真实存在且**有入向外键**的表 | **数据库** `pg_constraint` |
| G7-4 | 登记表与必需清单的规模棘轮（35 / 10） | 常量 |

**✅ 反证**：摘除 `MenuServiceImpl` 的 `@ReferenceGuard` ⇒ **G7-2 转红**，还原后 4/4 绿。

**✅ 守卫当场抓到一个真实漏项**：G7-1 首次运行报「实际 35 个 DELETE 端点，登记表 33」
⇒ 漏登记了 `AssignmentController`（撤销派工）与 `TaxController` 的第二个端点
（销项发票删除）。**这两个是此前 4 版静态扫描器都没找出来的** ——
静态扫描把 `TaxController` 的两个端点当成一个，而完全没看见 `AssignmentController`。

#### 🔴 实现过程中踩的 4 个坑，全部是「错误信息指错方向」

1. 🔴 **裸 `@SpringBootTest` 拿到的是 H2 内存库** ⇒ 查 `pg_constraint` 报
   `Table "PG_CONSTRAINT" not found (this database is empty)`，
   看起来像「SQL 写错了」，实则是**连错数据库**。
   正解：继承 `AbstractMapperTest` 走 Testcontainers。
   **判据：报「某个系统表/方言不存在」时，先确认连的是不是目标数据库。**
2. 🔴 `ctx.getBean("AgencyService")` 抛 `NoSuchBeanDefinition` ——
   **字符串是 bean 名（`agencyServiceImpl`），不是类型名**。
3. 🔴 `Class.forName("AgencyServiceImpl")` 缺包名，且实现类分散 **9 个包**；
   有接口**根本没有 Impl 类**（`AttachmentService`）。硬编码包列表每加一个 Service 就要改。
   正解：**遍历上下文里所有 bean，按目标类简名匹配**。
   **判据：定位「某个类」时优先用已经在运行的容器作索引，而不是靠名字反推坐标。**
4. 🔴 `RequestMethod` 没有 `getName()`（是 `name()`）。

#### ⚠️ 刻意砍掉的能力（不掩盖）

本守卫**不做**「Controller → Service 方法」的自动解析，
因而**不能**自动回答「某个新加的删除端点，其目标表是否需要守卫」。
`REQUIRED_GUARDED_SERVICES` 是**按实测证据人工维护**的清单。

**理由**：§8.7 记录的 4 版扫描器里，**有 2 版正是卡在这一步**（实现类分散、有接口无 Impl）。
宁可用一份**人工核实但稳定**的清单，也不用一个**自动但经常算错**的解析器 ——
后者一旦算错，守卫会给出**误导性的绿灯**。

能自动检出的部分是：端点漏登记、守卫指向不存在的表、守卫被摘掉、清单被悄悄改动。


### 8.6 批次 4（结构守卫 G7）待办

尚未实现「所有引用型 DELETE 端点必须有 `@ReferenceGuard`」的**结构性**棘轮。
现状：已手工覆盖 10 个服务（`t_subject`/`t_bank_account`/`t_agency`/`t_user`/`t_role`/
`t_customer`/`t_vendor`/`t_menu`/`t_enterprise`/`t_voucher_type`），
但**无机器校验** ⇒ 下一张表仍可能被漏掉。
⚠️ 本轮已三次证明静态扫描器不可靠（见 §8.7），故 G7 的实现方式需先裁定。

### 8.7 🔴 方法论：静态扫描器在本项目**连续三次产出错误结论**

本轮为「哪些表有删除入口 / 哪些有引用检查」写了 4 版静态扫描脚本，**全部产出错误**：

| 版本 | 方法 | 错误形态 |
|---|---|---|
| 1 | 按 Entity 名全文匹配 Service | `VoucherServiceImpl` 被算到 `t_user` 头上 |
| 2 | 严格按 Mapper 归属 | 实体名剥了 `Entity` 后缀再去比对完整类名 ⇒ **全部匹配落空** |
| 3 | 按 Controller 反查 Service | 方法体窗口串到下��个方法；`mapper2ent` 再次落空 |
| 4 | plpgsql 真查询 | ✅ 唯一正确的口径（DB 里数真实引用行） |

> **判据**：§4.5 第 45 条「必须用门禁自己的口径」在此处的推论是 ——
> **代码结构的「事实」（哪些方法被调用、哪些列有引用）应以运行时/数据库为真值源，
> 不要用文本解析推断。** 静态扫描在**格式规整**时可用，在本项目这种
> 实体命名不统一、Service 注入交叉、Mapper 泛型多种写法的代码库里**不可靠**，
> 且失败形态是「静默产出错误清单」而非报错。
> 🔴 讽刺点：脚本 2 和 3 的失败**都是我自己引入的 bug**，而第 1 版的错误清单
> 我差点直接写进 SPEC —— 若如此，会得到一份**看似严谨、实则全错**的选表依据。

---

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: ReferenceGuard
module: common
table: null

rules:
  - id: C-01
    source: "P134 §0.1/§0.2"
    rule: "逻辑删除使外键约束永不触发，删除被引用的父行必须由应用层拒绝"
    mapping: { mechanism: ReferenceGuardAspect, check: child_count_before_soft_delete }
    implementation: "@ReferenceGuard 注解 + ReferenceGuardAspect"
  - id: C-02
    source: "P134 §2.2"
    rule: "被引用时返回 HTTP 400，业务消息须指名子表与条数，且不回显库结构细节"
    mapping: { http: 400, payload: [child_label, count] }
    implementation: "ReferenceGuardAspect + GlobalExceptionHandler(BusinessException)"
  - id: C-03
    source: "P134 §5"
    rule: "子表查询失败或守卫异常时必须 fail-loud，不得静默跳过"
    mapping: { on_error: throw, forbid_silent_skip: true }
    implementation: "ReferenceGuardAspect"
  - id: C-04
    source: "P134 §6 批次4 / §2.3 AT-134-7"
    rule: "所有可软删且有入向外键且有 DELETE 端点的表必须标注 @ReferenceGuard，缺口以棘轮记录"
    mapping: { guard: structural_ratchet, forbid_unregistered: true }
    implementation: "ReferenceGuardStructureTest"

acceptance_tests:
  - id: AT-134-1
    given: "科目被 45 条凭证分录引用"
    when: "调用 DELETE /api/v1/subjects/{id}"
    then: "抛 BusinessException(400) 且科目 deleted 仍为 0"
  - id: AT-134-2
    given: "删除被拒后"
    when: "复查数据库"
    then: "科目 deleted=0 且引用它的分录数不变（负向断言）"
  - id: AT-134-3
    given: "科目无任何引用"
    when: "调用 DELETE"
    then: "删除成功，deleted=1"
  - id: AT-134-4
    given: "科目被引用"
    when: "捕获异常消息"
    then: "消息含子表中文名与条数"
  - id: AT-134-5
    given: "科目同时被两类数据引用"
    when: "删除"
    then: "消息同时列出两类引用方"
  - id: AT-134-6
    given: "企业 A 的科目被企业 B 的数据引用"
    when: "删除企业 A 的科目"
    then: "不因企业 B 的引用而被拒（引用检查须带 enterprise_id）"
  - id: AT-134-7
    given: "扫描全仓"
    when: "存在可软删+有入向FK+有DELETE端点但未标注的表"
    then: "结构守卫转红"
