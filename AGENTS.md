# AGENTS.md — 慧财财务 (Huicai Financial Software)

---

## §0 项目状态（硬数字，每次 commit 后更新）

> **更新基准**：commit `c9f7e9bc`+工作区（**P109 核销异常池修复完成，V1.1**）+ **覆盖率分支税已缴，棘轮 30/12/55 → 45/36/65**。本次：①**P109 批次 1+2 落地**（老丁裁定 D-109-1=表迁就 Entity、D-109-2=前端对齐 DB 4 值）—— 查实**整个核销异常池功能不可用**且已修：新增 `V169` 补 13 列 + `account_id`/`period` 降可空、Entity 删 12 处失效标注、前端词表三处对齐；顺带修掉一处**字段遮蔽**造成的反向缺口（Entity 重复声明 `updatedAt` 遮蔽了基类可写入版本 ⇒ `V127` 的真实列永不写入）；②**一处「工具告警 ≠ 缺陷」复核**：`check-entity-schema.mjs` 报 13 个「列不存在」，实为**开发库停在 Flyway V99**，V169 补上后脚本 `✅ 通过`；③**覆盖率分支税已缴，棘轮阈值重抬**（见 §4.5 第 30 条）—— 新增 `DtoLombokBranchCoverageTest` 使 105 个 DTO/VO 的 Lombok `equals`/`hashCode` 分支从 **2/5708（0.04%）** 升到 **5561/5708（97.4%）**，BRANCH **12.82%→36.80%**，阈值 30/12/55 → **45/36/65**（已反证仍能变红）；④`TenantSwitchRealDBTest`（11 例，P106 批次 2 回归锁，零生产缺陷）；⑤校正过期硬数字：**migration 85（最新 V169）**、L1 1663。⑫**P106 D-3 补做收尾：`V172` 删 `t_ai_feedback_log.tenant_id`** —— 关闭 V170 裁定未覆盖到的同类双列并存债；全仓主代码 tenant_id grep 零残留。⑫**P106 D-3 补做收尾：`V172` 删 `t_ai_feedback_log.tenant_id`** —— 关闭 V170 裁定未覆盖到的同类双列并存债。⑪**P106 批次 1b'：`t_dept` 隔离落地** —— 新增 **`V171`**（加 `enterprise_id` + `uq_dept_code` 改按企业分段 + RLS + FORCE），`t_dept` **移出 `SHARED_TABLES`**，`DeptEntity` 补 `enterpriseId (fill=INSERT)`；新增 `DeptTenantIsolationRealDBTest` 5 例；D-2a 守卫如设计般报警并据此把 `t_dept` **重分类 A→B**（基数 10+4 → 9+5，白名单 14→13）。⚠️ **开发库原停在 V166**，被 `check-entity-schema.mjs` 如实报为「列不存在」，按 §4.5 第 23 条确认版本后重启应用推到 V171。⑩**🔴 P0 修复：`t_user` 用户管理端点跨企业泄漏 PII（实施 P106 D-2a 时查出）** —— `UserServiceImpl` 7 个方法全无企业维度 + `t_user` 在 `SHARED_TABLES` 内（第二层不注入）+ DB 层 `relrowsecurity=false`/`policies=0` ⇒ **三层防线全空**；实测 `ctx=990007` 的 `pageUser` 返回企业 1 的用户，`getById`/`update`/`delete`/`resetPassword` 可**按猜测 id 读改删**别家企业用户。已修 9 处（含连带发现：`create` 从不落 `enterprise_id` ⇒ 新建用户为 NULL 游离数据）。⚠️ **根因是我自己给的豁免理由过宽**：「成员校验要读它，存在循环依赖」只解释了登录路径，却整表放行（见 §4.5 第 43 条）。⑨**P106 批次 1b（D-2a）落地：零 DDL 定性收口** —— `t_menu`/`t_role`/`t_role_menu`/`t_user_role`/`t_sys_config`/`t_audit_log`/`t_dept`/`t_agency`/`t_agency_user`/`t_enterprise` 十张表定性落 SPEC §0.1.1；🔴 **守卫反查查出原 §0.1 漏登 4 张表**（`SHARED_TABLES` 实为 14 张，多出的 4 张**都有 `enterprise_id`**）⇒ 补登为 **B 类（语义豁免）**，与 A 类（无隔离列）**理由根本不同**（见 §4.5 第 42 条）；`t_dept` 明确为**已知未修**的隔离缺口（`uq_dept_code` 全局唯一 + Service 不带企业维度查全表）。⑧**P106 批次 3（D-3）落地** —— 新增 **`V170`** 删 `t_classification_rule.tenant_id` 列 + `idx_classification_rule_tenant` 索引，**Entity 字段同步删**（只删列会报 `column does not exist`）；⚠️ **`t_ai_feedback_log.tenant_id` 刻意未删**（不在 D-3 裁定范围，§7.7 不得顺手扩大 DDL）。⑦**DIR-004 已闭环（老丁裁定方案 A）** —— `MyBatisPlusConfig` 拦截器顺序改为「企业/部门权限 → 分页 → 乐观锁」，修复**分页 `total` 跨租户泄漏**（实测 `total=43 / records=0`）；新增 `PaginationTotalTenantIsolationRealDBTest` 2 例，反证还原顺序即 2 条转红。⑥**P106 批次 1a-3（D-1）落地**（老丁裁定「废弃 `tenantId` 请求参数，改用上下文企业」）—— 6 个方法签名去参 + 2 个 Controller 删 4 处 `@RequestParam` + 前端 2 文件同步；**顺带修掉 3 处 SPEC 清单没预想到的东西**：①**`match()` 是第 7 处按 `tenant_id` 过滤的读路径**（同表同层，清单外漏列）—— 取证判据须从「逐个方法列举」改为「**grep 隔离列读写点**」；②**D-1 初版是 fail-open** —— `if (ctx != null) eq(...)` 在无上下文时**返回全表**，而注释自称「等价于读空集合（安全）」与代码相反，已改 `requireEnterpriseContext()` 缺失即抛；③**端到端断言对本改动不敏感（假绿）** —— 被 `EnterpriseDataPermissionInterceptor` 的 SQL 层注入掩盖，删掉应用层过滤仍全绿，已补「显式初始化 MP `TableInfo` 缓存后断言 SQL 片段」的应用层断言（见 §4.5 第 40 条）。**当前分支**：`develop`（**领先 `origin/develop` 1 个提交**；`main` 经 PR #30 已收口）。⚠️ **若 `develop` 与 `main` 再次出现分叉，以 `main` 为基线**
> **关联文档**：[项目说明](docs/CORE-项目说明.md)、[技术方案](docs/CORE-技术方案.md)、[需求分析](docs/CORE-需求分析.md)、[需求登记册](docs/development/requirements/REQUIREMENTS_REGISTRY.md)、[文档注册表](docs/CORE-文档注册表.md)、[测试策略](docs/testing/TEST-STRATEGY.md)、[Flyway治理规范](docs/development/standards/flyway-governance.md)、[商用化修复总纲](docs/specs/P101-commercial-gap-remediation.md)、[修复开发计划](docs/development/plans/2026-09-29-commercial-gap-remediation-plan.md)、[存量缺陷包](docs/specs/P107-stock-defect-fix-pack.md)、[P106 多账套 SPEC](docs/specs/P106-multi-book-account-set.md)、[P109 核销异常池 SPEC](docs/specs/P109-reconciliation-exception-pool-repair.md)、[P106 决策备忘](docs/development/plans/2026-10-06-p106-decision-memo.md)、[P106 D-2b 与科目映射备忘](docs/development/plans/2026-10-06-p106-d2b-and-account-mapping-memo.md)、[DIR-004 分页 total 跨租户泄漏](docs/dir/DIR-004-pagination-total-tenant-leak.md)

| 维度 | 数据 |
|------|------|
| 后端代码 | 526 个 Java 主代码文件（另 248 个测试类文件，其中 `*Test.java` 240 个）|
| 测试用例 | **2088 个可执行测试注解**（L2 口径，`-DexcludedGroups=`）/ 243 个后端测试类 + 26 个前端测试文件 265 用例。**2026-10-07（P106 D-1 后）全量实测：L1 `mvn clean test` = 1665 通过 / 0 Failures / 0 Errors / 5 Skipped；L2 真库 `mvn test -DexcludedGroups=` = 2124 通过 / 0 Failures / 0 Errors / 5 Skipped**，两次均打印 `All coverage checks have been met`（覆盖率门禁真执行，非静默跳过；`grep -c 'Skipping JaCoCo execution'` = 0）。⚠️ **本地跑 L2 必须先 `docker start huicai-redis`**，否则 11 个用例报 `RedisConnectionFailure`（环境型红，非代码回归，见 §4.5 第 24 条）|
| 覆盖率 | 棘轮门禁 **INSTRUCTION ≥49% / BRANCH ≥41% / METHOD ≥67%**（`backend/pom.xml` jacoco-check，**2026-10-06 P102 批次 7 后再次上调**，此前 48%/40%/67%、47%/38%/66%、45%/36%/65%，再此前 30%/12%/55%）。clean 口径（L1）实测 **49.15% / 41.57% / 67.88%**（2026-10-07 P0 修复后复测），缓冲 **0.15/0.57/0.88 点** ⚠️ 三维缓冲均已不足 1 点 —— **阈值已到当前实测值的天花板**：INSTR 48.71 ⇒ 不能再抬 0.49，BRANCH 40.84 ⇒ 不能抬 0.16，METHOD 67.54 ⇒ 不能抬 0.46（INSTR 49.01 已顶到 49，再抬 1 点即 `Rule violated`）。属 §4.5 第 25 条「紧贴实测」的最紧形态。🔴 **INSTR 容差仅 0.01 点**，已二次 L1 复测稳定，但属于链条最末端，后续新增 VO 必须补测试。🔴 **本轮阈值只敢抬 1~2 点是权衡结果**：新增 VO 会同时抬三个维度，抬得越紧越容易被非代码因素打红；AGENTS §4.5 第 25 条要求「紧贴实测」，但「紧贴」不等于「贴死」。**本次暴涨的来源不是「多写了测试」，而是补上一笔从未被量化的分支税**（§4.5 第 30 条）：105 个 DTO/VO 的 Lombok `equals`/`hashCode` 分支原覆盖 **2/5708（0.04%）**，现覆盖 **5561/5708（97.4%）**。🔴 已反证门禁仍能变红（INSTRUCTION 临时抬 0.99 ⇒ `Rule violated ... instructions covered ratio is 0.49, but expected minimum is 0.99` + `MVN_EXIT=1`，随后 `cp` 还原）。⚠️ **批次 6 后已无法再抬**（见上）⇒ 下一批若只新增 VO/VO 分支而不补测试，三个阈值会直接卡红。🔴 **标定必须 `mvn clean test`**：不带 clean 时 `target/jacoco.exec` 跨调用累积，实测虚高约 6 点，据此标定会让真实 CI 全红。⚠️ **阈值按 L1 标定**（L2 是超集、更高，实测 52.35/40.46/71.39）；`@SlowTest` 类不进 L1 ⇒ 新增慢测**不会**推高 L1 覆盖率。⚠️ **Lombok 的 `equals` 内部会调用每个字段的 getter** ⇒ 补 `equals/hashCode` 覆盖会顺带覆盖此前未被触及的 getter，这也是 INSTRUCTION/METHOD 同步大涨的原因 |
| 数据库 | PostgreSQL 16 / **88 个 migration，最新 V172**（V160=D1 补 `DISPUTED`、V161=对账审计表、V162=D8 补 `PENDING_CONFIRM`、V163=权限码种子、V164=治愈 identity 序列落后、V165=补代理用户企业种子、V166=FORCE RLS、V167=RLS 谓词空串硬化 `NULLIF(current_setting(...), '')`、V168=P106 补 2 个 `(enterprise_id, deleted)` 复合索引、V169=P109 核销异常池补 13 列 + `account_id`/`period` 降可空、**V170=P106 D-3 删 `t_classification_rule.tenant_id` 列 + 旧索引**、**V171=P106 批次 1b' `t_dept` 按企业隔离（加列 + unique 分段 + RLS）**、**V172=P106 D-3 补做删 `t_ai_feedback_log.tenant_id` 列**）；注意：版本号非连续，缺 V6-V62 与 V64-V91 共 85 个号；因 `out-of-order: true` + `validate-on-migrate: false` 不影响运行）⚠️ **开发库可能远落后于 migration 最高号**（实测长期停在 V99）⇒ `check-entity-schema.mjs` 报的「列不存在」需先确认开发库版本，勿直接当缺陷（§4.5 第 23 条）|
| API 端点 | 510+ 个后端端点 |
| 核心模块 | 基础数据、总账、应收应付、现金管理、固定资产、费用报销、发票税务、预算、财务报表、存储管理 |
| 业务单据类型 | 11 种（RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE/TRANSFER/SALARY/PRE_RECEIVE/PRE_PAY）|
| AI 服务 | Python FastAPI 5 端点（health/anomaly/embedding/match/ocr）|
| 技术栈 | Spring Boot 3.x + MyBatis-Plus + Redis 7 + MinIO + RabbitMQ |
| 开发流程 | 大闭环 + 内循环（three-phase-loop v3.0）|
| P0-P2 阶段 | 🟡 **功能模块**基本完成（基础体系 + 缺陷修复 + AI 辅助能力），但**基座与内控未收口**：M5b-V2 非超级角色已落地、**RLS 三层防线已修好并在最低权限主体下验证**（跨租户读 0 行 / 写被拒 / 谓词空串已由 V167 硬化）；**DTO 隔离批次①③已完成**，原门禁 P2 结构性存量 18 → **0（脚本已报「未发现违规」）**；⚠️ 但字段级门禁**天生看不见「无 status 字段的 Entity 直收」**，用反射按**参数类型**全仓扫描实测仍有 **26 处 `@RequestBody Entity`**（客户/供应商/员工/菜单/银行账户/现金&银行日记账/资产分类/凭证类型/汇总模板/税种配置/系统配置/AI 反馈/凭证模板）⇒ **批次④（2026-10-05）已把这 26 处全部清零**：新增 14 个 DTO（客户/供应商/员工/菜单/银行账户/现金&银行日记账/资产分类/凭证类型/汇总模板/系统参数/AI 反馈/凭证模板/银行日记账/分类规则），基线随之删除，断言升级为**零容忍**（`TenantDtoIsolationStructureTest#noEntityRequestBodyAnywhere`，全仓扫描 `com.huicai` 下所有 `@RestController` 的 POST/PUT 方法）。**全仓 `@RequestBody Entity` 已归零**；剩余待办：出参面 Entity 直出（如 `R<RoleEntity>`）、**P106 批次 1a 已实施完成（SPEC V1.4，REQ-2026-133；原登 134 与 P107 重号已更正见 V1.77，审核 V1.78，复审 V1.79，实施回写 V1.80）** —— 已修 **3 类生产缺陷**：①**预付款跨租户默认写入**（`PrepaymentEntity` 无 fill ⇒ `enterprise_id` 落 DB `DEFAULT 1`）；②核销日志两列不一致 + 1 处幽灵字段死代码；③**核销容差按企业 1 判定（静默错账）**。清 6 个硬编码常量 / 8 个有效使用点，`V168` 补 2 个复合索引。**两处诚实修正**：双列并存**不是**隔离泄漏（RLS 与 `tenant_id` 过滤是两个 AND，探针下跨企业查返 0 行），降级为数据不一致；真正的 P0 是 `enterprise_id` 落 DB 默认值。**读路径 7 处（清单外多 1 处）**：原登记 6 处已按 D-1 全部改为 `enterprise_id`，**外加实施中查出的第 7 处 `match()`**（§0.4 漏列，同表同层）。⚠️ **D-2b 已裁定**：`t_sys_config` 与 `t_audit_log` 均**保持平台级不加列**（与 `SHARED_TABLES` 现状一致）；**D-2a 已完成**（零 DDL 定性落 §0.1.1 + 守卫 5 例）；⚠️ **`t_dept` 是「已知未修」的隔离缺口**（全局唯一约束 + Service 不带企业维度查全表）；**批次 1b' 已完成**（`V171` 给 `t_dept` 加隔离列 + unique 分段 + RLS，移出白名单）；**D-3 已完成**（`V170` 删 `t_classification_rule.tenant_id` 列 + 旧索引，Entity 字段同步删；`t_ai_feedback_log.tenant_id` 不在裁定内故**刻意未删**）；⚠️ 实施中查出跨批次缺陷 DIR-004（分页 `total` 跨租户泄漏），**已按方案 A 修复闭环**。**方法论**：审 SPEC 必须把断言追到 **Entity 字段映射层**；涉 RLS 的验收**L2 恒绿**（连接角色是超级用户），必须走非超级探针。⚠️ 只覆盖 P106 的「多账套」子项，**年结/制单≠审核/数据权限粒度/部门级扩展仍未立项** |
| P3 远期 | ⏳ 0%（经营分析/预算预测/风控/工资薪酬）|
| CI 门禁 | 6 个 workflow：L1 单测 / L2 真库 / Full Stack / SPEC 契约 / 夜间 E2E / 性能基线。Full Stack 内 4 个静态检测**全部阻断式**：接口覆盖（**棘轮** `--max-uncovered 280`，只在倒退时红）/ 实体入参状态越权（P0 强制 + P2 结构性存量）/ 租户夹具一致性（A/B 两类判定）/ 路由覆盖。**2026-10-05 PR #28 真实 CI 全绿（DTO 隔离批次①）**；2026-10-03 PR #25 真实 CI 10/10 全绿；此前 PR #25 曾因覆盖率阈值标定口径错误被两个 L1 job 拉红（见登记册 V1.66）⇒ **门禁「绿得起来也红得掉」已获真实证据** |

