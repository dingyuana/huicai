# SPEC-P128：凭证类型租户化 —— 消除硬编码全局 id 与「凭证类型不存在」误报

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已审核通过**（2026-10-14，老丁）。**Redis 键裁定**：改用 `code` 组键（见 §6） |
| REQ | REQ-2026-144 |
| 前序 | 无（独立立项）；与用户提出的「新建企业引导配置主数据」需求同源 |
| 关联 | AGENTS §4.5 第 43 条（豁免过宽 / 概念混用）、第 8 条（注释与实际不符）、§4.2 第 18 条（改引用必须同步验种子）、§4.5 第 46 条（新增代码零覆盖） |
| 症状 | 山东CCCCCCCCCC公司（企业 4）批量审核银行流水 **19/19 全部失败**，原因「凭证类型不存在」 |

---

## §0 根因

### 0.1 直接原因

`t_voucher_type` 是**租户表**，但代码把它当**全局字典表**用。

```
t_voucher_type:  ENABLE + FORCE ROW LEVEL SECURITY
                 策略 enterprise_policy: enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint
```

而 `VoucherType.java` 的类注释把 id 当作**全局常量**：

```java
/**
 * 凭证类型常量，与 t_voucher_type 表 ID 对应。
 *   id = 1 → JZ (记账凭证)   id = 2 → SK (收款凭证)
 *   id = 3 → FK (付款凭证)   id = 4 → ZZ (转账凭证)
 */
public static final long JZ = 1L;  // SK = 2L; FK = 3L; ZZ = 4L
```

银行流水审核这条链路（`BankJournalServiceImpl:154`）：

```java
voucher.setVoucherNo(voucherNoService.generateNextNo(journal.getPeriod(), 1L));
```

`VoucherNoServiceImpl:36-39` 随即 `voucherTypeService.getById(1L)` → 被 RLS 按当前企业过滤 → 返回 `null` → `throw BusinessException.notFound("凭证类型不存在")`。

### 0.2 库内实际状态（已实测）

| `enterprise_id` | 凭证类型数 | 说明 |
|---|---|---|
| 0 | 4 | **死数据** —— RLS 谓词永不等于 0，任何上下文都读不到 |
| 1（默认企业） | 4 | 唯一可用 |
| 2 / 3 / 4 | **0** | 青岛AAAA、青岛BBBB、山东CCCC |

### 0.3 影响面远大于报告的那一家

🔴 **企业 2、3 同样 100% 失败**，只有「默认企业」(id=1) 能跑通。**这正是该缺陷长期未被发现的原因** —— 开发与演示全程在企业 1。

⚠️ **两个缺陷叠加，单修任一个都不够**：即便给企业 4 补种凭证类型，identity 序列也不会让它的行 id 恰好等于 1 ⇒ **代码侧的硬编码必须改**。

### 0.4 顺带查出第二个缺陷（值与注释不符）

`InputInvoiceStateMachineServiceImpl:61`：

```java
private static final long VOUCHER_TYPE_ID = 2L; // FK 付款凭证
```

`2L` 是 **SK 收款凭证**，`FK` 实为 `3L` ⇒ **进项发票审核/红冲用的是「收款凭证」而非「付款凭证」**。二者必居其一为错：值错则制证类型错，注释错则后人照注释改值会再错一次。本 SPEC 裁定按 `code` 取，**该歧义自动消解**（见 §7.7 明确排除：不单独裁定哪边是笔误，只保证不再错）。

### 0.5 判据沉淀

🔴 **「主数据表加了 `enterprise_id` + RLS」与「代码仍按全局字典用」是两类独立变更，合并时必炸**。前者上线时全部功能正常（企业 1 有种子），后者也一直正常（企业 1 的 id 恰好是 1）—— **两者各自正确，合起来才错**。

**判据：给任何字典/主数据表补租户列时，必须同时 grep「硬编码该表主键」与「按业务键查询」，前者是爆炸半径，后者才是正解。**

---

## §1 方案：按 code 取本企业凭证类型 + 按企业补种子

