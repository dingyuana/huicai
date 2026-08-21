# P36.1 SPEC — 红冲发票账务处理（红字凭证 + 进项税额转出）

> **版本**：V1.0 | **日期**：2026-08-20 | **作者**：Hermes
> **状态**：📝 待审核
> **编号**：HUICAI-SPC-036.1
> **关联PRD**：../prd/发票税务管理-PRD-V1.0.md、../prd/凭证模板引擎-PRD-V1.0.md
> **关联SPEC**：P36-invoice-reverse-chain.md、P40-input-invoice-state-machine.md
> **test_ref**：InputInvoiceRedFlushVoucherTest（新建）、OutputInvoiceRedFlushVoucherTest（新建）

---

## 业务背景

红冲发票（红字发票）**必须做凭证**。当前 `reverseInvoice()` 只创建红字发票（金额取反，PENDING_CONFIRM），不生成红字凭证；红字发票 confirm 后走普通制证路径，生成"负数金额"凭证而非"反向分录"，且**缺失进项税额转出、红冲原因枚举、已抵扣判断**三项核心账务处理。

依据财税规则，原蓝字发票的账务状态决定红冲处理方式：

| 情况 | 原发票状态 | 处理方式 |
|:----:|-----------|---------|
| 一 | 已入账 + 已抵扣（或已计入成本费用） | 冲销原分录 + **进项税额转出** |
| 二 | 已入账 + 未抵扣（免税项目等） | 无需额外分录，红字发票留存备查 |
| 三 | 未入账 | 蓝字+红字合并按正确净额入账 |

---

## 1. 输入契约

### 1.1 红冲接口（扩展 reverseInvoice）

```java
// InputInvoiceStateMachineService / OutputInvoiceStateMachineService
Long reverseInvoice(Long invoiceId, Long userId, String reason, String reverseReason);
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| invoiceId | Long | 是 | 原蓝字发票 ID |
| userId | Long | 是 | 操作人 |
| reason | String | 是 | 红冲备注（自由文本） |
| reverseReason | String | 是 | 红冲原因枚举：`INVOICE_ERROR`(开票有误) / `RETURN`(退货) / `DISCOUNT`(折让) / `OTHER`(其他) |

> 兼容：不带 reverseReason 的旧调用默认 `OTHER`，不阻断升级。

### 1.2 红字发票实体新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| reverseReason | String | 红冲原因枚举 |
| originalVoucherId | Long | 原蓝字发票对应凭证 ID（情况一/二判断依据） |
| originalCertificationStatus | String | 原发票抵扣状态快照（CERTIFIED/UNCERTIFIED） |

### 1.3 需要的基础科目

| 科目编码 | 名称 | 用途 |
|---------|------|------|
| 1601 | 原材料/库存商品 | 资产成本方（按原凭证分录科目） |
| 2202 | 应付账款 | 贷方 |
| 2221.01 | 应交税费—应交增值税—进项税额 | 借项原值 |
| 2221.04 | **应交税费—应交增值税—进项税额转出** | 新增，转出贷方 |
| 5001/6602 等 | 成本/费用科目 | 按原凭证分录科目 |

---

## 2. 输出契约

### 2.1 情况一：原入账且已抵扣 → 生成红字凭证（含进项转出）

红冲确认后自动生成红字凭证（source=REVERSAL，DRAFT，人工审核）：

| 方向 | 科目 | 金额 |
|:----:|------|------|
| 借 | 1601 原材料（或原成本科目） | -净额（红字） |
| 借 | 2221.01 进项税额 | -税额（红字） |
| 贷 | 2202 应付账款 | -总额（红字） |
| **借** | **1601 原材料（或原成本科目）** | **+税额（进项转出补正）** |
| **贷** | **2221.04 进项税额转出** | **+税额（转出）** |

> 等价简写：进项税额（2221.01）红字冲销 + 进项税额转出（2221.04）贷方转出，两笔同构对冲，净效应为不做进项抵扣。
> 若原成本科目缺失，进项转出借方记入原凭证的存货/费用科目。

### 2.2 情况二：原入账但未抵扣 → 不生成进项转出分录

仅生成冲销凭证（若无记录需求，可只建红字发票不制证，由 `voucherRequired` 开关控制，默认 true 生成冲销凭证）：

| 方向 | 科目 | 金额 |
|:----:|------|------|
| 借 | 1601 原材料 | -净额 |
| 借 | 2221.01 进项税额 | -税额 |
| 贷 | 2202 应付账款 | -总额 |

### 2.3 情况三：原未入账 → 合并净额制证

不额外生成红字凭证；若红字发票 confirm，按净额正常制证（借为负即冲减）。

### 2.4 输出回写

| 对象 | 字段 | 值 |
|------|------|----|
| 红字发票 | status | VOUCHERED + voucherId + voucherNo |
| 原蓝字发票 | isReversed | true（已有） |

---

## 3. 状态流转

```
原发票 CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED
    │  reverseInvoice(invoiceId, userId, reason, reverseReason)
    ▼