---

## §1 核心设计铁律（强制执行，违反直接 reject PR）

1. **人是唯一审核主体**：所有审核/结转/状态变更必须由人主动触发，系统不允许自动调整任何业务实体状态
2. **AI 输出 = 建议**：永远不自动应用 AI 结果到财务数据，必须人工确认后才能写入。AI 是横切辅助能力，不替代 Java 确定性模块
3. **凭证不可变性**：已审核/已过账凭证不可修改，只能通过红冲（红-蓝 对冲）修正
4. **状态机严格转换**：所有核心实体（凭证/单据/发票/应收/应付）禁止非法状态跳转
5. **审计追踪**：所有关键写操作通过 AOP + `jsonb` 快照记录变更前后全量数据
6. **数据权限**：MyBatis 拦截器自动注入 SQL 条件，实现组织级数据隔离
7. **金额精度**：所有金额计算使用 `BigDecimal`，禁止 `double/float`，数据库 `NUMERIC(18,2)`
8. **核销架构**：银行流水不直接参与核销，必须先生成收款单/付款单后再匹配应收/应付
9. **编号关联溯源**：所有业务实体通过编号双向关联（xxx_id 外键 + xxx_no 编号冗余），支持全链路追溯
10. **三步闭环铁律**：每项开发任务必须走 SPEC→Plan→审核→执行，不能跳过前两步直接动手
11. **事务红线**：所有涉及资金流转、记账、冲红、核销的核心写操作，必须使用 `@Transactional(rollbackFor = Exception.class)`，严禁无事务执行
12. **逻辑删除**：关键财务表禁止物理删除，统一使用 `deleted` 字段（Integer，0=正常，1=删除），所有查询必须带 `deleted = 0` 条件
13. **DTO/VO 隔离**：禁止将数据库 Entity 直接暴露给前端 Controller 返回值，入参必须定义 DTO/Param，出参必须定义 VO
14. **异常处理**：禁止直接抛出原生 `Exception` 或 `RuntimeException`，必须使用 `BusinessException`（`com.huicai.common.exception`），统一错误码管理
15. **竞品对标**：创建或修改需求时，必须主动对比成熟竞品（用友、金蝶、SAP、QuickBooks等）的同类功能设计与开发经验，识别差异点和最佳实践，纳入 SPEC 背景分析。禁止闭门造车式设计
16. **需求变更审计**：任何需求修改必须记录变更原因、对标竞品结论、影响范围，同步更新 REQUIREMENTS_REGISTRY 版本历史

---

## §2 开发工作流标准

### 2.1 双层闭环流程

```
大闭环（closed-loop-doc-governance）— 项目级文档治理
├── Phase 1: PRD — 需求登记册 + REQ 编号 + 验收标准
├── Phase 2: Spec — SPEC 文档 + REQ 回链 + 版本历史
├── Phase 3: DEV — 开发实施 + DIR 捕获
└── Phase 4-5: 审核 + 闭环回写

内循环（three-phase-loop v3.1）- 功能级微循环开发
├── TRIAGE -> PLAN -> 审核门 -> BUILD(微循环) -> VERIFY -> REPORT
└── 每个微循环 5-15 分钟，TDD-First (Red->Green)，SDD+BDD 规范驱动
```

### 2.2 任务执行流程
```
需求理解 -> 写 SPEC（四段模板+BDD）-> 老丁审核 -> TDD微循环(Red->Green) -> 验证 -> commit -> push
```

### 2.3 开发规则
- **三步闭环**：SPEC->Plan->审核->执行，禁止跳过前两步
- **SDD 规范驱动**：每个 SPEC 必须含四段模板（输入契约/输出契约/状态流转/异常处理），Spec 定义边界，代码实现边界
- **BDD 行为契约**：验收标准必须用 Given-When-Then 格式，每个场景对应一个 @Test 方法
- **TDD-First (Red->Green)**：每个微循环先写测试，运行确认 FAIL(红)，再写实现，运行确认 PASS(绿)。跳过 RED 验证 = 违规
- **先写 SPEC 再写代码**：所有功能必须先有设计文档，禁止边想边写
- **测试必须 0 fail**：每次提交前 `mvn test` 必须 Failures: 0
- **负向断言强制**：所有状态机方法必须同时验证「该做的做了」+「不该做的没做」
- **BDD 场景覆盖率**：VERIFY 阶段必须确认每个 Given-When-Then 都有对应 @Test 且 PASS
- **轻量任务直写**：纯命令/查询/文档修改类任务，Hermes 直接执行

### 2.4 文档提交规范
- 新需求必须分配 REQ 编号，写入 REQUIREMENTS_REGISTRY.md
- 每个 SPEC 必须关联至少一个 REQ 编号，含版本历史
- 每次 commit 后更新 §0 硬数字
- commit message 首行 ≤ 50 字符，禁止 emoji、TODO、待办标记
- `git add` 必须指定具体文件路径，禁止根目录 `git add -A`

---

## §3 沟通铁律（强制执行）

### R9 — 选项呈现规则
- 选项必须在对话最后列出，不混入叙述文本
- 选项数量 ≤ 4 个，超过的先合并归类
- 每个选项必须标「推荐」或「⭐」
- 依据简短，不替用户做选择

### R10 — 任务完成回复规则
- 开头固定：`**任务已完成。**`
- 必须含 6 列总结表：`项 / 内容 / 状态 / 验证 / commit / push`
- 关键发现独立列出
- 遗留事项独立列出
- 查询任务：开头 `**任务已完成。**` + 结构化回答，不写 6 列表格

---

## §4 陷阱与经验库（持续更新）

### 4.1 业务逻辑类
1. **状态机副作用泄漏**：`confirm()` 只改状态，不生成凭证/业务单/应收单，后置逻辑由独立端点触发
2. **红冲匹配时机**：不在导入循环内匹配红字发票，导入完成后统一调 `batchLinkRedFlushInvoices()` 扫全库匹配
3. **发票导入模式**：导入时只创建发票（PENDING_CONFIRM），人工审核后才创建业务单

### 4.2 Entity-DB 不一致类
4. **Entity 字段 vs DB 列对不齐**：`OutputInvoiceEntity` 的 `auditedBy`/`auditedAt` 没有 `@TableField(exist = false)`，但 `t_output_invoice` 表没有这列。MyBatis-Plus 自动生成 SELECT 报 "column does not exist"。根源：注释写"V63 已添加列"但实际没加。修复：每次新增 Entity 字段时必须检查 DB schema，`exist = false` 和 `value=` 二选一，不可裸写。同类字段（`auditedBy/auditedAt/updatedBy/docStatus/voucherStatus`）是高风险模式。
5. **注释说"Vxx 列已添加"但 migration 实际没写**：V63 只给 `t_output_invoice` 加了 `version`，注释声称的 `audited_by`/`audited_at` 从未存在。教训：Entity 注释中的 migration 引用必须与 migration SQL 逐行对证。
6. **String 映射 JSONB 列缺 typeHandler**：`ai_mapping_result`/`aux_dimension`/`assist_json`/`ocr_data` 等字段在 Entity 中是 `String`，但 DB 列是 `JSONB`。全字段 UPDATE 时 PostgreSQL 报错 "column is of type jsonb but expression is of type character varying"。项目已有 `JsonbTypeHandler`，所有 String→JSONB 字段必须加 `@TableField(typeHandler = JsonbTypeHandler.class)`。已经用 AuditLogEntity 验证过正确的写法。
7. **自定义 SQL 中的表名必须与 DB 实际表名一致**：`ArapSettlementMapper.pageWithPartyName()` 的 JOIN 中用了 `t_supplier`，但数据库实际表名是 `t_vendor`。写 JOIN SQL 前先 `\dt` 确认表名，不要靠猜。

8. **`@TableField("col_name")` 指向不存在的列（2026-07-23 修复，反复出现）**：
   - `InputInvoiceEntity`：`amount_ex_tax`、`ai_risk_tag`、`process_status`、`ai_mapping_result`、`doc_no`、`voucher_no` 映射到不存在的列
   - `OutputInvoiceEntity`：同上 + `receivable_id`、`reversed_from`、`original_invoice_no`、`version`
   - `BankStatementEntity`：15+ 个字段无 `@TableField(exist = false)`，包括 `direction`、`batchId`、`purpose`、`transactionRemark`、`reviewedBy`、`reviewedAt`、`generatedDocId`、`generatedVoucherId`、`generatedAt`、`ruleId`、`aiConfidence`、`aiSuggestedAction`、`aiBusinessScene`、`version`
   - **根源**：Entity 按"未来完整 schema"写，但 DB 是另一个版本。注释写"Vxx 列已添加"但 migration 从未执行。
   - **预防**：统一用 `node backend/scripts/check-entity-schema.mjs` 在编译时检查字段映射一致性。后续每次改 Entity 都要跑这个检查。
   - **H-17 已集成 pre-commit hook**（2026-07-23）：提交涉及 `*Entity.java` 的变更时自动运行检查脚本。docker postgres 未运行时自动降级跳过列检查，仅做 typeHandler 警告。首次克隆仓库后执行 `bash backend/scripts/install-hooks.sh` 安装。
   - ⚠️ **工具告警 ≠ 缺陷**（2026-09-29 REQ-123 修正）：该脚本曾把 `cur.assist_json::text` 整体当成列名，误报「`assist_json` 列不存在」，而该列**真实存在**（`t_voucher_entry.assist_json jsonb`），SQL 实测可执行。根因是 `extractColumnRefs()` 的 `cleaned` 链未剥离 PostgreSQL `::type` 转型（已修）。**看到「引用了不存在的列」必须先在真实 DB 上把 SQL 跑一遍再下结论**，否则会把工具缺陷写进缺陷台账。

9. **同名字段在两张表的 CHECK 允许集不同（2026-09-29 慢测 REQ-119 沉淀）**：`status` 这个名字在三处允许集互不相同，**按「实体类型」猜合法值必然踩坑**：

   | 表 | 约束 | 允许值 |
   |---|---|---|
   | `t_business_doc` | `chk_doc_status` | DRAFT / SUBMITTED / **APPROVED** / VOUCHERED / PARTIALLY_RECONCILED / FULLY_RECONCILED / CLOSED / REJECTED / REVERSED |
   | `t_arap_settlement` | `chk_settlement_status` | DRAFT / SUBMITTED / **CONFIRMED** / REJECTED / VOUCHERED / REVERSED / CANCELLED |
   | `t_input_invoice` / `t_output_invoice` | `chk_input_invoice_status` / `chk_output_invoice_status` | PENDING_CONFIRM / PENDING_REVIEW / **CONFIRMED** / VOUCHERED / … |

   - `CONFIRMED` 对发票合法、对**业务单据非法**；`t_business_doc` 用 `APPROVED`/`VOUCHERED` 表达「已审核/已制证」。
   - 教训：写任何 Entity 前先查该表**自己的** CHECK 定义（`pg_get_constraintdef`），不要跨表沿用状态值。

10. **`exist = false` 字段是「幽灵字段」，赋值无效且不可断言（2026-09-29 慢测 REQ-117/119 沉淀）**：`@TableField(exist = false)` 的字段**完全不参与 SQL**，因此
    - 对它 `setXxx()` 后 `updateById` 不会落库 —— 代码里出现这类赋值属**误导性代码**；
    - 从 DB 读回**必然为 null** —— 任何 `assertEquals("xxx", loaded.getDocNo())` 都**永远不可能通过**。
    - 已确认的幽灵字段（注释均明写「DB 无此列」）：发票侧 `docNo`/`voucherNo`、`OutputInvoiceEntity.auditedBy/auditedAt`、凭证侧 `sourceDocId/sourceDocNo/sourceDocType`、`t_arap_settlement.voucherNo`、`BankStatementEntity.direction`、`AuditLogEntity.createdAt`。
    - **替代写法**：断言真实 id 列（`doc_id`/`voucher_id`/`business_doc_id`），并加 `assertNull(loaded.getGhostField())` 作为「该列确已废弃」的负向断言。
    - ⚠️ `t_input_invoice.audited_by/audited_at` **是**真实列（与 Output 侧不对称），别照搬。

