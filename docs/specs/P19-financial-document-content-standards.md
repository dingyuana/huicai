# P19 SPEC — 财务单据内容完整性与展示规范

> 状态：初版
> 目标：统一 4 类财务单据的数据完整性、字段规范、摘要生成规则和展示层标准
> 关联分支：`fix/business-doc-detail-incomplete`
> 工期：3 批

---

## 1. 现状摸底 (2026-06-16)

| 单据类型 | 后端模块 | 控制器 | 前端页面 | 测试覆盖 |
|---|---|---|---|---|
| 记账凭证 (Voucher) | `finance/voucher/` | `VoucherController.java` | `views/finance/voucher/*.vue` | 有 |
| 业务单据 (BusinessDoc) | `finance/business-doc/` | `BusinessDocController.java` | `views/finance/business-doc/*.vue` | 有 (P19-2) |
| 应收/应付 (AR/AP) | `arap/` | `ReceivableController.java` / `PayableController.java` | `views/arap/*.vue` | 部分 |
| 费用报销单 | `arap/expense/` | `ExpenseReimbursementController.java` | `views/arap/expense/*.vue` | 部分 |

### 1.1 当前统计

| 指标 | 值 | 备注 |
|---|---|---|
| 凭证表 `t_voucher` 列数 | 21 列 | 含 5 态状态机 |
| 业务单据表 `t_business_doc` 列数 | 19 列 | 含 5 态状态机 |
| 费用报销表 `t_expense_reimbursement` 列数 | 15 列 | 5 态 |
| 应收 `t_receivable` 列数 | ~12 列 | 需核实 |
| 应付 `t_payable` 列数 | ~12 列 | 需核实 |
| 已完成修复 (P19-2 已提交) | 6 文件 | Service/VO/前端/Vue |
| 待修复 (P19-3) | 4 文件 | 前端列表/前端 API |
| 待修复 (P19-4) | 3 文件 | 应收/应付/报销单 |

### 1.2 已完成修复 (P19-2)

| # | 修复内容 | 文件 | 状态 |
|---|---|---|---|
| D1 | VO 增加 createdBy/submittedBy/approvedBy + enrichedSummary 字段 | `BusinessDocVO.java` | ✅ |
| D2 | populateUserNames 回填制单人/提交人/审批人真实姓名 | `BusinessDocServiceImpl.java` | ✅ |
| D3 | update 方法防御：可选字段缺省时保留原值避免被 null 覆写 | `BusinessDocServiceImpl.java` | ✅ |
| D4 | loadDoc 补全 supplierId/customerId/applicantId/deptId/attachmentIds | `BusinessDocEdit.vue` | ✅ |
| D5 | 详情页加"去核销"按钮 + 核销推荐抽屉 | `BusinessDocDetail.vue` | ✅ |
| D6 | reconciliation API 补 getPaymentRecommend/getReceiptRecommend | `reconciliation.ts` | ✅ |
| D7 | findSubjectByCode 缺失科目时抛 BusinessException | `BusinessDocServiceImpl.java` | ✅ |
| D8 | enrichSummary 方法拼接"付/收{对方单位}-{原摘要}" | `BusinessDocServiceImpl.java` | ✅ |
| D9 | 详情页显示 enrichedSummary 替代原始 summary | `BusinessDocDetail.vue` | ✅ |
| D10 | 19 个单元测试 (update 防御 + getDetail 回填 + generateVoucher 防护) | `BusinessDocServiceImplTest.java` | ✅ |

---

## 2. 设计规范

### 2.1 字段必填标准

所有财务单据统一规定以下字段为**必填**：

| 字段 | 业务单据 | 凭证 | 应收/应付 | 费用报销 | 验收标准 |
|---|---|---|---|---|---|
| 对方单位 ID | ✅ (supplierId/customerId) | - | ✅ (partyId) | ✅ (employeeId) | 不可为 null |
| 对方单位名称 | ✅ (展示层) | ✅ (摘要) | ✅ | - | 不可为空、不可简写 |
| 金额 | ✅ | ✅ (借贷双行) | ✅ | ✅ | > 0 |
| 日期 | ✅ (docDate) | ✅ (period) | ✅ | ✅ | 合法的会计期间内 |
| 摘要 | ✅ | ✅ | ✅ | ✅ | 不可为空，含对方单位信息 |
| 制单人 | ✅ (自动回填) | ✅ | ✅ | ✅ | 由 SecurityUtils 自动注入 |
| 审批人 | 条件必填 | 条件必填 | 条件必填 | 条件必填 | 审批流完成后自动记录 |

