# P13 SPEC — 税务申报 (进销项归集 + 申报表自动生成)
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效
> **关联PRD**：../prd/发票税务管理-PRD-V1.0.md

> **编号**：HUICAI-SPC-013（骨架完整, 6 归集方法 + calculateVat 已在 main）
> 目标：进项/销项按 period 自动归集 → 增值税应纳税额计算 → 申报表生成 → 申报状态机
> 工期：1 批（骨架已有, 主要补单测 + 申报表详情表）

> **test_ref**：TaxServiceImplTest, TaxApiContractTest
---

> **关联需求**: REQ-2026-030

## 1. 输入契约
→ 见本文 [现状摸底 / 税务申报参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [申报状态机图 / 状态常量 / 状态转换方法] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_input_invoice` (进项发票) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `t_output_invoice` (销项发票) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `t_tax_declaration` (申报主表) | ✅ 实体已建 + Mapper 已建 (P3 阶段) |
| `TaxServiceImpl` | ✅ 18 个接口方法全部实现 (certify + 5 个归集 + calculateVat + 申报 CRUD) |
| `TaxServiceImplTest` | ❌ **零测试覆盖** — 本批补 |

**已实现的核心方法** (main 分支 commit `fa35a0e` 之前已落):
- `certify(Long id, String deductionPeriod)` — 进项发票勾选认证
- `inputSummary(String period)` / `inputByTaxRate(String period)` — 进项按 period 汇总
- `outputSummary(String period)` / `outputByTaxRate(String period)` — 销项按 period 汇总
- `calculateVat(String period)` — 应纳税额 = 销项 - 进项, 含附加税 12%

---

## 2. P13-1 任务 (本批)

### 2.1 补单测 (8 个)

| # | 测试 | 覆盖方法 |
|---|---|---|
| 1 | `certify_未认证_状态变CERTIFIED_扣除金额=税额` | `certify` |
| 2 | `certify_已认证_throw` | `certify` |
| 3 | `certify_发票不存在_throw` | `certify` |
| 4 | `calculateVat_正数_应纳税额=销项-进项+附加税12%` | `calculateVat` |
| 5 | `calculateVat_留抵_应纳税额=0+note提示` | `calculateVat` |
| 6 | `inputSummary_有数据_返回map` | `inputSummary` |
| 7 | `outputByTaxRate_有数据_返回列表` | `outputByTaxRate` |
| 8 | `submitDeclaration_DRAFT_变SUBMITTED` | `submitDeclaration` |

### 2.2 SPEC 文档

本文件就是。

### 2.3 不在 P13 范围

- 电子税务局对接 (需要外部 API, P14 候选)
- OCR 发票识别 (P15 票据管理)
- 多税种支持 (城建税/教育费附加已硬编码 12%, 后续可配置化)
- 申报撤销/作废流程 (P16 候选)

---

## 3. 关键设计

### 3.1 进项发票认证 (certify)

```
UNCERTIFIED ──→ certify(period) ──→ CERTIFIED
                                    deductionAmount = taxAmount
                                    certifiedDate = today
                                    deductionPeriod = period(默认当月)
```

### 3.2 增值税计算 (calculateVat)

```java
outputTax = SUM(t_output_invoice.taxAmount WHERE period=?)
inputTax  = SUM(t_input_invoice.deductionAmount WHERE period=? AND certification_status=CERTIFIED)
payable   = outputTax - inputTax
surcharge = payable * 0.12 (12% = 城建7% + 教育3% + 地方教育2%)
totalPayable = payable + surcharge
```

**留抵场景**: payable < 0 → surcharge=0, totalPayable=0, note="留抵税额(下期继续抵扣)"

### 3.3 申报状态机 (P13 后续)

```
DRAFT ──→ submitDeclaration ──→ SUBMITTED ──→ (人工) ──→ APPROVED
                                            └─→ (人工) ──→ REJECTED → DRAFT
```

当前 `submitDeclaration` 只到 SUBMITTED, 后续 P13-2 补 approve/reject。

---

## 4. 测试验收

**目标**: 281 → 289 (+8 测试, 0 fail, 0 error)
**commit**: `TBD`

---

## 5. P14+ 路线图

| 批 | 内容 | 优先级 |
|---|---|---|
| P14 | 银企对账完善 (BankReconciliation E2E) | 高 |
| P15 | 票据管理 + 附件上传 + OCR | 中 |
| P16 | 预算编制 + 控制 + 分析 | 中 |
| P17 | 报表中心 (三大报表) | 中 |
| P18 | 申报状态机扩展 (approve/reject) | 低 |

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P13-1 | 进项归集按period汇总 | `collectInput(period) → sum(amount) matches DB` |
| AT-P13-2 | 销项归集按period汇总 | `collectOutput(period) → sum(amount) matches DB` |
| AT-P13-3 | 增值税计算正确 | `calculateVat(period) → output_tax - input_tax == payable` |
| AT-P13-4 | 申报表生成 | `generateDeclaration(period) → declaration exists` |

---

## 6. BDD 场景 (Given-When-Then)

### 6.1 进项发票认证

```gherkin
Feature: 进项发票认证
  As a 财务人员
  I want to 认证进项发票
  So that 发票金额可被归集用于抵扣

  Background:
    Given 存在进项发票 I001
      | field             | value             |
      | id                | 1                 |
      | invoiceCode       | 1234567890        |
      | taxAmount         | 1000.00           |
      | certificationStatus | UNCERTIFIED     |
      | deductionAmount   | 0                 |

  Scenario: 认证未认证发票 → 状态变为 CERTIFIED
    Given 进项发票 I001 的认证状态为 UNCERTIFIED
    When 调用 certify(I001.id, "2026-06")
    Then 发票 I001 的认证状态变为 CERTIFIED
    And 发票 I001 的 deductionAmount 等于 taxAmount (1000.00)
    And 发票 I001 的 certifiedDate 等于当天
    And 发票 I001 的 deductionPeriod 等于 "2026-06"

  Scenario: 重复认证已认证发票 → 抛出异常
    Given 进项发票 I001 的认证状态为 CERTIFIED
    When 调用 certify(I001.id, "2026-06")
    Then 抛出 BusinessException 错误码 TAX_ALREADY_CERTIFIED
    And 发票 I001 的认证状态仍为 CERTIFIED

  Scenario: 认证不存在的发票 → 抛出异常
    Given 不存在 id=9999 的进项发票
    When 调用 certify(9999, "2026-06")
    Then 抛出 BusinessException 错误码 INVOICE_NOT_FOUND
```

### 6.2 进项/销项归集汇总

```gherkin
Feature: 进销项归集汇总
  As a 财务人员
  I want to 按期间归集进项和销项发票
  So that 获取期间内的汇总数据用于增值税计算

  Background:
    Given 存在进项发票 I001 (taxAmount=1000.00, period="2026-06", CERTIFIED)
    Given 存在进项发票 I002 (taxAmount=500.00, period="2026-06", CERTIFIED)
    Given 存在进项发票 I003 (taxAmount=300.00, period="2026-06", UNCERTIFIED)
    Given 存在销项发票 O001 (taxAmount=2000.00, period="2026-06")
    Given 存在销项发票 O002 (taxAmount=800.00, period="2026-06")

  Scenario: 进项归集按 period 汇总
    When 调用 inputSummary("2026-06")
    Then 返回进项汇总结果
    And 总金额 = 1500.00 (仅 CERTIFIED 发票)
    And 发票数量 = 2 (I001, I002)

  Scenario: 进项按税率分组
    When 调用 inputByTaxRate("2026-06")
    Then 返回按税率分组列表
    And 包含 13% 分组 (1000.00)
    And 包含 6% 分组 (500.00)

  Scenario: 销项归集按 period 汇总
    When 调用 outputSummary("2026-06")
    Then 返回销项汇总结果
    And 总金额 = 2800.00
    And 发票数量 = 2 (O001, O002)

  Scenario: 销项按税率分组
    When 调用 outputByTaxRate("2026-06")
    Then 返回按税率分组列表
    And 包含 13% 分组 (2000.00)
    And 包含 6% 分组 (800.00)

  Scenario: 无数据的期间返回空
    When 调用 inputSummary("2025-01")
    Then 返回空结果 (total=0, count=0)
```

### 6.3 增值税计算

```gherkin
Feature: 增值税应纳税额计算
  As a 财务人员
  I want to 计算增值税应纳税额
  So that 获得应缴金额并生成申报表

  Background:
    Given 存在进项发票 I001 (deductionAmount=1000.00, period="2026-06", CERTIFIED)
    Given 存在进项发票 I002 (deductionAmount=500.00, period="2026-06", CERTIFIED)
    Given 存在销项发票 O001 (taxAmount=2000.00, period="2026-06")
    Given 存在销项发票 O002 (taxAmount=800.00, period="2026-06")

  Scenario: 正数应纳税额 (销项 > 进项)
    Given 期间 "2026-06" 的销项总额 = 2800.00
    And 期间 "2026-06" 的进项总额 = 1500.00
    When 调用 calculateVat("2026-06")
    Then 应纳税额 = 2800.00 - 1500.00 = 1300.00
    And 附加税 = 1300.00 × 12% = 156.00
    And 应缴总额 = 1300.00 + 156.00 = 1456.00
    And note 为空

  Scenario: 留抵场景 (销项 < 进项)
    Given 期间 "2026-06" 的销项总额 = 1000.00
    And 期间 "2026-06" 的进项总额 = 3000.00
    When 调用 calculateVat("2026-06")
    Then 应纳税额 = 1000.00 - 3000.00 = -2000.00
    And 附加税 = 0
    And 应缴总额 = 0
    And note = "留抵税额(下期继续抵扣)"
```

### 6.4 申报表生成与提交

```gherkin
Feature: 申报表生成与提交
  As a 财务人员
  I want to 生成并提交税务申报表
  So that 完成税务申报流程

  Background:
    Given 存在期间 "2026-06" 的增值税计算结果 (payable=1300.00, surcharge=156.00, totalPayable=1456.00)

  Scenario: 生成申报表 → 状态为 DRAFT
    When 调用 generateDeclaration("2026-06")
    Then 创建申报主表记录
    And 申报状态为 DRAFT
    And 申报期间为 "2026-06"
    And 应缴金额 = 1456.00
    And 应纳税额 = 1300.00
    And 附加税 = 156.00

  Scenario: 提交草稿申报 → 状态变为 SUBMITTED
    Given 存在申报 declaration-D01 (status=DRAFT, period="2026-06")
    When 调用 submitDeclaration(declaration-D01.id)
    Then 申报状态变为 SUBMITTED
    And 提交时间不为空

  Scenario: 提交已提交的申报 → 抛出异常
    Given 存在申报 declaration-D01 (status=SUBMITTED)
    When 调用 submitDeclaration(declaration-D01.id)
    Then 抛出 BusinessException 错误码 TAX_ALREADY_SUBMITTED

  Scenario: 提交不存在的申报 → 抛出异常
    When 调用 submitDeclaration(9999)
    Then 抛出 BusinessException 错误码 DECLARATION_NOT_FOUND

  Scenario: 查看申报详情
    Given 存在申报 declaration-D01 (status=DRAFT, period="2026-06")
    When 调用 getDeclaration(declaration-D01.id)
    Then 返回申报详情
    And 包含进项归集明细
    And 包含销项归集明细
    And 包含增值税计算明细
```

### 6.5 申报状态机完整流转

```gherkin
Feature: 申报状态机
  As a 系统
  I want to 管理申报状态的生命周期
  So that 确保申报流程合规

  Scenario: 完整正向流转
    Given 申报 declaration-D01 状态为 DRAFT
    When 用户提交申报
    Then 状态变为 SUBMITTED
    When 税务人员审核通过
    Then 状态变为 APPROVED

  Scenario: 审核驳回 → 退回草稿
    Given 申报 declaration-D01 状态为 SUBMITTED
    When 税务人员审核驳回
    Then 状态变为 REJECTED
    When 用户重新提交
    Then 状态变为 SUBMITTED

  Scenario: 非法状态转换 → 拒绝
    Given 申报 declaration-D01 状态为 APPROVED
    When 调用 submitDeclaration(declaration-D01.id)
    Then 抛出 BusinessException 错误码 TAX_INVALID_STATE_TRANSITION
```

---

## 7. YAML 契约

### 7.1 枚举定义

```yaml
# ============================================================
# 枚举: 认证状态 & 申报状态
# ============================================================
CertificationStatus:
  type: string
  enum:
    - UNCERTIFIED   # 未认证
    - CERTIFIED     # 已认证
  description: 进项发票认证状态

DeclarationStatus:
  type: string
  enum:
    - DRAFT         # 草稿
    - SUBMITTED     # 已提交
    - APPROVED      # 已通过
    - REJECTED      # 已驳回
  description: 申报状态

# ============================================================
# 状态机转换规则
# ============================================================
StateMachine:
  Certification:
    transitions:
      - from: UNCERTIFIED
        to: CERTIFIED
        action: certify
        guard: 发票必须存在且当前状态为 UNCERTIFIED
    initial: UNCERTIFIED
  Declaration:
    transitions:
      - from: DRAFT
        to: SUBMITTED
        action: submitDeclaration
        guard: 申报必须存在且当前状态为 DRAFT
      - from: SUBMITTED
        to: APPROVED
        action: approveDeclaration
        guard: 人工审批通过 (P13-2)
      - from: SUBMITTED
        to: REJECTED
        action: rejectDeclaration
        guard: 人工审批驳回 (P13-2)
      - from: REJECTED
        to: DRAFT
        action: revertToDraft
        guard: 驳回后自动退回草稿 (P13-2)
    initial: DRAFT
```

### 7.2 错误码定义

```yaml
# ============================================================
# 错误码
# ============================================================
ErrorCode:
  INVOICE_NOT_FOUND:
    code: "TAX-001"
    message: "发票不存在"
    httpStatus: 404
  TAX_ALREADY_CERTIFIED:
    code: "TAX-002"
    message: "发票已认证，不可重复认证"
    httpStatus: 400
  TAX_ALREADY_SUBMITTED:
    code: "TAX-003"
    message: "申报已提交，不可重复提交"
    httpStatus: 400
  DECLARATION_NOT_FOUND:
    code: "TAX-004"
    message: "申报记录不存在"
    httpStatus: 404
  TAX_INVALID_STATE_TRANSITION:
    code: "TAX-005"
    message: "非法的状态转换"
    httpStatus: 400
  TAX_PERIOD_NOT_FOUND:
    code: "TAX-006"
    message: "期间无数据"
    httpStatus: 404
```

### 7.3 API 端点契约

```yaml
# ============================================================
# API: 进项发票认证
# ============================================================
openapi: 3.0.3
info:
  title: 税务申报 API
  version: 1.0.0
  description: 进销项归集 + 增值税计算 + 申报表管理

paths:
  /api/tax/certify:
    post:
      operationId: certifyInvoice
      summary: 认证进项发票
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [id, deductionPeriod]
              properties:
                id:
                  type: integer
                  description: 进项发票 ID
                  example: 1
                deductionPeriod:
                  type: string
                  pattern: '^\d{4}-\d{2}$'
                  description: 抵扣所属期 (YYYY-MM)
                  example: "2026-06"
      responses:
        "200":
          description: 认证成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiResponse'
              example:
                code: 200
                message: "认证成功"
                data:
                  id: 1
                  certificationStatus: "CERTIFIED"
                  deductionAmount: 1000.00
                  certifiedDate: "2026-06-15"
                  deductionPeriod: "2026-06"
        "400":
          description: 业务异常
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiResponse'
              examples:
                alreadyCertified:
                  value:
                    code: 400
                    message: "发票已认证，不可重复认证"
                    errorCode: "TAX-002"
        "404":
          description: 发票不存在
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiResponse'
              example:
                code: 404
                message: "发票不存在"
                errorCode: "TAX-001"

  # ============================================================
  # API: 进项归集汇总
  # ============================================================
  /api/tax/input/summary:
    get:
      operationId: inputSummary
      summary: 进项按期间汇总
      parameters:
        - name: period
          in: query
          required: true
          schema:
            type: string
            pattern: '^\d{4}-\d{2}$'
          description: 所属期 (YYYY-MM)
          example: "2026-06"
      responses:
        "200":
          description: 汇总结果
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/InvoiceSummaryResponse'
              example:
                code: 200
                message: "success"
                data:
                  period: "2026-06"
                  totalAmount: 1500.00
                  invoiceCount: 2
                  certifiedCount: 2
                  uncertifiedCount: 0

  /api/tax/input/by-tax-rate:
    get:
      operationId: inputByTaxRate
      summary: 进项按税率分组
      parameters:
        - name: period
          in: query
          required: true
          schema:
            type: string
            pattern: '^\d{4}-\d{2}$'
      responses:
        "200":
          description: 税率分组列表
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaxRateGroupResponse'
              example:
                code: 200
                message: "success"
                data:
                  period: "2026-06"
                  groups:
                    - taxRate: "13%"
                      amount: 1000.00
                      count: 1
                    - taxRate: "6%"
                      amount: 500.00
                      count: 1

  # ============================================================
  # API: 销项归集汇总
  # ============================================================
  /api/tax/output/summary:
    get:
      operationId: outputSummary
      summary: 销项按期间汇总
      parameters:
        - name: period
          in: query
          required: true
          schema:
            type: string
            pattern: '^\d{4}-\d{2}$'
      responses:
        "200":
          description: 汇总结果
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/InvoiceSummaryResponse'
              example:
                code: 200
                message: "success"
                data:
                  period: "2026-06"
                  totalAmount: 2800.00
                  invoiceCount: 2

  /api/tax/output/by-tax-rate:
    get:
      operationId: outputByTaxRate
      summary: 销项按税率分组
      parameters:
        - name: period
          in: query
          required: true
          schema:
            type: string
            pattern: '^\d{4}-\d{2}$'
      responses:
        "200":
          description: 税率分组列表
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaxRateGroupResponse'

  # ============================================================
  # API: 增值税计算
  # ============================================================
  /api/tax/calculate-vat:
    get:
      operationId: calculateVat
      summary: 计算增值税应纳税额
      parameters:
        - name: period
          in: query
          required: true
          schema:
            type: string
            pattern: '^\d{4}-\d{2}$'
      responses:
        "200":
          description: 增值税计算结果
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/VatCalculationResponse'
              examples:
                positive:
                  value:
                    code: 200
                    message: "success"
                    data:
                      period: "2026-06"
                      outputTax: 2800.00
                      inputTax: 1500.00
                      payable: 1300.00
                      surcharge: 156.00
                      surchargeRate: "12%"
                      totalPayable: 1456.00
                      note: null
                carryForward:
                  value:
                    code: 200
                    message: "success"
                    data:
                      period: "2026-06"
                      outputTax: 1000.00
                      inputTax: 3000.00
                      payable: -2000.00
                      surcharge: 0
                      surchargeRate: "12%"
                      totalPayable: 0
                      note: "留抵税额(下期继续抵扣)"

  # ============================================================
  # API: 申报表生成
  # ============================================================
  /api/tax/declaration/generate:
    post:
      operationId: generateDeclaration
      summary: 生成申报表
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [period]
              properties:
                period:
                  type: string
                  pattern: '^\d{4}-\d{2}$'
                  example: "2026-06"
      responses:
        "200":
          description: 申报表生成成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DeclarationResponse'
              example:
                code: 200
                message: "申报表生成成功"
                data:
                  id: 1
                  period: "2026-06"
                  status: "DRAFT"
                  payable: 1300.00
                  surcharge: 156.00
                  totalPayable: 1456.00
                  outputTax: 2800.00
                  inputTax: 1500.00
                  createdAt: "2026-06-15T10:00:00"

  # ============================================================
  # API: 申报提交
  # ============================================================
  /api/tax/declaration/submit:
    post:
      operationId: submitDeclaration
      summary: 提交申报 (DRAFT → SUBMITTED)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [id]
              properties:
                id:
                  type: integer
                  example: 1
      responses:
        "200":
          description: 提交成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DeclarationResponse'
              example:
                code: 200
                message: "提交成功"
                data:
                  id: 1
                  status: "SUBMITTED"
                  submittedAt: "2026-06-15T10:30:00"
        "400":
          description: 状态异常
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiResponse'
              example:
                code: 400
                message: "申报已提交，不可重复提交"
                errorCode: "TAX-003"

  # ============================================================
  # API: 申报详情查询
  # ============================================================
  /api/tax/declaration/{id}:
    get:
      operationId: getDeclaration
      summary: 查询申报详情
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        "200":
          description: 申报详情
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DeclarationDetailResponse'
        "404":
          description: 申报不存在

components:
  schemas:
    # ============================================================
    # 通用响应
    # ============================================================
    ApiResponse:
      type: object
      properties:
        code:
          type: integer
          example: 200
        message:
          type: string
        errorCode:
          type: string
          nullable: true
        data:
          type: object
          nullable: true

    # ============================================================
    # 发票汇总
    # ============================================================
    InvoiceSummaryResponse:
      type: object
      properties:
        code:
          type: integer
        message:
          type: string
        data:
          type: object
          properties:
            period:
              type: string
            totalAmount:
              type: number
              format: double
            invoiceCount:
              type: integer

    # ============================================================
    # 税率分组
    # ============================================================
    TaxRateGroupResponse:
      type: object
      properties:
        code:
          type: integer
        message:
          type: string
        data:
          type: object
          properties:
            period:
              type: string
            groups:
              type: array
              items:
                type: object
                properties:
                  taxRate:
                    type: string
                    example: "13%"
                  amount:
                    type: number
                    format: double
                  count:
                    type: integer

    # ============================================================
    # 增值税计算结果
    # ============================================================
    VatCalculationResponse:
      type: object
      properties:
        code:
          type: integer
        message:
          type: string
        data:
          type: object
          properties:
            period:
              type: string
            outputTax:
              type: number
              format: double
            inputTax:
              type: number
              format: double
            payable:
              type: number
              format: double
            surcharge:
              type: number
              format: double
            surchargeRate:
              type: string
              example: "12%"
            totalPayable:
              type: number
              format: double
            note:
              type: string
              nullable: true

    # ============================================================
    # 申报表
    # ============================================================
    DeclarationResponse:
      type: object
      properties:
        code:
          type: integer
        message:
          type: string
        data:
          type: object
          properties:
            id:
              type: integer
            period:
              type: string
            status:
              $ref: '#/components/schemas/DeclarationStatus'
            outputTax:
              type: number
              format: double
            inputTax:
              type: number
              format: double
            payable:
              type: number
              format: double
            surcharge:
              type: number
              format: double
            totalPayable:
              type: number
              format: double
            createdAt:
              type: string
              format: date-time
            submittedAt:
              type: string
              format: date-time
              nullable: true

    DeclarationDetailResponse:
      type: object
      properties:
        code:
          type: integer
        message:
          type: string
        data:
          type: object
          properties:
            declaration:
              $ref: '#/components/schemas/DeclarationResponse'
            inputInvoices:
              type: array
              items:
                type: object
                properties:
                  id:
                    type: integer
                  invoiceCode:
                    type: string
                  taxAmount:
                    type: number
                  certificationStatus:
                    $ref: '#/components/schemas/CertificationStatus'
            outputInvoices:
              type: array
              items:
                type: object
                properties:
                  id:
                    type: integer
                  invoiceCode:
                    type: string
                  taxAmount:
                    type: number
```