11. **`MenuEntity` 缺 `menuCode` 字段 → 任何经 MP 插菜单必失败（2026-09-29 慢测 REQ-120 沉淀）**：`t_menu.menu_code` 是 `NOT NULL` 且**无默认值**，但 `MenuEntity` 只有 `name`/`permissionCode`/`type`/`parentId`… **没有 `menuCode`**。故 `menuMapper.insert(entity)` 必然报 `null value in column "menu_code"`，且 `SELECT` 也不回读该列。这是与第 8 条同源的 Entity-DB 不一致（且**方向相反**：不是 `@TableField` 指向不存在的列，而是 DB 的必填列在 Entity 里缺失）。测试侧暂用 `JdbcTemplate` 直插绕开（`AbstractMapperTest.createMenu()`），**待补 Entity 字段**。
    - 另注：`t_menu` **没有** `enterprise_id` 列，别想当然按多租户表插入。

12. **Flyway 种子用显式 id ⇒ identity 序列永久落后（2026-09-29 慢测 REQ-120 沉淀，慢测 B 类头号根因）**：种子 migration 普遍用**显式 id** 插基础数据（`t_menu` 1~200、`t_subject` 1~102、`t_role`/`t_sys_config` 1~5）却**从未调用 `nextval`**，故序列仍停在 1 或 18。测试首次让 DB 自行分配 id 时拿到的正是 `nextval(seq)=1`，正撞种子行 → `duplicate key ... t_menu_pkey`。
    - **根治办法（已落基类）**：`AbstractMapperTest` 用 `pg_class JOIN pg_depend(deptype='i') JOIN pg_attribute(attname='id')` 通用列出全部 82 个 identity 序列，`setval(seq, GREATEST(COALESCE(MAX(id),1),1))`，**每 JVM 只跑一次**（静态 `AtomicBoolean`）。
    - **为何一次就够**：PostgreSQL **序列不参与事务回滚** —— 测试方法回滚后行消失、序列不倒退，且只增不减，故一次对齐永久有效。逐类硬编码 `align()` 是治标。
    - **同类连锁**：序列落后还会伪装成**业务唯一键**撞码（`uq_role_menu`/`uq_user_role`），因为测试复用了种子 id（如 role_id=1/menu_id=1）而该组合已存在。**新造关联表数据一律用基类 `createRole()`/`createMenu()`/`createSysUser()`，禁止硬编码外键 id**；这三个助手**刻意不缓存**（唯一键要求每次全新 id，缓存会重新引入撞码）。

13. **`NOT NULL` 必填列在 Entity 里**完全缺失** ⇒ 写入路径生产必挂（2026-09-29 慢测 REQ-121 沉淀，与第 8/11 条同源）**：`t_dept.dept_code`、`t_menu.menu_code`、`t_budget.budget_name` 都是 `NOT NULL` 且无默认值，但 `DeptEntity`/`MenuEntity`/`BudgetEntity` **根本没有这些字段**（不是映射写错，是压根没写）。故 `DeptServiceImpl.create`、`MenuServiceImpl.create` 等任何经 MyBatis-Plus 的 `insert` 都会报 `null value in column`。
    - 识别法：`information_schema.columns WHERE is_nullable='NO' AND column_default IS NULL` 的列，逐一在 Entity 里找对应字段；找不到就是这类缺口。
    - REQ-121 已补齐这 3 个字段，基类 `createMenu()` 也从 `JdbcTemplate` 绕开改回 Mapper 路径（**测试绕开只会掩盖生产缺陷，不要用它当长期方案**）。

14. **CHECK 约束的允许集是**大小写敏感**的，且同名约束跨表不同（2026-09-29 慢测 REQ-121 沉淀，补强第 9 条）**：
    | 约束 | 允许值（注意大小写） |
    |---|---|
    | `chk_user_status` | `ACTIVE` / `INACTIVE` / `LOCKED`（**没有** `enabled`）|
    | `chk_user_type` | `SUPER_ADMIN` / `AGENCY` / `ENTERPRISE`（**没有** `employee`）|
    | `chk_menu_type` | **大写** `MENU` / `BUTTON` / `DIR`（小写 `menu` 直接违约）|
    | `chk_disposal_status` | `DRAFT` / `APPROVED` / `VOUCHERED`（**没有** `PENDING_APPROVAL`）|
    | `chk_settlement_type` | `RECEIVE` / `PAY`（**没有** `RECEIVABLE`/`PAYABLE`）|
    | `chk_budget_type` | `DEPARTMENT` / `PROJECT` / `SUBJECT` / `OVERALL`（**没有** `OPERATION`）|
    | `chk_direction` | **小写** `debit` / `credit`（**没有** `DEBIT`/`CREDIT`）|
    - `status` 方向仍见第 9 条（`t_business_doc` 无 CONFIRMED、`t_arap_settlement` 有）。
    - 铁律：**任何常量都要 `pg_get_constraintdef` 查证后再用，禁止照抄别处的字符串或凭业务语感猜。** 本轮 6 项慢测失败全是猜错允许集。

15. **「幽灵字段」比第 10 条更严重的一档：表里连列都不存在（2026-09-29 慢测 REQ-121 沉淀）**：`InputInvoiceEntity.processStatus`（`t_input_invoice` 无 `process_status`）、`OutputInvoiceEntity.aiMappingResult`（`t_output_invoice` **无任何 jsonb 列**）、`auditedBy`/`auditedAt` 全是 `exist=false`。对它们的断言**永远不可能通过**，包括：
    - 期望「DB 往返等于写入值」（如 `assertEquals("{...json...}", found.getAiMappingResult())`）→ 必挂；
    - 用它们构造 `LambdaQueryWrapper` 条件 → `MyBatisSystemException`（MP 无法解析列）。
    - **正确写法**：正向断言真实列 + `assertNull(loaded.getGhostField())` 负向锁死。
    - **判断依据**：`@TableField` 注解只代表「作者以为」，写测试前必须用 `information_schema.columns` 确认列真实存在。

16. **「反向缺口」：真实列存在但 Entity 未声明（2026-09-29 REQ-126 沉淀，与第 10/13 条同源但方向相反）**：`@TableField(exist = false)` 还有一种更隐蔽的误用 —— **DB 列真实存在，Entity 却标成不存在**：
    - `BudgetEntryEntity.updatedAt` 标 `exist=false`，而 `t_budget_entry.updated_at` **真实存在**（与 `t_budget_adjustment` 对称）⇒ 该列从不写入、读回恒 null；
    - `t_budget.used_amount` 是 `NOT NULL DEFAULT 0` 的真实列，**Entity 连字段都没有** ⇒ 头级使用额永不回读。
    - **危害比幽灵字段更隐蔽**：列在 `\d` 里一眼可见，不查 `information_schema` 根本发现不了；且 MP 只映射已声明字段，故**不报错、不警告、只是永远读不到**。
    - **预防**：`check-entity-schema.mjs` 应双向校验 —— 不仅查「Entity 引用了 DB 没有的列」，还要查「DB 有 NOT NULL/业务关键列但 Entity 未声明」。本轮靠人工发现，工具尚未覆盖。

17. **Mapper 返回 `Map` 时，key 大小写必须与 Service 读法对齐（2026-09-29 REQ-126 沉淀，Mock 盲区的典型变体）**：`@Select("SELECT be.* ...")` 返回的 key 是 **snake_case**（`used_amount`/`control_type`），而 Service 按 **camelCase** 读（`entry.get("usedAmount")`）⇒ **真实 DB 恒读出 null，Mock 夹具却恰好写 camelCase 而全绿**。
    - 在预算里这直接引发 `switch(controlType)` **NPE**（`/budget/check` 一调即崩），且 `executionAnalysis` 的 `totalUsed` 恒 0 —— 两处都靠 Mock 测不出来。
    - **正解**（治本）：SQL 里显式别名 `be.used_amount AS "usedAmount"`。
    - **临时兜底**（本轮采用）：Service 侧加 `pick(row, camelKey, snakeKey)` 兼容两种 key。
    - **教训**：凡 `@Select` 返回 `Map<String,Object>` 的方法，**必须在真实 DB 上验证一次**，Mock 夹具的 key 名不能作为「SQL 会返回什么」的依据（补强第 7 条 Mock 盲区：Mock 只能证明 Service 分支逻辑，不能证明 key 名正确）。

18. **代码按标准科目体系引用科目，但种子没种全 ⇒ 生产 NPE（2026-09-29 REQ-127 沉淀，P55 未竟项）**：`AutoGenerationService` 硬编码引用 `1002/1122/2203/1123/2202/1012/1221/2211/6603`，而 `t_subject` 实际**缺 4 个**（`2203` 预收账款、`1221` 其他应收款、`2211` 应付职工薪酬、`6603` 财务费用）。P55 只把利息/手续费科目**改指 6603** 并加了事后 null 守卫，却**从未把 6603 种进库** ⇒ 缺陷跨月潜伏。
    - **症状**：`generateDocThenVoucher` 的 `arAcct.getId()` NPE，报错文案是 `because "arAcct" is null` —— **不含科目代码**，用户无从得知缺哪个科目。
    - **两层修法缺一不可**：① 引用处 `requireSubject(code)` 抛 `BusinessException` 并**指明代码**（铁律 #14）；② 补种子 migration。只做①则功能仍不可用，只做②则换个科目又崩。
    - **教训**：**改引用必须同步验种子**。凡新增/改动硬编码科目代码，先 `SELECT count(*) FROM t_subject WHERE code='XXXX'`（含 `deleted` 过滤前也要看），再决定「补种子」还是「改引用」。
    - **审计法**：`grep -o 'findSubjectByCode("[0-9]\{4\}")' | sort -u` 提取全部代码，与库内实际 code 求差集 —— 一次性挖出全部缺失科目，比逐个崩溃再补高效。

19. **「CHECK 允许集漏掉设计态」比「写错值」更隐蔽（2026-09-30 D8 沉淀，与第 9/14 条同源但更难发现）**：`chk_stmt_match_status` 允许集为 `('UNMATCHED','MATCHED','MANUAL_MATCHED','IGNORED')`，**不含 `PENDING_CONFIRM`**，而 `BankReconciliationServiceImpl:332` 在自动匹配 60-84 分档写它 ⇒ 该分档**必然抛 SQL 错**。
    - **为什么比第 9 条难**：该值**不是照抄别处的错值，而是整个功能的设计意图** —— `summarize()` 按它统计待确认数、`unmatchedItems()` 按它归入未达账项、Controller `@Operation` 文档写明「60-84 PENDING_CONFIRM」、Service 注释三处描述它。**所有这些都自洽地"证明"它合法**，唯独 DB 不认。
    - **判据**：凡代码写入某状态值，除了 `pg_get_constraintdef` 查证，还要问一句「**这个值是不是某个状态机的合法中间态**」——若是，说明**约束漏了它**（补约束），而不是代码该改（改代码会连带推翻 summarize/Controller 语义）。
    - **衍生铁律**：D4 落地时若把 `rejectMatch` 门禁只放行 `MANUAL_MATCHED`，auto-match 产出的 `PENDING_CONFIRM` 行就**永远无法人工驳回** ⇒ 违反人审铁律 #1。**任何"待人工决策"态都必须同时是 confirm 和 reject 的合法输入。**
    - 🔴 **反证时 Flyway 会把回退"补"回来**：为证明红，先 `psql` 回退约束会被下次 `mvn test` 启动的 Flyway 再次应用 `V162` 抹平（假绿）⇒ 必须 **src + target/classes 双删迁移文件**（与 §4.5 target/classes 坑同源）才能拿到真红。

20. **判「Entity 缺某字段」前必须看父类（2026-09-30 D2 调查修正，直接推翻原判断）**：`VoucherEntity`/`VoucherEntryEntity` 类体里 grep 不到 `deleted`，我据此判定「MP 逻辑删除未生效、`deleteById` 是物理删」，并据此向用户提了两个方案（B1 仅分录 / B2 含父表）。**实际 `BaseEntity:43-44` 就有 `@TableLogic private Integer deleted;`**，两实体继承之 ⇒ **MP 逻辑删除本已生效**，方案 B2 的「Entity 补字段」根本不需要做，父表也不存在 CASCADE 抹审计问题。
    - **本可被两条线索当场否掉**：① 既有测试 `VoucherEntryMapperRealDBTest:49,76` 早在调 `s.setDeleted(0)`/`e.setDeleted(0)` —— **能编译就证明字段存在**；② 全局 `application.yml:69-70` 已配 `logic-delete-field: deleted`，MP 只对**声明了该字段**的实体生效，两者矛盾时该先怀疑自己看漏了。
    - **铁律**：任何「Entity 没有 X」的结论，必须同时 grep ① 父类 `BaseEntity` ② 既有测试的 `setXxx` 调用 ③ MP 全局逻辑删除配置。三者任一命中即证伪。
    - **同源提醒**：`@TableField(exist = false)` 有三种误用方向（第 8 条"指向不存在的列"、第 10 条"幽灵字段"、第 16 条"真实列被标不存在"），本条是第四种 —— **"父类已声明却以为自己没声明"**。

### 4.3 测试类
6. **测试假阳性**：测试通过 ≠ 功能完成。跨实体链路必须真实贯通，不能只测单个模块 CRUD。E2E 测试必须模拟真实用户操作路径
7. **Mock 测试盲区**：Mock 测试发现不了 DB 约束（NOT NULL、CHECK、UNIQUE）、Flyway 不匹配、SQL 语法错误。核心 Mapper 必须跑真实 DB 测试（Testcontainers）
8. **Service 签名变更同步**：扩展现有方法签名时，所有调用点（Controller、Service 实现、所有测试文件）必须同步更新
9. **双重遮蔽型测试假阳性：`assertNotNull` + fixture 镜像缺陷值**（2026-09-29 REQ-123 沉淀）：`MenuServiceImpl.getRoutesByUserId` 因 `"menu"`（小写）永远过滤不掉任何菜单，**用户路由恒空** —— 而这个缺陷从未被任何测试发现，靠的是两层遮蔽同时成立：
    - **断言只验「非 null」**：`assertNotNull(result)` 对「正确的非空树」和「错误的空列表」**同样通过**。凡是返回集合的接口，断言必须落到**内容**（`size()` + 关键字段）；
    - **fixture 值与缺陷值一模一样**：测试 `stubEntity()` 也写 `setType("menu")`，于是「生产写错、测试也写错」互相印证，看起来自洽。**fixture 必须用 DB 真实取值**（大写 `MENU`），否则测试是在给 bug 背书。
    - **正确做法**：正向断言内容 + 负向断言「不该做的没做」（BUTTON/DIR 必须被过滤、小写值不匹配）—— 这正是铁律「负向断言强制」的应用场景。

