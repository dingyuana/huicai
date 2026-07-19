# S-00 — 底座架构规范

> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：⚠️ 待审
> **层级**：基础设施层
> **预估复杂度**：高
> **关联需求**：REQ-2026-047
> **关联文档**：[项目说明](../项目说明.md)、[技术方案](../技术方案.md)、[底座与双分支开发计划](../开发计划/底座与双分支开发计划.md)

---

## 概述

当前慧财系统代码采用平铺的模块结构（`system/`、`finance/`、`arap/`、`tax/`、`asset/`、`budget/`、`report/`），所有模块在同一包空间下，底座能力与业务逻辑混在一起。

本 SPEC 定义将代码重构为**三层架构**（base/SME/agency）的规范，明确每层的职责、边界、依赖规则和迁移步骤。

---

## 1. 输入契约

### 1.1 当前状态

```
backend/src/main/java/com/huicai/module/
├── system/       ← 底座（科目/期间/权限）+ 基础数据（客商/员工）
├── finance/      ← 底座（凭证/余额）+ SME（银行流水/对账）
├── arap/         ← SME（核销/预收预付/坏账）+ SME（费用报销）
├── tax/          ← SME（进销项发票/税务）
├── asset/        ← SME（固定资产）
├── budget/       ← SME（预算）
├── report/       ← 底座（报表引擎）
├── ai/           ← 横切（AI编排层）
└── storage/      ← 横切（MinIO附件）
```

**问题**：
- `finance` 同时包含底座（凭证引擎）和 SME（银行流水）
- `arap` 同时包含核销和费用报销（两个独立业务）
- 底座和业务逻辑没有物理隔离
- 新增 Agency 分支没有位置可放

### 1.2 目标状态

```
backend/src/main/java/com/huicai/
├── base/                     ← 底座（所有企业共用）
│   ├── subject/              ← 科目体系（从 system 迁移）
│   ├── period/               ← 会计期间（从 system 迁移）
│   ├── voucher/              ← 凭证引擎（从 finance 迁移）
│   ├── balance/              ← 科目余额（从 finance 迁移）
│   ├── report/               ← 报表引擎（从 report 迁移）
│   ├── auth/                 ← RBAC权限（从 system 迁移）
│   ├── masterdata/           ← 基础数据（从 system 迁移）
│   ├── audit/                ← 审计日志（从 system 迁移）
│   └── config/               ← 系统配置（从 system 迁移）
├── sme/                      ← SME 分支
│   ├── invoice/              ← 进销项发票（从 tax 迁移）
│   ├── arap/                 ← 应收应付/核销（从 arap 迁移）
│   ├── cash/                 ← 银行流水/对账（从 finance 迁移）
│   ├── asset/                ← 固定资产（从 asset 迁移）
│   ├── expense/              ← 费用报销（从 arap 迁移）
│   ├── salary/               ← 工资薪酬（新建）
│   └── budget/               ← 预算管理（从 budget 迁移）
├── agency/                   ← Agency 分支（新建）
│   ├── tenant/               ← 多客户账套管理
│   ├── batch/                ← 批量操作工作台
│   ├── client/               ← 客户CRM
│   └── mobile/               ← 移动端API
├── ai/                       ← AI 编排层（不变）
└── common/                   ← 公共工具（不变）
```

### 1.3 约束

- **底座不得依赖任何业务模块**（base 不能 import sme/agency 的任何类）
- **SME 和 Agency 只能依赖底座**，不能互相依赖
- **AI 层可以依赖 base、sme、agency**（横切辅助）
- 代码搬迁是**纯搬迁**，不改业务逻辑
- 每迁一个子包必须编译通过 + 测试通过
- 旧包路径保留兼容性（不删除，新代码迁入新包，逐步淘汰旧包）

---

## 2. 输出契约

### 2.1 迁移后的包结构

每个子包的迁移结果必须满足以下验收标准：

