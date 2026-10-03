# 开发计划 — 商用化差距修复（P101 总纲执行计划）

> **版本**：V1.1（进度回写） | **日期**：2026-10-03 | **负责人**：opencode
> **关联**：`docs/specs/P101-commercial-gap-remediation.md`（总纲）、子 SPEC P102~P107
> **基线**：commit `ab2f7e02`（分支 `develop`）；全量 1998/0/0/5 绿
> **范围**：排除 AI 功能（老丁 2026-09-29 指示）
> **当前实施分支**：`feature/req-131-test-gate`（领先 `develop` 40 commits，2026-10-03 尚未合入）

---

## 0. 目标与策略

**目标**：把「功能广度已达 QuickBooks Advanced 70–80%，但安全/审计/门禁基座全缺」的项目，补到**可交付企业生产**的底线。

**策略**（三条，贯穿全计划）：

1. **先基座后功能**：P102（安全）→ P103（审计）→ P106（内控深度）；功能广度已够，缺的是底线。
2. **每步必须可验证**：负向用例 + 真实 DB 断言，禁「先上后补」（AGENTS §4.3 铁律）。
3. **门禁先行**：P104（CI 真库门禁）尽早做，让后续所有改动都有真库回归网兜底。

---

## 1. 里程碑总览

| 里程碑 | 内容 | 子 SPEC | 前置 | 出口标准（DoD） | 状态（2026-10-03） |
|---|---|---|---|---|---|
| **M0 基线** | 立项、建分支、审计报告留档 | P101 | — | SPEC 审核通过，REQ-128 登记 | ✅ 完成 |
| **M1 门禁兜底** | main PR 跑真库套件 | P104-a | M0 | main PR 变红一次真库失败（证明门禁有效） | 🚧 **1.1/1.2/1.4 已完成；1.3「故意失败反证」未做 ⇒ DoD 未达成** |
| **M1.5 存量缺陷** | 6 项 P0/P1 点状缺陷修复（可并行，最快见效） | **P107** | 无 | 6 项 AT-107 全绿 → ✅ **2026-09-30 完成 D1~D6+D8，定向 83/83** |
| **M2 租户隔离** | 跨企业 403（三源并集）、入参封禁、RLS 生效 | P102-a/b/c | M0 | 越权用例全拒；`rolbypassrls=false`；AT-102-7 入参不生效 | 🚧 2.1/2.2/2.5 已交付；**2.4 角色降权待老丁人工执行**（runbook 已就绪、不可逆）；2.3/2.6 待做 |
| **M3 端点鉴权** | 9 清库端点 + 高危端点 `@PreAuthorize` | P102-d/e | M2、REQ 权限码列 | 无权限码用户 403；`PermissionCodeAuditTest` 绿 | ✅ 完成（`f4845866` + `V163`；⚠️「补 `permission_code` 列」前提已被实测证伪，真实列是 `permission` 且早已回填） |
| **M4 审计落地** | 快照真实落库、updateById 纳入 | P103 | M3（复用权限码） | `before_data/after_data` 非空断言通过 | ✅ 完成（`b19a8ee0`；遗留：快照取全量而非字段级 diff） |
| **M5 测试成色** | 29 个同义反复改造、覆盖矩阵 | P104-b/c | M1 | 核心模块真库覆盖 ≥60%；同义反复归零 | 🚧 **归零 28/29**（仅 `VoucherTemplateMapperTest`）；**60% 未达标**（L1 实测 32%/13%/56%）；5.2 覆盖矩阵与 5.4 死资产清理未做 |
| **M6 文档地基** | REQ 唯一、SPEC 状态回写、硬数字单点 | P105 | 无 | 编号全局唯一；13 个漂移 SPEC 回写 | 🚧 6.1 已完成（登记册 V1.47）；**6.3 状态回写与 6.4 硬数字单点本轮（2026-10-03）补做**；6.2 未做 |
| **M7 内控深度** | 年结、制单≠审核、权限粒度 | P106 | M4 | 详见 P106（本批只锁边界，可独立排期） | ❌ **未立项**：`docs/specs/` 无 P106 文件，登记册无 REQ-2026-133 条目 |