10. **测试断言可能落后于「有意的」生产重构**（2026-09-29 REQ-124 沉淀）：`ReconciliationServiceImpl.execute()` 有显式注释说明 P1-fix「统一核销写路径」把金额扣减延后到 `ArapSettlementService.approve()`（引用铁律 #1 人审铁律），而 3 个测试仍断言重构前的旧行为（`execute()` 后立刻 `CONFIRMED` + 金额已扣减）。**判据**：生产代码里若带着「为什么这样改」的注释且指向铁律，那是**有意重构**，该改测试而非生产代码；反之才是设计缺陷。
    - **识别信号**：`grep` 到 `// P1-fix: ...（人审铁律：...）` 这类注释 + 状态机出现「有前置校验却无任何方法可满足前置条件」的悬空状态（如 `SUBMITTED` 是终点而 `approve()` 要求 `CONFIRMED`）。
    - **禁止做法**：为了让旧断言变绿而放宽生产状态机（如把 `reverse()` 白名单加上 `SUBMITTED`），那是在拆掉人审铁律。
    - **正确做法**：测试走完整审批链（`execute` → `approve`），并**补「审批前金额不得变动」负向断言** —— 这条断言此前完全缺失，导致重构抽空的行为无人守门。
    - ⚠️ 顺带发现同源生产缺陷（REQ-2026-125 已修）：`ArapSettlementServiceImpl.logReconciliationLog()` 写审批日志时 `setTargetDocId(null)`，而 `ReconciliationServiceImpl.reverse()` 用 `reconLog.getTargetDocId()` 回查单据 → `selectById(null)` 返回 null → **金额静默不回滚却照常返回成功**。已改：按 `sourceDocType=SETTLEMENT` 委托 `ArapSettlementServiceImpl.reverse()` 红冲，缺 `targetDocId` 或单据不存在一律抛 `BusinessException`。
    - 🔴 **`if (doc != null)` 包住回滚逻辑 = 静默失败的典型反模式**（2026-09-29 REQ-2026-125 沉淀）：回查实体失败就整块 `skip`，但方法继续把日志置 `CANCELLED`/`REJECTED` 并返回成功 ⇒ DB 里留下「已反核销」状态而单据金额纹丝不动，**比直接抛异常危险得多**。凡「回滚/同步」类逻辑，回查不到实体**必须抛 `BusinessException`**。
    - 🔴 **「可空列」被当成「该空」**：`V144` 把 `target_doc_id` 放宽为可空（为解决核销单生命周期日志插入报错），于是 `logReconciliationLog()` 长期硬编码 `setTargetDocId(null)`。但核销单明细本就指向真实业务单据，补写即可自描述。**DB 放宽约束只解决「插不进去」，不解决「信息缺失导致下游查不到」**。

11. **Mock 把「库里根本不存在的主数据」stub 成存在 ⇒ 缺陷两年不暴露**（2026-09-29 REQ-127 沉淀，补强第 9 条）：`AutoGenerationServiceTest` 对 `2203` 预收账款写了 `stubSubject("2203", 2203L)`，而**生产库根本没有 2203** —— 测试替种子「补」了一行，缺陷因此完全不可见。
    - **两层遮蔽同时成立**：① 早期用例更粗，`subjectMapper.selectList(any())` **与 code 无关地回任何科目**（问 2203 也回 1122），连 `queryingCode` 匹配器都不用；② 后来的用例「精确」到 code，却精确地 stub 了一个**不存在的真实数据**。
    - **识别法**：`grep` 夹具里的科目/单据代码，与 `SELECT code FROM t_subject` 求差集 —— 测试里出现而库里没有的 code，就是被 stub 出来的幻觉数据。
    - **正确做法**：缺数据的场景要**显式留空**（不 stub → 默认空集合 → 触发防御分支）并断言异常；真实 DB 类则应真跑一次全新库迁移。
    - 🔴 **补种子后，测试夹具会撞唯一约束**（同源副作用）：V159 种入 6603 后，`VoucherEntryMapperRealDBTest.insertProfitSubject("6603",…)` 立即报 `duplicate key ... uq_subject_code_ent`。**夹具凡插主数据一律 find-or-insert**（先 select 再决定是否 insert），否则每补一次种子就要炸一批测试。同理，正文里凡 `insertXxx(code)` 这类造数助手都该如此。

### 4.4 技术类
7. **Jackson LocalDateTime 序列化**：`application.yml` 的 `date-format` 对 `LocalDateTime` 无效，必须注册专用序列化器
8. **Mockito `any()` 歧义**：MyBatis-Plus `updateById` 有双签名，必须用 `any(Entity.class)` 明确类型
9. **Flyway migration 漂移**：V 版本号必须连续，重复版本会导致迁移失败。迁移文件 commit 后不会自动执行，必须重启应用
10. **Flyway 重复版本**：新建迁移时版本号不得与已有文件冲突（V130 与 V140 各出现两份）。重命名后 `validate-on-migrate: false` 临时绕过校验，因 V130 修改导致后续校验链全部失效
11. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端
11. **MyBatis-Plus `updateById` 忽略 null 字段 → UpdateWrapper 双保险**（2026-09-11 反核销幽灵凭证修复，连续踩坑三次沉淀）：`updateById` 默认 NOT_NULL 策略，**实体字段置 null 不会更新对应 DB 列**（该列保持旧值）。凡需把某列更新为 NULL，必须：`①实体字段 setXxx(null)`（防止后续 `updateById` 把内存旧值回写覆盖）＋`②mapper.update(null, new UpdateWrapper<>().eq("id",id).set("col", null))`（显式清列）。两者缺一不可——只做 ① 列不清空，只做 ② 被 ① 的 updateById 回写。实例：`ArapSettlementServiceImpl.reverse()` 清 `voucher_id`、`restoreUnsettledAmount()` 清 `voucher_id`/`voucher_no`。**预防**：代码评审见到「需要置空某列」必须双查这两点；新增同类逻辑参考 SPC-111。

12. **`@Version` 乐观锁的两个陷阱**（2026-09-29 慢测 REQ-118 沉淀）：
    - **MyBatis-Plus 不把 `@Version` 列的 DB 默认值回填到插入时的内存对象**。若实体用 DB 默认值（如 `version DEFAULT 1`）而测试插入后直接拿原对象 `updateById`，`version` 仍为 null，`OptimisticLockerInnerInterceptor` 遇 null 会**同时跳过版本条件与递增**（既不校验并发也不 bump）。正确用法：插入后**重新 `selectById` 拿到实体**再更新。
    - **同一 SqlSession 内两次 `selectById` 返回同一个对象实例**（一级缓存），且更新成功后新 version 会回写进该对象。故「拿两个实体分别代表新旧 version」的乐观锁测试是**假的**——两个其实是同一个、且 version 已被刷新。必须**显式构造**一个带过期 version 的实体来验证「过期 version 更新命中 0 行」。

13. **测试基类 `@Transactional` 与 `REQUIRES_NEW` 互斥**（2026-09-29 慢测 REQ-118 沉淀）：`AbstractMapperTest` 类上带 `@Transactional`，每个测试方法结束即回滚。被测代码若内部开 `REQUIRES_NEW` 事务（如 `AutoGenerationService.autoGenerateInNewTx`），**新事务看不到外层未提交的夹具数据**，会报「记录不存在」这类看似无关的错误。修法：该测试类显式声明 `@Transactional(propagation = Propagation.NOT_SUPPORTED)` 关闭回滚，改由 `@BeforeEach` 自行清理数据（此时 `@BeforeEach` 基类设置的 `EnterpriseContextHolder` 与各类 id 缓存仍生效）。

14. **MyBatis-Plus 乐观锁冲突不抛异常、只返回 0 行**（2026-09-29 慢测 REQ-121 沉淀，补强第 12 条）：`OptimisticLockerInnerInterceptor`（已注册于 `MyBatisPlusConfig:24`）的机制是「把 `version` 条件塞进 WHERE，命中 0 行即视为冲突」，**不抛任何异常**。故 `assertThrows(...)` 形式的乐观锁测试是**永假的**（永远等不到异常），必须断言 `assertEquals(0, mapper.updateById(stale))` 并补一条「数据未被改动」的负向断言。

15. **自动生成逻辑常写在 Service 而非 Mapper（2026-09-29 慢测 REQ-121 沉淀）**：`PeriodServiceImpl.save()` **覆写了 MyBatis-Plus 的 `save`**，在其中生成 `periodCode`/`startDate`/`endDate`。测试若直接 `periodMapper.insert(entity)` 就**绕过了全部生成逻辑**，断言必然失败。**教训**：断言「自动生成」类行为前，先确认该逻辑挂在哪一层（Service 覆写 / 拦截器 / MetaObjectHandler），用对入口再写断言，别想当然调 Mapper。

16. **`selectCount(null)` 是全表计数 ⇒ 测试隔离越界（2026-09-29 慢测 REQ-121 沉淀）**：`selectCount(null)` 不带任何条件，会把种子数据和其它用例残留行一并计入 —— 慢测全量跑时得到 11 而非用例自己的 4 条。**修法**：每个用例造数时带唯一前缀（如 `"9999.E2E.KW.DOC."`），断言时用 `likeRight`/`eq` 收敛到自己的数据。**同类高危写法**：`SELECT count(*)` 裸查 + 断言固定总数（`SystemClearControllerIntegrationTest` 即栽在这），断言前必须先取 baseline 再做增量比较。
    - 🔴 **全表维护操作返回的行数天然含种子数据**（REQ-122 定位）：`SystemClearController.clearBusinessDocs()` 是 6 个无 `WHERE` 的 `DELETE` + 2 个全表解绑 `UPDATE`，其 `deleted` 必然包含库中**原有行** —— 实测 `t_business_doc` 有 **7 条 Flyway 种子行**，故单据删除数是 8 而非用例造的 1 条。**这类接口的返回行数本质上无法断言绝对值**，必须「先取基线、再断言增量」（`baseline + 本用例新增行数`）。**注意区分**：`assertEquals(0, 清空后 count(*))` 是**成立**的（清空即全表语义），要改的只是操作前的行数断言；而「保留型」断言（如「银行日记账应保留」）要从 `assertEquals(1, 总数)` 改成 `baseline + 1` —— 原写法往往只是**侥幸**通过（基线恰为 0）。

17. **测试可能针对「尚未建模」的功能（2026-09-29 慢测 REQ-121 沉淀）**：`BudgetFlowE2ETest` 断言部门/项目维度、逐条目使用额、控制方式（BLOCK/WARN），但 `t_budget_entry` 实际只有 10 列，`deptId`/`projectId`/`periodMonth`/`controlType`/`usedAmount` **全是幽灵字段**；于是 `checkBudget` 读到 `controlType=null` → `switch(null)` **NPE**，`addUsedAmount` 的 UPDATE 引用不存在的 `used_amount` 列。**这类不是「测试数据填错」，而是功能未实现** —— 继续改测试只会掩盖缺口。**识别信号**：Entity 里成片 `exist=false` + 生产代码直接读这些字段。**正确处置**：判为功能缺口走 SPEC 立项，测试保持失败待实现，**禁止把断言改弱来「做绿」**。

### 4.4 文档治理类
12. **SPEC 漂移检测**：架构变更后（如 P33→P34），SPEC 仍描述旧架构。每次大变更后必做 SPEC vs 代码一致性审计
13. **DIR 提交**：开发中发现需求模糊/规范缺失/流程不准确时，提交 DIR 到 `docs/dir/`，迭代结束时统一回写
14. **文档版本联动**：SPEC 版本号必须与 DESIGN.md 联动，修改代码后同步更新文档版本历史
15. **设计文档冲突**：DESIGN.md 变更后，检查 `docs/development/` 下所有文档是否冲突，冲突文件移入 `archive/`

### 4.5 工具链类
16. **OpenCode 委派失败**：worker 异常退出 → Hermes 直写 + 留任务书
17. **`git add <dir>` 陷阱**：add 目录后必查 `git status --short`，防止卷进 IDE 自动生成文件
18. **`execute_code` 工具返回空**：沙箱内 `terminal()`/`read_file()` 可能返回空，改用 `subprocess.run()` 直接调系统命令
19. **Maven JDK 21 编译**：`maven-compiler-plugin` 必须 ≥ 3.12.0，否则 `--release 21` 报错

20. 🔴 **surefire `<argLine>` 覆盖 ⇒ JaCoCo 门禁永久静默跳过（假绿，2026-09-30 P104 收口实证）**：`jacoco:prepare-agent` 在 `initialize` 阶段**设置 `argLine` 属性**，surefire 到 `test` 阶段才取值。若 `pom.xml` 的 surefire 写了**字面量** `<argLine>-Xmx...</argLine>`（不含 `@{argLine}`），该字面量会**整体覆盖**属性 → agent 从未挂载 → 无 `target/jacoco.exec` → `report` 与 `check` 双双打印 `Skipping JaCoCo execution due to missing execution data file` → **构建照样 SUCCESS**。
    - **危害等级高于普通缺陷**：门禁「看起来在管」，实则零信号。本项目 `70%` 覆盖率门禁因此**从未执行过一次**，此前所有「测试全过 ⇒ 覆盖率达标」的结论全是假绿。
    - **正解**：`<argLine>@{argLine} -Xmx...</argLine>`。用 `@{}`（延迟替换）而非 `${}`（立即展开），未启用 JaCoCo 时替换为空串而非字面量。
    - **反证判据**（下次遇到先查这两条，别信绿灯）：① `grep -c 'Skipping JaCoCo execution' <ci.log>` 必须为 0；② 必须出现 `All coverage checks have been met` 或 `Rule violated` 之一（两者皆无 = 门禁没跑）。
    - 🔴 **删冗余 `mvn jacoco:check` 步骤反而是对的**：命令行 `jacoco:check` 走 `default-cli` execution，**拿不到 pom 绑定 execution 里的 `<rules>`**，必然报 `The parameters 'rules' ... are missing or invalid` 而假失败。覆盖率门禁随 `mvn test` 即可生效（`<phase>test</phase>`），**不要重复调**。

