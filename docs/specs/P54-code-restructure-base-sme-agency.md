# P54 - 代码架构重构：底座/SME/Agency 三分层

> **版本**：DRAFT v0.1 | **日期**：2026-07-17 | **作者**：Hermes
> **状态**：待老丁审核
> **关联文档**：[项目说明](../项目说明.md)、[技术方案](../技术方案.md)、[分层架构规范](../架构/分层架构规范.md)

---

## 1. 背景与目标

### 1.1 现状

当前所有代码集中在 `com.huicai.module.*` 下，扁平结构，无分层：

```
com.huicai.module
├── system    # 科目/期间/权限/基础数据
├── finance   # 凭证/资金流水/银行对账
├── arap      # 应收应付/核销/费用报销
├── tax       # 进销项发票
├── asset     # 固定资产
├── budget    # 预算
├── report    # 报表
├── ai        # AI 编排层
└── storage   # MinIO 存储
```

### 1.2 目标架构

项目说明文档定义的三分层架构：

```
com.huicai.base    # 底座 — 所有企业通用的财务骨架
com.huicai.sme     # SME 分支 — 中小微企业一站式财务闭环
com.huicai.agency  # Agency 分支 — 代账公司多客户批量处理引擎
com.huicai.common  # 通用工具/异常/响应（保持不变）
com.huicai.config  # 全局配置（保持不变）
```

### 1.3 为什么要改

| 原因 | 说明 |
|------|------|
| 架构对齐 | 项目说明和技术方案已定义三分层，代码需跟进 |
| 职责清晰 | 底座=全员通用，SME=中小微企业，Agency=代账公司，互不干扰 |
| 增量开发 | Agency 分支后续开发时与 base/sme 物理隔离，降低耦合 |
| 代码治理 | 明确模块边界，防止跨层依赖 |

---

## 2. 模块归属映射

### 2.1 映射矩阵

| 当前包 | 目标层 | 目标包 | 说明 |
|--------|-------|--------|------|
| `module.system` | **base** | `base.system` | 科目/期间/权限/基础数据 |
| `module.finance` (部分) | **base** | `base.voucher` | 凭证引擎/科目余额/编号关联 |
| `module.report` | **base** | `base.report` | 报表引擎 |
| `module.storage` | **base** | `base.storage` | MinIO 附件存储 |
| `module.finance` (部分) | **sme** | `sme.cash` | 银行流水/资金管理/对账 |
| `module.tax` | **sme** | `sme.tax` | 进销项发票/税务 |
| `module.arap` | **sme** | `sme.arap` | 应收应付/核销/费用报销 |
| `module.asset` | **sme** | `sme.asset` | 固定资产 |
| `module.budget` | **sme** | `sme.budget` | 预算管理 |
| (新建) | **sme** | `sme.salary` | 工资薪酬（S-14） |
| (新建) | **agency** | `agency.*` | 全部待建 |
| `module.ai` | **横切** | `ai` | 保持独立，不变 |
| `common` | **通用** | `common` | 保持不变 |
| `config` | **配置** | `config` | 保持不变 |

### 2.2 finance 包拆分说明

`module.finance` 当前混装了底座和 SME 的内容，需要拆分为二：

| 当前 finance 子模块 | 归属 | 目标包 |
|-------------------|------|--------|
| VoucherController/Service/Entity | **base** | `base.voucher` |
| VoucherStateMachineService | **base** | `base.voucher` |
| SubjectBalanceService | **base** | `base.voucher` |
| PeriodCloseService | **base** | `base.voucher` |
| VoucherTemplateService | **base** | `base.voucher` |
| NumberingTraceService | **base** | `base.voucher` |
| BusinessDocService/Entity | **sme** | `sme.arap`（合并到核销） |
| BankStatementService | **sme** | `sme.cash` |
| BankReconciliationService | **sme** | `sme.cash` |
| BankJournalService | **sme** | `sme.cash` |
| SalesInvoiceImportService | **sme** | `sme.tax` |
| InputInvoiceImportService | **sme** | `sme.tax` |

---

## 3. 实施步骤

### 3.1 原则

- **增量迁移，非大爆炸**
- 每次迁移一个模块，编译+测试通过后再继续
- 先移 base，再移 sme，最后移 common/config（不动）
- 每个模块迁移后验证 `mvn test` 全量通过