### ① 新增按 code 取值的能力（根治）

`VoucherTypeService` 新增：

```java
/**
 * 取当前企业的指定 code 凭证类型；缺失时抛出「可操作」的异常。
 * @param code 业务编码 JZ/SK/FK/ZZ，非主键 id
 */
VoucherTypeEntity requireByCode(String code);
```

实现要点：
- 走 MyBatis-Plus 查询，**由 RLS + 应用层拦截器自动收敛到当前企业**（该表不在 `SHARED_TABLES` 白名单，已核实）；
- 缺失时抛 `BusinessException`，文案必须**回答「去哪配」**（§4.5 第 50 条）：
  > `当前企业未配置「记账凭证(JZ)」，请先到【基础资料 → 凭证类型】新增后再制证`

### ② 19 处硬编码 id 全部改为按 code 取

| 现状 | 改为 |
|---|---|
| `1L` / `VoucherType.JZ` | `requireByCode("JZ")` |
| `VoucherType.SK` / `2L` | `requireByCode("SK")` |
| `VoucherType.FK` / `3L` | `requireByCode("FK")` |
| `VoucherType.ZZ` / `4L` | `requireByCode("ZZ")` |

**波及面（实施前须逐点确认，见 §6）** —— ⚠️ **2026-10-14 守卫实测更正为 20 处调用点**（原估 19 漏了 `AutoGenerationService:603`），扣除 §7 登记的 2 处排除项（`VoucherServiceImpl:159/477`）= **18 处待改**：

`VoucherServiceImpl`(2，**均为排除项**)、`TaxServiceImpl`(4)、`InputInvoiceStateMachineServiceImpl`(2)、`InputInvoiceImportService`(1)、`SalesInvoiceImportService`(1)、`PurchaseReturnServiceImpl`(1)、`ReconciliationServiceImpl`(1)、`ArapSettlementServiceImpl`(1)、`BusinessDocServiceImpl`(3)、`PrepaymentServiceImpl`(2)、`BankJournalServiceImpl`(1)、`AutoGenerationService`(1)。

`VoucherServiceImpl:159/477` 取的是 `dto.getVoucherTypeId()` / `original.getVoucherTypeId()`（**前端传入或库中已有**），**不在本批改动范围** —— 但须核实入库的 `voucher_type_id` 是否也指向全局 id（登记为 §7 已知局限）。

⚠️ **`ReconciliationServiceImpl:502` / `ArapSettlementServiceImpl:382` / `AutoGenerationService:603` 传的是局部变量 `voucherTypeId`** —— 实施前须逐个追其来源，确认它同样是硬编码而非按 code 解析，否则改错层级。

### ③ 按企业补种子（Flyway）

- 为企业 2/3/4 各补 4 条 `JZ/SK/FK/ZZ`；
- **清理 `enterprise_id = 0` 的 4 条死数据**（逻辑删除，不物理删 —— 铁律 #12）；
- ⚠️ **新企业仍需人工/引导配置**：种子只覆盖存量。后续新建企业的凭证类型缺失由用户提出的「新建企业引导」需求承接（另行立项，见 §7.7）。

### ④ 结构守卫（防回归）

新增 `VoucherTypeHardcodeStructureTest`：
- 扫描 `com.huicai` 下全部 `generateNextNo(...)` 调用点；
- **禁止字面量与 `VoucherType.*` 常量作为第二参数**，必须传 `requireByCode(...)` 的结果；
- **含守卫自身的反证**（字符串注入验证规则能命中违规、不误报合法写法），且注入点必须在断言之前（§4.5 第 41 条）。

---

## §2 输入 / 输出契约

### 2.1 无端点签名变更

本 SPEC **不改任何 Controller 端点签名**。变更仅在 Service 内部（id → code 查表）。

### 2.2 `GET /api/v1/voucher-types`（行为变更，非签名变更）

现状：返回**当前企业**的凭证类型。企业 2/3/4 当前恒返回空数组。
变更后：随 §1③ 种子补齐，返回各自 4 条。

---