21. 🔴 **「绿灯」不等于「通过」：门禁自身的执行也必须验证**（2026-09-30 四门禁连查，同日四个 workflow 在 main/develop/feature 上**历史上 100% 全红**）
    | 门禁 | 表面 | 实际真相 |
    |------|------|---------|
    | SPEC 契约 | 红 | **真红**：96 份 SPEC 中 91 份无机器可读契约 + 校验器 5 层缺陷（崩溃/围栏提取/类型未分派/ID 格式强压/缺契约判失败） |
    | L1 前端 | 红 | **真红**：`npm test -- --coverage` 但 `@vitest/coverage-v8` 从未安装 |
    | L1 Java | 红 | **假红**：1757 用例全过，挂在冗余 `mvn jacoco:check`（见第 20 条）|
    | L1 覆盖率 | 绿 | **假绿**：静默跳过，从未执行（见第 20 条）|
    | L2 真库 | 红 | **真红**：workflow **从无 `services:` 块**，Redis 只被 `docker pull` 从未启动 → 11 个 `RedisConnectionFailure` |
    | Full Stack 路由 | 红 | **假红**：`check_route_coverage.py` 只解析 `routes/base.ts`（96 条路由只认出 23 条）→ 75 个组件被误判孤儿 |
    - **教训**：判断门禁有效性**必须看它自己的输出**，不能看它的退出码或颜色。四类失效模式：① 恒红（阈值/规则不可达成）② 恒绿（规则根本没执行）③ 假红（工具自身缺陷）④ 输入不全（只读了一部分数据源）。
    - **判「恒红」通用解法**：把不可达成的硬失败**降级为警告 + 留强制开关**。SPEC 门禁用 `--require-contract`、覆盖率用实测值做**棘轮**、路由检查只对「路由指向不存在组件」判失败 —— 原则一致：**门禁必须可执行且能变红，否则等于没有**。
    - **判「恒绿」通用解法（2026-10-03 补第三例）**：先量出真实存量，再把上限设成实测值做**棘轮**，然后**删掉 `continue-on-error`**。已用三次：SPEC 契约覆盖率、覆盖率阈值、接口覆盖 `--max-uncovered 280`（基线：后端 425 端点、前端 148 调用、匹配 145、未接 **280**、孤儿 0）。**要点**：上限必须写在 CI 命令行里（可审计），且每次清掉一批存量就要下调 —— 否则棘轮又变成固定上限（见第 25 条）。

22. **多文件配置只读其一 ⇒ 误报成批**（2026-09-30）：`check_route_coverage.py` 硬编码单文件 `base.ts`，而路由实际拆在 8 个文件（`agency/base/lab/sme-asset/sme-base/sme-business/sme-report/sme-tax`），96 条只认出 23 条。同类：validator 用 glob `P*-*.md` 却假设单一 schema。**教训：解析多份配置必须 `glob` 全部，且改完立刻跑一遍看「认出的条数」是否与实际数量级相符**（23 vs 96 这种量级差一眼可见）。

23. **安全加固会静默改写测试造数，症状伪装成「隔离失效」**（2026-09-30，源自 DIR-001，REQ-2026-129/P102-M2）：把 `MyMetaObjectHandler.insertFill` 的 `enterpriseId` 从 `strictInsertFill` 改为**无条件覆盖**（正确修复，堵死 48 处 Entity 直入越权）后，所有「只给实体硬设 `enterpriseId` 而未切上下文」的夹具被静默改写。实测 3 处回归，报错信息与真实原因**完全无关**：科目冲突报「唯一键冲突」、隔离用例报「企业 B 的数据不应被查到」—— 后者极易被误判为*隔离机制失效*，进而反向把生产逻辑改松。
    - **正确范式**（项目内已有范例：`OpeningContinuityRealDBTest` / `AuxiliaryDetailRealDBTest` / `CashSubjectBalanceRealDBTest` / `IncomeStatementCaliberRealDBTest`）：切**上下文** `useEnterprise(ENT_ID)`，不在实体上硬设；确需跨租户造数用 `AbstractMapperTest#withoutEnterpriseContext` 显式出口并在注释说明意图。
    - **机械守卫**：`scripts/check_tenant_fixture.py` 已写好并反证过（注入两处违规 exit 1、真实仓库 exit 0），**2026-10-03 已挂进 `full-stack-test.yml` 的阻断式门禁** `tenant-fixture-check`（不加 `continue-on-error`）。反证手法可复用：把某测试类的 `useEnterprise(...)` 行删掉再跑脚本，应 exit 1 并精确指出行号。
    - ⚠️ **守卫自身也有盲区（DIR-002，2026-10-03 已处理主要项）**：早期版本把「文件内出现 `useEnterprise(` 或 `withoutEnterpriseContext(`」当合规 ⇒ 既漏「上下文=A 而实体=B」的**静默错配**（代码正常编译、跑绿、无报错，症状是别的用例断言莫名失败），也会被纯文本替换骗过。已升级为 **A/B 两类判定**：A = 设了非默认企业号却没管上下文；B = `useEnterprise(X)` 与 `setEnterpriseId(Y)` 的 Y 不一致。**残留盲区 3 条**（求值不了的变量、默认值不参与 B 比对、`EnterpriseContextHolder.set()` 第三种写法）已写在脚本 docstring 里 —— **机械守卫只能挡住不自觉的违规，不能当唯一保障。**
    - **配套教训**：加固类改动必须先问「谁在依赖被改掉的旧行为」，且**守卫脚本不接 CI 等于没有**（本条从「已写好」到「已挂门禁」隔了 3 天）。
    - ⚠️ **判断「是不是默认值」必须求值、不能看常量名**：`private static final Long ENTERPRISE_ID = 1L;` 名字像非默认、值其实是默认；按名字判断会产生成片误报（本轮实测 21 处 / 4 个文件）。

24. **本地跑 L2 有两个隐含前置，漏一个就会把环境问题误判为代码回归**（2026-10-03，DIR-003 实证）：`mvn test -DexcludedGroups=` 本机实测 **5 分钟**跑完 2023 个用例（并不需要"留夜间"），但两个前置没满足时报错**与真缺陷无法区分**：
    - **Redis 必须先起**：`docker start huicai-redis`（`application.yml:9-11` 指向 `localhost:6379`）。未起时 `LedgerChainRealDBTest` / `VoucherIntegrationTest` / `BankStatementAuditIntegrationTest` 共 **11 个用例报 `RedisConnectionFailure`** —— 而 CI 侧 `l2-integration-test.yml` 有 `services.redis`，故**只在本地出现**。起 Redis 后同 3 类 **12/12 全绿**，证明非代码回归。
    - **Testcontainers 需 Docker 守护可用**：否则表现为 `ConnectException` / `HikariPool - Connection is not available`。
    - **判据**：报错里出现 `RedisConnectionFailure` / `ConnectException` 且**栈顶不在业务断言**时，先查环境再查代码。**「本地全绿 ⇒ 安全」不成立，「本地 L2 红 ⇒ 代码坏」同样不成立。**

25. 🔴 **阈值标定后不重抬 ⇒ 棘轮退化为固定下限（2026-10-03 实测发现）**：覆盖率门禁 2026-09-30 按实测 32%/13%/56% 标定为 30%/12%/55%，三天后实测已升到 **38.9%/17.9%/61.8%**，而**阈值一步未抬** ⇒ 留了 8.9/5.9/6.8 个百分点的**静默回退空间**：覆盖率可以从 38.9% 一路跌到 30% 全程无人红。**「门禁会红」与「门禁卡得住」是两件事** —— 前者只需阈值不等于实测值（已实测会红），后者要求阈值紧贴实测值。
    - **铁律**：**每次因补测试而提升覆盖率后，必须回来重抬阈值**；否则「棘轮」只是个名字。同类形态：任何「按当时实测值标定的下限」都会随代码演进而失效（阈值、扫描器存量、白名单都是快照）。
    - **正确做法**：阈值写在**注释里连同实测值与标定日期**一起维护（`backend/pom.xml` 的 jacoco-check 注释块已按此重写，含口径说明：约束项是 L1，L2 更高）；标定依据用 `target/site/jacoco/jacoco.csv` 按包聚合，不要抄构建日志里的四舍五入值。
    - 🔴 **但标定必须 `mvn clean test`，否则实测值虚高约 6 个百分点**（2026-10-03 实测踩坑）：`target/jacoco.exec` **跨 Maven 调用累积** —— 前一次 L2（`-DexcludedGroups=`，2023 用例）写入的覆盖数据不会自动清掉，与后一次 L1（1634 用例）叠加，于是 `jacoco.csv` 读到 38.9/17.9/61.8，而 clean 口径真值只有 **32.3/13.9/55.1**。据此把阈值抬到 36/16/58 后，**真实 CI 两个 L1 job 直接 BUILD FAILURE**（`Rule violated ... 0.32/0.13/0.55`）。**CI 每次都是全新 checkout（无 `target/`），故 CI 报出的值才是真值。**
    - **顺带**：反证阈值本身也别忘 —— 把阈值临时抬到 0.99 跑一次，若不报 `Rule violated` 说明门禁根本没执行（呼应第 20 条的假绿形态），反证完记得 `git checkout --` 还原。

26. 🔴 **XML 注释里不能用非 BMP 字符（emoji），否则 Maven 直接读不了 POM**（2026-10-03 实测）：给 `pom.xml` 的注释加 🔺（U+1F53A）后，`mvn test` 在 **Scanning for projects 阶段**就炸：
    ```
    Non-parseable POM ... Illegal character 0xd83d found in comment
    ```
    原因是 Java 的 XML 解析器按 UTF-16 处理，非 BMP 字符是**代理对**（高字节 `0xd83d`）⇒ 注释里出现即致命，且**报错位置指向 `END_TAG seen`**，与真正的出错字符相隔很远，肉眼很难定位。
    - **判据**：改 `pom.xml`（或其它 XML）注释后，若报 `Non-parseable POM` / `Illegal character 0x???? found in comment`，先找注释里的 **emoji 与其它非 BMP 字符**（🔺🔴⚡️ 类，U+1F000 以上），BMP 内的 `⚠ ✅ ❌ → ⇒` 没问题。
    - **同源提醒**：Markdown 文档（`AGENTS.md` / SPEC）里可以用 emoji，**XML 里不行** —— 同一段文字复制到两处时容易只改一半。


27. 🔴 **运行手册里写得像想当然的方案，可能被数据库/框架的内置保护直接拒绝**（2026-10-03 实测，M5b 角色降权）：手册写「`ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS` 让 RLS 生效」，执行时报 `permission denied to alter role` / `DETAIL: The bootstrap user must have the SUPERUSER attribute.` —— **PostgreSQL 保护 bootstrap 超级用户，不允许摘掉其超级用户属性**。同一语句改**别的**角色却成功 ⇒ 不是权限问题，是数据库内置保护，读文档与查权限都看不出来。
    - **判据**：任何「改数据库/框架全局属性」的方案，执行前先用一个**一次性的探针角色/对象**验证这条语句本身是否被接受，别直接对生产对象下手。
    - **配套**：探针要能区分「三层原因」——① 属性是否可改（探针角色对照）；② 改完后行为是否真变（用同一查询在「无参 / 目标值 / 其它值」三种条件下对比）；③ **本次差点漏掉的一层**：超级用户**本身就绕过 RLS**，所以 `rolbypassrls=false` 对超级用户毫无意义 —— 只看 `pg_roles` 的属性值会误判为「已生效」。**判据：看行为，不只看属性。**
    - **同源提醒**：`NO FORCE` 类「兜底层」（FORCE RLS、fail-closed、乐观锁）对**最高权限主体**往往无效，因为最高权限本身就绕过；这类结论必须用探针实测，不能靠推演。
    - 🔴 **推论（2026-10-03 M5b-V2 实测，最贵的一条）**：**「兜底层已就绪」这类结论若是在最高权限主体下验证的，就永远无法证伪。** P102 交付「`TenantRlsInitializer` 已让 RLS 具备生效条件」时，验证环境是超级用户 ⇒ RLS 恒被绕过 ⇒ **该结论不可能被任何测试证伪**；直到真正降权，租户表立刻全部读 0 行（`pg_stat_activity` 显示 GUC 恒为 NULL），才暴露出这层机制**从未工作**。
      - **判据**：任何「兜底层/纵深防御」交付，必须回答「**在最低权限主体下它还成立吗**」；验证方式是把主体降级后跑一次**真实业务读路径**（不是探针 SQL）。
      - **反模式**：用「探针角色跑 SQL 有效」代替「应用跑业务有效」—— 前者只证明**策略谓词**正确，后者才证明**机制**接通；本次两者结论相反。
    - ⚠️ **两条并列的「静默失效」都被这次降权一次性挖出**（都属「看起来在工作、实际从未工作」）：①**切面与事务 advice 顺序不确定** —— 二者默认同为 `Ordered.LOWEST_PRECEDENCE`，`SET LOCAL` 落在事务外的自动提交连接上会被立刻丢弃 ⇒ 治法是**显式**给事务 advice order（`@EnableTransactionManagement(order = 0)`），并且**加了这个 `@Configuration` 后必须补回 `proxyTargetClass = true`**，否则 Boot 自动配置退让导致代理失效、变成另一个假绿；②**方法/类根本没有事务** —— 全库 75 个 `*ServiceImpl` 里 29 个一个 `@Transactional` 都没有，读路径切面压根不触发。**教训：「有注解」不等于「会生效」，注解只在一批「没写注解的方法」之外生效，而没人统计过那批。**

28. ✅ **【已修复，V167】** **`SET LOCAL` 结束后的「空串」会让 `::bigint` 谓词抛 SQL 错**（2026-10-03 实测并修复）：PostgreSQL 在「曾执行过 `SET LOCAL` 的事务」结束后，`current_setting('app.enterprise_id', true)` 读回的是**空串**（全新会话才是 NULL）。原谓词 `current_setting(...)::bigint` 因此会让**无企业上下文的事务**（定时任务 / 初始化 / 批处理）复用该连接时抛 `invalid input syntax for type bigint: ""`，即把 fail-closed 变成 **fail-500**。
    - **修法**（V167 + `P108-rls-predicate-empty-string-hardening.md`）：谓词改 `NULLIF(current_setting('app.enterprise_id', true), '')::bigint` —— 空串归 NULL ⇒ 返 0 行。**70 张表重写完毕，红→绿反证已做**（移除 V167 ⇒ 用例报 `invalid input syntax` 变红）。
    - **判据（写谓词前必做）**：任何 `current_setting(...)::type` 谓词，都要为**三种取值形态**设计 —— 具体值 / `''`（曾 `SET LOCAL` 过）/ NULL（全新会话），且必须在**同一会话**里跑一遍「BEGIN; SET LOCAL …; COMMIT; 再查」确认，别只在全新会话里验一次。
    - **为什么此前没人发现**：超级用户绕过 RLS，**根本执行不到谓词** —— 与第 27 条同源。

