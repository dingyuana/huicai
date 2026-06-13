# SPEC: Phase 1 — 银行流水导入与智能分类引擎

## 基本信息

- **任务 ID**: P1-BANK-IMPORT
- **类型**: feature
- **优先级**: high
- **依赖**: P0-SKELETON（项目骨架已就绪）、I0-AI-SERVICE（AI 微服务已就绪）
- **执行工具**: OpenCode
- **参考实现**: `/root/data/disk/huihua-finance`（Go 版，业务逻辑可直接移植）

## 背景

当前慧财财务（huicai/Java）已具备基础骨架（Spring Boot + MyBatis-Plus + Vue 3）和 AI 微服务（Python FastAPI + RabbitMQ），但核心的**银行流水批量导入**和**智能分类**功能尚未实现。

huihua-finance（Go 版）已实现了完整的端到端流程：Excel/CSV 导入 → 列名智能映射 → 规则引擎自动分类 → 兜底启发式分类 → 人工确认 → 凭证/单据生成。该项目的业务逻辑成熟，可直接移植为 Java 实现。

## 目标

实现银行流水批量导入与智能分类引擎，达到以下指标：

1. ✅ 支持 Excel/CSV 格式批量导入，自动识别列名（中英文变体）
2. ✅ 分类规则引擎上线，8 条种子规则覆盖 80% 常见流水
3. ✅ 兜底启发式分类确保 100% 流水有分类结果
4. ✅ 导入成功后流水进入"待确认"状态，前端可审核确认
5. ✅ 已有 AI 微服务（match/anomaly/embedding）接入规则引擎下游

## 技术约束

- 后端：Spring Boot 3.x + MyBatis-Plus + JDK 17
- Excel 解析：Apache POI 或 EasyExcel
- 数据库：PostgreSQL 16，已有 `t_bank_statement` 表需要扩充
- AI 通信：RabbitMQ（已有 `AiTaskService` + `TaskConsumer`）
- 前端：Vue 3 + Element Plus（已有 `BankStatementList` 基础页）
- 无需新安装中间件

## 具体要求

### 1. 数据库表

#### 1.1 分类规则表 `t_classification_rule`

```sql
CREATE TABLE t_classification_rule (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    rule_type          VARCHAR(30) NOT NULL DEFAULT 'keyword',
    pattern            TEXT NOT NULL,
    match_field        VARCHAR(30) NOT NULL DEFAULT 'description',
    direction          VARCHAR(10),
    classification     VARCHAR(50) NOT NULL,
    priority           INT NOT NULL DEFAULT 0,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    debit_subject_id   BIGINT,
    credit_subject_id  BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by         BIGINT,
    updated_by         BIGINT,
    deleted            INT DEFAULT 0
);

CREATE INDEX idx_cls_rule_tenant ON t_classification_rule(tenant_id, is_active, priority);
```

#### 1.2 AI 反馈日志表 `t_ai_feedback_log`

```sql
CREATE TABLE t_ai_feedback_log (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            BIGINT NOT NULL,
    bank_txn_id          BIGINT NOT NULL,
    ai_suggested_action  VARCHAR(50),
    ai_confidence        INT,
    ai_business_scene    VARCHAR(100),
    human_action         VARCHAR(50) NOT NULL,
    human_modified_fields JSONB,
    created_by           BIGINT,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_feedback_txn ON t_ai_feedback_log(bank_txn_id);
CREATE INDEX idx_ai_feedback_tenant ON t_ai_feedback_log(tenant_id);
```

#### 1.3 扩充 `t_bank_statement` 表

> **方案 C 迁移策略**：V5 字段**全部保留**（包括 `tx_type` 4 值与 `match_status` 对账语义），**仅新增**字段。P1 不动现有 autoMatch/confirmMatch 逻辑。

需要为 `t_bank_statement` 增加以下字段（通过 DDL 迁移，建议新建 `V17__p1_bank_classification.sql`）：

