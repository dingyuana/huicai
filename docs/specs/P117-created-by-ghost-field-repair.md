# SPEC-P117：修复 `created_by` / `updated_by` 幽灵字段 —— 一条从未生效过的制审分离内控

| 项 | 内容 |
|---|---|
| **状态** | ✅ 已实施（V1.0） |
| **关联 REQ** | REQ-2026-141 |
| **前置** | SPEC-P116（凭证层制审分离补齐）—— 本 SPEC 是 P116 取证过程中查出的**根因** |
| **严重度** | 🔴 **P0**（内控失效，非功能不可用） |
| **迁移** | `V178__spec_p117_add_created_by_updated_by_columns.sql` |

---

## §1 缺陷描述

`BaseEntity` 的 `createdBy` / `updatedBy` 两个字段被标注了 `@TableField(exist = false)`：

```java
/** 创建人 — 由 Service 层手动 set，不参与 MyBatis-Plus 自动 SQL */
@TableField(exist = false)
private Long createdBy;
```

该注解使 MyBatis-Plus **既不把这两列写入 INSERT/UPDATE，也不把它们映射进 SELECT**。于是：

1. 全仓 **57 处** `setCreatedBy(...)` 全是**误导性死代码**（AGENTS §4.2 第 10 条「幽灵字段」形态）；
2. 任何依赖 `getCreatedBy()` 的内控判断**恒为 null** ⇒ 恒不触发。

## §2 波及面（实测，非推演）

| 行为 | 位置 | 后果 |
|---|---|---|
| 🔴 **业务单据制审分离** | `BusinessDocServiceImpl#approve` | 「制单人不能审核自己提交的单据」**自落地起从未拦下过一次** |
| 🔴 凭证制审分离 | `VoucherServiceImpl#audit`（P116 补齐） | 同样恒不触发 |
| 🟡 分支判断恒真 | `AiFeedbackLogServiceImpl` | `if (entity.getCreatedBy() == null)` 恒成立 |
| 🟡 前端「制单人」列恒空白 | `BusinessDocVO` / `ExpenseReimbursementVO` / `VoucherVO` | `createdByName` 永不回填 |

### 2.1 行为证据链

夹具行经 JDBC 直查 `created_by = 900001`，同一行经 `voucherMapper.selectById(id)` 读回 `createdBy = null` ⇒ `userId.equals(entity.getCreatedBy())` 恒 false。

## §3 为什么不曾被发现

三个条件同时成立，使缺陷恰好不显形（AGENTS §4.3 第 9 条同型）：

1. `t_voucher` 等表的 `created_by` 实测 **37/37 行全为 NULL** —— 极易被读成「种子数据不走 Service」，而非「谁都不落库」；
2. 前端「制单人」列本就少被使用，空白不易察觉；
3. 代码里写着 `entity.getCreatedBy() != null` 的判空，**看起来很健壮**，实际正是它让缺陷隐形。

⚠️ 这三点都不是证据，只是缺陷不显形的三个条件。**「37 行全空」恰恰是症状，不是「种子数据特殊」的证明。**

## §4 修复方案

### 4.1 为什么不能直接去掉注解

去掉 `exist = false` 后，MyBatis-Plus 会把这两列写进**所有** `BaseEntity` 子类的 SQL。实测 83 张 `t_*` 表：

| 分类 | 数量 |
|---|---|
| 同时有 `created_by` 与 `updated_by` | **22** |
| 至少缺其中之一 | **61** |

缺列的表若不补，MP 生成的 SQL 会报 `column does not exist`，且**只在运行期暴露**（L1 Mock 测不出，只有 L2 真库会红）。

### 4.2 实施内容

1. **`V178`**：为 61 张缺列表补 `created_by bigint` / `updated_by bigint`（`ADD COLUMN IF NOT EXISTS`，幂等）；
2. **`BaseEntity`**：去掉两处 `@TableField(exist = false)`，恢复为真实映射列；
3. **不配 fill**：`MyMetaObjectHandler` 不填这两个字段，仍由 Service 手动 `setCreatedBy`（57 处保持不变，现在才真正落库）。

### 4.3 类型裁定：`t_prepayment` 是唯一例外

`PrepaymentEntity` **不继承 `BaseEntity`**，其 `createdBy` 为 `String`，且该表 `created_at/updated_at` 是 `date`（基类为 `LocalDateTime`）⇒ 类型体系本就不同源。

