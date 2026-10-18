# SPEC-P127 租户表事务边界缺口（RLS GUC 失效型读写故障）

| 项 | 内容 |
|---|---|
| 状态 | **V1.0 —— 已实施并闭环**（老丁裁定：D-1 选项1 / D-2 选项A / D-3 选项A） |
| 创建 | 2026-10-14 |
| 触发 | 生产报障：代理端会计导入银行对账单全部失败 |
| 关联 | AGENTS §4.5 第 27/48/50 条、铁律 #11（事务红线）、铁律 #13（DTO/VO 隔离不涉及） |
| REQ | 建议 **REQ-2026-143**（登记册当前最大 REQ-2026-142） |
| 前置 | SPEC-P122（`TenantGucSetter` 抽取）、P102/M5b（RLS 落地）、V167（谓词空串硬化）、V176/V177（代理端 RLS 误分类修复） |

---

## 0. 摘要（一句话）

银行对账单导入失败**与会计角色无关**，根因是 `BankStatementExcelImportService` 整个类没有 `@Transactional`，导致 `app.enterprise_id` 这个 RLS 开关从未被设置；同一根因还会在 **UPDATE/DELETE/SELECT** 上以**静默丢数据**的形态复现（比本次的响亮失败危险得多），须一并收口。

---

## 1. 事实与证据

### 1.1 生产现象

```
ERROR: new row violates row-level security policy for table "t_bank_statement"
  at BankStatementExcelImportService.confirmImport(BankStatementExcelImportService.java:526)
  at BankStatementController.confirmImport(BankStatementController.java:165)
```

- `t_bank_statement` 实测 **0 行** ⇒ 该功能在本开发库**从未成功导入过任何一行**。

### 1.2 根因（单一，已闭环）

| 环节 | 事实 | 出处 |
|---|---|---|
| RLS 策略 | `enterprise_id = NULLIF(current_setting('app.enterprise_id',true),'')::bigint`，且 `FORCE ROW LEVEL SECURITY`（owner 也不豁免） | `pg_policy` / `pg_class` 实测 |
| 开关写入 | `SET LOCAL app.enterprise_id = N` | `TenantGucSetter.java:70` |
| **前置条件** | `SET LOCAL` **只在事务内有效**；且 `apply()` 首行即 `if (!isActualTransactionActive()) return;` | `TenantGucSetter.java:56` |
| 设置入口 1 | `TenantRlsInitializer` 切点 = `@Transactional` / `@within(@Transactional)` | `TenantRlsInitializer.java:76` |
| 设置入口 2 | `EnterpriseDataPermissionInterceptor#beforeQuery` / `#beforeUpdate` | `…Interceptor.java:77,84` |
| 缺陷类 | `BankStatementExcelImportService` **类级 0 处、方法级 0 处** `@Transactional`（第 16 行仅有一个未使用的 import） | 源码实测 |
| 入口 | `BankStatementController#confirmImport`（第 165 行）直接调用，无事务包裹 | `BankStatementController.java:165` |

⇒ **事务外 ⇒ 无 GUC ⇒ 谓词退化为 `enterprise_id = NULL` ⇒ 恒不成立。**

> 补充取证：反编译 `mybatis-plus-extension-3.5.7` 的 `MybatisPlusInterceptor`，
> 其 `intercept()` **只对 `SqlCommandType.SELECT` 分支**，其余（含 **INSERT**）统一走
> `beforeUpdate` ⇒ **INSERT 并非拦截器覆盖不到，而是 `apply()` 因无事务而提前 return。**
> 这一点纠正了「MyBatis-Plus 没有 beforeInsert 钩子所以 INSERT 漏网」的初始推断。

### 1.3 后果矩阵（以真实应用角色 `huicai_app` 实测，`super=false, bypassrls=false`）