```sql
-- P1 业务分类相关
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS direction VARCHAR(4);       -- 业务方向 in/out
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS batch_id VARCHAR(50);        -- 导入批号
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS classification VARCHAR(50);  -- 业务分类 bank_fee/.../pending
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS rule_id BIGINT;              -- 命中规则 ID

-- P1 AI 增强相关
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_confidence INT DEFAULT 0;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_suggested_action VARCHAR(50);
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS ai_business_scene VARCHAR(100);

-- P1 出纳确认相关（review_status 独立于 match_status）
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

-- 约束
ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_review_status
  CHECK (review_status IN ('PENDING', 'CONFIRMED', 'RECLASSIFIED'));
ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_direction
  CHECK (direction IN ('in', 'out') OR direction IS NULL);

-- 索引（高频查询）
CREATE INDEX IF NOT EXISTS idx_stmt_review_status ON t_bank_statement(review_status);
CREATE INDEX IF NOT EXISTS idx_stmt_classification ON t_bank_statement(classification);
CREATE INDEX IF NOT EXISTS idx_stmt_batch_id ON t_bank_statement(batch_id);
```

**与 V5 已有字段的语义关系**：
- `tx_type` (V5, 4 值) **保留不动**——继续服务对账流程（autoMatch/confirmMatch）
- `direction` (P1, 2 值) — 分类引擎入口参数，与 `tx_type` 关系：`direction=in` ↔ `tx_type IN ('INCOME','TRANSFER_IN')`；`direction=out` ↔ `tx_type IN ('EXPENSE','TRANSFER_OUT')`
- `match_status` (V5, 4 值) — **对账状态**（与银行日记账的匹配）
- `review_status` (P1, 3 值) — **出纳确认状态**（PENDING/CONFIRMED/RECLASSIFIED）—— **与 match_status 独立**

### 2. 分类规则引擎（Java 后端）

#### 2.1 模型与数据结构

```java
// ClassificationRuleEntity — 对应 t_classification_rule 表（MyBatis-Plus）
// RuleMatchResult — 匹配结果 DTO
public class RuleMatchResult {
    private boolean matched;
    private Long ruleId;
    private String ruleName;
    private String classification;
    private String debitSubjectCode;   // 可选，用于自动生成凭证
    private String creditSubjectCode;
}
```

#### 2.2 规则匹配逻辑

参考 Go 版 `MatchRule()` 实现，完全移植到 Java：

```
MatchRule(rule, description, counterparty, direction):
  1. 方向过滤: rule.direction 非空且不等于 direction → 不匹配
  2. 根据 match_field 确定匹配文本 (description / counterparty)
  3. 根据 rule_type:
     - keyword: containsString(文本, pattern) — 大小写不敏感包含
     - keyword_regex: pattern 按 | 分割，任一子关键词包含即匹配
     - counterparty_match: 等价于 keyword，但在 counterparty 字段上执行
  4. 全部条件满足 → 匹配成功
```

#### 2.3 规则引擎服务

```java
ClassificationRuleService:
  - MatchTransaction(tenantId, description, counterparty, direction) → RuleMatchResult
    规则按 priority ASC 排序，first-match-wins，命中即停
  
  - ListActiveRules(tenantId) → List<ClassificationRuleEntity>
  - CreateRule / UpdateRule / DeleteRule / ReorderPriority (CRUD)
  - SeedRules(tenantId) — 初始化 8 条种子规则
```

#### 2.4 兜底启发式分类

```java
fallbackClassify(description, counterparty, direction, amount) → String
// 关键词分组按优先级 (1 最高, 9 关键词兜底, 10 方向兜底):
//   1  bank_fee         out    手续费/工本费/年费/账户管理费
//   2  interest_income  in     利息/结息/存款利息
//   3  tax_payment      out    税/税务/缴税/税金/增值税/所得税/...
//   4  social_security  out    社保/公积金/养老/医疗/...
//   5  insurance_fee    out    保险/保费/投保
//   6  salary_payment   out    工资/薪资/薪酬/劳务费/奖金/津贴
//   7  business_receipt in     货款/收款/销售/回款/客户/应收/收入
//   8  business_payment out    货款/付款/采购/支付/供应商/应付/支出
//   9  internal_transfer (不限) 转账/转存/调拨/上划/下拨/内部
//  10  方向兜底 (低置信度)    in→business_receipt, out→business_payment
//  兜底 (无方向)            → "pending" (归入待处理池, 等人工确认)
```