| 子包 | 迁移路径 | 验收标准 | 优先级 |
|------|---------|---------|--------|
| `base/subject` | `system` → `base/subject` | 编译通过，`SubjectService` 可用 | P0 |
| `base/period` | `system` → `base/period` | 编译通过，`PeriodService` 可用 | P0 |
| `base/voucher` | `finance` → `base/voucher` | 编译通过，`VoucherService` 可用 | P0 |
| `base/balance` | `finance` → `base/balance` | 编译通过，`SubjectBalanceService` 可用 | P0 |
| `base/report` | `report` → `base/report` | 编译通过，`ReportService` 可用 | P0 |
| `base/auth` | `system` → `base/auth` | 编译通过，`UserService`/`RoleService` 可用 | P0 |
| `base/masterdata` | `system` → `base/masterdata` | 编译通过，`CustomerService` 可用 | P0 |
| `base/audit` | `system` → `base/audit` | 编译通过，`AuditLogService` 可用 | P0 |
| `base/config` | `system` → `base/config` | 编译通过，`SysConfigService` 可用 | P0 |
| `sme/invoice` | `tax` → `sme/invoice` | 编译通过，发票导入+状态机可用 | P1 |
| `sme/arap` | `arap` → `sme/arap` | 编译通过，核销工作台可用 | P1 |
| `sme/cash` | `finance` → `sme/cash` | 编译通过，银行流水导入可用 | P1 |
| `sme/asset` | `asset` → `sme/asset` | 编译通过，资产卡片可用 | P1 |
| `sme/expense` | `arap` → `sme/expense` | 编译通过，报销单可用 | P1 |
| `sme/budget` | `budget` → `sme/budget` | 编译通过，预算编制可用 | P1 |

### 2.2 迁移后代码规范

- 每个子包必须包含完整四层：`entity/`、`mapper/`、`service/`、`controller/`
- 所有 Spring Bean 扫描路径必须更新（`@ComponentScan` / `@MapperScan`）
- 所有 MyBatis XML 映射文件路径必须更新
- 前端 API 路径不变（`/api/v1/...`），不涉及前端代码改动

### 2.3 不迁移的内容

- `common/`（公共工具类）— 保持原位
- `ai/`（AI编排层）— 保持原位
- `config/`（全局配置）— 保持原位
- 所有 Flyway 迁移文件 — 保持原位
- 所有测试代码 — 保持原位（测试代码路径不用改，因为测试的是服务行为而不是包路径）

---

## 3. 状态流转

### 3.1 迁移流程

```
当前状态 → 创建目标包结构 → 逐个搬迁子包 → 更新Spring配置 → 编译验证 → 测试验证 → 完成
```

### 3.2 搬迁步骤

| 步骤 | 操作 | 验证 | 回滚 |
|------|------|------|------|
| 1 | 创建 `base/` 包下的所有子包目录 | 目录存在 | 删除目录 |
| 2 | 搬迁 `subject`：`system/entity/SubjectEntity.java` → `base/subject/entity/` | 编译通过，SubjectMapperTest 通过 | git revert |
| 3 | 搬迁 `period`：同上模式 | 编译通过，PeriodMapperTest 通过 | git revert |
| 4 | 搬迁 `auth`：User/Role/Menu/Dept | 编译通过，登录可用 | git revert |
| 5 | 搬迁 `masterdata`：Customer/Vendor/Employee | 编译通过 | git revert |
| 6 | 搬迁 `voucher`：从 finance 搬迁 | 编译通过，凭证CRUD可用 | git revert |
| 7 | 搬迁 `balance`：从 finance 搬迁 | 编译通过，科目余额查询可用 | git revert |
| 8 | 搬迁 `report`：从 report 搬迁 | 编译通过，三大报表可用 | git revert |
| 9 | 搬迁 `audit` + `config` | 编译通过 | git revert |
| 10 | 搬迁 SME 子包：invoice/arap/cash/asset/expense/budget | 编译通过，全量测试通过 | git revert |

**每个子包搬迁的微循环**：
```
① 创建目标包路径
② 复制 Entity/Mapper/Service/Controller 到新包
③ 更新文件中的 package 声明
④ 更新 MyBatis XML 中的 namespace
⑤ 更新所有 import 引用（旧包→新包）
⑥ 编译验证
⑦ 运行该模块的测试
⑧ git commit
```

### 3.3 负向断言