| SQL 类型 | 无 GUC 时的真实表现 | 危害等级 |
|---|---|---|
| **INSERT** | 抛 `new row violates row-level security policy` | 中 —— 响亮，可发现 |
| **UPDATE** | `UPDATE 0`，**无任何报错** | 🔴 高 —— 静默丢数据 |
| **DELETE** | `DELETE 0`，**无任何报错** | 🔴 高 —— 静默丢数据 |
| **SELECT** | 返回 **0 行** | 🔴 **最高** —— 「看起来像业务数据本来就没有」 |

判定依据（AGENTS §4.5 第 48 条）：**RLS 谓词不匹配时，写路径抛错反而是幸运；读路径只会少返回几行。** 本次报障命中的是**四种形态里最安全的一种**。

> 对照组：`GUC=1` + `enterprise_id=999` 被拒 ⇒ **策略本身正确**，不是配置错误。
> `GUC=1` + `enterprise_id=1` 插入成功 ⇒ 机制本身有效。

### 1.4 与「刘会计角色」的关系（更正提问前提）

`confirmImport` 端点**没有任何角色校验、也不解析企业上下文**，对**任何角色**都失败。
角色唯一的影响是间接的：若会计未先选客户（P123 场景），`EnterpriseContextHolder` 为 `null`，
则即便补上 `@Transactional`，`TenantGucSetter.apply()` 仍会因 `enterpriseId == null` 提前 return —— **仍然失败**。

---

## 2. 同类缺陷清单（含不确定性声明）

### 2.1 已确认（生产日志实证）

| 编号 | 位置 | 表现 |
|---|---|---|
| F-1 | `BankStatementExcelImportService#confirmImport` | INSERT 全行拒绝，`t_bank_statement` 恒 0 行 |

### 2.2 候选（未定论，**不得据此直接批量改**）

以「方法体**直接**访问 Mapper 字段 且 类级/方法级均无 `@Transactional`」为判据扫描 97 个含 Mapper 的类，命中 6 处：

| 编号 | 位置 | 类型 |
|---|---|---|
| F-2 | `AiTaskService#getById()` | 读 |
| F-3 | `AiTaskService#tagAnomaly()` | 写 |
| F-4 | `AiFeedbackLogServiceImpl#getById()` | 读 |
| F-5 | `AiFeedbackLogServiceImpl#page()` | 读 |
| F-6 | `AnalysisService#listMetrics()` | 读 |
| F-7 | `AttachmentService#presignedUrl()` | 读 |

### 2.3 🔴 必须声明的取证局限（不得跳过）

**该扫描器自检失败，不可作为存量清单的权威来源。**

- 自检项：它能否识别**已知真阳性** F-1？
- 实测结果：**不能**。`BankStatementExcelImportService.java` 存在异常缩进（`confirmImport` 内
  `stmt.setCreatedBy(...)` 顶格），导致基于括号计数的分方法逻辑失配，F-1 未被命中。
- 首版扫描器另有假阴性缺陷（只在该行找 `@Transactional`，漏掉标准写法的独占注解行），
  修正后候选数由 17 降到 6 —— **说明该工具的读数对实现细节高度敏感，不可信**。

⇒ **结论：F-2~F-7 只能作为「待核实线索」，真实存量未知，可能更多也可能更少。**
按 AGENTS §4.5 第 21 条，门禁首次上线必须先证明它对真阳性敏感 —— 故本 SPEC 的守卫方案
（D-1 选项 1）要求**先以 F-1 为真阳性验证守卫能红**，再用于盘点。

---

## 3. 连带缺陷

| 编号 | 现象 | 位置 |
|---|---|---|
| F-8 | `confirmImport` 开头即 `batchCache.remove(batchId)`，插入失败后**批次已被消费**；用同一 batchId 重试只会得到「批次不存在或已过期」，必须重新上传整个文件 | `BankStatementExcelImportService.java:510` |
| F-9 | `previewExcel` 只解析不落库，所以**上传预览看着正常**、一按确认才全灭，极易被误判为 Excel 格式问题 | 同上 |