创建红字发票（PENDING_CONFIRM，金额取反，reverseReason 记录）
原发票 → REVERSED
    │  人工确认红字发票 confirm()
    ▼
判断 originalCertificationStatus + originalVoucherId
    ├── CERTIFIED && voucher != null → 情况一：红字凭证 + 进项转出分录
    ├── UNCERTIFIED && voucher != null → 情况二：红字冲销凭证（无转出）
    └── voucher == null → 情况三：不额外制证（合并净额）
    ▼
红字发票 → VOUCHERED（回写 voucherId/voucherNo）
```

**非法转换（必须阻断）：**
| 场景 | 阻断 |
|------|------|
| 红冲原因枚举非法 | BusinessException: 未知红冲原因 |
| 原发票已红冲（reversedFrom 非空） | BusinessException: 不可重复红冲（已有） |
| 红字发票重复 confirm | BusinessException: 已生成凭证 |

---

## 4. 异常处理

| 错误码 | 场景 | 消息 |
|:------:|------|------|
| 400-XXX | 红冲原因枚举非法 | 未知红冲原因: {value}，应为 INVOICE_ERROR/RETURN/DISCOUNT/OTHER |
| 400-XXX | 缺少科目 2221.04 | 缺少基础科目配置(2221.04 进项税额转出) |

---

## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| RED-01 | Given 进项发票已抵扣+已过账 When 红冲(return) Then 生成红字凭证：借1601红字+借2221.01红字+贷2202红字+借1601补正+贷2221.04转出 |
| RED-02 | Given 进项发票已抵扣+已过账 When 红冲(开票有误) Then 同上（原因不影响分录结构） |
| RED-03 | Given 进项发票未抵扣+已过账 When 红冲 Then 红字凭证含3条分录（无2221.04转出） |
| RED-04 | Given 进项发票未入账（无voucherId） When 红冲确认 Then 不额外生成红字凭证 |
| RED-05 | Given 红冲原因枚举非法 When 调用红冲 Then 抛异常，不创建红字发票 |
| RED-06 | Given 红字凭证生成后 Then 红字发票 status=VOUCHERED + voucherId 回写 |
| RED-07 | Given 原发票已红冲 When 重复红冲 Then 抛异常 |
| RED-08 | Given 缺少2221.04科目 When 情况一红冲 Then 抛异常(500)，红字发票已建（事务回滚需确认） |

---

## 影响文件

| 文件 | 变更 |
|------|------|
| `InputInvoiceStateMachineServiceImpl.java` | reverseInvoice 签名扩展 + 红字凭证生成 + 情况判断 |
| `OutputInvoiceStateMachineServiceImpl.java` | 同上（销项对称，贷应收/借收入） |
| `InputInvoiceEntity.java` / `OutputInvoiceEntity.java` | +reverseReason/originalVoucherId/originalCertificationStatus（DB 若无列则 V138 迁移） |
| 新增测试 | `InputInvoiceRedFlushVoucherTest` / `OutputInvoiceRedFlushVoucherTest` |

---

## 不做的事（V1.0）

| 不做 | 理由 |
|------|------|
| 自动审核红字凭证 | 人审铁律，红字凭证保持 DRAFT |
| 红字凭证自动过账 | 同上 |
| 税务申报联动（附表二） | 系统外，人工申报 |
| 数电发票入账标识 | 平台能力，不在系统内 |