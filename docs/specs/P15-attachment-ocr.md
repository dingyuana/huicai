# P15 SPEC — 票据/附件管理 (上传 + 模拟 OCR)

> **编号**：HUICAI-SPC-015（storage 模块基础已有, P15-1 加 OCR 规则解析）
> 目标：附件上传 → 模拟 OCR → 结构化 JSON → 关联到单据
> 工期：1 批

---

> **关联需求**: REQ-2026-046

## 1. 输入契约
→ 见本文 [现状摸底 / 附件上传与OCR参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [票据状态机图 / 状态常量 / 状态转换方法] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_attachment` 表 | ✅ 实体 + 字段 (bizType/bizId/fileName/ocrData/vector) |
| `AttachmentService` | ✅ upload/list/delete/presignedUrl, **缺 runOcr** |
| `MinioService` | ✅ 上传/下载/预签名 |
| `AttachmentController` | ✅ 4 端点 |

**关键观察**: `ocrData` 字段已有, 但始终为 null — **OCR 未实现**

---

## 2. P15-1 任务

### 2.1 runOcr 方法 (规则解析模拟)

不接外部 OCR API（成本高+需要凭证），用**规则解析**模拟：
- 输入: attachmentId + 业务提示（hint 含 expectedType, bizType, bizId）
- 输出: 结构化 JSON 字符串（按 bizType 不同字段不同）

```java
String runOcr(Long attachmentId, Map<String, String> hint);
```

**业务类型 → 字段映射**:
- `bank_statement` → {txDate, amount, summary, counterAccount}
- `sales_invoice` → {invoiceNo, invoiceDate, amount, taxAmount, customerName}
- `input_invoice` → {invoiceNo, invoiceDate, amount, taxAmount, vendorName}
- `expense_reimbursement` → {reimbNo, employeeName, amount, expenseType}

### 2.2 单测 (3 个)

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `runOcr_attachment_不存在_throw` | runOcr |
| 2 | `runOcr_bank_statement_返回4字段JSON` | runOcr |
| 3 | `runOcr_保存ocrData到t_attachment` | runOcr + 持久化 |

### 2.3 端点

```java
POST /api/v1/attachments/{id}/ocr
Body: {"bizType": "sales_invoice", "hint": {...}}
```

---

## 3. 关键设计

### 3.1 模拟实现

`runOcr` 内部用 HashMap 模拟字段填充（实际生产可换 Python OCR 微服务）：

```java
String runOcr(Long id, Map<String, String> hint) {
    AttachmentEntity e = mapper.selectById(id);
    if (e == null) throw new BusinessException("附件不存在");
    String bizType = hint.getOrDefault("bizType", e.getBizType());
    String json = mockExtract(bizType, hint);
    e.setOcrData(json);
    mapper.updateById(e);
    return json;
}
```

### 3.2 后续可扩展 (P16+)

- 接入 Python OCR 微服务 (PaddleOCR / 百度 OCR)
- 写入向量库 (pgvector) — 已有 `vector` 字段
- AI 二次校验 (LLM 识别)

---

## 4. 不在 P15 范围

- 外部 OCR API 接入
- 异步任务队列 (RabbitMQ) — 当前同步
- 向量检索 / 语义匹配
- 文件预览 (PDF/图片)
- 多文件批量 OCR

---

## 5. 测试验收

**目标**: 292 → 295 (+3 测试, 0 fail, 0 error)

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P15-1 | 附件上传成功 | `upload(file) → attachment.id != null` |
| AT-P15-2 | OCR识别返回结构化JSON | `ocrRecognize(file) → result contains 'invoice_no'` |
| AT-P15-3 | 附件关联到业务实体 | `attach(bizType, bizId, fileId) → link exists` |
