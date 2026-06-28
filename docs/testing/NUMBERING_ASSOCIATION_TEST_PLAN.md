# 编号关联体系测试流程

**版本**: v1.0  
**日期**: 2026-06-28  
**目标**: 验证编号关联体系全链路正确性

---

## 一、测试分层策略

```
┌─────────────────────────────────────────────────────┐
│ L0: 单元测试 (FastTest)                              │
│ - VO/DTO 序列化                                    │
│ - 工具类逻辑                                       │
│ - Mock Service 层                                  │
├─────────────────────────────────────────────────────┤
│ L1: Controller 层测试 (MockMvc)                      │
│ - 接口参数绑定                                     │
│ - 响应结构验证                                     │
│ - Mock Service 依赖                                │
├─────────────────────────────────────────────────────┤
│ L2: 真实 DB Mapper 测试 (@SlowTest + Testcontainers) │
│ - 编号字段 CRUD                                    │
│ - 索引验证                                         │
│ - 关联查询                                         │
├─────────────────────────────────────────────────────┤
│ L3: 端到端业务链路测试                               │
│ - 销售链路: 发票→应收单→凭证                         │
│ - 采购链路: 发票→应付单→凭证                         │
│ - 核销链路: 核销单→凭证                            │
└─────────────────────────────────────────────────────┘
```

---

## 二、测试用例清单

### T1: 实体字段完整性测试 (L2)

**目的**: 验证 V64 Migration 所有新增字段存在且可读写

| 序号 | 测试点 | 预期结果 |
|------|--------|----------|
| T1-1 | InputInvoiceEntity.docNo 字段 | 可设置/读取 |
| T1-2 | InputInvoiceEntity.voucherNo 字段 | 可设置/读取 |
| T1-3 | ReceivableEntity.docNo/voucherNo/invoiceNo | 均可读写 |
| T1-4 | PayableEntity.docNo/voucherNo/invoiceNo | 均可读写 |
| T1-5 | VoucherEntity.sourceDocId/No/Type | 均可读写 |
| T1-6 | BusinessDocEntity.voucherNo | 可读写 |
| T1-7 | ArapSettlementEntity.voucherNo | 可读写 |

### T2: 索引存在性测试 (L2)

**目的**: 验证 V64 Migration 所有索引已创建

| 序号 | 测试点 | 预期结果 |
|------|--------|----------|
| T2-1 | idx_input_invoice_doc_no | 存在 |
| T2-2 | idx_input_invoice_voucher_no | 存在 |
| T2-3 | idx_receivable_doc_no/voucher_no/invoice_no | 存在 |
| T2-4 | idx_payable_doc_no/voucher_no/invoice_no | 存在 |
| T2-5 | idx_voucher_source_doc_no/type | 存在 |
| T2-6 | idx_business_doc_voucher_no | 存在 |
| T2-7 | idx_arap_settlement_voucher_no | 存在 |
| T2-8 | idx_settle_entry_receivable/payable | 存在 |

### T3: 销售链路编号传递测试 (L3)

**链路**: 销售发票 → 应收单 → 凭证

| 序号 | 测试点 | 步骤 | 验证 |
|------|--------|------|------|
| T3-1 | 销售发票生成应收单 | 调用 OutputInvoiceStateMachineServiceImpl | 应收单.docNo = 发票.docNo<br>应收单.invoiceNo = 发票.invoiceNo |
| T3-2 | 应收单生成凭证 | 调用 markVouchered(invoiceNo, voucherNo) | 应收单.voucherNo 被设置<br>凭证.sourceDocType = "OUTPUT_INVOICE"<br>凭证.sourceDocNo = 发票号 |
| T3-3 | 完整链路追溯 | 输入发票号 | upstream: 空<br>downstream: 发票→应收单→凭证 |

### T4: 采购链路编号传递测试 (L3)

**链路**: 采购发票 → 应付单 → 凭证

| 序号 | 测试点 | 步骤 | 验证 |
|------|--------|------|------|
| T4-1 | 采购发票导入 | 调用 InputInvoiceImportService.importBatch() | 发票.docNo 被设置<br>发票.voucherNo 被设置 |
| T4-2 | 采购发票生成应付单 | 调用 createPayableFromInvoice() | 应付单.docNo = 发票.docNo<br>应付单.invoiceNo = 发票.invoiceNo<br>应付单.voucherNo = 发票.voucherNo |
| T4-3 | 完整链路追溯 | 输入采购发票号 | upstream: 空<br>downstream: 发票→应付单→凭证 |

