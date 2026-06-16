# P19 任务书：财务单据内容完整性与展示规范 — 全模块改造

> 日期：2026-06-16 | 任务 ID：P19-FINANCIAL-DOC-CONTENT
> 上游文档：`docs/specs/P19-financial-document-content-standards.md`
> 关联分支：`fix/business-doc-detail-incomplete`（P19-2 已完成）
> 状态：P19-2 ✅ 已合并 | P19-3 🔄 待开发 | P19-4 📋 待开发

---

## 目标

完成 4 类财务单据（记账凭证/业务单据/应收应付/费用报销）的数据完整性与展示规范改造，统一摘要格式、字段回填、状态机保护和前端展示标准。

---

## P19 批次总览

| 批次 | 范围 | 工时预估 | 优先级 | 状态 |
|---|---|---|---|---|
| **P19-2** | 业务单据 (BusinessDoc) — 核心修复 | 已交付 | P0 | ✅ |
| **P19-3** | 业务单据 前端补全 + 凭证增强 | 0.5d | P0 | 🔄 |
| **P19-4** | 应收/应付 (AR/AP) + 费用报销单改造 | 1.5d | P1 | 📋 |
| **P19-5** | 跨模块联调 + 回归测试 | 0.5d | P1 | 📋 |

---

## P19-2 — 业务单据核心修复 ✅ (已交付)

### 改动清单

| # | 文件 | 改动 | 行数 |
|---|---|---|---|
| 1 | `BusinessDocVO.java` | 新增 `createdBy`/`submittedBy`/`approvedBy`/`enrichedSummary` 字段 + `EntryVO` 补充 `subjectCode`/`subjectName` | +6 |
| 2 | `BusinessDocServiceImpl.java` | 注入 `UserMapper`；新增 `populateUserNames`/`enrichSummary`/`resolvePartyName`；`getDetail`/`pageQuery` 中回填；`update` 可选字段防御；`findSubjectByCode` 抛异常 | +47 |
| 3 | `BusinessDocDetail.vue` | 详情页显示 `enrichedSummary`；新增"去核销"按钮 + 核销推荐抽屉 | +142 |
| 4 | `BusinessDocEdit.vue` | `loadDoc` 补全 `supplierId`/`customerId`/`applicantId`/`deptId`/`attachmentIds` | +5 |
| 5 | `reconciliation.ts` | 新增 `getPaymentRecommend`/`getReceiptRecommend` API | +14 |
| 6 | `BusinessDocServiceImplTest.java` | 19 个测试：10 update 防御 + 5 getDetail 回填 + 4 generateVoucher 防护 | +512 |

### 验收

- `populateUserNames` 回填 createdBy/submittedBy/approvedBy 为真实姓名
- `enrichSummary` 输出 `付/收{对方单位}-{摘要}` 格式
- `update()` 缺省字段保留原值（supplierId/customerId 等）
- `findSubjectByCode` 缺失时抛出 BusinessException
- 详情页显示 enrichedSummary + 核销推荐抽屉
- 19 个测试全部通过 ✅

---

## P19-3 — 业务单据前端补全 + 凭证增强 🔄

### 工作量：0.5d | 优先级：P0

### Step 1: 列表页改用 enrichedSummary（0.2d）

**文件**：`frontend/src/views/finance/business-doc/BusinessDocList.vue`

**当前问题**：列表页 `doc.summary` 显示原始摘要，未展示 `enrichedSummary`。

**改动**：
```diff
- <span>{{ scope.row.summary || '-' }}</span>
+ <span>{{ scope.row.enrichedSummary || scope.row.summary || '-' }}</span>
```

**验收**：列表页每行摘要列显示为"付/收XX科技-原摘要"格式。

### Step 2: 凭证摘要增强（0.2d）

**文件**：`backend/src/main/java/com/huicai/module/finance/service/impl/VoucherServiceImpl.java`

**当前问题**：手工创建的凭证和模板生成的凭证摘要未规范化。