## §3 状态流转

**不涉及**。凭证类型为静态主数据，无状态机语义。

---

## §4 异常处理

| 场景 | 现状 | 变更后 |
|---|---|---|
| 本企业未配置该 code | `凭证类型不存在`（误导：暗示 id 错） | `当前企业未配置「记账凭证(JZ)」，请先到【基础资料 → 凭证类型】新增后再制证` |
| 凭证类型行存在但被 RLS 过滤 | 同上（无法区分） | 同上（**本质同构，仍不可区分，见 §7 局限**） |
| `getById` 传入他人企业的 id | 同上 | 同上（`requireByCode` 天然免疫，故本 SPEC 消除该入口） |

🔴 **判据**：错误文案必须回答「**为什么失败 + 下一步去哪做**」。「凭证类型不存在」两问皆不答（§4.5 第 50 条同型）。

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-128-1 | 上下文企业 = 4（山东CCCCCCCCCC公司），该企业已种 4 条凭证类型 | 对银行流水执行批量审核 | **19 条全部成功**，生成凭证号前缀 `JZ`；无「凭证类型不存在」 |
| AT-128-2 | 上下文企业 = 2 / 3 | 同上 | 同样全部成功（证明非企业 1 特例） |
| AT-128-3 | 某企业**未**配置 `SK` 凭证类型 | 触发需 `SK` 的制证路径（如销项发票导入） | 抛 `BusinessException`，文案含 `SK`/`收款凭证` 与 `凭证类型` 配置指引；**不得**为 `凭证类型不存在` |
| AT-128-4 | 上下文企业 = 1 | 对既有单据制证 | 凭证号与改动前**逐字一致**（`JZ` + period + 4 位流水，Redis 键不变） |
| AT-128-5 | 任意企业 | `SELECT * FROM t_voucher_type WHERE enterprise_id = 0` | 全部 `deleted = 1`（死数据已逻辑删除） |
| AT-128-6 | 全仓扫描 | `generateNextNo(` 调用点 | 无字面量 / `VoucherType.*` 作为第二参数（守卫断言，含自证反证） |

⚠️ **AT-128-4 是防倒退的关键**：本改动触碰 19 处制证路径，若凭证号格式或 Redis 键变化，会造成**已存在凭证的编号断层**。Redis 键 `voucher:no:{period}:{voucherTypeId}` 中 `voucherTypeId` 由 1 变为**每企业不同的实际 id** ⇒ **键必然变化** ⇒ 须在 §6 明确处置（见下）。

---

## §6 实施计划与回归范围

| 批次 | 内容 | 回归 |
|---|---|---|
| 1 | `requireByCode` + 结构守卫（**先立守卫，TDD-Red**） | 守卫红 |
| 2 | Flyway 补种子 + 清理死数据 | L2 真库：各企业 4 条 |
| 3 | 逐模块替换 19 处调用点 | 每替换一处跑该模块 L1 + L2 |
| 4 | 全量回归 | **L1 `mvn clean test` + L2 `mvn test -DexcludedGroups=` 零回归**（须先 `docker start huicai-redis`，§4.5 第 24 条） |

### ✅ Redis 凭证号键漂移（老丁已裁定 2026-10-14：改用 `code` 组键）

Redis 键原含 `voucherTypeId`：`voucher:no:{period}:{voucherTypeId}`。企业 2 补种子后其 `JZ` 实 id 是 **5**（非 1）⇒ 键从 `voucher:no:202601:1` 变为 `voucher:no:202601:5`。

- 键变化本身**不产生错号**（键内计数会从 DB 最大号重新初始化，见 `VoucherNoServiceImpl:44-57`）；
- 但旧键残留与新键并存时，两者计数互不可见 ⇒ **存在重号窗口**。

**裁定：键改用 `code` 组成，与主键 id 解耦** ——

```
旧：voucher:no:{period}:{voucherTypeId}      ← 主键随企业而变，跨企业不一致
新：voucher:no:{period}:{enterpriseId}:{code}  ← 语义自明，且天然按企业分段
```