### 3.2 实施顺序

```
Phase 1: 创建目录结构 + 迁移 common
  → 创建 com.huicai.base / .sme / .agency 空目录
  → common 和 config 不动（已是独立包）

Phase 2: 迁移 base 模块
  Step 1: base.system（原 module.system）
  Step 2: base.voucher（原 module.finance 凭证部分）
  Step 3: base.report（原 module.report）
  Step 4: base.storage（原 module.storage）

Phase 3: 迁移 sme 模块
  Step 5: sme.tax（原 module.tax + finance 导入部分）
  Step 6: sme.arap（原 module.arap + finance BusinessDoc 部分）
  Step 7: sme.cash（原 module.finance 资金部分）
  Step 8: sme.asset（原 module.asset）
  Step 9: sme.budget（原 module.budget）

Phase 4: 保留旧包兼容层（可选）
  → 添加 @Deprecated 转发类，逐步迁移外部引用

Phase 5: 清理
  → 删除空余的 module 目录
  → 更新文档注册表
```

### 3.3 每个模块迁移的标准步骤

```
1. 创建目标包目录
2. 移动 .java 文件（git mv）
3. 更新 package 声明
4. 更新 import 引用（跨模块引用需特别注意）
5. 编译验证
6. 跑该模块的测试
7. 跑全量测试
8. commit
```

---

## 4. 需要修改的配置

### 4.1 Spring Boot 启动类

```java
// 当前
@SpringBootApplication
@MapperScan("com.huicai.module.*.mapper")

// 改为
@SpringBootApplication(scanBasePackages = {
    "com.huicai.common",
    "com.huicai.config",
    "com.huicai.base",
    "com.huicai.sme",
    "com.huicai.ai"
})
@MapperScan({
    "com.huicai.base.*.mapper",
    "com.huicai.sme.*.mapper",
    "com.huicai.ai.mapper"
})
```

### 4.2 MyBatis-Plus 配置

MyBatis-Plus 的 typeAliasesPackage 和 typeHandlersPackage 也需要更新扫描路径。

### 4.3 AOP 切面

审计日志 AOP 切面的 `@Around` 表达式需要更新包路径。

---

## 5. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 跨模块 import 遗漏 | 编译失败 | 每次迁移后跑全量编译 |
| 运行时组件扫描遗漏 | 启动失败 | 启动集成测试验证 |
| MyBatis mapper 未扫描到 | 数据访问失败 | Mapper 测试验证 |
| 测试类未同步迁移 | 测试遗漏 | 源文件 + 测试文件同时迁移 |
| 其它模块引用被迁移的包 | 编译错误 | 先移底层模块（base），再移上层（sme） |
| 文件数多（388个Java文件） | 工作量大 | 增量迁移，每个模块独立 commit |

---

## 6. 不做的事情

- 不改数据库表名、字段名
- 不改 API 路径（/api/v1/ 保持不变）
- 不改前端代码
- 不改 Docker 部署配置
- 不改业务逻辑
- 不改测试逻辑（只改包名）

---

## 7. 工作量估算

| 阶段 | 文件数 | 预估工时 | 说明 |
|------|-------|---------|------|
| Phase 1 创建目录 | ~10 | 0.5h | 创建空目录结构 |
| Phase 2 base 模块 | ~150 | 2-3h | 4个模块，每步30-45min |
| Phase 3 sme 模块 | ~200 | 2-3h | 5个模块，每步20-30min |
| Phase 4 兼容层 | 可选 | 1h | 添加转发类 |
| Phase 5 清理 | ~10 | 0.5h | 删除旧目录 |
| 验证 | 全量 | 1h | 编译+测试 |
| **合计** | **388** | **7-10h** | |

---

## 8. 待确认问题

1. **实施顺序**：先 base 再 sme（推荐）还是先 sme 再 base？
2. **兼容层**：是否需要保留旧包的转发类（@Deprecated）？推荐：不保留，一步到位，减少技术债。
3. **agency 目录**：空目录是否现在创建？推荐：创建，为后续开发预留位置。
4. **finance 包拆分**：凭证部分（base.voucher）和资金部分（sme.cash）如何确切划分？需要精确定义每个类的归属。