- 不允许一次性搬迁多个子包后不验证（必须每步验证）
- 不允许在搬迁过程中修改业务逻辑
- 不允许删除旧包（旧包保留，新包创建，逐步过渡）
- 不允许在搬迁过程中引入新依赖或新框架
- 不允许 sme/agency 模块 import base 以外的包

---

## 4. 异常处理

| 场景 | 处理方式 | 回滚 |
|------|---------|------|
| 搬迁后编译失败 | 检查 import 和 package 声明，修复后重试 | git reset --hard HEAD~1 |
| 搬迁后测试失败 | 检查搬迁是否正确（是否误改了业务逻辑） | git reset --hard HEAD~1 |
| 旧包仍被引用 | 全局搜索旧包名，更新引用 | — |
| Spring Bean 扫描不到 | 检查 `@ComponentScan` 配置 | 添加扫描路径 |
| MyBatis XML 找不到 | 检查 `mybatis.mapper-locations` 配置 | 更新配置 |
| 循环依赖 | 搬迁不改变 Bean 依赖关系，不应出现 | 检查是否误改 |

---

## 验收标准（BDD）

### 场景 1：底座搬迁后科目体系可用
- **Given** 当前项目在 `feature/architecture-base-branch` 分支
- **When** 将 `SubjectService` 从 `system` 搬迁到 `base/subject`
- **Then** 编译通过，`SubjectMapperTest` 全部通过
- **And** 科目录入/查询/树形结构功能在浏览器中可用

### 场景 2：底座搬迁后凭证引擎可用
- **Given** 项目已搬迁 `voucher` 子包到 `base/voucher`
- **When** 运行 `mvn test`
- **Then** `VoucherMapperTest` 全部通过，无新增失败
- **And** 凭证创建/审核/过账功能在浏览器中可用

### 场景 3：SME 搬迁后发票导入可用
- **Given** 项目已搬迁 `invoice` 子包到 `sme/invoice`
- **When** 运行 `mvn test`
- **Then** `OutputInvoiceMapperTest` 和 `InputInvoiceStateMachineTest` 全部通过
- **And** 发票导入页面在浏览器中可用

### 场景 4：底座不依赖 SME
- **Given** 底座代码已搬迁完成
- **When** 检查 `base/` 下所有 Java 文件的 import 语句
- **Then** 没有任何 import 指向 `com.huicai.sme` 或 `com.huicai.agency`
- **And** 编译通过

### 场景 5：全量回归测试通过
- **Given** 所有子包搬迁完成
- **When** 运行 `mvn test`
- **Then** 测试失败数与搬迁前一致（0新增失败）
- **And** 关键业务链路（发票→凭证→核销→报表）可用

---

## 依赖关系

- **依赖**：无（本 SPEC 是架构根基，不依赖其他 SPEC）
- **被依赖**：S-14（工资薪酬）、Agent账套管理（Agency SPEC）等所有后续 SPEC
- **关联**：[底座与双分支开发计划](../开发计划/底座与双分支开发计划.md) — 具体执行计划

---