**收益**：① 键不再依赖主键，企业增删凭证类型不影响编号连续性；② 键自带企业维度，肉眼即可判断编号归属；③ 与 §1①「按 code 解析」口径统一，全链路只有 `code` 一个业务概念。

⚠️ **迁移期须一次性清理旧键**：`redis-cli --scan --pattern 'voucher:no:*'` 列出后删除，避免新旧键并存产生重号。清理前须确认 `SELECT max(voucher_no) FROM t_voucher` 已覆盖该企业该期间最大号（删除后重建时会以 DB 最大号初始化，不会从头编号）。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不裁定 §0.4 的「值 vs 注释」谁是笔误（改为按 code 取后歧义自动消解）
- ❌ 不改 `VoucherServiceImpl:159/477` 的 `dto.getVoucherTypeId()` 路径（前端传入，另行核实现存数据是否指向全局 id）
- ❌ 不做「新建企业引导配置主数据」需求本体（用户已提出，**建议作为 P129 承接**，与本 SPEC 共用 `requireByCode` 的错误文案）
- ❌ 不改 `t_voucher_type` 的 RLS 策略（策略本身正确，错的是代码假设）
- ❌ 不物理删除 `enterprise_id = 0` 的死数据（铁律 #12）

**已知局限（须在 SPEC 内留痕，不得假装已解决）**：
1. 「行存在但被 RLS 过滤」与「行不存在」的错误文案**仍不可区分** —— 两者对用户都是「未配置」，指引相同，可接受。
2. 存量企业 2/3/4 的种子补齐后，**将来新建的企业仍会缺凭证类型**，仍需 P129 引导。
3. 各企业 `JZ` 的实际 id 不同（企业 1 是 1，企业 2 是 5…），**任何仍按 id 判断凭证类型的旧代码/旧脚本仍是隐患**，本 SPEC 仅保证 `generateNextNo` 入口不再硬编码。

---

## §7.1 2026-10-18 实施补记：§7.3 遗留已收口 + 查出新的 fail-open 缺口

### 已修复：§7.3 的「仍按 id 判断凭证类型」

`VoucherServiceImpl#validateVoucherTypeLimit` 原按全局常量分支：

```java
if (voucherTypeId == VoucherType.SK && !hasCashBankInDebit) throw ...
if (voucherTypeId == VoucherType.FK && !hasCashBankInCredit) throw ...
if (voucherTypeId == VoucherType.ZZ && (hasCashBankInDebit || hasCashBankInCredit)) throw ...
```

🔴 **在企业 2/3/4 上一次都不触发**（其主键为 5~8，与常量 1~4 永不相等）⇒
收款凭证不挂现金科目、转账凭证塞现金科目，**都不拦**。企业 1 恰好正常，长期不可见。

修法：`requireVoucherTypeOfCurrentEnterprise` 改为返回 `VoucherTypeEntity`，
`validateVoucherTypeLimit` 按 `getCode()` 分支（归一化 `trim().toUpperCase()`，与 `requireByCode` 同口径）。

**守卫扩面**：`VoucherTypeHardcodeStructureTest` 原只扫 `generateNextNo` 第二参数与 `setVoucherTypeId` 实参，
第三形态「拿常量与 id 比对」**完全不在扫描范围内**（§4.5 第 31 条复发）。已新增
`noVoucherTypeConstantComparisonAnywhere`，剥离注释与字符串后全仓扫描，并自证：命中 4 个常量、
跳过定义文件、**不误报注释里对历史笔误的说明**。

**真库行为测试** `VoucherTypeLimitByCodeRealDBTest`（9 例）——
🔴 结构守卫只能证明「不再引用常量」，**不能证明改完校验真会触发**
（例如 code 归一化写错，守卫照样全绿而校验恒不触发），故必须走 `VoucherService#create` 真实链路。
含 3 条「自证前提」用例：①本企业四种主键均 ≠ 1/2/3/4 ②现金银行科目集合非空 ③借方科目不属于现金银行。
**反证**：注入回按 id 比对 ⇒ 恰好 3 条「应被拒」用例转红（`nothing was thrown`），正向对照仍绿。