> **并行策略**：M1（门禁）、**M1.5（P107 存量缺陷）**、M6（文档）三者互不依赖，可同时开工；M5 依赖 M1 的门禁生效后才有意义。
> **M1.5 为什么优先**：6 项中有 4 项是 P0 且**端点当前 100% 报错或静默谎报**（对账争议、批量审核、凭证删除），修完立刻消除线上可见故障，且不依赖任何基座改造。
> **2026-10-03 复核**：M1.5 之后又叠加了 6 处 P0 状态越权修复与状态越权门禁（`60414668`~`79af2a90`、`e0f4b958`），它们不在原 M1~M7 分解内，属门禁派生出的新一批 P0 —— 说明**门禁修好之后立刻开始产生收益**（原为纯人工扫描，前 5 处全靠肉眼发现）。

> **并行策略**：M1（门禁）、**M1.5（P107 存量缺陷）**、M6（文档）三者互不依赖，可同时开工；M5 依赖 M1 的门禁生效后才有意义。
> **M1.5 为什么优先**：6 项中有 4 项是 P0 且**端点当前 100% 报错或静默谎报**（对账争议、批量审核、凭证删除），修完立刻消除线上可见故障，且不依赖任何基座改造。

---

## 2. 任务分解（按内循环微循环，每个 5–15 分钟，TDD 红→绿）

### M1 门禁兜底（P104-a）｜预计 0.5 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 1.1 | `l2-integration-test.yml` 触发分支加 `main` | 现只监听 `develop`；改 `[ main, develop ]` | 推 main 后 Actions 出现 L2 job |
| 1.2 | 修 Testcontainers 预拉镜像 | 现拉 `mysql/redis`，实际用 `pgvector/pgvector:pg16` | workflow 日志显示拉 pgvector 成功 |
| 1.3 | 真库门禁**失败必须红** | 故意推一个失败断言验证门禁真会拦截 | 确认 PR 变红（**这是门禁有效的唯一证明**） |
| 1.4 | `full-stack-test.yml` 保持 `mvn test`（L1 快测） | 两层门禁分工：L1 快、L2 慢 | 文档标注职责 |

### M1.5 存量缺陷修复（P107）｜预计 2 天｜可与 M1/M2 并行

| # | 任务 | 关键点 | 验证 | 状态 |
|---|---|---|---|---|
| 1.5.1 | D1 对账 `DISPUTED` 违反 CHECK | **V160** 补 `DISPUTED`（DROP+ADD 幂等） | AT-107-1 真实 DB 调 `dispute()` 不报 SQL 错 | ✅ 2/2 |
| 1.5.2 | D2 凭证分录物理删除 | `deleteByVoucherId` 改 `UPDATE deleted=1`；`selectByVoucherId` 补 `e.deleted=0` + **JOIN 父表 `v.deleted=0`**；另 4 个零调用方物理 DELETE 一并转软删。**注意**：`BaseEntity:43` 已带 `@TableLogic deleted`，MP 逻辑删除本已生效，无需改 Entity | AT-107-2/3（3/3 绿，红灯反证 `expected 1 but was 0`） | ✅ 3/3 |
| 1.5.3 | D3 三个空壳批量服务 | 三处抛 `BusinessException(501, "功能未实现")`，**禁 return success** | AT-107-4 断言 `success != true` | ✅ 6/6 |
| 1.5.4 | D4 银行对账确认/驳回空壳 | **V161** 建 `t_bank_reconciliation_log`；confirm 落 `MANUAL_MATCHED`（非 `MATCHED`）+ 回写日记账 + 记日志；reject 回落 `UNMATCHED` + 释放日记账 | AT-107-5 真实 DB 状态流转 | ✅ 真库 6/6 + Mock 21/21 |
| 1.5.5 | D5 金额精度 | `BankStatementExcelImportService:140,277`（**P107 漏记第 3 处**）+ `TaxServiceImpl:878` 改 `BigDecimal.valueOf` | AT-107-6/7 精度往返断言 | ✅ 2/2 + 52/52 |
| 1.5.6 | D6 明文口令 | 口令改 `${ENV:默认}`；**JWT 去默认**（`${JWT_SECRET}` 缺值即启动失败）；compose 9 处 + 新增 `.env.example` | AT-107-8 静态扫描 | ✅ 5/5 |
| 1.5.7 | **D8（实施中新发现）** 流水 `PENDING_CONFIRM` 违反 CHECK | `BankReconciliationServiceImpl:332` 写该值而 `chk_stmt_match_status` 允许集无它 ⇒ 自动匹配落 60-84 分档必崩。**V162** 补该值；`rejectMatch` 一并放行该中间态（否则 auto-match 产物无法人工驳回，违人审铁律 #1） | 红灯反证 `violates chk_stmt_match_status`（2/6 红）→ 6/6 绿 | ✅ 6/6 |