29. 🔴 **会话级 `set_config(..., false)` 会把脏值留在连接池连接上 ⇒ 跨用例污染，且「本地绿、CI 红」**（2026-10-03 实测，PR #26）：`TenantRlsRealDBTest` 用 `set_config('app.enterprise_id', '987654', false)`（第三参 `false` = **会话级**，不是事务级）在自动提交下执行，值**永久留在连接上**；随后运行的 `TenantRlsGucRealDBTest` 读到 `987654` ⇒ **CI 上 2 条用例红，而本地全绿**（本地只是连接复用顺序不同）。
    - **两个都要做**：① **污染源**改为事务级（`is_local=true`），非事务场景必须在 `finally` 里 `RESET app.enterprise_id`；② **受害用例**在断言前主动 `RESET`，使断言**与执行顺序无关** —— 只修①是靠运气，只修②是掩盖污染源。
    - **判据**：任何 `set_config`/`SET`（会话级）碰到连接池，就必须问「这个值什么时候被清掉」。断言若依赖「当前连接的干净状态」，必须自己先清理，不能指望前一个用例自觉。
    - **同源提醒**：「本地全绿 ⇒ 安全」又一次被推翻 —— 这类污染**只在连接复用顺序不同的环境暴露**，本地复现要靠 `-Dsurefire.runOrder=reverse` 之类手段。

30. 🔴 **新增 `@Data` DTO 会直接拉低方法覆盖率并让门禁变红 —— 这不是「覆盖率退步」，是「DTO 覆盖率税」（2026-10-05 DTO 批次③ 实测，2026-10-06 补充分支维度）**：本项目 JaCoCo **把 Lombok 生成的 getter/setter 计入方法数**（未按 `@lombok.Generated` 过滤）。实测加 8 个 DTO：方法总数 **6610 → 6780（+170，约 21/个）**，已覆盖只 **3609 → 3637（+28）** ⇒ METHOD 从 54.60% 掉到 **53.64%**，低于 54% 阈值，**真实 BUILD FAILURE**（`Rule violated ... 0.53, but expected minimum is 0.54`）。
    - **错误做法**：把阈值往下调「迁就」代码 —— 那是把门禁改成橡皮图章。
    - **正解**：补测试把税缴掉。新增 `DtoMappingContractTest` 用反射触碰每个 DTO 的**每个字段**（setter + getter 等值回读 + `toEntity()`），一次清零，且顺带锁住两条真实契约：**① DTO 每个字段都必须被 `toEntity` 映射**（漏映射 = 客户端提交被静默丢弃）；**② `toEntity` 不得凭空写入 DTO 里不存在的字段**（= 不得凭空写服务端托管字段）。修后 56.18%，阈值重抬 54% → 55%（§4.5 第 25 条要求）。
    - 🔴 **2026-10-06 补上的另一半：税不只在 METHOD，还在 BRANCH，且规模大得多**。`DtoMappingContractTest` 只做 setter/getter 回读，**从不调用 `equals`/`hashCode`** ⇒ 实测 105 个 DTO/VO 的 Lombok `equals`/`hashCode` 分支**原覆盖 2/5708（0.04%）**，而这 5708 个分支占整个 BRANCH 分母的约 **24%**。补 `DtoLombokBranchCoverageTest`（按类路径扫描全部 DTO/VO，对每个字段构造四条对照以覆盖 `this.f == null ? other.f != null : !this.f.equals(other.f)` 的两个分支与外层 if 的两个分支）后：**BRANCH 12.82% → 36.80%（+23.98 点）**，且因 Lombok 的 `equals` 内部会调 getter，**INSTRUCTION 31.77% → 46.27%、METHOD 57.17% → 65.52%** 同步大涨。阈值随即重抬 30/12/55 → **45/36/65**。
      - **四条路径缺一不可**（`equals` 遇首个不等字段即返回，故只能**逐字段**构造「前序字段全相等」的对照对）：①两对象该字段都 null ②都相等非 null ③this 侧 null、other 侧非 null ④反向。
      - ⚠️ **「DTO 覆盖率税」这个说法当时只按 METHOD 观测，漏了 BRANCH 维度** —— 与 §4.5 第 25 条「只按一个维度推断整体」同型。**判据：新增 `@Data` 类时，METHOD 与 BRANCH 两个维度都要估，只看方法数会低估一个数量级。**
      - ⚠️ 并非所有 DTO/VO 都有 Lombok `equals`：实测 `TaxBurdenVO` 是手写 POJO（只有 getter/setter，**根本没有 equals**）⇒ 走 Object 的身份 equals，不含分支，不属要缴的税。扫描时必须按「是否自声明 `equals(Object)`」过滤，否则会把身份 equals 当成覆盖失败。
    - **判据**：凡要新增一批 `@Data` DTO/VO，先预估「方法数 × 21」**与「分支数 × 约 45~66」**，并预留对应的反射测试；否则「纯样板代码」会把门禁拉红，而这与代码质量无关 —— 别误判为回归去改阈值。
    - **同源提醒**：DTO 的存在还带来**出参面**问题（`RoleController#create` 等仍 `R<RoleEntity>` 直出 Entity），本批未改（改出参会动前端契约），登记为待办；实测出参面共 **152 个返回点 / 30 个控制器 / 37 个不同 Entity 类型**。✅ **2026-10-06 出参面 VO 化已推进 4 批**：Role / Customer / Vendor / Employee（批次 1~3，19 个返回点）+ **批次 4 的 7 个干净控制器 / 29 个返回点**（Period / VoucherType / SummaryLib / SysConfig / AssetCategory / CashJournal / BankJournal）+ **批次 5 的 4 个干净控制器 / 21 个返回点**（BankAccount / AssetCard / ClassificationRule / AiTask+AiAnomalyTag）—— + **批次 6 的 1 个控制器 / 8 个返回点**（Prepayment —— 顺带修掉一个真实前端缺陷，见 §4.5 第 39 条）+ **批次 7 的 3 个干净控制器 / 13 个返回点**（Ticket / Attachment / AiFeedbackLog —— 三处前端都是 `Promise<any>` 无类型，已补 contract）—— 剩余 **64 个返回点 / 11 个控制器**（按 `R<...Entity>` 正则实测）。
      - ✅ **2026-10-06 实测：出参面 DTO 化的「税」已被上面那个扫描式测试预付掉**。`DtoLombokBranchCoverageTest` 按类路径扫描，新增 VO **无需再写测试**即自动覆盖其 getter（被 `equals` 调）/ setter（被本测试的 `build()` 调）/ `equals`+`hashCode` 分支 ⇒ DTO/VO 的**方法**覆盖已达 **1692/1781（95.0%）**、**分支**覆盖 **5561/5708（97.4%）**。⚠️ 但**余量很紧**：阈值重抬到 45/36/65 后，**BRANCH 只能再吃 533 个未覆盖分支、METHOD 只能再吃 57 个未覆盖方法**（≈ 2~3 个未覆盖的 `@Data` 类）⇒ 新增 DTO/VO **必须**落在扫描范围内（`@Data` 类或 record、有可构造途径），否则立刻把门禁拉红。**判据：新增 DTO/VO 前先确认它能被扫描测试覆盖（实测 `DtoLombokBranchCoverageTest` 会打印「扫描 N 个 / 无样例字段 M 个」，M 异常增大即覆盖不全）。**

31. 🔴 **字段级门禁对「无状态字段的 Entity 直收」完全失明（2026-10-05 结构性守卫发现）**：`scripts/check_entity_status_massassignment.py` 收尾时报「✅ 未发现实体入参的状态越权风险」，但它是按「Entity 有 status 等字段 + 被 `@RequestBody` 直收」判定的 —— `TaxTypeEntity`、`CustomerEntity`、`MenuEntity` 等**根本没有 status 字段**，于是**铁律 #13 的违规被整类漏判**。
    - **反证**：`TenantDtoIsolationStructureTest#noControllerTakesEntityRequestBody` 用**参数类型**判定（遍历 `com.huicai` 下全部 `@RestController` 的 POST/PUT 方法，检查 `@RequestBody` 参数是否为 `*Entity`），当场揪出门禁没报的 `TaxController#createTaxType/updateTaxType`。
    - **正解**：门禁**不能只按字段判**，必须配一个「按类型判」的反射断言；两者互补 —— 字段级判「哪些字段危险」，类型级判「哪些端点违规」。
    - **配套**：类型级守卫一上线就把剩余量暴露成 **26 处**，故用**棘轮**钉基线（`KNOWN_ENTITY_BODY_BASELINE` + `size==26`）：新增必红、清一处必须同步减清单并改 size（§4.5 第 21 条「恒绿/假绿」形态的第 4 类「输入不全」）。
    - **教训复用**：写任何「扫描型门禁」前先问一句 —— **它的判定依据是不是覆盖了违规的全部形态？** 只覆盖「有 status 的那一类」就等于给其余类别开了免检通道。

33. 🔴 **L2 Testcontainers 的连接角色是超级用户 ⇒ RLS 恒被绕过 ⇒ 隔离类断言是假绿**（2026-10-05 P106 批次 1a 实测）：`AbstractMapperTest:71-75` 用 `withUsername("test")`，而官方 postgres 镜像把 `POSTGRES_USER` 建为**超级用户**。探针实测（`TempRlsProbeTest`，已删）：`current_user=test`、**`rolsuper=t`**、`bypassrls=t`、`tableowner=test`，同时 `relrowsecurity=t` + **`relforcerowsecurity=t`** + `enterprise_policy` 1 条 + Flyway 到 **V167** ⇒ **配置全对，但谓词根本执行不到**（AGENTS §4.5 第 27 条的同型再现）。
    - **症状极具欺骗性**：任何「切到别的企业上下文应查不到」的断言在 L2 里**恒绿**，且绿灯与「隔离已修好」无法区分；本轮 `AccountSetIsolationRealDBTest` 首版就中招（切到企业 990002 后仍可见 1 行，本以为隔离失效，实为角色绕过）。
    - **正解**：隔离断言必须像 `TenantRlsRealDBTest` 那样**显式降权** —— `CREATE ROLE xxx NOSUPERUSER NOBYPASSRLS` + `GRANT SELECT` + 在 `setAutoCommit(false)` 的连接里 `SET LOCAL ROLE` + `set_config('app.enterprise_id', …, true)`，跑完 `rollback()`；**全库只有 `TenantRlsRealDBTest` 做对了**，其余测试类默认都跑在超级用户上。
    - **配套守卫**：断言前提本身要留一条测试（`l2RoleIsSuperuserSoRlsAssertionsNeedSetRole` 断言 `rolsuper=t`），否则将来 Testcontainers 改用非超级角色时，探针写法会静默失去意义而无人察觉。
    - **推论**：**L2 全绿不代表 RLS 有效**；涉 RLS 的验收必须在「开发库 + 非超级应用角色 `huicai_app`」或「L2 + 探针角色」两处之一做，二者缺一即为未验证。
34. 🔴 **Entity 不继承 `BaseEntity` 且全类无 `@TableField` ⇒ `MyMetaObjectHandler.insertFill` 压根不被调用 ⇒ `enterprise_id` 静默落 DB 默认值**（2026-10-05 P106 批次 1a-1 实测，与 §4.2 第 10/16 条同源但**机制不同**）：`PrepaymentEntity` 是 `implements Serializable`（非继承 `BaseEntity`），且**全类没有一个 `@TableField`/`FieldFill`** ⇒ MyBatis-Plus 的 `TableInfo.withInsertFill` 恒为 `false` ⇒ `insertFill` 回调**根本不会触发**（不是「触发了但没填」，是「压根没进」）⇒ `MyMetaObjectHandler:29-31` 那段「无条件覆盖为上下文企业」的修复对它**完全无效**；而 `t_prepayment.enterprise_id` 是 `V105` 加的 `NOT NULL DEFAULT 1` ⇒ **任何非企业 1 的上下文创建的预付款都落进企业 1**。
    - **为什么 §4.5 第 23 条那次加固没抓到**：`MyMetaObjectHandler` 是**元对象层**修复，只对「声明了 fill 的字段」生效；**它无法覆盖「实体压根没用 fill 机制」这一类**——`hasSetter("enterpriseId")` 为真也不够，MyBatis-Plus 在 `TableInfo` 阶段就已判定该表无需 fill。
    - **判据**：见 Entity 先查两件事 —— ①`extends BaseEntity`？②类体内有 `@TableField(fill=…)`？**两者皆否 ⇒ 该实体的 `enterprise_id`/`createdAt`/`updatedAt` 一律走 DB 默认值**，而 V102~V105 补的列全是 `NOT NULL DEFAULT 1`。
    - **影响面（已全量扫描）**：18 个实体无 `FieldFill`，其中 8 个不继承 `BaseEntity`；5 个声明 `enterpriseId`，但 `UserEntity`/`AgencyUserEnterpriseEntity`/`AgencyEnterpriseEntity` 的 `enterprise_id` **按设计就是「归属企业」而非上下文**（被覆盖反而是错的）⇒ **只有 `PrepaymentEntity` 是缺陷**。这也说明**不能用「有无 enterpriseId 字段」判定**，必须逐个看语义。
    - **治法（实测后收窄）**：**只能**给 `enterpriseId` 补 `@TableField(fill = FieldFill.INSERT)`。**不要**改用「继承 `BaseEntity`」—— 用真实库核对列类型即知会引入 **3 处不匹配**：`t_prepayment.created_at`/`updated_at` 是 `date`（基类 `LocalDateTime`）、`created_by` 是 `varchar(50)`（基类 `Long`）、且基类还有 `updated_by`/`version` 两列本表没有。⚠️ `PrepaymentEntity:12` 的类注释**早已写明**「不继承 BaseEntity，因为 createdBy(String)/createdAt(LocalDate)/updatedAt(LocalDate) 类型与基类不兼容」—— **这条既有注释就是正确线索，动手前应先读它**。**也不要**在 Service 里手填 `setEnterpriseId` —— 那样又回到「谁记得填」的老路。
    - **「有 fill 字段」是触发元对象回调的开关**：MyBatis-Plus 只看「该实体是否有任意一个字段声明了 `FieldFill`」来决定是否调 `insertFill`，**不看目标字段自己有没有注解** ⇒ 所以给 `enterpriseId` 单独加注解即可生效，无需其它字段配合。

