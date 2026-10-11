# SPEC-P119：补齐其余 5 类单据的制审分离（P117 遗留的前置缺口）

| 项 | 内容 |
|---|---|
| **状态** | 📋 草案，待老丁裁定范围 |
| **关联 REQ** | 待分配 |
| **前置** | SPEC-P117（`created_by` 真实落库）、SPEC-P116（凭证层）、SPEC-P118（审计字段记真实操作人） |
| **严重度** | 🟠 中高（内控缺口，但**当前无法修复**——见 §2） |

---

## §1 背景：P116 只补了 2 处，遗漏 5 处

P116 为凭证层补了制审分离。取证（扫描全部 `approve(` / `audit(` 入口）发现另有 **5 个审核入口无任何制审分离校验**：

| # | 入口 | 位置 |
|---|---|---|
| 1 | 预算审批 | `BudgetServiceImpl#approve(Long id)` |
| 2 | 核销审批 | `ReconciliationServiceImpl#approve(Long logId)` |
| 3 | 银行流水审核 | `BankStatementServiceImpl#audit(Long, Long)` |
| 4 | 银行流水审批 | `BankStatementServiceImpl#approve(Long)` |
| 5 | 资产处置审批 | `AssetDisposalServiceImpl#approve(Long)` |
| 6 | 费用报销审批 | `ExpenseReimbursementServiceImpl#approve(Long, String)` |

⚠️ 这 6 个缺口**在 P117 之前修不了** —— `created_by` 是幽灵字段时，任何校验恒不触发。**P117 才使它们具备修复条件。**

## §2 🔴 决定性发现：5/6 处**现在加了校验也不生效**

`V178` 给全部 83 张表补了 `created_by` 列，但**列存在 ≠ 有人写入**。实测各实体的创建路径：

| 入口 | 表有列 | **创建路径是否写 `created_by`** | 加校验后能否生效 |
|---|---|---|---|
| 预算审批 | ✅ | ❌ **无** | ❌ 恒不触发 |
| 资产处置审批 | ✅ | ❌ **无** | ❌ 恒不触发 |
| 银行流水 audit/approve | ✅ | ❌ **无** | ❌ 恒不触发 |
| 费用报销审批 | ✅ | ❌ 本体无（只有凭证有） | ❌ 恒不触发 |
| 核销审批 | ✅ | ✅ 有（`ReconciliationServiceImpl:391`） | ✅ 可直接加 |
| 凭证 audit | ✅ | ✅ 有 | ✅ 已完成（P116） |
| 业务单据 approve | ✅ | ✅ 有 | ✅ 已完成 |

⇒ **本 SPEC 的真正内容不是「加 6 处校验」，而是「先让 5 条创建路径写上 `created_by`，再加校验」。**

⚠️ 否则就是 §4.5 第 21 条的**恒绿门禁**形态：代码在、校验在、就是不拦 —— 比没有更危险（看起来有防护）。

## §3 创建路径的形态差异（决定修法）

| 实体 | 创建位置 | 是否拿得到登录人 |
|---|---|---|
| `BankStatementEntity` | `parseRow(...)`（**纯行解析**，2 处） | ❌ 无上下文 |
| `AssetDisposalEntity` | `AssetDisposalCreateDTO#toEntity()`（**DTO 映射**） | ❌ 无上下文 |
| `ExpenseReimbursementEntity` | `ExpenseReimbursementDTO#toEntity()` / Service | ⚠️ Service 侧有 |
| `BudgetEntity` | 待查（`create` 走 Mapper） | ⚠️ 待定 |
| `ReconciliationLogEntity` | `ReconciliationServiceImpl:391` | ✅ 已有 |

### 3.1 核心设计问题

`toEntity()` / `parseRow()` 是**纯函数**（DTO → Entity），在其中读取登录态会把「映射」与「上下文」耦合，且**DTO 可被多处复用**（`AssetDisposalCreateDTO` 若同时用于导入与手工新增，登录态来源可能不同）。

**候选修法**：
- **(A) 在 Service 层补写**：DTO 保持纯映射，由调用它的 Service 在 insert 前 `setCreatedBy(currentOperatorId())`。⇒ 不动 DTO 契约，符合现有架构；需确认每个 Service 插入点。
- **(B) 改用 fill 注解**：给这些实体的 `createdBy` 加 `@TableField(fill = INSERT)`，由 `MyMetaObjectHandler` 统一从 `SecurityUtils` 取。⇒ 改动最小、覆盖最全；但 `MyMetaObjectHandler` 当前**不处理 `createdBy`**（只处理 `createdAt`/`updatedAt`/`importedAt`/`enterpriseId`），需扩展它，且会影响**所有** BaseEntity 子类（与 P117 同量级的影响面）。

## §4 待你裁定的三个问题

### Q1：`created_by` 的填充方式 —— (A) Service 补写 / (B) 统一 fill 注解

- **(A)** 不动 `MyMetaObjectHandler`，逐个 Service 插入点补写。影响面可控，但**易漏**（漏一处即该表恒不触发）。
- **(B)** 扩展 `MyMetaObjectHandler` 统一填充。覆盖全、不易漏，但**改的是全局组件**，且与 P118「由 Service 手动 set」的既有约定冲突 —— 需先统一口径，否则两种写法并存。

### Q2：`MyMetaObjectHandler` 若采用 (B)，无登录态路径怎么办

定时任务 / Excel 导入 / 系统初始化时无登录态。三个选项：
- 写 NULL（则该单据永远无法做制审分离校验）
- 回退 `DEFAULT_USER_ID = 1L`（写入假值，与 P118 刚清理的问题同型）
- **跳过填充并登记为已知局限**（推荐：诚实，且 Excel 导入本就无「录入人」概念）

### Q3：本轮做多少

- **全量**：5 条创建路径 + 6 处校验
- **仅可立即生效的**：只做「核销审批」（第 2 项），其余登记为待前置
- **分组**：先做 Service 侧已有创建路径的（核销、费用报销），再做需要改 DTO 的（资产处置、银行流水）

## §5 已知风险（无论选哪种）

⚠️ **加校验会让既有夹具变红**：项目内大量测试是「同一人建单 + 审批」，修复后一律被拒。

> 实测参照：P117 修复时已有 **6 个既有夹具**因此变红（`LedgerChainRealDBTest` ×4、`VoucherIntegrationTest` ×2），已改为建单人与审核人分离。本批预计同类影响面更大。

## §6 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V0.1 | 2026-10-11 | 草案。取证发现 5/6 处缺 `created_by` 写路径，提出三个待裁定问题 |