### 2.2 摘要规范 (核心)

**通用规则**：
```
摘要格式 = {收/付}{对方单位名称}{原始摘要}
例：付XX科技7月服务费
    收XX商贸货款
```

**各单据级别**：

| 级别 | 场景 | 摘要内容 | 示例 |
|---|---|---|---|
| 凭证摘要 | 生成自业务单据 | `付/收{对方单位}-{原摘要}[发票号]` | 付XX科技-生成自单据SK2026060001[25922000000054246714] |
| 凭证分录摘要 | 业务单据分录 | `{分录摘要}[发票号]` 优先，缺省用凭证摘要 | 7月服务费[25922000000054246714] |
| 业务单据摘要 | 手工录入 | `{对方单位}{业务性质}` | 付XX科技7月服务费 |
| 业务单据展示摘要 | 列表/详情 | `{单据专用摘要} \|\| enrichedSummary[发票号]` | 付XX科技-生成自单据FK2026060001[25922000000054246714] |
| 核销摘要 | 核销日志 | `核销{应收/应付单单号}` | 核销SK2026060001 |
| 红冲摘要 | 红冲单据 | `红冲自 {原单据号}` | 红冲自FK2026060001 |

**enrichedSummary 实现逻辑** (已实现，2026-06-27 新增发票号后缀)：
```java
enrichSummary(BusinessDocEntity entity):
    base = entity.summary != null && !blank ? entity.summary : "生成自单据 " + entity.docNo
    partyName = resolvePartyName(entity)  // 优先 supplier, 其次 customer
    if partyName != null && !blank -> prefix = SUPPLIER_DOC_TYPES.contains(entity.docType) ? "付" : "收"
                                     base = prefix + partyName + "-" + base
    if entity.invoiceNo != null && !blank -> base += "[" + entity.invoiceNo + "]"  // 发票号后缀，审计追溯用
    return base
```

### 2.3 单据编号规则 (已实现)

**统一格式**：`{前缀}{年份}{月份}{4位流水号}`

| 单据类型 | 前缀 | 示例 |
|---|---|---|
| 收款单 (RECEIPT) | SK | SK2026060001 |
| 付款单 (PAYMENT) | FK | FK2026060002 |
| 报销单 (EXPENSE) | BX | BX2026060001 |
| 进项发票 (INVOICE_IN) | FPR | FPR2026060001 |
| 销项发票 (INVOICE_OUT) | FPS | FPS2026060001 |
| 其他应收 (OTHER_RECEIVABLE) | QTY | QTY2026060001 |
| 其他应付 (OTHER_PAYABLE) | QTF | QTF2026060001 |

流水号使用 Redis INCR 保证原子性。

### 2.4 状态机标准 (已实现)

**业务单据**：`DRAFT → SUBMITTED → APPROVED → VOUCHERED → (红冲 → DRAFT)`
**凭证**：`DRAFT → SUBMITTED → AUDITED → POSTED`
**费用报销单**：`DRAFT → SUBMITTED → APPROVED/REJECTED → VOUCHERED`
**应收/应付**：`PENDING → PARTIAL → SETTLED → BAD_DEBT`

所有状态机必须：
- 拒绝非法转移（如 DRAFT → VOUCHERED 跳转）
- 允许红冲从已制证状态开始
- 已归档/已结账期间不允许状态变更

### 2.5 对方单位名称处理规范