### 🔴 新缺口（未修，登记待立项）：无现金/银行科目 ⇒ 凭证类型限制整体 fail-open

`validateVoucherTypeLimit` 开头：

```java
List<String> cashBankCodes = subjectMapper.getCashBankCodes();
if (cashBankCodes.isEmpty()) { return; }   // ← 直接跳过全部三条规则
```

🔴 **本项目自建的新企业账套默认没有 1001/1002 科目**（L2 Testcontainers 全新库实测为空集，
且 `getCashBankCodes` 的裸 SQL 会被 `EnterpriseDataPermissionInterceptor` 注入当前企业过滤
⇒ 查不到别家的现金科目）。此时收款/付款/转账凭证的科目限制**一条都不生效**，且无任何日志或提示。

**症状**：新账套下制凭证永远不会被科目规则拦住，界面与日志均无异常 ⇒ 静默失效。

**未在本 SPEC 修的理由**：改 fail-open 为 fail-closed 会让「尚在建科目体系的新账套」**完全无法制证**，
属业务策略变更，须老丁裁定。可选方向：①改为「无现金科目时告警但不拦」②保持现状但在凭证类型配置页提示
③建账引导期强制要求至少一个现金/银行科目（与 P129 建档引导合并）。

**复现方式**：`VoucherTypeLimitByCodeRealDBTest#premiseCashBankCodesNotEmpty` 自建企业（不建 1001 科目）即红。

### 同批修复：P135 遗留的接口覆盖门禁回归

`/api/ai/quota`（P135 配额查询端点）前端从未接入 ⇒ 接口覆盖棘轮由 92 升至 **93** 越限。
已接前端并在 AI 任务页展示配额（`AiTaskList.vue` 顶部 tag，剩余 <20% 转 warning，用尽时 tooltip
说明「何时恢复 + 联系管理员提升」—— §4.5 第 50 条：禁用/限流必须同时给出路）。棘轮回到 92（余量 0）。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: VoucherTypeEntity
module: base.voucher
table: t_voucher_type

# 本 SPEC 不涉及业务状态机（凭证类型为静态主数据），契约以「规则型」表达。
rules:
  - id: C-01
    source: "P128 §1①"
    rule: "凭证类型必须按业务编码 code 在当前企业范围内解析，禁止以全局主键 id 解析"
    mapping: { lookup: by_code, scope: current_enterprise, not: global_id }
    implementation: "VoucherTypeService.requireByCode"
  - id: C-02
    source: "P128 §4"
    rule: "本企业缺少指定 code 凭证类型时，异常文案须包含 code、中文名与配置指引，不得为「凭证类型不存在」"
    mapping: { error_code: VOUCHER_TYPE_NOT_CONFIGURED, message_must_contain: [code, 中文名, 配置指引] }
    implementation: "VoucherTypeService.requireByCode"
  - id: C-03
    source: "P128 §1③"
    rule: "每个 ACTIVE 企业须具备 JZ/SK/FK/ZZ 四条凭证类型；enterprise_id = 0 的历史行为逻辑删除"
    mapping: { per_enterprise: [JZ, SK, FK, ZZ], legacy_zero_rows: deleted_equals_1 }
    implementation: "Flyway migration (V1xx)"
  - id: C-04
    source: "P128 §1④ / AT-128-6"
    rule: "generateNextNo 的第二参数禁止为字面量或 VoucherType.* 常量，必须来自 requireByCode"
    mapping: { guard: VoucherTypeHardcodeStructureTest, forbids: [numeric_literal, VoucherType_constant] }
    implementation: "VoucherTypeHardcodeStructureTest"
  - id: C-05
    source: "P128 §1①"
    rule: "凭证类型查询不得加入 SHARED_TABLES 白名单（保持租户隔离）"
    mapping: { table: t_voucher_type, shared_tables_contains: false, rls: enterprise_policy }
    implementation: "EnterpriseDataPermissionInterceptor"
