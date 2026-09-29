# 开发计划 — 商用化差距修复（P101 总纲执行计划）

> **版本**：V1.0（草案，待老丁审核） | **日期**：2026-09-29 | **负责人**：opencode
> **关联**：`docs/specs/P101-commercial-gap-remediation.md`（总纲）、子 SPEC P102~P106
> **基线**：commit `ab2f7e02`（分支 `develop`）；全量 1998/0/0/5 绿
> **范围**：排除 AI 功能（老丁 2026-09-29 指示）

---

## 0. 目标与策略

**目标**：把「功能广度已达 QuickBooks Advanced 70–80%，但安全/审计/门禁基座全缺」的项目，补到**可交付企业生产**的底线。

**策略**（三条，贯穿全计划）：

1. **先基座后功能**：P102（安全）→ P103（审计）→ P106（内控深度）；功能广度已够，缺的是底线。
2. **每步必须可验证**：负向用例 + 真实 DB 断言，禁「先上后补」（AGENTS §4.3 铁律）。
3. **门禁先行**：P104（CI 真库门禁）尽早做，让后续所有改动都有真库回归网兜底。

---

## 1. 里程碑总览

| 里程碑 | 内容 | 子 SPEC | 前置 | 出口标准（DoD） |
|---|---|---|---|---|
| **M0 基线** | 立项、建分支、审计报告留档 | P101 | — | SPEC 审核通过，REQ-128 登记 |
| **M1 门禁兜底** | main PR 跑真库套件 | P104-a | M0 | main PR 变红一次真库失败（证明门禁有效） |
| **M2 租户隔离** | 跨租户 403、RLS 生效 | P102-a/b/c | M0 | 越权用例全拒；`rolbypassrls=false` |
| **M3 端点鉴权** | 8 清库端点 + 高危端点 `@PreAuthorize` | P102-d/e | M2、REQ 权限码列 | 无权限码用户 403；`PermissionCodeAuditTest` 绿 |
| **M4 审计落地** | 快照真实落库、updateById 纳入 | P103 | M3（复用权限码） | `before_data/after_data` 非空断言通过 |
| **M5 测试成色** | 29 个同义反复改造、覆盖矩阵 | P104-b/c | M1 | 核心模块真库覆盖 ≥60%；同义反复归零 |
| **M6 文档地基** | REQ 唯一、SPEC 状态回写、硬数字单点 | P105 | 无 | 编号全局唯一；13 个漂移 SPEC 回写 |
| **M7 内控深度** | 年结、制单≠审核、权限粒度 | P106 | M4 | 详见 P106（本批只锁边界，可独立排期） |

> **并行策略**：M1（门禁）与 M2（租户）互不依赖，可并行。M5 依赖 M1 的门禁生效后才有意义。

---

## 2. 任务分解（按内循环微循环，每个 5–15 分钟，TDD 红→绿）

### M1 门禁兜底（P104-a）｜预计 0.5 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 1.1 | `l2-integration-test.yml` 触发分支加 `main` | 现只监听 `develop`；改 `[ main, develop ]` | 推 main 后 Actions 出现 L2 job |
| 1.2 | 修 Testcontainers 预拉镜像 | 现拉 `mysql/redis`，实际用 `pgvector/pgvector:pg16` | workflow 日志显示拉 pgvector 成功 |
| 1.3 | 真库门禁**失败必须红** | 故意推一个失败断言验证门禁真会拦截 | 确认 PR 变红（**这是门禁有效的唯一证明**） |
| 1.4 | `full-stack-test.yml` 保持 `mvn test`（L1 快测） | 两层门禁分工：L1 快、L2 慢 | 文档标注职责 |

### M2 租户隔离（P102-a/b/c）｜预计 2 天

| # | 任务 | 关键点 | 验证 |
|---|---|---|---|
| 2.1 | `X-Enterprise-Id` 加成员校验 | 查 `t_agency_user_enterprise` 确认用户属该企业，否则 403；**不回退** | 越权用例：企业1用户带 `X-Enterprise-Id: 2` → 403 |
| 2.2 | RLS 让 GUC 生效 | 业务事务入口 `SET LOCAL app.enterprise_id`（`EnterpriseDataPermissionInterceptor` 或事务拦截器） | Testcontainers 查 `t_voucher` 仅本企业行 |
| 2.3 | 应用角色 `NOBYPASSRLS` | 现 `rolsuper=t`；需新建非超级应用角色 + 授权 | `pg_roles.rolbypassrls=false`；全量真库仍绿 |
| 2.4 | 拦截器 fail-closed | `DataPermissionInterceptor` 4 处 `catch→return null` 改抛异常 | 注入 SQL 解析异常 → 事务中止 |

> ⚠️ 2.2/2.3 风险最高：收紧 RLS 可能让现存超级用户查询返 0 行。**必须先在 Testcontainers 全量真库跑通再动生产角色**。

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