| 场景 | 规则 | 实现 |
|---|---|---|
| Supplier 名称回填 | `supplierId → VendorMapper` | `populatePartyNames()` |
| Customer 名称回填 | `customerId → CustomerMapper` | `populatePartyNames()` |
| 员工名称回填 | `employeeId → EmployeeMapper` | 待 P19-4 |
| 摘要中对方单位 | `resolvePartyName(entity)` | `enrichSummary()` |
| 收付款标示 | PAYMENT/EXPENSE/INVOICE_IN/OTHER_PAYABLE → "付" | `enrichSummary()` |
| 收付款标示 | RECEIPT/INVOICE_OUT/OTHER_RECEIVABLE → "收" | `enrichSummary()` |

### 2.6 制单人/审批人/审核人显示

| 角色字段 | 业务单据 | 凭证 | 应收/应付 | 费用报销 |
|---|---|---|---|---|
| createdBy → createdByName | ✅ | ✅ | 待 P19-4 | 待 P19-4 |
| submittedBy → submittedByName | ✅ | ✅ | 待 P19-4 | 待 P19-4 |
| approvedBy → approvedByName | ✅ | ✅ | 待 P19-4 | 待 P19-4 |
| auditedBy → auditedByName | - | ✅ | - | - |
| postedBy → postedByName | - | ✅ | - | - |

---

## 3. 各模块详细规范

### 3.1 记账凭证 (Voucher) — P19-3

**当前问题 (Spec 原文)**：
- 系统内可能存在的仅有系统流水号的摘要，缺乏业务含义
- 凭证摘要过于简单，无法通过摘要快速了解业务内容

**强制规范**：
1. **摘要**：所有凭证的 `summary` 字段必须符合"对方单位+业务性质"格式
   - 手工录入：录入时必须填写完整的"对方单位+业务性质"
   - 自动生成（来自业务单据）：自动拼接 `enrichedSummary`
   - 已提交/已审核/已记账凭证：**禁止修改摘要**
2. **制单人/审核人/记账人**：
   - 制单：系统自动回填当前用户
   - 审核：显示审核人姓名、审核时间
   - 记账：显示记账人姓名、记账时间
3. **借/贷金额**：
   - 借方合计 = 贷方合计（试算平衡校验）
   - 不可为 0（一条分录借方或贷方必须 > 0）
4. **附件**：可选关联，显示附件张数

### 3.2 收款/付款单据 (BusinessDoc) — P19-2 (已修复)

**已完成** 见 1.2 节。

**待完成 — P19-3**：
- 列表页 `BusinessDocList.vue` 改用 `enrichedSummary` 替代原始 summary 显示
- 补充前端 API 调用和数据类型

### 3.3 应收款/应付款单据 (AR/AP) — P19-4

**当前问题**：
- 应收应付列表和详情页缺少对方单位名称回填
- 缺少制单人/提交人/审核人显示
- 摘要未规范化
- 核销后状态更新不一致

**改造计划**：

1. **ReceivableVO / PayableVO** 增加字段：
   - `partyName` (对方单位名称)
   - `createdByName` / `approvedByName`
   - `enrichedSummary`

2. **ReceivableServiceImpl / PayableServiceImpl**：
   - 注入 `CustomerMapper` / `VendorMapper`
   - 注入 `UserMapper`
   - 新增 `populatePartyNames()` / `populateUserNames()` 方法
   - 在 `getDetail()` 和 `pageQuery()` 中调用

3. **摘要规范**：
   - 应收摘要：`收{对方单位}{业务类型}`
   - 应付摘要：`付{对方单位}{业务类型}`

### 3.4 费用报销单据 (Expense Reimbursement) — P19-4

**当前问题**：
- 缺少申请人姓名/部门名回填
- 报销单号格式需确认统一
- 摘要未规范化

**改造计划**：
1. **ExpenseReimbursementVO** 增加字段：
   - `employeeName` / `deptName`
   - `createdByName` / `approvedByName`
   - `enrichedSummary`

2. **ExpenseReimbursementServiceImpl**：
   - 注入 `EmployeeMapper` / `DeptMapper` / `UserMapper`
   - 新增名称回填方法
   - 在 `getDetail()` 和 `pageQuery()` 中调用

---

## 4. 验收标准 (跨模块)

