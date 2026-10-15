# SPEC-P129：企业建档补全联系信息 + 建档后引导配置银行账号 + 银行账号按企业选择

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已审核通过**（2026-10-14，老丁）。**D-1 裁定：手机号/邮箱对非管理员脱敏**（见 §4.1） |
| REQ | REQ-2026-145 |
| 前序 | 无；与 **SPEC-P128**（凭证类型租户化）同源 |
| 关联 | AGENTS §4.5 第 50 条（禁用/中断必须给出路）、第 43 条（豁免理由须逐路径）、§4.2 第 13 条（NOT NULL 列在 Entity 缺失 ⇒ 写入必挂）、§4.5 第 34 条（Entity 无 fill ⇒ enterprise_id 落默认值） |
| 症状 | 用户报障：新增企业只有编码/名称/税号，无地址联系人电话；建完不知道下一步配银行账号；银行账号页无法按企业选择 |

---

## §0 取证（全部实测，非推断）

### 0.1 `t_enterprise` 无任何联系信息列

```
id, enterprise_code, enterprise_name, tax_id, mode, agency_id,
status, seed_data_done, start_period, created_by/at, updated_by/at, deleted, version
```

⇒ **地址、联系人、电话均无列**，`EnterpriseCreateDTO` 亦只有 `enterpriseCode/enterpriseName/taxId/agencyId`。

⚠️ 这是与 §4.2 第 13 条**同型但方向相反**的一类：那次是 DB 有 `NOT NULL` 列而 Entity 缺字段；这次是**业务需要而 DB/Entity/DTO 三处都没有**。

### 0.2 银行账号页的企业维度是「只读展示」，不可选

`frontend/src/views/finance/bank-account/BankAccountList.vue`：

```
prop="enterpriseName"          →  表格只读列
{{ row.enterpriseName || '—' }}
```

**无企业筛选下拉、无指定企业的入口**。而 `6898ba34` 刚落地「系统管理员跨企业总览」⇒ 管理员能一次看到全部企业的账号，**却无法按企业收敛或为某企业建账号**，与该提交的能力不匹配。

### 0.3 与 P128 同源：新建企业缺主数据

P128 的根因是「新企业没有凭证类型 ⇒ 制证报『凭证类型不存在』」。本 SPEC 处理的是**同一个家族**的另两个成员（银行账号、联系信息）。

🔴 **判据沉淀**：新建企业后的「**主数据就绪度**」目前无人负责 —— 银行账号、凭证类型、联系信息三项全靠人工想起来去配，缺了就报一句技术性错误。**根治方向是「建档 → 就绪度检查 → 逐项引导」，而不是各处补空判断。**

---

## §1 方案

### ① 补联系信息（DDL + Entity + DTO + 前端，三方对照）

新增列（**均可空**，不阻塞建档 —— 建档是高频轻操作，强制填全会把门槛提到不合理的高度）：

| 列 | 类型 | 说明 |
|---|---|---|
| `contact_name` | `varchar(64)` | 联系人 |
| `contact_phone` | `varchar(32)` | 电话（手机或带区号固话） |
| `contact_email` | `varchar(128)` | 邮箱 |
| `address` | `varchar(255)` | 地址 |

⚠️ **类型裁定须查证**（§4.5 第 53 条：批量 DDL 前先看类型分布）：`contact_phone` 用 `varchar` 而非 `bigint`，因为固话含区号/连字符；邮箱同理。

**三方对照清单（§7 铁律 5）**：`V1xx` 迁移 → `EnterpriseEntity` 字段 → `EnterpriseCreateDTO` + 更新入参 → 前端表单 → `EnterpriseVO` 出参。**任一端遗漏都会在运行期才暴露。**

### ② 建档后主动引导进银行账号设置（AGENTS §4.5 第 50 条的正面用法）

创建成功后**不静默返回列表**，而是弹出结果面板：

- 标题：「企业 X 创建成功」；
- **就绪度清单**（逐项显示状态，不隐藏）：
  - ✅ 企业基本信息 —— 已完成
  - ⚠️ 银行账号 —— **未配置**，影响收款单/付款单/银行对账单
  - ⚠️ 凭证类型 —— 由 P128 承接，**未配置时影响制证**
- **可点击的主行动按钮**：「去配置银行账号」→ 跳转 `/finance/bank-account?enterpriseId={新企业id}`；
- 次要按钮：「稍后再说」。

