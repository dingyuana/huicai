# DIR — Document Improvement Request

> 用于捕获开发过程中发现的需求模糊、规范缺失、流程不准确问题。
> 每条 DIR 不超过 3 个字段，提交 ≤ 2 分钟。

## 模板

```
DIR-{序号}: {一句话问题描述}
- 类型：需求遗漏 / 规范模糊 / 流程错误 / 技术约束缺失
- 影响范围：{模块/文件/功能}
- 关联：REQ-2026-XXX / P-{编号}
```

## 当前开放的 DIR

| DIR | 描述 | 类型 | 关联 | 状态 |
|-----|------|------|------|------|
| DIR-001 | **安全加固会静默改写测试造数，且症状伪装成「隔离失效」** | 规范缺失 | REQ-2026-129 / P102 | ✅ **已闭环**（2026-10-03）：已回写 AGENTS §4.5 第 23 条，且 `check_tenant_fixture.py` 已挂进 `full-stack-test.yml` 阻断式门禁 |
| DIR-002 | 静态检查若依赖调用方自觉遵守排除清单，其自身会成为新的假绿来源 | 流程错误 | REQ-2026-131 / P104 | ✅ **主要盲区已消除**（2026-10-03 检测器升级为 A/B 两类判定，见下）；残留盲区 3 条已在脚本 docstring 明示 |
| DIR-003 | 慢测（L2）只在夜间 CI 跑，本地无等价入口，导致「本地全绿 ⇒ 安全」不成立 | 流程错误 | REQ-2026-131 / P104 | ✅ **已回写** AGENTS §4.5 第 24 条（2026-10-03，实测 L2 本地 5 分钟可跑完） |
| DIR-004 | **分页 `total` 跨租户泄漏**：分页插件排在企业隔离拦截器之前，COUNT 拿不到 `enterprise_id` | 技术约束缺失 | REQ-2026-133 / P106 | ✅ **已闭环**（2026-10-07 采方案 A：重排 `MyBatisPlusConfig` 拦截器顺序；`PaginationTotalTenantIsolationRealDBTest` 2 例钉死，反证还原顺序即 2 条转红） |

---

## DIR-001

- **类型**：规范缺失
- **影响范围**：`MyMetaObjectHandler.insertFill` 加固（P102-M2，`enterpriseId` 由 `strictInsertFill` 改为无条件覆盖）会**静默改写**所有「只给实体设 `enterpriseId` 而未切换上下文」的测试夹具。实测造成 3 处回归，其中 `CashFlowPeriodRangeRealDBTest` 报 `uq_subject_code_ent` 冲突、`DataIsolationAuditTest` / `BankStatementDataIsolationTest` 各 1 条隔离用例转红。
- **关联**：REQ-2026-129 / P102（SPEC V1.2 §2.2 已记录，但未沉淀为通用规范）

**为什么值得单列一条 DIR**：这类回归的**报错信息与真实原因完全无关**。科目撞种子唯一约束，提示是「唯一键冲突」；隔离用例失败，提示是「企业B 的数据不应被查到」—— 后者极易被误判为*隔离机制失效*，进而走向「把生产逻辑改松」的反向修复。实际情况是**造数根本没落到目标企业**，隔离逻辑是对的。

**正确范式**（项目内已有 4 个范例：`OpeningContinuityRealDBTest` / `AuxiliaryDetailRealDBTest` / `CashSubjectBalanceRealDBTest` / `IncomeStatementCaliberRealDBTest`）：把**上下文**切到目标企业 `useEnterprise(ENT_ID)`，而不是在实体上硬设。确需跨租户造数时用 `AbstractMapperTest#withoutEnterpriseContext` 显式出口，并在注释里说明意图。

**配套**：`scripts/check_tenant_fixture.py` 做机械检查（已反证：注入两处违规均 exit 1，真实仓库 exit 0）。**建议**：把该检查挂进 L1 门禁，避免同类夹具再次混入。

---

## DIR-002

- **类型**：流程错误
- **影响范围**：本轮写的 `scripts/check_tenant_fixture.py` 依赖「文件内出现 `useEnterprise(` / `withoutEnterpriseContext(` 就算合规」这一近似判定。它能抓真实违规，但**依赖调用方自觉使用这两个 API** —— 若有人用第三种方式（如直接 `EnterpriseContextHolder.set(9901L)`）绕过，守卫会误判为合规。这类「检查工具自身也有盲区」的问题在本项目已出现多次（见 AGENTS §4.2 第 8/10/16 条的 Entity-DB 不一致系列）。
- **关联**：REQ-2026-131 / P104

