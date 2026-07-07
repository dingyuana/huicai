# 核心链路映射 — Linkage Map

> **编号**：HUICAI-ARC-005
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部
**原则**：改了什么表，就把包含该表的所有链路测一遍。

---

## 表索引

| 表 | 涉及链路 | ID 是否 IDENTITY | 备注 |
|:---|---:|---:|---|
| t_output_invoice | L1 | ✅ (V29) | 销项发票 |
| t_input_invoice | L2 | ✅ (V29) | 进项发票 |
| t_business_doc | L1, L2, L3, L5, L6 | ✅ (V28) | 业务单据（替代 t_receivable/t_payable） |
| t_business_doc_entry | L1, L2 | ✅ (V61) | 业务单据分录 |
| t_voucher | L1, L2, L3, L4 | ✅ (V28) | 凭证 |
| t_voucher_entry | L1, L2, L3 | ✅ (V28) | 凭证分录 |
| t_voucher_cash_flow | L3 | ✅ (V54) | 现金流标签 |
| t_voucher_template | L4 | ✅ (V23) | 凭证模板 |
| t_voucher_template_line | L4 | ✅ (V23) | 模板行 |
| t_audit_log | L1, L2, L3 | ✅ (V52) | 审计日志 |
| t_bank_statement | L5 | ✅ (V28 前) | 银行流水 |
| t_reconciliation_log | L5 | ✅ (V24) | 对账记录 |
| t_reconciliation_exception | L5 | ✅ (V38) | 对账异常 |
| t_reconciliation_suggestion | L5 | ✅ (V54) | 对账建议 |
| t_arap_settlement | L6 | ✅ (V54) | 核销单 |
| t_arap_settlement_entry | L6 | ✅ (V54) | 核销明细 |
| t_period | L7 | ✅ (V32) | 会计期间 |
| t_subject_balance | L7 | ✅ (V54) | 科目余额 |

---

## L1: 销售发票审核通过（P34 业务单据体系）

**状态流**：`PENDING_REVIEW → CONFIRMED`

**路径**：`/api/v1/tax/output-invoices/{id}/confirm` → `OutputInvoiceStateMachineServiceImpl.confirm()`

**涉及表**：t_output_invoice → t_business_doc(INVOICE_OUT) → t_voucher

**P34 变更**（2026-07-01）：删除 t_receivable/t_payable 独立表（V74），统一使用 t_business_doc 记录业务单据。发票审核通过后，创建 INVOICE_OUT 类型业务单据 + 凭证。

**验证脚本**（curl + psql）：

```bash
#!/usr/bin/env bash
# 用法: bash verify-l1.sh <invoice_id>

TOKEN=$(curl -s -X POST 'http://localhost:8000/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")

ID=${1:-2}

echo "=== L1: 审核通过 invoice=$ID ==="
RESP=$(curl -s -X POST "http://localhost:8000/api/v1/tax/output-invoices/$ID/confirm" \
  -H "Authorization: Bearer $TOKEN")
echo "API: $RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read().split('API: ')[1]); assert d['code']==200, f'API failed: {d}'; print('  code=200 OK')"

echo "=== 验证 ==="
# 1. 发票状态 CONFIRMED
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT '1. invoice status: ' || status FROM t_output_invoice WHERE id=$ID;"
# 2. 业务单据已创建 (INVOICE_OUT) — P34 变更：替代应收单
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT '2. business_doc: id=' || id || ' status=' || status || ' doc_type=' || doc_type || ' amount=' || amount
FROM t_business_doc WHERE invoice_no=(SELECT invoice_no FROM t_output_invoice WHERE id=$ID);"
# 3. 凭证已创建 (DRAFT)
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT '3. voucher: id=' || id || ' voucher_no=' || voucher_no || ' status=' || status
FROM t_voucher WHERE source_doc_id=$ID AND source_doc_type='OUTPUT_INVOICE';"
echo "=== L1 OK ==="
```

---

## L2: 采购发票确认