🔴 **判据**：这里必须回答「**为什么现在要配**」与「**点了会发生什么**」。仅弹一句「创建成功」再让人自己找入口，等于把「找不到」换成「不知道为什么要找」（第 50 条同型）。

⚠️ **诚实限定**：就绪度清单里「凭证类型」一项，**在 P128 未实施前无法可靠判定**（当前是硬编码 id，企业 2/3/4 恒缺）。本 SPEC 只**展示该项并标注「由 P128 承接」**，**不做真实探测**，避免给出一个查不出来的假状态。

### ③ 银行账号页支持按企业选择

`BankAccountList.vue` 顶部新增**企业下拉**：

- **数据源**：当前用户可见企业（代理端取派工客户，自有用户取归属 ∪ 派工，与 `EnterpriseController#page` 的可见集口径一致 —— ⚠️ 可见性 ≠ 归属，§4.5 第 54 条已踩过）；
- **默认值**：当前工作企业；
- **联动**：切换后列表按 `enterprise_id` 过滤；**「新增」弹窗的企业归属固定为下拉当前选中值**，不允许在弹窗里再选一次（避免两处口径打架）。

⚠️ **必须回答「为什么这里能选企业」**：普通用户下拉**只有一项**（其当前企业）⇒ 表现为不可改；**只有跨企业管理员/代理端**才有多项。判据同第 50 条：给出路的同时要说清何时无路。

---

## §2 输入 / 输出契约

### 2.1 `POST /api/v1/enterprise`（请求体新增 4 个可选字段）

```jsonc
{
  "enterpriseCode": "ENT002",
  "enterpriseName": "青岛示例公司",
  "taxId": "91370200XXX",
  "agencyId": 1,
  // 新增（均可选）
  "contactName": "张三",
  "contactPhone": "0532-88888888",
  "contactEmail": "zhangsan@example.com",
  "address": "山东省青岛市市南区XX路1号"
}
```

**破坏性**：无。全部为可选新增，旧调用方不受影响。

### 2.2 `GET /api/v1/enterprise/{id}` / `page`（响应新增 4 字段）

```jsonc
{ "contactName": "张三", "contactPhone": "...", "contactEmail": "...", "address": "..." }
```

⚠️ **PII 脱敏（老丁 2026-10-14 裁定：D-1 = 脱敏）**：手机号/邮箱属个人信息，**对非管理员一律脱敏**。

| 字段 | 脱敏规则 | 例 |
|---|---|---|
| `contactPhone` | 保留前 3 后 4 | `138****5678` |
| `contactEmail` | 保留首字符 + 完整域名 | `z***@example.com` |

**可见全值的角色**：`SUPER_ADMIN` / `AGENCY_ADMIN`。其余角色（`ACCOUNTANT`/`REVIEWER`/`ASSISTANT`）一律返回脱敏值。

🔴 **脱敏必须在服务端做，且按「角色」而非「是否有值」判定** —— 前端脱敏等于把明文已经发到了浏览器，脱敏形同虚设。判定须复用**登录态角色**（`SecurityUtils` 现有取值），不得新增第二套角色判定（与 P115「单一事实源」同源要求）。

⚠️ **不得用「已登录就脱敏、点按钮再显示明文」做二次解锁** —— 那等于给 PII 加了一个取件码。若业务确需明文，应另立需求并单独评审。

### 2.3 `GET /api/v1/bank-accounts`（新增可选查询参数）

```
?enterpriseId={id}     // 不传 = 当前工作企业（保持既有默认行为不变）
```

**破坏性**：无（新增可选参数）。

---

## §3 状态流转

**企业状态机不变**（`PENDING/ACTIVE/SUSPENDED/TERMINATED`，由 P113 `EnterpriseWriteGuard` 把关）。

本 SPEC **不新增状态**。就绪度是**派生事实**（由「有无银行账号」计算），**不得固化为列或状态** —— 否则会与实际配置漂移（这正是 P128 里 `enterprise_id=0` 死数据的成因）。

---

## §4 异常处理

