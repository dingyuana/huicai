# SPEC-P132：凭证编辑归属校验 —— 只有制单人能改自己的草稿凭证

| 项 | 内容 |
|---|---|
| 状态 | ✅ **已审核通过并实施**（2026-10-15，老丁裁定） |
| REQ | REQ-2026-147 |
| 前序 | SPEC-P130（凭证编辑页永久 403）—— **同一缺陷链的第二个环节** |
| 关联 | AGENTS §4.5 第 43 条（负向断言必须含「数据未变」）、第 44 条（三处必须同批改）、§4.2 第 16 条（裸 count 不可靠） |

---

## §0 根因

P130 修的是「**进不去**编辑页」（前端路由因幽灵权限码 403）。但**进去之后**还有一道缺失的闸：

```java
// VoucherServiceImpl#update —— 修复前只有这两道校验
if (!"DRAFT".equals(entity.getStatus())) throw ...("仅草稿状态的凭证可修改");
assertPeriodOpen(entity.getPeriod());
// ⚠️ 没有「操作人是否为制单人」
```

⇒ **任何有访问权的人都能改别人的草稿凭证**，全程无报错。

### 0.1 为什么这两个环节必须一起补

| 环节 | 缺失后果 | 修复 |
|---|---|---|
| 进不去编辑页 | 该页面对非超管永久 403（功能不可用） | P130（补 `voucher:update` 权限码） |
| 进去后能改谁的 | **任何人可改他人草稿**（内控失效） | **本 SPEC** |

只修前者 = 把门锁换成更大的门但里面不设规矩；只修后者 = 门都进不去，校验形同虚设。

> 判据：**发现「A 环节缺失」时，必须追问「A 之后那一环呢」。** 缺陷链常成对出现，补一环等于给另一环更强的存在感。

---

## §1 方案

在 `update` 的状态校验之后、与既有 `assertNotSelfReview` **逐字对齐**地加归属判断：

```java
if (userId != null && entity.getCreatedBy() != null
        && userId.equals(entity.getCreatedBy()) == false) {
    throw BusinessException.forbidden("只能修改本人录入的凭证：制单人为 " + ... );
}
```

⚠️ **null 放行**：`created_by` 为 NULL（自动生成 / 历史数据）时无法判断归属，按「不误伤合法路径」放行，并登记为已知局限。既有 `assertNotSelfReview` 同样是 null 放行，**不另造口径**（P115「单一事实源」同源要求）。

---

## §2 输入 / 输出契约

`PUT /api/base/voucher/v1/vouchers/{id}`：

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 制单人改自己的 DRAFT | ✅ 允许 | ✅ 允许（不变） |
| **他人改你的 DRAFT** | ✅ **允许** | 🔴 **403「只能修改本人录入的凭证」** |
| `created_by IS NULL` | ✅ 允许 | ✅ 允许（null 放行） |
| 非 DRAFT | ✅ 拒绝 | ✅ 拒绝（状态机不变） |

---

## §3 状态流转

**不涉及**。本 SPEC 不改变凭证状态机，`DRAFT → 提交` 路径不受影响。

---

## §4 异常处理

| 场景 | 文案 |
|---|---|
| 越权修改 | `只能修改本人录入的凭证：制单人为 X，当前操作人为 Y（voucherId=Z）` —— 须含**双方 id**，便于判断是权限问题还是数据异常 |
| 无登录态（`userId == null`） | 放行（与既有校验一致） |

---

## §5 BDD 验收场景

| # | Given | When | Then |
|---|---|---|---|
| AT-132-1 | 制单人 9001 录的 DRAFT | 以 9001 修改 | 允许，摘要落库 |
| AT-132-2 | 制单人 9001 录的 DRAFT | **以 9002 修改** | 🔴 403，**且摘要未被改动** |
| AT-132-3 | `created_by IS NULL` 的 DRAFT | 以任意人修改 | 允许 |
| AT-132-4 | 非 DRAFT | 制单人修改 | 仍报「仅草稿状态可修改」 |

⚠️ **AT-132-2 的负向断言不可省**：只断言「抛异常」无法排除「先 update 再校验」的实现 —— 那种实现同样抛异常但**数据已改坏**（§4.5 第 43 条）。

---

## §6 实施结果（2026-10-15）

`VoucherEditOwnershipRealDBTest` 4 例全绿；**反证**（移除归属校验）**1 例转红**。

L1 `1754/0/0/5` + `All coverage checks have been met`；L2 `2280/0/0/5`。

🔴 **实施中踩到并修正的测试装置问题**：正向用例首轮报「会计期间不存在: 209801」—— `assertPeriodOpen` 先抛，根本走不到归属校验。**报错指错方向**，看起来像「校验写错了」。修法：测试先建会计期间。

> 判据：**同一用例连续两次因不同原因报错时，先确认第一层前置条件是否满足**，别急着改被测逻辑。

---

## §7 明确排除与已知局限（§7.7）

**排除**：
- ❌ 不改「审核自己」的既有校验（`assertNotSelfReview` 口径正确，仅复用）
- ❌ 不给 `delete` 加同样的归属校验（另一缺陷链，本 SPEC 不扩大范围）
- ❌ 不改前端（403 由后端返回即可，前端已有错误提示机制）

**已知局限**：
1. **`created_by IS NULL` 时放行** —— 自动生成（`source=GENERATED`）与历史数据的凭证可能无制单人，此时任何人可改。这是「不误伤合法路径」的取舍，**不是**「已确认安全」。
2. **删除路径未覆盖** —— `DELETE /vouchers/{id}` 是否存在同类归属缺口，本 SPEC 未取证。
3. **P131 的其余幽灵码已随该 SPEC 处理**；本 SPEC 只管凭证编辑归属。

---

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

entity: VoucherEntity
module: base.voucher
table: t_voucher

# 不涉及状态机变更（凭证状态机不变），契约以「规则型」表达。
rules:
  - id: C-01
    source: "P132 §1"
    rule: "只有制单人可修改自己的 DRAFT 凭证；他人修改必须被拒"
    mapping: { condition: "userId != null && createdBy != null && !userId.equals(createdBy)", action: forbidden }
    implementation: "VoucherServiceImpl.update"
  - id: C-02
    source: "P132 §1"
    rule: "created_by 为 NULL 时放行（无法判断归属时不误伤），与既有 assertNotSelfReview 口径逐字对齐"
    mapping: { null_created_by: allow, align_with: assertNotSelfReview }
    implementation: "VoucherServiceImpl.update"
  - id: C-03
    source: "P132 §5 / §4.5 第 43 条"
    rule: "越权修改被拒后，凭证数据不得已被改动（负向断言不可省）"
    mapping: { negative_assertion_required: data_unchanged }
    implementation: "VoucherEditOwnershipRealDBTest"
  - id: C-04
    source: "P132 §2"
    rule: "归属校验不得放宽既有状态机：DRAFT 之外的状态仍按原规则拒绝"
    mapping: { status_gate: unchanged }
    implementation: "VoucherServiceImpl.update"