**改动**：
1. `create()` 方法增加摘要校验：手动创建凭证时，`summary` 不得为空（"对方单位+业务性质"）
2. `createByTemplate()` 方法：模板生成凭证的摘要从模板行取，但需要拼接对方单位信息（如有关联单据）
3. `submit()`/`audit()`/`post()` 流转时摘要不做修改

**注意**：不要强行修改已有凭证的 summary，只对新创建凭证做规范校验。

**验收**：
- 新创建凭证 summary 不可为空
- 模板生成凭证自动拼接待对方单位

### Step 3: 凭证详情页显示完整姓名（0.1d）

**文件**：`frontend/src/views/finance/voucher/*.vue`

**改动**：
- 详情页/列表页的"制单人"字段改为显示 `createdByName`（若存在）而非原始 ID
- 审核人、记账人同理

**验收**：凭证页可看到人员真实姓名。

---

## P19-4 — 应收/应付 + 费用报销单改造 📋

### 工作量：1.5d | 优先级：P1

### Step 1: RepayableVO 增强（0.3d）

**文件**：新建 `ReceivableVO.java` / `PayableVO.java`（若不存在 VO 则新建）

**字段新增**：
```java
// 对方单位名称（从 customerId/supplierId 回填）
private String partyName;
// 制单人/提交人/审批人真实姓名
private String createdByName;
private String submittedByName;
private String approvedByName;
// 增强摘要
private String enrichedSummary;
```

### Step 2: PayableServiceImpl/ReceivableServiceImpl 改造（0.4d）

**文件**：
- `arap/service/impl/ReceivableServiceImpl.java`
- `arap/service/impl/PayableServiceImpl.java`

**改动**：
1. 注入 `CustomerMapper` / `VendorMapper` / `UserMapper`
2. 新增 `populatePartyNames(List<VO>)` 方法
3. 新增 `populateUserNames(List<VO>)` 方法
4. 新增 `enrichSummary(ARAPEntity)` 方法
5. 在 `getDetail()` 和 `pageQuery()` 中调用以上方法

**验收**：
- `getDetail(id)` 返回包含 `partyName`/`createdByName`/`enrichedSummary`
- `pageQuery()` 每个 VO 包含名称回填
- 摘要格式统一为 `收/付{对方单位}-{原业务摘要}`

### Step 3: /ReimbursementVO 增强（0.3d）

**文件**：新建 `ExpenseReimbursementVO.java`

**字段新增**：
```java
private String employeeName;
private String deptName;
private String createdByName;
private String approvedByName;
private String enrichedSummary;
```

### Step 4: ExpenseReimbursementServiceImpl 改造（0.3d）

**文件**：`arap/service/impl/ExpenseReimbursementServiceImpl.java`

**改动**：
1. 注入 `EmployeeMapper` / `UserMapper`
2. 新增名称回填方法
3. 在 `getDetail()` 和 `pageQuery()` 中调用

**验收**：费用报销详情显示员工姓名/部门名/制单人。

### Step 5: AR/AP 前端列表页/详情页改造（0.2d）

**文件**：`frontend/src/views/arap/*/` 相关 vue 文件

**改动**：
- 列表页 `partyName` 替代原来的 customerId/supplierId 原始 ID
- 详情页显示 `enrichedSummary` 替代原始 `summary`
- 显示制单人/审批人真实姓名

---

## P19-5 — 跨模块联调 + 回归测试 📋

### 工作量：0.5d | 优先级：P1

### Step 1: 编写集成测试（0.3d）

**测试清单**：

| # | 测试场景 | 覆盖模块 | 预期 |
|---|---|---|---|
| 1 | 业务单据 pageQuery 返回 enrichedSummary | BusinessDoc | enrichedSummary 非空 |
| 2 | 业务单据 getDetail 返回完整 partyName | BusinessDoc | supplierName/customerName 有值 |
| 3 | 凭证创建时 summary 为空 → 拒绝 | Voucher | 400 错误 |
| 4 | 凭证创建时 summary 合规 → 通过 | Voucher | 201 成功 |
| 5 | AR 列表 pageQuery 返回 partyName | Receivable | partyName 非空 |
| 6 | AP 列表 pageQuery 返回 partyName | Payable | partyName 非空 |
| 7 | 报销单 getDetail 返回 employeeName | Expense | employeeName 非空 |
| 8 | 红冲业务单据 → 新单据摘要含"红冲自" | BusinessDoc | 摘要包含源 docNo |
| 9 | 不可修改已审核凭证的摘要 | Voucher | 400 错误 |
| 10 | 期间关账后所有单据只读 | 全部 | validatePeriodOpen 拦截 |

