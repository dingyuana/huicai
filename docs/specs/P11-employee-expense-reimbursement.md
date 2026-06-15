# P11 SPEC — 个人（员工）报销单 端到端自动接入

> 状态：开发方案（待老丁审核 → 委 Hermes 直写）
> 目标：银行流水→识别"员工"→关联报销单→生成付款单→生成凭证
> 工期：4 批工单（每批独立 commit、可回滚）

---

## 0. 业务背景

你最初的 5 分支图中，唯一**未实现的分支**：

```
对方是个人（员工）→ 关联报销单 → 生成付款单 → 生成凭证
```

现状：银行流水走完 P1 分类后，`salary_payment`（薪资）和 `expense`（费用）都是"不匹配员工"的——只能走 A 类直接制证或掉进待处理池。

P11 补上：**当银行流水的对方是个人名时，系统应自动匹配员工、创建报销单、生成凭证**。

---

## 1. 工单分批

| 批 | 内容 | 新增文件 | 测试+ |
|---|---|---|---|
| **P11-1** | 员工档案（CRUD + DB 迁移） | `EmployeeEntity`/`Mapper`/`Service`/`Controller`、Flyway V33 | ~10 |
| **P11-2** | 费用报销单（CRUD + 状态机 + DB 迁移） | `ExpenseReimbursementEntity`/`Mapper`/`Service`/`Controller`、Flyway V34 | ~15 |
| **P11-3** | 银行流水分类→员工匹配 | 改 `FallbackHeuristicService` / `AutoGenerationService` + 新增匹配逻辑 | ~5 |
| **P11-4** | 报销单→付款单→凭证自动生成 | 改 `AutoGenerationService` 串联 P10-3 路径 | ~5 |

---

## 2. P11-1：员工档案

### 2.1 数据模型（Flyway V33：`t_employee`）

```sql
CREATE TABLE t_employee (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(50),              -- 工号
    name          VARCHAR(100) NOT NULL,    -- 姓名
    dept_id       BIGINT,                   -- 部门ID（关联 system/dept）
    phone         VARCHAR(20),
    email         VARCHAR(100),
    bank_name     VARCHAR(100),             -- 工资卡银行
    bank_account  VARCHAR(50),              -- 工资卡号
    id_card       VARCHAR(18),             -- 身份证号
    is_active     BOOLEAN DEFAULT true,
    remark        VARCHAR(500),
    created_at    TIMESTAMP DEFAULT now(),
    updated_at    TIMESTAMP DEFAULT now(),
    deleted       INTEGER DEFAULT 0
);
CREATE INDEX idx_t_employee_name ON t_employee(name);
CREATE INDEX idx_t_employee_code ON t_employee(code);
```

### 2.2 API

| 方法 | 路由 | 说明 |
|------|------|------|
| GET | `/api/v1/employees/page` | 分页查询 |
| GET | `/api/v1/employees/{id}` | 详情 |
| POST | `/api/v1/employees` | 新增员工 |
| PUT | `/api/v1/employees/{id}` | 修改 |
| DELETE | `/api/v1/employees/{id}` | 删除（逻辑） |

### 2.3 关键设计

- `EmployeeService` 提供 `findByName(name) → EmployeeEntity` 方法——供 P11-3 匹配使用
- 自动注入系统已有 `MyMetaObjectHandler` 处理 createAt/updatedAt
- 沿用 P9 单测模板（Mockito + @InjectMocks）

---

## 3. P11-2：费用报销单

### 3.1 数据模型（Flyway V34：`t_expense_reimbursement`）

```sql
CREATE TABLE t_expense_reimbursement (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reimb_no        VARCHAR(50) NOT NULL,     -- 报销单号（REIMB-YYYYMM-XXXX）
    employee_id     BIGINT NOT NULL,          -- 员工ID
    dept_id         BIGINT,                   -- 部门ID
    expense_type    VARCHAR(50) NOT NULL,     -- 费用类型: TRAVEL/OFFICE/ENTERTAIN/TRANSPORT/COMMUNICATION/OTHER
    amount          NUMERIC(18,2) NOT NULL,    -- 报销金额
    summary         VARCHAR(500),             -- 报销说明
    status          VARCHAR(20) DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/VOUCHERED
    doc_id          BIGINT,                   -- 关联 t_business_doc（付款单）
    voucher_id      BIGINT,                   -- 关联凭证
    bank_stmt_id    BIGINT,                   -- 关联银行流水
    attachment_ids  TEXT,                     -- 附件列表
    submitted_at    TIMESTAMP,
    approved_at     TIMESTAMP,
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now(),
    deleted         INTEGER DEFAULT 0
);
CREATE INDEX idx_er_employee ON t_expense_reimbursement(employee_id);
CREATE INDEX idx_er_status ON t_expense_reimbursement(status);
CREATE INDEX idx_er_stmt ON t_expense_reimbursement(bank_stmt_id);
```

### 3.2 状态机

```text
              提交        审核通过        生成凭证
DRAFT ─────→ SUBMITTED ──→ APPROVED ──────→ VOUCHERED
                  │            │
                  │            └── → REJECTED
                  └── → 撤回 → DRAFT
```