32. 🔴 **git push 卡在 401 挑战 = HTTP/2 被中间设备打断，换 `http.version=HTTP/1.1` 即可（2026-10-05 实测）**：症状很有迷惑性 ——
    - `curl https://github.com/.../info/refs` **返回 200**、`Test-NetConnection -Port 443` **True**、
      `git push` 却**静默挂住**（无报错，`timeout` 杀掉后日志为空）；`GIT_TRACE_CURL=1` 显示流程停在
      **收到 401 的瞬间**（`www-authenticate: Basic realm="GitHub"` 之后没有下文）；
    - 同样地，`git ls-remote` 能成功（**公开仓库可匿名读**）⇒ **「ls-remote 通」不能证明 push 也通**，
      这是最容易被拿来当「网络没问题」证据的那条命令；
    - 判据与治法：`GIT_CURL_VERBOSE=1` 看到卡在 401 ⇒ 加 `-c http.version=HTTP/1.1`
      （ALPN 协商到 h2 后带认证的流会被重置，h1 立刻成功）。
    - ⚠️ **顺带一个独立坑**：`credential.helper=store --file=...` 的凭据文件**必须由 git 自己写**
      （`printf 'protocol=https\nhost=github.com\nusername=..\npassword=..\n\n' | git credential-store --file=X store`）。
      **手写等价的 `https://user:token@github.com` 一行不会被解析**（`credential-store get` 返回空 ⇒
      表现为 `could not read Username`，看着像「没有凭据」，实为「格式不被接受」）。
      PowerShell 写文件还要额外注意 `Set-Content -Encoding UTF8` 会带 **BOM**，同样导致解析失败。
    - **配合**：把凭据放进 `--file=/tmp/xxx` 而不是全局配置，用完即删；本次已删除。

35. 🔴 **过滤器只 `set` 从不清 ⇒ 「无上下文」类负向断言在测试里会恒绿（2026-10-06 P106 批次 2 实测）**：`JwtAuthenticationFilter` 在 `enterpriseId != null` 时 `EnterpriseContextHolder.set(...)`，**但从不把已存在的值清掉**（它只在 `finally` 里清自己那一份）。而 `AbstractMapperTest` 的 `@BeforeEach` 会把上下文**预置为 `DEFAULT_ENTERPRISE_ID`(1)** ⇒ 于是「JWT 无企业 + 不带头 ⇒ 应无上下文」这类负向场景，请求实际带着**企业 1** 进去，端点返回 `200` + 一堆 `null`，**看起来通过、实则什么都没验**。
    - ⚠️ **这是测试夹具产物，不是生产缺陷**：生产里该 `finally` 保证每个请求起始上下文为 null（Tomcat 线程复用场景亦然）。判据是「谁预置了它」—— 只有测试基类会。
    - **修法**：这类用例在发请求前**显式** `EnterpriseContextHolder.clear()`（MockMvc 与测试同线程，清了即生效）。
    - **配套教训**：凡断言「**没有**上下文 / **没有**权限 / **没有**记录」的场景，都要先问「**会不会有东西从夹具里漏进来**」。正控（本类有 `currentPeriodEndpointActuallyReflectsContext` 证明两次请求确实返回不同企业）+ 反向自检（无上下文时端点必须返 400）**两条都要**，否则整类恒绿。

36. 🔴 **断言响应体里的中文 `msg` 等于在断言一个解码假象（2026-10-06 实测）**：`MvcResult#getContentAsString()` **不带参数时按 `ISO-8859-1` 解码**，而 `GlobalExceptionHandler` 走 Jackson 输出的响应**未显式带 `charset`** ⇒ 期望串（UTF-8 中文）与实际串（乱码）逐字比对必然不等，报错信息里两边看起来一模一样、只差编码，极易误判成「端点行为不对」。
    - **修法**：①只断言 `code` 等**结构化字段**（`readTree(...).path("code").asInt()`），不逐字比对 `msg`；②确需读 body 时显式 `getContentAsString(StandardCharsets.UTF_8)`。
    - **判据**：MockMvc 断言失败时，若「期望」与「实际」肉眼看不出差别，**先怀疑编码**。注意 `JwtAuthenticationFilter` 自己写的 403 响应体**是**带 `charset=UTF-8` 的，所以同一测试类里不同端点的解码行为可能不一致 —— 这本身就是个陷阱。

37. 🔴 **脚本化批量改代码会把「无参调用」和「多行语句」一起改崩**（2026-10-06 P102 批次 4 实测，一轮改崩 4 次才收敛）：用正则批量把 `return R.ok(X);` 改成 `return R.ok(VO.from(X));` 时，连环踩了三个坑，且**每一个都编译不过、但报的是完全无关的行**：
    - **坑 1：括号配平取「表达式」时，正反两个方向都会错**。扫描到 `depth` 归 0 时 `j` 已经**越过**闭合的 `)`，于是
      - 取 **`s[start:j]`** ⇒ `inner` 多带一个右括号 ⇒ 包裹后变成 `from(x)))` —— **多一个右括号**（批次 5 实测，编译报 `';' expected`）；
      - 或按「行尾补 `));`」⇒ **少一个右括号**（批次 4 实测，`R.ok(a(...)))` 应为 `));` 却写成 `)));`）。
      两种都报在**上一个方法**的结尾，肉眼完全看不出错在哪。
      **正解**：扫描结束后显式 `inner = s[start:j-1]` 并 `assert s[j-1] == ')'`；改完立刻跑**剔除字符串/注释后的括号余额**检查 —— 直接数原文会被 `@Operation(summary="规则列表(精确)")` 这类中文括号骗出假阴性。
    - **坑 2：无参 `R.ok()` 被当成有参**。`R<Void>` 的 delete/lock/unlock 一律是裸 `return R.ok();`，脚本把它改成 `return R.ok(PeriodVO.from()));` ⇒ 括号失衡 + `cannot find symbol` 混在一起。**判据**：包裹前必须先按**方法声明的返回类型**筛，`R<Void>` / `R<Long>` / `R<Map<..>>` 一律不动。
    - **坑 3：正则依赖字段命名假设**。脚本假设 service 字段叫 `xxxService`，而 `AssetCategoryController` / `BankJournalController` 叫 `service` ⇒ 8 处静默漏改，直到**编译报 `inference variable T has incompatible bounds`** 才暴露。**正解**：按方法**声明的返回类型**（`R<...VO>`）定位方法体，而不是猜表达式长相。
    - **强制三查（缺一不可）**：① `mvn test-compile` ② 括号余额逐文件统计（负值或非 0 即崩）③ `R<Void>` 方法体必须仍是裸 `R.ok()`。
    - ⚠️ **判断依据**：`R.ok()` 的无参形态是**唯一**不含表达式的调用，任何「包裹 `R.ok(...)` 内层」的规则都会误伤它。同类高危还有 `R.ok(null)`（成功但无数据）。

38. 🔴 **「审计列」不是「内部字段」的同义词 —— 是否外露由前端契约决定**（2026-10-06 P102 批次 4 实测，契约守卫当场报红）：`MasterDataVoContractTest` 的 `NEVER_EXPOSE` 里放了 `createdAt`，理由是「它属审计列」。守卫立刻报 `PeriodVO 外露了内部字段 [createdAt]` —— 因为**有 5 个前端接口明确声明了 `createdAt: string`**（PeriodVO / VoucherTypeVO / SummaryLibVO / SysConfigVO / BankJournalVO），它是这些页面的**合法展示字段**。
    - **与 §4.2 第 9 条同型**：那里是「同名 `status` 在三张表的 CHECK 允许集不同」，这里是「同名列在不同契约下语义不同」。**判据**：字段的敏感性**不能按列名归类**，必须逐个 VO 对着**前端 interface 的实际声明**核对。
    - **两类约束必须分开写**：①**契约约束**（前端声明了什么就必须有，漏了前端读到 `undefined`）②**安全约束**（哪些列不许外露）。把两者混成一个 `NEVER_EXPOSE` 集合，就会把合法的展示字段误判为泄漏。
    - **守卫的这类红灯不要「改守卫」了事**：本轮先确认了 5 个前端接口确实声明 `createdAt`，才把它移出集合并**在代码里写明依据**（哪 5 个接口、哪一行）。判据是「有没有外部证据」，不是「守卫报错了」。
    - ✅ **2026-10-06 批次 5 的第二例，方向相反：这次要改的是「前端 interface」**。`classificationRule.ts#ClassificationRule` 声明了 `tenantId` 与 `updatedAt`，守卫报 `ClassificationRuleVO 外露了内部字段 [tenantId, updatedAt]`。查证结果：`ClassificationRuleList.vue` **两个都不读**，且 `t_classification_rule` 两列都真实存在 ⇒ 是**前端契约陈旧**，不是 VO 泄露。⇒ 从 interface 删掉两行（VO 随之收窄）。
      - 附带修正一个**易混点**：`pageRules(params: { tenantId?: number })` 里的 `tenantId` 是**请求参数**（控制器确实收它），**不属于出参契约**，不能跟着删。**判据**：同一文件里同名字段出现在「函数入参」与「返回 interface」是两件事，只改后者。
      - `tenantId` 该删还有一层依据：P106 D-1 已裁定**废弃 `tenantId`**，契约里继续声明会诱导后人往多账套方向用它。

39. 🔴 **「前端读的字段后端没有」要区分「TS 陈旧」与「功能未实现」—— 判据是页面有没有在用**（2026-10-06 P102 批次 6 实测，Prepayment 4 列恒空白）：`PrepaymentList.vue` 读 `prepayNo`/`vendorName`/`customerName`/`appliedAmount`，而 `t_prepayment` **四个列一个都没有**。同一个现象在批次 4/5 已见过两次，但**这次结论相反**：
    - CashJournal：**页面读的是真实列**，只有 interface 写错 ⇒ 改 TS（契约陈旧）。
    - ClassificationRule：`tenantId`/`updatedAt` **页面两个都不读** ⇒ 改 TS 删掉（契约陈旧）。
    - **Prepayment：页面真的在读这 4 个列**（4 处 `el-table-column` + 详情弹窗）⇒ **不是契约陈旧，是功能未实现**，光改 TS 会把「空白列」变成「永久空白且类型合法」。
    - **判据**：改 TS 之前先 `grep` 页面有没有真的读它。**读了 ⇒ 必须接上真实数据**（本批：跨表 JOIN + 列改名），**没读 ⇒ 才可删**。
    - ⚠️ `appliedAmount → settled_amount` 这一处还暴露了**同名字段被用在两种类型上**：`row` 是预付款、`d` 是 `targetDocOptions`（来自 `pageReceivable`/`pagePayable` 的业务单）。两者真实列名恰好都是 `settled_amount` ⇒ 统一替换正确；**只改一半会让另一处继续读 undefined**。改名前必须逐个确认每个出现点的数据类型。
    - 🔴 **诚实标注数据来源的缺口，不靠兜底值掩盖**：`prepayNo` 来自 `doc_id → t_business_doc.doc_no`，而 `doc_id` 只有「银行流水自动生成」那条路径会写（`AutoGenerationService:775,815`），**手工建单的预付款没有 doc_id**（`PrepaymentCreateDTO` 刻意不含它）⇒ 该列对手工单仍为 null。真要每条都有编号，须按铁律 #9 给 `t_prepayment` 补 xxx_no 冗余列（需 Flyway 迁移），已登记待办。**判据**：写 `「未生成」`之类兜底值 = 把「来源缺失」伪装成正常数据，正是 §4.5 第 25 条说的那类假绿。
    - ⚠️ **Mock 证明不了跨表关联查得到**：`ArapSettlementMapper.pageWithPartyName` 曾把 JOIN 写成不存在的 `t_supplier`（§4.2 第 7 条），而 Mock 夹具照样全绿。本批因此补了真库测试 `PrepaymentViewRealDBTest`，并双向反证过：①`lookupNames` 返回空 Map ⇒ 2 例转红（单据号/供应商名/客户名断言）②`create()` 改回无视 `settledAmount` ⇒ 1 例转红。
    - ✅ **顺带修掉一个未被踩到的陷阱**（真库测试逼出来的，不是读代码看出来的）：`create()` 原本 `if (unsettled == null) unsettled = amount`，**无视调用方已传入的 `settledAmount`** ⇒ 传了 `settled>0` 的调用方会拿到 `amount ≠ settled + unsettled` 的不一致记录，而 `applyToPayable`/`BadDebtService` 全在此基础上做加减。**现网两条创建路径都传 `settled=ZERO`（`AutoGenerationService:780,820`；HTTP 路径 DTO 刻意不含该字段），所以此前从未被触发** —— 不是已发生的错账，是个还没人踩的坑。改为按不变量推导封掉。**教训**：写不变式断言时，被断言的不变式往往在生产代码里只是「恰好成立」，不成立的那条分支要主动构造出来。

40. 🔴 **「兜底层有效」会掩盖「上层改动无效」，端到端断言可能对本次改动完全不敏感（2026-10-07 P106 D-1 反证实测）**：D-1 把 `page()` 的应用层 `enterprise_id` 过滤按计划改完，`AT-106-9` 端到端用例（真实 service 回路、真库、两企业夹具）**全绿**。但按 AGENTS 的反证要求把该过滤**删掉再跑，用例依然全绿** —— 因为第二层防线 `EnterpriseDataPermissionInterceptor` 已在 **SQL 层**注入同一条件，**掩盖了应用层的缺失**。探针实测打出 `total=10 / records=1`（10 = 全表行数）才暴露问题。
    - **判据**：当改动位于「多层防护的某一层」时，**端到端断言只证明「整体不漏」，不证明「这一层在起作用」**。必须补**层内断言**（直接验该层的产物，如 wrapper 的 SQL 片段、拦截器改写后的 SQL）。
    - 🔴 **为此付出 3 步代价才做对，两个坑都要记住**：
      - `LambdaQueryWrapper#getCustomSqlSegment()` 需要 MP 的 `TableInfo` **lambda→列名缓存**，而该缓存**只在 Spring 上下文启动时建立** ⇒ 纯 Mockito 单测里直接抛 `MybatisPlusException: can not find lambda cache`。正解：`TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Entity.class)`。
      - 退而求其次断言 `getParamNameValuePairs()` **实测恒为 `{}`** —— 该 map 在**渲染 SQL 时**才填充，不是在 `eq()` 时。⇒ 只能走「显式初始化缓存 + 渲染 SQL 片段」。
      - ⚠️ **更要紧的是自造的测试夹具可能让反证失效**：`match()` 那条真库用例首版把两列设成 `enterprise_id=E / tenant_id=1` 后断言「两个上下文都返 null」—— 而**旧实现按 `tenant_id` 过滤时同样返 null** ⇒ 该用例对旧实现**同样绿**，反证无效。改成「ctx=E 必须命中（正向）+ ctx=1 必须不命中（负向）」才真正可证伪。**判据：写完反证后要确认「把实现改回旧版，用例确实转红」，而不是只看用例在新实现下是绿的。**
    - 🔴 **顺带查出一个跨批次的产品级缺陷（DIR-004，已闭环）**：`total` 与 `records` 不一致本身是线索 —— `MyBatisPlusConfig` 把 `PaginationInnerInterceptor` 注册在 `EnterpriseDataPermissionInterceptor` **之前**，MP 分页插件**自行执行 COUNT 并短路拦截器链** ⇒ **COUNT 永远拿不到 `enterprise_id`**。用已种子的 `t_subject` 复现：上下文企业无科目时 **`total=43`（别的企业的条数）/ `records=0`**。**凡用 MyBatis-Plus 分页，务必同时断言 `total` 与 `records.size()`，只断言 records 等于没断言一半。**
    - ✅ **治法（方案 A，已实施）**：顺序改为「企业/部门权限 → 分页 → 乐观锁」。**机理必须靠反编译确认，不能靠猜** —— MP 3.5.7 的 `MybatisPlusInterceptor.intercept` 按注册顺序对每个拦截器依次调用 `willDoQuery`→`beforeQuery`，而 `PaginationInnerInterceptor.willDoQuery` **自行拼 COUNT 并直接用传入的 executor 执行，该路径不再回拦截器链**。守卫 `PaginationTotalTenantIsolationRealDBTest`（2 例）**刻意不加任何应用层企业条件、直接调 mapper** —— 加了就等于把缺陷重新掩盖，守卫会退化成假绿。
    - ⚠️ 顺带确认一条长期存在的**设计选择**：`EnterpriseDataPermissionInterceptor` 在**无企业上下文时是 fail-open（直接放行，不过滤）**，代码注释自认「刻意放行的少数场景（定时任务/初始化）」。⇒ 应用层 Service **不能**依赖它兜底：无上下文时必须自己在入口 fail-closed（P106 D-1 即按此改，见 §4.5 第 41 条形态）。