---

## 4. 待决策（老丁裁定后方可实施）

### D-1 修复策略（三选一）⭐ 需拍板

| 选项 | 做法 | 优点 | 风险 |
|---|---|---|---|
| **选项 1（推荐）** | 强制事务边界：给 F-1 补 `@Transactional`，逐项核实并补全 F-2~F-7，并**新增机械守卫**禁止再犯 | 改动面小、与项目既定不变式一致（`TenantRlsInitializer` 类注释已写明「凡走租户表的读写路径都必须有事务」）、不动核心安全组件 | 属于**约定+守卫**，无法从结构上杜绝；守卫需先自证对真阳性敏感 |
| 选项 2 | 兜底 GUC：`TenantGucSetter` 在**无事务**时改用会话级 `set_config(...,false)`，语句执行后在同一连接上 `RESET` | **从结构上根治**，无论有无事务都能工作 | 改的是租户隔离核心组件；会话级设置若未清理干净即**跨请求串租户**（§4.5 第 29 条已踩过）；性能多一次往返 |
| 选项 3 | 1 + 2 并做 | 最稳 | 工期最长，两套机制需证明不互相干扰 |

**倾向选项 1**：先把已确认的 F-1 止血并加守卫，用守卫把真实存量逼出来，再按批次收敛。
选项 2 触及安全底座，按 §7 风险边界宜单独立项、单独反证。

### D-2 `confirmImport` 的事务语义（需拍板）

现行为**逐行容错**：catch 住单行异常、累加 `failed`、继续下一行。
加 `@Transactional` 后若不调整，会因方法级事务的**原子回滚**语义把「部分成功」变成「全批失败」。

- 选项 A（推荐）：**整批原子** —— 任一行失败则整批回滚，返回明确失败原因，让用户修正后重新导入。金融数据更契合「要么全对、要么全不做」。
- 选项 B：保留逐行容错 —— 则必须改用「逐行独立事务」（如 `TransactionTemplate` 按行包裹），复杂度上升。

### D-3 批次缓存消费时机（需拍板）

F-8 的修法：失败时**不消费批次**（`remove` 改为「取出但失败则放回」），允许原 batchId 重试；
或直接接受「失败即需重传」并在 UI 上明确提示。倾向后者（改动小、语义清晰），但需在响应里回传失败行明细。

---

## 5. 验收标准（BDD，实施后逐条对应 `@Test`）

| 编号 | Given | When | Then |
|---|---|---|---|
| AT-127-1 | 会计已登录且已选客户 | 以其 token 调 `POST /bank-statements/confirm-import` | 返回成功，`t_bank_statement` 新增行数 = 导入行数 |
| AT-127-2 | 同上 | 复查新行 `enterprise_id` | 等于该会计当前上下文企业（**非 1、非 NULL**） |
| AT-127-3 | 同上 | 再查 `flyway`/RLS | 无 `row-level security policy` 异常 |
| AT-127-4（反证） | 摘掉 `@Transactional` | 重跑 AT-127-1 | **必须转红**（证明测试对本次改动敏感） |
| AT-127-5 | F-2~F-7 中任一 | 触发对应端点 | 不出现「静默 0 行」（须先逐项判定真伪再决定是否纳入） |
| AT-127-6 | 导入含非法行 | 调 confirm-import | 按 D-2 裁定返回（原子⇒全批失败；容错⇒回传失败行明细） |

---

## 6. 竞品对标（铁律 #15）

用友/金蝶的银行对账单导入均为「**先入暂存表 → 校验 → 用户确认 → 事务性批量入账**」，
且**导入失败一律不产生半截数据**。本项目当前「逐行容错 + 批次先消费」的组合，
既不满足 D-2 选项 A 的原子性，也牺牲了可重试性 ⇒ 倾向 **D-2 选项 A + D-3 保留批次**。