**置信度标记** (写入 `t_bank_statement.ai_confidence` 0-100):
- 规则命中 → 90 (高置信度)
- 关键词兜底命中 (priority 1-9) → 75 (中高置信度)
- 方向兜底 (priority 10) → 50 (低置信度, **待人工确认**)
- pending (无方向) → 50 (低置信度, **待人工确认**)

低置信度 (ai_confidence < 60) 的流水在出纳工作台优先展示, 需人工复核或重新分类.

### 3. Excel/CSV 导入流程

#### 3.1 列名解析器

```java
class ColumnResolver {
    // Phase 1: 精确匹配 — headerMap exact lookup
    // Phase 2: 子串匹配 — 列名 contains 任一候选名
    // Phase 3: 级联回退 — 主列→次列→三级列
    
    // 日期列: "交易日期"/"记账日期"/"日期"/"发生日期"/date
    // 摘要列: "摘要"/"备注"/"附言"/"用途"/description  
    // 收入列: "收入金额"/"贷方金额"/"收入"/income/credit
    // 支出列: "支出金额"/"借方金额"/"支出"/expense/debit
    // 金额列: "金额"/"发生金额"/"交易金额"/amount (当收入支出无单独列时)
    // 对方户名: 四层 cascade: 付款人名称→收款人名称→对方户名→付款人/收款人
}
```

#### 3.2 导入服务

```java
BankStatementImportService:
  - preview(file) → ImportPreviewResult { columns, sampleRows, totalRows, headerRow }
  - importFromExcel(file, bankAccountId) → ImportResult { total, success, failed, duplicate }
  - importFromCsv(content, bankAccountId) → ImportResult
  
  核心流程:
  1. openExcel → findHeaderRow (≥5非空单元格)
  2. buildColumnIndex (两阶段列名映射)
  3. 逐行解析:
     a. parseDate (多格式: 20240101 / 2024-01-01 / 2024/01/01 / 2024年01月02日)
     b. parseAmount (income/expense/amount 三列互斥逻辑)
     c. parseCounterparty (四层 cascade + 摘要提取)
     d. determineDirection (debit>0→in, credit>0→out)
     e. 自动分类: MatchTransaction → fallbackClassify
     f. 记录 AI 字段 (rule_id, classification, ai_confidence)
  4. 批量写入 t_bank_statement (去重检测)
```

### 4. 种子规则数据（8 条）

直接复制 Go 版已验证的规则集，导入时自动创建：

| 规则名称 | pattern | match_field | direction | classification | priority |
|---------|---------|-------------|-----------|---------------|---------|
| 银行手续费 | 手续费\|工本费\|年费\|账户管理费 | description | out | bank_fee | 1 |
| 利息收入 | 利息\|结息\|存款利息 | description | in | interest_income | 2 |
| 业务收款 | 货款 | description | in | business_receipt | 3 |
| 业务付款 | 货款 | description | out | business_payment | 4 |
| 内部转账 | 转账\|转存\|调拨\|上划\|下拨 | description | (空) | internal_transfer | 5 |
| 税务缴费 | 税\|税务\|缴税\|税金\|税款\|增值税\|... | description | out | tax_payment | 6 |
| 社保缴费 | 社保\|公积金\|养老\|医疗\|失业\|... | description | out | social_security | 7 |
| 保险费用 | 保险\|保费\|投保\|财产险\|... | description | out | insurance_fee | 8 |

### 5. 前端界面

#### 5.1 导入向导对话框

步骤一：文件上传（拖拽/点击，.xlsx/.xls/.csv）
步骤二：预览确认（表格展示解析结果，日期/摘要/金额/对方户名/分类）
步骤三：确认导入（显示导入结果摘要）

#### 5.2 流水列表页增强

- 列新增：分类标签（彩色 badge）、匹配状态、置信度
- 筛选条件：分类、匹配状态、日期范围
- 操作列：确认、重分类、生成单据

#### 5.3 规则管理页

- 表格展示所有规则（名称、模式、方向、分类、启停状态、优先级）
- 编辑弹窗（名称、模式、匹配字段、方向、分类）
- 拖拽排序
- 启用/禁用开关

### 6. AI 微服务接入（利用已有基础设施）

#### 6.1 匹配流程（已有 + 增强）

