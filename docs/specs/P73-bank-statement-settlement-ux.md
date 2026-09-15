# P73 SPEC — 银行流水核销体验优化（状态命名 + 批量核销 + 小额直制证 + 待办提醒）

> **版本**：V1.1 | **最后修改**：2026-09-15 | **作者**：Sisyphus
> **状态**：✅ 批1已实现（P0 状态命名 + 状态联动 Bug 修复 + 存量修正）；⏸️ 批1范围调整（批量核销跳过——勘察发现凭证已全部生成，无单可核）；⏳ 批2待启动

> **编号**：HUICAI-SPC-073 | 优先级：高（P73）
> 依据：生产环境银行对账单出现 85 条 `payment_created` 状态堆积（业务单据已生成但凭证未生成），用户误解"已生单"为已完成；核销需逐条手工操作，效率低
> 目标：消除状态命名歧义、提供批量核销能力、小额交易简化流程、待办可视化提醒
> 工期：分两批交付（批1: P0+P1；批2: P2+P3）

> **关联需求**: REQ-2026-079（银行流水核销体验优化）、REQ-2026-007（自动制证）、REQ-2026-072（批量凭证审核）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-15 | 初版，审核通过（4 项审核点全部确认） |
| V1.1 | 2026-09-15 | 批1实现：**勘察发现 85 条流水的业务单据已全部 VOUCHERED（凭证 DRAFT），批量核销无对象，范围调整为「状态联动 Bug 修复 + 存量 85 条修正」**。根因：`BusinessDocServiceImpl.generateVoucher` 三处制证落点不回写流水状态。修复：新增 `markDocVouchered()` 统一回写（条件更新幂等）。存量：UPDATE 85 条流水→voucher_generated。测试：BusinessDocServiceImplTest +3 场景（Red 2 fail → Green 39 pass），全量回归 1598 通过 0 Failures |

---

## 0. 背景与问题

### 0.1 现状数据（2026-09-15 生产库）

| review_status | 数量 | 实际含义 | 用户感知 |
|---|---|---|---|
| `payment_created` | 85 | 业务单据已生成，**凭证未生成** | 误以为"已完成" |
| `voucher_generated` | 15 | 凭证已生成（手续费直制证） | 正确 |

### 0.2 核心问题

1. **状态命名误导**：`payment_created` 前端显示"已生单"，用户误以为流程结束，实际还需核销制证
2. **核销断点**：85 条业务单据无批量核销入口，需逐条去核销工作台手工操作
3. **小额过度设计**：¥87 业务收款也要走"生单→核销→制证"三步，操作成本高于交易本身
4. **待办不可见**：无仪表盘提醒，用户不知道有 85 条待核销

---

## 1. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 批次 |
|---|--------|------|------|------|------|
| 1 | P0 | 状态命名：`payment_created` 前端显示"已生单"→"待核销" | `bankStatement.ts` + `BankStatementView.vue` | ✅ 低 | 批1 |
| 2 | P1 | 批量核销：核销工作台支持多选业务单据批量制证 | 核销工作台前端 + `ReconciliationService` | ⚠️ 中 | 批1 |
| 3 | P2 | 阈值配置：金额 < 阈值的业务收付允许"直接制证"跳过核销 | `AutoGenerationService` + 系统配置表 | ⚠️ 中 | 批2 |
| 4 | P3 | 核销提醒：仪表盘"待核销 N 条"卡片 + 跳转 | 仪表盘前端 + 统计 API | ✅ 低 | 批2 |

---

## 2. P0 状态命名优化（批1）

### 2.1 输入契约

当前 `REVIEW_STATUS_LABELS` 映射（`frontend/src/api/modules/bankStatement.ts`）：

| reviewStatus | 当前显示 | 问题 |
|---|---|---|
| `payment_created` | `B已生单` | 误导为"已完成"，实际凭证未生成 |
| `voucher_generated` | `A已制证` | 正确 |

### 2.2 输出契约

| reviewStatus | 新显示 | 颜色 | 说明 |
|---|---|---|---|
| `payment_created` | `待核销` | warning（黄） | 业务单据已生成，待核销制证 |
| `voucher_generated` | `已制证` | success（绿） | 凭证已生成，流程完成 |

### 2.3 影响面

- `frontend/src/api/modules/bankStatement.ts`：`REVIEW_STATUS_LABELS` 映射
- `frontend/src/views/finance/bank-statement/BankStatementView.vue`：状态标签、统计条文案、批量操作按钮可用性判断（`canAudit`/`canApprove` 引用的状态名不变，仅显示层改）
- **不改后端**：`payment_created` 枚举值保持不变（DB/API 兼容）

### 2.4 异常处理

| 场景 | 处理 |
|---|---|
| 历史数据 `payment_created` | 前端显示自动变为"待核销"，无迁移 |
| `BATCH_STATUS_MATRIX` 引用 | 检查批量操作按钮的状态矩阵，确保"待核销"状态仍可触发核销操作 |

---

## 3. P1 批量核销（批1）

### 3.1 现状

核销工作台（`/arap/reconciliation`）当前仅支持**单条**业务单据核销制证。85 条待核销需 85 次手工操作。

### 3.2 输入契约

| # | 场景 | 前置条件 |
|---|------|---------|
| S1 | 用户在核销工作台勾选多条 `VOUCHERED=false` 的业务单据 | 单据状态为 `CONFIRMED` 且 `voucher_id IS NULL` |
| S2 | 用户点击"批量制证" | 选中 2-100 条单据 |
| S3 | 部分单据制证失败（如科目未配置） | 返回失败明细，成功部分正常提交 |

### 3.3 输出契约

**API**：`POST /sme/arap/v1/reconciliation/batch-generate-voucher`