---

## 7. 风险边界

- 本 SPEC **不涉及任何 DDL**，无需 Flyway 迁移。
- 若 D-1 选 2，则触及租户隔离核心组件，须按 §7.7 单独登记风险并单独反证。
- 修复不得改动 `EnterpriseDataPermissionInterceptor.SHARED_TABLES`（已核验与 RLS 层**无冲突**：
  13 张白名单表均无 FORCE RLS，反向亦成立）。

---

## 8. 变更记录

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V0.1 | 2026-10-14 | opencode | 初稿：F-1 实证确认 + F-2~F-7 候选（含扫描器自检失败的局限声明）+ F-8/F-9 连带缺陷 + D-1~D-3 待裁定 |
| V1.0 | 2026-10-14 | opencode | 老丁裁定 **D-1 选项1 / D-2 选项A / D-3 选项A**，实施并闭环（详见 §9） |

---

## 9. 实施闭环记录（V1.0）

### 9.1 老丁裁定与实施对应

| 决策 | 裁定 | 实施内容 |
|---|---|---|
| D-1 | 选项 1（强制事务边界） | `BankStatementExcelImportService` 加**类级** `@Transactional(rollbackFor = Exception.class)`；`confirmImport` 另加**方法级**同款注解作为显式声明（整批原子是安全关键契约，由守卫单独锁定） |
| D-2 | 选项 A（整批原子） | 删除逐行 `catch` 吞异常 + `failed` 累加；任一行失败即抛 `BusinessException`，触发整体回滚，报错自带行号/流水号/日期/金额 |
| D-3 | 选项 A（失败不消费批次） | `batchCache.remove(batchId)` → `get(batchId)`，**仅在整批成功后**才 `remove` |

附带修正：`previewExcel` 两处 `catch (Exception ignored) {}` 改为 `log.warn` 留痕（静默吞异常形态）。

### 9.2 守卫（机械不变量）

`TenantTableTransactionBoundaryTest`（6 例）—— 用 **ASM 读字节码**判定「方法是否引用 `*Mapper`」，
刻意**不用正则**：首版正则扫描器自检失败（已知真阳性都扫不出）。

反证已做且成立：
- 摘掉**类级**注解 ⇒ `everyMapperTouchingMethodIsTransactional` 转红，精确报出 `[previewExcel, previewExcel]`
- 摘掉**类级+方法级** ⇒ **4 例转红**，且 ASM 扫描器准确报出 `[confirmImport, previewExcel, previewExcel]`
  （即证明装置能识别真阳性 F-1）
- 还原后 6/6 绿

### 9.3 守卫当场查出的第二处真实缺陷（登记时未预料）

`previewExcel` 的 `statementMapper.countDuplicate(...)` 同样处于无事务状态。
机理：RLS 谓词取不到 GUC ⇒ 该 SELECT **静默返 0 行** ⇒ `isDup` 恒为 `false`
⇒ **重复检测从未生效**（且外面还包着 `catch (Exception ignored) {}`，连异常都不出声）。

🔴 **诚实限定**：该缺陷在生产中**一直是被掩盖的** —— 因为 INSERT 必然失败、表恒为空，
根本不存在「重复」可检测。**两个缺陷互为掩体**。只有 INSERT 修好后它才变得可达。
故准确表述是「结构上必然失效、但因表恒空而未造成可见错账」，而非「已造成重复入账」。

### 9.4 端到端验证（AGENTS §4.5 第 50 条：改完必须重建容器 + 重跑原报障请求）