> **M1.5 汇总（2026-09-30 完成）**：D1~D6 + **D8** 共 7 项全部 TDD 红→绿（含红→绿反证），定向回归 **83/83** 全绿（8 个受影响测试类）。**全量回归留夜间自动跑**（用户明确要求不做耗时全局测试）。新增 3 个 Flyway 迁移 `V160`/`V161`/`V162`。

### M2 租户隔离（P102-a/b/c）｜预计 2 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 2.1 | `X-Enterprise-Id` 加**三源并集**校验 | `t_user.enterprise_id` ∪ `t_agency_user_enterprise` ∪ `SUPER_ADMIN`；越权 403 不回退。**注意**：只查成员表会锁死 `accountant01`/`reviewer01`/`assistant01`（其 `enterprise_id` 为 NULL 且无成员记录） | AT-102-1/1b/1c；Testcontainers 确认种子账号不被锁 |
| 2.2 | **入参 `enterpriseId` 封禁（L1 兜底）** | `MyMetaObjectHandler:20` 改 `strictInsertFill`→**强制覆盖**为 `EnterpriseContextHolder.get()`，忽略入参值；一次堵死 48 处 Entity 直入 | AT-102-7：body 含 `{"enterpriseId":999}` → 落库为上下文企业 |
| 2.3 | RLS 让 GUC 生效 | 业务事务入口 `SET LOCAL app.enterprise_id` | Testcontainers 查 `t_voucher` 仅本企业行 |
| 2.4 | 应用角色 `NOBYPASSRLS` | 现 `rolsuper=t`；需新建非超级应用角色 + 授权 | `pg_roles.rolbypassrls=false`；全量真库仍绿 |
| 2.5 | 拦截器 fail-closed | `DataPermissionInterceptor` 4 处 `catch→return null` 改抛异常 | 注入 SQL 解析异常 → 事务中止 |
| 2.6 | SUPER_ADMIN 切换留痕 | from→to + 操作人，复用 P103 审计切面 | 审计表有切换记录 |

> ⚠️ 2.3/2.4 风险最高：收紧 RLS 可能让现存超级用户查询返 0 行。**必须先在 Testcontainers 全量真库跑通再动生产角色**。
> 📌 2.2（L1 兜底）**先做**：改动面 1 个类即可堵死 48 处越权路径；L2 的 DTO 隔离分批见 P102 §7（批1 涉资金模块优先）。

### M3 端点鉴权（P102-d/e）｜预计 2 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 3.1 | V160 migration 补 `t_menu.permission_code` | 现**无此列**；按现有 82 菜单回填权限码 | migration 幂等；`check-entity-schema` 绿 |
| 3.2 | 8 个清库端点下线或收口 | `SystemClearController` 无 WHERE 硬删；建议**开发环境 profile 才暴露** | 普通用户 403；`t_voucher` 行数不变 |
| 3.3 | 高危端点加 `@PreAuthorize` | 清库/反结账/制证/审核/红冲/科目 | 逐端点无权限码 → 403 |
| 3.4 | `PermissionCodeAuditTest` 启动自检 | 端点权限码必须在 `t_menu` 有对应项 | 故意加无码端点 → 测试红 |

### M4 审计落地（P103）｜预计 1.5 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 4.1 | `AuditLogEntity` 快照字段映射修正 | 4 字段 `exist=false`；真实列 `before_data/after_data`；补 `entity_type/entity_id/entity_no` | 往返断言非空 |
| 4.2 | `updateById` 纳入切面 | 现切面只拦 `insert`/`deleteById`/`@Auditable` | 改凭证后查审计有 before/after |
| 4.3 | 删不存在的 `status` 列查询 | `AuditLogServiceImpl:34` 查 `t_audit_log` 无此列 | 带 status 查询不再报 SQL 错 |
| 4.4 | 状态变更快照改合法 JSON | 现手工拼串（`"entityId=..."` 非 JSON） | 快照可 `jsonb` 解析 |
| 4.5 | `@Auditable` 覆盖核心实体 | 现仅 5 处 | 凭证/单据/发票/核销 覆盖 |