```
规则引擎匹配失败或置信度<60
  ↓
AiTaskService.createAndDispatch("MATCH", "bank_txn", txnId, inputData)
  ↓ RabbitMQ → Python match/score API
  ↓
返回相似度评分 + 候选匹配项
  ↓
AiResultListener 接收结果 → 更新 ai_confidence + ai_suggested_action
```

#### 6.2 异常检测（已有）

```
凭证/流水提交审核时
  ↓
AiTaskService.createAndDispatch("ANOMALY", "voucher", voucherId, inputData)
  ↓ RabbitMQ → Python anomaly/voucher API
  ↓
返回异常标签 (LOW/MEDIUM/HIGH/CRITICAL)
  ↓
存入 t_ai_anomaly_tag → 前端警告展示
```

## 接口设计

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bank-statements/preview` | 预览 Excel 内容（两阶段列名映射 + 预校验） |
| POST | `/api/v1/bank-statements/import` | 导入银行流水（触发分类引擎） |
| GET | `/api/v1/bank-statements/page` | 分页查询流水（已有，增强 review_status 过滤） |
| POST | `/api/v1/bank-statements/{id}/classify` | 单笔重分类 |
| POST | `/api/v1/bank-statements/classify-all` | 批量重分类（待处理流水） |
| POST | `/api/v1/bank-statements/{id}/review` | **P1 新增** 出纳确认流水（PENDING→CONFIRMED，触发业务单据/凭证生成） |
| POST | `/api/v1/bank-statements/batch-review` | **P1 新增** 批量出纳确认 |
| POST | `/api/v1/bank-statements/batch-confirm` | 批量确认（兼容旧版，等价于 batch-review） |
| GET | `/api/v1/classification-rules` | 规则列表 |
| POST | `/api/v1/classification-rules` | 创建规则 |
| PUT | `/api/v1/classification-rules/{id}` | 更新规则 |
| DELETE | `/api/v1/classification-rules/{id}` | 删除规则 |
| POST | `/api/v1/classification-rules/reorder` | 拖拽排序 |
| POST | `/api/v1/classification-rules/seed` | 初始化种子规则 |
| POST | `/api/v1/classification-rules/match` | 单笔测试匹配 |

## 验收标准

1. ✅ 上传 Excel 银行流水，自动识别列名并正确解析日期/金额/摘要/对方
2. ✅ 导入后每条流水自动分类（规则引擎或兜底），`classification` 字段非空
3. ✅ 规则管理页 CRUD + 排序 + 启停全部可用
4. ✅ 前端展示分类标签 + 置信度（如有）
5. ✅ 待处理流水（兜底 pending）可在前端批量重分类
6. ✅ 种子规则自动初始化（新租户首次使用时）
7. ✅ 无规则时不影响导入，全部归入待处理
8. ✅ 与现有 AI 服务（RabbitMQ 通道）可正常通信
9. ✅ **导入阶段不自动创建任何业务单据/凭证**；所有单据（收款单/付款单/银行转账单）与凭证（bank_fee/interest_income/tax_payment/social_security/insurance_fee）均在出纳在 FR-BANK-07 工作台确认流水后，根据分类触发创建/生成
10. ✅ **salary_payment 分类**（兜底第 6 级识别）在出纳确认时生成付款单（DRAFT 状态，`FROM_BANK_TXN` 来源标记，关联员工档案）

## OpenCode 执行指令

**目标**：在 huicai/Java 项目中实现银行流水导入与智能分类引擎

**上下文**：
- 项目路径：`/data/disk/disk/huicai/`
- 参考实现：`/root/data/disk/huihua-finance/`（Go 版，`internal/service/bank_transaction_service.go` + `classification_rule_service.go`）
- AI 服务已在 `ai-service/` 目录就绪（Python FastAPI）
- 现有 `BankStatementController` + `BankStatementServiceImpl` 需增强

**约束**：
- 后端 Java 代码在 `backend/` 目录
- 新建 module 放在 `com.huicai.module.finance.service.impl` 下
- 数据库迁移脚本放在 `backend/src/main/resources/db/` 目录
- Excel 解析用 EasyExcel（alibaba）或 POI
- 尊重已有的 `R<T>` 响应体规范
- 不影响现有模块（凭证/单据/税务/报表等）

**验收标准**：见上方 8 条验收标准