41. 🔴 **DDL 的执行顺序对「留证型查询」是硬约束，把审计统计放在 `DROP COLUMN` 之后会让迁移直接失败（2026-10-07 P106 D-3 实测）**：`V170` 里有一段「删列前统计 `tenant_id <> enterprise_id` 的行数并 `RAISE NOTICE`」的留证逻辑（意图是把「删列前到底有多少行两列不一致」写进 Flyway 历史，避免该事实随删列一并消失）。首版把它排在 `ALTER TABLE ... DROP COLUMN tenant_id` **之后** ⇒ Flyway 立即失败：
    ```
    Message : ERROR: column "tenant_id" does not exist
    Location: db/migration/V170__...sql (Line 34)
    ```
    **判据**：迁移里凡是「查询某列 → 据以生成后续 DDL 或仅留证」的语句，**必须在该列被删/改之前**。这不是代码风格，是执行顺序约束。
    - 🔴 **同型第二坑：只删 DB 列、忘了删 Entity 字段 ⇒ 运行期直接 `column does not exist`**。MyBatis-Plus 会把 Entity 上**任何非 `exist=false` 的字段**纳入 SELECT/INSERT 列表 ⇒ 列已删而字段还在，真库立刻报错（实测 2 条 ERROR）。**判据：删列的「三方对照」（PG ↔ Entity ↔ 业务代码）里，Entity 侧不是可选项 —— `check-entity-schema.mjs` 的**反向**校验（DB 有列/Entity 无字段）查不出这种情况，因为方向是「Entity 有字段/DB 无列」，而该脚本本轮已覆盖双向，但**运行期才是最终裁判**。
    - 🔴 **第三坑（反证自身造假）**：给 schema 守卫写反证时，我先把 `ALTER TABLE ... ADD COLUMN tenant_id` 注入、却把断言放在**注入之前** ⇒ 守卫恒绿，反证「通过」但毫无意义。改成「先加列、再断言」后立刻转红。**判据：写反证时必须问「注入点是否在断言之前」** —— 否则你验的是「列本来就不存在」，与「守卫能抓住列复活」无关（同 §4.5 第 21 条：门禁自身的执行也必须验证）。

42. 🔴 **「白名单 = 某类表」这种不变量极易少算一张，而少算的那张恰好能通过自检（2026-10-07 P106 D-2a 实测）**：实施 D-2a（给 10 张「无隔离列的表」补定性结论）时加了守卫「白名单不得混入本组之外的表」，**反查立刻报出 4 张未登记**：`t_user`、`t_agency_enterprise`、`t_agency_user_enterprise`、`t_service_progress` —— 白名单实为 **14 张**。
    - **根因不是「忘了 4 张」，而是这两类表根本不该放进同一个不变量**：
      - **A 类（无隔离列）**：白名单理由是「注入 `enterprise_id` 会**必然 SQL 报错**」—— 表里压根没这列。
      - **B 类（有隔离列但语义不同）**：白名单理由是「注入会**破坏设计语义**」—— 如 `t_service_progress` 建表注释明写「`agency_id` 隔离 + `enterprise_id` 存被服务客户」，代理端要**跨客户**查询；`t_user.enterprise_id` 是**归属企业**且「决定当前企业是谁」的成员校验本身要读它（**循环依赖**）。
      ⇒ 把二者混为一谈，就会写出「白名单 = 无隔离列的表」这条**错误不变量**，而它**恰好能靠「只看其中 10 张」通过自检**。
    - **判据**：写「某集合 = 某类表」的不变量前，先问 **「这批成员被排除的理由是不是同一条？」**；理由不同就分栏登记，并让守卫**两个方向都锁**（少一张 ⇒ 运行期报错；多一张 ⇒ **静默关闭该表第二层防线且无任何报错**）。
    - 🔴 **反证脚本自身也会失败，采信它给出的绿之前必须确认「注入确实发生」**：本次反证 ②「从白名单移除一张 A 类表」的注入脚本用了与真实格式不符的匹配串，`assert` 失败**却没中止脚本**，于是那一轮跑的是**未注入的绿** —— 若照单全收，就会把一个没验证过的反证写进文档（与 §4.5 第 41 条「注入点必须在断言之前」同源，此处是「注入点必须真的匹配上」）。
    - **为什么「零 DDL 的文档收口」也需要守卫**：文档性结论最容易的失效形态就是**与代码分叉**，而白名单是「放行不过滤」的名单 —— **多一张业务表等于静默关闭该表的第二层防线，全程无报错**。守卫成本 5 条真库用例，收益是这类分叉立刻可见。

43. 🔴 **给某张表开「白名单豁免」时，只解释一条路径就等于整表放行（2026-10-07 P106 D-2a 实施中查出 P0）**：我给 `t_user` 写进 `SHARED_TABLES` 的理由是「`enterprise_id` 是归属企业、且决定『当前企业是谁』的成员校验本身要读它」—— 这**只解释了登录路径**，但白名单是**表级**的，于是用户管理端点一并被放行。实测三层防线全空：应用层 `UserServiceImpl` 7 个方法无企业维度、第二层因白名单不注入、DB 层 `relrowsecurity=false` + `policies=0`，`ctx=990007` 的 `pageUser` 返回了企业 1 的用户名/真实姓名/邮箱/手机号，且 `getById`/`update`/`delete`/`resetPassword` 可**按猜测 id 读改删**别家企业用户。
    - **判据**：开豁免时必须**逐路径**列出「哪些路径需要豁免」，并让**其余路径自己补隔离**。
      「整表豁免 + 只解释一条路径」必然漏 —— 因为白名单的粒度是表，而理由的粒度是路径。
    - ⚠️ **豁免理由成立 ≠ 该表不需要隔离**：真正要问的是「**这个豁免在哪些端点生效**」。
      本次修法是**保留豁免**（登录须在无上下文时按用户名查人，注入会让 ctx=null 谓词恒不成立 ⇒ **全员无法登录**；
      代理端亦需跨客户企业读本代理用户），而让**用户管理路径自己校验归属**。
    - 🔴 **越权写入必须「先校验后改」且带数据未变的负向断言**：守卫里加了「越权 `updateStatus` 被拒后
      `status` 必须保持原值」，防的是「先 update 再校验」这种数据已改坏的实现 ——
      只断言抛异常不足以覆盖它（§4.3 第 10 条静默失败形态）。
    - 🔴 **连带发现一个反向缺口**：`UserSaveDTO` 刻意不含 `enterpriseId`，且 `UserEntity` 不继承
      `BaseEntity` ⇒ `insertFill` 不触发 ⇒ 新建用户 `enterprise_id` 为 **NULL**。
      一旦列表按企业过滤，这些用户**永远不可见**（自己建的管理员看不到自己建的人），
      同时它们是「不属于任何企业」的游离数据 —— 与预付款 1a-1 同型。
      **判据：给某张表加上过滤之前，先确认它的数据真的都带那列的值**；否则过滤会把合法数据一起滤掉。
    - ⚠️ **「编译失败」不算反证成功**：本次反证 ①（删掉 `enterprise_id` 条件行）首版留下了悬空语句
      ⇒ Maven 编译失败，看着像「红了」，实际是无效反证。必须确认红灯**来自断言**而不是来自编译。
      修正为「整段替换且保持可编译」后才拿到真红（§4.5 第 41 条同型）。

44. 🔴 **给已有表补隔离列时，「DB 加了列」不等于「数据带上了列」—— 漏改 Entity 的症状极具误导性（2026-10-07 P106 批次 1b' 实测）**：给 `t_dept` 做隔离时我按序做了「DB 加 `enterprise_id` → 改唯一约束 → 开 RLS → 移出白名单 → 补 Entity」，**顺序颠倒**导致跑测试时报 `DuplicateKeyException`，看着像「唯一约束没改成功」，真实原因是 `DeptEntity` **不继承 `BaseEntity`** ⇒ MyBatis-Plus 不把该列纳入 INSERT ⇒ 新部门落 `enterprise_id = DEFAULT 1` ⇒ 两个企业建同名部门**仍然撞键**。
    - **判据**：补隔离列的三处必须**同一次改完**：DB 列 / Entity 字段 / 应用层过滤或白名单。**任一缺失的表现都不是「报错指向的那一处」**。
    - **为什么 Entity 只需加字段、不必加其它**：本类的 fill 回调**本来就会触发**（`createdAt` 已是 `@TableField(fill=INSERT)`），而 `MyMetaObjectHandler.insertFill` 是按**字段名** `enterpriseId` 判存在并**无条件覆盖**为上下文企业（§4.5 第 34 条）。⚠️ 反之，若类内**一个 fill 字段都没有**，则整个回调压根不触发，加字段也不够（预付款 1a-1 同型）。
    - 🔴 **「守卫按设计报警」是好现象，不要当成需要消音的噪声**：D-2a 守卫的「A 类不得有隔离列」在加列后立刻转红 —— 它正确识别出「`t_dept` 的分类前提已变」。处置是**重分类 A→B + 改基数**，而不是把断言放宽。⇒ **判据：断言转红时先问「是代码错了，还是世界变了」**；后者应更新断言的分类，并保留一条历史留证条目。
    - ⚠️ **反证脚本注入也可能编译不过**：本轮「改回全局唯一约束」「RLS 谓词改 `USING true`」两处反证，首版把注入写进 `try/finally` 导致 Java 结构破坏 ⇒ **编译失败**，白跑一轮。改用「注入放方法首部 + 依赖 `mvn clean` 重建容器免恢复」后才拿到真红。**编译失败一律不算反证成功**（§4.5 第 41/43 条同型）。

### 4.6 工作流执行类
20. **起步跳过三步闭环**：收到"开发/继续开发/写代码"指令时，Hermes 必须先走 SPEC→审核门→再执行，禁止直接写 SPEC 文档或代码。**三次纠正沉淀：** 2026-07-09 ai-evolution-v2 起步时直接写计划文档+commit，跳过老丁审核（违反铁律 #10）。修正：收到任何开发指令，第一条输出必须是 SPEC 草案或要求确认需求，不是代码/计划文档。
21. **`git add -A` 导致 doc 漂移**：写完文档后用了 `git add -A` 而非指定文件路径，导致关联 issue 修复。修正：commit 前先 `git status --short` 确认只有目标文件被跟踪。

---

## §5 命名约定（强制执行）

- 表名前缀 `t_`（如 `t_voucher`、`t_business_doc`）
- 主键：`id BIGINT GENERATED ALWAYS AS IDENTITY`
- 金额列：`NUMERIC(18,2)`
- 扩展数据：`JSONB` 列（OCR 结果、AI 负载、辅助核算）
- AI 向量：`VECTOR(768)` + IVFFlat 索引
- 表/列名：**snake_case**（PostgreSQL 约定）
- Java 字段：camelCase，用 `@TableField(value = "snake_case_name")` 映射

---

## §6 常用命令速查

| 操作 | 命令 |
|------|------|
| 启动后端 | `cd backend && mvn spring-boot:run` |
| 启动前端 | `cd frontend && npm run dev` |
| 运行全部测试 | `cd backend && mvn test` |
| 快测试（无 Docker） | `cd backend && mvn test` |
| 完整测试（含真实 DB） | `cd backend && mvn test -DexcludedGroups=` |
| 单个测试类 | `cd backend && mvn test -Dtest=XxxTest` |
| 编译检查 | `cd backend && mvn compile` |
| 打包 | `cd backend && mvn clean package -DskipTests` |
| 启动 Docker 环境 | `docker compose up -d` |
| 查看 Flyway 状态 | `cd backend && mvn flyway:info` |
| 查看 Git 日志 | `git log --oneline -10` |

---

## §7 风险边界（操作前需人工确认）

1. **禁止 `git push -f`**：任何强制推送都会覆盖 Git 历史，必须经老丁确认
2. **禁止直接修改生产配置**：`application-prod.yml` 等生产环境配置文件不可修改
3. **禁止硬编码敏感信息**：数据库密码、API Key、密钥等必须通过环境变量或配置中心注入
4. **DDL 必须走 Flyway**：禁止直接修改数据库表结构，所有 schema 变更必须生成 Flyway migration
5. **三方对照审计**：任何 schema 变更必须 `PG ↔ Entity ↔ 业务代码` 三方对齐，禁止只改一端
6. **Git add 禁止通配**：`git add` 必须指定具体文件路径，禁止根目录 `git add -A`
7. **破坏性操作确认**：DROP、DELETE 无 WHERE、truncate 等操作必须经老丁确认

---

## §8 技术栈（已落地）

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.x, Spring Security + JWT |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL 16, pgvector, pg_trgm |
| 缓存/分布式锁 | Redis 7 |
| 消息队列 | RabbitMQ |
| 文件存储 | MinIO |
| AI 服务 | Python 3.11 + FastAPI, Hugging Face / PyTorch |
| 前端 | Vue 3 + Element Plus + ECharts |
| 报表 | EasyExcel |
| API 文档 | Knife4j (Swagger) |
| 监控 | Prometheus + Grafana（待接入） |
| 部署 | Docker Compose |