| 场景 | 处理 |
|---|---|
| 联系信息全为空 | **允许建档**（均可选），就绪度面板不因此报红 |
| `contactPhone` 格式非法 | 前端软校验提示，**不阻断建档**（避免为格式卡住主流程） |
| 引导跳转时目标企业已不可见 | 银行账号页按「当前可见集」过滤，下拉自动回落到当前工作企业，并提示「目标企业不在可见范围」 |
| 银行账号页下拉只有一项 | 展示但置灰 + tooltip「当前角色仅可见 1 个企业」 |
| 建档成功但跳转失败 | 结果面板独立存在，不依赖跳转成功 |

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-129-1 | 代理端管理员在 `/agency/enterprise-list` | 填写编码/名称/税号 + 地址/联系人/电话后创建 | 创建成功；`t_enterprise` 对应行 4 列均落库且值一致（非仅前端显示） |
| AT-129-2 | 同上，但**不填**任何联系信息 | 创建 | **仍创建成功**（可空是硬要求） |
| AT-129-3 | 同上 | 创建成功 | 弹出结果面板，含「企业基本信息 ✅ / 银行账号 ⚠️ 未配置 / 凭证类型 ⚠️ 由 P128 承接」三项 |
| AT-129-4 | 结果面板已显示 | 点击「去配置银行账号」 | 跳转 `/finance/bank-account?enterpriseId={新id}`，且**企业下拉已选中该企业** |
| AT-129-5 | 系统管理员（可见 4 个企业）在银行账号页 | 打开企业下拉 | 列出其可见的 4 个企业；默认选中当前工作企业 |
| AT-129-6 | 下拉已选中企业 A | 点「新增」并提交 | 新账号 `enterprise_id` = A；**弹窗内不再提供第二个企业选择器** |
| AT-129-7 | 下拉已选中企业 A | 切换到企业 B | 列表按 B 过滤，**不出现 A 的账号**（负向断言） |
| AT-129-8 | 普通企业用户（非管理员） | 打开银行账号页 | 下拉仅 1 项且置灰，tooltip 说明原因 |
| AT-129-9 | 已存在账号的企业 | 查询 `GET /bank-accounts`（不传参） | 与改动前结果**完全一致**（默认行为不变，回归锁） |

⚠️ **AT-129-1 必须查库**：§4.2 第 16 条 —— 「前端显示了」不等于「落库了」，Mock 与页面自证都不算数。

---

## §6 实施计划

| 批次 | 内容 | 反证 |
|---|---|---|
| 1 | `V1xx` 迁移补 4 列（幂等 `ADD COLUMN IF NOT EXISTS`） | 删列 ⇒ MP 报 `column does not exist` |
| 2 | `EnterpriseEntity` + `EnterpriseCreateDTO` + `EnterpriseVO` 同步 | 漏 Entity ⇒ 查库断言转红 |
| 3 | 前端表单 4 字段（均可选） | 去掉字段提交 ⇒ 仍创建成功 |
| 4 | 建档结果面板 + 引导跳转 | 去掉 CTA ⇒ AT-129-4 转红 |
| 5 | 银行账号页企业下拉 + 列表过滤 + 新增归属 | 去掉过滤 ⇒ AT-129-7 转红 |

**回归**：L1 `mvn clean test` + L2 `mvn test -DexcludedGroups=`（先 `docker start huicai-redis`）。⚠️ 前端 `MasterDataVoContractTest` 须同步（新增字段可能撞 `NEVER_EXPOSE`）。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不做「就绪度」持久化（派生事实，禁止固化为列/状态）
- ❌ 不改企业状态机（保持 P113 `EnterpriseWriteGuard` 语义）
- ❌ 不做联系信息的**必填校验**（建档是轻操作，强制填全是门槛倒退）
- ❌ 不改银行账号的**归属权限模型**（沿用 `6898ba34` 的跨企业总览 + P120 角色守卫）
- ❌ 不做凭证类型的真实探测（由 P128 承接，见 §1② 诚实限定）

**已知局限**：
1. ~~**PII 脱敏未实施**~~ —— **已裁定并纳入本 SPEC 实施范围**（D-1，见 §2.2）；实施时须有真库/接口层断言证明**非管理员拿到的确实是脱敏值**，而非仅前端遮挡。
2. **就绪度仅 2 项** —— 联系信息不计入就绪度（因其为可选），故面板不是完整的「建档向导」。
3. **「银行账号未配置」的判定口径**取自 `t_bank_account` 有无该企业的 `ACTIVE` 账号；若业务上「有账号即已配置」不成立（如仅有停用账号），须在实施时与业务确认。
4. **代理端可见集口径**若与 `EnterpriseController#page` 分歧，须以该控制器为准 —— 本 SPEC 不改其可见集逻辑。
5. **脱敏后无法复制/拨打** —— 会计联系客户时需明文。当前裁定不提供明文解锁（§2.2 已说明理由），若业务反馈不可接受，需**另立需求**评审，不得在前端加「显示明文」按钮绕过。