**状态流**：`PENDING_CONFIRM → CONFIRMED`

**路径**：`/api/v1/input-invoices/{id}/confirm`

**涉及表**：t_input_invoice → t_business_doc(INVOICE_IN) → t_business_doc_entry → t_voucher

**P34 变更**（2026-07-01）：删除 t_payable 独立表（V74），统一使用 t_business_doc 记录业务单据。采购发票确认后，创建 INVOICE_IN 类型业务单据 + 凭证。

```bash
#!/usr/bin/env bash
# verify-l2.sh <invoice_id>

TOKEN=...

ID=${1:-1}
echo "=== L2: 采购发票确认 id=$ID ==="
curl -s -X POST "http://localhost:8000/api/v1/input-invoices/$ID/confirm" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin); assert d['code']==200, f'fail: {d}'; print('OK')"

docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'invoice: ' || status FROM t_input_invoice WHERE id=$ID;"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'business_doc: id=' || id || ' status=' || status || ' doc_type=' || doc_type FROM t_business_doc
WHERE invoice_no=(SELECT invoice_no FROM t_input_invoice WHERE id=$ID);"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'voucher: id=' || id || ' status=' || status FROM t_voucher
WHERE source_doc_id=(SELECT id FROM t_business_doc
  WHERE invoice_no=(SELECT invoice_no FROM t_input_invoice WHERE id=$ID));"
echo "=== L2 OK ==="
```

---

## L3: 凭证状态机

**状态流**：`DRAFT → SUBMITTED → AUDITED → POSTED`

### L3a: 提交 DRAFT → SUBMITTED

```bash
# verify-l3a.sh <voucher_id>
TOKEN=...
ID=${1:-1}
echo "=== L3a: 提交凭证 id=$ID ==="
curl -s -X POST "http://localhost:8000/api/v1/vouchers/$ID/submit" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'status=' || status FROM t_voucher WHERE id=$ID;"
```

### L3b: 审核 SUBMITTED → AUDITED

```bash
# verify-l3b.sh <voucher_id>
TOKEN=...
ID=${1:-1}
echo "=== L3b: 审核凭证 id=$ID ==="
curl -s -X POST "http://localhost:8000/api/v1/vouchers/$ID/audit" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'voucher status=' || status FROM t_voucher WHERE id=$ID;"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'audit_log: ' || count(*) FROM t_audit_log WHERE target_id=$ID AND target_type='VOUCHER';"
```

### L3c: 过账 AUDITED → POSTED

```bash
# verify-l3c.sh <voucher_id>
TOKEN=...
ID=${1:-1}
echo "=== L3c: 过账凭证 id=$ID ==="
curl -s -X POST "http://localhost:8000/api/v1/vouchers/$ID/post" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'status=' || status FROM t_voucher WHERE id=$ID;"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'cash_flow: ' || count(*) FROM t_voucher_cash_flow WHERE voucher_id=$ID;"
```

---

## L4: 凭证模板

### L4a: 用模板生成凭证

**路径**：`/api/v1/voucher-templates/{id}/generate`

**涉及表**：t_voucher_template → t_voucher_template_line → t_voucher → t_voucher_entry

```bash
# verify-l4.sh <template_id>
TOKEN=...
ID=${1:-1}
echo "=== L4a: 模板生成凭证 template=$ID ==="
curl -s -X POST "http://localhost:8000/api/v1/voucher-templates/$ID/generate" \
  -H "Authorization: Bearer $TOKEN"
# 验证最近一条凭证是否有正确的分录
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT v.id, v.voucher_no, v.status,
       (SELECT count(*) FROM t_voucher_entry ve WHERE ve.voucher_id=v.id) as entry_count
FROM t_voucher v ORDER BY v.id DESC LIMIT 1;"
```

### L4b: 模板开关 (isActive)