| # | 验收项 | 模块 | 优先级 |
|---|---|---|---|
| AC1 | 单据详情页显示对方单位名称（不可为空白/null） | 全部 | P0 |
| AC2 | 摘要不符合"付/收+对方单位+业务性质"格式的需修复 | 全部 | P0 |
| AC3 | 凭证摘要显示 "付/收XX科技-摘要" 格式 | 凭证/业务单据 | P0 |
| AC4 | 制单人/审批人/记账人/提交人 真实姓名回填 | 全部 | P1 |
| AC5 | 前端列表和详情均展示 enrichedSummary | 全部 | P1 |
| AC6 | 可选字段编辑时保留原值，不被 null 覆写 | 业务单据 | P1 |
| AC7 | 科目代码缺失时抛异常，避免生成空凭证 | 业务单据 | P1 |
| AC8 | 状态机非法转移拒绝 + 返回明确错误信息 | 全部 | P1 |
| AC9 | 所有单据编号格式统一：前缀+年月+顺序号 | 全部 | P1 |
| AC10 | 红冲单据摘要标明"红冲自原单据号" | 业务单据 | P2 |
| **AC11** | **发票导入生成的凭证/业务单据，摘要包含发票号，格式：[发票号]** | **发票/业务单据/凭证** | **P0** |
| AC12 | 附件张数/链接在详情页可查看 | 全部 | P2 |
| AC13 | 数据权限：仅可查看本单位（orgId）单据 | 全部 | P3 |
| AC14 | 审批流完备：制单人≠审批人 | 全部 | P3 |

---

## 5. 常见错误及预防

| # | 常见错误 | 预防机制 | 状态 |
|---|---|---|---|
| E1 | 摘要仅包含系统流水号 | create() 时校验 summary 不可为纯数字/编号 | 待实现 |
| E2 | 对方单位名称为 null/空串 | populatePartyNames 拒绝回填空值 | ✅ |
| E3 | 编辑时可选字段被 null 覆写 | update 方法 if-not-null 保护 | ✅ |
| E4 | 科目不存在时生成 0 分录空凭证 | findSubjectByCode 抛 BusinessException | ✅ |
| E5 | 前端 loadDoc 漏字段 | 补全所有缺失 DTO 字段 | ✅ |
| E6 | 期间已关账但单据仍可修改 | validatePeriodOpen 拦截 | ✅ |
| E7 | 审批人为制单人自己 | 制单和审批角色分离校验 | 待实现 |
| E8 | 核销完成后应收应付状态未更新 | 核销事务中同步更新状态 | 待 P19-4 |

---

## 6. 设计决策记录

| 决策 ID | 决策 | 理由 | 影响范围 |
|---|---|---|---|
| DEC-001 | `enrichedSummary` 作为 VO 新字段，不改 DB | 保持原始数据不变，展示层增强 | BusinessDocVO |
| DEC-002 | voucher 和业务单据共用 enrichSummary 方法 | 确保摘要格式一致 | BusinessDocServiceImpl |
| DEC-003 | findSubjectByCode 抛异常而非返回 null | 配合 @Transactional 回滚，数据完整性 | generateVoucher |
| DEC-004 | update 方法 if-not-null 包裹可选字段 | 前端编辑漏传字段时不丢失数据 | update() |
| DEC-005 | populatePartyNames 批量查询 | 避免 N+1 问题 | pageQuery |
| DEC-006 | populateUserNames 批量查询 | 避免 N+1 问题 | pageQuery |
| DEC-007 | 核销推荐使用抽屉形式而非跳页 | 用户不丢失上下文 | BusinessDocDetail.vue |
| DEC-008 | 金额统一 NUMERIC(18,2) | 人民币精度足够，无多币种需求 | 全表 |

---

## 7. 未来规划 (P19 批外)

- **凭证模板增强**：当前 8 个 docType 的科目映射为硬编码 (`DOC_VOUCHER_SUBJECTS`)，未来用 `VoucherTemplateEntity` 配置驱动
- **审批流引擎**：支持多级审批、会签、条件审批
- **AI 摘要建议**：根据对方单位和业务性质自动推荐摘要
- **批量编辑**：单据列表支持批量修改对方单位
- **数据导出**：单据列表和详情支持导出 Excel/PDF