```json
{
  "docIds": [289, 290, 291],
  "result": {
    "total": 3,
    "success": 2,
    "failed": [{"docId": 291, "docNo": "SK-2024-001", "reason": "科目 6603 未配置"}]
  }
}
```

**事务语义**：
- 每条单据独立事务（`REQUIRES_NEW`），单条失败不影响其他
- 制证成功后：业务单据 `voucher_id` 回填、状态 → `VOUCHERED`；银行流水 `reviewStatus` → `voucher_generated`

### 3.4 状态流转

```
[业务单据] CONFIRMED --批量制证--> VOUCHERED
[银行流水] payment_created --联动--> voucher_generated
```

### 3.5 BDD 验收标准

**BDD-1 批量制证全成功**
- Given 核销工作台有 3 条 `CONFIRMED` 且未制证的业务单据（金额 100/200/300）
- When 用户勾选 3 条并点击"批量制证"
- Then 返回 success=3，3 张凭证生成，单据状态变为 VOUCHERED，关联流水状态变为 voucher_generated

**BDD-2 批量制证部分失败**
- Given 3 条单据中 1 条科目未配置
- When 批量制证
- Then 返回 success=2 + failed=1（含原因），成功的 2 条正常制证，失败的保持 CONFIRMED

**BDD-3 幂等**
- Given 单据已 VOUCHERED
- When 再次批量制证
- Then 返回 failed=1，原因"已制证，跳过"，不重复生成凭证

---

## 4. P2 阈值配置（批2）

### 4.1 设计

新增系统配置项 `huicai.bank.smallAmountDirectVoucher`（默认 `false`）+ 阈值 `huicai.bank.smallAmountThreshold`（默认 `1000.00`）。

当 `smallAmountDirectVoucher=true` 且业务收付金额 < 阈值时，`business_receipt`/`business_payment` 走 A 类路径（直接制证），跳过业务单据 + 核销环节。

### 4.2 状态流转（阈值开启后）

```
[business_receipt/payment] 金额 < 1000 → voucher_generated（直制证）
[business_receipt/payment] 金额 ≥ 1000 → payment_created（需核销）
```

### 4.3 异常处理

| 场景 | 处理 |
|---|---|
| 阈值配置缺失 | 默认 1000.00，功能默认关闭 |
| 金额恰好等于阈值 | 走核销路径（`<` 而非 `<=`） |
| 直制证后需红冲 | 与 A 类一致，凭证红冲即可 |

### 4.4 BDD 验收标准

**BDD-4 阈值开启小额直制证**
- Given `smallAmountDirectVoucher=true`, 阈值=1000，流水金额 500，分类 business_receipt
- When 确认后自动生单
- Then 直接生成凭证，状态 voucher_generated，无业务单据

**BDD-5 阈值关闭默认行为**
- Given 配置关闭或金额 ≥ 阈值
- When 确认后自动生单
- Then 生成业务单据，状态 payment_created（与现状一致）

---

## 5. P3 核销提醒（批2）

### 5.1 设计

**API**：`GET /sme/cash/v1/bank-statements/pending-settlement-count?accountId=` 返回 `payment_created` 状态流水数（复用已有 `status-counts` 数据，或新增轻量端点）。

**前端**：仪表盘新增卡片"待核销业务单据 N 条"，点击跳转银行流水页并自动过滤 `reviewStatus=payment_created`。

### 5.2 输出契约

| 卡片 | 数据源 | 跳转 |
|---|---|---|
| 待核销业务单据 | `status-counts.payment_created` | `/finance/bank-statement?reviewStatus=payment_created` |

### 5.3 BDD 验收标准

**BDD-6 仪表盘提醒**
- Given 存在 85 条 payment_created 流水
- When 用户打开仪表盘
- Then 显示"待核销业务单据 85 条"卡片
- When 点击卡片
- Then 跳转银行流水页且列表仅显示 85 条待核销记录

---

## 6. 实施计划

| 批次 | 内容 | 交付物 | 预估 |
|---|---|---|---|
| 批1 | P0 状态命名 + P1 批量核销 | 前端标签改 + 核销工作台批量制证 API + 测试 | 1 个 commit |
| 批2 | P2 阈值配置 + P3 核销提醒 | 配置项 + AutoGenerationService 阈值分支 + 仪表盘卡片 + 测试 | 1 个 commit |

**测试策略**：
- P0: 前端单测（label 映射断言）
- P1: `ReconciliationServiceTest` 批量制证 BDD-1/2/3（RealDB）
- P2: `AutoGenerationServiceTest` 阈值分支 BDD-4/5
- P3: 仪表盘卡片渲染测试 + 跳转参数断言

**回滚方案**：
- P0: 纯前端 label，回滚 label 映射即可
- P1: API 新增不改旧端点，回滚前端按钮即可
- P2: 配置默认关闭，回滚不影响存量
- P3: 纯前端展示，回滚卡片即可

---

## 7. 风险边界确认

| 改动 | 是否触碰 | 说明 |
|---|---|---|
| 状态命名 | ❌ 不触碰状态机 | 仅前端显示层，DB/API 枚举不变 |
| 批量核销 | ⚠️ 触碰凭证生成 | 复用现有 `generateVoucher` 单条逻辑，独立事务 |
| 阈值配置 | ⚠️ 触碰 AutoGenerationService | 新增分支，默认关闭，需审核 |
| 核销提醒 | ❌ 只读 | 复用现有统计 API |

---

> **审核确认点**：
> 1. P0 状态名"待核销"是否准确（备选："待制证"）
> 2. P1 批量核销是否允许跨单据类型（收款单+付款单混合批量）
> 3. P2 阈值默认值 1000 是否合理
> 4. 批1/批2 是否可合并为一次交付