---

## §4.1 待老丁裁定

| # | 议题 | 裁定（老丁 2026-10-14） |
|---|---|---|
| D-1 | `contactPhone`/`contactEmail` 是否对非管理员脱敏 | ✅ **脱敏** —— 仅 `SUPER_ADMIN`/`AGENCY_ADMIN` 见全值，其余角色脱敏；服务端按角色判定 |
| D-2 | 联系信息是否必填 | ✅ **均可选**（见 §7 排除项） |
| D-3 | 银行账号页企业下拉对普通用户是否可见 | ✅ **可见但置灰 + tooltip**（隐藏会让用户以为功能不存在） |

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: EnterpriseEntity
module: agency.tenant
table: t_enterprise

# 本 SPEC 不涉及业务状态机（企业状态机由 P113 把关，本 SPEC 不新增状态），契约以「规则型」表达。
rules:
  - id: C-01
    source: "P129 §1①"
    rule: "t_enterprise 必须具备 contact_name/contact_phone/contact_email/address 四列，且均可空"
    mapping: { columns: [contact_name, contact_phone, contact_email, address], nullable: true }
    implementation: "Flyway migration (V1xx) + EnterpriseEntity"
  - id: C-02
    source: "P129 §2.1 / §7 排除项"
    rule: "联系信息一律可选，禁止因格式或缺失阻断企业建档"
    mapping: { required: false, blocking_validation: forbidden }
    implementation: "EnterpriseCreateDTO"
  - id: C-03
    source: "P129 §4.1 D-2"
    rule: "contact_phone 用 varchar(32)、contact_email 用 varchar(128)，禁止用 bigint 存电话"
    mapping: { contact_phone: varchar(32), contact_email: varchar(128) }
    implementation: "Flyway migration (V1xx)"
  - id: C-04
    source: "P129 §3"
    rule: "就绪度为派生事实，禁止持久化为列或状态（防与实际配置漂移）"
    mapping: { readiness: derived, forbidden: persisted_column_or_status }
    implementation: "n/a"
  - id: C-05
    source: "P129 §1② / AT-129-4"
    rule: "企业创建成功后必须展示结果面板，且提供可点击入口直达 /finance/bank-account?enterpriseId={id}"
    mapping: { must_show_panel: true, cta_target: /finance/bank-account, cta_param: enterpriseId }
    implementation: "frontend agency enterprise-list"
  - id: C-06
    source: "P129 §1③ / AT-129-6"
    rule: "银行账号的 enterprise_id 固定取自页面企业下拉的当前选中值；新增弹窗内不得再提供第二个企业选择器"
    mapping: { enterprise_source: page_selector, second_selector: forbidden }
    implementation: "BankAccountList.vue + backend bank-account create"
  - id: C-07
    source: "P129 §2.3 / AT-129-9"
    rule: "GET /api/v1/bank-accounts 的 enterpriseId 为可选参数；不传时行为与改动前完全一致"
    mapping: { param_optional: true, default: current_enterprise }
    implementation: "BankAccountController"
  - id: C-08
    source: "P129 §4 / AT-129-8"
    rule: "企业下拉对普通用户可见但置灰，须 tooltip 说明「仅可见 1 个企业」，禁止直接隐藏"
    mapping: { when_single: disabled_with_tooltip, hidden: forbidden }
    implementation: "BankAccountList.vue"
  - id: C-09
    source: "P129 §2.2（D-1 老丁裁定）"
    rule: "contactPhone/contactEmail 属 PII，对非 SUPER_ADMIN/AGENCY_ADMIN 一律服务端脱敏；禁止仅前端遮挡"
    mapping: { full_visibility_roles: [SUPER_ADMIN, AGENCY_ADMIN], masking: server_side, frontend_only_masking: forbidden, phone: "前3后4", email: "首字符+完整域名" }
    implementation: "EnterpriseVO + SecurityUtils 登录态角色"