⚠️ `t_prepayment.created_by` 实测为 **`varchar(50)`**，是 84 张表中的唯一非 bigint。**`V178` 刻意排除该表**，不得改动其列类型。

### 4.4 列不给默认值

`created_by` / `updated_by` 均**可空、无 DEFAULT**。理由：`created_by` 必须由业务显式赋值才可追溯；`DEFAULT 0` 会造出「用户 id=0」这种幽灵记录。

## §5 验证

| 验证项 | 方法 | 结果 |
|---|---|---|
| 制审分离真拦 | `VoucherMakerCheckerGuardTest` 真库 3 例 | ✅ 红 → 绿 |
| **反证（关键）** | 把 `createdBy` 改回 `exist=false` | ✅ **1 例转红** ⇒ 门禁能红，非恒绿 |
| 业务单据层零回归 | `BusinessDoc*` 74 例 | ✅ 全绿 |
| L2 全量真库 | `mvn test -DexcludedGroups=` | ✅ **2203 / 0 / 0 / 5** |
| L1 全量 | `mvn clean test` | ✅ **1706 / 0 / 0 / 5**，`All coverage checks have been met` |
| 覆盖率 | clean 口径 | 49.41% / 41.96% / 68.07%（阈值 49/41/67） |

## §6 修复过程中暴露的三类回归（均为**修复的正确后果**）

| 类 | 用例 | 成因 | 处置 |
|---|---|---|---|
| **A. 内控真的生效了** | `LedgerChainRealDBTest`×4、`VoucherIntegrationTest`×2 | 夹具用同一 `USER_ID` 建单**并**审核 —— 过去靠死校验放行 | 新增 `AUDITOR_ID = 2L`，建单人与审核人分离 |
| **B. 断言把缺陷当规范** | `DepreciationVoucherRealDBTest#generate_endToEnd_balancedAndBackfilled` | 原断言写着 `assertNull(voucher.getCreatedBy())` 并附注释论证「这是项目既有行为」 | 改为 `assertEquals(USER_ID, ...)` 正向断言 |
| **C. 测试夹具撞唯一键** | `PeriodMapperTest`×2、`DepreciationVoucherRealDBTest#generate_closedPeriodFails` | 新守卫夹具用了全仓通用的 `period_code='202607'`，并发时撞 `uq_period_code_ent` | 改用本类独占编码 `P116C7` |

⚠️ **B 类是最值得记住的一条**：那条断言不仅锁死了缺陷，还在注释里给出了**看似合理实则错误**的论证（「审计追溯靠 `t_close_log.operator_id` 与应用日志」）。**测试断言可以固化正确行为，也可以把缺陷升格成规范。**

## §7 已知局限与已裁定项

1. **历史行 `created_by` 仍为 NULL —— 已裁定：不回填**（2026-10-11 老丁裁定）

   **裁定依据（取证，非推测）**：
   - 实测待回填规模：`t_voucher` 37 行 + `t_business_doc` 44 行 = **81 行**；
   - 反查源 `t_audit_log` 192 行中仅 **3 行**有 `operator_id`（**1.6%**），`t_close_log` **0 行** ⇒ 「反查回填」最多救回 1~2 行，**无实际意义**，属无效迁移；
   - 该 81 行经取证确认为**开发期造的测试/演示数据**：`created_at` 仅集中在 **2~3 个小时**内、`id` 连续、编号规则化（`JZ2025120001`）。

   **故保持现状**。制审分离对这 81 行不生效（校验对 `created_by IS NULL` 放行），与业务单据层口径一致 ——「不知道谁制的」不能反过来说「他自审」。

   ⚠️ **明确否决的两个选项**：
   - 「回填为管理员 1」—— 审计值是假的，等于把「不知道谁做的」伪装成「管理员做的」；
   - 「反查回填」—— 由上述 1.6% 覆盖率证伪。

2. **`DEFAULT_USER_ID` 硬编码** —— ✅ 已由 SPEC-P118 收口（5 文件 22 处改为取真实登录人，`0L`→`1L`）。

3. **前端「制单人」列** —— 字段恢复后，**历史行仍空白**，新单据正常显示。属预期行为，非缺陷。

## §8 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-10-11 | 首版。定位幽灵字段根因，实施 `V178` + 去掉 `exist=false`，L1/L2 零回归 |