**待评估**：是否需要把判定升级为解析 `EnterpriseContextHolder` 的所有调用形式；或明确接受该盲区并在脚本文档中写明「仅覆盖两种范式」。

**2026-10-03 反证时已实证该盲区，且比预想更严重**：反证过程中把 `CashFlowPeriodRangeRealDBTest` 的 `useEnterprise(` **纯文本替换**成 `withoutEnterpriseContext(`，检测器立刻判绿 —— 但这两者语义**相反**（前者切上下文、后者清上下文），替换后的夹具实际上必然失效。已把该盲区写进脚本 docstring（「检查工具自身也可能假绿」），并保留在 CI 阻断门禁中（净收益仍为正：能挡住绝大多数不自觉的违规）。
**新增待办选项**：③ 检测器改为校验「`useEnterprise(常量)` 的实参与 `setEnterpriseId(常量)` 一致」，而不是只看 API 是否出现 —— 这能同时消掉「替换即骗过」与「传错企业号」两类漏判。

**2026-10-03 已实施（选项 ③）**，检测器从「只判 API 是否出现」升级为两类判定：

| 类 | 判什么 | 为什么重要 |
|---|---|---|
| **A** | 设了非默认企业号，却既无 `useEnterprise(` 也无 `withoutEnterpriseContext(` | 原判定，保持不变 |
| **B** | **有 `useEnterprise(X)` 但与 `setEnterpriseId(Y)` 的 Y 不一致** | **此前完全不被检查，而它才是真正危险的形态**：上下文=A、实体=B 时 M2 的无条件覆盖会把实体静默改写成 A ⇒ 数据落到 A 而测试以为落在 B。**该代码能正常编译、正常跑绿、无任何报错**，症状是「另一个用例的断言莫名失败」，只能靠静态比对发现 |

**同时纠正上条记录里的一个说法**：`useEnterprise(` → `withoutEnterpriseContext(` 的纯文本替换确实能骗过**检测器**，但替换后的 Java **根本无法编译**（`withoutEnterpriseContext(Runnable)` 不接受 `long` 实参）⇒ 该手法无法悄悄穿过 CI（编译阶段就会红）。所以真正值得补的是 **B 类**（编译正常、行为静默错误），现已覆盖。
**三条残留盲区**（接受，已写进脚本 docstring）：① 目标企业写成变量/字段时无法静态求值（如 `setEnterpriseId(enterpriseId)`）→ 跳过；② `setEnterpriseId(1L)` 这类默认值不参与 B 类比对，故「上下文=2L + 实体=1L」的反向错配不报；③ 直接 `EnterpriseContextHolder.set(...)` 第三种写法不识别（但若同时有非默认 `setEnterpriseId` 且无两个 API，仍会被 A 类抓到）。
**反证记录（4 项全绿）**：真实仓库 `exit=0`；`useEnterprise(ENT_ID)` 改成 `useEnterprise(9999L)` ⇒ B 类 `exit=1` 并指到 3 处；整行删掉 `useEnterprise(...)` ⇒ A 类 `exit=1` 并指到 3 处；合法的 `DataIsolationAuditTest`（`withoutEnterpriseContext` 跨租户隔离夹具）单独跑 ⇒ `exit=0`（无误报）。
**实施中踩到的坑（已写成脚本注释）**：判断「是不是默认企业」必须**求值**而不是看常量名 —— `private static final Long ENTERPRISE_ID = 1L;` 这种「名字听着像非默认、值其实是默认」的写法，早期按常量名判断会产生 **21 处误报（4 个文件）**。

---

## DIR-003

- **类型**：流程错误
- **影响范围**：L1（1757+ 用例）本地约 1 分钟可跑完，L2（2055 用例，真库 Testcontainers）本地约 15 分钟、按老丁要求留夜间 CI。这造成一个危险的错觉：**P102 全部安全加固的本地验证都只覆盖了 L1，而 L2 连续三轮是红的**（`6be565d6` / `a3937d94` / `8232ced2`），其中 `VoucherEntryMapperRealDBTest` 暴露的 `assist_json ->> ?` 解析失败，在真实库里意味着**辅助核算账一直在返回跨租户数据**。
- **关联**：REQ-2026-131 / P104

**待评估**：涉及租户隔离、事务、RLS、真实 SQL 方言的改动，是否应约定「合并前必须本地跑一次 L2」；或在提交信息里强制标注该改动是否 L2-clean。