### M5 测试成色（P104-b/c）｜预计 3 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 5.1 | 29 个同义反复 `*MapperTest` → 真库 | 改 `extends AbstractMapperTest`；优先 budget/masterdata/storage | 覆盖 SQL 真实执行；顺带修 `BudgetMapperTest` 的 `OPERATION` 违规夹具 |
| 5.2 | 覆盖矩阵落文档 | 按模块统计真库/Mock 比例 | `docs/testing/TEST-STRATEGY.md` 重写 |
| 5.3 | 负向断言门禁 | 推广 REQ-123/124/125 正确写法为规范 | 抽检新增测试负向断言密度 |
| 5.4 | 死资产清理 | 3 个 `.removed.ts`、3 个永不执行 `@Suite`、1 条无效 surefire exclude、`@FastTest` 0 用 | 清理后计数与文档一致 |

### M6 文档地基（P105）｜预计 1 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 6.1 | `REQ-079/080` 重号修正 | 各两次、内容互斥（`:82`vs`:273`、`:103`vs`:274`） | 编号全局唯一 |
| 6.2 | `SPC-003/006` 空指针 | 无对应 SPEC 文件；补 SPEC 或改指针 | 指针全部可解析 |
| 6.3 | 13 个漂移 SPEC 状态回写 | `S-18`/`S-23`/`P55`/`P57`/`P61` 等标「待开发/待审核」但代码已落地 | SPEC 状态 = 代码实况 |
| 6.4 | 硬数字单点 | `TEST-STRATEGY.md` 滞后 16 天；`flyway-governance` 失效 | §0 为唯一可信源，其余引用之 |

---

## 3. 依赖图

```
M0 ──┬─▶ M1(门禁) ──▶ M5(测试成色)
     │
     ├─▶ M1.5(存量缺陷 P107) ──▶ (独立可完成)
     │
     └─▶ M2(租户) ──▶ M3(端点鉴权) ──▶ M4(审计) ──▶ M7(内控深度/可独立)
     
     M6(文档地基) ── 独立，随时可做
```

## 4. 风险登记

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| RLS 收紧致全量真库返 0 行 | 中 | 高（阻塞 M2） | 先 Testcontainers 全量验证；2.2/2.3 单独 commit 便于回滚 |
| 权限码补列需回填 82 菜单 | 中 | 中 | V160 幂等 migration；先补列再回填 |
| 门禁加严后历史 PR 大面积变红 | 高 | 中 | M1 先只加触发分支，不改断言；变红逐项修而非放宽 |
| 切企业成员校验误伤代理端 | 中 | 中 | 成员表 `t_agency_user_enterprise` 已存在，先只读校验不删功能 |
| 审计补全后日志表膨胀 | 中 | 低 | 快照只存变更字段，非全量 dump |

## 5. 不在本计划范围

- AI 能力（全部排除）
- 前端 122 个 Playwright 接入 CI（另立任务，D 项发现）
- P106 年结/多账套的功能实现（本批只锁边界与依赖，建议独立排期）
- 29 个同义反复以外的测试大规模重写

---

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：基于四路审计，拆为 M0~M7 八个里程碑、依赖图、风险登记；排除 AI |
| V1.1 | 2026-10-03 | opencode | **进度回写**：①§1 里程碑表新增「状态」列 —— 原表只有 DoD 没有状态，M1/M2/M3/M4/M5/M6/M7 的真实进度只能从登记册反推（这本身违反 M6「硬数字单点」）；②头部由「V1.0 草案，待老丁审核」改为 V1.1 并补当前实施分支；③标注 M1 的 **DoD 未达成**（1.3 故意失败反证未做）、M2 的 **2.4 角色降权为不可逆人工步骤**、M5 的 **60% 覆盖率未达标**、M7 的 **从未立项**；④M3 行更正「补 `permission_code` 列」这一已被实测证伪的前提；⑤补记 M1.5 之后新增的 6 处 P0 状态越权修复与状态越权门禁（原分解未含，属门禁派生的新批次） |
