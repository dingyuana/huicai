# SPEC-P118：审计字段记真实操作人（`DEFAULT_USER_ID` 硬编码收口）

| 项 | 内容 |
|---|---|
| **状态** | ✅ 已实施（V1.0） |
| **关联 REQ** | REQ-2026-142 |
| **前置** | SPEC-P117（`created_by` 从幽灵字段修复为真实落库列） |
| **严重度** | 🟡 中（审计质量缺陷，非功能不可用） |

---

## §1 缺陷描述

P117 把 `created_by` / `updatedBy` 从幽灵字段修复为**真实落库列**后，「写什么值」就成了唯一的质量关口。而在此之前这些赋值**根本不影响任何行为**，所以「随便填个常量」是完全正常的写法。

全仓实测：**7 个文件、34 处** `setCreatedBy(DEFAULT_USER_ID)`。

## §2 为什么必须现在修

修复前这些常量赋值是死代码，无害；**修复后会真实落库**，于是每一处都在审计字段里写下一个**并非实际操作人**的值。

⚠️ 若不在 P117 之后立即收口，就会出现「列修好了、值反而更假了」的倒退。

## §3 波及面（实测）

| 文件 | 处数 | 状态 |
|---|---|---|
| `SalesInvoiceImportService` | 3 | 无 userId 上下文 → 已改 |
| `InputInvoiceImportService` | 3 | 无 userId 上下文 → 已改 |
| `PurchaseReturnServiceImpl` | 3 | 无 userId 上下文 → 已改 |
| `ReconciliationServiceImpl` | 4 | 无 userId 上下文 → 已改 3 处；另 4 处已是三元形态，保留 |
| `ArapSettlementServiceImpl` | 9 | 无 userId 上下文 → 已改 |
| `PrepaymentServiceImpl` | 2 | 已是 `userId != null ? userId : DEFAULT` → 合规 |
| `AutoGenerationService` | 2 | 同上 → 合规 |

### 3.1 顺带查出一个更危险的取值

`ArapSettlementServiceImpl` 的 `DEFAULT_USER_ID` 原本是 **`0L`**，是 7 个文件中**唯一的例外**（其余均为 `1L`）。

在 P117 之前这无所谓（值不落库）；修复后会造出「用户 id=0」—— **一个永不存在、谁都不是的用户**。已改为 `1L`。

## §4 实施内容

为 5 个文件新增私有辅助方法，统一取真实登录人：

```java
private Long currentOperatorId() {
    try {
        Long uid = com.huicai.base.system.util.SecurityUtils.getCurrentUserId();
        return uid != null ? uid : DEFAULT_USER_ID;
    } catch (Exception e) {
        return DEFAULT_USER_ID;
    }
}
```

⚠️ **保留兜底而非返回 null**：`getCurrentUserId()` 在无登录态（定时任务 / 系统初始化）会返 null 或抛异常，若直接写 null 则 `created_by` 会是 NULL —— 而**恒为 NULL 恰好就是 P117 那个缺陷的表征**，不能让修复引入新的同类问题。

## §5 守卫（`AuditOperatorRealUserStructureTest`，5 例）

| 用例 | 作用 |
|---|---|
| `noHardcodedAuditOperator` | 扫描全仓生产代码，禁止 `.setCreatedBy(DEFAULT_USER_ID)` / `.setResolvedBy(...)` 这类裸常量赋值 |
| `guardDetectsHardcodedAssignment` | **守卫自身的反证**：注入字符串，验证规则能命中违规、不误报合法写法 |
| `helperTrulyReadsCurrentUser` | 验证 `currentOperatorId()` 真的取登录人、判 null、回退常量（三者缺一即红） |
| `defaultUserIdIsNotZero` | 禁止 `DEFAULT_USER_ID = 0` |
| `createdByIsNotGhostField` | **P117 不变量回归锁**：若 `createdBy` 被改回 `exist=false`，立刻变红 |

### 5.1 反证已做且成立

注入一处 `.setCreatedBy(DEFAULT_USER_ID)` ⇒ 守卫转红并精确指出 `PurchaseReturnServiceImpl.java:107`；还原后恢复绿。

## §6 验证

| 验证项 | 结果 |
|---|---|
| 相关模块回归 | ✅ 300/300 |
| L2 全量真库 | ✅ **2208 / 0 / 0 / 5** |
| L1 全量 | ✅ **1711 / 0 / 0 / 5**，`All coverage checks have been met` |

## §7 已知局限

1. **方法签名未改**：`submit(Long id)` / `reject(Long id, String reason)` / `cancel(Long id)` 等仍无 `userId` 参数，操作人从登录上下文取。属既有接口形态，改签名会波及全部调用点，**本轮不扩大范围**。
2. **历史数据不回填** —— ✅ **已裁定（2026-10-11）**，取证与理由详见 SPEC-P117 §7.1：81 行存量经证实为开发期测试/演示数据，且反查源覆盖率仅 1.6%，回填无意义且会写入假审计值。
3. **`created_by` 与 `submitted_by` / `audited_by` 并存**：部分表另有专用的「提交人/审核人」列，与 `created_by` 语义不同，本轮未合并（避免重演 `tenant_id` 双列并存债）。

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: BaseEntity
module: common
table: t_voucher

# P118：P117 让 created_by 真正落库后，全仓 34 处
# setCreatedBy(DEFAULT_USER_ID) 才开始产生实际影响 —— 每处都写下
# 一个并非实际操作人的值。本 SPEC 收口这些赋值。
rules:
  - id: R-118-01
    source: "审计字段不得写死常量，须取真实登录人"
    mapping:
      forbidden_pattern: '.setCreatedBy(DEFAULT_USER_ID)'
      required_pattern: currentOperatorId
    implementation: ArapSettlementServiceImpl
    test_ref: AuditOperatorRealUserStructureTest

  - id: R-118-02
    source: "DEFAULT_USER_ID 必须 > 0（id=0 不存在任何真实用户）"
    mapping:
      field: DEFAULT_USER_ID
      min_value: 1
      note: "ArapSettlementServiceImpl 原为 0L，是 7 个文件中唯一例外"
    implementation: ArapSettlementServiceImpl
    test_ref: AuditOperatorRealUserStructureTest

  - id: R-118-03
    source: "无登录态时回退兜底值而非 null（恒 NULL 即 P117 缺陷表征）"
    mapping:
      helper: currentOperatorId
      fallback: DEFAULT_USER_ID
      fallback_required: true
    implementation: ReconciliationServiceImpl
    test_ref: AuditOperatorRealUserStructureTest

acceptance_tests:
  - id: AT-118-1
    given: "生产代码出现 .setCreatedBy(DEFAULT_USER_ID)"
    when: "运行守卫"
    then: "红灯并指出文件:行号"
    test_ref: AuditOperatorRealUserStructureTest#noHardcodedAuditOperator
  - id: AT-118-2
    given: "DEFAULT_USER_ID 被设为 0"
    when: "运行守卫"
    then: "红灯"
    test_ref: AuditOperatorRealUserStructureTest#defaultUserIdIsNotZero

---

## §8 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-10-11 | 首版。收口 5 个文件的 22 处硬编码，新增结构守卫 5 例 |