### T5: 核销链路测试 (L3)

**链路**: 应收/应付单 → 核销单 → 凭证

| 序号 | 测试点 | 步骤 | 验证 |
|------|--------|------|------|
| T5-1 | 核销单生成凭证 | 调用 ArapSettlementServiceImpl.generateVoucher() | 核销单.voucherNo 被设置<br>应收/应付单.voucherNo 被回写 |
| T5-2 | 核销→凭证溯源 | 输入核销单号 | downstream: 核销单→凭证 |
| T5-3 | 应收单→核销单追溯 | 输入应收单号 | downstream: 应收单→核销单→凭证 |

### T6: 追溯接口测试 (L1)

**接口**: `GET /api/v1/vouchers/trace?no={编号}`

| 序号 | 测试点 | 输入 | 预期 |
|------|--------|------|------|
| T6-1 | 凭证号追溯 | voucherNo | upstream: 凭证→核销单→应收单→发票 |
| T6-2 | 发票号追溯 | invoiceNo | downstream: 发票→应收单→凭证 |
| T6-3 | 业务单号追溯 | docNo | downstream: 业务单→凭证 |
| T6-4 | 核销单号追溯 | settlementNo | upstream: 核销单→应收单<br>downstream: 核销单→凭证 |
| T6-5 | 无效编号 | "NONEXISTENT" | traceType="UNKNOWN", 上下均为空 |

### T7: 一致性校验 Job 测试 (L2)

**类**: NumberingConsistencyCheckJob

| 序号 | 测试点 | 前置条件 | 预期日志 |
|------|--------|----------|----------|
| T7-1 | 无问题场景 | 所有关联字段一致 | "✅ 未发现不一致数据" |
| T7-2 | 发票 voucherId 非空但 voucherNo 为空 | 插入脏数据 | "进项发票: voucherId 非空但 voucherNo 为空: N 条" |
| T7-3 | 业务单据 VOUCHERED 但 voucherId 为空 | 插入脏数据 | "业务单据: 状态 VOUCHERED 但 voucherId 为空: N 条" |
| T7-4 | 核销单 voucherId 非空但 voucherNo 为空 | 插入脏数据 | "核销单: voucherId 非空但 voucherNo 为空: N 条" |

---

## 三、测试执行顺序

```
1. mvn test                          # L0+L1: 快速验证（默认跳过 slow）
2. mvn test -Dgroups="slow"          # L2: 真实 DB 测试（需 Docker）
3. 手动验证 L3: 端到端链路测试
4. 手动触发 Job: mvn test -Dtest=NumberingConsistencyCheckJobTest
```

---

## 四、数据准备策略

### 测试数据编码规则
- 所有测试数据使用 `9999.xxxx` 编码前缀，避免与 V60 生产数据冲突
- 示例: `9999.INV.001`, `9999.DOC.001`, `9999.VCH.001`

### 测试数据清理
- 每个测试方法使用 `@Transactional` 自动回滚
- Job 测试使用独立事务，不影响主测试

---

## 五、预期覆盖率

| 模块 | 覆盖率 | 说明 |
|------|--------|------|
| 实体字段 | 100% | 所有新增字段可读写 |
| Migration | 100% | 所有 ALTER TABLE + CREATE INDEX |
| 销售链路 | 100% | 发票→应收单→凭证 |
| 采购链路 | 100% | 发票→应付单→凭证 |
| 核销链路 | 100% | 核销单→凭证，应收/应付回写 |
| 追溯接口 | 100% | 6 种实体类型匹配 |
| 一致性 Job | 100% | 5 类检查全部覆盖 |

---

## 六、文件清单

| 文件 | 类型 | 用途 |
|------|------|------|
| `NumberingAssociationEntityTest.java` | L2 | 实体字段 CRUD |
| `NumberingAssociationIndexTest.java` | L2 | 索引存在性 |
| `NumberingTraceControllerTest.java` | L1 | 追溯接口 MockMvc |
| `NumberingTraceServiceImplTest.java` | L1 | 追溯 Service Mock |
| `NumberingConsistencyCheckJobTest.java` | L2 | 一致性 Job |
| `NumberingAssociationE2ETest.java` | L3 | 端到端链路 |