**2026-10-03 实测结论（部分解答）**：`mvn test -DexcludedGroups=` 在本机 **5 分 00 秒**跑完 2023 个用例，**不需要留夜间** —— 「本地无等价入口」的前提已不成立。但取数时踩到该条描述的**第二重风险**（不是「跑得慢」，而是「跑出来的红不是代码红」）：
- 首跑 `2023 / 0 Failures / 11 Errors / 5 Skipped`，11 个 Error **全部**是 `RedisConnectionFailure`（`LedgerChainRealDBTest` 4 + `VoucherIntegrationTest` 5 + `BankStatementAuditIntegrationTest` 2）。
- 根因是本地 Redis 未起（`application.yml:9-11` 指向 `localhost:6379`，容器 `huicai-redis` 处于 Exited）；而 CI 的 `l2-integration-test.yml` 有 `services.redis` ⇒ **该失败模式只在本地出现**。
- `docker start huicai-redis` 后重跑同 3 类：**12/12 全绿**，证明与代码无关。
- **已沉淀**：AGENTS §4.5 第 24 条。**待办仍未关闭**：CI 无法验证「本地等价」，故合并前是否强制本地 L2 仍无机制保障 —— 建议要么在提交信息标注 L2-clean，要么把 Redis 也纳入 Testcontainers/Compose 的测试前置。

---

## DIR-004

- **类型**：技术约束缺失（MyBatis-Plus 插件注册顺序无规范）
- **影响范围**：**所有**使用 MyBatis-Plus 分页且未在应用层显式带 `enterprise_id` 条件的接口。
  实测（`t_subject`，上下文企业 990001 无科目、**未加任何企业条件**）：**`total=43` / `records=0`**
  ⇒ 总条数是**别的企业**的条数。
- **关联**：REQ-2026-133 / P106（在批次 1a-3 / D-1 的反证过程中查出，非 D-1 本身引入）
- **状态**：✅ 已闭环（2026-10-07，方案 A）

**根因**：`MyBatisPlusConfig` 注册顺序为
`Pagination → OptimisticLocker → EnterpriseDataPermission → DataPermission`。
MyBatis-Plus 按注册顺序调用内层拦截器的 `willDoQuery`，而 `PaginationInnerInterceptor`
会**自行拼 COUNT 并用传入的 executor 直接执行**，该路径**不再回到拦截器链**
⇒ 排在它后面的 `EnterpriseDataPermissionInterceptor#beforeQuery` 无机会改写 COUNT。

**为什么至今无人发现**：多数 Service 已在应用层自己带 `enterprise_id`，
COUNT 与 SELECT 都带条件时总数恰好正确 ⇒ **缺陷被应用层掩盖**。
本条正是在 D-1 反证「删掉应用层过滤会怎样」时才暴露（AGENTS §4.5 第 40 条）。

**为什么不能靠 Mock 发现**：Mock 里 `mapper.selectPage` 直接返回 `Page` 对象，
`total` 由分页拦截器在**运行时**计算，Mock 永远看不到 COUNT。

**待裁定方案**（详见 [DIR-004-pagination-total-tenant-leak.md](DIR-004-pagination-total-tenant-leak.md)）：

| 方案 | 做法 | 结论 |
|---|---|---|
| **A** | 把企业/数据权限拦截器移到分页**之前** | ✅ **已采纳并实施**（老丁 2026-10-07）。改 4 行，一次修好所有分页接口 |
| B | 换 MP 官方 `TenantLineInnerInterceptor` | 不采纳 —— 等于重做三层防线第二层 |
| C | 加守卫测试暴露存量 | ✅ **作为 A 的配套落地**（`PaginationTotalTenantIsolationRealDBTest`） |

**实施结果**：顺序改为 `EnterpriseDataPermission → DataPermission → Pagination → OptimisticLocker`。
机理经 MP 3.5.7 反编译确认（分页插件在 `willDoQuery` 里自行执行 COUNT，该路径不回拦截器链）。
反证「还原顺序 ⇒ 2 条转红」已实测；全量 L1 `1663/0/0/5`、L2 `2104/0/0/5`、前端 265 全绿，无既有测试转红。

**配套沉淀**：AGENTS §4.5 第 40 条 —— 「凡用 MyBatis-Plus 分页，务必同时断言 `total` 与
`records.size()`，只断言 records 等于没断言一半」。