### 3.3 API

| 方法 | 路由 | 说明 |
|------|------|------|
| GET | `/api/v1/expense-reimbursements/page` | 分页（支持按员工/状态/日期筛选） |
| GET | `/api/v1/expense-reimbursements/{id}` | 详情 |
| POST | `/api/v1/expense-reimbursements` | 创建草稿 |
| PUT | `/api/v1/expense-reimbursements/{id}` | 修改草稿 |
| POST | `/api/v1/expense-reimbursements/{id}/submit` | 提交审核 |
| POST | `/api/v1/expense-reimbursements/{id}/approve` | 审核通过 |
| POST | `/api/v1/expense-reimbursements/{id}/reject` | 驳回 |
| POST | `/api/v1/expense-reimbursements/{id}/generate-voucher` | 生成凭证 |

### 3.4 凭证模板（硬编码）

| 费用类型 | 借方科目 | 贷方科目 |
|---------|---------|---------|
| TRAVEL | 5602.03 差旅费 | 1002 银行存款 |
| OFFICE | 5602.04 办公费 | 1002 |
| ENTERTAIN | 5602.05 业务招待费 | 1002 |
| TRANSPORT | 5602.06 交通费（复用） | 1002 |
| COMMUNICATION | 5602.07 通讯费 | 1002 |
| OTHER | 5602.99 其他费用 | 1002 |

---

## 4. P11-3：银行流水→员工匹配

### 4.1 匹配逻辑（`BankStatementServiceImpl` 新增/改）

在 `FallbackHeuristicService` 兜底中增加**员工名称匹配**环节：

```
1. 兜底引擎在匹配银行流水时，若对手方名称（counterAccount）不为空
2. 先用 tryMatchEmployeeName(stmt.getCounterAccount())
3. 逻辑: 对 counterName 做全名 → 短名匹配（去除空格、特殊字符）→ 查 t_employee
4. 若命中 → 分类设为 "employee_expense"（新分类），路由到 B 类
5. 否则 → 原样走现有兜底
```

### 4.2 分类路由新增

```java
case "employee_expense" -> "B";  // 走 B 类生单路径
```

### 4.3 `AutoGenerationService` 新增

在 `generateDocThenVoucher` 的 `createReceivableOrPayableFromBankDoc` 之前，增加：

```java
if ("employee_expense".equals(classification)) {
    // 查员工
    EmployeeEntity emp = employeeService.findByName(counterName);
    if (emp == null) return;
    // 自动创建报销单草稿
    ExpenseReimbursementEntity reimb = createExpenseReimbursement(stmt, emp);
    // 后续走报销单审核流程（停在 DRAFT 等人提交 → 审批）
    doc.setDocType("EXPENSE_REIMB");
}
```

**注意**：这里不自动审批——**停在 DRAFT 状态**，等人去"报销单管理"页面提交→审批才生成凭证。这是老丁"人是唯一审核主体"硬约束。

---

## 5. P11-4：报销单→付款单→凭证自动生成

### 5.1 当报销单被审批通过（`/approve`）时

自动调用 `AutoGenerationService` 或 `VoucherAutoGenerateService` 生成凭证：

```
1. status APPROVED → 按 expense_type 匹配科目
2. 调 voucherTemplateService.matchByClassification("employee_expense")
3. 降级: 硬编码映射（见 §3.4 模板）
4. 生成凭证草稿 (docstatus=0)
5. 更新报销单 status=VOUCHERED, voucher_id=凭证ID
6. 更新关联银行流水 generated_voucher_id
```

### 5.2 关键

- **不做 P10-4 式自动核销**——员工报销不是往来，直接生成凭证
- 凭证停在人审（docstatus=0），核准后才能过账

---

## 6. 测试与验收

| 批 | 新增测试 | 桩描述 |
|---|---|---|
| P11-1 | 10 | Employee CRUD + findByName |
| P11-2 | 15 | 报销单 CRUD + 状态机（DRAFT→SUBMITTED→APPROVED→VOUCHERED / REJECTED） |
| P11-3 | 5 | 员工名称匹配 + 兜底分类 + 自动创建报销单 DRAFT |
| P11-4 | 5 | 报销单审批后自动生成凭证 |

**验收数字**：235 → 275（+40 测试），0 fail，0 error。

---

## 7. 不在 P11 范围

- **员工→报销单→银行付款核销**（员工已领钱，企业已付→不需要核销）
- **多级审批流**（报销单仅单级审批：提交→审批通过/驳回）
- **OCR 识别发票附件**（现有 `t_attachment` 已备案，但不做自动解析）
- **前端页面**（P11 只做后端 API，前端独立排期）

---

## 8. 决策点

一、**工单顺序**（1→2→3→4）接受？
二、**费用类型只列 6 种**（差旅/办公/招待/交通/通讯/其他）够吗？
三、**报销单审批后自动生成凭证停在 docstatus=0**（不过账）接受？
四、**先开工 P11-1**（最高 ROI）？

等你逐项回。