| 步骤 | 结果 |
|---|---|
| 改前基线 | `t_bank_statement` = **0 行** |
| 重建镜像 + 重启容器 | `huicai-backend` healthy，`/system/health` = 200 |
| `POST /preview-excel` | 200，valid=3 |
| `POST /confirm-import` | 200，`success=3, failed=0, classified=3` |
| 落库实测 | **新增 3 行**，`enterprise_id` 全为 **1**（= 登录上下文企业，非 NULL、非默认值） |
| 后端日志 | **无** `row-level security` 报错 |
| 重复导入同一文件 | `isDuplicate=True` ×3，`duplicate=3 / success=0`，总行数**仍为 3** ⇒ 重复检测已生效 |

### 9.5 回归

- 银行对账单相关既有测试 **58 例全绿**（`BankStatementRestContractTest` / `BankStatementControllerTest` /
  `BankStatementExcelImportServiceTest` / `BankStatementServiceTest` 全套 / `BankStatementRealDataImportTest`）
- 全量 L1 见 §9.6

### 9.6 未闭合项（登记为遗留，不假装已解决）

| 编号 | 事项 | 状态 |
|---|---|---|
| F-2~F-7 | 6 处候选（`AiTaskService#getById/tagAnomaly`、`AiFeedbackLogServiceImpl#getById/page`、`AnalysisService#listMetrics`、`AttachmentService#presignedUrl`） | ⏳ **未逐项确认**。本次守卫只覆盖 `BankStatementExcelImportService` 一个类；全仓级 ASM 扫描尚未落地。**不得据此宣称全仓已收口** |
| — | 正则扫描器不可信 | 该工具已废弃，不作为存量依据 |
| — | D-1 选项 2（无事务时兜底 GUC） | 按裁定**不做**，仍为备选设计 |
| — | 全仓 74 张带 `enterprise_id` 的表中 5 张无 FORCE RLS | 已核验为 V176/V177 的**有意豁免**（`t_agency_enterprise` / `t_agency_user_enterprise` / `t_contract` / `t_service_progress` / `t_user`），非新缺口；SHARED_TABLES 与 RLS 两层**无冲突** |

---

## 10. 机器可读契约

# MACHINE-READABLE CONTRACT

contract_version: "1.0"

module: sme.cash
table: t_bank_statement

# 本 SPEC 修复后必须成立的不变量。
# 共同机理：企业隔离开关 app.enterprise_id 由 TenantGucSetter 用 SET LOCAL 写入，
# 而 SET LOCAL 只在事务内有效 ⇒ 任何「直接访问租户表却无事务边界」的方法都会失效，
# 且四种 SQL 形态中三种是**静默**的。
rules:
  - id: R-127-01
    source: "凡直接访问 Mapper 的方法，必须被类级或方法级 @Transactional 覆盖"
    mapping:
      guard: TenantTableTransactionBoundaryTest
      detection: asm-bytecode
      forbidden: [method_touches_mapper_without_transaction]
    implementation: BankStatementExcelImportService
    test_ref: TenantTableTransactionBoundaryTest

  - id: R-127-02
    source: "银行对账单确认导入必须整批原子（铁律 #11 + 老丁裁定 D-2 选项A）"
    mapping:
      method: confirmImport
      rollback_for: java.lang.Exception
      partial_success_allowed: false
    implementation: BankStatementExcelImportService
    test_ref: TenantTableTransactionBoundaryTest

  - id: R-127-03
    source: "导入失败时不得消费批次，须允许原 batchId 重试（老丁裁定 D-3 选项A）"
    mapping:
      method: confirmImport
      batch_consume: on_success_only
      cache: batchCache
    implementation: BankStatementExcelImportService
    test_ref: TenantTableTransactionBoundaryTest

  - id: R-127-04
    source: "重复检测不得静默失效（previewExcel 的 countDuplicate 曾在无事务下恒返 0 行）"
    mapping:
      method: previewExcel
      forbidden: [catch_swallows_exception_without_log]
      note: "无事务 ⇒ SELECT 静默返 0 行 ⇒ isDup 恒 false；异常须留痕"
    implementation: BankStatementExcelImportService
    test_ref: TenantTableTransactionBoundaryTest