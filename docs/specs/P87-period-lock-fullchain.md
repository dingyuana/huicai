# P87 SPEC — 期间锁全链路 + 反结账未过账结转凭证防护

> **版本**：V1.0 | **最后修改**：2026-09-22 | **作者**：Hermes
> **状态**：✅ 已实现（拦截面补齐 + reopen 防护，测试通过，commit e893232）
> **编号**：HUICAI-SPC-087 | 优先级：P1（数据完整性——期间锁是全链路闸门）
> **依据**：PRD-017 §P87 + R-139（需求矩阵）+ `VoucherServiceImpl`/`PeriodCloseServiceImpl` 代码 trace
> **关联**：P68（期间状态机 open→closed→reopen）、S-18
> **test_ref**：PeriodCloseServiceImplTest#reopenPeriod_blockedWhenPendingDraftCarryoverVoucher、#reopenPeriod_passesWhenNoPendingCarryoverVoucher

---

## 0. 背景与现状

PRD-017 立项时判断"期间锁仅制证 1 处拦截，需扩展修改/过账/红冲 3 处"。代码 trace 后**现状比 PRD 预判的好**——`assertPeriodOpen` 已被 7 个写方法复用，真实缺口只有 2 处。

### 现状盘点（代码 trace 实证）

`assertPeriodOpen(period)`（VoucherServiceImpl L645）已挂在 7 个写入口：

| 写方法 | assertPeriodOpen | 位置 |
|---|:--:|---|
| create（制证） | ✅ | L187 |
| update（修改） | ✅ | L224 |
| delete（删除） | ✅ | L252 |
| submit（提交） | ✅ | L272 |
| audit（审核） | ✅ | L309 |
| unpost（反过账） | ✅ | L334 |
| post（过账） | ✅ | L405 |
| **reverse（红冲）** | ❌ **缺** | L411 |

`reverse()` 会向**原凭证所属期间**插入新 DRAFT 红冲凭证——原期间若已 closed，就会在锁定期间里凭空多一张草稿，破锁。这是 8 个写操作里唯一漏的。

### 二缺口：reopenPeriod 无"未过账结转凭证"防护

`reopenPeriod()`（PeriodCloseServiceImpl L351）反结账时只改期间状态为 open + 调 `validateReopenOrder`（顺序校验），**不检查该期间是否残留半成品的自动结转凭证**。

- 结账流程会生成 `CLOSE-`（损益结转）/`DISTRIB-`（利润分配）凭证，初始态 DRAFT，需人工审核→过账才完成
- 若结账后凭证还停在 DRAFT（未过账）就被人反结账，重新打开的期间里会留着"垃圾草稿"，污染下期期初结转基线

**设计取舍（守人工审核铁律）：** reopen **只拦截、不自动删除**。存在 DRAFT 结转凭证时抛异常，要求人工先处理（删除草稿或完成记账）再反结账。不自动清理——所有单据状态变更必须人确认（MEMORY 铁律：系统不允许自动调整状态）。

---

## 1. 改动契约

### 1.1 副作用声明

| 方法 | 副作用 | 事务 |
|---|---|---|
| `VoucherServiceImpl.reverse(id, userId)` | 新增：对原凭证期间做 `assertPeriodOpen` 前置校验（纯读校验，无写）；原期间 closed 则阻断红冲 | `@Transactional`（既有） |
| `PeriodCloseServiceImpl.reopenPeriod(period, userId)` | 新增：反结账前 `voucherMapper.selectCount` 扫 CLOSE-/DISTRIB-/DEPR- 三个前缀的 DRAFT 未红冲凭证；任一 >0 抛 `badRequest` 阻断 reopen | `@Transactional`（既有） |

### 1.2 异常码穷举

| 触发 | 异常 | message 片段 |
|---|---|---|
| reverse 时原凭证期间已 closed/locked | `BusinessException.badRequest` | "凭证所属期间 ... 已结账/已锁定，禁止红冲"（复用 assertPeriodOpen 文案） |
| reopen 时存在 DRAFT 结转凭证 | `BusinessException.badRequest` | "期间 {period} 存在 {n} 张未过账的 {prefix} 结转凭证，请先删除草稿或完成记账后再反结账" |

### 1.3 物理路径编码约定

- 红冲拦截点：`VoucherServiceImpl.reverse()` 取到 `original` 后、构造红冲前立即 `assertPeriodOpen(original.getPeriod(), original.getEnterpriseId())`——与其余 7 个写方法同一入口，同一校验方法，不重复造轮子
- reopen 防护扫描条件：`voucherNo LIKE prefix+period%` + `status=DRAFT` + `reversedFrom IS NULL`（未被红冲）+ `deleted=0`；三前缀逐一查（`selectCount` 返回 `Long`，>0 拦截）

---

## 2. 验收场景（BDD）

- **P87-AC1**：Given 期间 202407 已 closed When 对该期间凭证执行修改/过账/删除/提交/审核/反过账 Then 全部被 `assertPeriodOpen` 拦截（现状既有，回归确认）
- **P87-AC2**：Given 期间 202407 已 closed When 对该期间已过账凭证执行红冲(reverse) Then 拦截，报"已结账，禁止红冲"，不生成红冲草稿【新增】
- **P87-AC3**：Given closed 期间存在 1 张 DRAFT 的 `CLOSE-202407` 结转凭证 When 反结账 Then 拦截，列出数量与前缀，期间状态保持 closed，不执行 updateById【新增】
- **P87-AC4**：Given closed 期间无 DRAFT 结转凭证（已全部过账）When 反结账 Then 正常翻 open（既有 reopen 语义不变）

---

## 3. 与既有 SPEC 的关系

- 期间状态机定义沿用 **S-18 / P68**（open→closed→reopen 三态），本 SPEC **不改状态枚举**，只补"写入锁定期间的拦截面"和"reopen 前的数据完整性检查"
- 结转凭证前缀命名（`CLOSE-`/`DISTRIB-`/`DEPR-`）与 **P85 结转序列**一致；P85 增加 DEPR（折旧）前缀时，本防护的三前缀扫描天然覆盖，无需改 P87
- 不触碰 **P84 结账工作台**的一键人工审核路径（那条走 `ElMessageBox.confirm` 人触发，合规）

---

## 4. 交付清单

| 项 | 状态 |
|---|---|
| `VoucherServiceImpl.reverse()` 加 assertPeriodOpen | ✅ |
| `PeriodCloseServiceImpl.reopenPeriod()` 加 DRAFT 结转凭证防护（3 前缀） | ✅ |
| 2 个 P87 回归测试（AC3 拦截 / AC4 放行） | ✅ |
| 全量回归 | 33 tests（PeriodCloseServiceImplTest 30 + VoucherServiceImplTest 3）0 fail |
| commit | `e893232` |

### 回归阻断规则（防重复）
新增/修改任何向期间写入凭证的路径（新写方法、新增结转类型）时，必须：①调用 `assertPeriodOpen`；②若产生 DRAFT 结转凭证，把其前缀登记进 `reopenPeriod` 的扫描数组。二者缺一即破锁/留垃圾草稿。