```yaml
# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: BaseArchitecture
module: base
table: n/a (architecture refactor)

# --- Migration States ---
states:
  AS_IS:
    description: "当前平铺结构 — system/finance/arap/tax/asset/budget/report 在同一层"
    initial: true
    terminal: false
  BASE_READY:
    description: "底座子包（subject/period/voucher/balance/report/auth/masterdata/audit/config）已搬迁到 base/"
    initial: false
    terminal: false
  SME_READY:
    description: "SME 子包（invoice/arap/cash/asset/expense/budget）已搬迁到 sme/"
    initial: false
    terminal: false
  AGENCY_READY:
    description: "Agency 子包（tenant/batch/client/mobile）已创建"
    initial: false
    terminal: false
  DONE:
    description: "全部搬迁完成，全量测试通过"
    initial: false
    terminal: true

# --- Transitions ---
transitions:
  - id: T-01
    from: AS_IS
    to: BASE_READY
    trigger: migrateBase()
    precondition: "当前为平铺结构"
    postcondition: "base/ 下所有子包编译通过，底座测试通过"
    side_effects:
      - entity: "base/subject"
        action: create
      - entity: "base/period"
        action: create
      - entity: "base/voucher"
        action: create
      - entity: "base/balance"
        action: create
      - entity: "base/report"
        action: create
      - entity: "base/auth"
        action: create
      - entity: "base/masterdata"
        action: create
      - entity: "base/audit"
        action: create
      - entity: "base/config"
        action: create
    test_ref: test_base_migration
    negative_assertions:
      - assertion: "base/ 下任何文件不应 import sme/ 或 agency/ 包"
        method: test_base_no_sme_dependency

  - id: T-02
    from: BASE_READY
    to: SME_READY
    trigger: migrateSme()
    precondition: "BASE_READY 状态"
    postcondition: "sme/ 下所有子包编译通过，全量测试通过"
    side_effects:
      - entity: "sme/invoice"
        action: create
      - entity: "sme/arap"
        action: create
      - entity: "sme/cash"
        action: create
      - entity: "sme/asset"
        action: create
      - entity: "sme/expense"
        action: create
      - entity: "sme/budget"
        action: create
    test_ref: test_sme_migration
    negative_assertions:
      - assertion: "sme/ 下任何文件不应 import agency/ 包"
        method: test_sme_no_agency_dependency

  - id: T-03
    from: SME_READY
    to: AGENCY_READY
    trigger: createAgency()
    precondition: "SME_READY 状态"
    postcondition: "agency/ 下子包存在，编译通过"
    side_effects:
      - entity: "agency/tenant"
        action: create
      - entity: "agency/batch"
        action: create
      - entity: "agency/client"
        action: create
      - entity: "agency/mobile"
        action: create
    test_ref: test_agency_creation

  - id: T-04
    from: AGENCY_READY
    to: DONE
    trigger: fullTest()
    precondition: "AGENCY_READY 状态"
    postcondition: "mvn test 0新增失败，核心链路可用"
    test_ref: test_full_regression

# --- Constraints ---
constraints:
  - id: C-01
    type: architecture
    rule: "Base 层不得依赖 SME 或 Agency 层"
    enforcement: "编译检查 + Code Review"
  - id: C-02
    type: architecture
    rule: "SME 和 Agency 层只能依赖 Base 层，不能互相依赖"
    enforcement: "编译检查 + Code Review"
  - id: C-03
    type: process
    rule: "每个子包单独搬迁，每步验证后 git commit"
    enforcement: "三步闭环流程"
  - id: C-04
    type: process
    rule: "搬迁不改业务逻辑，只改 package 和 import"
    enforcement: "git diff 审查"

# --- Acceptance Tests ---
acceptance_tests:
  - id: AT-001
    description: "底座搬迁后科目体系可用"
    method: test_base_subject_migration
    assertion: "SubjectService 在 base/subject 下可用，编译通过"
    status: missing
  - id: AT-002
    description: "底座搬迁后凭证引擎可用"
    method: test_base_voucher_migration
    assertion: "VoucherService 在 base/voucher 下可用，凭证CRUD正常"
    status: missing
  - id: AT-003
    description: "底座不依赖 SME"
    method: test_base_no_sme_dependency
    assertion: "base/ 下无 import 指向 sme/ 或 agency/"
    status: missing
  - id: AT-004
    description: "全量回归测试通过"
    method: test_full_regression
    assertion: "mvn test 失败数与搬迁前一致"
    status: missing

# --- Out of Scope ---
out_of_scope:
  - "前端代码重构（API 路径不变，前端不需要改）"
  - "Flyway 迁移文件搬迁"
  - "测试代码搬迁"
  - "业务逻辑变更或优化"
  - "Agency 分支功能开发（仅创建包结构）"
  - "工资薪酬模块实现（由 S-14 单独处理）"

# --- Dependencies ---
dependencies:
  - spec: 底座与双分支开发计划
    relation: "执行计划与本 SPEC 一致，按 P0-1→P0-2→P0-3→P0-4→P0-5 顺序执行"
```

---

> **文档结束。** 本 SPEC 定义了底座架构搬迁的规范。审核通过后进入 P0-2 执行阶段。