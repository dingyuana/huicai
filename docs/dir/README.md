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
| DIR-001 | **安全加固会静默改写测试造数，且症状伪装成「隔离失效」** | 规范缺失 | REQ-2026-129 / P102 | ✅ **已回写** AGENTS §4.5 第 23 条（2026-10-03）；守卫脚本仍未接 CI |
| DIR-002 | 静态检查若依赖调用方自觉遵守排除清单，其自身会成为新的假绿来源 | 流程错误 | REQ-2026-131 / P104 | 🟡 待评估 |
| DIR-003 | 慢测（L2）只在夜间 CI 跑，本地无等价入口，导致「本地全绿 ⇒ 安全」不成立 | 流程错误 | REQ-2026-131 / P104 | ✅ **已回写** AGENTS §4.5 第 24 条（2026-10-03，实测 L2 本地 5 分钟可跑完） |

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