### Step 2: 人工确认检查清单（0.2d）

| # | 检查项 | 检查方式 |
|---|---|---|
| 1 | 启动 Spring Boot，所有端点返回正常 | curl /api/v1/business-docs/page |
| 2 | 前端 npm run build 无报错 | 构建日志 |
| 3 | 业务单据列表展示 enrichedSummary | 浏览器查看 |
| 4 | 应收应付列表展示 partyName | 浏览器查看 |
| 5 | 报销单列表展示 employeeName | 浏览器查看 |
| 6 | 凭证创建时 summary 校验生效 | Postman 测试 |
| 7 | 所有测试通过 | mvn test |

---

## 依赖关系图

```
P19-2 (核心修复) ── 已完成, 作为后续基础
      │
      ├──→ P19-3 (前端 + 凭证) ── 依赖 P19-2 的 enrichedSummary 字段
      │         │
      │         └──→ P19-5 (联调) ── 所有批次完成后回归
      │
      └──→ P19-4 (AR/AP + 报销) ── 独立于 P19-3, 可并行开发
                │
                └──→ P19-5 (联调)
```

P19-3 和 P19-4 **可并行开发**。P19-5 在两者完成后执行。

---

## 交付物清单

| 交付物 | 文件 | 批次 |
|---|---|---|
| 业务单据 VO 增强 | `BusinessDocVO.java` | P19-2 ✅ |
| 业务单据 Service 增强 | `BusinessDocServiceImpl.java` | P19-2 ✅ |
| 业务单据详情页核销 + enrichedSummary | `BusinessDocDetail.vue` | P19-2 ✅ |
| 业务单据编辑页字段补全 | `BusinessDocEdit.vue` | P19-2 ✅ |
| 核销推荐前端 API | `reconciliation.ts` | P19-2 ✅ |
| 业务单据单元测试（19个） | `BusinessDocServiceImplTest.java` | P19-2 ✅ |
| 列表页 enrichedSummary 展示 | `BusinessDocList.vue` | P19-3 |
| 凭证摘要校验增强 | `VoucherServiceImpl.java` | P19-3 |
| 凭证详情页姓名显示 | `Voucher*.vue` | P19-3 |
| AR/AP VO 类 | `ReceivableVO.java` / `PayableVO.java` | P19-4 |
| AR/AP Service 改造 | `ReceivableServiceImpl.java` / `PayableServiceImpl.java` | P19-4 |
| 费用报销 VO 类 | `ExpenseReimbursementVO.java` | P19-4 |
| 费用报销 Service 改造 | `ExpenseReimbursementServiceImpl.java` | P19-4 |
| AR/AP 前端改造 | `views/arap/*.vue` | P19-4 |
| 集成测试（10+ 场景） | `*Test.java` | P19-5 |
| 人工确认记录 | 无（一次性执行） | P19-5 |

---

## 备注

1. **P19-2 已在 `fix/business-doc-detail-incomplete` 分支**，含 9 个原子提交。P19-3/P19-4/P19-5 建议继续在此分支开发或另建 `fix/p19-financial-doc-content` 分支。

2. **后端代码修改需重启 Spring Boot**，前端代码修改需 `npm run build` 后部署。

3. P19-3 和 P19-4 共享 `enrichedSummary` 设计模式，可复用相同的 `enrichSummary() + resolvePartyName()` 逻辑。

4. **受影响的 DB 数据问题**（如 supplier_id 为 NULL、voucher_id 为 NULL）代码层面无法修复，需人工 SQL 恢复。

5. 请先审阅本任务书，确认后再开始实施。