```bash
# verify-l4b.sh <template_id>
TOKEN=...
ID=${1:-1}
echo "=== L4b: 切换模板状态 id=$ID ==="
curl -s -X PATCH "http://localhost:8000/api/v1/voucher-templates/$ID/toggle?active=false" \
  -H "Authorization: Bearer $TOKEN"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'is_active=' || is_active FROM t_voucher_template WHERE id=$ID;"
curl -s -X PATCH "http://localhost:8000/api/v1/voucher-templates/$ID/toggle?active=true" \
  -H "Authorization: Bearer $TOKEN"
```

---

## L5: 银行对账

**路径**：`/api/v1/bank-reconciliation/execute`

**涉及表**：t_bank_statement ↔ t_business_doc（核销表：t_reconciliation_log, t_reconciliation_exception, t_reconciliation_suggestion）

```bash
# verify-l5.sh
TOKEN=...
echo "=== L5: 执行对账 ==="
curl -s -X POST "http://localhost:8000/api/v1/bank-reconciliation/execute?period=2025-12" \
  -H "Authorization: Bearer $TOKEN"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'matched: ' || count(*) FROM t_reconciliation_log WHERE period='2025-12';"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'exceptions: ' || count(*) FROM t_reconciliation_exception WHERE period='2025-12';"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT 'suggestions: ' || count(*) FROM t_reconciliation_suggestion WHERE period='2025-12';"
```

---

## L6: 核销结算

**状态流**：DRAFT → CONFIRMED → VOUCHERED → REVERSED

**路径**：`/api/v1/reconciliation/execute` → 核销工作台

**涉及表**：t_business_doc → t_arap_settlement → t_arap_settlement_entry → t_voucher

**P34 变更**（2026-07-01）：核销源已从 t_receivable/t_payable 改为 t_business_doc。核销唯一入口为核销工作台（ReconciliationController）。

```bash
# verify-l6.sh
TOKEN=...
echo "=== L6: 核销 ==="
# 列出待核销业务单据（P34 变更：替代应收/应付单）
curl -s "http://localhost:8000/api/v1/business-docs/page?status=DRAFT&current=1&size=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin); recs=d.get('data',{}).get('records',[])
for r in recs: print(f'  business_doc {r[\"id\"]}: {r.get(\"docNo\",\"\")} docType={r.get(\"docType\",\"\")} amount={r[\"amount\"]}')"
```

---

## L7: 期末结转

**路径**：`/api/v1/period-close/execute`

**涉及表**：t_period, t_subject_balance, t_voucher

```bash
# verify-l7.sh
TOKEN=...
echo "=== L7: 结转 period=2025-12 ==="
curl -s -X POST "http://localhost:8000/api/v1/period-close/execute?period=2025-12&userId=1" \
  -H "Authorization: Bearer $TOKEN"
docker exec huicai-postgres psql -U huicai -d huicai -tc "
SELECT status FROM t_period WHERE period='2025-12';"
```

---

## 使用规则

1. **每次 migration 提交前**：确定改动了哪些表，在表索引中标记
2. **每次 migration 提交后**：执行所有包含被改表的链路验证脚本
3. **链路验证失败 = migration 回滚**，不允许带病上线
4. **新增核心流**时，必须在本文档中添加对应链路

### 快速查表

```sql
-- 检查某张表是否所有涉及链路都已验证
SELECT table_name, array_agg(DISTINCT link) as links
FROM (
  VALUES
    ('t_output_invoice','L1'), ('t_business_doc','L1'), ('t_business_doc_entry','L1'),
    ('t_input_invoice','L2'),
    ('t_voucher','L3'), ('t_voucher_entry','L3'), ('t_voucher_cash_flow','L3'), ('t_audit_log','L3'),
    ('t_voucher_template','L4'), ('t_voucher_template_line','L4'),
    ('t_bank_statement','L5'), ('t_reconciliation_log','L5'), ('t_reconciliation_exception','L5'), ('t_reconciliation_suggestion','L5'),
    ('t_arap_settlement','L6'), ('t_arap_settlement_entry','L6'),
    ('t_period','L7'), ('t_subject_balance','L7')
) AS t(table_name, link)
GROUP BY table_name;
```
